/*
 * Copyright (c) 2020, Christopher Oswald <https://github.com/cesoun>
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
package com.optimalquestguide.Layouts;


import java.awt.*;
import java.util.function.Function;

/**
 * Grid Layout implementation with support for dynamic sizing based on cell visibility.
 * <p>
 * Functions the same as DynamicGridLayout but collapses the space between cells when they have
 * their visibility set to false. By default the space remains there and they would
 * need to be from the grid.
 */
public class CollapsingGridLayout extends GridLayout {

    public CollapsingGridLayout() {
        this(1, 0, 0, 0);
    }

    public CollapsingGridLayout(int rows, int cols) {
        this(rows, cols, 0, 0);
    }

    public CollapsingGridLayout(int rows, int cols, int hgap, int vgap) {
        super(rows, cols, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            return calculateSize(parent, Component::getPreferredSize);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            return calculateSize(parent, Component::getMinimumSize);
        }
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            final Insets insets = parent.getInsets();
            final int ncomponents = parent.getComponentCount();
            int nrows = getRows();
            int ncols = getColumns();

            if (ncomponents == 0) {
                return;
            }

            if (nrows > 0) {
                ncols = (ncomponents + nrows - 1) / nrows;
            } else {
                nrows = (ncomponents + ncols - 1) / ncols;
            }

            final int hgap = getHgap();
            final int vgap = getVgap();

            // scaling factors
            final Dimension pd = preferredLayoutSize(parent);
            final Insets parentInsets = parent.getInsets();
            int wborder = parentInsets.left + parentInsets.right;
            int hborder = parentInsets.top + parentInsets.bottom;
            final double sw = (1.0 * parent.getWidth() - wborder) / (pd.width - wborder);
            final double sh = (1.0 * parent.getHeight() - hborder) / (pd.height - hborder);

            final int[] w = new int[ncols];
            final int[] h = new int[nrows];

            // calculate dimensions for all visible components + apply scaling
            for (int i = 0; i < parent.getComponentCount(); i++) {
                final int r = i / ncols;
                final int c = i % ncols;
                final Component comp = parent.getComponent(i);

                if (!comp.isVisible()) continue;

                final Dimension d = comp.getPreferredSize();
                d.width = (int) (sw * d.width);
                d.height = (int) (sh * d.height);

                if (w[c] < d.width) {
                    w[c] = d.width;
                }

                if (h[r] < d.height) {
                    h[r] = d.height;
                }
            }

            // Apply new bounds to all child components
            for (int c = 0, x = insets.left; c < ncols; c++) {
                for (int r = 0, y = insets.top; r < nrows; r++) {
                    int i = r * ncols + c;

                    if (i < ncomponents) {
                        if (!parent.getComponent(i).isVisible()) continue;

                        parent.getComponent(i).setBounds(x, y, w[c], h[r]);
                    }

                    y += h[r] + vgap;
                }

                x += w[c] + hgap;
            }
        }
    }

    /**
     * Calculate outer size of the layout based on it's visible children and sizer
     *
     * @param parent parent component
     * @param sizer  functioning returning dimension of the child component
     * @return outer size
     */
    private Dimension calculateSize(final Container parent, final Function<Component, Dimension> sizer) {
        final int ncomponents = getVisibleComponents(parent);
        int nrows = getRows();
        int ncols = getColumns();

        if (nrows > 0) {
            ncols = (ncomponents + nrows - 1) / nrows;
        } else {
            nrows = (ncomponents + ncols - 1) / ncols;
        }

        final int[] w = new int[ncols];
        final int[] h = new int[nrows];

        // Calculate dimensions for all visible components
        for (int i = 0; i < parent.getComponentCount(); i++) {
            final int r = i / ncols;
            final int c = i % ncols;
            final Component comp = parent.getComponent(i);

            // Ignore not visible components.
            if (!comp.isVisible()) continue;

            final Dimension d = sizer.apply(comp);

            if (w[c] < d.width) {
                w[c] = d.width;
            }

            if (h[r] < d.height) {
                h[r] = d.height;
            }
        }

        // Calculate total width and height of the layout
        int nw = 0;

        for (int j = 0; j < ncols; j++) {
            nw += w[j];
        }

        int nh = 0;

        for (int i = 0; i < nrows; i++) {
            nh += h[i];
        }

        final Insets insets = parent.getInsets();

        // Apply insets and horizontal and vertical gap to layout
        // This uses a different nrow count because we only want to apply to the visible components.
        return new Dimension(
                insets.left + insets.right + nw + (ncols - 1) * getHgap(),
                insets.top + insets.bottom + nh + (getVisibleComponents(parent) - 1) * getVgap());
    }

    /**
     * Get the count of visible child components
     *
     * @param parent parent component
     * @return visible count
     */
    private int getVisibleComponents(Container parent) {
        int visible = 0;

        for (Component c : parent.getComponents())
            if (c.isVisible())
                visible++;

        return visible;
    }
}