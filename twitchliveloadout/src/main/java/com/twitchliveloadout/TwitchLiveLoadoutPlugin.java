/*
 * Copyright (c) 2020, Pepijn Verburg <pepijn.verburg@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.twitchliveloadout;

import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.twitchliveloadout.ui.TwitchLiveLoadoutPanel;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.task.Schedule;
import com.google.gson.*;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import org.pf4j.Extension;

/**
 * Manages polling and event listening mechanisms to synchronize the state
 * to the Twitch Configuration Service. All client data is fetched in this class
 * ad passed to a couple of other classes.
 */
@Extension
@PluginDescriptor(
	name = "Twitch Live Loadout",
	description = "Send live Equipment, Bank, Combat Statistics and more to Twitch Extensions as a streamer.",
	enabledByDefault = false,
	type = PluginType.MISCELLANEOUS
)
public class TwitchLiveLoadoutPlugin extends Plugin
{
	@Inject
	private TwitchLiveLoadoutConfig config;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ClientToolbar clientToolbar;

	/**
	 * The plugin panel to manage data such as combat fights.
	 */
	private static final String ICON_FILE = "/panel_icon.png";
	private TwitchLiveLoadoutPanel pluginPanel;
	private NavigationButton navigationButton;

	/**
	 * Twitch Configuration Service state that can be mapped to a JSON.
	 */
	private TwitchState twitchState;

	/**
	 * Twitch Configuration Service API end-point helpers.
	 */
	private TwitchApi twitchApi;

	/**
	 * Dedicated manager for all fight information.
	 */
	private FightStateManager fightStateManager;

	/**
	 * Dedicated manager for all item information.
	 */
	private ItemStateManager itemStateManager;

	/**
	 * Dedicated manager for all skill / stat information.
	 */
	private SkillStateManager skillStateManager;

	/**
	 * Initialize this plugin
	 * @throws Exception
	 */
	@Override
	protected void startUp() throws Exception
	{
		super.startUp();

		initializeTwitch();
		initializeManagers();
		initializePanel();
	}

	/**
	 * Cleanup properly after disabling the plugin
	 * @throws Exception
	 */
	@Override
	protected void shutDown() throws Exception
	{
		super.shutDown();

		shutDownPanels();
		shutDownManagers();
		shutDownTwitch();
	}

	private void shutDownTwitch()
	{
		// Only the API requires dedicated shutdown
		twitchApi.shutDown();

		twitchState = null;
		twitchApi = null;
	}

	private void shutDownManagers()
	{
		// Only the fight state manager requires dedicated shutdown
		fightStateManager.shutDown();

		fightStateManager = null;
		itemStateManager = null;
		skillStateManager = null;
	}

	private void shutDownPanels()
	{
		pluginPanel = null;
		clientToolbar.removeNavigation(navigationButton);
	}

	private void initializePanel()
	{
		pluginPanel = new TwitchLiveLoadoutPanel(twitchApi, fightStateManager);
		pluginPanel.rebuild();

		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), ICON_FILE);

		navigationButton = NavigationButton.builder()
			.tooltip("Twitch Live Loadout Status")
			.icon(icon)
			.priority(99)
			.panel(pluginPanel)
			.build();

		clientToolbar.addNavigation(navigationButton);
	}

	private void initializeTwitch()
	{
		twitchState = new TwitchState(config, itemManager);
		twitchApi = new TwitchApi(this, client, config, chatMessageManager);
	}

	private void initializeManagers()
	{
		fightStateManager = new FightStateManager(this, config, client);
		itemStateManager = new ItemStateManager(twitchState, client, itemManager, config);
		skillStateManager = new SkillStateManager(twitchState, client);
	}

	/**
	 * Helper to get the current configuration.
	 * @param configManager
	 * @return
	 */
	@Provides
	TwitchLiveLoadoutConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TwitchLiveLoadoutConfig.class);
	}

	/**
	 * Polling mechanism to update the state only when it has changed.
	 */
	@Schedule(period = 500, unit = ChronoUnit.MILLIS, asynchronous = true)
	public void syncState()
	{
		// Base this on the fact is the state is changed for a fixed time for debouncing purposes.
		// This makes the updates more smooth when multiple changes occur fast after each other.
		// Take for example switching gear: when the first armour piece is worn a change is directly
		// triggered, causing the rest of the switch to come the update after (a few seconds later).
		// This debouncing behaviour makes sure that quickly succeeding changes are batched and with that
		// let for example gear switches come through in one state update towards the viewer.
		final boolean updateRequired = twitchState.isChangedLongEnough();

		// Guard: check if something has changed to avoid unnecessary updates.
		if (!updateRequired)
		{
			return;
		}

		final JsonObject filteredState = twitchState.getFilteredState();

		// We will not verify whether the set was successful here
		// because it is possible that the request is being delayed
		// due to the custom streamer delay
		final boolean isScheduled = twitchApi.scheduleBroadcasterState(filteredState);

		// guard: check if the scheduling was successful due to for example rate limiting
		// if not we will not acknowledge the change
		if (!isScheduled) {
			return;
		}

		final String filteredStateString = filteredState.toString();
		final String newFilteredStateString = twitchState.getFilteredState().toString();

		// Guard: check if the state has changed in the mean time,
		// because the request takes some time, in this case we will
		// not acknowledge the change
		if (!filteredStateString.equals(newFilteredStateString))
		{
			return;
		}

		twitchState.acknowledgeChange();
	}

	/**
	 * Polling mechanism to update the fight statistics as many
	 * events are continuously updating various properties (e.g. game ticks).
	 * This would overload the update life cycle too much so a
	 * small penalty in the form of the (polling) delay is worthwhile.
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncFightStatisticsState()
	{
		JsonObject fightStatistics = fightStateManager.getFightStatisticsState();

		twitchState.setFightStatistics(fightStatistics);
	}

	/**
	 * Polling mechanism to sync player info.
	 * We cannot use the game state update events as the player name is not loaded then.
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncPlayerInfo()
	{

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final String playerName = client.getLocalPlayer().getName();
		twitchState.setPlayerName(playerName);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		itemStateManager.onItemContainerChanged(event);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		skillStateManager.onStatChanged(event);
		fightStateManager.onStatChanged(event);
	}

	@Subscribe
	public void onFakeXpDrop(FakeXpDrop event)
	{
		fightStateManager.onFakeXpDrop(event);
	}

	@Subscribe
	public void onAnimationChanged(final AnimationChanged event)
	{
		fightStateManager.onAnimationChanged(event);
	}

	@Subscribe
	public void onGraphicChanged(SpotAnimationChanged event)
	{
		fightStateManager.onGraphicChanged(event);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		 fightStateManager.onHitsplatApplied(event);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		fightStateManager.onNpcDespawned(npcDespawned);
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned playerDespawned)
	{
		fightStateManager.onPlayerDespawned(playerDespawned);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged interactingChanged)
	{
		fightStateManager.onInteractingChanged(interactingChanged);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		 fightStateManager.onGameTick();
	}

	/**
	 * Simulate game ticks when not logged in to still register for idling fight time when not logged in
	 */
	@Schedule(period = 600, unit = ChronoUnit.MILLIS, asynchronous = true)
	public void onLobbyGameTick()
	{
		if (client.getGameState() != GameState.LOGIN_SCREEN)
		{
			return;
		}

		fightStateManager.onGameTick();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		String key = configChanged.getKey();

		// Always clear the scheduled state updates
		// when either the value is increased of decreased
		// it can mess up the state updates badly
		if (key.equals("syncDelay"))
		{
			twitchApi.clearScheduledBroadcasterStates();
		}

		// Handle keys that should trigger an update of the state as well.
		// Note that on load these events are not triggered, meaning that
		// in the constructor of the TwitchState class one should also load
		// this configuration value!
		if (key.equals("overlayTopPosition"))
		{
			twitchState.setOverlayTopPosition(config.overlayTopPosition());
		}
		else if (key.equals("virtualLevelsEnabled"))
		{
			twitchState.setVirtualLevelsEnabled(config.virtualLevelsEnabled());
		}

		twitchState.forceChange();
	}

	public boolean hasValidPanels()
	{
		return pluginPanel != null;
	}

	public void updateConnectivityPanel()
	{
		if (!hasValidPanels())
		{
			return;
		}

		pluginPanel.getConnectivityPanel().rebuild();
	}

	public void updateCombatPanel()
	{
		if (!hasValidPanels())
		{
			return;
		}

		pluginPanel.getCombatPanel().rebuild();
	}
}
