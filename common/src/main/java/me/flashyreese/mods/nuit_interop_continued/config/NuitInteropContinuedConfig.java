package me.flashyreese.mods.nuit_interop_continued.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Modifier;

public class NuitInteropContinuedConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Nuit-Interop-Continued Config");
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static final NuitInteropContinuedConfig INSTANCE = load(
            Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("nuit-interop-continued.json").toFile());

    public boolean interoperability = true;
    public boolean debugMode = false;
    public boolean preferNuitNative = true;
    public boolean processOptiFine = true;
    public boolean processMCPatcher = false;
    private File file;

    public static NuitInteropContinuedConfig load(File file) {
        NuitInteropContinuedConfig config;
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                config = GSON.fromJson(reader, NuitInteropContinuedConfig.class);
            } catch (Exception e) {
                LOGGER.error("Could not parse config, falling back to defaults!", e);
                config = new NuitInteropContinuedConfig();
            }
        } else {
            config = new NuitInteropContinuedConfig();
        }
        config.file = file;
        config.writeChanges();
        return config;
    }

    public void writeChanges() {
        File dir = this.file.getParentFile();
        if (!dir.exists()) dir.mkdirs();
        try (FileWriter writer = new FileWriter(this.file)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            throw new RuntimeException("Could not save config", e);
        }
    }
}
