# T227 - Why Production Clouds Have No Stable Middle Ground

**The question:** why do some storms and frames look convincing while others look obviously bad,
and which small set of renderer variables separates them?

**The answer: the lighting transfer's depth response (C), expressed through the interaction of storm
scale with the view and sun direction (D/E).** Luminance responds to light-path optical depth only
over a narrow band, od ~1 to 4. Past od 4 the ambient term is a constant
(`mix(retention, 1, exp(-od))` with retention 0.58-0.74) and the three scatter octaves are ~0. So in
the shadow regime the final image keeps only **6-15% of the density field's local structure**,
against 57-96% inside the band.

A storm is hundreds of blocks across and light crosses od 4 within tens of blocks. So **60-80% of
the visible cloud in every noon exterior view sits in that flat plateau, and only 1-12% in the
band**. A view looks GOOD when it happens to show little plateau: small on screen, lit from the
camera's side, or looking at the sun-facing top. It looks BAD when a large area of shadow-regime
cloud fills the frame: the anvil's flank or underside up close. Nothing in between exists, because
the band itself is so narrow.

**Not the cause:** density structure, thresholding and erosion. The shadowed cloud carries as much
density contrast as the lit cloud (D-view local contrast 18-32 in both), and its density, erosion
and envelope statistics match the lit regions within a few percent. The storm descriptors barely
vary between realizations (the autorun always spawns the same archetype), so they cannot carry a
per-view split.

**Reproduced across three storm realizations** (23 labelled cases), and **shown causally** by a
controlled intervention inside the data. Same storm, same camera, same density, only the sun moved:
the form-band fraction rose 0.09 -> 0.22 and the view went from mixed to GOOD.

**Next change (evidence-backed, not built):** a two-scale shadow transfer. Keep `exp(-od)` for the
lit skin, and add a depth response for the body that spans od ~4 to ~40 (logarithmic in od, or od
normalized by the local column thickness), so shadowed cloud keeps rendering its density structure
at every depth. A single-scale softening was pre-checked and is not enough (item 12).

## Method

1. **Matrix.** Three storm realizations, one client launch each (`c67a5df4`, `713224df`,
   `5245a518`). Eight fixed poses per storm: SIDE, LATERAL, FAR, PVN, LOWSIDE, ABOVE, NEAREDGE,
   THROUGH. Each captured in FINAL (with the temporal pair, for the ALPHA view) and four diagnostic
   views at identical pixels: **L** light-path optical depth (mode 2, `exp(-0.5 od)`), **D** local
   density (mode 3), **E** pre-erosion density (mode 4), **X** macro envelope (mode 6).
   SIDE / LOWSIDE / FAR were repeated at **dayTime 2000 and 4000**. **Every capture before T227 ran
   at frozen noon**, so sun elevation had never been varied. Every render cell's state was dumped
   (`T227_CELL`: role, footprint, heights, density, edge softness, development, darkness, anvil
   strength, precipitation, lifecycle, seed).
2. **Validity.** Storm 1's fixture dissolved at the sixth pose (ABOVE onward excluded). The
   matrix was then moved to the front of the capture list and storms 2-3 completed. The FINAL
   frame of the FAR pose was a stale copy of LATERAL's in storms 2-3 (mean diff 0.005 against a
   normal 7-10), a capture artifact on that move; it was excluded, and FAR's sun variants and
   diagnostic views were kept. PVN sat inside tree canopy in all storms (a camera-placement
   problem, not the renderer) and was excluded.
3. **Labels.** Each valid image was judged by eye on the brief's properties: silhouette, tower /
   anvil proportion, lower-body coherence, lighting smoothness, cell/vein artifacts, shadow
   plateaus, interior readability, edges and depth. Scale: GOOD 2, MIXED 1, BAD 0 (half steps
   allowed).
4. **Measurements.** Scripts: `t227_matrix.py` (per-region image and state metrics, dome and lower
   mass never pooled) and `t227_transfer.py` (per-regime transfer gain). Cloud pixels are split by
   the L view into **lit** (od < 1), **form band** (1-4) and **shadow** (> 4). Per regime:
   luminance local contrast vs density local contrast at the same pixels; the ratio is the
   **transfer gain**.

## Results

5. **Visual verdicts reproduce across storms.**

   | pose | storm 1 | storm 2 | storm 3 | what it shows |
   |---|---|---|---|---|
   | FAR / LATERAL | GOOD | GOOD | GOOD | small on screen; lit crown and silhouette carry it |
   | SIDE | BAD-mixed (flat dome) | mixed-GOOD | GOOD | the dome's form band decides (item 8) |
   | SIDE, low sun | - | GOOD | GOOD | more of the dome enters the band |
   | ABOVE | (lost) | mixed-GOOD | mixed-GOOD | lit top, readable; spotty |
   | LOWSIDE (all suns) | BAD | BAD | BAD | anvil reads as a flat blue-grey ball over a bright, veiny lower blob |
   | NEAREDGE | (lost) | BAD | BAD | a huge featureless blue-grey underside |
   | THROUGH | (lost) | BAD | BAD | flat dark underside; tower only patchily lit |

   See `T227_storm{1,2,3}_grid.png`.
6. **The transfer erases structure in the shadow regime, identically in every storm and pose.**

   | regime | share of visible cloud (noon exterior) | density local contrast | **transfer gain** |
   |---|---|---|---|
   | lit (od < 1) | 14-30% | 22-29 | 0.20-0.40 |
   | form band (1-4) | **1-12%** | - | **0.57-0.96** |
   | shadow (od > 4) | **60-80%** | 18-32 (as much as lit) | **0.06-0.15** |

   The density field has structure everywhere; the final image keeps it only in the lit skin and
   the thin band. The od distribution is bimodal: the dome and anvil at od ~8 (the L view's 8-bit
   ceiling is 8.31), the lower mass at od ~0.6. In every storm the dome is darker and flatter than
   the lower mass (luma ~110-115 vs 120-137): at noon the storm renders top-dark and bottom-bright.
7. **Correlation with the label** (Spearman, 23 cases, three storms):

   | variable | rho |
   |---|---|
   | **form-band fraction** | **+0.79** |
   | shadow area (shadow fraction x screen size) | -0.72 |
   | shadow fraction | -0.64 |
   | screen size | -0.64 |
   | plateau fraction (an outcome, for reference) | -0.57 |
   | shadow-regime gain | +0.46 |
   | lit-regime density contrast | **+0.19** |

   The best predictor is renderer state (where the visible cloud falls on the od axis), not the
   density field.
8. **Causal check: the sun axis.** Same storm, same camera, same density, only the light moved:
   - storm 2 SIDE, noon -> dayTime 2000: form band 0.09 -> 0.22, shadow 0.76 -> 0.47, local
     contrast 3.8 -> 5.0, plateau 0.45 -> 0.35, label 1.5 -> 2;
   - storm 3 SIDE: form band 0.12 -> 0.15, shadow 0.70 -> 0.45;
   - LOWSIDE, whose sun stays behind the storm: form band 0.02 -> 0.08, still BAD.

   Quality follows the band where the band moves, and stays put where it does not.
9. **Why storm 1's SIDE is worse than storms 2-3's at the same pose:** the smallest form band of
   the three (0.07 vs 0.09 / 0.12), the most shadow (0.77 vs 0.76 / 0.70) and the least contrast
   (3.37 vs 3.81 / 4.15). The descriptors are near-identical (anvil y 771-1002, radius ~490;
   base radius ~510-540; tower/core the same), so the difference is the noise realization.
   It decides how much of the dome surface happens to sit in the narrow band. Same mechanism,
   smaller lever.

## The five candidate causes

10. **A. Storm morphology / descriptors:** secondary. They set the scale (a ~210-block-thick
    anvil) that puts most of the storm past od 4. But they barely vary between realizations here,
    and the density structure they produce is present everywhere.
    **B. Density thresholding:** no. Shadowed cloud has the same density contrast as lit cloud
    (item 6), and density/erosion statistics do not separate GOOD from BAD.
    **C. Lighting transfer: dominant.** Gain ~0.1 past od 4 against ~0.6-0.9 inside the band.
    **D. Morphology x lighting: the mechanism.** Storm scale against the transfer's od scale
    produces the bimodal, 60-80%-plateau distribution.
    **E. Camera / view:** decides how much plateau fills the frame (screen size rho -0.64, shadow
    area -0.72) and, with the sun, where the band lands.
11. **Direct / ambient balance in the shadow regime:** by construction direct is ~0 (three octaves
    at extinction 1 / 0.42 / 0.18 give ~0.04 at od 8, with slope near zero). The ambient is
    `mix(retention, 1, exp(-od))`, constant past od 4. The T217 deep-scatter fill is gated on
    camera density, so it is off for every exterior view. Shadow-regime luminance is therefore
    one ambient level, modulated only by height and material darkness.

## Pre-check of the proposed direction (diagnostic arm, removed)

12. `PaShadowDepthScale` k: ambient depth response `exp(-od * k)` instead of `exp(-od)`, storm 4
    (`57036274`), same camera, k = 0.25 / 0.12.

    | pose | plateau P0 -> K12 | shadow gain P0 -> K12 | mean luma shift |
    |---|---|---|---|
    | SIDE | 0.48 -> 0.28 | 0.12 -> 0.15 | +6.6 |
    | LATERAL | 0.24 -> 0.06 | 0.11 -> 0.15 | +6.4 |
    | LOWSIDE | 0.43 -> 0.26 | 0.11 -> 0.14 | +5.4 |
    | NEAREDGE | 0.72 -> 0.67 | 0.06 -> 0.09 | +4.5 |
    | THROUGH | 0.74 -> 0.73 | 0.09 -> 0.12 | +2.8 |
    | ABOVE (all lit) | 0.25 -> 0.33 (worse) | - | +3.1 |

    The direction is right where the shadow is moderate. But a single exponential scale cannot
    reach the deep anvil undersides (their od is far past 8.31; `exp(-0.12 od)` is ~0 there too),
    it flattens the lit skin's own gradient (ABOVE), and it brightens the mean. Visually the
    change is subtle (`T227_precheck_P0_K25_K12.png`). **Hence the two-scale recommendation, not
    a single k.**

## Decision

13. **Dominant variable:** the fraction of visible cloud that falls inside the transfer's narrow
    responsive od band (1-4). **Its renderer cause:** the shadow-regime transfer (the constant
    ambient past od 4 and the collapsing octaves), acting on a storm hundreds of blocks deep.
14. **Next change (T228):** a two-scale shadow transfer. Keep `exp(-od)` for the lit skin (the
    ABOVE regression says the skin must not change). Add a bounded depth term for the body over
    od ~4 to ~40, logarithmic in od or od normalized by the column's thickness, so the shadowed
    luminance keeps a gain of the order of the form band's. Mean-preserving against production at
    the storm's median depth, so the storm does not simply brighten.
    **Acceptance on this T227 matrix, all storms:** shadow-regime gain up from ~0.1; plateau
    down in LOWSIDE / NEAREDGE / THROUGH; GOOD views not worse (ABOVE's lit contrast held). Two
    instrument prerequisites:
    - an L view with a wider od range (log-encoded; the 8-bit view saturates at 8.31, which hid
      the deep-underside od);
    - the sun axis kept in every future evaluation (every task before T227 judged at frozen noon
      only).
15. **Production change in T227:** none. The matrix capture block, the pre-check arm and both
    markers were removed; the working tree is production HEAD. T226 (per-storm precipitation) is
    parked, verified but not final, on `quality/t226-local-precipitation` (`0b861cb7`), so this
    matrix measured production.

## Limits

16. - One storm archetype: the autorun always spawns the same storm type. Realizations differ in
      seed and noise, not in development stage or size.
    - The labels are one reviewer's judgement, on a 0-2 scale.
    - The L view saturates at od 8.31.
    - The pre-check ran on one storm.

    None of these touch the central measurement: the per-regime transfer gain was identical in
    all four storms.

## Validation

17. No production code changed (the working tree equals HEAD `16a4a71e` plus the preserved
    `PAchangelog.md`), so the gates were not re-run for a documentation commit. Markers
    `t211-captures.txt` and `t212-edge-trace.txt` retired.

## Evidence (`t227-campaign/`)

| file | what |
|---|---|
| `t227-storm{1,2,3}.log` | per-storm cell dumps, geometry traces, capture controls, fixture loss |
| `T227_storm{1,2,3}_grid.png`, `T227_storm2_SIDE_noon_4000_2000.png` | the FINAL images the labels were made from |
| `t227_s1.json`, `t227_s2.json`, `t227_transfer.json` | per-pose and per-regime metrics |
| `t227-precheck.log`, `T227_precheck_P0_K25_K12.png`, `t227_precheck.py` | the ambient-depth pre-check |
| `t227_matrix.py`, `t227_transfer.py`, `t221_*.py` | analysis |
