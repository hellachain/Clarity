package dev.itemvisuals.gui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** iOS-style switch with a sliding knob and colour fade. */
public class UiToggle extends AbstractWidget {
   private final String label;
   private final BooleanSupplier state;
   private final Runnable toggle;
   private final Ui.Smooth knob;
   private final Ui.Smooth hover = new Ui.Smooth(0.0F);

   public UiToggle(int x, int y, int w, String label, BooleanSupplier state, Runnable toggle) {
      super(x, y, w, 14, Component.literal(label));
      this.label = label;
      this.state = state;
      this.toggle = toggle;
      this.knob = new Ui.Smooth(state.getAsBoolean() ? 1.0F : 0.0F);
   }

   public UiToggle tip(String text) {
      this.setTooltip(Tooltip.create(Component.literal(text)));
      return this;
   }

   protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      Font font = Minecraft.getInstance().font;
      int x = this.getX();
      int y = this.getY();
      float k = this.knob.update(this.state.getAsBoolean() ? 1.0F : 0.0F, 14.0F);
      float hv = this.hover.update(this.isHovered() ? 1.0F : 0.0F, 16.0F);
      int track = Ui.lerp(0xFF4A4464, Ui.ACCENT, k);
      if (hv > 0.01F) {
         Ui.round(g, x - 1, y, x + 25, y + 14, 5, Ui.mul(Ui.ACCENT, 0.35F * hv));
      }
      Ui.round(g, x, y + 1, x + 24, y + 13, 5, track);
      int kx = x + 1 + Math.round(12.0F * k);
      Ui.round(g, kx, y + 2, kx + 10, y + 12, 4, 0xFFFFFFFF);
      Ui.text(g, font, Ui.fit(font, this.label, this.getWidth() - 30), x + 30, y + 3, Ui.lerp(Ui.DIM, Ui.TEXT, Math.max(k, hv)));
   }

   public void onClick(MouseButtonEvent event, boolean doubleClick) {
      this.toggle.run();
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
   }
}
