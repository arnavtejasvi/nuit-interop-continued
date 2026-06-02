package me.flashyreese.mods.nuit_interop_continued.neoforge;

import me.flashyreese.mods.nuit_interop_continued.NuitInteropContinued;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(NuitInteropContinued.MOD_ID)
public class NuitInteropContinuedNeoForge {
    public NuitInteropContinuedNeoForge(IEventBus bus) {
        NuitInteropContinued.init();
    }
}
