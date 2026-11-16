package dev.itemvisuals.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.itemvisuals.ItemVisualsClient;
import dev.itemvisuals.bb.BBModel;
import dev.itemvisuals.config.VisualsConfig;
import dev.itemvisuals.share.Share;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

public final class ModelLibrary {
   private static final Map<String, Optional<BBModel>> MODELS = new HashMap<>();
   private static final List<Identifier> TEXTURES = new ArrayList<>();
   private static final Identifier MISSING = Identifier.withDefaultNamespace("textures/misc/white.png");
   private static final List<ModelLibrary.AnimatedTexture> ANIMATED = new ArrayList<>();
   private static List<String> listing;
   private static int ticks;
   private static final Map<String, ModelLibrary.Info> INFO_CACHE = new HashMap<>();
   private static List<ModelLibrary.Info> infos;
   private static final Set<String> HEAD_DIRS = Set.of(
      "hat",
      "hats",
      "helmet",
      "helmets",
      "head",
      "heads",
      "headwear",
      "cap",
      "caps",
      "mask",
      "masks",
      "crown",
      "crowns",
      "Ñˆлем",
      "Ñˆлемы",
      "ÑˆÐ»ÑÐ¿Ð°",
      "ÑˆÐ»ÑÐ¿Ñ‹",
      "голова"
   );
   private static final Set<String> HAND_DIRS = Set.of(
      "bow",
      "bows",
      "dagger",
      "daggers",
      "sword",
      "swords",
      "wand",
      "wands",
      "staff",
      "staffs",
      "relik",
      "reliks",
      "spear",
      "spears",
      "weapon",
      "weapons",
      "tool",
      "tools",
      "оружие",
      "меч",
      "мечи",
      "лук",
      "луки",
      "Ð¿Ð¾ÑÐ¾Ñ…",
      "Ð¿Ð¾ÑÐ¾Ñ…Ð¸"
   );
   private static int counter;

   public static synchronized void tick() {
      ticks++;

      for (ModelLibrary.AnimatedTexture a : ANIMATED) {
         try {
            a.update(ticks);
         } catch (Exception var3) {
         }
      }
   }

   public static synchronized BBModel get(String relPath) {
      return MODELS.computeIfAbsent(relPath, ModelLibrary::load).orElse(null);
   }

   public static synchronized List<String> list() {
      if (listing == null) {
         Path root = VisualsConfig.itemsDir();

         try (Stream<Path> s = Files.walk(root)) {
            listing = s.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
               .map(p -> root.relativize(p).toString().replace('\\', '/'))
               .sorted()
               .toList();
         } catch (Exception var6) {
            listing = List.of();
         }
      }

      return listing;
   }

   public static synchronized void rescan() {
      listing = null;
      infos = null;
   }

   public static synchronized List<ModelLibrary.Info> infos() {
      if (infos == null) {
         Path root = VisualsConfig.itemsDir();
         List<ModelLibrary.Info> out = new ArrayList<>();

         for (String rel : list()) {
            Path p = root.resolve(rel);
            long mod = 0L;
            long added = 0L;

            try {
               BasicFileAttributes at = Files.readAttributes(p, BasicFileAttributes.class);
               mod = at.lastModifiedTime().toMillis();
               added = Math.max(mod, at.creationTime().toMillis());
            } catch (Exception var14) {
            }

            String key = rel + "@" + mod;
            ModelLibrary.Info inf = INFO_CACHE.get(key);
            if (inf == null || inf.added() != added) {
               int slash = rel.lastIndexOf(47);
               String folder = slash < 0 ? "" : rel.substring(0, slash);
               String name = rel.substring(slash + 1);
               if (name.toLowerCase(Locale.ROOT).endsWith(".bbmodel")) {
                  name = name.substring(0, name.length() - 8);
               }

               inf = new ModelLibrary.Info(rel, name, folder, isHead(folder, p), added);
               INFO_CACHE.put(key, inf);
            }

            out.add(inf);
         }

         infos = out;
      }

      return infos;
   }

   private static boolean isHead(String folder, Path p) {
      String[] parts = folder.toLowerCase(Locale.ROOT).split("/");

      for (int i = parts.length - 1; i >= 0; i--) {
         if (HEAD_DIRS.contains(parts[i])) {
            return true;
         }

         if (HAND_DIRS.contains(parts[i])) {
            return false;
         }
      }

      try {
         String s = Files.readString(p, StandardCharsets.UTF_8);
         Matcher m = Pattern.compile("\"display\"\\s*:\\s*\\{").matcher(s);
         if (!m.find()) {
            return false;
         }

         int open = m.end() - 1;
         int depth = 0;
         int end = -1;

         for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
               depth++;
            } else if (c == '}') {
               if (--depth == 0) {
                  end = i;
                  break;
               }
            }
         }

         if (end < 0) {
            return false;
         }

         JsonObject d = JsonParser.parseString(s.substring(open, end + 1)).getAsJsonObject();
         return d.has("head") && !d.has("firstperson_righthand");
      } catch (Exception var10) {
         return false;
      }
   }

   public static synchronized void reload() {
      TextureManager tm = Minecraft.getInstance().getTextureManager();

      for (Identifier id : TEXTURES) {
         tm.release(id);
      }

      TEXTURES.clear();

      for (ModelLibrary.AnimatedTexture a : ANIMATED) {
         a.strip.close();
      }

      ANIMATED.clear();
      MODELS.clear();
      listing = null;
      infos = null;
      INFO_CACHE.clear();
      Animator.clear();
   }

   private static Optional<BBModel> load(String rel) {
      boolean shared = rel.startsWith("@r/");
      Path root = shared ? Share.dir() : VisualsConfig.itemsDir();
      Path p = root.resolve(shared ? rel.substring(3) : rel).normalize();
      if (p.startsWith(root) && Files.isRegularFile(p)) {
         try {
            BBModel m = BBModel.parse(rel, Files.readString(p, StandardCharsets.UTF_8));
            if (!shared || m.faceCount <= 30000 && !(m.max[0] - m.min[0] > 192.0F) && !(m.max[1] - m.min[1] > 192.0F) && !(m.max[2] - m.min[2] > 192.0F)) {
               TextureManager tm = Minecraft.getInstance().getTextureManager();

               for (BBModel.Texture t : m.textures) {
                  if (t.png == null) {
                     t.handle = MISSING;
                  } else {
                     try {
                        NativeImage img = NativeImage.read(t.png);
                        if (shared && (long)img.getWidth() * img.getHeight() > 8388608L) {
                           img.close();
                           t.handle = MISSING;
                        } else {
                           Identifier id = Identifier.fromNamespaceAndPath("clarity", "dynamic/" + counter++);
                           String label = rel + "#" + t.name;
                           int w = img.getWidth();
                           int h = img.getHeight();
                           t.translucent = false;

                           for (int y = 0; y < h && !t.translucent; y++) {
                              for (int x = 0; x < w; x++) {
                                 int a = img.getPixel(x, y) >>> 24;
                                 if (a >= 8 && a < 248) {
                                    t.translucent = true;
                                    break;
                                 }
                              }
                           }

                           if (t.emissive) {
                              for (int y = 0; y < h; y++) {
                                 for (int xx = 0; xx < w; xx++) {
                                    if (img.getPixel(xx, y) >>> 24 < 8) {
                                       img.setPixel(xx, y, 0);
                                    }
                                 }
                              }
                           }

                           int fh = t.uvW > 0.0F ? Math.round(w * t.uvH / t.uvW) : w;
                           if (fh > 0 && h > fh && h % fh == 0) {
                              int frames = h / fh;
                              int[] order = t.frameOrder;
                              if (order == null || order.length == 0) {
                                 order = new int[frames];
                                 int i = 0;

                                 while (i < frames) {
                                    order[i] = i++;
                                 }
                              }

                              for (int i = 0; i < order.length; i++) {
                                 order[i] = Math.floorMod(order[i], frames);
                              }

                              NativeImage frame = new NativeImage(w, fh, false);

                              for (int y = 0; y < fh; y++) {
                                 for (int xxx = 0; xxx < w; xxx++) {
                                    frame.setPixel(xxx, y, img.getPixel(xxx, order[0] * fh + y));
                                 }
                              }

                              DynamicTexture dt = new DynamicTexture(() -> label, frame);
                              tm.register(id, dt);
                              ANIMATED.add(new ModelLibrary.AnimatedTexture(img, dt, w, fh, order, t.frameTime, t.interpolate));
                           } else {
                              tm.register(id, new DynamicTexture(() -> label, img));
                           }

                           TEXTURES.add(id);
                           t.handle = id;
                        }
                     } catch (Exception var19) {
                        ItemVisualsClient.LOG.warn("Bad texture {} in {}", new Object[]{t.name, rel, var19});
                        t.handle = MISSING;
                     }
                  }
               }

               if (m.textures.isEmpty()) {
                  ItemVisualsClient.LOG.warn("{} has no embedded textures", rel);
               }

               ItemVisualsClient.LOG.info("Loaded item model {} ({} faces, {} animations)", new Object[]{rel, m.faceCount, m.animations.size()});
               return Optional.of(m);
            } else {
               ItemVisualsClient.LOG.warn("Shared model {} is too big ({} faces), not shown", rel, m.faceCount);
               return Optional.empty();
            }
         } catch (Exception var20) {
            ItemVisualsClient.LOG.error("Failed to load {}", rel, var20);
            return Optional.empty();
         }
      } else {
         return Optional.empty();
      }
   }

   static Identifier texture(BBModel m, int idx) {
      if (idx >= 0 && idx < m.textures.size()) {
         return m.textures.get(idx).handle instanceof Identifier id ? id : MISSING;
      } else {
         return MISSING;
      }
   }

   private ModelLibrary() {
   }

   private static final class AnimatedTexture {
      final NativeImage strip;
      final DynamicTexture tex;
      final int w;
      final int h;
      final int[] order;
      final int frameTime;
      final boolean interpolate;
      int lastKey = -1;

      AnimatedTexture(NativeImage strip, DynamicTexture tex, int w, int h, int[] order, int frameTime, boolean interpolate) {
         this.strip = strip;
         this.tex = tex;
         this.w = w;
         this.h = h;
         this.order = order;
         this.frameTime = frameTime;
         this.interpolate = interpolate;
      }

      void update(int tick) {
         int total = this.order.length * this.frameTime;
         int pos = Math.floorMod(tick, total);
         int idx = pos / this.frameTime;
         int sub = pos % this.frameTime;
         int key = this.interpolate ? pos : idx;
         if (key != this.lastKey) {
            this.lastKey = key;
            int a = this.order[idx];
            int b = this.order[(idx + 1) % this.order.length];
            float t = this.interpolate && a != b ? (float)sub / this.frameTime : 0.0F;
            NativeImage dst = this.tex.getPixels();
            if (dst != null) {
               for (int y = 0; y < this.h; y++) {
                  for (int x = 0; x < this.w; x++) {
                     int c1 = this.strip.getPixel(x, a * this.h + y);
                     if (t > 0.0F) {
                        c1 = mix(c1, this.strip.getPixel(x, b * this.h + y), t);
                     }

                     dst.setPixel(x, y, c1);
                  }
               }

               this.tex.upload();
            }
         }
      }

      static int mix(int c1, int c2, float t) {
         int r = 0;

         for (int sh = 0; sh < 32; sh += 8) {
            int x = c1 >>> sh & 0xFF;
            int y = c2 >>> sh & 0xFF;
            r |= (Math.round(x + (y - x) * t) & 0xFF) << sh;
         }

         return r;
      }
   }

   public record Info(String path, String name, String folder, boolean head, long added) {
   }
}
