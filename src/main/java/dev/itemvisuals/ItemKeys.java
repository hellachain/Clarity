package dev.itemvisuals;

import dev.itemvisuals.config.Binding;
import dev.itemvisuals.config.VisualsConfig;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemKeys {
   private static final ItemKeys.Keys NONE = new ItemKeys.Keys(null, null);
   private static final Map<CustomData, ItemKeys.Keys> CACHE = new IdentityHashMap<>();

   public static ItemKeys.Keys of(ItemStack stack) {
      CustomData data = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      if (data != null && !data.isEmpty()) {
         synchronized (CACHE) {
            ItemKeys.Keys k = CACHE.get(data);
            if (k != null) {
               return k;
            }

            if (CACHE.size() > 4096) {
               CACHE.clear();
            }

            CompoundTag tag = data.copyTag();
            CompoundTag attrs = tag.getCompound("ExtraAttributes").orElse(tag);
            k = new ItemKeys.Keys((String)attrs.getString("id").orElse(null), (String)attrs.getString("uuid").orElse(null));
            CACHE.put(data, k);
            return k;
         }
      } else {
         return NONE;
      }
   }

   public static String itemKey(ItemStack stack) {
      return "item:" + BuiltInRegistries.ITEM.getKey(stack.getItem());
   }

   public static String typeKey(ItemStack stack) {
      ItemKeys.Keys k = of(stack);
      return k.skyblockId() != null ? "id:" + k.skyblockId() : itemKey(stack);
   }

   public static String exactKey(ItemStack stack) {
      ItemKeys.Keys k = of(stack);
      return k.uuid() != null ? "uuid:" + k.uuid() : null;
   }

   public static Binding find(ItemStack stack) {
      return find(stack, VisualsConfig.INSTANCE.bindings);
   }

   public static Binding find(ItemStack stack, Map<String, Binding> b) {
      if (b.isEmpty()) {
         return null;
      }

      ItemKeys.Keys k = of(stack);
      Binding r = null;
      if (k.uuid() != null) {
         r = b.get("uuid:" + k.uuid());
      }

      if (r == null && k.skyblockId() != null) {
         r = b.get("id:" + k.skyblockId());
      }

      if (r == null) {
         r = b.get(itemKey(stack));
      }

      return r != null && r.model != null ? r : null;
   }

   private ItemKeys() {
   }

   public record Keys(String skyblockId, String uuid) {
   }
}
