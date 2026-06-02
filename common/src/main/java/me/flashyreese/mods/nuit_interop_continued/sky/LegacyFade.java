package me.flashyreese.mods.nuit_interop_continued.sky;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit_interop_continued.utils.Utils;

public class LegacyFade {
    public static final Codec<LegacyFade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("startFadeIn", 0).forGetter(LegacyFade::getStartFadeIn),
            Codec.INT.optionalFieldOf("endFadeIn", 0).forGetter(LegacyFade::getEndFadeIn),
            Codec.INT.optionalFieldOf("startFadeOut", 0).forGetter(LegacyFade::getStartFadeOut),
            Codec.INT.optionalFieldOf("endFadeOut", 0).forGetter(LegacyFade::getEndFadeOut),
            Codec.BOOL.optionalFieldOf("alwaysOn", false).forGetter(LegacyFade::isAlwaysOn)
    ).apply(instance, LegacyFade::new));

    private final int startFadeIn;
    private final int endFadeIn;
    private final int startFadeOut;
    private final int endFadeOut;
    private final boolean alwaysOn;

    public LegacyFade(int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut, boolean alwaysOn) {
        this.startFadeIn = alwaysOn ? startFadeIn : Utils.normalizeTickTime(startFadeIn);
        this.endFadeIn = alwaysOn ? endFadeIn : Utils.normalizeTickTime(endFadeIn);
        this.startFadeOut = alwaysOn ? startFadeOut : Utils.normalizeTickTime(startFadeOut);
        this.endFadeOut = alwaysOn ? endFadeOut : Utils.normalizeTickTime(endFadeOut);
        this.alwaysOn = alwaysOn;
    }

    public int getStartFadeIn() { return startFadeIn; }
    public int getEndFadeIn() { return endFadeIn; }
    public int getStartFadeOut() { return startFadeOut; }
    public int getEndFadeOut() { return endFadeOut; }
    public boolean isAlwaysOn() { return alwaysOn; }
}
