# Troubleshooting

## Startup or mod loading failure

### Wrong game version or loader
Project Atmosphere is currently documented for Minecraft `1.20.1` on Forge. Using Fabric, NeoForge, or another Minecraft version is not confirmed here.

### Missing required dependency
If the log mentions `simpleclouds` or `gaboulibs`, install those mods first. They are declared as mandatory in `mods.toml`.

### Missing season provider
Current code throws an error if no season provider is loaded. If startup fails with a message about installing Serene Seasons, ProjectAtmosphereForTFC, or Ecliptic Seasons, add one of those mods and retry.

### PA x TFC incompatibility on 0.8.0.0
The official `0.8.0.0` changelog marks PA x TFC as temporarily incompatible. If that bridge is installed and weather logic behaves incorrectly, remove it or wait for a compatibility update.

## Commands do not work

### Use the `/pa` root
Current server commands are registered under `/pa`. Start with `/pa help`, then use the feature groups:

- `/pa forecast`
- `/pa temperature`
- `/pa humidity`
- `/pa pressure`
- `/pa wind`
- `/pa fog`
- `/pa cloud`
- `/pa tornado`
- `/pa hurricane`
- `/pa system`

Legacy paths like `/pa weatherdebug` still work for now, but they are only compatibility aliases.

### Wrong dimension
Many forecast and debug commands only work in the Overworld.

### Missing permission level
Admin/debug commands such as cloud spawning, tornado spawning, hurricane spawning, and fog forcing require permission level `2`.

## Config problems

### Common config values
Use the mod's Forge common config entries for weather tuning. The schema in `data/mcp/mods/projectatmosphere/config-schema.json` maps the real keys and default values.

### Biome temperature overrides
Custom biome temperature overrides live in `config/projectatmosphere/biome_temps.json`. Invalid biome ids or malformed season ranges are skipped instead of applied.

### Dynamic Trees integration
Latest official notes say the Dynamic Trees module is still work in progress and should remain disabled.

## Legacy command notes
Old command paths are still available for compatibility. Prefer the new `/pa` tree in new docs and support answers.

## When to check known issues
Check `data/mcp/mods/projectatmosphere/known-issues.json` when:
- a compatibility problem appears after updating to `0.8.0.0`
- an admin/debug command behaves inconsistently
- an integration is mentioned in old docs but not in current release notes
