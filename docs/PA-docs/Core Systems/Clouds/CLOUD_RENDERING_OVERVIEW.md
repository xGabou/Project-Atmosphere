# Cloud Rendering — Current State Overview

Last verified: 2026-08-15, against a live `Forge-1.20.1` dev client. This is the entry point for
cloud rendering work — read this first. Everything else cloud-related in this folder that predates
2026-08-14 has been moved to `archived/` because it describes a pre-implementation planning phase,
a since-deleted shader, or an iteration log whose claims were never checked against a real render.
See "What's archived, and why" at the bottom before trusting anything older.

## Data model (still accurate — unchanged by this pass)

See `CLOUD_CANONICAL_ARCHITECTURE.md` in this folder for the full contract. Short version:

- **Persistent weather truth:** `CloudRegionState` / `CloudClusterState`.
- **Render derivation:** `CloudField` / `CloudFieldSnapshot` — smooths its source for presentation,
  never invents an unrelated weather population.
- **Render detail:** deterministic `CloudletLayout` derived from a field seed.
- **Severe-convection derivative:** `CloudCell`, reconciled from current fields.
- Client density/whiteout truth is whatever representation actually completed the current frame's
  composite.

## Render pipeline — two renderers, one active at a time

There are **two independent, fully-built volumetric raymarchers** in this codebase. Which one owns
the sky is resolved by `ClientCloudRenderOwnership.resolve()` and is not visible on screen — check
with `/pa system cloudStatus` (`cloudOwner=`).

| | Field (legacy/fallback) | Atmosphere (current, sophisticated) |
|---|---|---|
| Shader | `cloud_field_volume.fsh` (694 lines) | `cloud_atmosphere_volume.fsh` (~3,900 lines) |
| Java | `clouds/client/render/field/*` | `clouds/client/render/volumetric/*` |
| Lighting | Hardcoded static direction, no phase function, no self-shadow march | Real sun/moon direction, dual-lobe Henyey-Greenstein phase, Hillaire multi-scattering octaves, Beer-powder, temporal reprojection |
| `cloudOwner` value | `PA_FIELD_FALLBACK` | `PA_VOLUMETRIC` |

`PA_VOLUMETRIC` is preferred whenever `CLOUD_VOLUMETRIC_RENDERER_ENABLED` +
`CLOUD_FIELD_RENDERER_ENABLED` are both true (default) and the shader compiled successfully. If
you're debugging "the clouds look wrong" and haven't checked which of these two is actually on
screen, check that first — it changes which shader file is even worth reading.

**Ownership is also gated ahead of that:** whenever the Simple Clouds mod is loaded,
`CloudBackendResolver`/`ClientCloudRenderOwnership` return `SIMPLE_CLOUDS`/`VANILLA`
unconditionally — neither native renderer is registered at all. There's no override to run PA's
native renderer while SC stays installed. `cloudMode: HYBRID` is dead code (behaves identically to
`FULL`); `mods.toml` declares Simple Clouds `mandatory = false` (the FAQ doc's "mandatory
dependency" claim is stale).

## Current known issues (live-verified 2026-08-14/15)

Full detail, evidence screenshots, and exact code references are in the two dated reports in this
folder — this is the short version.

1. **Storm silhouettes are smooth, textureless domes — partially improved, not solved.**
   `cloud_atmosphere_volume.fsh`'s edge-erosion noise was capped at a max 32% density reduction, a
   leftover safety clamp from a past "noise eating holes through cloud cores" regression. A scoped
   fix (storm profiles only, erosion coefficient and floor both raised) is applied and
   hot-reload-verified against the same coordinates before/after — real improvement, still not
   billowy. See `PA_VOLUMETRIC_LIVE_RENDER_AUDIT_2026-08-14.md`.

2. **The structured multi-tier storm system never activates. Root cause confirmed, not fixed.**
   `stormStructureShape()` (the "real" BASE/CORE/TOWER/CROWN/ANVIL system, as opposed to the smooth
   generic fallback) is architecturally unreachable for any `PA_CLUSTER`-sourced storm — the
   standard source for native storms. Confirmed live via a diagnostic readout
   (`hasSevereStructures=false`), not inferred. Root cause: structural roles are only ever assigned
   through cloudlet generation, and cloudlet generation is deliberately disabled for `PA_CLUSTER`
   fields (to avoid duplicating "already authoritative" cluster geometry) — but nothing was ever
   built to give clusters their own structured render cells instead. Two fix directions are
   proposed, neither implemented yet. See `PA_STORM_STRUCTURE_PIPELINE_INVESTIGATION_2026-08-14.md`.

3. **Two fully-built parallel renderers**, as above — increases the odds any given debugging pass
   edits the one that isn't on screen.

4. **The repo's own `gradle/wrapper/gradle-wrapper.jar`/`.properties` are gitignored** and were
   absent from this checkout; a clean clone cannot run `gradlew` directly. Worked around by pointing
   at a cached Gradle 8.8 install. Worth fixing at the repo level.

5. **Existing saves under `run/saves/` are all `1.21.1`** (from `NeoForge-1.21.1` branch work) and
   are not compatible with a `Forge-1.20.1` client — don't open them with this branch's client.

## What's archived, and why

Everything in `archived/` is kept for history, not deleted, but should not be treated as current:

- **Pre-implementation planning docs** (`CloudField*.md`, `1NewCloudSystem.md`,
  `cloud_backend_start_here.md`, `cloud_render_data_contract.md`,
  `cloud_renderer_integration_points.md`, `cloud_system_map.md`,
  `cloud_type_evolution_architecture.md`, `phase0_cloud_boundary/`) — all written before the native
  renderer existed. Several say so explicitly ("does not replace the current renderer... yet",
  "not the final volumetric renderer"). The thing they were planning now exists and works
  differently in several ways than what was originally sketched.
- **Iteration/remediation logs** (`CLOUD_RENDERING_REMEDIATION_LOG.md`,
  `NATIVE_CLOUD_VISUAL_IMPROVEMENT_LOG.md`, `NATIVE_CLOUD_VISUAL_IMPROVEMENT_REPORT.md`,
  `CLOUD_REMEDIATION_FINAL_REPORT.md`, `PA_CLOUDS_AUDIT.md`) — process logs of past debugging
  passes, several explicitly marked "in-game pending" or admitting no capture campaign was ever run.
  `NATIVE_CLOUD_VISUAL_IMPROVEMENT_REPORT.md` has a genuinely useful confirmed-defects table if
  you're looking for older symptom descriptions, but none of it has been re-verified against the
  current shader — treat every claim in there as unconfirmed until re-checked live.
- **`PA_CLOUD_SHAPE_RESEARCH_VISUAL_REWORK_REPORT.md`** — describes work against `cloud_volume.fsh`,
  which no longer exists in the codebase. Notably, the exact "cylinder/capsule cloud" shape this
  report describes fixing has resurfaced in the current pipeline (see Issue 2 above) — the fix
  didn't carry forward when the renderer was rebuilt around the weather-map system.
- **Old crash/shadow reports** (`cloud-shadow-crash-fix-report-2026-06-15.md`,
  `cloud-shadow-gpu-upload-removal-report-2026-06-15.md`, `latest-log-check-2026-06-15.md`) —
  reference class names (`CloudShadowRenderer`, `CloudRenderer`, `CloudRenderHook`) that no longer
  exist under those names in the current package layout.
- **Phase/migration reports** (`PA_NATIVE_CLOUD_SPAWN_REPORT.md`,
  `PA_PHASE7_PROGRESSIVE_LONG_DISTANCE_CLOUD_VISIBILITY_REPORT.md`,
  `PA_SIMPLE_CLOUDS_IDENTITY_ROLLBACK_AUDIT.md`,
  `PROJECT_ATMOSPHERE_CLOUD_BACKEND_MIGRATION_REPORT.md`,
  `PROJECT_ATMOSPHERE_PHASE5_EXECUTION_REPORT.md`, `PROJECT_ATMOSPHERE_PHASE5_MORPHOLOGY_AUDIT.md`)
  — point-in-time completion logs for backend/migration work, superseded by the current state of
  the code.
- **Scratch files** (`temp_remaining_files*.txt`, `temp_risky_files.txt`,
  `project_atmosphere_cloud_layout_docs.zip`) — leftovers from an unrelated past cleanup pass, not
  documentation at all.

## Out of scope for this cleanup

The pressure/season/cyclone/weathercell/severe-weather reports still sitting in this folder
(`PA_ATMOSPHERE_STABILIZATION_PASS_REPORT.md`, `PA_CYCLONE_*`, `PA_LIVE_ATMOSPHERE_PERSISTENCE_REPORT.md`,
`PA_PHASE1-4_*`, `PA_PHASE6_7_SHARED_FOUNDATION_REPORT.md`, `PA_PRESSURE_DYNAMICS_SEASON_DRIFT_AUDIT.md`,
`PA_SEVERE_WEATHER_PLANNING_REPORT.md`, `PA_TASK2B_SEASONAL_DRIFT_REPORT.md`,
`PA_TASK3_WEATHERCELL_AND_CLIENT_GATING_REPORT.md`,
`PROJECT_ATMOSPHERE_PRESSURE_TARGET_LIFECYCLE_FIX_REPORT.md`) are about the weather **simulation**
backend, not rendering. This pass didn't touch them — everything in this conversation's verified
knowledge is about the render pipeline specifically, and I don't have live-verified current
knowledge of the simulation side to judge what's still accurate there. They're left in place,
untouched, neither endorsed as current nor archived as stale.
