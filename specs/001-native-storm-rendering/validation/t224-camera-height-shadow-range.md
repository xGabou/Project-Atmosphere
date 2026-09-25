# T224 - Camera-Height Shadow Range Pricing

**The question:** how much visual error and GPU cost come from shortening the light path from
~240 blocks to ~102 blocks when the camera enters the cloud layer's height band?

**The answer: CASE A. The cap is removed and every camera now marches the full cone.** Full reach
costs nothing measurable at the production scale (PVN +0.04 +- 0.15 ms, SIDE -0.17 +- 0.22 ms,
ten-shot ABBA pairs). It restores the anvil's shadow on the tower under the overhang, which
production left sunlit. On the standard motion route it is *steadier* than production (luma
instability -13 to -18%). And it removes a whole-frame switch that fires on precipitation as well
as on camera height.

What the brief's premise got wrong matters as much as the price. **The "band" is not the cloud
layer's height band.** The flag tests entry into the *ray volume*, and precipitation pushes that
volume's floor up to 180 blocks below the cloud base. In the measured storm (MaxPrecipitation
0.43-0.46) the floor sat at **y = 33-43, below sea level**. So every above-ground camera below the
anvil top was in band, and the 240-block cone never ran at any gameplay height while it rained.
The "below the layer" state the brief describes only exists over dry weather. The switch then fires
when the camera crosses y = SlabBaseY - padding, or when MaxPrecipitation (the maximum over
*every* cell in the weather map) moves that floor past a stationary camera. That is a pop in time
that no altitude test can see.

## RECOVERY

1. **Starting HEAD:** `56de323` (T223, "a low-passed base field keeps the veins; shadow-form line
   closed").
2. **Branch:** `quality/t217-deep-interior-lighting` (not merged). `PAchangelog.md` modified and
   uncommitted, preserved. `campaignGate` PASS before every client run (`compile-t224*.log`).

## THE RULE (Task 1, read from the production path)

3. **Exact height rule.** In `main()` (the production path; a second definition at the
   material-trace diagnostic `stormMaterialTraceAt` is `CameraPos.y` in `[SlabBaseY, SlabTopY]`
   and feeds nothing else):
   ```glsl
   float lowY = SlabBaseY - precipitationRayPadding();   // mix(0, 180, smoothstep(0.02, 0.85, MaxPrecipitation))
   if (FunnelCount > 0) lowY = min(lowY, min(Funnel0A.w, SlabBaseY) - 40.0);
   ... t0 = entry distance into [lowY, SlabTopY] along the ray ...
   bool cameraStartsInsideSlab = t0 <= 1.0;
   ```
   Then in `lightMarchOpticalDepth`: `steps = clamp(LightSteps, 2, 8); if (cameraStartsInsideSlab)
   steps = min(steps, 4);`. It is effectively a per-camera switch, uniform across the frame. From
   outside the volume `t0 <= 1` only within one block of its boundary. The same flag also gates
   (a) the od-28 cone early-out, which only trims light already below `exp(-28)`, and (b) the
   in-cloud path: `cameraInsideCloud = cameraStartsInsideSlab && CameraCloudDensity > 0.08`
   replaces the whole cone with a 2-sample probe (18 x local + 82 x a probe 28 blocks up-sun).
   - **Taps:** 4 in band, `LightSteps` (6 at ULTRA 96/6) otherwise. MAX_LIGHT_STEPS = 8.
   - **Cone spread:** unchanged. It is a fixed golden-angle spiral per tap index,
     `(i + 0.5) * 0.28 * stepLength * 0.24`, so taps 0-3 are identical in both cases.
   - **Shared field use:** unchanged. Every tap reads the field when bits 1|8 are set (production
     bits 43); detail on taps 0-1 only, in both cases.
   - **Storm role:** none. The rule reads no role, lobe or descriptor.
   - **Measured boundary this session:** SlabBaseY 130.0, SlabTopY 1011.5-1013.6,
     MaxPrecipitation 0.425-0.456 -> padding 87-97 blocks -> **floor y = 33.3-43.1**. The new
     frame-line instrument logs this (`rayFloorY`, `slabBaseY`, `maxPrecip`).
4. **Short-range taps (in band):** 14.0 / 33.9 / 62.1 / 102.2 blocks (first step 14, growth
   x1.42).
5. **Long-range taps:** 14.0 / 33.9 / 62.1 / 102.2 / 159.1 / 240.0 blocks.
6. **Short max reach:** 102.2 blocks (plus the spiral's lateral offset).
7. **Long max reach:** 240.0 blocks.

## BASELINE HEIGHT EFFECT (Tasks 2-3)

Run 1 (fixture `1c89adfd`, radius 671): H0 at y=120 and H1 at y=170, both at 1.6R and aiming at
the storm centre. **Both turned out to be IN band** (floor ~43), so the H0/H1 difference is
viewpoint, not the rule. The rule's effect is measured instead at **zero parallax**: P0
(production) against R1 (cap lifted) at one pose. The switch is binary and frame-uniform, so an
in-band R1 frame *is* the below-band rendering of that view. That makes the zero-parallax pair the
cleaner measurement of what the brief asked. P0B/R1B repeats give the image floor (0.00-0.09
levels).

8. **H0 luminance (production):** dome 118.25, lower mass 135.94 (8-bit, inner regions, neck split
   per T221).
9. **H1 luminance (production):** dome 117.35, lower mass 131.13.
10. **Shadowed fraction (light-path view, od > 4):** dome 85.7% (H0) / 96.1% (H1); lower mass 0% at
    both. With full reach the H0 dome od median goes 7.50 -> 8.31.
11. **Transmittance delta:** lower mass at H1, od median 0.63 -> 0.63. **Unchanged**: the visible
    lower mass from the ground sits more than 240 blocks below the anvil base, so neither cone
    reaches the overhang. Dome transmittance falls with the extra ~0.8 od.
12. **Lower-mass difference (R1 - P0):** H0 +0.00, H1 -0.23, LOWSIDE -0.30 levels (noise). **SIDE
    lower/neck -5.91 levels, 41.6% of pixels > 2 levels.** That is the neck directly under the
    anvil, which production lights white (`t224-campaign/T224_SIDE_neck_P0_vs_R1.png`).
13. **Dome difference:** darker at every pose: H0 -3.15, H1 -3.52, LOWSIDE -3.86, SIDE -1.44
    levels. Local contrast drops slightly (e.g. 3.96 -> 3.35). Whole covered cloud -3.2 to -3.5
    levels; 28-43% of covered pixels move more than 0.010 luma. Anvil shadow reach: the full cone
    darkens exactly what lies 102-240 blocks below an up-sun occluder (noon sun, `dayTime` 6000).

## FULL-RANGE ARM (Tasks 4-7)

`PaLightReach` (a live uniform on lean FINAL, `leanFinal=true` on every shot) lifted only the
4-tap cap. The early-out and everything else were untouched.

14. **R0 (production):** as in items 8-13: tower/neck under the anvil sunlit; the dome already
    85-96% shadowed.
15. **R1 (full reach):** the anvil's shadow falls on the tower top under the overhang
    (`T224_THROUGH_P0_over_R1.png`, run 4: camera in clear air inside the body, **-12.5 levels,
    57% of pixels > 2 levels**, repeat floor 0.39). Lit patches remain only outside the anvil's
    footprint and on the lower flanks. Where the camera is in dense cloud (POCKET, INSIDE,
    camera density 0.80-0.81) R1 is an **exact no-op**, because the in-cloud probe replaces the
    cone. Out of band (the slab-top sweep) it is also an exact no-op.
16. **Shadow recovery: YES.** It happens exactly where the brief predicted: material 102-240
    blocks below the anvil (the neck at SIDE, the tower top from inside the body). The ground-level
    lower mass is too far below for either cone.
17. **Over-darkening check: not excessive.** The dome darkens 1.4-3.9 levels (it is already
    86-96% shadowed; `d(8bit)/d(od)` is small there). The tower under the anvil drops to a
    readable shadowed grey with structure intact, not black. On the route the cloud luma is
    -5.4% overall (-3.9% OUTSIDE, -4.0% APPROACH, -5.0% ENTRY, -6.6% INTERIOR). The largest
    per-frame change is in a clear-air pocket under the anvil (150 -> 70 levels, frames 866-872):
    the physically expected shadow, not a defect. The one real cost is a slight loss of dome
    local contrast (item 13). The dome's flatness is a separate, known issue (T221-T223).
18. **Light-march cost:** the only change is +2 taps (taps 5-6, field reads, no detail) per cone
    march. Production scale 0.25 (cloud target 400x225), ten-shot ABBA:
    **PVN +0.040 +- 0.153 ms, SIDE -0.173 +- 0.219 ms. Below the noise floor.** At the 0.75
    capture pin (9x the pixels), PVN +0.73 +- 0.31 and SIDE +0.60 +- 0.14 ms; LOWSIDE +0.05 on
    the 7 valid shots (its fixture dissolved at shot 7, flagged `descriptors fell 10 -> 0`, and
    every later shot was discarded). Scaled by pixels this gives ~0.1-0.2 ms, consistent with
    T201's ceiling of 0.27/0.31 ms for the *whole* 4-tap cone. Band: **<= 0.25 ms, easy**.
    PLAY_VIS_NEAR is the H0 pose (1.6R, y=120), so it is covered.
19. **Renderer cost (whole renderer, `_TEMPORAL` totals via the T208 campaign, 0.25 phase,
    1920x1080):**

    | | raw phase | field | rain | total (min-max) |
    |---|---|---|---|---|
    | SIDE, post-bank (full reach) | 6.14 | 1.94 | 1.08 | **9.35** (9.28-9.41) |
    | SIDE, capped control, next session | 6.69 | 1.70 | 1.12 | 9.70 (9.67-9.72) |
    | PVN, post-bank (full reach) | 5.12 | 1.98 | 1.54 | **8.82** (8.77-8.90) |
    | PVN, capped control, next session | 5.36 | 1.77 | 1.63 | 8.95 (8.87-9.06) |

    **<= 10 ms: YES.** Full reach is *not* above its own control (-0.36 / -0.13 ms across two
    sessions with different storms, `c52fe6ab` / `382b4ac0`), which matches the paired ~0. The
    control was the pre-T224 cap restored temporarily, run back to back and then removed. **But
    both sit ~1.7 ms above the T215 class (7.66 / 7.17).** Today's fixtures are heavier storms
    (the field build alone, which the reach cannot touch, reads 1.70-1.98 against 1.36-1.41),
    so the <= 10 ms margin is thin *independently of T224*. Recorded as a standing risk. Cloud-
    pass capture totals at 0.25 in the paired run: PVN 4.55 / 4.59, SIDE 6.68 / 6.51 ms
    (production / full reach). A first attempt with the `t135-performance.txt` budget contract
    was stopped: it sweeps legacy LOW/MEDIUM/HIGH modes on `arm=production`, not the lean FINAL
    0.25 path the budget refers to.

## TRANSITION (Task 9)

The boundary a player can cross depends on the weather:
- **Raining (this storm):** the floor is below sea level, so no above-ground camera crosses it
  from below. The first sweep, centred on y = 43, was aborted: the route's chunks were unloaded
  and the heightmap read the world floor, so the camera would have gone underground
  (`t224-motion-aborted.log`). The only crossing above ground is the **slab top**, which run 2
  swept (8 cycles, y 966-1056, 16 crossings, 2200 frames per arm).
- **Dry (MaxPrecipitation < 0.02):** the floor is SlabBaseY (130 here), which is crossable. Its
  one-frame pop equals the zero-parallax R1-P0 difference.
- **In time:** any change in MaxPrecipitation moves the floor, and a stationary camera is crossed
  in one frame.

20. **Boundary luminance delta.** At the slab top: **0.000 levels** (prod - full lumaMean offset
    in band +0.000, out of band -0.000, sd 0.006 / 0.000). Nothing lies 102-240 blocks up-sun of
    a dome seen from above. At a base crossing (dry weather) or a precipitation-driven floor
    move: **a one-frame whole-frame step of -3.2 to -3.5 levels** over covered cloud, with 28-43%
    of cloud pixels jumping more than 0.010 luma. That is ~100x the repeat floor, and 4-5x the
    route's mean natural frame-to-frame change (dLumaMean 0.0028).
21. **Popping.** Slab top: none. The 16 crossing frames are at the route's noise (dLumaMean
    0.0017 prod / 0.0017 full, lumaPop 0.031 / 0.031, against 0.0028 / 0.063 elsewhere).
    Base/precipitation crossing: a global pop of the size in item 20. **With the cap removed there
    is no longer any switch to pop.**
22. **Flicker.** Alpha flicker is unaffected by construction (a radiance-only change): 0.00238 vs
    0.00238 on the standard route. The self-referential luma metrics on the standard route (run 3,
    2200 frames per arm, all segments) are **better with full reach**: dLumaMean -16%, lumaPop
    -18%, dLuma2Mean -13%; >2-level frame jumps 43 -> 35; per frame, full reach is better by >25%
    in 276 frames and worse in 108. Both reach arms (production temporal path, 0.25) share a
    5-frame, ~15-level dip-and-recover episode at a few points of the slab-top sweep that the
    single-frame 0.50 control does not show. It appears equally in both arms (63 vs 62 jumps), so
    it belongs to the temporal path, not the reach. Recorded, out of scope.

## OPTIONAL SMOOTH RANGE (Tasks 10-11)

23-26. **Not built.** Task 10 requires full reach to be non-trivially expensive; it is below the
    production noise floor. A height-weighted blend would also barely move: for a raining storm
    every gameplay camera is deep inside the volume, so it would give the short range almost
    everywhere and keep the unshadowed tower. No equation, visual result, transition result or
    cost to report.

## DECISION

27. **KEEP the full range (CASE A).** The camera-height cap is removed from production.
28. **Mechanism.** The cap was a cost cut keyed to the wrong quantity. It keyed on entry into the
    ray volume, not the cloud layer. Because precipitation extends that volume up to 180 blocks
    below the base, it covered almost every gameplay camera during rain. Its 102-block reach
    stops short of the anvil for exactly the material that should sit in the anvil's shadow (the
    tower and neck, 102-240 blocks below). Whenever the floor moved past the camera (altitude, or
    rain intensity anywhere in the weather map) it switched the whole frame by ~3.3 levels.
    The two extra taps are field reads with no detail, which is why they are free.
29. **Production change.** `lightMarchOpticalDepth`: the `if (cameraStartsInsideSlab) steps =
    min(steps, 4);` cap is deleted; every camera marches `clamp(LightSteps, 2, 8)`. The
    diagnostic mirrors (`...EndpointMidpoint`, `...RefinedPair`, `...WithoutDetail`) lose the
    same cap so they keep mirroring production. Debug view 7 (`...CappedFull`) keeps its explicit
    cap as the "former" comparison. Unchanged: the od-28 early-out and the in-cloud probe, both
    still keyed to `cameraStartsInsideSlab`. No constant is pinned by a sandbox for this; the
    `PaLightReach` arm, its config, the T224M campaign and the reach arms are removed. The
    capture driver is restored to HEAD. Kept as an instrument: `VolumetricCloudRenderer.
    lastRayVolumeFloorY/lastSlabBaseY/lastMaxPrecipitation` and the `rayFloorY= slabBaseY=
    maxPrecip=` fields on every `T152_FRAME` line.
30. **Exact next task.** **T225: the global-precipitation coupling of the ray volume.**
    `precipitationRayPadding()` reads MaxPrecipitation, the maximum over every cell in the weather
    map, so the ray volume's floor (and with it `t0/t1` of every primary ray, the od-28 early-out
    and the in-cloud probe gate `cameraStartsInsideSlab && CameraCloudDensity > 0.08`) moves when
    *any* cell's rain changes, however distant. T224 removed the largest consumer (the cone cap).
    T225 should measure whether the remaining consumers pop when the floor crosses a stationary
    camera. The instrument is already on the frame line (`rayFloorY`, `maxPrecip`). A static
    camera plus a forced MaxPrecipitation ramp is the test. Measure first, as here. Separately,
    watch the thin <= 10 ms margin in heavy storms (item 19).

## VALIDATION

31. **campaignGate:** PASS after the collapse (`gate-t224.log`).
32. **check build:** PASS (`check-build-t224.log`, 56 s).
33. **stormMorphologySandbox:** PASS (10 invariants PASSED, 0 FAILED, `sandbox-t224.log`).
34. **Markers:** `t224-reach-motion.txt`, `t211-captures.txt`, `t135-performance.txt` and
    `t208-temporal-cleanup.txt` all retired after their runs. The temporary control cap was
    removed from the shader and verified absent. `t132-autorun.txt`,
    `t132-autorun-exit.txt` and `t098-captures.txt` as before.
35. **git status:** `PAchangelog.md` modified and uncommitted; everything else committed.
36. **Commit:** see the branch log (not merged).

## Evidence (`t224-campaign/`)

| file | what |
|---|---|
| `t224-run1-static.log`, `T224_{P0,R1}_{H1,SIDE}.png`, `T224_SIDE_neck_P0_vs_R1.png` | run 1: height pair, zero-parallax rule effect, light-path views, capture-pin costs |
| `t224-motion-aborted.log` | first altitude sweep, aborted (floor below ground) |
| `t224-run2-motion.log` | slab-top altitude sweep, production_050 / reach_prod / reach_full, 2200 frames each |
| `t224-run2b-cost.log` | 0.75 ABBA cost (PVN, SIDE; LOWSIDE partial; later poses void) |
| `t224-run3-route.log` | standard T152 route, reach_prod vs reach_full |
| `t224-run4-through.log`, `T224T_{P0,R1}_THROUGH.png`, `T224_THROUGH_P0_over_R1.png` | through-body poses |
| `t224-run5-cost025.log` | 0.25 (production scale) ABBA cost, PVN and SIDE |
| `t224-run6-totals-full.log`, `t224-run7-totals-capped-control.log` | whole-renderer totals, post-bank and the capped control |
| `t224_static.py`, `t224_pop.py`, `t224_motion.py`, `t224_route.py`, `t224_cost.py`, `t224_costq.py` | analysis |
