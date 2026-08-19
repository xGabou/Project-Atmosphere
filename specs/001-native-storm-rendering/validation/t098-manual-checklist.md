# T098 Manual Validation Checklist

**Feature**: `001-native-storm-rendering`
**Prepared**: 2026-08-19
**Status**: awaiting manual capture — **T098 is not complete**
**Gate**: T118 passed, so the reopened T098 may now be collected.

T098 is judged against the **revised two-part** morphology criteria. Artifact absence alone is no
longer sufficient: all nine positive features must be present *and* all eight rejected forms absent.

---

## 0. Before you start

1. Build and launch the native client (no Simple Clouds):

   ```powershell
   .\gradlew.bat runClient
   ```

2. Confirm the native renderer owns clouds, not the legacy or Simple Clouds path:

   ```
   /pa cloud volumetric status
   ```

   If the native renderer is not active, nothing below is valid evidence.

3. Find or wait for a severe storm (`STORM_ANVIL`). Get within the native storm detail distance
   (default 1536 blocks) so descriptors are adopted.

4. Confirm descriptors are adopted before shooting anything:

   ```
   /pa cloud volumetric diagnostics stormDensity
   ```

   If it prints `no descriptor-owned storm is currently adopted`, you are looking at the broad-map
   fallback, not the descriptor path, and the captures would not test the Phase 4S work.

**Use one storm for all seven captures.** The `group <id>` line identifies it — keep the same short
id throughout. If the storm decays and you have to switch, start the set over and note it.

---

## 1. The diagnostic command

```
/pa cloud volumetric diagnostics stormDensity
```

Prints to chat **and** to `latest.log` with a `[T098]` prefix, so you can copy values out of the log
rather than transcribing from chat. Example shape:

```text
=== T098 storm density calibration ===
camera=(1204.3, 190.0, -882.1)  descriptors=9  topologyGeneration=41
cell.density  min=0.6100  max=0.8800  mean=0.7420   (fixture reference 0.92)
envelope strength (density * detailWeight)  min=0.6100  max=0.8800  mean=0.7420
at camera: coverage=0.0000  density=0.0000  noiseBaked=true

group 3f9a11c2  distance=612m  members=9  roles[base=2,core=2,tower=2,anvil=3]
  centre=(1810, -940)  baseY=228  topY=506
  cell.density  min=0.6100  max=0.8800  mean=0.7420
    BASE  #0  cell.density = 0.8800   strength = 0.8800
    ...
```

Nearest storm group is listed first.

---

## 2. Screenshots to take

Seven captures, all of the same storm. Take the diagnostic **immediately before or after each
shot**, without moving, so the numbers match the frame.

| # | Name | What it must show | Why it matters |
|---|---|---|---|
| 1 | **FAR** | Whole storm, complete silhouette: base, towers, anvil | Silhouette curvature, balloon rejection, overall proportions |
| 2 | **SIDE** | Medium distance, full vertical structure | Tower narrowing, base→tower→anvil transitions, vertical walls |
| 3 | **UNDER** | Directly beneath, looking up | Broad connected base, underside curvature vs. flat slab, rain attachment |
| 4 | **INSIDE** | Camera inside the body | Interior density variation — the single most important capture for this correction |
| 5 | **ABOVE** | Above, looking down or diagonally | Anvil spread, upper structure, top-surface billowing |
| 6 | **LATERAL A** | Side view, one horizontal angle | Baseline for the seam comparison |
| 7 | **LATERAL B** | Same storm, 60–120° around from A | Descriptor seams and unstable silhouettes appear on angle change |

### One additional capture I recommend

| # | Name | What it must show | Why |
|---|---|---|---|
| 8 | **NEAR-EDGE** | Close to the storm's *outer boundary* — near enough that the surface fills much of the frame, but outside the body | This is where the two highest-frequency detail octaves (5.7 → 1.4 block wavelengths) are resolvable. FAR and SIDE cannot show them: at distance those bands fall below a pixel. Without it, "surface variation at multiple spatial frequencies" (FR-023) and "multi-scale billowing" can only be judged from the coarse bands, and the balloon failure this correction fixes is specifically a *fine-detail* failure. |

It costs one extra shot and it is the capture most likely to expose a remaining problem. Take it if
you can.

---

## 3. Data to record per capture

Only what T098 actually uses. Everything else is noise at this stage.

| Data | Needed? | Notes |
|---|---|---|
| `cell.density` block from the command | **Yes, every capture** | The whole point of the calibration |
| Storm group short id | **Yes, every capture** | Proves all seven frames are the same storm |
| Camera coordinates | **Yes, every capture** | The command prints them; no manual work |
| Weather / rain state | **Yes, UNDER and INSIDE only** | Rain attachment and whiteout parity are read from those two frames |
| FPS | **No** | Performance is Phase 4P / T070–T072, deliberately not gated on here |
| GPU usage | **No** | Same — and an un-instrumented GPU reading would not be comparable to the release gate anyway |

Camera coordinates and the group id both come from the command output, so in practice: **run the
command, screenshot the view, keep the log block.**

---

## 4. Density calibration table

Fill this in as you go. The goal is the observed range, not a pass/fail.

| Capture | cell.density min | max | mean | Group id | Observation |
|---|---|---|---|---|---|
| FAR | | | | | |
| SIDE | | | | | |
| UNDER | | | | | |
| INSIDE | | | | | |
| ABOVE | | | | | |
| LATERAL A | | | | | |
| LATERAL B | | | | | |
| NEAR-EDGE (optional) | | | | | |

**Summary to report back:**

- minimum observed live severe-storm `cell.density`: ______
- maximum observed live severe-storm `cell.density`: ______
- typical / representative value: ______
- materially different from the fixture's 0.92? yes / no

A value below 0.92 is **not** assumed to be a defect. Descriptor density is the authoritative
measure of how much cloud a member carries, and a lower value legitimately produces a thinner,
wispier storm. What the calibration decides is whether the deterministic fixtures are testing a
representative part of the range, or an unrepresentatively dense one.

If you can, sample **two or three different storms** at different lifecycle stages (growing, mature,
decaying) for the range — a single mature storm will not show the spread.

---

## 5. What I will evaluate the captures against

### Positive — all nine must be present (FR-023)

| # | Criterion | Best captures |
|---|---|---|
| 1 | Broad connected lower cloud base | UNDER, SIDE |
| 2 | Dense convective / core region | SIDE, INSIDE |
| 3 | Towers emerging naturally from the base | SIDE, FAR |
| 4 | Progressive vertical narrowing where appropriate | SIDE, LATERAL A/B |
| 5 | Broad upper anvil | FAR, ABOVE |
| 6 | Multi-scale billowing across the body | NEAR-EDGE, SIDE, ABOVE |
| 7 | Surface variation at multiple spatial frequencies | NEAR-EDGE, INSIDE |
| 8 | Irregular but coherent silhouette curvature | FAR, LATERAL A vs B |
| 9 | Continuous base / core / tower / anvil transitions | SIDE, FAR |

### Rejected — none may be present (FR-024)

| # | Rejected form | Best captures |
|---|---|---|
| 1 | Large smooth balloon surfaces | FAR, LATERAL A/B |
| 2 | Large regions of visually uniform density | INSIDE, NEAR-EDGE |
| 3 | Recognizable ellipsoid or sphere primitives | FAR, ABOVE |
| 4 | Isolated ears or bulb protrusions | LATERAL A vs B |
| 5 | Descriptor seams | LATERAL A vs B, UNDER |
| 6 | Rectangular or vertical walls | SIDE, LATERAL A/B |
| 7 | Flat slabs | UNDER |
| 8 | Uniformly smooth silhouettes | FAR |

**T098 does not pass if any positive item is missing, even when every rejected item is clear.** That
asymmetry is the entire reason this task was reopened.

---

## 6. Removing the scaffolding

The diagnostic is deliberately small and self-contained:

- `StormDensityCalibrationReport.java` (new, delete whole file)
- `StormGeometryBuildCoordinator.describeDensityCalibration(...)` (one delegating method)
- one `stormDensity` node and one handler in `TelemetryDebugClientCommand`

Delete those three once T098 records its calibration, or fold the report into the full storm
diagnostic capture when US4 implements
`contracts/storm-render-diagnostics.md`. It does no per-frame work and holds no state, so leaving it
in place until then costs nothing.
