package me.flashyreese.mods.nuit_interop_continued.mixin;

import me.flashyreese.mods.nuit.resource.SkyboxResourceListener;
import me.flashyreese.mods.nuit_interop_continued.NuitInteropContinued;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyboxResourceListener.class)
public class MixinSkyboxResourceListener {
    @Inject(method = "readFiles", at = @At("TAIL"))
    public void onReadFiles(ResourceManager resourceManager, CallbackInfo ci) {
        NuitInteropContinued.getInstance().inject(resourceManager);
    }
}
