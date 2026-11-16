package dev.itemvisuals.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.itemvisuals.bb.BBModel;
import dev.itemvisuals.config.Binding;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

public final class BodyWear {
   public static final boolean ENABLED = false;

   public static BodyWear.Worn extract(LivingEntity entity) {
      return null;
   }

   public static void submit(BodyWear.Worn w, HumanoidModel<?> model, HumanoidRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
      BBModel m = w.model();
      float[] d = w.deltas();
      if (m.capeBone > 0) {
         float lift = state.isCrouching ? 25.0F : 0.0F;
         float side = 0.0F;
         if (state instanceof AvatarRenderState a) {
            lift += a.capeLean / 2.0F + a.capeFlap;
            side = a.capeLean2 / 2.0F;
         }

         if (lift != 0.0F || side != 0.0F) {
            d = (float[])d.clone();
            int k = m.capeBone * 9;
            d[k] += Math.max(-4.0F, lift);
            d[k + 2] = d[k + 2] + side;
         }
      }

      Matrix4f[] bones = m.boneMatrices(d, null);
      ItemTransform display = VisualsHook.bodyTransform(m);
      Matrix4f local = VisualsHook.localTransform(w.binding().placeOrNull("body"));

      for (int part = 0; part < BBModel.PARTS.length; part++) {
         if (m.usesPart(part)) {
            ModelPart mp = switch (part) {
               case 0 -> model.body;
               case 1 -> model.head;
               case 2 -> model.rightArm;
               case 3 -> model.leftArm;
               case 4 -> model.rightLeg;
               default -> model.leftLeg;
            };
            if (mp.visible) {
               poseStack.pushPose();
               model.root().translateAndRotate(poseStack);
               mp.translateAndRotate(poseStack);
               PartPose rest = mp.getInitialPose();
               poseStack.translate(-rest.x() / 16.0F, -rest.y() / 16.0F, -rest.z() / 16.0F);
               poseStack.translate(0.0F, -0.25F, 0.0F);
               poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
               poseStack.scale(0.625F, -0.625F, -0.625F);
               display.apply(false, poseStack.last());
               poseStack.last().mulPose(local);
               BBItemRenderer.INSTANCE
                  .submit(
                     new BBItemRenderer.Arg(m, bones, w.glint(), part), poseStack, collector, light, OverlayTexture.NO_OVERLAY, w.glint(), state.outlineColor
                  );
               poseStack.popPose();
            }
         }
      }
   }

   private BodyWear() {
   }

   public record Worn(BBModel model, float[] deltas, Binding binding, boolean glint) {
   }
}
