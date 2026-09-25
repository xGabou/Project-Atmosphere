# T225 - Rain-Driven Ray-Volume / Lighting-Gate Stability

**The question:** if the camera does not move, can a change in the global rain value alter cloud
rendering just because the ray-volume floor or an in-cloud gate moves?

**The answer: YES - and the worst case was not a lighting gate at all. CASE A, fixed.** With the
camera held still, ramping the global `MaxPrecipitation` produced a **+95-level whole-frame luma
jump and a +0.38 alpha jump in one frame**. It happened when the ray-volume floor passed the
camera, and the camera was *not* moving.

The mechanism is a rain-pass sentinel error. A ray the rain pass rejects before its march (it
misses the ray volume, or terrain stands in front of it) wrote `vec4(0.0)`. In the rain texel
format that decodes as **transmittance 0 at depth 0**: an opaque black rain layer. Since T207 the
temporal resolve composites the rain texel into every pixel. So **every camera below the ray volume
saw the lower half of the frame black, and every camera above the slab top saw a black sky.**

Global rain decides who is below the volume: rain drops the floor under the ground and hides the
defect. It shows fully **in dry weather below the cloud base** (the floor is then the slab base),
which is ordinary gameplay. The fix writes the existing "no rain" sentinel (`4095`, as the T210
coarse mask already does) at those three returns. After it, the largest one-frame luma change on
the rain-on ramp is **0.06 levels** (was 95.4), alpha 0.00009 (was 0.38). Zero added work. A
sandbox invariant pins it and fails against the old code.

Everything else the brief asked about is continuous:
- **Ray entry:** a stationary floor crossing moves luma ~0.02 levels per frame, same as its
  neighbours, lumaPop 0.
- **Od-28 early-out:** no measurable effect.
- **In-cloud gate:** cannot be switched by rain.

One real **global coupling remains, continuous (no pop)**: the march's `rainFraction` darkens
non-puff cloud below its profile base by `max(morphology.a, MaxPrecipitation)`. So rain on a remote
cell darkens the local storm by up to 6-8 levels. It is attributed exactly (an arm reading the local
value alone flattens it to 0.01 levels). It is **deferred, not fixed**: for descriptor-owned storms
the local value is 0 (they are absent from the raster), so today *all* of a storm's own rain
darkening comes from the global max. Removing it would delete a legitimate rain effect. It needs a
per-descriptor precipitation input first.

## RECOVERY

1. **Starting HEAD:** `fd2b242` (T224, the camera-height light cap removed).
2. **Branch:** `quality/t217-deep-interior-lighting` (not merged). `PAchangelog.md` preserved and
   uncommitted. `campaignGate` PASS before every client run.
3. **Production state:** T224's full light cone (6 taps / 240 blocks) for every camera; production
   constants unchanged (temporal 1, upscale -1, rain lattice 0, origin jitter 0, macro envelope 3,
   lower body 1, deep scatter 1, field bits 43). Coverage pretest OFF in production
   (`coveragePretestEnabled = false`).

## DEPENDENCY MAP (Task 1)

4-6. **Every production branch through which the global rain scalar reaches the frame.**
`MaxPrecipitation` = the maximum `precipitationIntensity` over every render cell in the weather map
(`CloudWeatherMapRenderer`), uploaded once (`VolumetricCloudRenderer`) and copied to the rain pass
(`copyFrameUniforms`).

| # | where | exact input / threshold | branch outcome | what can change |
|---|---|---|---|---|
| A | `main()` ray volume | `lowY = SlabBaseY - mix(0, 180, smoothstep(0.02, 0.85, MaxPrecipitation))` (plus funnels) | `t0/t1` of every ray | march start/end; **continuous** in the scalar |
| B | march strides | `baseStep = (t1-t0)/stepBudget`; `coarseStep = max(1.5 baseStep, 3 fineStep)`; in-cloud `legacyFineStep = max(0.5 baseStep, 2)` | step lattice of rays whose span the padding lengthens | sample positions; **continuous** |
| C | early rejects in `main()` | `t1 <= t0` (volume miss), `t1 <= t0` after `sceneRayLimit` (terrain first), `!anyCoverage` (pretest, off in production) | **rain pass wrote `vec4(0)` = opaque rain at depth 0** | **discrete: a black half frame / black sky - THE DEFECT, fixed** |
| D | `cameraStartsInsideSlab = t0 <= 1` | floor vs camera | gates the od-28 cone early-out, the near-probe pretest (off), `cameraInsideCloud` | early-out only trims light below `exp(-28)`; see F |
| E | od-28 early-out | `cameraStartsInsideSlab && od*Ext >= 28` | cone stops | radiance below 7e-13 of the sample; **invisible** |
| F | `cameraInsideCloud` | `cameraStartsInsideSlab && CameraCloudDensity > 0.08` | 2-sample analytic probe replaces the cone; fine-step and growth choices | cannot flip by rain: see items 20-23 |
| G | march `rainFraction` | `!directPuffBody && (precipitationSample \|\| p.y < baseY)`: `0.25 + 0.75 max(morphology.a, MaxPrecipitation)` | darkens sun 20%, ambient 32%, tints | **continuous global coupling, attributed, deferred** |
| H | rain shafts (rain pass) | `rainShaftDensityAt`: `MaxPrecipitation <= 0.02` -> none; descriptor-owned storms take `precipitation = MaxPrecipitation` | legitimate rain | rain presence/intensity (Task 13) |
| I | rain pass `rpRainFraction` | as G, on rain samples | rain shading | legitimate rain |
| - | compiled out of lean FINAL | `precipitationCandidate` (`PA_PRECIPITATION_ABSENT`), rain segment test (`PA_ARM_NO_RAIN_SEGMENT`) | - | nothing |
| - | diagnostic only | T140 oracle padding, quadrature diagnostic `MaxPrecipitation <= 0.02`, debug views 5-7 | - | nothing in FINAL |
| - | CPU, diagnostic only | `worldTimeAffectsDensity`, `LastDrawInputs` hashes | stability digests | never invalidates history in production |

## RAIN-RAMP (Tasks 2-5)

Harness: `T225M` (`t225-rain-ramp.txt`), a STATIONARY route. Four fixed cameras, each held 550
frames: 40 frames of settle at 0, then a linear 0 -> 0.90 -> 0 ramp of a `MaxPrecipitation`
override. The override replaces the uploaded scalar for every program, so the shader sees exactly
what a remote cell raising the global max would produce while the local storm's cells stay as they
are. Production temporal configuration (mode 1, 0.25). Arms:
- `hold_body`: scalar held at 0.45, rain pass off. The stationary noise floor.
- `ramp_body`: ramp, rain pass off. The cloud body's response alone.
- `ramp_full`: ramp, rain drawn.

The frame line logs the replicated geometry (`padding`, `inRayVolume`, `inCloudGate`, `lightPath`,
centre-ray `t0/t1`, `override`, `naturalMax`, `localPrecip`, `remotePrecip`, `alphaMean`).

Poses: **P0_FLOOR** 1.6R at y = SlabBase - 2 = 128 (the floor crosses it early in the ramp);
**P1_PVN** 1.6R, y 120; **P2_EDGE** 1.12R at mid body; **P3_THROUGH** 0.82R, 0.62h. All four had
camera density 0.

7. **Rain sequence:** 0 (settle) -> 0.90 -> 0, linear, 0.0035 per frame.
8. **Padding:** 0 -> 180 blocks (smoothstep 0.02-0.85), up to ~1.15 blocks per frame.
9. **Ray-volume floor:** 130 -> -50. It crosses P0 at override 0.067 (frames 59/532) and P1 at
   0.138 (629/1062). P2 and P3 are always inside.
10. **Classification transitions:** `inRayVolume` flips only at those four frames. **`inCloudGate`
    never flips; `lightPath` is `cone` on every frame of every arm.**
11. **Luminance jumps.**

    | | P0 | P1 | P2 | P3 |
    |---|---|---|---|---|
    | hold (floor) max 1-frame, levels | 0.008 | 0.020 | 0.033 | 0.033 |
    | body ramp max 1-frame | 0.069 | 0.061 | 0.026 | 0.041 |
    | body, around the floor crossing | ~0.02 (lumaPop 0) | ~0.02 | - | - |
    | **full ramp, pre-fix** | **95.44** | **95.29** | 0.026 | 0.043 |
    | full ramp, post-fix | 0.061 | 0.064 | 0.026 | 0.041 |

    The pre-fix jumps sit where the floor is ~1.3-1.8 blocks above the camera: frame 52 at P0
    (floor 129.6, camera 128) and frame 627 at P1 (floor 121.3, camera 120). That is the moment
    the downward rays stop missing the volume.
12. **Alpha jumps:** pre-fix full ramp **0.381 / 0.379** (alpha 0.106 -> 0.487 while the camera is
    below the floor); post-fix 0.00009 / 0.00012, the same as the body arm (0.00012-0.00015).
13. **Coverage jumps:** pre-fix 0.381 / 0.379; post-fix 0.00015. Silhouette steady.

## RAY ENTRY (Task 6)

14. **Baseline:** the cloud body is continuous through the floor crossing (item 11). The ray's
    `t0` goes continuously to 0 as the floor descends past the camera. The only discrete things
    the crossing flips are the early-out and the in-cloud gate (items 16-23), and neither shows.
15. **Fixed-entry diagnostic** (`ramp_body_fixed`: padding held at 0, floor pinned at 130, no
    `inRayVolume` flips): the body still darkens almost identically. P0 7.2 vs 7.7 levels, P1 6.2 vs
    6.4, P2 5.49 vs 5.48, P3 3.1 vs 2.9. **Ray geometry is not the cause of the continuous
    response.** Geometry alone moves alpha by 0.004 at P0, continuously. The discrete jump was the
    rain-pass sentinel, which is geometry-triggered but not geometry: holding entry fixed would hide
    it (the camera never leaves the volume) without fixing it.

## LIGHT SHORTCUT (Task 7)

16. **Trigger frequency:** not countable in lean FINAL (the workload counters are compiled into
    the monolith only). The trigger is `cameraStartsInsideSlab && od x Ext >= 28`. The ramp flips
    its gate at the four floor crossings.
17. **Affected pixels:** those whose cone passes od 28. The light it drops is at most `exp(-28)` =
    7e-13 of the sample's direct term. At the four crossings the frame moves ~0.02 levels with
    lumaPop 0, indistinguishable from neighbouring frames.
18. **Disable-shortcut result:** not built. The brief conditions it on a visible jump, and there is
    none.
19. **Cost:** none added.

## IN-CLOUD GATE (Task 8)

20. **Ray-volume classification:** `inRayVolume` follows the floor (flips at P0/P1).
21. **Local density classification:** `CameraCloudDensity` comes from
    `CloudCellDensityMath.densityAt` (cell bodies only; no rain term), smoothed by
    `CameraCloudDensityTracker`. It is 0.000 at all four poses on every frame.
22. **Analytic-path transitions:** none (`inCloudGate` 0 on every frame; `lightPath=cone`).
    Structurally none are possible: the gate needs `CameraCloudDensity > 0.08`, density exists only
    inside cell bodies, cell bodies lie above SlabBaseY, and the padding only ever lowers the floor.
    So a camera dense enough to pass the density term is inside the volume whatever the rain.
23. **Bug: NO.** The two concepts are ANDed, not conflated, and rain can only move the one that is
    already satisfied.

## GLOBAL VS LOCAL RAIN (Tasks 9-10)

24. **Global max rain:** 0.458 (natural) in every T225 session.
25. **Local rain:** the fixture storm's own cells: 0.458. `remotePrecip` = 0.0; this world has one
    storm group (10 cells) and no other weather cells, so today the global max *is* the local
    storm.
26. **Remote-rain case:** constructed. The override raises the shader's global scalar exactly as a
    heavier remote cell would, with the local storm's cells unchanged. A natural remote heavy cell
    does not exist in this world.
27. **Local-render impact:**
    - **Rain pass (pre-fix):** a remote heavy cell that drops the floor under a camera *un*hides
      nothing and hides the defect. A remote cell drying out is what exposes it: the whole lower
      frame goes black. Fixed.
    - **Cloud body (the `rainFraction` coupling, G):** continuous darkening of the local storm as
      the global scalar rises. P0 -8.2, P1 -6.3, P2 -6.0, P3 -2.0 levels over 0 -> 0.9, linear,
      ~0.01 levels per frame at this ramp rate, no pop. **Attributed exactly:** `ramp_body_local`
      (rainFraction reading `morphology.a` alone) flattens it to 0.37 / 0.11 / 0.01 / 0.39 levels,
      the P0/P1 remainder being the geometry of item 15. **And the local arm renders exactly as
      the dry state: this storm's `morphology.a` is 0, because descriptor-owned storms are absent
      from the raster map.** Its own base darkening today comes 100% from the global max.
    - **Rain shafts (H):** a descriptor-owned storm's shaft intensity is `MaxPrecipitation` itself
      (line 5603, "their separate precipitation intensity therefore comes from the frame
      maximum"). Its rain renders at the heaviest cell's intensity anywhere.

## CANDIDATE

28. **Exact change** (class: none of the brief's A-D; it is the branch the measurements actually
    point at). `cloud_atmosphere_volume.fsh`:
    ```glsl
    #if defined(PA_RAIN_PASS) && !defined(PA_RAIN_MASK_OUTPUT)
    const vec4 PA_EMPTY_RAY_OUTPUT = vec4(0.0, 0.0, 0.0, 4095.0);   // rgb 0, packed (depth 0, T_r 1)
    #else
    const vec4 PA_EMPTY_RAY_OUTPUT = vec4(0.0);
    #endif
    ```
    The volume-miss, terrain-clip and pretest returns in `main()` write `PA_EMPTY_RAY_OUTPUT`
    instead of `vec4(0.0)`. The cloud program and the rain mask are unchanged (they keep 0). The
    T140 oracle's diagnostic return is untouched. Sandbox (`T206_PRODUCTION_SPLIT`) now requires
    the sentinel and exactly three uses (`rainEmptyRaySentinel=4095`). Verified to FAIL with one
    return reverted, and to pass with the fix.
29. **Stationary continuity:** post-fix full ramp, max one-frame luma 0.061 / 0.064 / 0.026 / 0.041
    levels (P0-P3), alpha 0.00009-0.00012, coverage 0.00015. **Every pose sits on its body-only
    arm and on the hold arm's noise scale.** The discontinuity is removed, not reduced.
30. **Normal rain correctness:** sentinel captures (P1 = PLAY_VIS_NEAR, 0.25):

    | | pre-fix | post-fix |
    |---|---|---|
    | DRY, rain pass on: near-black pixels | **43.8%** (P1 terrain alone 16.9%) | 31.0% = its body-only twin (terrain) |
    | DRY vs DRY-body-only | black lower half | mean \|d\| 0.024 |
    | above slab top (natural rain): near-black | **38.1%** (sky) | **0.0%** |
    | natural vs WET 0.45 | identical | mean \|d\| 0.046 |
    | natural vs DRY | - | 2.8% of pixels (the rain shafts vanishing when dry) |

    Rain still appears, disappears and scales with the scalar. The ramp's in-volume poses give
    identical full and body curves where no rain is in view.
31. **Motion:** stationary ramp as item 29. Standard T152 route, production arm (`deep_scatter_on`
    = production temporal, 2200 frames): dLumaMean 0.00346, lumaPop 0.078, dLuma2 0.00547,
    flicker 0.00205, 19 frames > 2 levels, in line with T224's full-reach route (0.00348 / 0.079 /
    35 jumps). The route never leaves the ray volume in rain (floor 33.5), so the fix is a no-op
    along it by construction; no regression.
32. **Added cost:** none. It writes a different constant on paths that already return, and a
    pixel whose rain texel now reads "no rain" skips the resolve's rain composite. Capture pin 0.25:
    P1 dry 4.89 ms vs natural 4.91 ms.
33. **<= 10 ms:** YES. In-volume rendering is bit-identical, so T224's whole-renderer totals
    (SIDE 9.35 / PVN 8.82 ms) stand.

## DECISION

34. **KEEP the sentinel fix (CASE A). DEFER the `rainFraction` coupling (CASE D / the brief's
    CASE B precondition unmet).**
35. **Mechanism.** The global rain scalar's only *discontinuous* path to the frame was the rain
    pass's early-reject output. Rain positions the ray-volume floor, the floor decides which rays
    are rejected, and a rejected ray's `vec4(0)` read as opaque rain in the T207 resolve. Ray entry,
    step lattice, early-out and gate classification are all continuous or invisible. The remaining
    global coupling (the march's `rainFraction`) is continuous and bounded (<= ~8 levels over the
    full scalar range). It is also the only rain darkening a descriptor storm gets, because the GPU
    has no local precipitation for descriptor storms, so "use the local value" would remove a real
    effect. The same is true of the storm's rain-shaft intensity.
36. **Production change:** the `PA_EMPTY_RAY_OUTPUT` sentinel (item 28) and its sandbox invariant.
    Nothing else. The attribution arms (`PaFixedRayPadding`, `PaRainFractionLocal`) are removed;
    the capture driver is restored to HEAD. **Kept as instruments:** the `precipitationOverride`
    (NaN in production), the STATIONARY rain-ramp route with its hold/ramp arms (`T225M`,
    `t225-rain-ramp.txt`), the frame-line geometry fields, `CloudWeatherMapRenderer.lastCells()`,
    and `VolumetricCloudRenderer.lastSlabTopY()/lastNaturalMaxPrecipitation()`.
37. **Exact next task.** **T226: per-descriptor precipitation.** Carry each storm descriptor's own
    `precipitationIntensity` to the GPU (a descriptor texel channel), and measure first. Then make
    the march's `rainFraction` (G) and the descriptor-owned shaft intensity (H, line 5603) read the
    local value instead of the frame maximum. Validate with the T225M ramp: the target is
    `ramp_body` flat under a remote-only ramp, with the storm's own darkening and shafts preserved
    at its own intensity. Only then consider making the ray-volume padding local (A), which is
    already continuous and harmless.

## VALIDATION

38. **campaignGate:** PASS (`gate-t225.log`, `rainEmptyRaySentinel=4095`); negative control FAIL
    as intended (`gate-t225-neg.log`).
39. **check build:** PASS (`check-build-t225.log`, 58 s).
40. **stormMorphologySandbox:** PASS (10 PASSED, 0 FAILED, `sandbox-t225.log`).
41. **Markers:** `t225-rain-ramp.txt`, `t211-captures.txt` and `t217-deep-motion.txt` retired
    after their runs; `t132-autorun.txt`, `t132-autorun-exit.txt` and `t098-captures.txt` as
    before.
42. **git status:** `PAchangelog.md` modified and uncommitted; everything else committed.
43. **Commit:** see the branch log (not merged).

## Evidence (`t225-campaign/`)

| file | what |
|---|---|
| `t225-run1-ramp-prefix.log` | stationary ramp, pre-fix: hold / body / full |
| `t225-run2-sentinel-prefix.log`, `T225_P1_dry_rainpass_over_body_prefix.png`, `T225_UP_nat_over_dry_prefix.png` | the black half-frame and black sky, pre-fix |
| `t225-run3-ramp-postfix.log` | ramp post-fix, with the fixed-entry arm |
| `t225-run4-ramp-attribution.log` | the local-rainFraction attribution arm |
| `t225-run5-sentinel-postfix.log`, `T225_postfix_DRY_P1_over_NAT_UP.png` | captures post-fix |
| `t225-run6-route-regression.log` | standard route regression |
| `t225_ramp.py` | analysis (frame logs trimmed to the fields it reads) |
