/*
 * Copyright (c) 2020, RKGman
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

package com.music.firebeats;

import static net.runelite.api.MenuOpcode.RUNELITE_OVERLAY_CONFIG;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

public class FireBeatsOverlay extends OverlayPanel {
    private final FireBeatsPlugin plugin;
    private final FireBeatsConfig config;

    @Inject
    private FireBeatsOverlay(FireBeatsPlugin plugin, FireBeatsConfig config)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_CENTER);
        this.plugin = plugin;
        this.config = config;
        getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Fire Beats overlay"));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (plugin.getMusicConfig().showCurrentTrackName() == true)
        {
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Area's Track Name: " + plugin.getCurrentTrackBox().getText())
                    .color(Color.GREEN)
                    .build());

            panelComponent.setPreferredSize(new Dimension(
                    graphics.getFontMetrics().stringWidth("Area's Track Name: " +
                            plugin.getCurrentTrackBox().getText()) + 10,
                    0));

            return super.render(graphics);
        }
        else
        {
            return null;
        }
    }
}
