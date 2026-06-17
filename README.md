# 🌤️ Project Atmosphere

**Project Atmosphere** reimagines Minecraft’s weather system with a dynamic, modular climate simulation engine. From drifting clouds and fluctuating temperatures to atmospheric pressure and snow depth, this system introduces realism and immersion, built for performance and future expansion.

---

## ✨ Core Features

- **🌡️ Temperature Simulation**
  - Daily min/max values per biome, interpolated every 10 in-game minutes.
  - Sinusoidal temperature curve (e.g., coldest at 3am, warmest at 3pm).
  - Realistic biome profiles: hot deserts, stable frozen peaks, tropical humidity.
  - Full support for **Serene Seasons** and **Serene Seasons Extended**.

- **📅 Weekly Forecast System**
  - 7-day forecasts for temperature, humidity, and pressure per biome.
  - Stored in async-safe JSON files and used to drive all weather behaviors.
  - Forecasts evolve with spikes, natural variation, and biome-specific logic.

- **🌪️ SpikeManager & VariationGenerator**
  - Simulates heatwaves and cold drops with configurable intensity and rarity.
  - Adds micro-fluctuations to daily values for natural variability.

- **☁️ Cloud Engine (SimpleClouds Integration)**
  - Clouds spawn dynamically based on biome humidity, temperature, and pressure.
  - Region-aware logic prevents cloud overlap.
  - Fully compatible with **Simple Clouds**.
  - NBT-persistent cloud states across save/load cycles.
  - Blends with **shaderpacks** like Complementary, SEUS, and BSL.

- **❄️ Snow Accumulation**
  - Snow appears and melts based on real temperature, not just weather flags.
  - Depth varies with sunlight exposure, heat, and biome conditions.

- **🌫️ Atmospheric Pressure & Humidity**
  - Pressure forecasts influence fog and cloud behavior.
  - Humidity system tracks biome saturation and dew point.

- **🧠 Modular Architecture**
  - Forecasting and state managers run asynchronously using safe, background threads.
  - All core systems are chunk-based and independently extensible.

- **Developer & Debug Tools**
  - `/pa help`
  - `/pa status`
  - `/pa debug on|off`
  - `/pa forecast`, `/pa temperature`, `/pa humidity`, `/pa pressure`, `/pa wind`
  - `/pa fog`, `/pa cloud`, `/pa tornado`, `/pa hurricane`, `/pa system`
  - Legacy commands still work for now, but the new `/pa` tree is the primary interface.

---

## 🛠️ Compatibility

- ✅ **Minecraft Forge 1.20.1**
- ✅ **Serene Seasons** (seasonal logic)
- ✅ **Serene Seasons Extended**
- ✅ **Simple Clouds** (cloud rendering)
- ✅ **Pretty Rain** (visual rain enhancements)

---

## 🔮 Planned Expansions

> Project Atmosphere is designed to evolve with modular features. Coming soon:

- ⚡ Storm progression (rain → thunder → severe storms)
- 💧 Rain quality, evaporation, and terrain wetness
- 🌬️ Wind simulation affecting clouds, fog, particles, and gameplay
- 🌾 Crop health mechanics (drought, overwatering, stress)
- 🧊 Entity/block freezing logic based on real temperatures
- ☁️ Fog simulation tied to humidity, pressure, and time of day

---

## 📜 License

This project uses a custom license. See `LICENSE.md` for details.

---

## 💬 Contact

For feedback, bug reports, or collaboration:
- Open an issue on the GitHub repository
- Contact **Gabou** directly via Discord or GitHub

---

*Project Atmosphere — Simulating a breathing, reactive Minecraft world.*
