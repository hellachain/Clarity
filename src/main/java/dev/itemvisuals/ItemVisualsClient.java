package dev.itemvisuals;

import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.itemvisuals.config.VisualsConfig;
import dev.itemvisuals.gui.VisualsScreen;
import dev.itemvisuals.render.Animator;
import dev.itemvisuals.render.ArmorHider;
import dev.itemvisuals.render.ModelLibrary;
import dev.itemvisuals.share.Share;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemVisualsClient implements ClientModInitializer {
   public static final String MOD_ID = "clarity";
   public static final Logger LOG = LoggerFactory.getLogger("Clarity");
   private static boolean openNextTick;
   private static int tickCounter;

   public void onInitializeClient() {
      VisualsConfig.load();
      installExample();
      Category category = Category.register(Identifier.fromNamespaceAndPath("clarity", "main"));
      KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.clarity.open", Type.KEYSYM, 75, category));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
         ModelLibrary.tick();
         Share.tick(mc);

         while (open.consumeClick()) {
            if (mc.gui.screen() == null) {
               mc.gui.setScreen(new VisualsScreen());
            }
         }

         if (openNextTick) {
            openNextTick = false;
            mc.gui.setScreen(new VisualsScreen());
         }

         if (++tickCounter % 200 == 0) {
            Animator.cleanup();
         }
      });
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
         LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("clarity");
         root.executes(c -> {
            openNextTick = true;
            return 1;
         });
         root.then(shareCommand());
         root.then(ClientCommands.literal("reload").executes(c -> {
            VisualsConfig.load();
            ModelLibrary.reload();
            c.getSource().sendFeedback(Component.literal("\u00a7d[Clarity]\u00a7r reloaded " + ModelLibrary.list().size() + " models"));
            return 1;
         }));
         LiteralArgumentBuilder<FabricClientCommandSource> armor = ClientCommands.literal("armor");
         armor.executes(c -> {
            c.getSource().sendFeedback(armorStatus());
            return 1;
         });
         armor.then(armorToggle("helmet", 0));
         armor.then(armorToggle("chestplate", 1));
         armor.then(armorToggle("leggings", 2));
         armor.then(armorToggle("boots", 3));
         armor.then(ClientCommands.literal("others").executes(c -> {
            VisualsConfig.INSTANCE.hideArmorOthers = !VisualsConfig.INSTANCE.hideArmorOthers;
            VisualsConfig.save();
            c.getSource().sendFeedback(armorStatus());
            return 1;
         }));
         root.then(armor);
         root.then(ClientCommands.literal("toggle").executes(c -> {
            VisualsConfig.INSTANCE.enabled = !VisualsConfig.INSTANCE.enabled;
            VisualsConfig.save();
            c.getSource().sendFeedback(Component.literal("\u00a7d[Clarity]\u00a7r " + (VisualsConfig.INSTANCE.enabled ? "enabled" : "disabled")));
            return 1;
         }));
         dispatcher.register(root);
      });
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> shareCommand() {
      return (LiteralArgumentBuilder<FabricClientCommandSource>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(
                           "share"
                        )
                        .executes(c -> {
                           ((FabricClientCommandSource)c.getSource()).sendFeedback(shareInfo());
                           return 1;
                        }))
                     .then(ClientCommands.literal("on").executes(c -> shareSet((FabricClientCommandSource)c.getSource(), true, null))))
                  .then(ClientCommands.literal("off").executes(c -> shareSet((FabricClientCommandSource)c.getSource(), false, null))))
               .then(
                  ((LiteralArgumentBuilder)ClientCommands.literal("others")
                        .then(ClientCommands.literal("on").executes(c -> shareSet((FabricClientCommandSource)c.getSource(), null, true))))
                     .then(ClientCommands.literal("off").executes(c -> shareSet((FabricClientCommandSource)c.getSource(), null, false)))
               ))
            .then(ClientCommands.literal("now").executes(c -> {
               Share.refresh();
               ((FabricClientCommandSource)c.getSource()).sendFeedback(Component.literal("§d[Clarity]§r uploading and fetching shared models again…"));
               return 1;
            })))
         .then(ClientCommands.literal("server").then(ClientCommands.argument("url", StringArgumentType.greedyString()).executes(c -> {
            String url = StringArgumentType.getString(c, "url").trim();
            if (url.equalsIgnoreCase("default")) {
               url = null;
            } else if (!url.startsWith("https://") && !url.startsWith("http://")) {
               url = "https://" + url;
            }

            VisualsConfig.INSTANCE.shareServer = url;
            VisualsConfig.save();
            Share.refresh();
            ((FabricClientCommandSource)c.getSource()).sendFeedback(shareInfo());
            return 1;
         })));
   }

   private static int shareSet(FabricClientCommandSource src, Boolean mine, Boolean others) {
      if (mine != null) {
         VisualsConfig.INSTANCE.shareMine = mine;
      }

      if (others != null) {
         VisualsConfig.INSTANCE.showOthers = others;
      }

      VisualsConfig.save();
      src.sendFeedback(shareInfo());
      return 1;
   }

   private static Component shareInfo() {
      VisualsConfig c = VisualsConfig.INSTANCE;
      String server = Share.server();
      String err = Share.lastError();
      return Component.literal(
         "§d[Clarity]§r sharing: your models "
            + (c.shareMine ? "§ashared§r" : "§7not shared§r")
            + ", others' models "
            + (c.showOthers ? "§ashown§r" : "§7hidden§r")
            + "\n§7server: "
            + (server.isEmpty() ? "none (/clarity share server <url>)" : server)
            + "\n§7"
            + Share.status()
            + (err.isEmpty() ? "" : "\n§c" + err)
      );
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> armorToggle(String name, int piece) {
      return (LiteralArgumentBuilder<FabricClientCommandSource>)ClientCommands.literal(name).executes(c -> {
         ArmorHider.toggle(piece);
         VisualsConfig.save();
         ((FabricClientCommandSource)c.getSource()).sendFeedback(armorStatus());
         return 1;
      });
   }

   private static Component armorStatus() {
      StringBuilder sb = new StringBuilder("§d[Clarity]§r armor: ");

      for (int i = 0; i < ArmorHider.KEYS.length; i++) {
         sb.append(i == 0 ? "" : ", ").append(ArmorHider.KEYS[i]).append(ArmorHider.hidden(i) ? " §8hidden§r" : " §ashown§r");
      }

      sb.append(" · others ").append(VisualsConfig.INSTANCE.hideArmorOthers ? "§atoo§r" : "§8not affected§r");
      return Component.literal(sb.toString());
   }

   private static void installExample() {
      Path target = VisualsConfig.itemsDir().resolve("example_staff.bbmodel");
      if (!Files.exists(target)) {
         try (InputStream in = ItemVisualsClient.class.getResourceAsStream("/assets/clarity/example_staff.bbmodel")) {
            if (in != null) {
               Files.createDirectories(target.getParent());
               Files.copy(in, target);
            }
         } catch (Exception var6) {
            LOG.warn("Could not install example model", var6);
         }
      }
   }
}
