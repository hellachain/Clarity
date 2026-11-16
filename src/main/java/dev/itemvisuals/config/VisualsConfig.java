package dev.itemvisuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.itemvisuals.ItemVisualsClient;
import dev.itemvisuals.share.Share;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

public class VisualsConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   public static VisualsConfig INSTANCE = new VisualsConfig();
   public boolean enabled = true;
   public boolean hideHelmet;
   public boolean hideChestplate;
   public boolean hideLeggings;
   public boolean hideBoots;
   public boolean hideArmorOthers;
   public boolean shareMine = true;
   public boolean showOthers = true;
   public String shareServer;
   public Map<String, Binding> bindings = new LinkedHashMap<>();
   public transient boolean dirty;

   public static Path dir() {
      return FabricLoader.getInstance().getConfigDir().resolve("clarity");
   }

   public static Path itemsDir() {
      return dir().resolve("items");
   }

   private static Path file() {
      return dir().resolve("config.json");
   }

   public static void load() {
      migrateOldFolder();

      try {
         Files.createDirectories(itemsDir());
         if (Files.exists(file())) {
            VisualsConfig c = (VisualsConfig)GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), VisualsConfig.class);
            if (c != null) {
               if (c.bindings == null) {
                  c.bindings = new LinkedHashMap<>();
               }

               INSTANCE = c;
            }
         }
      } catch (Exception var11) {
         ItemVisualsClient.LOG.error("Failed to load Clarity config", var11);
      }
   }

   private static void migrateOldFolder() {
      Path old = FabricLoader.getInstance().getConfigDir().resolve("itemvisuals");
      Path now = dir();
      if (!Files.exists(now) && Files.isDirectory(old)) {
         try {
            Files.move(old, now);
            ItemVisualsClient.LOG.info("Moved config/itemvisuals to config/clarity");
         } catch (IOException var10) {
            try (Stream<Path> walk = Files.walk(old)) {
               for (Path p : (Iterable<Path>)walk::iterator) {
                  Path t = now.resolve(old.relativize(p).toString());
                  if (Files.isDirectory(p)) {
                     Files.createDirectories(t);
                  } else {
                     Files.copy(p, t);
                  }
               }

               ItemVisualsClient.LOG.info("Copied config/itemvisuals to config/clarity");
            } catch (IOException var91) {
               ItemVisualsClient.LOG.error("Could not move the old config/itemvisuals folder", var91);
            }
         }
      }
   }

   public static void save() {
      try {
         Files.createDirectories(dir());
         Files.writeString(file(), GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
         Share.changed();
      } catch (IOException var1) {
         ItemVisualsClient.LOG.error("Failed to save Clarity config", var1);
      }
   }
}
