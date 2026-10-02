package dev.abros.anthub.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Informational row: fixed by the server, with no selection or button behavior. */
final class UiLockedRow extends AbstractWidget {
    UiLockedRow(int x, int y, int width, String label, String reason) {
        super(x, y, width, UiKit.CONTROL_HEIGHT, Component.literal(label));
        active = false;
        setTooltip(Tooltip.create(Component.literal(label + "\n" + reason)));
    }

    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        int x = getX(), y = getY();
        int ink = UiKit.muted();
        // A lock identifies a fixed requirement without suggesting a checkbox.
        g.renderOutline(x + 10, y + 4, 6, 6, ink);
        g.fill(x + 8, y + 8, x + 18, y + 15, ink);
        g.fill(x + 12, y + 10, x + 14, y + 13, UiKit.surface());
        var font = Minecraft.getInstance().font;
        Ui.text(g, font, UiKit.fit(font, getMessage().getString(), Math.max(0, getWidth() - 32)), x + 26, y + 6, UiKit.text(), false);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
