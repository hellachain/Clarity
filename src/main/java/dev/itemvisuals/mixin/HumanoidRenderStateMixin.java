package dev.itemvisuals.mixin;

import dev.itemvisuals.render.ArmorHider;
import dev.itemvisuals.render.BodyWear;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(HumanoidRenderState.class)
public abstract class HumanoidRenderStateMixin implements ArmorHider.Holder {
   @Unique
   private int itemvisuals$hidden;
   @Unique
   private BodyWear.Worn itemvisuals$worn;

   @Override
   public int itemvisuals$hiddenArmor() {
      return this.itemvisuals$hidden;
   }

   @Override
   public void itemvisuals$setHiddenArmor(int mask) {
      this.itemvisuals$hidden = mask;
   }

   @Override
   public BodyWear.Worn itemvisuals$worn() {
      return this.itemvisuals$worn;
   }

   @Override
   public void itemvisuals$setWorn(BodyWear.Worn worn) {
      this.itemvisuals$worn = worn;
   }
}
