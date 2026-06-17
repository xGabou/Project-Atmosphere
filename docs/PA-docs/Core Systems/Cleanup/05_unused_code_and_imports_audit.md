# Unused Code And Imports Audit

This section is conservative. It does **not** recommend deleting anything now. It identifies code that appears unused, debug-only, or misplaced so it can be moved toward a legacy section later if needed.

| File | Unused import or code item | Why it appears unused | Confidence | Classification | Should delete now | Should move to end of class later | Suggested note | Risk if removed |
|---|---|---|---|---|---|---|---|---|
| `src/main/java/net/Gabou/projectatmosphere/client/render/HudRenderTest.java` | Entire class appears to be a debug/test helper | The name strongly suggests temporary testing rather than production behavior | High | `MOVE_TO_LEGACY_SECTION_LATER` | No | Yes | Mark as debug/test-only if kept | Medium |
| `src/main/java/net/Gabou/projectatmosphere/client/render/TornadoLateRenderDiagnostics.java` | Entire class is diagnostics-oriented | It exists to exercise a specific late render path | High | `MOVE_TO_LEGACY_SECTION_LATER` | No | Yes | Keep as a debug reference, not production logic | Medium |
| `src/main/java/net/Gabou/projectatmosphere/util/ParticleAtlasDebugger.java` | Entire class appears debug-only | Debugger naming strongly implies non-core use | High | `MOVE_TO_LEGACY_SECTION_LATER` | No | Yes | Move away from normal utility surface later | Low to medium |
| `src/main/java/net/Gabou/projectatmosphere/network/FogDebugOverridePacket.java` | Debug override packet | Debug override packets are usually temporary or testing-centric | High | `REVIEW_MANUALLY` | No | Maybe | Confirm it is still needed before any cleanup | Medium |
| `src/main/java/net/Gabou/projectatmosphere/modules/tornado/TornadoDebug.java` | Debug helper class | Debug naming suggests non-core behavior | High | `MOVE_TO_LEGACY_SECTION_LATER` | No | Yes | Separate debug helpers from the main lifecycle story later | Medium |
| `src/main/java/net/Gabou/projectatmosphere/mixin/client/SimpleCloudsRendererDiagnosticsMixin.java` | Diagnostics-only mixin path | It appears to exist for instrumentation rather than core behavior | High | `MOVE_TO_LEGACY_SECTION_LATER` | No | Yes | Keep until the render boundary is stable | High |
| `src/main/resources/data/simpleclouds/cloud_types/generate_weather_enum.py` | Helper script, not runtime code | The file is a generation helper, not part of mod runtime | High | `DO_NOT_TOUCH` | No | No | Leave it as a build/data helper | Low |
| `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsRenderDiagnostics.java` | Diagnostics support class | Diagnostics naming suggests instrumentation-only code | High | `MOVE_TO_LEGACY_SECTION_LATER` | No | Yes | Keep available until new renderer boundaries exist | Medium |
| `src/main/java/net/Gabou/projectatmosphere/client/render/SkyEffectState.java` | Could be temporary staging state | The class name is generic enough that it may be doing more than it looks like | Medium | `REVIEW_MANUALLY` | No | Maybe | Verify whether it is still actively used by the render path | Medium |
| `src/main/java/net/Gabou/projectatmosphere/client/ClientSyncLock.java` | Coordination helper may be over-specialized | The lock may be used in a narrow way that could be hidden by broader handlers | Medium | `REVIEW_MANUALLY` | No | Maybe | Check call sites before any cleanup | Medium |
| `src/main/java/net/Gabou/projectatmosphere/client/render/SideInfo.java` | Small helper with unclear boundary | The name does not clearly explain ownership | Medium | `REVIEW_MANUALLY` | No | Maybe | Inspect whether it is purely a render helper or a compatibility shim | Low |
| `src/main/java/net/Gabou/projectatmosphere/client/render/VolumeBoxMesh.java` | Utility mesh helper could be reusable but hidden | The class may be a reusable geometry helper rather than a policy object | Medium | `KEEP` | No | No | Keep, but document its use sites | Low |
| `src/main/java/net/Gabou/projectatmosphere/client/crash/ProjectAtmosphereCrashHandler.java` | Crash-handling utility | Not unused, but it is a special-case helper that can be mistaken for core gameplay code | High | `KEEP` | No | No | Leave in place; just document it | High |
| `src/main/java/net/Gabou/projectatmosphere/util/AtmosphereUtils.java` | Catch-all utility naming | “Utils” classes often become dumping grounds | Medium | `REVIEW_MANUALLY` | No | Maybe | Check for broad helper growth | Medium |

## Import Audit Summary

- No confident blanket removal of imports is recommended yet.
- Several classes appear debug-oriented rather than dead.
- Anything touched by mixins, reflection, events, or data-driven runtime should be treated as `REVIEW_MANUALLY`.
- If cleanup happens later, the project should prefer moving legacy/debug sections to the end of the class rather than deleting them immediately.

