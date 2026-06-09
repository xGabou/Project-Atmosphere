# Install Project Atmosphere

## Prerequisites
- Minecraft `1.20.1`
- Forge `47+`
- Project Atmosphere `0.8.0.0`

## Required mods
- Project Atmosphere
- Simple Clouds
- Gabou's Libs

## Season provider note
Official documentation explicitly mentions Serene Seasons support. Current code also refuses to start without a season provider and names these accepted providers in the startup check:
- `sereneseasons`
- `projectatmospherefortfc`
- `eclipticseasons`

Official support for the non-Serene options still needs maintainer confirmation, so treat them as observed runtime behavior, not confirmed public compatibility.

## Installation order
1. Install Forge for Minecraft `1.20.1`.
2. Add the required dependency mods.
3. Add a season provider.
4. Add Project Atmosphere.
5. Add optional compatibility mods only after the core setup works.

No special jar ordering is required once the correct files are present in the `mods` folder.

## Basic verification
1. Start the game or server and confirm it reaches the main menu/world load without dependency errors.
2. Check that no startup error mentions missing `simpleclouds`, `gaboulibs`, or a missing season provider.
3. In an Overworld test world, run `/pa temperature current`.
4. If you want to test current-source fog commands, run `/pa fog spawn`.
