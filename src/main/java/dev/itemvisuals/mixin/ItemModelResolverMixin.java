package dev.itemvisuals.mixin;

import dev.itemvisuals.ItemVisualsClient;
import dev.itemvisuals.render.VisualsHook;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
   @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
   private void itemvisuals$swapModel(
      ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext, Level level, ItemOwner owner, int seed, CallbackInfo ci
   ) {
      try {
         if (VisualsHook.apply(output, item, displayContext, owner)) {
            ci.cancel();
         }
      } catch (Throwable var9) {
         ItemVisualsClient.LOG.error("Clarity render failed", var9);
      }
   }
}
