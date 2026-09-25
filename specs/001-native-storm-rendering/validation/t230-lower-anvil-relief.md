# T230 - Lower-Anvil Macro Relief

**The question:** can the anvil underside be given real large-scale geometric relief, so the
lighting finally has something to show?

**The answer: the geometry can be given relief - and it does not show. REJECTED (CASE B, shape not
banked), and the reason is the most useful finding of the T227-T230 line.**

**Attribution.** T213's accepted macro displacement is weighted by `deckWeight` on each lobe's
*own* normalized height (0.55-0.93). That covers the top of the anvil lobes and is **exactly zero
at their bottom**, which is the underside. So the underside never had macro relief.

**Candidate.** The mirror window: the same T213 macro signal, the same 38 blocks per SD, weight
`1 - smoothstep(0.12, 0.30, h)`, anvil role active and base role not. It does what was asked
geometrically:
- relief of **-26 to +16 blocks** (5th-95th percentile) at the ~400-block macro scale;
- the displaced surface's local spread at THROUGH **1.74 -> 2.87 blocks**, all in the broad band;
- no collateral (silhouette IoU 0.985-1.000, lower-mass veins unchanged, ABOVE identical).

**But the underside's FINAL image barely changes** (THROUGH flat fraction 1.00 -> 0.98, local
contrast 0.22 -> 0.32). **Nothing in this lighting model responds to the shape of a shadowed
surface:**
- the sun's od integrates the unchanged anvil above it (underside od local spread 1.35 -> 1.49
  with relief, so T228's curve would have no new signal either);
- the ambient depends on height alone (±20 blocks barely moves it);
- depth-below-surface stays constant, because the visible sample always sits at the surface
  wherever that surface is;
- there is no normal, orientation or sky-occlusion term at all.

T228, T229 and T230 each supplied a new input: a depth curve, a surface-depth signal, and
geometry. The missing piece is a **coupling**: underside shading that follows its own relief.

## RECOVERY

1. **Starting HEAD:** `00a20b4f` (T229). **2. Branch:** `quality/t217-deep-interior-lighting` (not
   merged; T226 separate). `PAchangelog.md` preserved. campaignGate PASS before the client run.
3. **Production state:** unchanged since T225; `PRODUCTION_MACRO_ENVELOPE_MODE = 3` (T213 block-space
   deck displacement, 38 blocks per SD); instruments modes 9 (log-od) and 10 (surface depth).
4. **The lower surface's controlling stage:**
   - **The shape** is the descriptor union: anvil lobes smooth-unioned, with the vertical profile
     and clamp of `stormRoleProfile` and the group blend radius.
   - **Stage 5 (macro envelope)** then displaces the envelope surface in block space by
     `deckWeight * 38 * clamp(macroSigned, ±2.5)`, only where `0.12 < coverage < 0.98`.
   - **`deckWeight`** is `smoothstep(0.55, 0.72, h) * (1 - smoothstep(0.93, 1.0, h))`, with
     `h = directStormHeight01`: the **per-lobe** height `(p.y - lobeBase) / (lobeTop - lobeBase)`,
     averaged over nearby lobes.
   - **So:** at an anvil lobe's base, h ~ 0 and the weight is 0. The underside is the one anvil
     surface T213 never displaced; base noise, erosion and threshold then act on an undisplaced
     smooth surface.
   - **Instrument note:** T229's mode 10 reads the union *before* stage 5, so it could not see
     relief. T230 adds **mode 11** (displaced surface depth, `-(d + shift)`) and **mode 12** (the
     lower-deck weight).

## BASELINE GEOMETRY (displaced surface depth, mode 11, in the lower-deck region W > 0.2)

5. **THROUGH:** SD 23.0 blocks, local spread **1.74**. 6. **NEAREDGE:** SD 44.5, local 9.29.
7. **LOWSIDE:** SD 41.7, local 20.7. 8. **Sides** (region = the flank part of the lower deck): SIDE
   23.0, LATERAL 29.3. 9. **Dominant scale:** the displacement field is the macro octave, period
   ~400 blocks. The depth spectrum is broad-band dominated (fine / mid / broad variance share
   THROUGH 0.00 / 0.00 / 0.22, LATERAL 0.04 / 0.26 / 0.64). 10. **Flat-surface fraction** (FINAL,
   local contrast < 1.5, same region): THROUGH **1.00**, NEAREDGE 0.98, LOWSIDE 0.81, SIDE 0.88,
   LATERAL 0.60.

## CANDIDATE

11. **Macro signal:** T213's own, unchanged. `macroSigned = (texture(BaseNoise,
    stormMacroEnvelopeDomain(p)).g - 0.4794) / 0.1163`, clamped to ±2.5; the displacement is
    `(deckWeight + lowerWeight) * 38 * macroSigned` blocks, through the same inverse-smoothstep
    re-evaluation. It was applied at both stage-5 sites (primary density and the storm-final density
    that feeds the light field), as T213 does.
12. **Lower-surface weighting:** `stormLowerDeckWeight = anvilActive && !baseActive ? 1 -
    smoothstep(0.12, 0.30, h) : 0` (role mask bits: base 1, anvil 8). The two windows never
    overlap (0.30 < 0.55). Verified with mode 12: weight 1 across the whole anvil underside,
    0 on the lower mass and tower, and 0 in ABOVE.
13. **Amplitude:** 38 blocks per SD, T213's accepted value (no sweep, no G2).
14. **Zero-bias result:** the macro field is zero-mean, but the sampled underside region is small
    against its ~400-block period. The added shift's mean in the measured region was -4.4 blocks
    (THROUGH), +5.8 (NEAREDGE) and +4.4 to +9.8 (flanks); area change -0.6% to +0.3%.

## GEOMETRY RESULT

15. **THROUGH:** SD 23.0 -> 26.4 blocks, local spread **1.74 -> 2.87 (+65%)**.
16. **NEAREDGE:** SD 44.5 -> 47.3, local 9.29 -> 9.52. 17. **LOWSIDE:** 41.7 -> 44.1, local
    20.7 -> 21.0.
18. **Protrusion / recession** (added shift, 5th / 95th percentile): THROUGH -25.9 / +16.5 blocks,
    NEAREDGE -11.8 / +32.2, LOWSIDE -10.2 / +25.9, LATERAL -11.1 / +43.1. Broad-band only; the
    fine share stays ~0.
19. **Flat-surface delta (FINAL):** THROUGH 1.00 -> 0.98, NEAREDGE 0.98 -> 0.98, LOWSIDE
    0.81 -> 0.80, SIDE 0.88 -> 0.86, LATERAL 0.60 -> 0.65.
20. **Visual:** in world space the displaced depth shows broad mottled lobes where production is
    smooth (`T230_THROUGH_displaceddepth_P0_vs_relief.png`). In FINAL only the underside's lower
    edge becomes a little more lobed, and the interior still reads flat
    (`T230_P0_relief_displaceddepth.png`). No spikes, stripes, fur or fragments.

## COLLATERAL

21. **Upper deck:** untouched by construction (windows disjoint); ABOVE frame identical.
22. **Tower:** no measurable change (neck region inside the frames above; IoU >= 0.985).
23. **Lower mass:** vein score LOWSIDE 0.265 -> 0.265, SIDE 0.405 -> 0.410, LATERAL
    0.466 -> 0.466; local contrast within 0.15.
24. **Projected area:** -0.58% (THROUGH) to +0.26% (NEAREDGE), <= 0.1% elsewhere.
25. **ABOVE silhouette:** IoU 1.0000.

## T228 RE-TEST (Task 10) - not rendered; predicted by measurement

26. **The exact T228 curve:** `ambient *= 1 + smoothstep(3,6,od)*0.7*(g(18) - g(od))`, `g =
    log(1+od)/log(41)`.
27-29. **Not run**, because the brief gates it on the geometry passing and the geometry is not
    visibly better. More decisively, the relief does not change the underside's **sun od** (the
    curve's only input):

    | view | od local spread, production | with relief | od median |
    |---|---|---|---|
    | THROUGH | 1.35 | 1.49 | 20.5 -> 18.5 |
    | NEAREDGE | 2.55 | 2.56 | 15.4 -> 15.2 |
    | LOWSIDE | 6.11 | 6.07 | 15.7 -> 15.4 |

    So relief + T228 can only reproduce T228's own underside result (NEAREDGE anvil lc +0.57).
30-32. **Deep retention, flat-shadow fraction, GOOD/BAD:** as in T228 alone; no transition is
    predicted for NEAREDGE or THROUGH.

## SUN AXIS / TEMPORAL / PERFORMANCE

33-34. **Sun axis:** low-sun P0 / R pairs were captured (`t230-run1-relief.log`). They were not
    analysed further, since the noon geometry already fails its image test.
35-38. **Temporal:** not run (no static pass).
39-42. **Performance:** not measured. The term reuses the macro fetch the deck already made and
    adds one smoothstep per band sample. The instruments cost nothing in FINAL, because the lean
    program bakes `PaDiagnosticLightingMode` to 0 and the captured values are never read.
    <= 10 ms unchanged (T224: 9.35 / 8.82 ms).

## DECISION

43. **REJECT (CASE B, shape not banked).** The geometry improves in world space but is not
    visually better by itself, and T228 cannot reveal it.
44. **Geometry mechanism result:** confirmed and fixed at source - the underside was undisplaced
    because T213's weight uses per-lobe height. Correct relief is available in one line (items
    11-12). **But in this renderer relief without a shape-coupled light term is invisible:**
    - shadowed luminance = ambient(height) x retention + the sun term through od;
    - the sun od crosses the same anvil above wherever the underside surface sits;
    - the ambient sees only ±20 blocks of height;
    - no term reads orientation, relief or sky occlusion.
45. **Production change:** none. Kept as instruments: mode 11 (displaced surface depth at the
    shaded sample) and mode 12 (lower-deck weight), with `stormLowerDeckWeight` and the
    shift / weight captures. They are dead code in FINAL. The relief arm is removed.
46. **Exact next task: T231, relief-coupled underside shading.**
    - Shade the underside by its own relief, using the same world-stable, zero-mean macro field
      that shapes it (T213's basis, not density noise, so the T221-T223 closure does not apply):
      in the shadowed regime (od onset, as T228) and inside the lower-deck window (mode 12), let
      the ambient follow the local relief height. Sagging lobes lighter (open to the ground and
      horizon), recessed hollows darker - a mammatus cue.
    - Pair it with the T230 relief (item 12) so geometry and shading agree.
    - Arithmetic only (the shift is already computed).
    - Judge it on the T227-T230 matrix: the undersides must move, the lower-mass veins stay
      unchanged, no mean lift, and motion no worse than T228's +4-8%.

## VALIDATION

47. **campaignGate:** PASS (`gate-t230.log`). 48. **check build:** PASS (`check-build-t230.log`).
49. **stormMorphologySandbox:** PASS (10 / 0). 50. **Markers:** `t211-captures.txt` retired.
51. **git status:** `PAchangelog.md` modified and uncommitted; everything else committed.
52. **Commit:** see the branch log (not merged).

## Evidence (`t230-campaign/`)

| file | what |
|---|---|
| `t230-run1-relief.log` | the run: six poses x P0 / relief (both with ALPHA), repeat, displaced depth x2, weight, log-od x2, low sun |
| `T230_P0_relief_displaceddepth.png`, `T230_THROUGH_displaceddepth_P0_vs_relief.png` | FINAL and displaced depth, production vs relief |
| `t230_geo.json`, `t230_geo.py` | geometry, image and collateral metrics |
