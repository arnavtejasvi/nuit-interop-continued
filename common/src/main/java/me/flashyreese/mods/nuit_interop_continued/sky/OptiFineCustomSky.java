package me.flashyreese.mods.nuit_interop_continued.sky;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.api.skyboxes.Skybox;
import me.flashyreese.mods.nuit.mixin.SkyRendererAccessor;
import me.flashyreese.mods.nuit_interop_continued.config.NuitInteropContinuedConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.util.List;

public class OptiFineCustomSky implements Skybox {
    public static final Codec<OptiFineCustomSky> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OptiFineSkyLayer.CODEC.listOf().optionalFieldOf("layers", ImmutableList.of()).forGetter(OptiFineCustomSky::getLayers),
            Level.RESOURCE_KEY_CODEC.fieldOf("world").forGetter(OptiFineCustomSky::getWorldResourceKey)
    ).apply(instance, OptiFineCustomSky::new));

    private final List<OptiFineSkyLayer> layers;
    private final ResourceKey<Level> worldResourceKey;
    private boolean active = true;

    public OptiFineCustomSky(List<OptiFineSkyLayer> layers, ResourceKey<Level> worldResourceKey) {
        this.layers = layers;
        this.worldResourceKey = worldResourceKey;
    }

    @Override
    public void render(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, float tickDelta, Camera camera, MultiBufferSource.BufferSource bufferSource, FogParameters fogParameters) {
        renderSky(skyRendererAccessor, poseStack, tickDelta, camera, bufferSource, fogParameters);
    }

    @Override
    public void close() {}

    private void renderSky(SkyRendererAccessor skyRendererAccessor, PoseStack poseStack, float tickDelta, Camera camera, MultiBufferSource.BufferSource bufferSource, FogParameters fogParameters) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        DimensionSpecialEffects effects = level.effects();

        if (effects.skyType() == DimensionSpecialEffects.SkyType.END) {
            ((SkyRenderer) skyRendererAccessor).renderEndSky();
            renderLayers(poseStack, level, tickDelta);
            return;
        }

        float sunAngle = level.getSunAngle(tickDelta);
        float timeOfDay = level.getTimeOfDay(tickDelta);
        float rainLevel = 1.0F - level.getRainLevel(tickDelta);
        int sunriseColor = effects.getSunriseOrSunsetColor(timeOfDay);
        int moonPhase = level.getMoonPhase();
        int skyColor = level.getSkyColor(mc.gameRenderer.getMainCamera().getPosition(), tickDelta);

        ((SkyRenderer) skyRendererAccessor).renderSkyDisc(ARGB.redFloat(skyColor), ARGB.greenFloat(skyColor), ARGB.blueFloat(skyColor));

        if (effects.isSunriseOrSunset(timeOfDay)) {
            ((SkyRenderer) skyRendererAccessor).renderSunriseAndSunset(poseStack, bufferSource, sunAngle, sunriseColor);
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        renderLayers(poseStack, level, tickDelta);
        poseStack.popPose();

        ((SkyRenderer) skyRendererAccessor).renderSunMoonAndStars(poseStack, bufferSource, timeOfDay, moonPhase, rainLevel, 0, fogParameters);
        bufferSource.endBatch();

        if (camera.getEntity().getEyePosition(tickDelta).y - level.getLevelData().getHorizonHeight(level) < 0.0) {
            ((SkyRenderer) skyRendererAccessor).renderDarkDisc();
        }
    }

    private void renderLayers(PoseStack poseStack, Level level, float tickDelta) {
        long timeOfDay = level.getDayTime();
        int clamped = (int)(timeOfDay % 24000L);
        float skyAngle = level.getTimeOfDay(tickDelta);
        float rain = level.getRainLevel(tickDelta);
        float thunder = level.getThunderLevel(tickDelta);
        if (rain > 0) thunder /= rain;
        for (OptiFineSkyLayer layer : layers) {
            if (layer.isActive(timeOfDay, clamped)) layer.render(level, poseStack, clamped, skyAngle, rain, thunder);
        }
    }

    @Override
    public void tick(ClientLevel level) {
        active = true;
        if (level.dimension() != worldResourceKey) {
            layers.forEach(l -> l.setConditionAlpha(-1.0F));
            active = false;
        } else {
            layers.forEach(l -> l.tick(level));
        }
    }

    @Override
    public boolean isActive() { return NuitInteropContinuedConfig.INSTANCE.interoperability && active; }

    public List<OptiFineSkyLayer> getLayers() { return layers; }
    public ResourceKey<Level> getWorldResourceKey() { return worldResourceKey; }
}
