package me.flashyreese.mods.nuit_interop_continued.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.components.RangeEntry;
import me.flashyreese.mods.nuit.components.Weather;
import me.flashyreese.mods.nuit.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class OptiFineSkyLayer {
    private static final Codec<Vector3f> VEC_3_F = Codec.FLOAT.listOf().comapFlatMap(
            list -> list.size() < 3 ? DataResult.error(() -> "Incomplete vector") : DataResult.success(new Vector3f(list.get(0), list.get(1), list.get(2))),
            vec -> ImmutableList.of(vec.x(), vec.y(), vec.z()));

    private static final LegacyFade ALWAYS_ON_FADE = new LegacyFade(0, 0, 0, 0, true);

    public static final Codec<OptiFineSkyLayer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("source").forGetter(OptiFineSkyLayer::getSource),
            Codec.BOOL.optionalFieldOf("biomeInclusion", true).forGetter(OptiFineSkyLayer::isBiomeInclusion),
            ResourceLocation.CODEC.listOf().optionalFieldOf("biomes", ImmutableList.of()).forGetter(OptiFineSkyLayer::getBiomes),
            RangeEntry.CODEC.listOf().optionalFieldOf("heights", ImmutableList.of()).forGetter(OptiFineSkyLayer::getHeights),
            OptiFineBlend.CODEC.optionalFieldOf("blend", OptiFineBlend.ADD).forGetter(OptiFineSkyLayer::getBlend),
            LegacyFade.CODEC.optionalFieldOf("fade", ALWAYS_ON_FADE).forGetter(OptiFineSkyLayer::getFade),
            Codec.BOOL.optionalFieldOf("rotate", false).forGetter(OptiFineSkyLayer::isRotate),
            Codec.FLOAT.optionalFieldOf("speed", 1.0F).forGetter(OptiFineSkyLayer::getSpeed),
            VEC_3_F.optionalFieldOf("axis", new Vector3f(1, 0, 0)).forGetter(OptiFineSkyLayer::getAxis),
            Loop.CODEC.optionalFieldOf("loop", Loop.DEFAULT).forGetter(OptiFineSkyLayer::getLoop),
            Codec.FLOAT.optionalFieldOf("transition", 1.0F).forGetter(OptiFineSkyLayer::getTransition),
            Weather.CODEC.listOf().optionalFieldOf("weathers", ImmutableList.of(Weather.NO_PRECIPITATION)).forGetter(OptiFineSkyLayer::getWeathers)
    ).apply(instance, OptiFineSkyLayer::new));

    private final ResourceLocation source;
    private final boolean biomeInclusion;
    private final List<ResourceLocation> biomes;
    private final List<RangeEntry> heights;
    private final OptiFineBlend blend;
    private final LegacyFade fade;
    private final boolean rotate;
    private final float speed;
    private final Vector3f axis;
    private final Loop loop;
    private final float transition;
    private final List<Weather> weathers;
    public float conditionAlpha = -1;

    public OptiFineSkyLayer(ResourceLocation source, boolean biomeInclusion, List<ResourceLocation> biomes, List<RangeEntry> heights, OptiFineBlend blend, LegacyFade fade, boolean rotate, float speed, Vector3f axis, Loop loop, float transition, List<Weather> weathers) {
        this.source = source; this.biomeInclusion = biomeInclusion; this.biomes = biomes;
        this.heights = heights; this.blend = blend; this.fade = fade; this.rotate = rotate;
        this.speed = speed; this.axis = axis; this.loop = loop; this.transition = transition; this.weathers = weathers;
    }

    public void tick(Level level) { conditionAlpha = getPositionBrightness(level); }

    public void render(Level level, PoseStack poseStack, int timeOfDay, float skyAngle, float rainGradient, float thunderGradient) {
        float alpha = Mth.clamp(conditionAlpha * getWeatherAlpha(rainGradient, thunderGradient) * getFadeAlpha(timeOfDay), 0.0F, 1.0F);
        if (alpha < 1e-4F) return;

        RenderPipeline pipeline = blend.getPipeline();
        Vector4f color = blend.getColor(alpha);
        RenderSystem.setShaderColor(color.x(), color.y(), color.z(), color.w());

        // Push poseStack transforms onto the ModelViewStack so the pipeline picks them up
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(poseStack.last().pose());
        if (rotate) modelView.rotate(Axis.of(axis).rotationDegrees(getAngle(level, skyAngle)));

        // Pre-compute face orientation matrices
        Matrix4fStack tmp = new Matrix4fStack(8);
        Matrix4f[] faceMatrix = new Matrix4f[6];
        tmp.rotate(Axis.XP.rotationDegrees(90.0F));
        tmp.rotate(Axis.ZP.rotationDegrees(-90.0F));
        faceMatrix[4] = new Matrix4f(tmp);
        tmp.pushMatrix(); tmp.rotate(Axis.XP.rotationDegrees(90.0F)); faceMatrix[1] = new Matrix4f(tmp); tmp.popMatrix();
        tmp.pushMatrix(); tmp.rotate(Axis.XP.rotationDegrees(-90.0F)); faceMatrix[0] = new Matrix4f(tmp); tmp.popMatrix();
        tmp.rotate(Axis.ZP.rotationDegrees(90.0F)); faceMatrix[5] = new Matrix4f(tmp);
        tmp.rotate(Axis.ZP.rotationDegrees(90.0F)); faceMatrix[2] = new Matrix4f(tmp);
        tmp.rotate(Axis.ZP.rotationDegrees(90.0F)); faceMatrix[3] = new Matrix4f(tmp);

        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 24)) {
            BufferBuilder builder = new BufferBuilder(byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (int side = 0; side < 6; side++) {
                float u = (float)(side % 3) / 3.0F;
                float v = (float)(side / 3) / 2.0F;
                Matrix4f m = faceMatrix[side];
                builder.addVertex(m, -100, -100, -100).setUv(u, v);
                builder.addVertex(m, -100, -100,  100).setUv(u, v + 0.5F);
                builder.addVertex(m,  100, -100,  100).setUv(u + 0.333F, v + 0.5F);
                builder.addVertex(m,  100, -100, -100).setUv(u + 0.333F, v);
            }
            try (MeshData meshData = builder.buildOrThrow()) {
                GpuDevice device = RenderSystem.getDevice();
                CommandEncoder encoder = device.createCommandEncoder();
                try (GpuBuffer vertexBuffer = device.createBuffer(
                        () -> "nuit_interop_sky",
                        BufferType.VERTICES, BufferUsage.DYNAMIC_WRITE,
                        meshData.vertexBuffer())) {
                    RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
                    try (RenderPass pass = encoder.createRenderPass(
                            target.getColorTexture(), OptionalInt.empty(),
                            target.getDepthTexture(), OptionalDouble.empty())) {
                        pass.setPipeline(pipeline);
                        pass.setVertexBuffer(0, vertexBuffer);
                        RenderSystem.AutoStorageIndexBuffer idxBuf = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                        pass.setIndexBuffer(idxBuf.getBuffer(24), idxBuf.type());
                        GpuTexture tex = Minecraft.getInstance().getTextureManager().getTexture(source).getTexture();
                        pass.bindSampler("Sampler0", tex);
                        pass.drawIndexed(0, 36);
                    }
                }
            }
        }

        modelView.popMatrix();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private float getAngle(Level level, float skyAngle) {
        float start = 0;
        if (speed != Math.round(speed)) {
            long day = (level.dayTime() + 18000L) / 24000L;
            start = (float)((day * (speed % 1.0F)) % 1.0);
        }
        return -360.0F * (start + skyAngle * speed);
    }

    private boolean getConditionCheck(Level level) {
        Entity cam = Minecraft.getInstance().getCameraEntity();
        if (cam == null) return false;
        BlockPos pos = cam.getOnPos();
        if (!biomes.isEmpty()) {
            Holder<Biome> biome = level.getBiome(pos);
            if (!biome.isBound()) return false;
            if (!(biomeInclusion && biomes.contains(level.getBiome(cam.blockPosition()).unwrapKey().orElseThrow().location()))) return false;
        }
        return heights == null || Utils.checkRanges(pos.getY(), heights, false);
    }

    private float getPositionBrightness(Level level) {
        if (biomes.isEmpty() && heights.isEmpty()) return 1.0F;
        if (conditionAlpha == -1) return getConditionCheck(level) ? 1.0F : 0.0F;
        return Utils.calculateConditionAlphaValue(1.0F, 0.0F, conditionAlpha, (int)(transition * 20), getConditionCheck(level));
    }

    private float getWeatherAlpha(float rain, float thunder) {
        float a = 0;
        if (weathers.contains(Weather.NO_PRECIPITATION)) a += 1.0F - rain;
        if (weathers.contains(Weather.WORLD_PRECIPITATION)) a += rain - thunder;
        if (weathers.contains(Weather.WORLD_THUNDERSTORM)) a += thunder;
        return Mth.clamp(a, 0.0F, 1.0F);
    }

    private float getFadeAlpha(int timeOfDay) {
        if (fade.isAlwaysOn()) return 1.0F;
        return me.flashyreese.mods.nuit_interop_continued.utils.Utils.calculateFadeAlphaValue(1.0F, 0.0F, timeOfDay, fade.getStartFadeIn(), fade.getEndFadeIn(), fade.getStartFadeOut(), fade.getEndFadeOut());
    }

    public boolean isActive(long timeOfDay, int clamped) {
        if (!fade.isAlwaysOn() && me.flashyreese.mods.nuit_interop_continued.utils.Utils.isInTimeInterval(clamped, fade.getEndFadeOut(), fade.getStartFadeIn())) return false;
        if (loop.getRanges() != null && !loop.getRanges().isEmpty()) {
            long adj = timeOfDay - (long) fade.getStartFadeIn();
            while (adj < 0) adj += 24000L * (int) loop.getDays();
            int day = (int)((adj / 24000L) % (int) loop.getDays());
            return Utils.checkRanges(day, loop.getRanges(), false);
        }
        return true;
    }

    public ResourceLocation getSource() { return source; }
    public boolean isBiomeInclusion() { return biomeInclusion; }
    public List<ResourceLocation> getBiomes() { return biomes; }
    public List<RangeEntry> getHeights() { return heights; }
    public OptiFineBlend getBlend() { return blend; }
    public LegacyFade getFade() { return fade; }
    public boolean isRotate() { return rotate; }
    public float getSpeed() { return speed; }
    public Vector3f getAxis() { return axis; }
    public Loop getLoop() { return loop; }
    public float getTransition() { return transition; }
    public List<Weather> getWeathers() { return weathers; }
    public void setConditionAlpha(float v) { conditionAlpha = v; }
}
