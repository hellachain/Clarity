package dev.itemvisuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.itemvisuals.render.ArmorHider;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {
   @Inject(
      method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void clarity$hideWings(
      PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci
   ) {
      if (state instanceof ArmorHider.Holder h && h.itemvisuals$worn() != null && !ArmorHider.skip(state, EquipmentSlot.CHEST)) {
         ci.cancel();
      }
   }
}
