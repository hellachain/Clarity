package dev.itemvisuals.mixin;

import dev.itemvisuals.render.ArmorHider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidMobRendererMixin {
   @Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
   private static void itemvisuals$armorMask(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver resolver, CallbackInfo ci) {
      ((ArmorHider.Holder)state).itemvisuals$setHiddenArmor(ArmorHider.mask(entity));
   }
}
