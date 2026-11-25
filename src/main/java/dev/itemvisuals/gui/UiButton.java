package dev.itemvisuals.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Rounded, animated button: hover glow, press flash, animated "selected" state for chips/tabs. */
public class UiButton extends AbstractWidget {
   public enum Style {
      NORMAL,
      PRIMARY,
      DANGER,
      CHIP,
      TAB
   }

   private final Supplier<String> label;
   private final Runnable onPress;
   private final Style style;
   private BooleanSupplier selected = () -> false;
   private BooleanSupplier struck = () -> false;
   private final Ui.Smooth hover = new Ui.Smooth(0.0F);
   private final Ui.Smooth sel;
   private long pressedAt = -100000L;

   public UiButton(int x, int y, int w, int h, Supplier<String> label, Style style, Runnable onPress) {
      super(x, y, w, h, Component.empty());
      this.label = label;
      this.style = style;
      this.onPress = onPress;
      this.sel = new Ui.Smooth(0.0F);
      this.setMessage(Component.literal(label.get()));
   }

   public static UiButton of(int x, int y, int w, int h, String label, Runnable onPress) {
      return new UiButton(x, y, w, h, () -> label, Style.NORMAL, onPress);
   }

   public static UiButton styled(int x, int y, int w, int h, String label, Style style, Runnable onPress) {
      return new UiButton(x, y, w, h, () -> label, style, onPress);
   }

   public static UiButton chip(int x, int y, int w, int h, String label, BooleanSupplier on, Runnable onPress) {
      UiButton b = new UiButton(x, y, w, h, () -> label, Style.CHIP, onPress);
      b.selected = on;
      b.sel.value = on.getAsBoolean() ? 1.0F : 0.0F;
      return b;
   }

   public UiButton selected(BooleanSupplier s) {
      this.selected = s;
      this.sel.value = s.getAsBoolean() ? 1.0F : 0.0F;
      return this;
   }

   public UiButton strike(BooleanSupplier s) {
      this.struck = s;
      return this;
   }

   public UiButton tip(String text) {
      this.setTooltip(Tooltip.create(Component.literal(text)));
      return this;
   }

   protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      Font font = Minecraft.getInstance().font;
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      float hv = this.hover.update(this.isHovered() && this.active ? 1.0F : 0.0F, 16.0F);
      float sl = this.sel.update(this.selected.getAsBoolean() ? 1.0F : 0.0F, 12.0F);
      float press = 1.0F - Mth.clamp((Ui.now() - this.pressedAt) / 220.0F, 0.0F, 1.0F);
      int base;
      int hi;
      int txt = Ui.TEXT;
      switch (this.style) {
         case PRIMARY:
            base = Ui.ACCENT_DARK;
            hi = Ui.ACCENT;
            break;
         case DANGER:
            base = 0xE0471C2A;
            hi = 0xF0782436;
            txt = 0xFFFFB4B4;
            break;
         case TAB:
            base = 0x00000000;
            hi = 0x30FFFFFF;
            break;
         default:
            base = 0xE02A243E;
            hi = 0xF03C3358;
      }
      int fill = Ui.lerp(base, hi, hv);
      if (this.style == Style.CHIP) {
         fill = Ui.lerp(fill, Ui.lerp(0xF05A3C8C, 0xF0784AB4, hv), sl);
      } else if (this.style == Style.TAB) {
         fill = Ui.lerp(fill, 0x40966EFF, sl);
         txt = Ui.lerp(Ui.DIM, Ui.TEXT, Math.max(sl, hv));
      }
      if (!this.active) {
         fill = Ui.mul(fill, 0.45F);
         txt = Ui.MUTED;
      }
      if (hv > 0.01F && this.style != Style.TAB) {
         Ui.round(g, x - 1, y - 1, x + w + 1, y + h + 1, 4, Ui.mul(this.style == Style.DANGER ? Ui.BAD : Ui.ACCENT, 0.45F * hv));
      }
      Ui.round(g, x, y, x + w, y + h, 3, fill);
      if (this.style != Style.TAB) {
         Ui.rect(g, x + 3, y, x + w - 3, y + 1, Ui.mul(0x30FFFFFF, 0.6F + 0.4F * hv));
      }
      if (press > 0.0F) {
         Ui.round(g, x, y, x + w, y + h, 3, Ui.mul(0x60FFFFFF, press));
      }
      if (this.style == Style.CHIP && sl > 0.01F) {
         int bw = (int)((w - 8) * sl);
         Ui.rect(g, x + w / 2 - bw / 2, y + h - 2, x + w / 2 + bw / 2, y + h - 1, Ui.ACCENT);
      }
      String s = Ui.fit(font, this.label.get(), w - 8);
      int tx = x + (w - font.width(s)) / 2;
      int ty = y + (h - 8) / 2 + (press > 0.6F ? 1 : 0);
      Ui.text(g, font, s, tx, ty, txt);
      if (this.struck.getAsBoolean()) {
         Ui.rect(g, tx - 1, ty + 3, tx + font.width(s) + 1, ty + 4, Ui.mul(Ui.DIM, 0.9F));
      }
   }

   public void onClick(MouseButtonEvent event, boolean doubleClick) {
      this.pressedAt = Ui.now();
      this.onPress.run();
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
   }
}
