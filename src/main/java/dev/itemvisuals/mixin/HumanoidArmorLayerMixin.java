package dev.itemvisuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.itemvisuals.ItemVisualsClient;
import dev.itemvisuals.render.ArmorHider;
import dev.itemvisuals.render.BodyWear;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
   @Inject(
      method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
      at = @At("TAIL")
   )
   private void clarity$wornArmor(
      PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci
   ) {
      if (state instanceof ArmorHider.Holder h) {
         BodyWear.Worn[] wornAll = h.itemvisuals$wornAll();
         if (wornAll != null && ((RenderLayer<?, ?>)(Object)this).getParentModel() instanceof HumanoidModel<?> model) {
            for (BodyWear.Worn worn : wornAll) {
               if (worn != null && !ArmorHider.skip(state, worn.slot())) {
                  try {
                     BodyWear.submit(worn, model, state, poseStack, collector, light);
                  } catch (Throwable ex) {
                     ItemVisualsClient.LOG.error("Clarity armor model failed for slot {}", worn.slot(), ex);
                     h.itemvisuals$setWornAll(null);
                     break;
                  }
               }
            }
         }
      }
   }

   @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
   private void itemvisuals$hidePiece(
      PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack, EquipmentSlot slot, int light, HumanoidRenderState state, CallbackInfo ci
   ) {
      if (ArmorHider.skipArmor(state, slot)) {
         ci.cancel();
      }
   }
}
