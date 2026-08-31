# T098 Manual Validation Checklist

**Feature**: `001-native-storm-rendering`
**Prepared**: 2026-08-19
**Status**: awaiting manual capture — **T098 is not complete**
**Gate**: **T133**, not T118. T118 is retained Phase 4S evidence and no longer unblocks this task.
T098 may only be collected after T133 passes, which in turn requires T132 on a fresh post-T134
reference. Do not collect these captures before then; they would record pre-T133 geometry.
**Updated**: 2026-08-21 (T134 accepted; T132 rebased onto a post-T134 reference).

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

---

# T098 live capture results - 2026-08-27 - REJECTED

**T098 is NOT accepted.** Three fresh severe fixtures were captured automatically after T133 closed.
All three show the same structural failure. T099 remains blocked.

## Capture automation

`StormT098CaptureDriver` (test-only, inert without `run/t098-captures.txt`) rides on the accepted
T132/T133 auto-driver: it captures after the suite and material trace have validated the fixture, so
the screenshots and the numeric evidence describe one storm. It puts the camera in spectator, hides
the HUD, enlarges the window to 1600x900, and writes the checklist's seven required views plus
NEAR-EDGE with the fixture id, fingerprint, scale and density calibration logged beside each frame.
It changes no renderer state and captures ordinary production frames - no debug view, no diagnostic
optimization mode.

Two infrastructure faults were found and fixed before any frame counted as evidence: the suite's
BELOW pose drops a survival player out of the world (every frame recorded a death screen), and the
capture poses had to be clamped above the void floor.

## Fixtures

| # | Fixture | Fingerprint | baseY | topY | Horizontal radius | Descriptors | Roles |
|---|---|---|---:|---:|---:|---:|---|
| 1 | `142bca36` | `fb48f5ae77e3f55b` | 136.0 | 1001.1 | 648.2 | 10 | - |
| 2 | `cf410ea8` | `671f4fa2bc595da4` | 136.0 | ~1001 | ~650 | 10 | - |
| 3 | `aa731334` | `18a3ff07f5437a6a` | 136.0 | 1001.1 | 664.9 | 10 | base=2, core=2, tower=2, anvil=4 |

Density calibration at SIDE on fixture 3: `cell.density min=0.7944 max=0.9861 mean=0.8764`
(fixture reference 0.92), `noiseBaked=true`, `distance=1130m`.

## What the captures show

**The storm reads as a mushroom, not a cumulonimbus.** In all three fixtures the SIDE and LATERAL
views show:

- a large, smooth, banded dome occupying the anvil band;
- a thin, wispy, largely broken neck below it;
- a small ragged lower blob, visibly **detached** from the dome;
- **concentric horizontal banding** across the dome's side, regular and arc-shaped.

The middle of the column is essentially empty even though `core=2` and `tower=2` descriptors are
present and adopted. The descriptors exist; they are not producing visible body.

Not everything failed. The ABOVE capture is genuinely good: the anvil top surface shows real
multi-scale billowing, ragged irregular edges, and surface variation at several spatial frequencies.
The noise, erosion and detail work is doing its job. **The failure is vertical continuity and
descriptor joining, not surface detail.**

## Checklist grading

Frequency: **3 of 3 fixtures**, identical failure.

### Positive half (FR-023) - all nine required

| # | Criterion | Verdict | Evidence |
|---|---|---|---|
| 1 | Broad continuous lower cloud base | **FAIL** | small ragged detached blob, SIDE/LATERAL, 3/3 |
| 2 | Dense convective/core region | **FAIL** | no readable core; the middle of the column is empty |
| 3 | Vertical tower emerging naturally from the base | **FAIL** | absent; only wisps between base and anvil |
| 4 | Progressive vertical narrowing | **FAIL** | no tower present to narrow |
| 5 | Broad upper anvil | **PASS** | present and broad in all three |
| 6 | Multi-scale billowing | **PASS** | clear on ABOVE |
| 7 | Surface variation at multiple spatial frequencies | **PASS** | clear on ABOVE |
| 8 | Irregular but coherent silhouette curvature | **FAIL** | ABOVE is ragged and good, but the dome's side reads smooth and banded |
| 9 | Continuous transitions between base, tower, core, anvil | **FAIL** | visible gap between base and anvil, 3/3 |

**Six of nine positive criteria absent. T098 is not passable while any positive item is absent.**

### Negative half (FR-024) - none may be present

| # | Rejected form | Present? | Evidence |
|---|---|---|---|
| 1 | Large smooth balloon surfaces | **PRESENT** | the anvil dome |
| 2 | Large regions of visually uniform density | **PRESENT** | INSIDE is a near-uniform field |
| 3 | Visible ellipsoid or sphere primitives | **PRESENT** | the dome reads as one ellipsoid |
| 4 | Isolated ears or bulb protrusions | not observed | - |
| 5 | Descriptor seams | **PRESENT** | concentric banding across the dome, 3/3 |
| 6 | Rectangular or vertical walls | not observed | - |
| 7 | Flat slabs | **arguably present** | the banding reads as stacked layers |
| 8 | Uniformly smooth silhouettes | **partially present** | dome side smooth; anvil top correctly ragged |

### Inconclusive captures

`3_UNDER` is **INCONCLUSIVE** in all three fixtures: the altitude clamp that keeps the camera above
the void floor places it in ground-level haze rather than beneath a readable base. This is a
limitation of the capture pose, not a verdict on the storm; the UNDER criterion needs a reworked pose
before it can be graded.

## 48-block blend cap: PARTIAL

Measured over 1,152 consecutive descriptor pairs across 128 deterministic T134 plans
(`CloudMorphologyTopologySandbox.reportStormBlendSaturation()`):

| Metric | Value |
|---|---|
| Pairs | 1,152 |
| **Saturated at the 48-block cap** | **1,024 (88.9%)** |
| Requested blend | 27.00 - 297.00 blocks |
| Mean requested blend | 156.80 blocks |
| Worst effective / requested | **0.1616** |

So the role-specific formulas ask for a mean 157-block join and the cap delivers 48 - on average
**3.3x too narrow**, worst case **6.2x**. The cap was introduced 2026-08-19 (commit `a681240`),
**before** T134 tripled severe scale on 2026-08-21, and was never re-derived afterwards.

This also sits against the written contract. `contracts/storm-density-composition.md` C3 requires that
"each blend radius is derived from the smaller participating lobe's world-space radius". With the cap
binding on 88.9% of pairs the blend is a **constant 48**, not a value derived from the participating
radius, so the delivered behaviour no longer matches the contract at T134 scale.

**Verdict: PARTIAL, not CONFIRMED.** The saturation is real, it is severe, and the visual signature -
primitives that fail to merge, visible joins, stacked appearance - is exactly what an undersized blend
produces. But it is **not proven sufficient**: the gap between the base and the anvil spans hundreds
of blocks, and widening a join from 48 to 157 blocks would not bridge that alone. The middle of the
column is empty even though CORE and TOWER descriptors are present and adopted, which points at
descriptor vertical coverage, envelope strength, or erosion survival in the CORE/TOWER band as a
second, independent contributor.

**No production change was made.** Phase 6 authorises a blend correction only if live evidence
confirms the cap causes the failure; the evidence shows it is consistent with part of the failure,
not that it is the cause. `STORM_MAX_BLEND_BLOCKS` remains `48`.

## Status

**T098 REJECTED. T099 remains blocked.** The accepted T132/T133/T134 architecture is not
contradicted by this result: scale, determinism, material continuity and optimization neutrality all
remain accepted. What fails is the visible morphology those systems compose.

SC-006 / T070 performance debt remains separate and open.

---


---

# T098 root cause CORRECTED - 2026-08-28

**The carrier-wavelength root cause recorded in the section below is WRONG and is retracted.** It was
inferred from a single centre-line trace and does not survive direct measurement. No production
change was made on the strength of it.

## What was claimed, and why it was wrong

The earlier section concluded that `STORM_BASE_NOISE_SCALE = 0.0025` gave the carrier a ~426-block
vertical period against an 865-block column, producing a repeated horizontal dead band that sliced
the storm. Two independent measurements refute this.

**1. The wavelength was never 426 blocks.** The carrier is
`perlinFbm(u, v, w, base frequency 4, 5 octaves)` remapped by `worleyFbm(..., 8 cells)`. At scale
0.0025 the texture repeat is 400 world blocks, so the *dominant* Perlin feature is a quarter of
that - about 107 blocks. `morphology-thresholds.md` already records this independently: the T124
re-measurement at base scale 0.0025 found the dominant base feature is **109.4 blocks**. The 426-block
figure was the texture repeat interval, not the feature size, and re-reading the trace confirms the
carrier oscillates roughly three times across 400 blocks, consistent with ~107-block features.

**2. The thresholds are correct for the quantity they gate.** The suspicion was that
`STORM_CARRIER_P05/P95` had been measured on the baked R channel while the shader applies them to
`carrierRaw = saturate(remap(r, -(1 - lowFbm), 1, 0, 1))`. Measured directly through the exact
production domain transform (`reportT098CarrierDistribution()`, 262,144 samples):

| Quantity | p05 | p50 | p95 | Below the 0.7128 constant |
|---|---:|---:|---:|---:|
| Baked R channel | 0.5693 | 0.6713 | 0.7620 | 75.91% |
| **Shader `carrierRaw`** | **0.7123** | 0.7836 | **0.8452** | **5.11%** |
| `carrierRaw`, severe column only | 0.7125 | 0.7834 | 0.8444 | **5.06%** |

The constants match the shader quantity to three decimals, and the severe column's distribution is
indistinguishable from the global one. Exactly the designed 5% is zeroed. **There is no regional
bias, no dead band, and no stale calibration.**

The original error was reading 8-of-26 low samples along one vertical line as a systematic band.
Those samples are 16 blocks apart inside a ~109-block feature, so they are strongly correlated -
roughly four independent samples that happened to cross one noise trough.

## What the evidence actually supports

The measured chain, from the same trace and the role-occupancy probe:

- `body` is **not** zero where `baseField` is zero. `lowerBound = (1 - coverage) - fill * coverage`,
  so at high coverage the body floor is positive: at Y=344 `baseField=0.000` but
  `bodyBefore=0.292`.
- Density dies at **erosion**, not at the remap. At Y=344 an erosion of `0.285` against a body of
  `0.292` leaves `density=0.009`; at Y=520 the same-magnitude erosion (`0.223`) against a body of
  `0.967` leaves `density=0.945`. Erosion is roughly constant; what varies is how much body it has
  to bite into.
- The tower is geometrically small. Role occupancy on the live adopted system:
  TOWER `envelopeVisible=7,478` against ANVIL `143,834` - a **19:1** ratio, and per 48-block band
  the tower carries 68-495 visible voxels against the anvil's 12,644.

So the tower has both the least cross-section and the least margin against a roughly constant
erosion bite. Where `baseField` is high the tower survives; where it is in an ordinary trough the
body sits near the coverage floor and erosion removes essentially all of it. The wide base and anvil
have enough cross-section that plenty of high-`baseField` material always survives.

**This is a proportion and erosion-sensitivity question, not a noise-calibration bug.** It is
therefore not addressed by changing `STORM_BASE_NOISE_SCALE`, and that change was not made.

## Status

`STORM_BASE_NOISE_SCALE` remains `0.0025`. `STORM_CARRIER_P05/P95` remain `0.7128`/`0.8451` -
re-measured and confirmed valid. T131, erosion, lighting, descriptor geometry, role strengths and
`STORM_MAX_BLEND_BLOCKS = 48` are all unchanged. **T098 remains REJECTED and OPEN**, with the
failing stage now correctly attributed and the next decision open.


---

# RETRACTED - T098 root cause - 2026-08-28 - carrier normalisation vs base-noise wavelength

**T098 remains REJECTED.** The missing CORE/TOWER body has been traced to a single first failing
stage. No production change was made: correcting it requires either a morphology-wide change or a
subjective visual decision, both outside the authorised scope.

## Two hypotheses were tested and FALSIFIED

**Hypothesis A - CORE/TOWER geometry absent.** Falsified. `StormT098RoleOccupancy` measured the live
adopted system on a 24-block grid (116,964 samples, fixture `38bc5412`). CORE and TOWER envelopes are
present and strong:

| Role | Envelope-visible voxels | Mean envelope | Max envelope | Visible Y |
|---|---:|---:|---:|---|
| BASE | 72,980 | 0.3883 | 0.8985 | 136 - 712 |
| CORE | 14,449 | 0.4409 | **1.0000** | 304 - 784 |
| TOWER | 7,478 | **0.5395** | 0.9681 | 376 - 928 |
| ANVIL | 143,834 | 0.3391 | 0.6543 | 592 - 976 |

CORE and TOWER have the *highest* mean and max envelope of any role. Their descriptor strengths are
also the highest (CORE `density=1.0000`, TOWER `0.9045`/`0.9681`, against BASE `0.857`/`0.899` and
ANVIL `0.798`-`0.811`). Neither geometry nor strength is the problem.

**Hypothesis B - T131 no longer engages at T134 scale.** Falsified. T131 requires BASE, CORE and
TOWER to cover the sample simultaneously; that condition was derived on a pre-T134 column spanning
Y 224-508, and the T134 column is 865 blocks tall, so it was reasonable to suspect the triple overlap
had been pulled apart. It has not. The vertical profile shows all three roles present from Y 376 to
Y 760, and the production trace shows `activeRoleMask=7` with `bodyAfter` (0.382) exceeding
`bodyBefore` (0.292) at Y 376-472. **T131 engages exactly where designed and adds body.** It simply
cannot compensate for a base field that is already zero.

## First failing stage: carrier -> baseField normalisation

From the production shader material trace on the same fixture:

| Y | coverage | strength | carrierRaw | **baseField** | erosion | **density** |
|---:|---:|---:|---:|---:|---:|---:|
| 136 | 0.456 | 0.870 | 0.824 | **0.936** | 0.233 | **0.998** |
| 184 | 0.641 | 0.870 | 0.736 | **0.083** | 0.269 | **0.000** |
| 232 | 0.789 | 0.870 | 0.709 | **0.000** | 0.256 | **0.000** |
| 344 | 0.870 | 0.870 | 0.689 | **0.000** | 0.285 | **0.009** |
| 376 | 0.884 | 0.884 | 0.663 | **0.000** | 0.200 | 0.247 |
| 520 | 0.984 | 0.984 | 0.828 | **0.953** | 0.223 | **0.945** |
| 536 | 0.963 | 0.963 | 0.834 | **0.983** | 0.201 | **0.988** |

Coverage and strength are high through the whole convective column. `carrierRaw` never fails - it
stays in 0.66-0.83 everywhere. **`baseField` is what collapses**, and it collapses before erosion,
before T131's remap, and before any lighting stage.

The cause is arithmetic:

    stormBaseField(carrier) = smoothstep(STORM_CARRIER_P05, STORM_CARRIER_P95, carrier)
    STORM_CARRIER_P05 = 0.7128
    STORM_CARRIER_P95 = 0.8451

Those are the measured p05/p95 of the Perlin-Worley carrier
(`validation/morphology-thresholds.md`). Any carrier below **0.7128 maps to exactly zero**. The
mid-column carrier sits at 0.663-0.75 - straddling and mostly below that floor - so the entire
cross-section is zeroed there, while the base (0.824) and the upper column (0.828-0.834) survive.

## Why the mid-column carrier is systematically low

The carrier trend is not scatter. It falls smoothly from 0.824 at Y136 to 0.689 at Y344 and rises
again to 0.834 at Y536: **one full noise cycle**.

`baseNoiseDomain()` maps world position by `rotated * STORM_BASE_NOISE_SCALE` with
`STORM_BASE_NOISE_SCALE = 0.0025`, so the carrier texture's world-space repeat period is
`1 / 0.0025 = 400` blocks. The rotation's vertical component is `0.9408`, giving an effective
**vertical period of about 426 blocks**.

The T134 severe column is **865 blocks** tall - **2.03 carrier periods**. The carrier therefore dips
below p05 in a horizontal band roughly every 426 blocks, zeroing `baseField` across the whole
cross-section at those heights and slicing the system into vertically separated masses. That is
exactly the captured silhouette: a surviving base, a dead middle, and a surviving anvil.

Before T134 the column was about 284 blocks - **less than one carrier period** - so no interior dead
band could form inside a storm and the calibration was sound.

This was foreseen. `validation/renderer-wide-architecture-audit.md` line 471 records that the base
wavelengths "were calibrated for the current envelope. They must be re-evaluated after a derived
system scale so billows remain primary/secondary structure and erosion remains surface breakup."
T134's task text likewise says to retain them "unless remeasurement proves a change is required."
**The remeasurement was never done. This is that remeasurement, and it proves a change is required.**

## Why no fix was applied

Every available correction is either morphology-wide or a subjective visual choice:

1. **Raise `STORM_BASE_NOISE_SCALE`** so the period shrinks relative to the storm (0.0025 -> 0.005
   gives about 4 cycles at 213 blocks). This is the direct fix for the measured cause, but it changes
   billow size for **every cloud type**, not just severe storms, and how large billows should read is
   an aesthetic decision.
2. **Lower or widen `STORM_CARRIER_P05`/`P95`.** Removes the dead band by letting low carrier through,
   but changes density everywhere and weakens the measured normalisation the thresholds document
   derives.
3. **Extend T131-style retention** beyond the BASE/CORE/TOWER triple overlap to the whole convective
   column. Targeted, but it is a second material-composition special case layered on the first.

The authorisation boundary permits a narrow correction only when it does not broadly retune
morphology. All three cross that line, so this stops here for a decision.

## Status

**T098 REJECTED, root cause proven. T099 blocked.** `STORM_MAX_BLEND_BLOCKS` remains `48` and was not
touched; it stays a separate, later question. No accepted T132/T133/T134 evidence is contradicted -
this is a material-stage calibration that T134 invalidated and nobody remeasured.

---

# T098 erosion hypothesis FALSIFIED - 2026-08-28

**The erosion-scaling path is closed.** No production change was made: the measurement inverts the
hypothesis before any code was touched.

## Production erosion, as implemented

    detailFbm  = detail.r * 0.625 + detail.g * 0.25 + detail.b * 0.125
    erosion    = (1.0 - detailFbm) * STORM_EROSION       // STORM_EROSION = 0.44
    bodyEroded = max(bodyAfter - erosion, 0.0)

It is an absolute subtraction with no coverage, edge-exposure or role term - so the framing that it
"subtracts a similar amount regardless of remaining body" is correct as far as the equation goes.
What does not follow is that this harms the tower.

## Measured erosion versus body, by role

Real adopted T134 geometry (live fixture `38bc5412`, transcribed) against the real baked base and
detail volumes, 20-block sampling, `reportT098ErosionVersusBody()`:

| Role | Samples | Mean body | Mean erosion | erosion/body | erosion >= body | Density visible | Mean density |
|---|---:|---:|---:|---:|---:|---:|---:|
| BASE | 68,540 | 0.3884 | 0.2297 | 0.591 | **44.6%** | 54.4% | 0.2511 |
| CORE | 5,370 | 0.6608 | 0.2298 | 0.348 | 6.4% | 93.1% | 0.4405 |
| **TOWER** | **1,814** | **0.7032** | 0.2302 | **0.327** | **5.7%** | **93.3%** | **0.4810** |
| ANVIL | 83,767 | 0.4058 | 0.2296 | 0.566 | **42.7%** | 56.0% | 0.2593 |

**TOWER is the least-eroded role in the system.** It carries the highest mean body, the lowest
erosion-to-body ratio, the smallest fraction where erosion consumes the whole body, the highest
density-visible fraction and the highest mean density. BASE and ANVIL are the heavily eroded roles,
losing roughly 44% of their samples outright.

Erosion is therefore not erasing the tower, and scaling erosion by available body would help BASE and
ANVIL - the roles that are already visually solid - while barely touching the tower. It would inflate
exactly the masses that must not balloon.

## What the numbers do show

The disparity is volume, not material quality:

| Measure | Value |
|---|---:|
| CORE + TOWER occupied samples | 7,184 |
| BASE + ANVIL occupied samples | 152,307 |
| BASE+ANVIL : CORE+TOWER | **21.2 : 1** |
| ANVIL : TOWER | **46.2 : 1** |
| CORE + TOWER share of storm volume | **4.50%** |

The convective column is dense, healthy and well-formed - and it is 4.5% of the system. A ray
crossing the tower traverses roughly one forty-sixth of the material a ray crossing the anvil does,
so it accumulates almost no opacity. That is why the tower reads as a wisp between two solid masses
while its per-sample density is the highest in the storm.

This traces directly to the T127 scale contract: BASE 900-1,100, CORE 420-520, TOWER 280-360 then
180-250, ANVIL 1,150-1,450. The anvil is specified *wider than the base*, and the tower at roughly a
quarter of the base diameter. Those ratios, composed, produce two broad masses joined by a thin
stalk.

## Status

`STORM_EROSION` remains `0.44`. `STORM_BASE_NOISE_SCALE`, `STORM_CARRIER_P05/P95`, T131, descriptor
geometry, role strengths and `STORM_MAX_BLEND_BLOCKS = 48` are all unchanged. **T098 remains
REJECTED and OPEN.** The next candidate is the tower/anvil cross-section relationship in the T127
contract, which is a specification question rather than a renderer bug.

---

# T098 root cause: the T127 proportional contract is violated and internally inconsistent - 2026-08-28

**This is the objective finding, stated in the contract's own terms.** No production geometry was
changed: resolving it requires a morphology decision with two valid answers.

## Every absolute diameter passes; the relationships do not

Measured across 128 deterministic mature severe plans (`reportT098RoleProportions()`):

| Role | Delivered | T127 absolute target | In range? |
|---|---:|---|:--:|
| BASE | 1044.0 | 900-1100 | yes |
| CORE | 504.0 | 420-520 | yes |
| lower TOWER | 315.0 | 280-360 | yes |
| upper TOWER | 216.0 | 180-250 | yes |
| ANVIL union | 1269.9-1287.3 | 1150-1450 | yes |

T127's scale table also states a **relationship** beside each diameter. Those were never guarded, and
two of them fail:

| Relationship | Delivered | T127 target | |
|---|---:|---|---|
| CORE / BASE | 0.483 | 0.45-0.50 | OK |
| **lower TOWER / CORE** | **0.625** | 0.65-0.75 | **VIOLATION** |
| ANVIL / BASE | 1.224 | 1.20-1.35 | OK |
| **ANVIL / upper TOWER** | **5.917** | 3.5-5.0 | **VIOLATION** |

**This is why T134 passed and T098 failed.** `validateStormPhysicalScale()` checks each diameter
against its own range and nothing else, so a system can satisfy every range and still compose into
the rejected silhouette. The proportional half of the contract was documented and never enforced.

## The contract is also internally inconsistent

With upper TOWER capped at 250 and the stated `ANVIL = 3.5-5.0 x upper TOWER`, the widest admissible
anvil is `5.0 x 250 = 1250`. But the ANVIL range runs to **1450**, and the generator delivers
**1278**. For any anvil above 1250 **no upper-tower value inside 180-250 can satisfy the
relationship.** The two halves of the table cannot both be met at the delivered scale.

## Sensitivity: which term actually drives the silhouette

Scaling role radii on the real T134 descriptor set and measuring through the production density
chain (`reportT098ProportionSensitivity()`, density-visible voxels at >= 0.02):

| columnScale | anvilScale | Column share | mass:column | anvil:tower | Column bands | Longest gap |
|---:|---:|---:|---:|---:|---:|---:|
| 1.00 | 1.00 | 6.49% | 14.4:1 | 35.1:1 | 11/19 | 6 |
| 1.15 | 1.00 | 8.54% | 10.7:1 | 27.4:1 | 11/19 | 6 |
| 1.30 | 1.00 | 10.96% | 8.1:1 | 19.7:1 | 11/19 | 6 |
| 1.45 | 1.00 | 14.14% | 6.1:1 | 15.5:1 | 11/19 | 6 |
| 1.80 | 1.00 | 24.62% | 3.1:1 | 8.9:1 | 12/19 | 5 |
| 1.00 | 0.90 | 6.73% | 13.8:1 | 32.7:1 | 11/19 | 6 |
| 1.00 | 0.70 | 7.62% | 12.1:1 | 25.7:1 | 11/19 | 6 |
| 1.30 | 0.90 | 11.36% | 7.8:1 | 18.4:1 | 11/19 | 6 |

Two results matter:

- **Widening the central column is the only effective lever.** It moves the column's share of
  occupied material from 6.49% to 24.62%. Shrinking the anvil is nearly inert: even a 30% reduction
  only reaches 7.62%, and it starts eroding the footprint.
- **Vertical band coverage never changes.** The column occupies 11 of 19 bands at every candidate,
  with the same 6-band gap. Those empty bands are the lowest (BASE only) and the highest (ANVIL
  only), which is correct morphology. The column is already vertically continuous where it should
  be - it is thin, not broken.

## The decision required

Restoring `ANVIL / upper TOWER <= 5.0` at the delivered anvil of 1278 needs
`upper TOWER >= 255.6`, against 216 today - a column scale of about **1.18**. Reaching the middle of
the 3.5-5.0 band needs about **1.39**, and the bottom about **1.69**. Two resolutions are both
defensible and they produce visibly different storms:

**A. Widen the central column and raise the upper-TOWER range.** Keeps the accepted footprint,
height, BASE and ANVIL untouched; changes T127's upper-TOWER range from 180-250 to admit ~255-365.
The sensitivity table shows this works. It makes the storm read as a thicker convective column.

**B. Narrow the ANVIL to 1250 or below and raise upper TOWER to 250.** Keeps both stated ranges,
satisfies the relationship at exactly 5.0, and needs no contract change - but the measurements show
anvil reduction barely moves the column share, so it likely under-delivers, and it trims the anvil
spread that T127 derived from horizon presence.

Choosing between them is a morphology judgement about what the severe system should look like, not a
measurement. **No production change was made.**

## Guard added

`validateT098ProportionGuardRejectsMushroom()` records the violations and asserts the inconsistency
still holds, so the finding cannot silently decay. It deliberately asserts the *rejection* rather
than the proportions: converting it into a live contract guard requires the decision above.

## Status

Unchanged: all T127/T134 role geometry, `STORM_EROSION`, `STORM_BASE_NOISE_SCALE`,
`STORM_CARRIER_P05/P95`, T131, role strengths, `STORM_MAX_BLEND_BLOCKS = 48`. **T098 remains
REJECTED and OPEN.**

---

# T098 after the T127 proportional correction - 2026-08-28 - STILL REJECTED

**The contract correction was real and is retained. It did not fix the silhouette.**

## What was corrected

`stormLobeSpec()`'s TOWER radius multiplier moved from `lerp(0.35, 0.24)` to
`lerp(0.392, 0.334)`, and the relationship guards T127 documented but never enforced are now live.
Both violated relationships are resolved at their midband:

| Relationship | Before | After | Target |
|---|---:|---:|---|
| lower TOWER / CORE | 0.625 | **0.700** | 0.65-0.75 |
| ANVIL / upper TOWER | 5.917 | **4.251** | 3.5-5.0 |
| upper / lower TOWER | 0.686 | 0.852 | narrowing retained |

BASE, CORE, ANVIL union, footprint and height unchanged.

## Measured effect on material

| Metric | Before | After |
|---|---:|---:|
| CORE+TOWER share of occupied material | 6.49% | **10.14%** |
| BASE+ANVIL : CORE+TOWER | 14.4 : 1 | **8.9 : 1** |
| ANVIL : TOWER | 35.1 : 1 | **21.1 : 1** |
| Column bands occupied | 11/19 | 11/19 |

A genuine +56% relative gain in central-column material.

## Live result: unchanged, 3 of 3 fixtures

Fixtures `8218145e`, `9bc87f73`, `f0529e4e`. Every SIDE capture still shows the same silhouette: a
broad banded anvil dome, a thin wispy neck, and a visibly separate lower base. **No T098 criterion
changed state.** The storm still reads as a mushroom rather than one connected cumulonimbus.

## The remaining deficit, measured

At full midband compliance the anvil alone still holds **32,515** density-visible voxels against the
entire central column's **5,976** - the anvil is **5.4x the whole CORE+TOWER column by itself**.
Reaching parity from role width alone would need a column far outside any T127 range and would turn
the storm into a cylinder, which T098 also rejects.

The sweep bounds this: even `columnScale = 1.80` - well beyond the derived contract - reaches only
24.62% column share with the anvil still at 8.9:1, and the occupied band count never moves off 11/19
at any candidate.

## Conclusion

The T127 relationship violation was real, worth fixing, and is fixed. It was **not** the cause of the
mushroom silhouette. The silhouette is produced by the accepted severe-scale envelope itself: an
ANVIL specified at 1.20-1.35 of BASE, spanning 1,270-1,287 blocks over a 210-block vertical extent,
will dominate any column that a 900-1,100 BASE can physically taper into.

**T098 remains REJECTED. T099 blocked.** `STORM_MAX_BLEND_BLOCKS = 48` is still not the blocker.

---

# T098 ANVIL/BASE hypothesis FALSIFIED - 2026-08-28 - structural limit reached

**Narrowing the anvil does not fix the silhouette, and the anvil is not the dominant term.** No
production change was made this session.

## Correction to a figure I reported earlier

The previous session quoted the shipped TOWER correction as raising column share to **10.14%**. That
came from a proxy that scaled CORE and TOWER together. Measured with the fixture rebased onto the
*actual* shipped geometry - TOWER only, lower x1.120 and upper x1.392, CORE untouched - the true
figure is **7.83%**, up from 6.49%.

The correction is still real and worth keeping: TOWER visible material more than doubled (943 ->
2,276 voxels) and ANVIL:TOWER fell from **35.1:1 to 14.5:1**. But the column-share gain is 1.34
points, not 3.65.

## ANVIL/BASE sweep, corrected TOWER baseline

| anvilScale | ANVIL span | ANVIL/BASE | Column share | anvil:column | anvil:tower | Footprint |
|---:|---:|---:|---:|---:|---:|---:|
| 1.021 | 1308 | 1.239 | 7.83% | 7.1:1 | 14.5:1 | 2263 |
| 0.980 | 1266 | 1.200 | 7.94% | 7.0:1 | 14.2:1 | 2263 |
| 0.940 | 1226 | 1.162 | 8.05% | 6.8:1 | 13.8:1 | 2263 |
| 0.899 | 1185 | 1.123 | 8.19% | 6.5:1 | 13.3:1 | 2263 |
| 0.858 | 1144 | 1.084 | 8.33% | 6.3:1 | 12.9:1 | 2229 |
| 0.780 | 1065 | 1.009 | 8.73% | 5.8:1 | 11.7:1 | 2196 |
| 0.700 | 985 | **0.933** | 9.28% | 5.1:1 | 10.3:1 | 2039 |

**No transition point exists.** Driving ANVIL/BASE from 1.239 to 0.933 - an anvil *narrower than the
base*, which no longer reads as an anvil and violates T127's 1.20-1.35 relationship outright - moves
column share by **1.45 percentage points** and leaves the anvil still 5.1x the entire column. The
occupied band count stays at 11/19 throughout. Nothing in the admissible range changes the
silhouette, so no live campaign was run for a change not worth making.

## Why the anvil was never the dominant term

| Comparison | Value |
|---|---:|
| BASE visible voxels | 21,521 |
| CORE + TOWER visible voxels | 4,635 |
| **BASE alone : entire column** | **4.6 : 1** |

Geometry predicts this independently: BASE 1044 against CORE 504 is a **2.07x diameter ratio**, which
is **4.29x in cross-sectional area**, and the measured 4.6:1 matches the 4.2:1 volume estimate. **Even
with the anvil deleted entirely, the base would still be 4.6x the column.**

## Structural conclusion

The mushroom silhouette is not produced by any single contract term. It follows from the role
decomposition itself: a ten-descriptor system carrying two BASE and four ANVIL members at 1,000-1,300
blocks wide, against two CORE and two TOWER members at 200-500 blocks, will always render as two
broad masses joined by a comparatively thin column. Every lever inside the current decomposition has
now been measured:

| Lever | Result |
|---|---|
| Carrier wavelength / thresholds | falsified - calibration correct, 5.06% zeroed as designed |
| Erosion scaling | falsified - TOWER is the *least* eroded role |
| TOWER proportion (T127 relationships) | real defect, corrected, silhouette unchanged |
| ANVIL/BASE proportion | falsified - no transition; anvil is not the dominant term |

**The current descriptor-role decomposition appears incapable of producing T098's required
silhouette without a structural morphology redesign** - either far fewer/narrower BASE and ANVIL
members, or a column built from more than two CORE and two TOWER descriptors, or a different
role-to-descriptor allocation entirely. That is a redesign, not a contract adjustment.

**T098 remains REJECTED. T099 blocked.** `STORM_MAX_BLEND_BLOCKS = 48` is still not the blocker.

## T098 root cause: the anvil's isotropic envelope boundary (2026-08-28)

Five hypotheses were measured and falsified before this one. Recorded so the
falsifications are not re-run: carrier wavelength/thresholds (calibration
correct, 5.06% zeroed as designed), erosion scaling (TOWER is the least-eroded
role), TOWER proportion (a real T127 contract defect, found and corrected, but
the silhouette was unchanged), ANVIL/BASE proportion (swept to 0.933, no
transition), and descriptor-role allocation (see below).

### What the profile actually showed

Equivalent cross-sectional diameter alone showed no mushroom - 1029 at y296,
705 at y616, 1658 at y904 - which contradicted the screenshots. Adding
connected-component analysis resolved it: the transition band does not narrow,
it **shatters**. 50-84 components at y616-776, against 14-24 in the base and
anvil bands.

### Why it is not an allocation problem

Every column-side lever is inert. Scored on the y616-776 band by mean
largest-component diameter:

| candidate | largest comp | components |
|---|---:|---:|
| current | 648.6 | 61.2 |
| towerTop+200 | 676.3 | 59.2 |
| coreTop+220 | 685.0 | 58.8 |
| towerWide x1.5 | 757.8 | 50.8 |
| bridge stage r=380 | 829.4 | 38.7 |
| baseTop+260 | 891.2 | 37.8 |
| anvilBase-200 | 1410.5 | 44.8 |

Connectivity tracked envelope *width* monotonically, regardless of which role
supplied it. That suggested a percolation threshold, so one was measured
directly - and falsified: an isolated column holds a 0.977-0.999 connected
fraction at every diameter from 200 to 1040 blocks, and narrow columns
fragment *less* (1.1 components at 200 wide, 27.0 at 1040). Column width is
not the cause.

### The actual defect

Isolating the roles found it. Dropping the ANVIL members from the band leaves
the column coherent:

| y | full | no-anvil | anvil cells | anvil coverage p50 |
|---:|:--|:--|---:|---:|
| 616 | 55 comps, 0.776 | 27 comps, 0.881 | 367 | 0.045 |
| 680 | 86 comps, 0.623 | 19 comps, 0.788 | 1186 | 0.200 |
| 744 | 58 comps, 0.463 | 4 comps, 0.984 | 1769 | 0.331 |
| 776 | 59 comps, 0.876 | 1 comp, 1.000 | 2214 | 0.403 |

All of that anvil material sat **below the anvil's own baseY of 769.6**, at
coverage low enough for the carrier to shred it.

The cause is in `StormLobeEvaluator.edgeWidthBlocks`. `envelopeFromDistance`
fades over +/- that width isotropically, but the width is derived from
`smallerRadius`, a horizontal extent. A role much wider than it is tall
therefore gets a boundary wider than its own body:

| role | softness | half-height | ratio |
|---|---:|---:|---:|
| BASE | 162.4 | 223.8 | 0.726 |
| CORE | 79.7 / 74.7 | 140.4 / 148.2 | 0.567 / 0.504 |
| TOWER | 50.2 / 44.3 | 183.3 / 167.7 | 0.274 / 0.264 |
| ANVIL (x4) | 259.6-272.0 | 105.3 | 2.465-2.583 |

The ANVIL multiplier `max(0.12, edgeSoftness * 1.65)` was introduced to stop
the strength-weighted iso contour discarding the canopy rim - a **lateral**
concern - but it is applied isotropically, so it also hung ~150 blocks of haze
straight down through the storm's waist.

### Fix

Bound the boundary by the lobe's own vertical extent, at 0.75 of half-height.
Dimensionally correct for every role, and it binds only the degenerate case:
BASE/CORE/TOWER softness is unchanged to the block, ANVIL falls 272.0 -> 89.5.
The anvil now contributes zero cells at y616/648/680 (was 367/735/1186).

Band components 61.2 -> 22.5; full-vs-noAnvil now track each other (24 vs 27
at y616) instead of diverging 3:1. Canopy is not shrunk: span stays 1800
blocks, total material falls 2.7%.

Mirrored in `cloud_atmosphere_volume.fsh` through the existing
`stormDescriptorVerticalBounds`. T076 GLSL parity and T111 shader compilation
pass; `./gradlew check` and `./gradlew build` pass with 40 invariants green.
T127 relationships and the T134 scale contract are unaffected - no morphology
geometry changed.

`validateT098EnvelopeBoundedByExtent` guards it as a system-level relationship
rather than another independent range, and carries the pre-fix witness
(2.280). Verified to throw when the bound is disabled.

### Status

T098 remains **OPEN**. The fix is proven offline and in regression, but the
live visual campaign (>=5 fresh severe fixtures through the controlled views)
has not been re-run. T098 must not be marked passed until it has.

## T098 live acceptance campaign, post envelope-extent fix (2026-08-30)

Six autoruns across six world seeds, yielding **five distinct fresh severe
fixtures** (two runs converged on the same storm). Every run reached
`T132_AUTORUN_FINISHED outcome=complete` with 10 descriptors and all 8
controlled views captured.

| run | seed | group | fingerprint | topY | horizontalRadius | height |
|---|---:|---|---|---:|---:|---:|
| fx1 | 5510001 | 9294726d | 97d76671 | 999.68 | 657.85 | 863.7 |
| fx2 | 5525838 | ae4aef49 | 43aff062 | 1001.82 | 685.02 | 865.8 |
| fx3 | 5533757 | 72259f41 | c2b11ff2 | 1002.38 | 686.25 | 866.4 |
| fx4 | 5541676 | 72259f41 | c2b11ff2 | — duplicate of fx3 — | | |
| fx5 | 5549595 | d266f801 | 6fae8172 | 1000.82 | 677.07 | 864.8 |
| fx6 | 918273645 | 6e8e8c73 | aca5f0ad | 1002.15 | 675.53 | 866.2 |

All heights sit inside T134's 720-880 and all footprints inside 1200-1500.

### Phase 5: the 0.75 extent bound is selective, with a thin margin

Pooled over all six runs, 60 live descriptors. Ratio is the unclamped
`edgeWidthBlocks` over the lobe's own half-height, measured before the clamp:

| role | count | clamped | min | median | max |
|---|---:|---:|---:|---:|---:|
| BASE | 12 | **0** | 0.710 | 0.720 | 0.736 |
| CORE | 12 | **0** | 0.443 | 0.506 | 0.523 |
| TOWER | 12 | **0** | 0.228 | 0.246 | 0.258 |
| ANVIL | 24 | **24** | 2.149 | 2.255 | 2.312 |

The bound behaves exactly as intended: it binds 24 of 24 ANVIL descriptors and
0 of 36 BASE/CORE/TOWER descriptors. **It did not affect BASE, CORE or TOWER on
any fresh fixture.**

The margin is nonetheless thin and should be treated as a known risk rather
than a comfortable separation: worst-case BASE reached 0.736 against the 0.75
bound, a margin of 0.014 (1.9%). Across 12 live BASE descriptors from five
independent worlds it was never crossed. The constant was not tuned.

### Phase 4: visual grading - FAIL on all five fixtures

Every fixture reads as **two cleanly separated masses**: an anvil dome above, a
fragmented base below, and clear sky between them with no connecting column.

| criterion | fx1 | fx2 | fx3 | fx5 | fx6 |
|---|---|---|---|---|---|
| 1 broad connected lower base | FAIL | FAIL | FAIL | FAIL | FAIL |
| 2 dense convective core | FAIL | FAIL | FAIL | FAIL | FAIL |
| 3 towers emerging from base | FAIL | FAIL | FAIL | FAIL | FAIL |
| 4 progressive vertical narrowing | FAIL | FAIL | FAIL | FAIL | FAIL |
| 5 broad upper anvil | PASS | PASS | PASS | PASS | PASS |
| 6 multi-scale billowing | PASS (anvil) | PASS | PASS | PASS | PASS |
| 7 multi-frequency surface variation | PASS (anvil) | PASS | PASS | PASS | PASS |
| 8 coherent silhouette curvature | FAIL | FAIL | FAIL | FAIL | FAIL |
| 9 continuous role transitions | FAIL | FAIL | FAIL | FAIL | FAIL |

Rejected forms observed on every fixture: detached upper mass, detached lower
base, mushroom / two-separated-masses, radial shredding of the base,
recognisable ellipsoid, uniformly smooth anvil silhouette.

Two views were inconclusive for capture-pose reasons, not morphology: **FAR**
is empty because the framing distance (horizontalRadius x 2.6, about 1710
blocks here) exceeds volumetric render range, and **UNDER** puts the camera
inside mountainside terrain because `MINIMUM_CAMERA_Y = 70` is too low for
these worlds. ABOVE, SIDE and both LATERAL views were fully usable and are what
the grading rests on.

### What the fix did and did not do

It did what it was measured to do. The anvil's sub-canopy haze is gone: the gap
between the masses is now clean sky rather than the shredded confetti the
pre-fix captures showed, and the clamp statistics confirm every anvil boundary
is now bounded by its own extent.

It did not make the storm read as one system, because removing the haze
revealed that nothing else occupies that space. ABOVE shows the anvil's own
surface carrying genuine multi-scale billowing, so the noise and detail
pipeline is working; the failure is that **CORE and TOWER produce almost no
visible density**, consistent with the earlier offline measurement of 4,635
CORE+TOWER visible voxels against 21,521 for BASE alone. The previous anvil
skirt was visually masking that absence.

The remaining blocker is therefore in role density/strength composition, not in
envelope extent and not in descriptor allocation. Per the campaign brief this
is recorded, not acted on.

### Status

T098 remains **OPEN** - CASE D. T099 remains blocked.
`STORM_MAX_BLEND_BLOCKS = 48` is **not** the next blocker: the storm does not
yet have a connected body for seams to appear on.

Regression after the campaign: `./gradlew check build` BUILD SUCCESSFUL, 40
invariants passed, 0 failures, including T076 GLSL parity, T111 shader
compilation, T098 extent and proportion guards, and the T134 scale contract.


## T098 role-density composition investigation (2026-08-31)

Result: the hypothesis is **falsified**. Role strength and material composition
are not where the column is lost. The loss is view-dependent and occurs after
`finalDensity`. Per the brief's stop condition 2, no material-composition
change was made.

### Production role strengths (live, fixture 9294726d)

| role | member | density = strength | major | vertical span |
|---|---|---:|---:|---|
| BASE | 0 / 1 | 0.8471 / 0.8142 | 533.0 / 523.3 | 136-584 / 150-597 |
| CORE | 2 / 3 | **1.0000 / 1.0000** | 298.9 / 271.9 | 349-630 / 402-698 |
| TOWER | 4 / 5 | 0.9659 / 0.9745 | 222.4 / 188.1 | 435-802 / 593-929 |
| ANVIL | 6-9 | 0.7954-0.8168 | 478-515 | 772-1000 |

CORE sits at the 1.0 ceiling and TOWER just below it; BASE and ANVIL are the
weakest roles in the storm. The column is the strongest material in the system
and has no upward headroom, so the Phase 3 sweep has no admissible upward arm
and "raise CORE/TOWER strength" is not an available lever.

### The column is healthy at every density stage

Offline integrated density by band, replacing the earlier thresholded cell
counts which measured presence rather than quantity:

| band | mean density | p50 | axis optical depth |
|---|---:|---:|---:|
| y296 BASE | 0.4548 | 0.4649 | 460.4 |
| y616 | 0.4660 | 0.4751 | 267.8 |
| y680 waist | 0.4932 | 0.5152 | 293.4 |
| y712 waist | 0.5024 | 0.5381 | 199.5 |
| y904 ANVIL | 0.4449 | 0.4634 | 825.2 |

The waist carries the highest mean density in the storm. An optical depth of
200-293 is overwhelmingly opaque; roughly 5 suffices.

The production shader agrees. Its own T128 material trace on the live fixture
reports, at the waist, coverage 0.966-0.974, bodyAfter 0.39-0.95, density
0.81-0.91 at y680-712, and an activeRoleMask progressing 1 -> 3 -> 7 -> 15 as
BASE, CORE, TOWER and ANVIL hand off. Retention through
envelope -> coverage -> bodyBefore -> T131 bodyAfter -> erosion -> finalDensity
shows no disproportionate CORE/TOWER loss at any stage.

The offline fixture was re-verified against the live descriptors before relying
on it: BASE 0.857/0.899 vs live 0.847/0.814, CORE 1.000/1.000 vs 1.000/1.000,
TOWER 0.905/0.968 vs 0.966/0.975, radii within a few blocks.

### Where it is actually lost: view dependence

The column renders correctly at close range and degrades with camera distance,
holding the fixture and every material input fixed:

| view | distance (x horizontalRadius) | column |
|---|---|---|
| NEAR_EDGE | 1.12 (737 blocks) | substantial, well-detailed, reaches the anvil |
| LADDER 1.20 | 789 | substantial, thin gap opening |
| LADDER 1.40 | 921 | present, gap widened, top fragmenting |
| LADDER 1.60 | 1053 | present but detached |
| SIDE | 1.70 (1118) | clean sky |
| LATERAL A/B | 1.90 (1250) | clean sky |
| FAR | 2.60 (1710) | storm entirely absent |

At SIDE the waist band y600-772 subtends about 8.7 degrees, roughly 112 pixels
of a 900-pixel frame, so this is not angular resolution.

Two candidate rendering causes were tested and both falsified:

- Frame-time governor. Every frame of the 2026-08-30 campaign ran with
  CloudFrameTimeGovernor saturated at its MIN_SCALE of 0.5, halving the march
  step budget. A capture-only pin holding the scale at 1.0 did not restore the
  column at 1.7x. The pin is marker-gated and test-only; its activation was not
  independently logged, so treat this arm as indicative rather than conclusive.
- T121 conservative rejection. At the waist the column lobes are inside their
  own vertical spans, so verticalLowerBound is about 0 and the rejection cannot
  fire.

### The next target

The remaining candidate is the exterior coarse-to-fine march transition: for
near-horizontal exterior rays the march advances on coarseStep, capped at
min(112, fineStep * 16) and grown by distanceGrowth = 1 + (t /
MaxRenderDistance) * 2.2, and only switches to fineStep when
directStormSegmentMayIntersect reports a hit. Near-horizontal rays are exactly
the ones aimed at the waist, and they traverse the longest slab span. If that
segment test misses the column for those rays, the march steps over material
the density field contains - which is precisely the observed signature.

This is a raymarch/acceleration question, not a morphology or material one, and
it was not pursued here.

### Status

T098 remains OPEN. T099 remains blocked. STORM_MAX_BLEND_BLOCKS = 48 is still
not the next blocker.

No production morphology, material, carrier, erosion or T131 behaviour was
changed. ./gradlew check build BUILD SUCCESSFUL, 40 invariants, 0 failures; the
ANVIL vertical-extent guard remains selective at 0.750 against its 1.000 bound.


## T098 raymarch-transition investigation (2026-08-31)

Result for the stated hypothesis: **falsified, CASE C**. The coarse march does
not skip past the column. `directStormSegmentMayIntersect` produced **zero
false negatives** on every traced ray at every distance.

The investigation did find the real divergence, and it is the opposite defect:
a false positive that exhausts the march's iteration budget in empty space.

### The march state machine, as implemented

Per iteration, in order:

1. `if (t >= t1 || transmittance < 0.015) break;`
2. `fine = sinceHit < 6`
3. `distanceGrowth = 1 + (t / MaxRenderDistance) * 2.2`
4. `stepLength = fine ? fineStep : min(coarseStep * distanceGrowth, coarseStepCap)`
5. if `!fine` and the puff segment test fires: `sinceHit = 0`, `fine = true`
6. if `!fine` and `directStormSegmentMayIntersect(p, p + step)` fires:
   `sinceHit = 0`, `fine = true`, `stepLength = fineStep`
7. if `!fine` and `sampleWeather(p.xz).r * CoverageMul <= 0.001`: skip the
   segment entirely
8. otherwise sample

Production values for the captured configuration: ULTRA `raymarchSteps` 96,
governor `stepScale` 0.5, so `stepBudget` 48; `exteriorFineStep` =
`2.5 * sqrt(96/48)` = 3.536; `coarseStep` = `max(2000/48 * 1.5, 3.536*3)` =
62.5; `coarseStepCap` = `min(112, 3.536*16)` = **56.57**; `MAX_STEPS` = 128;
`MaxRenderDistance` = 2000 (config `cloudRenderDistance`).

`stormGroupSegmentMayIntersect` is genuinely conservative: for every descriptor
in the group it takes the closest point on the segment and compares against a
bounding sphere of radius `hypot(max(major, minor) * 1.24 + |shear| + 2,
halfHeight + 2)`. Because it is segment-wide, the `groupVisited` dedup in
`directStormSegmentMayIntersect` is sound - re-testing the same group at
another sample fraction cannot change the answer.

### Measured: zero false negatives, catastrophic false positives

Offline replication of the production march against the live descriptors,
scored per coarse segment versus a 2-block reference traversal:

| factor | distance | firstFineT | refFirstT | falseNeg | marchedDepth | refDepth | exhausted |
|---|---:|---:|---:|---:|---:|---:|---|
| 1.12 waist | 736.7 | 0.0 | 436 | 0 | 6.3 | 255.6 | YES |
| 1.40 waist | 920.9 | 169.7 | 620 | 0 | 0.0 | 254.1 | YES |
| 1.60 waist | 1052.5 | 282.8 | 752 | 0 | 0.0 | 269.9 | YES |
| 1.70 waist | 1118.3 | 339.4 | 818 | 0 | 0.0 | 270.8 | YES |
| 2.00 waist | 1315.6 | 565.7 | 1016 | 0 | 0.0 | 277.1 | YES |
| 2.60 waist | 1710.3 | 961.7 | 1450 | 0 | 0.0 | 210.5 | YES |
| 1.70 baseControl | 1118.3 | 452.5 | 684 | 0 | 88.5 | 430.0 | YES |
| 1.70 anvilControl | 1118.3 | 339.4 | 620 | 0 | 40.2 | 590.4 | YES |

`firstFineT` is the first t at which the march enters fine stepping;
`refFirstT` is the first t at which the reference finds density >= 0.02.

The march enters fine stepping **hundreds of blocks before any material** - at
t=339 when material starts at t=818 for the SIDE waist ray. The bounding
spheres are enormous: about 698 blocks for BASE (533 * 1.24 = 661 horizontal
against halfHeight 224) and about 615 for ANVIL. Any ray within that radius of
a descriptor centre is promoted.

Once promoted the march cannot recover. `fine = sinceHit < 6` gives six fine
steps of 3.536 blocks, then one coarse iteration whose segment test is still
inside the same bounding sphere and immediately re-promotes. Net progress is
about 3 blocks per iteration, so the `MAX_STEPS` = 128 cap yields roughly 390
blocks of reach. **Every ray reports `exhausted=YES`, and every waist ray at or
beyond 1.40x accumulates exactly zero density.**

This reproduces the observed ladder exactly. At 1.12x the budget just reaches
material at 436 and returns a faint 6.3 of optical depth; by 1.40x it falls
short and returns 0.0; at 1.70x and beyond it is hopeless.

The controls show the defect is not column-specific. BASE captures 88.5 of 430
reference optical depth (21%) and ANVIL 40.2 of 590 (7%). They render at all
only because they are wide enough that many rays begin inside them.

### Consequence for the T098 history

The storm has been materially correct throughout. Six successive morphology and
material hypotheses failed because none of them was the defect: at the
distances used for visual grading the march never arrives at the storm. The
1.70x SIDE and 1.90x LATERAL poses sit past the point where the budget runs
out, and the 1.12x NEAR_EDGE pose - the one that always looked healthy - is the
only one inside it.

### Not fixed here

Phase 9 authorises a correction when false negatives are measured. There are
none, so the authorisation condition is not met and, per CASE C, no fix was
attempted and no further hypothesis was started.

The narrow correction this points to, for a future session, is the promotion
rule rather than the intersection test: promote to fine only when the segment
is near actual material rather than inside a bounding sphere sized to the whole
descriptor, and/or prevent the six-step fine window from being re-armed while
the reference distance to material is still large. Any such change must be
measured against the same false-positive and false-negative classification, and
against the T119/T121/T122/T123 workload counters, since reducing false
positives should *reduce* cost rather than raise it.

### Status

T098 remains **OPEN**. T099 remains blocked. `STORM_MAX_BLEND_BLOCKS = 48` is
still not the next blocker.

No production code changed. `./gradlew check` passes with 40 invariants and 0
failures.
