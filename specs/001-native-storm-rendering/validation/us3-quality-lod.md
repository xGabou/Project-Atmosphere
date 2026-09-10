# T139 - Five-mode quality policy (CURRENT ACCEPTED POLICY)

This records **what ships today**, not what Ultra should eventually look like. The two are
deliberately separated: section 7 states the desired direction, and section 6 is an explicit
warning against reading the current Ultra as that target.

Authority: `VolumetricQualityProfile` is the table the renderer reads. Every value below was taken
from source and cross-checked against runtime cell records; nothing is inferred.

---

## 1. Current five-mode ladder

| mode | internal scale | cloud target at 1920x1080 | marched pixels | % of display |
|---|---|---|---|---|
| Low | 0.125 | 240 x 135 | 32,400 | 1.56 % |
| Low 24 | 0.125 | 240 x 135 | 32,400 | 1.56 % |
| Medium | 0.125 | 240 x 135 | 32,400 | 1.56 % |
| High | 0.1875 | 360 x 203 | 73,080 | 3.52 % |
| **Ultra** | **0.250** | **480 x 270** | 129,600 | 6.25 % |

**Verified, not trusted.** Dimensions are `Mth.ceil(mainTarget.dimension * scale)`
(`VolumetricCloudRenderTargets.java:196-197`), which gives 202.5 -> **203** at High - so the
High target is 360x203, not 360x202. Runtime records confirm Ultra directly: 260 recorded cells
carry `ULTRA|96|0.250|1920x1080|480x270`.

---

## 2. Actual current production values per mode

From `VolumetricQualityProfile`:

| | Low | Low 24 | Medium | High | Ultra |
|---|---|---|---|---|---|
| raymarch steps | 24 | 32 | 40 | 64 | 96 |
| internal resolution scale | 0.125 | 0.125 | 0.125 | 0.1875 | 0.250 |
| light steps | 3 | 4 | 5 | 6 | 6 |
| scatter octaves | 1 | 2 | 3 | 3 | 3 |
| detail quality | 0 | 1 | 1 | 1 | 2 |
| weather map size | 256 | 384 | 512 | 512 | 512 |
| temporal enabled | **false** | true | true | true | true |
| shadow update interval | 8 | 6 | 4 | 2 | 1 |
| analytics (GL 4.3) | false | false | true | true | true |

**Cloud render distance is not mode-dependent.** It is the single config value
`AtmoCommonConfig.CLOUD_RENDER_DISTANCE` (default **2000** blocks, range 100..MAX), clamped to a
300-block floor at upload (`VolumetricCloudRenderer.java:478`). All five modes render to the same
distance.

**Temporal/history.** History is consumed only when
`historyEnabled && profile.temporalEnabled() && hasPrevFrame && historyTarget != null &&
isHistoryValid()` (`VolumetricCloudRenderer.java:351-355`). Validity is generation-based
(`VolumetricHistoryValidity`: world, dimension, owner, resource, topology, resolution) and is
**not** camera-based, so camera motion alone does not invalidate it. **Low disables temporal
accumulation entirely**; the other four enable it. Live history blend is 0.85.

**Reconstruction.** Upscale from the internal target to display resolution runs at display
resolution and costs **0.085-0.121 ms at every scale and pose, 0.01-0.29 % of cloud time**
(`performance-internal-resolution-frontier.md`). It is flat and is never the floor, even
expanding 240x135 over 1920x1080.

**Governor.** `CloudFrameTimeGovernor` is a scalar **step-budget** governor and is
mode-independent: budget 4.2 ms, downgrade after 40 consecutive over-budget frames, recovery
after 400 consecutive frames under half budget, step +/-0.125, clamped to **[0.5, 1.0]**. It
scales `StepScale` only - **it does not change internal resolution**. There is no per-mode floor
or ceiling, and no adaptive resolution state machine; the immutable adaptive governor with
EWMA/bands/cooldown specified by T046 is **not implemented** and its task is still open.

**Mode-specific LOD.** None beyond the table. `adaptiveCloudQuality` and
`nativeStormDetailDistance` (T044) do not exist yet; those tasks are open.

---

## 3. Monotonicity audit

Higher modes are equal or better on every dimension. No accidental inversion was found.

| dimension | Low -> Ultra | verdict |
|---|---|---|
| raymarch steps | 24 < 32 < 40 < 64 < 96 | strictly increasing |
| internal resolution | 0.125 = 0.125 = 0.125 < 0.1875 < 0.250 | non-decreasing |
| light steps | 3 < 4 < 5 < 6 = 6 | non-decreasing |
| scatter octaves | 1 < 2 < 3 = 3 = 3 | non-decreasing |
| detail quality | 0 < 1 = 1 = 1 < 2 | non-decreasing |
| weather map size | 256 < 384 < 512 = 512 = 512 | non-decreasing |
| temporal | off, on, on, on, on | non-decreasing |
| shadow update interval | 8 > 6 > 4 > 2 > 1 | strictly improving (lower is more frequent) |
| analytics | off, off, on, on, on | non-decreasing |
| render distance | identical across modes | flat by design |

**Specifically checked and NOT found**: no lower mode has more useful samples; High is not sharper
than Ultra on any path (Ultra is >= on every row); history does not differ unexpectedly - the one
difference, Low's disabled temporal, is intentional and is the GL 3.2 baseline; lighting and
detail are not non-monotonic; render distance does not decrease.

**Intentional plateaus** (equal, not inverted):

- **Light steps tie at High = Ultra = 6.** Ultra buys no extra light-cone taps over High. Given
  T149's finding that removing 27-40 % of light evaluations converted to ~1.02x, raising Ultra's
  light steps would cost without a measured visual case; the tie is deliberate.
- **Scatter octaves plateau at Medium/High/Ultra = 3.**
- **Detail quality ties at Low 24 = Medium = High = 1**, with Ultra alone at 2.
- **Weather map plateaus at Medium/High/Ultra = 512.**
- **Low and Low 24 and Medium share 0.125**, so the first three modes differ only in steps,
  lighting, detail, map size, temporal and analytics - not in resolution.

---

## 4. Measured performance evidence available per mode

**This is the section most likely to be misread, so it is stated bluntly: four of the five modes
have no measurement at their shipped configuration.**

| mode | shipped scale | cells measured at that scale | status |
|---|---|---|---|
| Low | 0.125 | **0** | **no valid cell** |
| Low 24 | 0.125 | **0** | **no valid cell** |
| Medium | 0.125 | **0** | **no valid cell** |
| High | 0.1875 | **0** | **no valid cell** |
| Ultra | 0.250 | **260** | measured extensively |

Every non-Ultra measurement on record was taken at the **pre-Rank-1** ladder - Low 0.250
(480x270), Low 24 0.375 (720x405), Medium 0.500 (960x540), High 0.500 (960x540) - and those cells
are **not** evidence for the current ladder. The T135 budget contract's own resolution column is
written against that superseded ladder as well.

Ultra at its shipped 0.250, cloud GPU p50, from the T153 production arm (one fixture, seven
poses), against the 8.0 ms Ultra cloud budget:

| pose | cloud p50 | cloud p95 | vs 8.0 ms budget |
|---|---|---|---|
| PLAY_VIS_NEAR | 100.28 | 110.60 | **12.5x over** |
| PLAY_VIS_MID | 64.80 | 74.38 | **8.1x over** |
| SIDE | 114.25 | 123.23 | **14.3x over** |
| FAR | 53.60 | 60.56 | **6.7x over** |
| ABOVE | 104.11 | 107.91 | **13.0x over** |
| BELOW | 23.58 | 24.26 | 2.9x over |
| NEAR_EDGE | 190.28 | 200.63 | **23.8x over** |

**The current ladder does not meet SC-006 and this document does not claim it does.** SC-006
requires p95 total-frame <= 16.7 ms at 1920x1080; the representative PLAY_VIS_NEAR frame p95 at
shipped Ultra is 113.32 ms, **6.8x over**. Representative Ultra remains far above the desired
performance target.

No number is invented for the four unmeasured modes. Their per-mode budgets (3.0 / 4.0 / 5.0 /
6.5 ms cloud) stand as targets carried forward from T135, unverified at the current ladder.

---

## 5. Visual compromises per mode

Measured against the previously shipped 0.750, from `performance-internal-resolution-frontier.md`:

| scale | modes | SSIM (SIDE / ABOVE) | silhouette mean displacement (SIDE / ABOVE) | cloud pixels changed >8/255 |
|---|---|---|---|---|
| 0.250 | **Ultra** | 0.993 / 0.988 | 2.40 / 2.98 px | 4.46 % |
| 0.1875 | **High** | 0.990 / 0.982 | 3.55 / 4.32 px | 6.84 % |
| 0.125 | **Low, Low 24, Medium** | 0.981 / 0.960 | 5.92 / 7.38 px | 10.58 % |

The binding constraint on this ladder is **silhouette softening, not structure**. T098a passes at
every scale down to 0.125 - centre-column share 1.0000, longest inner sky run 0 px, FAR coverage
actually *grows* slightly as scale falls because the low-resolution silhouette dilates rather than
thins. So the modes are not structurally broken at any rung; they are progressively softer.

Additional per-mode compromises: **Low** has no temporal accumulation, 0 detail quality, 1 scatter
octave, a 256 weather map and shadows refreshed only every 8 frames. **Low 24** and **Medium**
share Ultra's marched-pixel count deficit (1.56 % of display) while differing in step and lighting
budget.

---

## 6. Current Ultra warning

**Current Ultra (0.250 / 480x270) is NOT the desired final Ultra visual target.** It must not be
described as one anywhere.

- **Benefit**: a large, measured performance improvement over the old high-resolution path -
  representative Ultra went 497.3 -> 113.2 ms, **4.39x**, moving from 0.750 to 0.250.
- **Problems**: visibly too soft and foggy in gameplay; silhouette quantisation (2.40 px mean
  displacement at SIDE, 2.98 px at ABOVE, 4.46 % of cloud pixels changed); not representative of
  the intended "Ultra" tier; and the cumulonimbus macro morphology still requires T098b regardless
  of resolution.

---

## 7. Desired future Ultra direction

If the parallel core-renderer / execution-context experiment creates sufficient headroom, **test
restoring Ultra toward a higher internal resolution.** No specific rung is promised: 0.375 and
0.50 are candidates to be measured, not commitments. Any restoration must be re-measured on the
seven-pose fixture and re-graded visually before adoption.

The current policy is explicitly provisional and may be revised by that experiment's result.

---

## 8. T152 temporal implications

From `performance-moving-camera.md`, measured on the shipped renderer over a 2200-frame route per
arm:

- History ON and OFF produced **essentially identical route flicker** - 0.00232 mean for both,
  per-segment ratios 0.932-1.015.
- **Current temporal history is not providing meaningful flicker suppression.**
- Stability comes primarily from the **frozen sampling lattice** (`searchBlue` is a static
  screen-space phase), not from accumulation.
- **Interior instability exceeds silhouette instability**: interior flicker 0.00581 against
  0.00086 at ENTRY, interior p95 2.05 %, worst frame 20.7 % of pixels, and `colRunsMax` 75 inside
  against 7-9 wherever the storm has an edge against sky.
- Mean ghosting is small (<= 0.00055) but **tail events exist** (max 0.675), with bias within
  +/-0.0002 of zero, so there is no systematic trailing smear.
- **Disocclusion error grows substantially toward the interior**: 0.00023 -> 0.00389 -> 0.00490 ->
  0.00665 across OUTSIDE -> APPROACH -> ENTRY -> INTERIOR, a 29x spread.

**Policy consequence: current temporal accumulation must not be treated as a safety net for future
aggressive sampling changes.** A change that moves or subsamples the sampling lattice cannot
expect the history blend to rescue it, because the blend is not currently buying that stability.
This is the quality-side counterpart to T151's rejection of interleaved reconstruction.

---

## 9. T160 morphology implications

Recorded here as **future T098b requirements only**. No profile value is modified by T139.

- Move the **ANVIL radius-growth knee later than the current v ~= 0.62**.
- Develop a **broader upper canopy**; the radius currently peaks at v ~= 0.65 and declines while
  `verticalShape` fades, producing the rounded cap.
- Tune the **final radius endpoint independently of the knee** - endpoint sets how wide, knee sets
  where widening stops.
- Compare the **rendered ABOVE silhouette against the final-density footprint**. T160 measured the
  density footprint as roughly a 1.5:1 ellipse (468 x 312 blocks) while the in-game view appears
  markedly more circular; it did not measure rendered occupancy, so a renderer/reconstruction
  contribution is not excluded.

Quality-ladder relevance: if that comparison returns Outcome B - density elliptical, rendering
circular - then internal resolution and reconstruction are implicated in the canopy defect, and
this ladder becomes an input to the morphology fix rather than independent of it.

---

## 10. Dependency on the future core-renderer optimization

A separate experiment is determining whether the production shader's execution context and live
state are the dominant remaining cost. T139 does **not** wait on it, and this policy is written to
stand without it.

If that experiment yields substantial headroom, the following are expected to be revisited, in
order: **Ultra internal resolution** (section 7); the four unmeasured modes' scales, which were
set by the Rank 1 frontier under the current cost model; and the per-mode cloud GPU budgets, which
have never been met at any ladder. If it yields little, this ladder stands as the shipping policy
and the gap to SC-006 remains open.

---

## 11. Policy mistake found and corrected

**`AtmoCommonConfig.CloudRaymarchQuality` carried the pre-Rank-1 ladder and was showing it to
players.**

| mode | enum said | shipped reality | error |
|---|---|---|---|
| LOW | 0.25 | 0.125 | 2x |
| LOW_24 | 0.375 | 0.125 | 3x |
| MEDIUM | 0.50 | 0.125 | 4x |
| HIGH | 0.50 | 0.1875 | 2.7x |
| ULTRA | 0.75 | 0.25 | 3x |

`ProjectAtmosphereQuickOptionsScreen.cloudQualityLabel()` renders
`getRaymarchSteps() + " steps, " + round(getResolutionScale() * 100) + "%"` directly from that
enum, so the in-game quality label advertised resolutions **2x to 4x higher than the renderer was
using**, on every mode. The `cloudRaymarchQuality` config comment advertised the same stale ladder
to anyone editing the config file.

**Corrected** in this task: the enum values now match `VolumetricQualityProfile`, the config
comment states the shipped percentages, and the enum carries a javadoc making clear it is a label
that must track the profile. Step counts were already correct and are unchanged.

**Blast radius checked.** Only two call sites read these fields:
`ProjectAtmosphereQuickOptionsScreen` (the label) and `StormT135PerformanceProfile:287-288` (the
performance record's *configured* field). **No rendering behaviour depends on them** -
`CloudFieldVolumeRenderConfig` reads `VolumetricQualityProfile`, not the enum.

**Recorded evidence is not contaminated.** `StormT135PerformanceProfile` writes
`effectiveResolutionScale` from `VolumetricCloudRenderer.lastResolutionScale()` (line 257) and
that is the column printed in every `T1xx_CELL` row, so all recorded measurements report the true
runtime scale. The stale value sat only in the separate "configured" field.

Two smaller inconsistencies noted and **not** changed, as they are cosmetic: the `LOW_24` constant
displays as "LOW 32" (it runs 32 steps, so the label is accurate and the constant name is the odd
one), and four `ULTRA|96|0.750|1920x1080|960x540` cells exist where the recorded effective scale
and the target dimensions disagree - most likely the effective scale being sampled at cell start
before a resize landed.

---

## 12. What T140 must reprofile once the architecture work settles

T140 is **not** started by this task. When it runs it must:

1. Measure **all five modes at their shipped scales** - Low, Low 24 and Medium at 0.125, High at
   0.1875, Ultra at 0.250. Four of the five have no valid cell today, and the existing non-Ultra
   cells are at the superseded ladder and must not be reused.
2. Use T135's written per-mode targets (3.0 / 4.0 / 5.0 / 6.5 / 8.0 ms cloud, 16.7 ms total frame)
   and record per-mode pass/fail honestly; the current expectation is failure at every mode.
3. Re-measure rather than reuse the T147/T149 cost shares, per T158's closure.
4. Include representative visual checks per mode, and re-run T152's route if the sampling or
   reconstruction path changed.
5. Re-derive the ladder itself if the core-renderer experiment moved the cost model - the Rank 1
   frontier that chose these scales was measured under the current one.
6. Not treat this document's Ultra as a visual target; section 6 applies.

---

## 13. Gate

`./gradlew check build`: **BUILD SUCCESSFUL in 2m 55s**, 23 actionable tasks (18 executed,
5 up-to-date), 0 failures. The ladder-label correction in `AtmoCommonConfig` is therefore neutral
against the existing regression set, as expected for values no rendering path reads.
