# 🌤 Project Atmosphere v0.5.5.0

## 🌍 New Feature: Temperature Biome Support
Project Atmosphere now fully supports **Biomes O’ Plenty**, **Atmospheric**, and **Still Life** biomes for **temperature simulation**!
- Each biome from these mods now has integrated **temperature profiles** that align with Project Atmosphere’s forecasting system.
- Added **a flexible biome registration system**, allowing you to **add your own custom biome temperature profiles** through data files or code.
- Fog, cloud density, and humidity systems will adapt automatically when temperature data is applied.

## ☁ New Cloud Types
- **Altocumulus**
- **Altostratus**
- **Cumulus Congestus**
- **Cumulus Humilis**
- **Cumulus Mediocris**
- **Nimbostratus** → now behaves realistically as a *rain-only* cloud (no thunder)
---

## 🌫 Dense Clouds (No Rain)
- **Altostratus Dry**
- **Stratocumulus Opacus**
- **Cumulus Humilis**

---

## 🌧 Clouds That Now Produce Rain
- **Floating FarLand**
- **Overcast**
- **Thicker Stratocumulus**

---

## ⚙ System and Balance Updates
- Reorganized all cloud types into **7 clear categories**:
    1. Small Clouds
    2. Cloudy
    3. Cloudy Dense *(no rain except Altocumulus)*
    4. Rainy
    5. Heavy Rain
    6. Thunderstorm
    7. Take Shelter
- Every cloud was manually reviewed and tested using the **Cloud Previewer**
- Removed obsolete dithering profiles:
    - `stripe`
    - `stripe_side`
    - `dithering`
- Added a **Cloud Region Mixin** for unique cloud IDs  
  → Improves despawn logic and network sync reliability

---

## ❄ Serene Seasons Plus Integration
- Fixed **snowfall logic** — snow now behaves correctly across all biomes
- Fixed a **tick performance issue** related to `getTemperature()` calls
- Improved sync between **Project Atmosphere** and **Serene Seasons Plus**

---

## 🧠 Performance and Stability
- Verified using **Spark Profiler**
    - Reduced tick cost and thread load
    - Smoother atmospheric updates
- Applied small optimizations and cleanup across several systems

---

## 🔮 Next Steps
- Collect feedback for cloud balance and realism
- Begin work on:
    - **Northern Lights and Rainbow system**
    - **Cloud model revamp**
    - **Tornado system revival**
