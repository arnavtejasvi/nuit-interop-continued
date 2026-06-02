package me.flashyreese.mods.nuit_interop_continued.fabric;

import me.flashyreese.mods.nuit_interop_continued.NuitInteropContinued;
import net.fabricmc.api.ClientModInitializer;

public class NuitInteropContinuedFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NuitInteropContinued.init();
    }
}
