# T232 - Heavy-Storm March Budget Recovery

**The question:** why does the same production renderer grow from ~6.1 ms raw cloud to ~8.8 ms
in a heavy storm, and can SIDE go back under 10 ms without losing T231's quality?

**The answer: the premise was wrong. It is not the storm. KEEP (CASE A).**

- **The cause.** The ~2.9 ms is T231's code:
  - the storm workload is flat across builds (SIDE primary steps 4.3-4.5 M everywhere);
  - per-step cost jumps from 1.43 to 1.95-2.01 ns at exactly the T231 commit;
  - T224 and T230 builds on the same storm and machine run 9.05 and 9.21 ms.
- **Why T231 read as free.** It measured itself by toggling live uniforms inside one build, so
  both arms carried the code.
- **The mechanism.** The shading's per-sample input (the relief form, born inside the primary
  `cloudDensity`) had to travel to the lighting as a value of its own. The NVIDIA driver
  charges ~2.4 ms for that, whatever the route:
  - a global, a single product, an `out` parameter;
  - packed into `localStorm`, packed into the descriptor height;
  - with the arithmetic itself free.
- **The fix.** The form rides in the primary density's 8 low mantissa bits, and the density is
  live across every intervening call anyway.
- **The result.** Paired ABAB: SIDE **11.76 -> 9.33 ms (-2.44)**, PVN **10.20 -> 8.43 ms (-1.78)**:
  - the image is T231's up to a 1/101.6 form quantisation;
  - T231's underside gains are reproduced;
  - motion equals T231's.

## RECOVERY

1. **Starting HEAD:** `95bb4c25` (T231). **2. Branch:** `quality/t217-deep-interior-lighting` (not
   merged; T226 separate on `quality/t226-local-precipitation`). `PAchangelog.md` preserved.
3. **Production state at start:** T213 macro envelope mode 3, T215 lower-body fill, T217
   deep-interior compensation, T231 lower-anvil relief and form shading. Relief and shade were
   **live uniforms** in the lean FINAL program.
4. **Heavy-storm reproduction.** Reproduced at once: HEAD 12.16 / 10.70 ms (SIDE / PVN, T208
   totals).
   - **The bisect.** Same session, GPU clock held at 2340 MHz (nvidia-smi sampled for the whole
     session, `gpu-t232.csv`):
     - T224 source (`fd2b242`): 9.05 / 8.03;
     - T230 source (`61c2945a`): 9.21 / 8.66.
   - **The regression is the T231 commit.** "Heavy storm" was a label for the build, not the
     weather.

## WORKLOAD (T136 counters, 480x270 phase target; "light" = the T224 build, "heavy" = HEAD)

| item | light SIDE | heavy SIDE (run b / ABAB A1) | light PVN | heavy PVN (b / A1) |
|---|---|---|---|---|
| 5-6. cloud coverage (materialFraction) | 0.420 | 0.480 / 0.418 | 0.401 | 0.446 / 0.405 |
| 7-8. march span (primary steps / px) | 34.0 | 34.7 / 33.0 | 16.9 | 15.9 / 15.8 |
| 9-10. sample count (primary steps) | 4.40 M | 4.49 / 4.28 M | 2.19 M | 2.06 / 2.05 M |
| 11. material / empty run (steps) | 14.0 / 65.1 | 14.8 / 57.6 | 12.7 / 53.1 | 14.0 / 54.2 |
| 12. refinement (refineEvents) | 233 k | 294 / 197 k | 90 k | 137 / 74 k |
| 13. probe rate (field probe fetches / px) | 6.11 | 7.46 / 6.01 | 3.21 | 4.72 / 2.97 |
| 14. lobes visited / descriptor evals | 24.9 / 21.3 M | 28.1 / 23.0 M | 12.5 / 13.7 M | 13.4 / 13.1 M |

- **The workload moves ±15-30% run to run with the storm's state**; light evaluations move most,
  12-23 per pixel at SIDE. It does not follow the build.
- **The deciding pair is HEAD A1 against T224.** A1 did no more work than T224 (coverage 0.418 vs
  0.420, 13.6 vs 16.7 light evaluations per pixel) and still ran 8.34 ms raw against 6.30.

## TIMING ATTRIBUTION

15. **rawCloud "light":** 6.30 ms SIDE / 4.81 PVN (T224 build); 6.43 / 5.33 (T230 build).
16. **rawCloud "heavy":** 9.03 / 7.04 (HEAD run b); ABAB HEAD 8.34 and 8.95 / 6.29 and 6.66.
17. **ms/sample "light":** 1.43 ns per primary step at SIDE (T224), 1.45 (T230).
18. **ms/sample "heavy":** 2.01 (HEAD b), 1.95 (A1); PVN 3.42 / 3.07 against 2.19. **This is the
    whole effect: +37% per sample at a flat sample count.**
19-23. **Stage split.** The per-sample rise tracks one code path, the T231 form, not a march stage.
    Build-vs-build ladder, one variant per client session, SIDE / PVN totals:

    | variant (lean FINAL) | SIDE | PVN | raw SIDE |
    |---|---|---|---|
    | T230 source | 9.21 | 8.66 | 6.43 |
    | HEAD (relief + shade, live uniforms) | 12.16 | 10.70 | 9.03 |
    | + relief/shade baked as lean constants | 11.46 | 10.20 | 8.35 |
    | baked, shade = 0 (relief only) | 9.02 | 8.75 | 6.22 |
    | baked, shade with a CONSTANT form (arm D) | 9.28 | 8.86 | 6.48 |
    | form exported as one product (global) | 11.78 | 9.94 | 8.68 |
    | form packed into localStorm (crosses the light march for free) | 11.53 | 10.73 | 8.40 |
    | form written only by the primary density call | 12.02 | 10.06 | 8.92 |
    | form carried through the density tail inside the descriptor height | 11.64 | 10.69 | 8.54 |
    | form re-fetched in the lighting (arm J, no data from cloudDensity; weight 1) | 8.46 | 7.98 | 5.69 |
    | form returned through an `out` parameter | 11.56 | 10.78 | 8.43 |
    | **form returned, then packed into the primary density's low bits** | **8.88** | **8.28** | **6.10** |

    - **19. Primary (`cloudDensity` structure):** 0 - the relief geometry alone is free (9.02).
    - **20. Refinement:** 0 - refine events follow the storm, not the build.
    - **21. Lighting arithmetic:** 0 - the complete shading chain with a constant form costs
      nothing (arm D).
    - **22. Traversal:** 0 - steps and lobe visits flat.
    - **23. Other:**
      - 0.7 ms: the unfoldable live-uniform branches (baked constants);
      - ~2.4 ms: **transporting a per-sample value out of the primary density call**. Every
        non-fused route costs the same; a lighting-side re-fetch and the density-bit packing
        cost nothing.
24. **Dominant scaler: T231's form transport (~2.4 ms) plus its live-uniform branches (0.7 ms).**
    - **The mechanism, from the driver's compiled program.** NVIDIA's `!!NVfp5.0` intermediate
      was dumped offline with `isa.py` / `nvstat.py`.
    - **What it rules out.** The fast (shade 0) and slow (form live) programs are identical in
      that intermediate: 121 temporaries, 73 `CAL`s, 28 texture ops, 6,728 vs 6,736
      instructions. So the difference is made in the driver back end's register allocation.
    - **What that implies.** `cloudDensity` is a real subroutine (`CAL`). Any non-rematerialisable
      value held across such a call raises the allocation of the program's hottest region. The
      form was the one such value, and the density already is one.

## T231 CONTRIBUTION

25. **Occupied-pixel delta:** none attributable.
    - Coverage 0.418-0.480 across HEAD runs versus 0.420 (T224) - storm-state noise.
    - The relief moves the surface ±20 blocks and changes projected area -0.6..+0.3% (T230).
26. **Span delta:** none (33.0-34.7 steps/px in every build).
27. **Sample-count delta:** none (4.28-4.49 M SIDE steps in every build).
28. **Timing delta: +2.9 ms SIDE / +2.0 ms PVN** (HEAD vs T230 source, same session). All of it
    is code cost, none of it volume.
    - **Correction to T231:** its "11.96 vs 11.86 ms, T231 costs nothing" compared two
      live-uniform settings inside ONE build. The code was present in both arms. **A same-build
      A/B cannot see code-presence cost; only a build-vs-build A/B can.**

## CANDIDATE

29. **Exact optimization:**
    - **Constants.** `PaLowerDeckRelief = 1` and `PaReliefShade = 0.12` are baked into the lean
      FINAL program (`leanFinalConstants`). `leanFinalEligible` now routes any other value to
      the monolith, where they stay live, so arms still work and FINAL never ignores a setting.
    - **Form transport.** `cloudDensity`'s body becomes `cloudDensityImpl(..., out reliefForm)`.
      The old signature is a wrapper, and only the primary sample calls the impl.
    - **Packing.** The primary march folds the form into the density at once:
      `density = paPackDensityForm(bodyDensity + rainDensity, form)`, 8 low mantissa bits,
      `round(form * 50.8) + 128`. It flags the packed density with `localStorm + 4`.
    - **Unpacking.** `evaluateLightingComponents` unpacks it only for that caller (every other
      caller shades with form 0).
    - **Portability.** It requires `GL_ARB_shader_bit_encoding` behind a preprocessor guard; a
      driver without it keeps the plain path (identical image, old cost).
    - **Instrument.** Mode 14 (decode check) added; diagnostic clamp 0..14.
30. **Reason:**
    - the form was the only value carried out of the primary density call;
    - the density is carried across every intervening call regardless;
    - the error is a relative density change below 2^-15 and a form step of 1/101.6 - 0.12% of
      ambient at the production gain.
31. **Quality result** (one storm, adjacent arms per pose, `t232-quality-captures.log`,
    `T232_R_P_decode_NEAREDGE_LOWSIDE.png`):
    - **Decode check (mode 14).** Median error on cloud pixels is 3-10 / 255 in the ×25 view,
      below 0.002 form units. p99.9 is ~95 / 255, which is 0.015 form, 0.2% ambient, and sits
      on sky-blended silhouette pixels.
    - **The T231 effect is reproduced** in the lower-anvil form region, shade off -> production
      (T231's own R -> RS in brackets):

      | view | flat fraction | local contrast |
      |---|---|---|
      | NEAREDGE | 0.96 -> 0.84 (0.98 -> 0.86) | 0.56 -> 1.03 |
      | LOWSIDE | 0.80 -> 0.36 (0.82 -> 0.51) | 1.95 -> 2.84 |
      | SIDE | 0.87 -> 0.41 (0.89 -> 0.54) | 1.18 -> 2.36 |
      | LATERAL | 0.73 -> 0.20 (0.70 -> 0.26) | 1.29 -> 2.51 |

    - **Everywhere else, unchanged:**
      - the rest of the cloud moves ≤ 0.6 levels;
      - ABOVE is unchanged;
      - lean against monolith at production values differs ≤ 0.3 levels.
    - **THROUGH:** flat 0.95 -> 0.94, as in T231 (its view holds less than one macro cell).
32. **Motion result** (T152 route, 2200 frames per arm, logs `t232-motion-{s,u,v}.log`):
    - **The shading on one program:** shade on vs off, both on the monolith: `dLuma2` **+1.1%**,
      flicker **1.000**, per-frame 6 worse / 4 better.
      - HEAD's shader on the same route: +1.9%, 1.000, 17 / 9 (T231 itself: +1.8%).
    - **Repeat floor:** production against production, ±5%, 48 / 35.
    - **A pre-existing gap between the programs.** Lean against monolith differs under motion
      (flicker 0.875 / OUTSIDE 0.45 here; 0.891 / 0.56 on HEAD's shader). It is equal in both
      builds, so it predates T232 and is not caused by it.

## PERFORMANCE (paired ABAB: HEAD, candidate, HEAD, candidate; one session; T208 totals)

| | A1 HEAD | B1 cand | A2 HEAD | B2 cand | control mean | candidate mean | delta | ratio |
|---|---|---|---|---|---|---|---|---|
| 33-34. SIDE total | 11.46 | 9.11 | 12.07 | 9.54 | 11.76 | **9.33** | **-2.44** | 0.793 |
| 35-36. PVN total | 10.02 | 8.58 | 10.39 | 8.28 | 10.20 | **8.43** | **-1.78** | 0.826 |
| SIDE raw p50 | 8.34 | 6.35 | 8.95 | 6.76 | 8.64 | 6.55 | -2.09 | 0.758 |
| PVN raw p50 | 6.29 | 5.20 | 6.66 | 5.05 | 6.48 | 5.13 | -1.35 | 0.792 |

37. **p95:**
    - raw phase at SIDE: 8.87 / 9.43 (HEAD) -> 6.70 / 7.19 (candidate);
    - PVN: 6.68 / 7.29 -> 5.65 / 5.56;
    - total min/max spreads are ≤ 0.12 ms in every run.
    - **GPU clock:** median 2340 MHz under load across all 768 loaded samples of the session
      (minimum 2115).
38. **Recovered:** 2.44 ms SIDE, 1.78 ms PVN (the ≥ ~2 ms goal met at SIDE).
39. **≤ 10 ms: YES** - SIDE 9.11-9.54, PVN 8.28-8.58; SIDE ≤ 9.5 in one of the two pairs.

## DECISION

40. **KEEP (CASE A).**
    - **The dominant scaler:** T231's form transport plus its live uniforms.
    - **The fix:** quality-safe, recovering 2.4 ms.
41. **Mechanism conclusion:**
    - **The heavy-storm budget failure was a code regression that looked like weather.** T231
      made one per-sample value flow from the primary density subroutine to the lighting, and
      the driver charged ~2.4 ms for it however it was carried. Its live uniforms added 0.7 ms.
    - **Storm workload is flat, and no march stage grew.**
    - **Two harness rules follow:**
      - cost must be measured build-vs-build, never by toggling uniforms in one build;
      - "carry one more value" is not free in this program - fuse it into a value that is
        already live, or recompute it after the calls.
42. **Production change:**
    - the lean constants;
    - `cloudDensityImpl` plus the density-bit packing;
    - the `leanFinalEligible` guard (relief / shade must be production for FINAL);
    - mode 14;
    - the sandbox pins the packed transport and the baked constants.
    - **Rain.** Rain-pass lighting samples, which never carried the form reliably, now shade
      with form 0.
43. **Exact next task: T233, the THROUGH-scale underside octave, as planned by T231.** Its
    cost is to be judged build-vs-build from the start, against the new ~9.3 ms SIDE baseline.

## VALIDATION

44. **campaignGate:** PASS (`gate-t232.log`). 45. **check build:** PASS (`check-build-t232.log`).
46. **stormMorphologySandbox:** PASS (10 / 0). 47. **Markers:** `t208-temporal-cleanup.txt`
    retired; the temporary T217M/T211 markers removed. The T152 and T098 arms used for the
    motion and quality runs are removed; mode 14 kept.
48. **git status:** `PAchangelog.md` modified and uncommitted; everything else committed.
49. **Commit:** see the branch log (not merged).

## Evidence (`t232-campaign/`)

| file | what |
|---|---|
| `t232-timing.log` | T208 / T135 / T136 lines for every build variant and the ABAB (run names in headers) |
| `t232-motion-s.log`, `-u.log`, `-v.log` | routes: on / off / on repeat; on / off / on-monolith; the same on HEAD's shader |
| `t232-quality-captures.log`, `T232_R_P_decode_NEAREDGE_LOWSIDE.png` | the adjacent-arm capture set; shade off / production / decode check |
| `gpu-t232.csv` | nvidia-smi clocks and power across the whole session |
| `isa.py`, `nvstat.py` | offline compile of a lean shader on the driver and its NV assembly stats |
| `t232q.py`, `t232_route.py` | quality and motion analysis |
