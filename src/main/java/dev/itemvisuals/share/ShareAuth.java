package dev.itemvisuals.share;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Base64.Encoder;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.player.ProfilePublicKey.Data;

public final class ShareAuth {
   private ShareAuth() {
   }

   public static String host(String server) {
      URI u = URI.create(server);
      int port = u.getPort();
      boolean dflt = port < 0 || port == 443 && "https".equalsIgnoreCase(u.getScheme()) || port == 80 && "http".equalsIgnoreCase(u.getScheme());
      return u.getHost().toLowerCase(Locale.ROOT) + (dflt ? "" : ":" + port);
   }

   public static ProfileKeyPair keyPair(String json) throws IOException, CryptException {
      try {
         JsonObject j = JsonParser.parseString(json).getAsJsonObject();
         JsonObject kp = j.getAsJsonObject("keyPair");
         Data cert = new Data(
            Instant.parse(j.get("expiresAt").getAsString()),
            Crypt.stringToRsaPublicKey(kp.get("publicKey").getAsString()),
            Base64.getMimeDecoder().decode(j.get("publicKeySignatureV2").getAsString())
         );
         return new ProfileKeyPair(
            Crypt.stringToPemRsaPrivateKey(kp.get("privateKey").getAsString()),
            new ProfilePublicKey(cert),
            Instant.parse(j.get("refreshedAfter").getAsString())
         );
      } catch (RuntimeException var41) {
         throw new IOException("unexpected answer", var41);
      }
   }

   public static JsonObject body(String name, String uuid, PrivateKey key, Data cert, String host, long now) throws GeneralSecurityException {
      Signature s = Signature.getInstance("SHA256withRSA");
      s.initSign(key);
      s.update(("clarity-share-auth:" + host + ":" + uuid + ":" + now).getBytes(StandardCharsets.UTF_8));
      Encoder b64 = Base64.getEncoder();
      JsonObject o = new JsonObject();
      o.addProperty("name", name);
      o.addProperty("uuid", uuid);
      o.addProperty("expires", cert.expiresAt().toEpochMilli());
      o.addProperty("key", b64.encodeToString(cert.key().getEncoded()));
      o.addProperty("keySig", b64.encodeToString(cert.keySignature()));
      o.addProperty("ts", now);
      o.addProperty("sig", b64.encodeToString(s.sign()));
      return o;
   }
}
