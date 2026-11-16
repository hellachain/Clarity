package dev.itemvisuals.bb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class BBModel {
   public final String name;
   public final List<BBModel.Texture> textures = new ArrayList<>();
   public final List<BBModel.Bone> bones = new ArrayList<>();
   public final LinkedHashMap<String, BBAnimation> animations = new LinkedHashMap<>();
   public final Map<String, float[]> display = new HashMap<>();
   public final float[] min = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
   public final float[] max = new float[]{-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
   public int faceCount;
   public int capeBone = -1;
   public static final String[] PARTS = new String[]{"body", "head", "rightarm", "leftarm", "rightleg", "leftleg"};
   private final float ox;
   private final float oy;
   private final float oz;
   private final boolean javaLike;
   private final float resW;
   private final float resH;
   private static final String[] FACES = new String[]{"north", "south", "east", "west", "up", "down"};

   public boolean usesPart(int part) {
      for (BBModel.Bone b : this.bones) {
         if (b.part == part && !b.quads.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   public static BBModel parse(String name, String json) {
      return new BBModel(name, JsonParser.parseString(json).getAsJsonObject());
   }

   private BBModel(String name, JsonObject root) {
      this.name = name;
      String format = root.has("meta") ? str(root.getAsJsonObject("meta"), "model_format", "free") : "free";
      this.javaLike = format.equals("java_block");
      if (this.javaLike) {
         this.ox = 0.0F;
         this.oy = 0.0F;
         this.oz = 0.0F;
      } else {
         this.ox = 8.0F;
         this.oy = 0.0F;
         this.oz = 8.0F;
      }

      JsonObject res = root.has("resolution") ? root.getAsJsonObject("resolution") : new JsonObject();
      this.resW = (float)num(res, "width", 16.0);
      this.resH = (float)num(res, "height", 16.0);
      if (root.has("textures")) {
         for (JsonElement e : root.getAsJsonArray("textures")) {
            JsonObject t = e.getAsJsonObject();
            String src = str(t, "source", "");
            byte[] png = null;
            int comma = src.indexOf(44);
            if (src.startsWith("data:") && comma > 0) {
               try {
                  png = Base64.getDecoder().decode(src.substring(comma + 1));
               } catch (IllegalArgumentException var18) {
               }
            }

            float uw = (float)num(t, "uv_width", this.resW);
            float uh = (float)num(t, "uv_height", this.resH);
            BBModel.Texture tx = new BBModel.Texture(str(t, "name", "tex" + this.textures.size()), png, uw, uh);
            tx.frameTime = Math.max(1, (int)num(t, "frame_time", 1.0));
            tx.interpolate = t.has("frame_interpolate") && t.get("frame_interpolate").isJsonPrimitive() && t.get("frame_interpolate").getAsBoolean();
            tx.emissive = "emissive".equals(str(t, "render_mode", "default"));
            String sides = str(t, "render_sides", "auto");
            tx.cull = "front".equals(sides) || this.javaLike && !"double".equals(sides);
            String order = str(t, "frame_order", "").trim();
            if (!order.isEmpty()) {
               try {
                  tx.frameOrder = Arrays.stream(order.split("[\\s,]+")).mapToInt(Integer::parseInt).toArray();
               } catch (NumberFormatException var17) {
               }
            }

            this.textures.add(tx);
         }
      }

      Map<String, JsonObject> elements = new LinkedHashMap<>();
      if (root.has("elements")) {
         for (JsonElement e : root.getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            elements.put(str(o, "uuid", UUID.randomUUID().toString()), o);
         }
      }

      Map<String, JsonObject> groups = new HashMap<>();
      if (root.has("groups") && root.get("groups").isJsonArray()) {
         for (JsonElement e : root.getAsJsonArray("groups")) {
            JsonObject g = e.getAsJsonObject();
            groups.put(str(g, "uuid", ""), g);
         }
      }

      BBModel.Bone rootBone = new BBModel.Bone("root", "root", -1, new float[3], new float[3]);
      this.bones.add(rootBone);
      Set<String> used = new HashSet<>();
      if (root.has("outliner")) {
         this.walk(root.getAsJsonArray("outliner"), 0, elements, groups, used);
      }

      for (Entry<String, JsonObject> en : elements.entrySet()) {
         if (!used.contains(en.getKey())) {
            this.addElement(en.getValue(), rootBone);
         }
      }

      if (root.has("animations")) {
         for (JsonElement e : root.getAsJsonArray("animations")) {
            BBAnimation a = BBAnimation.parse(e.getAsJsonObject(), this);
            this.animations.put(a.name, a);
         }
      }

      if (root.has("display") && root.get("display").isJsonObject()) {
         for (Entry<String, JsonElement> enx : root.getAsJsonObject("display").entrySet()) {
            if (enx.getValue().isJsonObject()) {
               JsonObject d = enx.getValue().getAsJsonObject();
               float[] r = vec(d, "rotation", 0.0F);
               float[] tx = vec(d, "translation", 0.0F);
               float[] s = vec(d, "scale", 1.0F);
               this.display.put(enx.getKey(), new float[]{r[0], r[1], r[2], tx[0], tx[1], tx[2], s[0], s[1], s[2]});
            }
         }
      }

      if (this.faceCount == 0) {
         this.min[0] = this.min[1] = this.min[2] = 0.0F;
         this.max[0] = this.max[1] = this.max[2] = 16.0F;
      }
   }

   public int boneIndex(String uuidOrName) {
      for (int i = 0; i < this.bones.size(); i++) {
         if (this.bones.get(i).uuid.equals(uuidOrName)) {
            return i;
         }
      }

      for (int ix = 0; ix < this.bones.size(); ix++) {
         if (this.bones.get(ix).name.equalsIgnoreCase(uuidOrName)) {
            return ix;
         }
      }

      return -1;
   }

   private void walk(JsonArray arr, int parent, Map<String, JsonObject> elements, Map<String, JsonObject> groups, Set<String> used) {
      for (JsonElement e : arr) {
         if (e.isJsonPrimitive()) {
            String id = e.getAsString();
            JsonObject el = elements.get(id);
            if (el != null) {
               used.add(id);
               this.addElement(el, this.bones.get(parent));
            }
         } else if (e.isJsonObject()) {
            JsonObject node = e.getAsJsonObject();
            String id = str(node, "uuid", UUID.randomUUID().toString());
            JsonObject g = groups.getOrDefault(id, node);
            if (g.has("visibility") && !g.get("visibility").getAsBoolean()) {
               this.markUsed(node, used);
            } else {
               float[] o = vec(g, "origin", 0.0F);
               o[0] += this.ox;
               o[1] += this.oy;
               o[2] += this.oz;
               BBModel.Bone b = new BBModel.Bone(id, str(g, "name", "bone"), parent, o, vec(g, "rotation", 0.0F));
               b.lit = b.name.toLowerCase(Locale.ROOT).endsWith("_lit") || parent >= 0 && parent < this.bones.size() && this.bones.get(parent).lit;
               String key = b.name.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
               b.part = parent >= 0 && parent < this.bones.size() ? this.bones.get(parent).part : 0;

               for (int k = 0; k < PARTS.length; k++) {
                  if (key.equals(PARTS[k])) {
                     b.part = k;
                  }
               }

               this.bones.add(b);
               int idx = this.bones.size() - 1;
               if (key.equals("cape") && this.capeBone < 0) {
                  this.capeBone = idx;
               }

               if (node.has("children")) {
                  this.walk(node.getAsJsonArray("children"), idx, elements, groups, used);
               }
            }
         }
      }
   }

   private void markUsed(JsonObject node, Set<String> used) {
      if (node.has("children")) {
         for (JsonElement c : node.getAsJsonArray("children")) {
            if (c.isJsonPrimitive()) {
               used.add(c.getAsString());
            } else if (c.isJsonObject()) {
               this.markUsed(c.getAsJsonObject(), used);
            }
         }
      }
   }

   private void addElement(JsonObject el, BBModel.Bone bone) {
      if ((!el.has("visibility") || el.get("visibility").getAsBoolean()) && (!el.has("export") || el.get("export").getAsBoolean())) {
         String type = str(el, "type", "cube");
         float[] origin = vec(el, "origin", 0.0F);
         origin[0] += this.ox;
         origin[1] += this.oy;
         origin[2] += this.oz;
         float[] rot = this.rotationOf(el);
         Matrix4f m = new Matrix4f().translate(origin[0], origin[1], origin[2]);
         euler(m, rot[0], rot[1], rot[2]);
         m.translate(-origin[0], -origin[1], -origin[2]);
         if (type.equals("cube")) {
            this.addCube(el, bone, m);
         } else if (type.equals("mesh")) {
            this.addMesh(el, bone, m, origin);
         }
      }
   }

   public static Matrix4f euler(Matrix4f m, float x, float y, float z) {
      return m.rotateZ((float)Math.toRadians(z)).rotateY((float)Math.toRadians(y)).rotateX((float)Math.toRadians(x));
   }

   private float[] rotationOf(JsonObject el) {
      JsonElement r = el.get("rotation");
      if (r != null && r.isJsonArray()) {
         return vec(el, "rotation", 0.0F);
      } else if (r != null && r.isJsonObject()) {
         JsonObject o = r.getAsJsonObject();
         float a = (float)num(o, "angle", 0.0);
         String ax = str(o, "axis", "y");
         return new float[]{ax.equals("x") ? a : 0.0F, ax.equals("y") ? a : 0.0F, ax.equals("z") ? a : 0.0F};
      } else {
         return new float[3];
      }
   }

   private void addCube(JsonObject el, BBModel.Bone bone, Matrix4f m) {
      float[] f = vec(el, "from", 0.0F);
      float[] t = vec(el, "to", 0.0F);
      float inf = (float)num(el, "inflate", 0.0);
      float sx = t[0] - f[0];
      float sy = t[1] - f[1];
      float sz = t[2] - f[2];
      float x1;
      float y1;
      float z1;
      float x2;
      float y2;
      float z2;
      if (this.javaLike) {
         float gx = sx < 0.0F ? -inf : inf;
         float gy = sy < 0.0F ? -inf : inf;
         float gz = sz < 0.0F ? -inf : inf;
         x1 = f[0] - gx + this.ox;
         y1 = f[1] - gy + this.oy;
         z1 = f[2] - gz + this.oz;
         x2 = t[0] + gx + this.ox;
         y2 = t[1] + gy + this.oy;
         z2 = t[2] + gz + this.oz;
      } else {
         x1 = Math.min(f[0], t[0]) - inf + this.ox;
         y1 = Math.min(f[1], t[1]) - inf + this.oy;
         z1 = Math.min(f[2], t[2]) - inf + this.oz;
         x2 = Math.max(f[0], t[0]) + inf + this.ox;
         y2 = Math.max(f[1], t[1]) + inf + this.oy;
         z2 = Math.max(f[2], t[2]) + inf + this.oz;
      }

      JsonObject faces = el.has("faces") ? el.getAsJsonObject("faces") : new JsonObject();
      boolean boxUv = el.has("box_uv") && el.get("box_uv").getAsBoolean();
      boolean mirror = el.has("mirror_uv") && el.get("mirror_uv").getAsBoolean();
      float[] uvOff = vec(el, "uv_offset", 0.0F);

      for (String face : FACES) {
         JsonObject fo = faces.has(face) && faces.get(face).isJsonObject() ? faces.getAsJsonObject(face) : null;
         if (fo != null || boxUv) {
            int tex = 0;
            if (fo != null && fo.has("texture")) {
               JsonElement te = fo.get("texture");
               if (te.isJsonNull() || te.isJsonPrimitive() && te.getAsJsonPrimitive().isBoolean()) {
                  continue;
               }

               try {
                  tex = te.getAsInt();
               } catch (Exception var41) {
                  tex = 0;
               }
            }

            float[] uv;
            if (fo != null && fo.has("uv") && !boxUv) {
               uv = vec(fo, "uv", 0.0F);
            } else {
               uv = boxUv(face, uvOff, Math.abs(sx), Math.abs(sy), Math.abs(sz), mirror);
            }

            int uvRot = fo != null ? (int)num(fo, "rotation", 0.0) : 0;

            float[] p = switch (face) {
               case "north" -> new float[]{x2, y2, z1, x1, y2, z1, x1, y1, z1, x2, y1, z1};
               case "south" -> new float[]{x1, y2, z2, x2, y2, z2, x2, y1, z2, x1, y1, z2};
               case "east" -> new float[]{x2, y2, z2, x2, y2, z1, x2, y1, z1, x2, y1, z2};
               case "west" -> new float[]{x1, y2, z1, x1, y2, z2, x1, y1, z2, x1, y1, z1};
               case "up" -> new float[]{x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2};
               default -> new float[]{x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1};
            };
            boolean flipWinding = outwardWinding(face, p);
            this.transform(m, p);
            BBModel.Texture tx = tex >= 0 && tex < this.textures.size() ? this.textures.get(tex) : null;
            float uw = tx != null ? tx.uvW : this.resW;
            float uh = tx != null ? tx.uvH : this.resH;
            float u1 = uv[0] / uw;
            float v1 = uv[1] / uh;
            float u2 = uv[2] / uw;
            float v2 = uv[3] / uh;
            float[] corners = new float[]{u1, v1, u2, v1, u2, v2, u1, v2};
            int steps = (uvRot / 90 % 4 + 4) % 4;
            float[] uvs = new float[8];

            for (int i = 0; i < 4; i++) {
               int src = (i + steps) % 4;
               uvs[i * 2] = corners[src * 2];
               uvs[i * 2 + 1] = corners[src * 2 + 1];
            }

            if (flipWinding) {
               swapVertex(p, uvs);
            }

            this.add(bone, new BBModel.Quad(p, uvs, Math.max(0, Math.min(tex, Math.max(0, this.textures.size() - 1)))));
         }
      }
   }

   private static float[] boxUv(String face, float[] o, float sx, float sy, float sz, boolean mirror) {
      float u = o[0];
      float v = o[1];

      float[] r = switch (face) {
         case "east" -> new float[]{u, v + sz, u + sz, v + sz + sy};
         case "north" -> new float[]{u + sz, v + sz, u + sz + sx, v + sz + sy};
         case "west" -> new float[]{u + sz + sx, v + sz, u + 2.0F * sz + sx, v + sz + sy};
         case "south" -> new float[]{u + 2.0F * sz + sx, v + sz, u + 2.0F * sz + 2.0F * sx, v + sz + sy};
         case "up" -> new float[]{u + sz + sx, v + sz, u + sz, v};
         default -> new float[]{u + sz + 2.0F * sx, v, u + sz + sx, v + sz};
      };
      if (mirror) {
         float t = r[0];
         r[0] = r[2];
         r[2] = t;
      }

      return r;
   }

   private void addMesh(JsonObject el, BBModel.Bone bone, Matrix4f m, float[] origin) {
      if (el.has("vertices") && el.has("faces")) {
         Map<String, float[]> verts = new HashMap<>();

         for (Entry<String, JsonElement> v : el.getAsJsonObject("vertices").entrySet()) {
            JsonArray a = v.getValue().getAsJsonArray();
            verts.put(v.getKey(), new float[]{a.get(0).getAsFloat() + origin[0], a.get(1).getAsFloat() + origin[1], a.get(2).getAsFloat() + origin[2]});
         }

         for (Entry<String, JsonElement> fe : el.getAsJsonObject("faces").entrySet()) {
            JsonObject f = fe.getValue().getAsJsonObject();
            if (f.has("vertices")) {
               JsonElement te = f.get("texture");
               if (te == null || !te.isJsonNull() && !te.getAsJsonPrimitive().isBoolean()) {
                  int tex = te == null ? 0 : te.getAsInt();
                  JsonArray vk = f.getAsJsonArray("vertices");
                  JsonObject uvs = f.has("uv") ? f.getAsJsonObject("uv") : new JsonObject();
                  int n = vk.size();
                  if (n >= 3) {
                     String[] keys = new String[n];

                     for (int i = 0; i < n; i++) {
                        keys[i] = vk.get(i).getAsString();
                     }

                     if (n == 4) {
                        keys = sortQuad(keys, verts);
                     }

                     BBModel.Texture tx = tex >= 0 && tex < this.textures.size() ? this.textures.get(tex) : null;
                     float uw = tx != null ? tx.uvW : this.resW;
                     float uh = tx != null ? tx.uvH : this.resH;
                     int[] order = n == 3 ? new int[]{0, 1, 2, 2} : new int[]{0, 1, 2, 3};
                     float[] p = new float[12];
                     float[] uv = new float[8];

                     for (int i = 0; i < 4; i++) {
                        String k = keys[order[i]];
                        float[] v = verts.get(k);
                        if (v == null) {
                           v = new float[3];
                        }

                        p[i * 3] = v[0];
                        p[i * 3 + 1] = v[1];
                        p[i * 3 + 2] = v[2];
                        JsonArray u = uvs.has(k) ? uvs.getAsJsonArray(k) : null;
                        uv[i * 2] = u != null ? u.get(0).getAsFloat() / uw : 0.0F;
                        uv[i * 2 + 1] = u != null ? u.get(1).getAsFloat() / uh : 0.0F;
                     }

                     this.transform(m, p);
                     this.add(bone, new BBModel.Quad(p, uv, Math.max(0, Math.min(tex, Math.max(0, this.textures.size() - 1)))));
                  }
               }
            }
         }
      }
   }

   private static String[] sortQuad(String[] k, Map<String, float[]> verts) {
      float[] a = verts.get(k[0]);
      float[] b = verts.get(k[1]);
      float[] c = verts.get(k[2]);
      float[] d = verts.get(k[3]);
      if (a != null && b != null && c != null && d != null) {
         Vector3f ab = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
         Vector3f ac = new Vector3f(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
         Vector3f ad = new Vector3f(d[0] - a[0], d[1] - a[1], d[2] - a[2]);
         Vector3f n = new Vector3f(ab).cross(ac);
         Vector3f n2 = new Vector3f(ab).cross(ad);
         if (n.dot(n2) < 0.0F) {
            return new String[]{k[0], k[2], k[1], k[3]};
         }

         Vector3f n3 = new Vector3f(ac).cross(ad);
         return n3.dot(n) < 0.0F ? new String[]{k[0], k[1], k[3], k[2]} : k;
      } else {
         return k;
      }
   }

   private static boolean outwardWinding(String face, float[] p) {
      float ax = p[3] - p[0];
      float ay = p[4] - p[1];
      float az = p[5] - p[2];
      float bx = p[9] - p[0];
      float by = p[10] - p[1];
      float bz = p[11] - p[2];
      float gx = ay * bz - az * by;
      float gy = az * bx - ax * bz;
      float gz = ax * by - ay * bx;

      float d = switch (face) {
         case "north" -> -gz;
         case "south" -> gz;
         case "east" -> gx;
         case "west" -> -gx;
         case "up" -> gy;
         default -> -gy;
      };
      return d > 1.0E-9F;
   }

   private static void swapVertex(float[] p, float[] uv) {
      for (int a = 0; a < 3; a++) {
         float tmp = p[3 + a];
         p[3 + a] = p[9 + a];
         p[9 + a] = tmp;
      }

      for (int a = 0; a < 2; a++) {
         float tmp = uv[2 + a];
         uv[2 + a] = uv[6 + a];
         uv[6 + a] = tmp;
      }
   }

   private void transform(Matrix4f m, float[] p) {
      Vector3f v = new Vector3f();

      for (int i = 0; i < 4; i++) {
         m.transformPosition(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], v);
         p[i * 3] = v.x;
         p[i * 3 + 1] = v.y;
         p[i * 3 + 2] = v.z;
      }
   }

   private void add(BBModel.Bone bone, BBModel.Quad q) {
      bone.quads.add(q);
      this.faceCount++;

      for (int i = 0; i < 4; i++) {
         for (int a = 0; a < 3; a++) {
            float c = q.pos[i * 3 + a];
            if (c < this.min[a]) {
               this.min[a] = c;
            }

            if (c > this.max[a]) {
               this.max[a] = c;
            }
         }
      }
   }

   public Matrix4f[] boneMatrices(float[] d, Matrix4f[] out) {
      if (out == null || out.length != this.bones.size()) {
         out = new Matrix4f[this.bones.size()];

         for (int i = 0; i < out.length; i++) {
            out[i] = new Matrix4f();
         }
      }

      for (int i = 0; i < this.bones.size(); i++) {
         BBModel.Bone b = this.bones.get(i);
         Matrix4f m = out[i];
         if (b.parent >= 0) {
            m.set(out[b.parent]);
         } else {
            m.identity();
         }

         if (i != 0) {
            int k = i * 9;
            float arx = 0.0F;
            float ary = 0.0F;
            float arz = 0.0F;
            float apx = 0.0F;
            float apy = 0.0F;
            float apz = 0.0F;
            float sx = 1.0F;
            float sy = 1.0F;
            float sz = 1.0F;
            if (d != null) {
               arx = d[k];
               ary = d[k + 1];
               arz = d[k + 2];
               apx = d[k + 3];
               apy = d[k + 4];
               apz = d[k + 5];
               sx = d[k + 6];
               sy = d[k + 7];
               sz = d[k + 8];
            }

            m.translate(b.ox - apx, b.oy + apy, b.oz + apz);
            euler(m, b.rx - arx, b.ry - ary, b.rz + arz);
            m.scale(sx, sy, sz);
            m.translate(-b.ox, -b.oy, -b.oz);
         }
      }

      return out;
   }

   public float[] restDeltas() {
      float[] d = new float[this.bones.size() * 9];

      for (int i = 0; i < this.bones.size(); i++) {
         d[i * 9 + 6] = 1.0F;
         d[i * 9 + 7] = 1.0F;
         d[i * 9 + 8] = 1.0F;
      }

      return d;
   }

   static String str(JsonObject o, String k, String d) {
      JsonElement e = o.get(k);
      return e != null && e.isJsonPrimitive() ? e.getAsString() : d;
   }

   static double num(JsonObject o, String k, double d) {
      JsonElement e = o.get(k);
      if (e != null && e.isJsonPrimitive()) {
         try {
            return e.getAsDouble();
         } catch (Exception var6) {
            return d;
         }
      } else {
         return d;
      }
   }

   static float[] vec(JsonObject o, String k, float d) {
      float[] r = new float[]{d, d, d, d};
      JsonElement e = o.get(k);
      if (e != null && e.isJsonArray()) {
         JsonArray a = e.getAsJsonArray();

         for (int i = 0; i < Math.min(4, a.size()); i++) {
            try {
               r[i] = a.get(i).getAsFloat();
            } catch (Exception var9) {
            }
         }
      } else if (e != null && e.isJsonPrimitive()) {
         try {
            float v = e.getAsFloat();
            r[0] = r[1] = r[2] = v;
         } catch (Exception var8) {
         }
      }

      return r;
   }

   public static final class Bone {
      public final String uuid;
      public final String name;
      public final int parent;
      public final float ox;
      public final float oy;
      public final float oz;
      public final float rx;
      public final float ry;
      public final float rz;
      public final List<BBModel.Quad> quads = new ArrayList<>();
      public boolean lit;
      public int part;

      Bone(String uuid, String name, int parent, float[] o, float[] r) {
         this.uuid = uuid;
         this.name = name;
         this.parent = parent;
         this.ox = o[0];
         this.oy = o[1];
         this.oz = o[2];
         this.rx = r[0];
         this.ry = r[1];
         this.rz = r[2];
      }
   }

   public static final class Quad {
      public final float[] pos = new float[12];
      public final float[] uv = new float[8];
      public final float nx;
      public final float ny;
      public final float nz;
      public final int texture;

      Quad(float[] p, float[] uv, int texture) {
         System.arraycopy(p, 0, this.pos, 0, 12);
         System.arraycopy(uv, 0, this.uv, 0, 8);
         this.texture = texture;
         Vector3f a = new Vector3f(p[3] - p[0], p[4] - p[1], p[5] - p[2]);
         Vector3f b = new Vector3f(p[9] - p[0], p[10] - p[1], p[11] - p[2]);
         Vector3f n = a.cross(b);
         if (n.lengthSquared() < 1.0E-12F) {
            a.set(p[6] - p[3], p[7] - p[4], p[8] - p[5]);
            b.set(p[0] - p[3], p[1] - p[4], p[2] - p[5]);
            n = a.cross(b);
         }

         if (n.lengthSquared() > 1.0E-12F) {
            n.normalize();
         } else {
            n.set(0.0F, 1.0F, 0.0F);
         }

         this.nx = n.x;
         this.ny = n.y;
         this.nz = n.z;
      }
   }

   public static final class Texture {
      public final String name;
      public final byte[] png;
      public final float uvW;
      public final float uvH;
      public int frameTime = 1;
      public int[] frameOrder;
      public boolean interpolate;
      public boolean emissive;
      public boolean translucent;
      public boolean cull;
      public Object handle;

      Texture(String name, byte[] png, float uvW, float uvH) {
         this.name = name;
         this.png = png;
         this.uvW = uvW;
         this.uvH = uvH;
      }
   }
}
