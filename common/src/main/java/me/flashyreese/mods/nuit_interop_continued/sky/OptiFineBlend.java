package me.flashyreese.mods.nuit_interop_continued.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.serialization.Codec;
import me.flashyreese.mods.nuit.mixin.RenderPipelinesAccessor;
import me.flashyreese.mods.nuit_interop_continued.NuitInteropContinued;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.function.Function;

public enum OptiFineBlend {
    ALPHA("alpha",   new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA), a -> new Vector4f(1,1,1,a)),
    ADD("add",       new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE),                 a -> new Vector4f(1,1,1,a)),
    SUBTRACT("subtract", new BlendFunction(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ZERO),  a -> new Vector4f(a,a,a,1)),
    MULTIPLY("multiply", new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ONE_MINUS_SRC_ALPHA), a -> new Vector4f(a,a,a,a)),
    DODGE("dodge",   new BlendFunction(SourceFactor.ONE, DestFactor.ONE),                       a -> new Vector4f(a,a,a,1)),
    BURN("burn",     new BlendFunction(SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_COLOR),       a -> new Vector4f(a,a,a,1)),
    SCREEN("screen", new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_COLOR),        a -> new Vector4f(a,a,a,1)),
    OVERLAY("overlay", new BlendFunction(SourceFactor.DST_COLOR, DestFactor.SRC_COLOR),          a -> new Vector4f(a,a,a,1)),
    REPLACE("replace", null,                                                                     a -> new Vector4f(1,1,1,a));

    public static final Codec<OptiFineBlend> CODEC = Codec.STRING.xmap(OptiFineBlend::byName, OptiFineBlend::toString);

    private final String name;
    private final BlendFunction blendFunction;
    private final Function<Float, Vector4f> colorFunc;
    private RenderPipeline pipeline;

    OptiFineBlend(String name, BlendFunction blendFunction, Function<Float, Vector4f> colorFunc) {
        this.name = name; this.blendFunction = blendFunction; this.colorFunc = colorFunc;
    }

    public static OptiFineBlend byName(String name) {
        return Arrays.stream(values()).filter(b -> b.name.equals(name)).findFirst().orElse(ADD);
    }

    public RenderPipeline getPipeline() {
        if (pipeline == null) {
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelinesAccessor.getMatricesColorSnippet());
            builder.withLocation(ResourceLocation.fromNamespaceAndPath(NuitInteropContinued.MOD_ID, "pipeline/optifine_sky_" + name));
            builder.withVertexShader("core/position_tex");
            builder.withFragmentShader("core/position_tex");
            builder.withDepthWrite(false);
            if (blendFunction != null) builder.withBlend(blendFunction); else builder.withoutBlend();
            builder.withSampler("Sampler0");
            builder.withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS);
            pipeline = builder.build();
        }
        return pipeline;
    }

    public Vector4f getColor(float alpha) { return colorFunc.apply(alpha); }

    @Override public String toString() { return name; }
}
