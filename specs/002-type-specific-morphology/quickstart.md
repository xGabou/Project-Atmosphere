# T255 validation guide

1. Run `gradlew.bat stormMorphologyPrototypeSandbox --offline --no-daemon -PsimpleCloudsMandatory=false` for controls/anatomy/bounds, smooth-union enclosure and ray-lattice checks. The separate `stormMorphologyPrototypePreview` task writes preliminary CPU views; neither task establishes native acceptance.
2. Retain relevant checks with `gradlew.bat cloudMorphologyTopologySandbox stormMorphologySandbox --offline --no-daemon -PsimpleCloudsMandatory=false`.
3. Run `gradlew.bat generateLeanFinalShader stormVolumetricGeometrySandbox --offline --no-daemon -PsimpleCloudsMandatory=false` for shader variants and renderer regressions. This is not native CPU/GPU parity; the capture fixture reads real float GPU density for that test.
4. Use opt-in native client captures with neutral mode 1, three fixed seeds, identical scale framing and all nine requested views plus storm-specific/control arms. Reject obstructed/clipped/stale evidence.
5. Inspect against report criteria and credited IRL references. Record failures as open work, not lowered thresholds.
6. Measure real renderer GPU median/p95/worst required view against 10 ms; report hardware/resolution/timer validity and work attribution.
7. Inspect git status before handoff, keep PAchangelog.md uncommitted and defaults unchanged. No automatic commit, merge or push.

Native launch and capture/export commands are in ../001-native-storm-rendering/validation/t255-storm-prototype/README.md. The full fixture is 98 arms; PA_T255_SMOKE=1 is only 20 arms and PA_T255_LIFECYCLE=1 only three side views. The corrected back view is a common 4400-block offset for all types. Export with `export-gallery.ps1 -CaptureFolder <logged-output> -PassName <unique-name> -CaptureLog <log> -RequireComplete`, then `summarize-evidence.ps1 -CaptureLog <log> -OutputFolder <export-folder>`. Complete export rejects missing or rejected standard views; it still does not approve realism or performance. Source/log/image hashes and explicit review verdict belong to every pass.
