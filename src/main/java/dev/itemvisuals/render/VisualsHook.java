package dev.itemvisuals.render;

import dev.itemvisuals.ItemKeys;
import dev.itemvisuals.bb.BBModel;
import dev.itemvisuals.config.Binding;
import dev.itemvisuals.config.Placement;
import dev.itemvisuals.config.VisualsConfig;
import dev.itemvisuals.share.Share;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class VisualsHook {
   public static final String[] PLACES = new String[]{"third_person", "first_person", "gui", "ground", "head"};
   public static final String[] PLACE_LABELS = new String[]{"3rd person", "1st person", "Icon", "Dropped", "Head"};
   public static ItemStack previewStack;
   public static Binding previewBinding;
   private static final Map<BBModel, Supplier<Vector3fc[]>> EXTENTS = new WeakHashMap<>();
   private static final ItemTransform TP = t(0.0F, -90.0F, 55.0F, 0.0F, 4.0F, 0.5F, 0.85F);
   private static final ItemTransform FP = t(0.0F, -90.0F, 25.0F, 1.13F, 3.2F, 1.13F, 0.68F);
   private static final ItemTransform GROUND = t(0.0F, 0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 0.5F);
   private static final ItemTransform HEAD = t(0.0F, 180.0F, 0.0F, 0.0F, 13.0F, 7.0F, 1.0F);
   private static final ItemTransform FIXED = t(0.0F, 180.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
   private static final ItemTransform GUI = t(30.0F, 225.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.625F);
   private static final Map<BBModel, Map<ItemDisplayContext, ItemTransform>> DISPLAY_CACHE = new WeakHashMap<>();
   public static boolean armPass;
   private static final Map<BBModel, ItemTransform> ARM_CACHE = new WeakHashMap<>();
   private static final ItemTransform NONE = new ItemTransform(new Vector3f(), new Vector3f(), new Vector3f(1.0F, 1.0F, 1.0F));
   private static final Map<BBModel, ItemTransform[]> USING_CACHE = new WeakHashMap<>();
   private static final Map<BBModel, ItemTransform> BODY_CACHE = new WeakHashMap<>();

   public static String place(ItemDisplayContext ctx) {
      return switch (ctx) {
         case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> "third_person";
         case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> "first_person";
         case HEAD -> "head";
         case GROUND, FIXED, ON_SHELF -> "ground";
         default -> "gui";
      };
   }

   public static Binding binding(ItemStack item, Entity holder) {
      Binding r = Share.find(holder, item);
      return r != null ? r : ItemKeys.find(item);
   }

   public static boolean hasModel(ItemStack item) {
      return hasModel(item, null);
   }

   public static boolean hasModel(ItemStack item, Entity holder) {
      if (VisualsConfig.INSTANCE.enabled && item != null && !item.isEmpty()) {
         Binding b = binding(item, holder);
         return b != null && b.model != null && ModelLibrary.get(b.model) != null;
      } else {
         return false;
      }
   }

   public static boolean apply(ItemStackRenderState output, ItemStack item, ItemDisplayContext ctx, ItemOwner owner) {
      if (!VisualsConfig.INSTANCE.enabled) {
         return false;
      }

      Binding b = previewStack != null && item == previewStack ? previewBinding : binding(item, owner != null ? owner.asLivingEntity() : null);
      if (b != null && b.model != null) {
         BBModel model = ModelLibrary.get(b.model);
         if (model == null) {
            return false;
         }

         float[] deltas = Animator.pose(model, b, item, ctx, owner);
         Matrix4f[] bones = model.boneMatrices(deltas, null);
         LayerRenderState layer = output.newLayer();
         layer.setUsesBlockLight(true);
         LivingEntity living = owner != null ? owner.asLivingEntity() : null;
         boolean using = !armPass
            && living != null
            && living.isUsingItem()
            && living.getUseItem() == item
            && (ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
         layer.setItemTransform(using ? usingTransform(model, ctx) : baseTransform(model, ctx));
         layer.setLocalTransform(localTransform(b.placeOrNull(place(ctx))));
         layer.setExtents(EXTENTS.computeIfAbsent(model, VisualsHook::extents));
         boolean glint = b.glint && item.hasFoil();
         if (glint) {
            layer.setFoilType(FoilType.STANDARD);
         }

         layer.setupSpecialModel(BBItemRenderer.INSTANCE, new BBItemRenderer.Arg(model, bones, glint));
         output.setAnimated();
         output.appendModelIdentityElement(new Object());
         return true;
      } else {
         return false;
      }
   }

   private static Supplier<Vector3fc[]> extents(BBModel m) {
      Vector3fc[] e = new Vector3fc[8];

      for (int i = 0; i < 8; i++) {
         e[i] = new Vector3f((i & 1) == 0 ? m.min[0] : m.max[0], (i & 2) == 0 ? m.min[1] : m.max[1], (i & 4) == 0 ? m.min[2] : m.max[2]);
      }

      return () -> e;
   }

   static Matrix4f localTransform(Placement p) {
      Matrix4f m = new Matrix4f();
      if (p != null && !p.isDefault()) {
         m.translate(0.5F, 0.5F, 0.5F);
         m.translate(p.moveX / 16.0F, p.moveY / 16.0F, p.moveZ / 16.0F);
         m.rotateXYZ((float)Math.toRadians(p.turnX), (float)Math.toRadians(p.turnY), (float)Math.toRadians(p.turnZ));
         float s = p.size / 100.0F;
         m.scale(s, s, s);
         m.translate(-0.5F, -0.5F, -0.5F);
      }

      m.scale(0.0625F);
      return m;
   }

   private static ItemTransform t(float rx, float ry, float rz, float tx, float ty, float tz, float s) {
      return new ItemTransform(new Vector3f(rx, ry, rz), new Vector3f(tx / 16.0F, ty / 16.0F, tz / 16.0F), new Vector3f(s, s, s));
   }

   static ItemTransform usingTransform(BBModel m, ItemDisplayContext ctx) {
      ItemTransform[] u;
      synchronized (USING_CACHE) {
         u = USING_CACHE.computeIfAbsent(m, k -> {
            float[] r = k.display.get("thirdperson_righthand_using");
            float[] l = k.display.get("thirdperson_lefthand_using");
            if (l == null) {
               l = r;
            }

            return new ItemTransform[]{toTransform(r), toTransform(l)};
         });
      }

      ItemTransform t = u[ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? 1 : 0];
      return t != NONE ? t : baseTransform(m, ctx);
   }

   private static ItemTransform toTransform(float[] d) {
      return d == null
         ? NONE
         : new ItemTransform(new Vector3f(d[0], d[1], d[2]), new Vector3f(d[3] / 16.0F, d[4] / 16.0F, d[5] / 16.0F), new Vector3f(d[6], d[7], d[8]));
   }

   static ItemTransform bodyTransform(BBModel m) {
      ItemTransform t;
      synchronized (BODY_CACHE) {
         t = BODY_CACHE.computeIfAbsent(m, k -> toTransform(k.display.get("clarity_body")));
      }

      return t != NONE ? t : baseTransform(m, ItemDisplayContext.HEAD);
   }

   static ItemTransform baseTransform(BBModel m, ItemDisplayContext ctx) {
      if (armPass) {
         ItemTransform a;
         synchronized (ARM_CACHE) {
            a = ARM_CACHE.computeIfAbsent(
               m,
               k -> {
                  float[] d = k.display.containsKey("clarity_arm") ? k.display.get("clarity_arm") : k.display.get("itemvisuals_arm");
                  return d == null
                     ? NONE
                     : new ItemTransform(new Vector3f(d[0], d[1], d[2]), new Vector3f(d[3] / 16.0F, d[4] / 16.0F, d[5] / 16.0F), new Vector3f(d[6], d[7], d[8]));
               }
            );
         }

         if (a != NONE) {
            return a;
         }
      }

      Map<ItemDisplayContext, ItemTransform> cache;
      synchronized (DISPLAY_CACHE) {
         cache = DISPLAY_CACHE.computeIfAbsent(m, k -> new EnumMap<>(ItemDisplayContext.class));
      }

      return cache.computeIfAbsent(ctx, c -> {
         float[] d = m.display.get(c.getSerializedName());
         if (d == null && c == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            d = m.display.get("thirdperson_righthand");
         }

         if (d == null && c == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            d = m.display.get("firstperson_righthand");
         }

         if (d == null && c == ItemDisplayContext.ON_SHELF) {
            d = m.display.get("fixed");
         }

         if (d != null) {
            return new ItemTransform(new Vector3f(d[0], d[1], d[2]), new Vector3f(d[3] / 16.0F, d[4] / 16.0F, d[5] / 16.0F), new Vector3f(d[6], d[7], d[8]));
         }

         return switch (c) {
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> TP;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> FP;
            case HEAD -> HEAD;
            case GROUND -> GROUND;
            case FIXED, ON_SHELF -> FIXED;
            case GUI -> GUI;
            default -> ItemTransform.NO_TRANSFORM;
         };
      });
   }

   private VisualsHook() {
   }
}
