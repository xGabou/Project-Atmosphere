# 🌩️ Project Atmosphere – TODO Checklist

## ☁️ Cloud System

- [ ] Implement GeckoLib CloudEntity
- [ ] Add cloud variants: cumulus, cirrus, fog
- [ ] Add wind drifting logic
- [ ] Detect and merge cloud entities
- [ ] Save merged state in NBT with original identity
- [ ] Restore original clouds after split

## 🌫️ Fog System

- [ ] Add morning fog when temperature < 10°C
- [ ] Customize fog by biome: jungle, swamp, plains
- [ ] Fade fog after sunrise or player descent
- [ ] Use safe Forge hook to override vanilla fog

## 🧪 Shader Compatibility

- [ ] Do not override sky renderer (retain shader support)
- [ ] Add config option: cloudMode FULL | HYBRID | VANILLA
- [ ] Ensure visual blend for vanilla + custom clouds
- [ ] Test with Complementary, BSL, SEUS, Iris+Sodium

## 🌦️ Weather Manager

- [ ] Create server-authoritative WeatherManager
- [ ] Override vanilla rain/thunder states
- [ ] Sync weather state to clients
- [ ] Simulate 7-day forecast based on season + temperature
- [ ] Forecast includes random ±10°C scaling
- [ ] Final displayed temp varies ±3°C daily
- [ ] Generate storms using season + wind + temperature

## 🌪️ Extreme Weather Events

- [ ] Implement TornadoEvent (wind + suction)
- [ ] Implement HurricaneEvent (wide-area wind + rain)
- [ ] Implement SandstormEvent (reduced visibility + particles)
- [ ] Implement SnowstormEvent with snow accumulation
- [ ] Handle spring melt logic for snow_pile blocks

## 🌡️ Temperature System

- [ ] Integrate Serene Seasons API
- [ ] Add fallback temperature simulation if SS missing
- [ ] Mixin to override temperature source if needed
- [ ] Expose API to other mods for temp reading
- [ ] Allow forecast-based temperature overrides

## 🌾 Rain & Crops

- [ ] Integrate with Farmer’s Delight: rain helps growth
- [ ] Track rainfall per chunk
- [ ] Convert dirt to corpse dirt if too much rain
- [ ] Only affect uncovered farmland

## ⚙️ Config & Performance

- [ ] Create projectatmosphere.toml config
- [ ] Options: fog, cloud mode, forecast deviation, event toggle
- [ ] Use patterns: Observer, Strategy, Factory
- [ ] Avoid BlockEntities — use chunk managers
- [ ] Memoize biome → weather lookup
- [ ] Separate ClientWeatherHandler and ServerWeatherHandler

## 🧩 Mod Compatibility

- [ ] Farmer’s Delight
- [ ] Naturalist
- [ ] Immersive Weathering
- [ ] Tough As Nails
- [ ] Epic Fight






Dithering, Island,pathways, stripe
thicker_stratocumulus not rainy


