package dev.itemvisuals.render;

import dev.itemvisuals.config.VisualsConfig;
import dev.itemvisuals.share.Share;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class ArmorHider {
   public static final String[] NAMES = new String[]{"Helmet", "Chest", "Legs", "Boots"};
   public static final String[] KEYS = new String[]{"helmet", "chestplate", "leggings", "boots"};
   public static final int HAT = 16;

   private ArmorHider() {
   }

   public static boolean hidden(int i) {
      VisualsConfig c = VisualsConfig.INSTANCE;

      return switch (i) {
         case 0 -> c.hideHelmet;
         case 1 -> c.hideChestplate;
         case 2 -> c.hideLeggings;
         default -> c.hideBoots;
      };
   }

   public static void toggle(int i) {
      VisualsConfig c = VisualsConfig.INSTANCE;
      switch (i) {
         case 0:
            c.hideHelmet = !c.hideHelmet;
            break;
         case 1:
            c.hideChestplate = !c.hideChestplate;
            break;
         case 2:
            c.hideLeggings = !c.hideLeggings;
            break;
         default:
            c.hideBoots = !c.hideBoots;
      }
   }

   public static int mask(LivingEntity entity) {
      VisualsConfig c = VisualsConfig.INSTANCE;
      if (!c.enabled) {
         return 0;
      }

      int m = VisualsHook.hasModel(entity.getItemBySlot(EquipmentSlot.HEAD), entity) ? 16 : 0;
      if (!(entity instanceof Player)) {
         return m;
      }

      boolean me = entity == Minecraft.getInstance().player;
      if (!me) {
         m |= Share.hiddenArmor(entity);
      }

      if (me || c.hideArmorOthers) {
         for (int i = 0; i < 4; i++) {
            if (hidden(i)) {
               m |= 1 << i;
            }
         }
      }

      return m;
   }

   public static int bit(EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> 1;
         case CHEST -> 2;
         case LEGS -> 4;
         case FEET -> 8;
         default -> 0;
      };
   }

   public static boolean skipArmor(EntityRenderState state, EquipmentSlot slot) {
      if (!(state instanceof ArmorHider.Holder h)) {
         return false;
      } else {
         int m = h.itemvisuals$hiddenArmor();
         if ((m & bit(slot)) != 0) {
            return true;
         }
         if (slot == EquipmentSlot.HEAD && (m & 16) != 0) {
            return true;
         }
         return h.itemvisuals$worn(slot) != null;
      }
   }

   public static boolean skip(EntityRenderState state, EquipmentSlot slot) {
      return state instanceof ArmorHider.Holder h && (h.itemvisuals$hiddenArmor() & bit(slot)) != 0;
   }

   public interface Holder {
      int itemvisuals$hiddenArmor();

      void itemvisuals$setHiddenArmor(int var1);

      BodyWear.Worn[] itemvisuals$wornAll();

      void itemvisuals$setWornAll(BodyWear.Worn[] var1);

      default BodyWear.Worn itemvisuals$worn() {
         return this.itemvisuals$worn(EquipmentSlot.CHEST);
      }

      default BodyWear.Worn itemvisuals$worn(EquipmentSlot slot) {
         BodyWear.Worn[] all = this.itemvisuals$wornAll();
         int idx = BodyWear.slotIndex(slot);
         return all != null && idx >= 0 && idx < all.length ? all[idx] : null;
      }
   }
}
