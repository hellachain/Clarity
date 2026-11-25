package dev.itemvisuals.gui;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Inventory slot with rounded frame, hover glow, pulsing selection and a dot for bound items. */
public class SlotWidget extends AbstractWidget {
   private final int slot;
   private final IntFunction<ItemStack> stacks;
   private final IntPredicate selected;
   private final IntPredicate bound;
   private final IntConsumer onClick;
   private final Ui.Smooth hover = new Ui.Smooth(0.0F);
   private final Ui.Smooth sel;

   public SlotWidget(int x, int y, int slot, IntFunction<ItemStack> stacks, IntPredicate selected, IntPredicate bound, IntConsumer onClick) {
      super(x, y, 18, 18, Component.empty());
      this.slot = slot;
      this.stacks = stacks;
      this.selected = selected;
      this.bound = bound;
      this.onClick = onClick;
      this.sel = new Ui.Smooth(selected.test(slot) ? 1.0F : 0.0F);
   }

   protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
      int x = this.getX();
      int y = this.getY();
      float hv = this.hover.update(this.isHovered() ? 1.0F : 0.0F, 18.0F);
      float sl = this.sel.update(this.selected.test(this.slot) ? 1.0F : 0.0F, 12.0F);
      if (sl > 0.01F) {
         float pulse = 0.55F + 0.25F * (float)Math.sin(Ui.now() / 260.0);
         Ui.round(g, x - 2, y - 2, x + 20, y + 20, 4, Ui.mul(Ui.ACCENT, pulse * sl));
      } else if (hv > 0.01F) {
         Ui.round(g, x - 1, y - 1, x + 19, y + 19, 4, Ui.mul(Ui.ACCENT2, 0.45F * hv));
      }
      Ui.outline(g, x, y, x + 18, y + 18, 3, Ui.lerp(0x40FFFFFF, 0x90FFFFFF, Math.max(hv, sl)), Ui.lerp(0xF0171322, 0xF0322A4C, Math.max(hv * 0.6F, sl)));
      ItemStack st = this.stacks.apply(this.slot);
      if (!st.isEmpty()) {
         g.item(st, x + 1, y + 1);
         if (this.bound.test(this.slot)) {
            Ui.round(g, x + 13, y + 1, x + 17, y + 5, 2, Ui.ACCENT);
         }
         if (this.isHovered()) {
            g.setTooltipForNextFrame(Minecraft.getInstance().font, st, mouseX, mouseY);
         }
      }
   }

   public void onClick(MouseButtonEvent event, boolean doubleClick) {
      this.onClick.accept(this.slot);
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
   }
}
