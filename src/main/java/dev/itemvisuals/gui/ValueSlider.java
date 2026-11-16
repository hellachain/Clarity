package dev.itemvisuals.gui;

import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Slim animated slider: label + value on top, gradient track and a knob that eases to the value. */
public class ValueSlider extends AbstractSliderButton {
   private final String label;
   private final String unit;
   private final float min;
   private final float max;
   private final float step;
   private final Consumer<Float> setter;
   private final Ui.Smooth shown;
   private final Ui.Smooth hover = new Ui.Smooth(0.0F);

   public ValueSlider(int x, int y, int w, int h, String label, String unit, float min, float max, float step, float current, Consumer<Float> setter) {
      super(x, y, w, h, Component.empty(), Mth.clamp((current - min) / (max - min), 0.0F, 1.0F));
      this.label = label;
      this.unit = unit;
      this.min = min;
      this.max = max;
      this.step = step;
      this.setter = setter;
      this.shown = new Ui.Smooth((float)this.value);
      this.updateMessage();
   }

   public float get() {
      float v = (float)(this.min + (this.max - this.min) * this.value);
      return Math.round(v / this.step) * this.step;
   }

   public void set(float v) {
      this.setValue(Mth.clamp((v - this.min) / (this.max - this.min), 0.0F, 1.0F));
   }

   private String valueText() {
      float v = this.get();
      return (this.step >= 1.0F ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v)) + this.unit;
   }

   protected void updateMessage() {
      this.setMessage(Component.literal(this.label + ": " + this.valueText()));
   }

   protected void applyValue() {
      this.setter.accept(this.get());
   }

   public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      Font font = Minecraft.getInstance().font;
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      float hv = this.hover.update(this.isHovered() || this.isFocused() ? 1.0F : 0.0F, 16.0F);
      float t = this.shown.update((float)this.value, 22.0F);
      String v = this.valueText();
      Ui.text(g, font, Ui.fit(font, this.label, w - font.width(v) - 6), x, y + 1, Ui.lerp(Ui.DIM, Ui.TEXT, hv));
      Ui.text(g, font, v, x + w - font.width(v), y + 1, Ui.TEXT);
      int ty = y + this.getHeight() - 6;
      Ui.round(g, x, ty, x + w, ty + 3, 1, 0xFF3A3452);
      int fx = x + Math.round((w - 2) * t);
      Ui.gradientH(g, x, ty, Math.max(x + 1, fx), ty + 3, Ui.ACCENT2, Ui.ACCENT);
      int r = hv > 0.5F ? 4 : 3;
      if (hv > 0.01F) {
         Ui.round(g, fx - r - 2, ty + 1 - r - 2, fx + r + 2, ty + 2 + r + 2, 4, Ui.mul(Ui.ACCENT, 0.3F * hv));
      }
      Ui.round(g, fx - r, ty + 1 - r, fx + r, ty + 2 + r, 3, 0xFFFFFFFF);
   }
}
