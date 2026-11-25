package dev.itemvisuals.mixin;

import dev.itemvisuals.render.ArmorHider;
import dev.itemvisuals.render.BodyWear;
import dev.itemvisuals.render.VisualsHook;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
   @Shadow
   @Final
   protected ItemModelResolver itemModelResolver;

   @Inject(
      method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
      at = @At("TAIL")
   )
   private void clarity$hat(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
      if (state instanceof ArmorHider.Holder h) {
         h.itemvisuals$setHiddenArmor(ArmorHider.mask(entity));
         h.itemvisuals$setWornAll(BodyWear.extractAll(entity));
      }

      ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
      if (VisualsHook.hasModel(head, entity)) {
         state.wornHeadType = null;
         state.wornHeadProfile = null;
         this.itemModelResolver.updateForLiving(state.headItem, head, ItemDisplayContext.HEAD, entity);
      }
   }
}
