package dev.itemvisuals.share;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.itemvisuals.ItemKeys;
import dev.itemvisuals.ItemVisualsClient;
import dev.itemvisuals.config.Binding;
import dev.itemvisuals.config.Placement;
import dev.itemvisuals.config.VisualsConfig;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.item.ItemStack;

public final class Share {
   public static final String DEFAULT_SERVER = "https://clarity-share.lokniksquarez.workers.dev";
   private static final boolean SELF = Boolean.getBoolean("clarity.share.self");
   private static final boolean OFFLINE = Boolean.getBoolean("clarity.share.offlineTest");
   private static final int MAX_MODELS = 64;
   private static final int MAX_RAW = 6291456;
   private static final int MAX_B64 = 1900000;
   private static final String CERTS = System.getProperty("clarity.share.certUrl", "https://api.minecraftservices.com/player/certificates");
   private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");
   private static final Gson GSON = new Gson();
   private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Clarity share");
      t.setDaemon(true);
      return t;
   });
   private static final HttpClient HTTP = HttpClient.newBuilder()
      .version(Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(8L))
      .followRedirects(Redirect.NORMAL)
      .build();
   private static volatile Map<String, String> index = Map.of();
   private static final Map<String, Share.Remote> REMOTE = new ConcurrentHashMap<>();
   private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
   private static final Map<Path, Share.Hashed> HASHES = new ConcurrentHashMap<>();
   private static volatile String status = "";
   private static volatile String lastError = "";
   private static volatile String uploadedV;
   private static volatile String token;
   private static volatile String keyProblem = "";
   private static volatile long tokenExp;
   private static volatile long indexAt = -1L;
   private static volatile long dirtyAt = -1L;
   private static volatile long keyRetryAt;
   private static volatile boolean firstIndex = true;
   private static volatile int sharedCount;
   private static final EquipmentSlot[] LIVE_SLOTS = new EquipmentSlot[]{
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private static final String[] LIVE_NAMES = new String[]{"mainhand", "offhand", "head", "chest", "legs", "feet"};
   private static final ExecutorService LIVE_IO = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Clarity live");
      t.setDaemon(true);
      return t;
   });
   private static volatile Map<String, Map<String, String>> live = Map.of();
   private static String liveMine;
   private static volatile String liveSent;
   private static volatile boolean liveBusy;
   private static volatile boolean livePolling;
   private static long liveCheckAt;
   private static long liveChangedAt;
   private static long liveSentAt;
   private static volatile long liveRetryAt;
   private static volatile long livePollAt;

   private Share() {
   }

   public static String server() {
      String s = System.getProperty("clarity.share.server");
      if (s == null || s.isBlank()) {
         s = VisualsConfig.INSTANCE.shareServer;
      }

      if (s == null || s.isBlank()) {
         s = "https://clarity-share.lokniksquarez.workers.dev";
      }

      s = s.trim();

      while (s.endsWith("/")) {
         s = s.substring(0, s.length() - 1);
      }

      return s;
   }

   public static boolean active() {
      return VisualsConfig.INSTANCE.enabled && !server().isEmpty();
   }

   public static Path dir() {
      return VisualsConfig.dir().resolve("remote");
   }

   public static String status() {
      if (server().isEmpty()) {
         return "No share server set";
      }

      if (!VisualsConfig.INSTANCE.enabled) {
         return "Mod is off";
      }

      int others = (int)REMOTE.values().stream().filter(r -> !r.bindings().isEmpty()).count();
      String s = status.isEmpty() ? "Connecting…" : status;
      return s + (VisualsConfig.INSTANCE.showOthers ? " · " + others + " player" + (others == 1 ? "" : "s") + "' models loaded" : "");
   }

   public static String lastError() {
      return lastError;
   }

   public static void changed() {
      dirtyAt = System.currentTimeMillis();
   }

   public static void refresh() {
      REMOTE.clear();
      uploadedV = null;
      indexAt = -1L;
      changed();
   }

   public static void tick(Minecraft mc) {
      if (mc.level == null || mc.player == null) {
         indexAt = -1L;
         liveMine = null;
         liveSent = null;
         live = Map.of();
      } else if (active()) {
         long now = System.currentTimeMillis();
         if (indexAt < 0L || now - indexAt > 60000L) {
            indexAt = now;
            IO.execute(Share::fetchIndex);
         }

         if (dirtyAt >= 0L && now - dirtyAt > 3000L && !firstIndex) {
            dirtyAt = -1L;
            JsonObject binds = GSON.toJsonTree(VisualsConfig.INSTANCE.bindings).getAsJsonObject();
            boolean share = VisualsConfig.INSTANCE.shareMine;
            User u = mc.getUser();
            int hide = hideMask();
            IO.execute(() -> upload(binds, share, u, hide));
         }

         tickLive(mc, now);
      }
   }

   private static void tickLive(Minecraft mc, long now) {
      if (now - liveCheckAt >= 250L) {
         liveCheckAt = now;
         String mine = liveState(mc.player);
         if (!mine.equals(liveMine)) {
            liveMine = mine;
            liveChangedAt = now;
         }
      }

      String want = liveMine;
      boolean needed = want != null && (!want.equals("{}") || liveSent != null && !liveSent.equals("{}") || liveSent == null && hasExact());
      boolean stale = want != null && want.equals(liveSent) && !want.equals("{}") && now - liveSentAt > 21600000L;
      if (needed
         && (!want.equals(liveSent) || stale)
         && VisualsConfig.INSTANCE.shareMine
         && !firstIndex
         && !liveBusy
         && now - liveChangedAt >= 500L
         && now - liveSentAt >= 1500L
         && now >= liveRetryAt) {
         liveBusy = true;
         liveSentAt = now;
         User u = mc.getUser();
         LIVE_IO.execute(() -> sendLive(want, u));
      }

      if (VisualsConfig.INSTANCE.showOthers && !livePolling && now - livePollAt >= 3000L) {
         List<String> ids = new ArrayList<>();

         for (Player p : mc.level.players()) {
            if (p != mc.player || SELF) {
               String u = undash(p.getUUID());
               if (index.containsKey(u) && ids.size() < 40) {
                  ids.add(u);
               }
            }
         }

         if (!ids.isEmpty()) {
            livePollAt = now;
            livePolling = true;
            LIVE_IO.execute(() -> pollLive(ids));
         } else if (!live.isEmpty()) {
            live = Map.of();
         }
      }
   }

   private static int hideMask() {
      VisualsConfig c = VisualsConfig.INSTANCE;
      return (c.hideHelmet ? 1 : 0) | (c.hideChestplate ? 2 : 0) | (c.hideLeggings ? 4 : 0) | (c.hideBoots ? 8 : 0);
   }

   public static int hiddenArmor(Entity e) {
      if (e instanceof Player p && active() && VisualsConfig.INSTANCE.showOthers && (p != Minecraft.getInstance().player || SELF)) {
         Map<String, String> s = live.get(undash(p.getUUID()));
         String h = s == null ? null : s.get("hide");
         if (h != null) {
            try {
               return Integer.parseInt(h) & 15;
            } catch (NumberFormatException var5) {
               return 0;
            }
         }
      }

      return 0;
   }

   private static boolean hasExact() {
      for (String k : VisualsConfig.INSTANCE.bindings.keySet()) {
         if (k.startsWith("uuid:")) {
            return true;
         }
      }

      return false;
   }

   private static String liveState(Player pl) {
      Map<String, Binding> binds = VisualsConfig.INSTANCE.bindings;
      JsonObject o = new JsonObject();
      if (!binds.isEmpty()) {
         for (int i = 0; i < LIVE_SLOTS.length; i++) {
            ItemStack st = pl.getItemBySlot(LIVE_SLOTS[i]);
            if (!st.isEmpty()) {
               String k = ItemKeys.exactKey(st);
               Binding b = k == null ? null : binds.get(k);
               if (b != null && b.model != null) {
                  String type = ItemKeys.typeKey(st);
                  String item = ItemKeys.itemKey(st);
                  o.addProperty(LIVE_NAMES[i], k + "|" + type + (type.equals(item) ? "" : "|" + item));
               }
            }
         }
      }

      int hide = hideMask();
      if (hide != 0) {
         o.addProperty("hide", String.valueOf(hide));
      }

      return GSON.toJson(o);
   }

   private static void sendLive(String want, User user) {
      try {
         String body = "{\"s\":" + want + "}";
         HttpResponse<String> r = send("PUT", "/v1/live", body, auth(user));
         if (r.statusCode() == 401) {
            token = null;
            r = send("PUT", "/v1/live", body, auth(user));
         }

         if (r.statusCode() == 404) {
            liveRetryAt = System.currentTimeMillis() + 600000L;
            return;
         }

         if (r.statusCode() != 200) {
            throw new IOException(error(r));
         }

         liveSent = want;
      } catch (Exception var7) {
         liveRetryAt = System.currentTimeMillis() + 10000L;
         ItemVisualsClient.LOG.warn("Clarity: could not share worn items: {}", msg(var7));
         return;
      } finally {
         liveBusy = false;
      }
   }

   private static void pollLive(List<String> ids) {
      try {
         HttpResponse<String> r = send("GET", "/v1/live?u=" + String.join(",", ids), null, null);
         if (r.statusCode() == 404) {
            livePollAt = System.currentTimeMillis() + 600000L;
            return;
         }

         if (r.statusCode() != 200) {
            throw new IOException(error(r));
         }

         Map<String, Map<String, String>> m = new HashMap<>();

         for (Entry<String, JsonElement> e : JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("live").entrySet()) {
            if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().get("s") instanceof JsonObject s) {
               Map<String, String> slots = new HashMap<>();

               for (Entry<String, JsonElement> se : s.entrySet()) {
                  if (se.getValue().isJsonPrimitive()) {
                     slots.put(se.getKey(), se.getValue().getAsString());
                  }
               }

               m.put(e.getKey(), slots);
            }
         }

         live = m;
      } catch (Exception var12) {
         livePollAt = System.currentTimeMillis() + 15000L;
         ItemVisualsClient.LOG.debug("Clarity: could not get worn items: {}", msg(var12));
         return;
      } finally {
         livePolling = false;
      }
   }

   private static Binding liveFind(Player p, String u, ItemStack stack, Share.Remote r) {
      Map<String, String> slots = live.get(u);
      if (slots != null && !slots.isEmpty()) {
         for (int i = 0; i < LIVE_SLOTS.length; i++) {
            if (p.getItemBySlot(LIVE_SLOTS[i]) == stack) {
               String v = slots.get(LIVE_NAMES[i]);
               if (v == null) {
                  return null;
               }

               String[] parts = v.split("\\|");
               if (parts.length < 2) {
                  return null;
               }

               ItemKeys.Keys k = ItemKeys.of(stack);
               String seen = k.skyblockId() != null ? "id:" + k.skyblockId() : ItemKeys.itemKey(stack);
               boolean same = seen.equals(parts[1]) || k.skyblockId() == null && parts.length > 2 && seen.equals(parts[2]);
               if (!same) {
                  return null;
               }

               Binding b = r.bindings().get(parts[0]);
               return b != null && b.model != null ? b : null;
            }
         }
      }

      return null;
   }

   public static Binding find(Entity e, ItemStack stack) {
      if (e instanceof Player p && !index.isEmpty() && active()) {
         Minecraft mc = Minecraft.getInstance();
         boolean me = p == mc.player;
         if (me ? SELF : VisualsConfig.INSTANCE.showOthers) {
            UUID id = p.getUUID();
            if (!SELF && id.version() != 4) {
               return null;
            }

            String u = undash(id);
            String v = index.get(u);
            if (v == null) {
               return null;
            }

            Share.Remote r = REMOTE.get(u);
            if ((r == null || !r.v().equals(v)) && (r == null || System.currentTimeMillis() > r.retryAt()) && LOADING.add(u)) {
               IO.execute(() -> loadProfile(u, v));
            }

            if (r == null) {
               return null;
            }

            Binding lb = r.exact() ? liveFind(p, u, stack, r) : null;
            return lb != null ? lb : ItemKeys.find(stack, r.bindings());
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static void fetchIndex() {
      try {
         HttpResponse<String> r = send("GET", "/v1/index", null, null);
         if (r.statusCode() != 200) {
            throw new IOException(error(r));
         }

         Map<String, String> m = new HashMap<>();

         for (Entry<String, JsonElement> e : JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("players").entrySet()) {
            m.put(e.getKey(), e.getValue().getAsString());
         }

         index = m;
         REMOTE.keySet().removeIf(k -> !m.containsKey(k));
         if (firstIndex) {
            firstIndex = false;
            changed();
         }
      } catch (Exception var4) {
         fail("Share server unreachable", var4);
      }
   }

   private static void loadProfile(String u, String v) {
      try {
         HttpResponse<String> r = send("GET", "/v1/p/" + u, null, null);
         if (r.statusCode() == 404) {
            REMOTE.remove(u);
            return;
         }

         if (r.statusCode() != 200) {
            throw new IOException(error(r));
         }

         JsonObject p = JsonParser.parseString(r.body()).getAsJsonObject();
         JsonObject models = p.getAsJsonObject("models");
         JsonObject binds = p.getAsJsonObject("bindings");
         Map<String, Binding> out = new HashMap<>();
         Map<String, Boolean> got = new HashMap<>();

         for (Entry<String, JsonElement> e : binds.entrySet()) {
            if (e.getValue().isJsonObject()) {
               JsonObject bj = e.getValue().getAsJsonObject();
               String h = bj.has("model") ? bj.get("model").getAsString() : "";
               if (HEX64.matcher(h).matches() && models.has(h) && got.computeIfAbsent(h, x -> fetchModel(u, x))) {
                  Binding b = (Binding)GSON.fromJson(bj, Binding.class);
                  b.model = "@r/" + h + ".bbmodel";
                  sanitize(b);
                  out.put(e.getKey(), b);
               }
            }
         }

         boolean exact = out.keySet().stream().anyMatch(k -> k.startsWith("uuid:"));
         REMOTE.put(u, new Share.Remote(r.headers().firstValue("x-version").orElse(v), out, 0L, exact));
      } catch (Exception var16) {
         ItemVisualsClient.LOG.warn("Clarity: could not load shared models of {}: {}", u, var16.toString());
         REMOTE.put(u, new Share.Remote("", Map.of(), System.currentTimeMillis() + 120000L, false));
         return;
      } finally {
         LOADING.remove(u);
      }
   }

   private static boolean fetchModel(String u, String h) {
      try {
         Path f = dir().resolve(h + ".bbmodel");
         if (Files.isRegularFile(f)) {
            return true;
         }

         HttpResponse<String> r = send("GET", "/v1/m/" + u + "/" + h, null, null);
         if (r.statusCode() != 200) {
            return false;
         }

         GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(r.body().trim())));

         byte[] raw;
         try {
            raw = in.readNBytes(6291457);
         } catch (Throwable var9) {
            try {
               in.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }

            throw var9;
         }

         in.close();
         if (raw.length <= 6291456 && sha256(raw).equals(h)) {
            Files.createDirectories(dir());
            Path tmp = Files.createTempFile(dir(), h, ".tmp");
            Files.write(tmp, raw);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
         } else {
            return false;
         }
      } catch (Exception var10) {
         ItemVisualsClient.LOG.warn("Clarity: could not download shared model {}: {}", h, var10.toString());
         return false;
      }
   }

   private static void sanitize(Binding b) {
      if (b.places == null) {
         b.places = new LinkedHashMap<>();
      }

      b.places.values().removeIf(Objects::isNull);

      for (Placement p : b.places.values()) {
         p.moveX = clamp(p.moveX, -100.0F, 100.0F);
         p.moveY = clamp(p.moveY, -100.0F, 100.0F);
         p.moveZ = clamp(p.moveZ, -100.0F, 100.0F);
         p.turnX = clamp(p.turnX, -180.0F, 180.0F);
         p.turnY = clamp(p.turnY, -180.0F, 180.0F);
         p.turnZ = clamp(p.turnZ, -180.0F, 180.0F);
         p.size = clamp(p.size, 0.0F, 400.0F);
      }

      b.switchDelay = Math.max(0, Math.min(2000, b.switchDelay));
      if (b.animAlways == null) {
         b.animAlways = "none";
      }

      if (b.animRightClick == null) {
         b.animRightClick = "none";
      }

      if (b.animSwing == null) {
         b.animSwing = "none";
      }

      if (b.animRelease == null) {
         b.animRelease = "none";
      }

      if (b.switchMode == null) {
         b.switchMode = "smooth";
      }
   }

   private static float clamp(float v, float lo, float hi) {
      return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : 0.0F;
   }

   private static void upload(JsonObject binds, boolean share, User user, int hide) {
      try {
         String me = undash(user.getProfileId());
         if (!share) {
            if (uploadedV != null || index.containsKey(me)) {
               HttpResponse<String> r = send("DELETE", "/v1/p", null, auth(user));
               if (r.statusCode() != 200) {
                  throw new IOException(error(r));
               }

               uploadedV = null;
               Map<String, String> m = new HashMap<>(index);
               m.remove(me);
               index = m;
            }

            status = "Not sharing your models";
            lastError = "";
            sharedCount = 0;
            return;
         }

         TreeMap<String, JsonObject> models = new TreeMap<>();
         TreeMap<String, JsonObject> outBinds = new TreeMap<>();
         Map<String, Path> files = new HashMap<>();
         int skipped = 0;

         for (Entry<String, JsonElement> e : binds.entrySet()) {
            JsonObject b = e.getValue().getAsJsonObject();
            if (b.has("model") && b.get("model").isJsonPrimitive()) {
               String rel = b.get("model").getAsString();
               if (!rel.startsWith("@")) {
                  Path f = VisualsConfig.itemsDir().resolve(rel).normalize();
                  if (f.startsWith(VisualsConfig.itemsDir()) && Files.isRegularFile(f)) {
                     Share.Hashed h = hash(f);
                     if (h == null || h.b64() > 1900000 || !models.containsKey(h.hash()) && models.size() >= 64) {
                        skipped++;
                     } else {
                        JsonObject info = new JsonObject();
                        info.addProperty("name", f.getFileName().toString().replaceFirst("\\.bbmodel$", ""));
                        info.addProperty("size", h.size());
                        models.put(h.hash(), info);
                        files.put(h.hash(), f);
                        JsonObject copy = b.deepCopy();
                        copy.addProperty("model", h.hash());
                        outBinds.put(e.getKey(), copy);
                     }
                  }
               }
            }
         }

         if (models.isEmpty() && hide == 0) {
            if (uploadedV != null || index.containsKey(me)) {
               send("DELETE", "/v1/p", null, auth(user));
            }

            uploadedV = null;
            sharedCount = 0;
            lastError = "";
            status = skipped > 0 ? "Your models are too big to share" : "Nothing to share yet (no models on items)";
            return;
         }

         JsonObject prof = new JsonObject();
         prof.addProperty("v", 1);
         prof.addProperty("name", user.getName());
         prof.add("models", GSON.toJsonTree(models));
         prof.add("bindings", GSON.toJsonTree(outBinds));
         String json = GSON.toJson(prof);
         String v = sha256(json.getBytes(StandardCharsets.UTF_8)).substring(0, 16);
         String note = skipped > 0 ? " (" + skipped + " too big)" : "";
         if (v.equals(uploadedV) || v.equals(index.get(me))) {
            uploadedV = v;
            sharedCount = models.size();
            lastError = "";
            status = (models.isEmpty() ? "Sharing hidden armor" : "Sharing " + models.size() + " model" + (models.size() == 1 ? "" : "s")) + note;
            return;
         }

         status = "Uploading…";
         HttpResponse<String> r = send("PUT", "/v1/p", json, auth(user));
         if (r.statusCode() == 401) {
            token = null;
            r = send("PUT", "/v1/p", json, auth(user));
         }

         if (r.statusCode() == 409) {
            for (JsonElement m : JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("missing")) {
               Path f = files.get(m.getAsString());
               if (f != null) {
                  HttpResponse<String> mr = send("PUT", "/v1/m/" + m.getAsString(), b64gzip(Files.readAllBytes(f)), auth(user));
                  if (mr.statusCode() != 200) {
                     throw new IOException(f.getFileName() + ": " + error(mr));
                  }
               }
            }

            r = send("PUT", "/v1/p", json, auth(user));
         }

         if (r.statusCode() != 200) {
            throw new IOException(error(r));
         }

         uploadedV = v;
         sharedCount = models.size();
         lastError = "";
         Map<String, String> mx = new HashMap<>(index);
         mx.put(me, v);
         index = mx;
         status = (models.isEmpty() ? "Sharing hidden armor" : "Sharing " + models.size() + " model" + (models.size() == 1 ? "" : "s")) + note;
         ItemVisualsClient.LOG.info("Clarity: shared {} model(s)", models.size());
      } catch (Exception var18) {
         fail("Upload failed", var18);
         dirtyAt = System.currentTimeMillis() + 60000L;
      }
   }

   private static String auth(User user) throws Exception {
      if (token != null && System.currentTimeMillis() < tokenExp - 60000L) {
         return token;
      }

      String why = "";
      Optional<ProfileKeyPair> keys = OFFLINE ? Optional.empty() : chatKeys(user);
      JsonObject body;
      if (keys.isPresent()) {
         body = ShareAuth.body(
            user.getName(),
            undash(user.getProfileId()),
            keys.get().privateKey(),
            keys.get().publicKey().data(),
            ShareAuth.host(server()),
            System.currentTimeMillis()
         );
      } else {
         body = new JsonObject();
         body.addProperty("name", user.getName());
         if (OFFLINE) {
            body.addProperty("offline", true);
            body.addProperty("uuid", undash(user.getProfileId()));
         } else {
            why = "; chat key: " + keyProblem;
            String serverId = new BigInteger(130, new SecureRandom()).toString(16);

            while (serverId.length() < 8) {
               serverId = "0" + serverId;
            }

            body.addProperty("serverId", serverId);

            try {
               Minecraft.getInstance().services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
            } catch (Exception var6) {
               throw new IOException("Mojang login check failed (" + msg(var6) + ")" + why, var6);
            }
         }
      }

      HttpResponse<String> r = send("POST", "/v1/auth", GSON.toJson(body), null);
      if (r.statusCode() != 200) {
         throw new IOException(error(r) + why);
      }

      JsonObject j = JsonParser.parseString(r.body()).getAsJsonObject();
      token = j.get("token").getAsString();
      tokenExp = j.get("expires").getAsLong();
      return token;
   }

   private static Optional<ProfileKeyPair> chatKeys(User user) {
      String game;
      try {
         Minecraft mc = Minecraft.getInstance();
         Optional<ProfileKeyPair> k = (Optional<ProfileKeyPair>)((CompletableFuture)mc.submit(() -> mc.getProfileKeyPairManager().prepareKeyPair())
               .get(10L, TimeUnit.SECONDS))
            .get(20L, TimeUnit.SECONDS);
         if (k.isPresent() && !k.get().publicKey().data().hasExpired()) {
            return k;
         }

         game = k.isPresent() ? "expired" : "none";
      } catch (Exception var6) {
         game = msg(var6);
      }

      if (System.currentTimeMillis() < keyRetryAt) {
         return Optional.empty();
      }

      try {
         HttpRequest req = HttpRequest.newBuilder(URI.create(CERTS))
            .timeout(Duration.ofSeconds(20L))
            .header("authorization", "Bearer " + user.getAccessToken())
            .header("content-type", "application/json; charset=utf-8")
            .header("user-agent", "Clarity-mod")
            .POST(BodyPublishers.noBody())
            .build();
         HttpResponse<String> r = HTTP.send(req, BodyHandlers.ofString());
         if (r.statusCode() != 200) {
            throw new IOException("HTTP " + r.statusCode());
         }

         ProfileKeyPair k = ShareAuth.keyPair(r.body());
         if (k.publicKey().data().hasExpired()) {
            throw new IOException("expired");
         }

         ItemVisualsClient.LOG.info("Clarity: the game had no chat key ({}), got one from Mojang", game);
         return Optional.of(k);
      } catch (Exception var5) {
         keyRetryAt = System.currentTimeMillis() + 300000L;
         keyProblem = "game " + game + ", Mojang " + msg(var5);
         ItemVisualsClient.LOG.warn("Clarity: no chat key ({})", keyProblem);
         return Optional.empty();
      }
   }

   private static Share.Hashed hash(Path f) {
      try {
         long size = Files.size(f);
         long mtime = Files.getLastModifiedTime(f).toMillis();
         Share.Hashed h = HASHES.get(f);
         if (h != null && h.size() == size && h.mtime() == mtime) {
            return h;
         }

         if (size > 6291456L) {
            return null;
         }

         byte[] raw = Files.readAllBytes(f);
         h = new Share.Hashed(sha256(raw), size, mtime, b64gzip(raw).length());
         HASHES.put(f, h);
         return h;
      } catch (IOException var7) {
         return null;
      }
   }

   private static HttpResponse<String> send(String method, String path, String body, String auth) throws IOException, InterruptedException {
      Builder b = HttpRequest.newBuilder(URI.create(server() + path))
         .timeout(Duration.ofSeconds(30L))
         .header("user-agent", "Clarity-mod")
         .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
      if (auth != null) {
         b.header("authorization", "Bearer " + auth);
      }

      return HTTP.send(b.build(), BodyHandlers.ofString());
   }

   private static String error(HttpResponse<String> r) {
      try {
         return JsonParser.parseString(r.body()).getAsJsonObject().get("error").getAsString() + " (" + r.statusCode() + ")";
      } catch (Exception var2) {
         return "HTTP " + r.statusCode();
      }
   }

   private static void fail(String what, Exception e) {
      lastError = what + ": " + msg(e);
      status = what;
      ItemVisualsClient.LOG.warn("Clarity: {}: {}", what, msg(e));
   }

   private static String msg(Throwable e) {
      while (e.getCause() != null && (e instanceof ExecutionException || e.getMessage() == null)) {
         e = e.getCause();
      }

      return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
   }

   private static String b64gzip(byte[] raw) throws IOException {
      ByteArrayOutputStream bo = new ByteArrayOutputStream(raw.length / 2 + 64);

      try (GZIPOutputStream gz = new GZIPOutputStream(bo)) {
         gz.write(raw);
      }

      return Base64.getEncoder().encodeToString(bo.toByteArray());
   }

   private static String sha256(byte[] data) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
      } catch (Exception var2) {
         throw new IllegalStateException(var2);
      }
   }

   private static String undash(UUID id) {
      return id.toString().replace("-", "").toLowerCase(Locale.ROOT);
   }

   private record Hashed(String hash, long size, long mtime, int b64) {
   }

   private record Remote(String v, Map<String, Binding> bindings, long retryAt, boolean exact) {
   }
}
