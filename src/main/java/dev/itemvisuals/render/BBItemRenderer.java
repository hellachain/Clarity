package dev.itemvisuals.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import dev.itemvisuals.bb.BBModel;
import java.util.function.Consumer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class BBItemRenderer implements SpecialModelRenderer<BBItemRenderer.Arg> {
   public static final BBItemRenderer INSTANCE = new BBItemRenderer();
   private static final int FULL_BRIGHT = 15728880;
   private static final float[] HALO = new float[]{0.18F, 0.42F};
   private static final int[] HALO_COL = new int[]{1442840575, 721420287};

   public void submit(BBItemRenderer.Arg arg, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean hasFoil, int outlineColor) {
      if (arg != null) {
         BBModel m = arg.model();
         int texCount = Math.max(1, m.textures.size());

         for (int t = 0; t < texCount; t++) {
            int tex = t;
            if (usesTexture(m, tex, arg.part())) {
               boolean glow = tex < m.textures.size() && m.textures.get(tex).emissive;
               boolean mixed = glow && usesLit(m, tex);
               int l = glow ? 15728880 : light;
               boolean translucent = tex < m.textures.size() && m.textures.get(tex).translucent;
               OrderedSubmitNodeCollector pass = collector.order(translucent ? 2 : (glow ? 1 : 0));
               boolean emissiveOnly = glow && !mixed;
               boolean cull = !emissiveOnly && tex < m.textures.size() && m.textures.get(tex).cull;
               Identifier id = ModelLibrary.texture(m, tex);
               RenderType type = emissiveOnly
                  ? RenderTypes.entityTranslucentEmissive(id)
                  : (
                     translucent
                        ? (cull ? RenderTypes.entityTranslucentCullItemTarget(id) : RenderTypes.entityTranslucent(id))
                        : (cull ? RenderTypes.entityCutoutCull(id) : RenderTypes.entityCutout(id))
                  );
               pass.submitCustomGeometry(poseStack, type, (pose, buf) -> emit(m, arg.bones(), arg.part(), tex, pose, buf, l, light, overlay, 0.0F, -1, cull));
               if (glow) {
                  for (int k = 0; k < HALO.length; k++) {
                     float push = HALO[k];
                     int col = HALO_COL[k];
                     pass.submitCustomGeometry(
                        poseStack,
                        RenderTypes.eyes(ModelLibrary.texture(m, tex)),
                        (pose, buf) -> emit(m, arg.bones(), arg.part(), tex, pose, buf, 15728880, 15728880, overlay, push, col, false)
                     );
                  }
               }
            }
         }

         if (arg.glint()) {
            collector.submitCustomGeometry(
               poseStack, RenderTypes.entityGlint(), (pose, buf) -> emit(m, arg.bones(), arg.part(), -1, pose, buf, light, light, overlay, 0.0F, -1, false)
            );
         }
      }
   }

   private static boolean usesLit(BBModel m, int tex) {
      for (BBModel.Bone b : m.bones) {
         if (b.lit) {
            for (BBModel.Quad q : b.quads) {
               if (q.texture == tex) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean usesTexture(BBModel m, int tex, int part) {
      for (BBModel.Bone b : m.bones) {
         if (part < 0 || b.part == part) {
            for (BBModel.Quad q : b.quads) {
               if (q.texture == tex) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static void emit(
      BBModel m,
      Matrix4f[] bones,
      int part,
      int tex,
      Pose pose,
      VertexConsumer buf,
      int light,
      int worldLight,
      int overlay,
      float push,
      int color,
      boolean outward
   ) {
      Vector3f p = new Vector3f();
      Vector3f n = new Vector3f();

      for (int bi = 0; bi < m.bones.size(); bi++) {
         BBModel.Bone b = m.bones.get(bi);
         if (!b.quads.isEmpty() && (push == 0.0F || !b.lit) && (part < 0 || b.part == part)) {
            Matrix4f bm = bones[bi];
            int ql = b.lit ? worldLight : light;
            boolean reverse = outward && bm.determinant3x3() >= 0.0F;

            for (BBModel.Quad q : b.quads) {
               if (tex < 0 || q.texture == tex) {
                  bm.transformDirection(q.nx, q.ny, q.nz, n);
                  if (n.lengthSquared() > 1.0E-12F) {
                     n.normalize();
                  }

                  if (outward) {
                     n.negate();
                  }

                  for (int k = 0; k < 4; k++) {
                     int v = reverse ? 4 - k & 3 : k;
                     bm.transformPosition(q.pos[v * 3], q.pos[v * 3 + 1], q.pos[v * 3 + 2], p);
                     if (push != 0.0F) {
                        p.fma(push, n);
                     }

                     buf.addVertex(pose, p.x, p.y, p.z)
                        .setColor(color)
                        .setUv(q.uv[v * 2], q.uv[v * 2 + 1])
                        .setOverlay(overlay)
                        .setLight(ql)
                        .setNormal(pose, n.x, n.y, n.z);
                  }
               }
            }
         }
      }
   }

   public void getExtents(Consumer<Vector3fc> output) {
   }

   public BBItemRenderer.Arg extractArgument(ItemStack stack) {
      return null;
   }

   private BBItemRenderer() {
   }

   public record Arg(BBModel model, Matrix4f[] bones, boolean glint, int part) {
      public Arg(BBModel model, Matrix4f[] bones, boolean glint) {
         this(model, bones, glint, -1);
      }
   }
}
