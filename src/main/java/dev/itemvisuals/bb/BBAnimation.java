package dev.itemvisuals.bb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;

public final class BBAnimation {
   public final String name;
   public final BBAnimation.Loop loop;
   public final float length;
   final List<BBAnimation.BoneTracks> tracks = new ArrayList<>();

   private BBAnimation(String name, BBAnimation.Loop loop, float length) {
      this.name = name;
      this.loop = loop;
      this.length = length;
   }

   static BBAnimation parse(JsonObject o, BBModel model) {
      String loopS = BBModel.str(o, "loop", "once");

      BBAnimation.Loop loop = switch (loopS) {
         case "loop" -> BBAnimation.Loop.LOOP;
         case "hold" -> BBAnimation.Loop.HOLD;
         default -> BBAnimation.Loop.ONCE;
      };
      float len = (float)BBModel.num(o, "length", 0.0);
      BBAnimation a = new BBAnimation(BBModel.str(o, "name", "animation"), loop, len);
      float maxT = 0.0F;
      if (o.has("animators") && o.get("animators").isJsonObject()) {
         for (Entry<String, JsonElement> en : o.getAsJsonObject("animators").entrySet()) {
            JsonObject an = en.getValue().getAsJsonObject();
            String type = BBModel.str(an, "type", "bone");
            if (type.equals("bone")) {
               int bone = model.boneIndex(en.getKey());
               if (bone < 0) {
                  bone = model.boneIndex(BBModel.str(an, "name", ""));
               }

               if (bone >= 0 && an.has("keyframes")) {
                  BBAnimation.BoneTracks bt = new BBAnimation.BoneTracks();
                  bt.bone = bone;

                  for (JsonElement ke : an.getAsJsonArray("keyframes")) {
                     JsonObject k = ke.getAsJsonObject();
                     String ch = BBModel.str(k, "channel", "rotation");
                     BBAnimation.Track tr;
                     switch (ch) {
                        case "rotation":
                           tr = bt.rot == null ? (bt.rot = new BBAnimation.Track()) : bt.rot;
                           break;
                        case "position":
                           tr = bt.pos == null ? (bt.pos = new BBAnimation.Track()) : bt.pos;
                           break;
                        case "scale":
                           tr = bt.scale == null ? (bt.scale = new BBAnimation.Track()) : bt.scale;
                           break;
                        default:
                           continue;
                     }

                     BBAnimation.Key key = new BBAnimation.Key();
                     key.time = (float)BBModel.num(k, "time", 0.0);
                     key.interp = BBModel.str(k, "interpolation", "linear");
                     JsonArray dps = k.has("data_points") ? k.getAsJsonArray("data_points") : new JsonArray();
                     JsonObject first = dps.size() > 0 ? dps.get(0).getAsJsonObject() : new JsonObject();
                     JsonObject last = dps.size() > 1 ? dps.get(1).getAsJsonObject() : first;
                     String[] ax = new String[]{"x", "y", "z"};

                     for (int i = 0; i < 3; i++) {
                        key.pre[i] = Molang.parse(val(first, ax[i]));
                        key.post[i] = Molang.parse(val(last, ax[i]));
                     }

                     tr.keys.add(key);
                     maxT = Math.max(maxT, key.time);
                  }

                  for (BBAnimation.Track t : new BBAnimation.Track[]{bt.rot, bt.pos, bt.scale}) {
                     if (t != null) {
                        t.keys.sort(Comparator.comparingDouble(x -> x.time));
                     }
                  }

                  a.tracks.add(bt);
               }
            }
         }
      }

      return a.length <= 0.0F ? new BBAnimation(a.name, a.loop, Math.max(maxT, 0.05F)).copyTracks(a) : a;
   }

   private BBAnimation copyTracks(BBAnimation o) {
      this.tracks.addAll(o.tracks);
      return this;
   }

   private static Object val(JsonObject o, String k) {
      JsonElement e = o.get(k);
      if (e != null && !e.isJsonNull()) {
         return e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : e.getAsString();
      } else {
         return 0;
      }
   }

   public boolean finished(float t) {
      return this.loop == BBAnimation.Loop.ONCE && t > this.length;
   }

   public void sample(float time, float lifeTime, float[] out) {
      float t = switch (this.loop) {
         case ONCE -> time;
         case LOOP -> this.length > 0.0F ? time % this.length : 0.0F;
         case HOLD -> Math.min(time, this.length);
      };
      if (this.loop != BBAnimation.Loop.ONCE || !(time > this.length)) {
         Molang.Ctx ctx = new Molang.Ctx();
         ctx.animTime = t;
         ctx.lifeTime = lifeTime;
         float[] tmp = new float[3];

         for (BBAnimation.BoneTracks bt : this.tracks) {
            int b = bt.bone * 9;
            if (bt.rot != null && eval(bt.rot, t, ctx, tmp)) {
               out[b] += tmp[0];
               out[b + 1] = out[b + 1] + tmp[1];
               out[b + 2] = out[b + 2] + tmp[2];
            }

            if (bt.pos != null && eval(bt.pos, t, ctx, tmp)) {
               out[b + 3] = out[b + 3] + tmp[0];
               out[b + 4] = out[b + 4] + tmp[1];
               out[b + 5] = out[b + 5] + tmp[2];
            }

            if (bt.scale != null && eval(bt.scale, t, ctx, tmp)) {
               out[b + 6] = out[b + 6] * tmp[0];
               out[b + 7] = out[b + 7] * tmp[1];
               out[b + 8] = out[b + 8] * tmp[2];
            }
         }
      }
   }

   private static boolean eval(BBAnimation.Track tr, float t, Molang.Ctx ctx, float[] out) {
      List<BBAnimation.Key> k = tr.keys;
      if (k.isEmpty()) {
         return false;
      }

      if (k.size() != 1 && !(t <= k.get(0).time)) {
         BBAnimation.Key last = k.get(k.size() - 1);
         if (t >= last.time) {
            read(last.post, ctx, out);
            return true;
         }

         int i = 0;

         while (i < k.size() - 1 && k.get(i + 1).time <= t) {
            i++;
         }

         BBAnimation.Key a = k.get(i);
         BBAnimation.Key b = k.get(i + 1);
         float span = b.time - a.time;
         float f = span <= 0.0F ? 1.0F : (t - a.time) / span;
         float[] va = new float[3];
         float[] vb = new float[3];
         read(a.post, ctx, va);
         read(b.pre, ctx, vb);
         if (a.interp.equals("step")) {
            System.arraycopy(va, 0, out, 0, 3);
            return true;
         }

         if (!a.interp.equals("catmullrom") && !b.interp.equals("catmullrom")) {
            if (a.interp.equals("bezier") || b.interp.equals("bezier")) {
               f = f * f * (3.0F - 2.0F * f);
            }

            for (int j = 0; j < 3; j++) {
               out[j] = va[j] + (vb[j] - va[j]) * f;
            }

            return true;
         } else {
            float[] v0 = new float[3];
            float[] v3 = new float[3];
            if (i > 0) {
               read(k.get(i - 1).post, ctx, v0);
            } else {
               System.arraycopy(va, 0, v0, 0, 3);
            }

            if (i + 2 < k.size()) {
               read(k.get(i + 2).pre, ctx, v3);
            } else {
               System.arraycopy(vb, 0, v3, 0, 3);
            }

            for (int j = 0; j < 3; j++) {
               out[j] = catmull(v0[j], va[j], vb[j], v3[j], f);
            }

            return true;
         }
      } else {
         read(k.get(0).pre, ctx, out);
         return true;
      }
   }

   private static float catmull(float p0, float p1, float p2, float p3, float t) {
      float t2 = t * t;
      float t3 = t2 * t;
      return 0.5F * (2.0F * p1 + (-p0 + p2) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
   }

   private static void read(Molang.Expr[] e, Molang.Ctx c, float[] out) {
      for (int i = 0; i < 3; i++) {
         out[i] = (float)e[i].eval(c);
      }
   }

   static final class BoneTracks {
      int bone;
      BBAnimation.Track rot;
      BBAnimation.Track pos;
      BBAnimation.Track scale;
   }

   static final class Key {
      float time;
      String interp;
      Molang.Expr[] pre = new Molang.Expr[3];
      Molang.Expr[] post = new Molang.Expr[3];
   }

   public enum Loop {
      ONCE,
      LOOP,
      HOLD;
   }

   static final class Track {
      final List<BBAnimation.Key> keys = new ArrayList<>();
   }
}
