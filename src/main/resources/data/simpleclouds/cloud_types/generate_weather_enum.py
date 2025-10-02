import os
import json

CLOUD_TYPES_DIR = "."  # script runs inside cloud_types

weather_types = set()
cloud_map = {}

for file in os.listdir(CLOUD_TYPES_DIR):
    if file.endswith(".json"):
        path = os.path.join(CLOUD_TYPES_DIR, file)
        with open(path, "r", encoding="utf-8") as f:
            try:
                data = json.load(f)
                wt = data.get("weather_type", "none").upper()
                weather_types.add(wt)
                # filename without .json is your cloudId
                cloud_id = file.replace(".json", "")
                cloud_map[cloud_id] = wt
            except Exception as e:
                print(f"Error reading {file}: {e}")

# Generate Java enum
print("package net.Gabou.projectatmosphere.util;\n")
print("import net.minecraft.resources.ResourceLocation;\n")
print("import java.util.Map;")
print("import java.util.HashMap;\n")
print("public enum WeatherType {")
print("    " + ",\n    ".join(sorted(weather_types)) + ";\n")

# Generate cloudId -> WeatherType map
print("    private static final Map<ResourceLocation, WeatherType> CLOUD_MAP = new HashMap<>();\n")
print("    static {")
for cloud_id, wt in cloud_map.items():
    print(f"        CLOUD_MAP.put(new ResourceLocation(\"simpleclouds\", \"{cloud_id}\"), {wt});")
print("    }\n")
print("    public static WeatherType getWeatherType(ResourceLocation id) {")
print("        return CLOUD_MAP.getOrDefault(id, NONE);")
print("    }")
print("}")
