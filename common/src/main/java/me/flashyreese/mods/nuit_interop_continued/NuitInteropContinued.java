package me.flashyreese.mods.nuit_interop_continued;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.flashyreese.mods.nuit.SkyboxManager;
import me.flashyreese.mods.nuit.skybox.SkyboxType;
import me.flashyreese.mods.nuit_interop_continued.config.NuitInteropContinuedConfig;
import me.flashyreese.mods.nuit_interop_continued.sky.OptiFineCustomSky;
import me.flashyreese.mods.nuit_interop_continued.utils.ResourceManagerHelper;
import me.flashyreese.mods.nuit_interop_continued.utils.Utils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NuitInteropContinued {
    public static final String MOD_ID = "nuit_interop_continued";
    private static final Logger LOGGER = LoggerFactory.getLogger("Nuit-Interop-Continued");

    private static final String OPTIFINE_SKY_PARENT = "optifine/sky";
    private static final Pattern OPTIFINE_SKY_PATTERN = Pattern.compile("optifine/sky/(?<world>\\w+)/(?<name>\\w+).properties$");
    private static final String MCPATCHER_SKY_PARENT = "mcpatcher/sky";
    private static final Pattern MCPATCHER_SKY_PATTERN = Pattern.compile("mcpatcher/sky/(?<world>\\w+)/(?<name>\\w+).properties$");

    private static final SkyboxType<OptiFineCustomSky> OPTIFINE_CUSTOM_SKY_TYPE =
            SkyboxType.register(new SkyboxType<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "optifine-custom-sky"), 1, OptiFineCustomSky.CODEC));

    public static void init() {
        LOGGER.info("Nuit-Interop-Continued initialized. Continuing where Nuit-Interop left off (credit: FlashyReese).");
    }

    private static NuitInteropContinued instance;
    public static NuitInteropContinued getInstance() {
        if (instance == null) instance = new NuitInteropContinued();
        return instance;
    }

    public void inject(ResourceManager manager) {
        if (!NuitInteropContinuedConfig.INSTANCE.interoperability) return;
        if (NuitInteropContinuedConfig.INSTANCE.preferNuitNative && !SkyboxManager.getInstance().getSkyboxMap().isEmpty()) {
            LOGGER.info("Nuit native skyboxes detected — skipping OptiFine/MCPatcher conversion.");
            return;
        }
        LOGGER.warn("Nuit-Interop-Continued: converting MCPatcher/OptiFine skyboxes to Nuit format...");
        LOGGER.warn("Visual issues may occur. Do not report these to Nuit or resource pack authors.");
        SkyboxManager.getInstance().clearSkyboxes();
        convert(new ResourceManagerHelper(manager));
    }

    private void convert(ResourceManagerHelper helper) {
        if (NuitInteropContinuedConfig.INSTANCE.processOptiFine) convertNamespace(helper, OPTIFINE_SKY_PARENT, OPTIFINE_SKY_PATTERN);
        if (NuitInteropContinuedConfig.INSTANCE.processMCPatcher) convertNamespace(helper, MCPATCHER_SKY_PARENT, MCPATCHER_SKY_PATTERN);
    }

    private void convertNamespace(ResourceManagerHelper helper, String parent, Pattern pattern) {
        JsonArray overworldLayers = new JsonArray();
        JsonArray endLayers = new JsonArray();

        helper.searchIn(parent)
                .filter(id -> id.getPath().endsWith(".properties"))
                .sorted(Comparator.comparing(ResourceLocation::getPath, (a, b) -> compareSkyboxIds(a, b, pattern)))
                .forEach(id -> processSkybox(helper, id, pattern, overworldLayers, endLayers));

        if (!overworldLayers.isEmpty()) createAndAddSkybox("minecraft:overworld", "optifine-overworld", overworldLayers);
        if (!endLayers.isEmpty()) createAndAddSkybox("minecraft:the_end", "optifine-end", endLayers);
    }

    private int compareSkyboxIds(String id1, String id2, Pattern pattern) {
        Matcher m1 = pattern.matcher(id1), m2 = pattern.matcher(id2);
        if (m1.find() && m2.find()) {
            int n1 = Utils.parseInt(m1.group("name").replace("sky", ""), -1);
            int n2 = Utils.parseInt(m2.group("name").replace("sky", ""), -1);
            if (n1 >= 0 && n2 >= 0) return Integer.compare(n1, n2);
        }
        return 0;
    }

    private void processSkybox(ResourceManagerHelper helper, ResourceLocation id, Pattern pattern, JsonArray overworldLayers, JsonArray endLayers) {
        Matcher matcher = pattern.matcher(id.getPath());
        if (!matcher.find()) return;
        String world = matcher.group("world");
        String name = matcher.group("name");
        if (world == null || name == null) return;
        if (name.equals("moon_phases") || name.equals("sun")) {
            LOGGER.info("Skipping {}: moon_phases/sun replacements not supported", id);
            return;
        }
        LOGGER.info("Converting {} to Nuit format...", id);
        Properties props = loadProperties(helper, id);
        if (props == null) return;
        JsonObject json = Utils.convertOptiFineSkyProperties(helper, props, id);
        if (json == null) return;
        if ("world0".equals(world)) overworldLayers.add(json);
        else if ("world1".equals(world)) endLayers.add(json);
    }

    private Properties loadProperties(ResourceManagerHelper helper, ResourceLocation id) {
        try (InputStream in = helper.getInputStream(id)) {
            if (in == null) { if (NuitInteropContinuedConfig.INSTANCE.debugMode) LOGGER.error("Cannot read: {}", id); return null; }
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            if (NuitInteropContinuedConfig.INSTANCE.debugMode) LOGGER.error("Error loading: {}", id);
            return null;
        }
    }

    private void createAndAddSkybox(String world, String name, JsonArray layers) {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("type", MOD_ID + ":optifine-custom-sky");
        json.add("layers", layers);
        json.addProperty("world", world);
        SkyboxManager.getInstance().addSkybox(ResourceLocation.fromNamespaceAndPath(MOD_ID, name), json);
    }
}
