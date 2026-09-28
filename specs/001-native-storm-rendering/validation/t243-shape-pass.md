# T243 - Per-family cloud shape pass (accepted)

Branch `quality/t217-deep-interior-lighting`, from HEAD `92d8e219` (T242). Shape only, one family at a
time, every change an experiment candidate A/B'd in the type gallery before it shipped. The user
accepted the gallery at candidate 60 (`t243-campaign/t243_final3.png`: every family SIDE / UNDER /
ABOVE, neutral and production lighting); this commit makes candidate 60 permanent and removes the
experiment plumbing (`PaShapeCandidate` uniform, `PA_SHAPE_EXPERIMENT` define, splat uniforms, the
weather-map cache signature term, the gallery spawn seed).

## 1. What ships, by family

| family | change | where |
|---|---|---|
| cumulonimbus / supercell | mass by anatomy after erosion: base / trunk / transition floor 0.9 from 0.35..1.2 edge bands inside the union surface; the thin anvil floor 0.75 from 0.25..1 band (solid sheet, wispy outer edge, no holes). Geometry unchanged. | volume, storm erosion stage |
| cumulus (puffs) | broad rounded turrets on every lobe's upper part (3 + 5 around, square-rooted, 0.40 / 0.25 of the lobe radius); flat base untouched | volume, `directPuffLobeSample` |
| cumulus mediocris | both middle lobes rise from one shoulder, flanking it (one connected asymmetric upper mass, no "ears") | `CloudMorphologyGenerators` |
| cumulus congestus | wider middle (0.64-0.70 R) and upper (0.60-0.66 R) tiers; still 8 lobes (puff tile cap) | `CloudMorphologyGenerators` |
| stratus | member tops flat in the splat (per-member rim tapers averaged into fish-scale plates); the volume tapers the deck top by TOTAL coverage, the top undulates +-0.2 with the slow noise, and the carve works against coverage only (no terraced plates) | splat + volume carve |
| stratocumulus | clumps 0.55-1.0 of the plan radius, packed at ~0.78 R (was 0.94): a semi-connected field with natural gaps | `CloudMorphologyGenerators` |
| nimbostratus | flat rain-layer top (0.86-0.94), top tapered over the outer rim only (no stair wall), base lowered 0.25 of the span with a strong carve in the lower third (ragged hanging base); deck built from 14-18 members at 0.52 base radius instead of 7-11 at 0.74 (one broad layer from above, no curled / notched outline); decks pack at 0.65 R with a third of the jitter | splat, volume, generator |
| cirrus | fibrous strands ~18 blocks apart across the wind with body between the fibres; larger, longer plan (radius 0.45, 8-12 strands); top offset 14; density scale x3 (0.78 -> 2.34) so the streaks read straight overhead | volume, generator, `CloudShapeProfile` |

## 2. Rejected (tested, not shipped)

* **Thin-ribbon cirrus** (density only in a wandering 0.18-of-slab ribbon with fall streaks, plus a
  stretched-noise streak split): from a steep below view cirrus vanished even at 3x density - the
  ~4-block ribbon is thinner than the march step at altitude. The old grazing UNDER pose had hidden
  this; the gallery's UNDER pose is now steep for thin high layers (`t243_ci6_rejected.png`).
* **Mediocris middles leaning inward** over the anchor: the topology guards (upper offset >= 0.24,
  footprint >= 0.78) cap the lean at ~-0.10, which changed nothing visible.
* **Earlier stratus coverage saturation** (carve against coverage / 0.18): no visible effect.
* **Gentler nimbostratus rim taper** (cubic, 0.40): no effect.
* **Cumulonimbus / supercell core density x1.8 and x4** (base, trunk, tower; anvil untouched,
  `t243_cb5_density_probe.png`): only the rain base darkened; the trunk's blue see-through look is
  unchanged. It is not missing density - it is lighting (the shadowed trunk is filled with sky colour).
  Not shipped; it is the next task.

## 3. Harness lessons

* Judge SHAPE on the neutral-lighting arms. The vertical dark stripes and the rounded "tube" shading on
  lit decks are lighting; the 15-member nimbostratus was first rejected on its lit side view and only
  accepted after the neutral view (`t243_ns3_rejected.png` vs `t243_ns5_neutral.png`).
* Gallery spawns draw from the world random, so a Java layout change cannot be compared against an
  older run's instance. A temporary spawn seed made layouts repeat run to run while experimenting; it is
  removed here.
* The T132 capture fixture can transiently fail to adopt a storm group ("no complete published storm
  group"); the auto driver now re-requests it every 120 frames (harness only, committed separately).

## 4. Cost

Not measured (shape-only mandate). The storm mass floor and the cumulus turrets were monolith-only
experiments until now; they now compile into the lean FINAL program as well. They add no texture
fetches (the floor reuses the union distance the erosion stage already has; the turrets are ALU).
Flag for the next performance pass.

## 5. Validation

`campaignGate` 72/72; `cloudMorphologyTopologySandbox` and `stormVolumetricGeometrySandbox` pass;
offline NVIDIA compile of the monolith, the generated lean FINAL / rain variants and the weather splat.
Final gallery: `t243-campaign/t243_final3.png`.
