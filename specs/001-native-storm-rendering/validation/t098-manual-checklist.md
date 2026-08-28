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
