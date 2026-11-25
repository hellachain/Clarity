package dev.itemvisuals.render;

import dev.itemvisuals.bb.BBAnimation;
import dev.itemvisuals.bb.BBModel;
import dev.itemvisuals.config.Binding;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class Animator {
   private static final Map<String, Animator.State> STATES = new HashMap<>();
   private static final long START = Util.getMillis();

   public static void clear() {
      synchronized (STATES) {
         STATES.clear();
      }
   }

   public static void cleanup() {
      long now = Util.getMillis();
      synchronized (STATES) {
         Iterator<Animator.State> it = STATES.values().iterator();

         while (it.hasNext()) {
            if (now - it.next().lastSeen > 10000L) {
               it.remove();
            }
         }
      }
   }

   public static float[] pose(BBModel model, Binding b, ItemStack stack, ItemDisplayContext ctx, ItemOwner owner) {
      long now = Util.getMillis();
      LivingEntity living = owner != null ? owner.asLivingEntity() : null;
      boolean hand = ctx.firstPerson() || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
      String key = (living != null && hand ? "e" + living.getId() + (ctx.leftHand() ? "L" : "R") : "s:" + ctx.name()) + "|" + model.name;
      Animator.State s;
      synchronized (STATES) {
         s = STATES.computeIfAbsent(key, k -> new Animator.State());
      }

      s.lastSeen = now;
      String desired = b.animAlways;
      if (living != null && hand) {
         boolean swingingThis = living.swinging && living.swingingArm != null && living.getItemInHand(living.swingingArm) == stack;
         if (swingingThis && (!s.wasSwinging || living.swingTime < s.lastSwingTime)) {
            s.swingStart = now;
         }

         s.wasSwinging = swingingThis;
         s.lastSwingTime = living.swingTime;
         BBAnimation swing = anim(model, b.animSwing);
         BBAnimation release = anim(model, b.animRelease);
         boolean held = rightClickHeld(living, stack);
         if (s.wasHeld && !held && release != null) {
            s.releaseStart = now;
         }

         s.wasHeld = held;
         if (swing != null && s.swingStart >= 0L && (float)(now - s.swingStart) < swing.length * 1000.0F && s.swingStart >= s.releaseStart) {
            desired = b.animSwing;
         } else if (release != null && s.releaseStart >= 0L && !held && (float)(now - s.releaseStart) < release.length * 1000.0F) {
            desired = b.animRelease;
         } else if (held) {
            desired = b.animRightClick;
         }
      }

      if (desired == null) {
         desired = "none";
      }

      int delay = Math.max(0, b.switchDelay);
      if (!desired.equals(s.current)) {
         boolean swingNow = (desired.equals(b.animSwing) || desired.equals(b.animRelease)) && !desired.equals("none");
         if (delay == 0 || s.current.isEmpty() || swingNow && !"hold".equals(b.switchMode)) {
            switchTo(s, desired, now, "smooth".equals(b.switchMode) && !s.current.isEmpty() ? Math.min(delay, 150) : 0);
         } else if ("hold".equals(b.switchMode)) {
            if (!desired.equals(s.pending)) {
               s.pending = desired;
               s.pendingSince = now;
            }

            if (now - s.pendingSince >= delay) {
               switchTo(s, desired, now, 0);
            }
         } else {
            switchTo(s, desired, now, delay);
         }
      } else {
         s.pending = null;
         if (desired.equals(b.animSwing) && s.swingStart > s.currentStart) {
            s.currentStart = s.swingStart;
         }
      }

      float life = (float)(now - START) / 1000.0F;
      float[] out = model.restDeltas();
      BBAnimation cur = anim(model, s.current);
      float w = 1.0F;
      if (s.prev != null && s.blendMs > 0) {
         w = Math.min(1.0F, (float)(now - s.blendStart) / s.blendMs);
         if (w >= 1.0F) {
            s.prev = null;
         }
      }

      if (s.prev != null && w < 1.0F) {
         float[] a = model.restDeltas();
         float[] p = model.restDeltas();
         if (cur != null) {
            cur.sample((float)(now - s.currentStart) / 1000.0F, life, a);
         }

         BBAnimation pa = anim(model, s.prev);
         if (pa != null) {
            pa.sample((float)(now - s.prevStart) / 1000.0F, life, p);
         }

         for (int i = 0; i < out.length; i++) {
            out[i] = p[i] + (a[i] - p[i]) * w;
         }
      } else if (cur != null) {
         cur.sample((float)(now - s.currentStart) / 1000.0F, life, out);
      }

      return out;
   }

   private static void switchTo(Animator.State s, String anim, long now, int blend) {
      if (blend > 0 && !s.current.isEmpty()) {
         s.prev = s.current;
         s.prevStart = s.currentStart;
         s.blendStart = now;
         s.blendMs = blend;
      } else {
         s.prev = null;
      }

      s.current = anim;
      s.currentStart = now;
      s.pending = null;
   }

   private static boolean rightClickHeld(LivingEntity living, ItemStack stack) {
      if (living.isUsingItem() && living.getUseItem() == stack) {
         return true;
      }

      Minecraft mc = Minecraft.getInstance();
      return living == mc.player && mc.gui.screen() == null && mc.options.keyUse.isDown() && mc.player.getMainHandItem() == stack;
   }

   static BBAnimation anim(BBModel m, String name) {
      return name != null && !name.equals("none") ? m.animations.get(name) : null;
   }

   private Animator() {
   }

   private static final class State {
      String current = "";
      long currentStart;
      String prev;
      long prevStart;
      long blendStart;
      int blendMs;
      String pending;
      long pendingSince;
      boolean wasSwinging;
      int lastSwingTime;
      long swingStart = -1L;
      boolean wasHeld;
      long releaseStart = -1L;
      long lastSeen;
      String model;
   }
}
