package me.flashyreese.mods.nuit_interop_continued.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import me.flashyreese.mods.nuit.components.RangeEntry;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class Utils {
    private static final Pattern OPTIFINE_RANGE_SEPARATOR = Pattern.compile("(\\d|\\))-(\\d|\\()");

    public static JsonObject convertOptiFineSkyProperties(ResourceManagerHelper resourceManagerHelper, Properties properties, ResourceLocation propertiesResourceLocation) {
        JsonObject json = new JsonObject();

        ResourceLocation sourceTexture = parseSourceTexture(properties.getProperty("source", null), resourceManagerHelper, propertiesResourceLocation);
        if (sourceTexture == null) return null;
        json.addProperty("source", sourceTexture.toString());

        if (properties.containsKey("blend")) {
            json.addProperty("blend", properties.getProperty("blend"));
        }

        JsonObject fade = new JsonObject();
        if (properties.containsKey("startFadeIn") && properties.containsKey("endFadeIn") && properties.containsKey("endFadeOut")) {
            int startFadeIn = Objects.requireNonNull(toTickTime(properties.getProperty("startFadeIn"))).intValue();
            int endFadeIn = Objects.requireNonNull(toTickTime(properties.getProperty("endFadeIn"))).intValue();
            int endFadeOut = Objects.requireNonNull(toTickTime(properties.getProperty("endFadeOut"))).intValue();
            int startFadeOut;
            if (properties.containsKey("startFadeOut")) {
                startFadeOut = Objects.requireNonNull(toTickTime(properties.getProperty("startFadeOut"))).intValue();
            } else {
                startFadeOut = endFadeOut - (endFadeIn - startFadeIn);
                if (startFadeIn <= startFadeOut && endFadeIn >= startFadeOut) startFadeOut = endFadeOut;
            }
            fade.addProperty("startFadeIn", normalizeTickTime(startFadeIn));
            fade.addProperty("endFadeIn", normalizeTickTime(endFadeIn));
            fade.addProperty("startFadeOut", normalizeTickTime(startFadeOut));
            fade.addProperty("endFadeOut", normalizeTickTime(endFadeOut));
        } else {
            fade.addProperty("alwaysOn", true);
        }
        json.add("fade", fade);

        if (properties.containsKey("speed")) {
            json.addProperty("speed", Float.parseFloat(properties.getProperty("speed", "1")) * -1);
        }
        if (properties.containsKey("rotate")) {
            json.addProperty("rotate", Boolean.parseBoolean(properties.getProperty("rotate", "true")));
        }
        if (properties.containsKey("transition")) {
            json.addProperty("transition", Integer.parseInt(properties.getProperty("transition", "1")));
        }
        if (properties.containsKey("axis")) {
            String[] axis = properties.getProperty("axis").trim().replaceAll(" +", " ").split(" ");
            if (axis.length >= 3) {
                float x = Float.parseFloat(axis[0]);
                float y = Float.parseFloat(axis[1]);
                float z = Float.parseFloat(axis[2]);
                JsonArray axisArray = new JsonArray();
                axisArray.add(z); axisArray.add(y); axisArray.add(-x);
                json.add("axis", axisArray);
            }
        }
        if (properties.containsKey("weather")) {
            JsonArray jsonWeather = new JsonArray();
            String[] weathers = properties.getProperty("weather").split(" ");
            if (weathers.length > 0) Arrays.stream(weathers).forEach(jsonWeather::add);
            else jsonWeather.add("clear");
            json.add("weathers", jsonWeather);
        }
        if (properties.containsKey("biomes")) {
            String biomesStr = properties.getProperty("biomes");
            if (biomesStr.startsWith("!")) { json.addProperty("biomeInclusion", false); biomesStr = biomesStr.substring(1); }
            String[] biomes = biomesStr.split(" ");
            if (biomes.length > 0) {
                JsonArray jsonBiomes = new JsonArray();
                Arrays.stream(biomes).filter(ResourceLocation::isValidPath).forEach(jsonBiomes::add);
                json.add("biomes", jsonBiomes);
            }
        }
        if (properties.containsKey("heights")) {
            List<RangeEntry> ranges = parseRangeEntriesNegative(properties.getProperty("heights"));
            if (!ranges.isEmpty()) {
                JsonArray arr = new JsonArray();
                ranges.forEach(r -> { JsonObject o = new JsonObject(); o.addProperty("min", r.min()); o.addProperty("max", r.max()); arr.add(o); });
                json.add("heights", arr);
            }
        }
        if (properties.containsKey("days")) {
            List<RangeEntry> ranges = parseRangeEntries(properties.getProperty("days"));
            if (!ranges.isEmpty()) {
                JsonObject loopObj = new JsonObject();
                JsonArray loopRange = new JsonArray();
                ranges.forEach(r -> { JsonObject o = new JsonObject(); o.addProperty("min", r.min()); o.addProperty("max", r.max()); loopRange.add(o); });
                int value = properties.containsKey("daysLoop") ? parseInt(properties.getProperty("daysLoop"), 8) : 8;
                loopObj.addProperty("days", value);
                loopObj.add("ranges", loopRange);
                json.add("loop", loopObj);
            }
        }
        return json;
    }

    public static ResourceLocation parseSourceTexture(String source, ResourceManagerHelper helper, ResourceLocation propertiesId) {
        String namespace, path;
        if (source == null) {
            namespace = propertiesId.getNamespace();
            path = propertiesId.getPath().replace(".properties", ".png");
        } else if (source.startsWith("./")) {
            namespace = propertiesId.getNamespace();
            String fileName = propertiesId.getPath().split("/")[propertiesId.getPath().split("/").length - 1];
            path = propertiesId.getPath().replace(fileName, source.substring(2));
        } else {
            String[] parts = source.split("/", 3);
            if (parts.length == 3 && parts[0].equals("assets")) { namespace = parts[1]; path = parts[2]; }
            else {
                ResourceLocation parsed = ResourceLocation.tryParse(source);
                if (parsed == null) return null;
                namespace = parsed.getNamespace(); path = parsed.getPath();
            }
        }
        try {
            ResourceLocation textureId = ResourceLocation.fromNamespaceAndPath(namespace, path);
            InputStream in = helper.getInputStream(textureId);
            if (in == null) return null;
            try { in.close(); } catch (Exception ignored) {}
            return textureId;
        } catch (ResourceLocationException e) { return null; }
    }

    public static Number toTickTime(String time) {
        String[] parts = time.split(":");
        if (parts.length == 2) {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return h * 1000 + (m / 0.06F) - 6000;
        }
        return null;
    }

    public static int normalizeTickTime(int tickTime) {
        int r = tickTime % 24000;
        return r < 0 ? r + 24000 : r;
    }

    public static boolean isInTimeInterval(int currentTime, int startTime, int endTime) {
        if (currentTime < 0 || currentTime >= 24000) throw new RuntimeException("Invalid time: " + currentTime);
        return startTime <= endTime ? (currentTime >= startTime && currentTime <= endTime) : (currentTime >= startTime || currentTime <= endTime);
    }

    public static float calculateFadeAlphaValue(float maxAlpha, float minAlpha, int currentTime, int startFadeIn, int endFadeIn, int startFadeOut, int endFadeOut) {
        if (isInTimeInterval(currentTime, endFadeIn, startFadeOut)) return maxAlpha;
        if (isInTimeInterval(currentTime, startFadeIn, endFadeIn)) {
            int dur = calculateCyclicTimeDistance(startFadeIn, endFadeIn);
            int passed = calculateCyclicTimeDistance(startFadeIn, currentTime);
            return minAlpha + ((float) passed / dur) * (maxAlpha - minAlpha);
        }
        if (isInTimeInterval(currentTime, startFadeOut, endFadeOut)) {
            int dur = calculateCyclicTimeDistance(startFadeOut, endFadeOut);
            int passed = calculateCyclicTimeDistance(startFadeOut, currentTime);
            return maxAlpha + ((float) passed / dur) * (minAlpha - maxAlpha);
        }
        return minAlpha;
    }

    public static int calculateCyclicTimeDistance(int startTime, int endTime) {
        return (endTime - startTime + 24000) % 24000;
    }

    public static List<RangeEntry> parseRangeEntries(String source) {
        List<RangeEntry> list = new ArrayList<>();
        for (String part : source.trim().split(" ")) {
            if (part.contains("-")) {
                String[] p = part.split("-");
                if (p.length == 2) { int a = parseInt(p[0],-1), b = parseInt(p[1],-1); if (a>=0&&b>=0) list.add(new RangeEntry(a,b)); }
            } else { int v = parseInt(part,-1); if (v>=0) list.add(new RangeEntry(v,v)); }
        }
        return list;
    }

    public static List<RangeEntry> parseRangeEntriesNegative(String source) {
        List<RangeEntry> list = new ArrayList<>();
        for (String part : source.trim().split(" ")) {
            String s = OPTIFINE_RANGE_SEPARATOR.matcher(part).replaceAll("$1=$2");
            if (s.contains("=")) {
                String[] p = s.split("=");
                if (p.length == 2) {
                    int j = parseInt(stripBrackets(p[0]), Integer.MIN_VALUE);
                    int k = parseInt(stripBrackets(p[1]), Integer.MIN_VALUE);
                    if (j!=Integer.MIN_VALUE && k!=Integer.MIN_VALUE) list.add(new RangeEntry(Math.min(j,k), Math.max(j,k)));
                }
            } else { int v = parseInt(stripBrackets(part), Integer.MIN_VALUE); if (v!=Integer.MIN_VALUE) list.add(new RangeEntry(v,v)); }
        }
        return list;
    }

    private static String stripBrackets(String str) {
        return str.startsWith("(") && str.endsWith(")") ? str.substring(1, str.length()-1) : str;
    }

    public static int parseInt(String str, int def) {
        try { return Integer.parseInt(str); } catch (Exception e) { return def; }
    }

    public static Codec<Double> getClampedDouble(double min, double max) {
        return Codec.DOUBLE.xmap(f -> Mth.clamp(f, min, max), Function.identity());
    }
}
