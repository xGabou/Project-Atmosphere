# T251 - Cumulus mediocris top-down asymmetry

Branch `quality/t217-deep-interior-lighting`, from `c3c846fd` (T250). Layout only (`CloudMorphologyGenerators`,
mediocris path). No shader, lighting, grid, storm or nimbostratus change. Evidence: `validation/t251-campaign/`.

## 1. Remaining defect

From above, mediocris often read as a symmetric four-leaf "X" / hourglass: four near-equal lobes in two mirrored
pairs around a pinched waist. T246 had jittered the three base shoulders by +-0.20 rad; larger base changes broke
the topology sandbox (width, anisotropy, spacing).

## 2. Seeded instances (`CG_SEED=4242`, six instances, all 12 views; `t251_above`)

Symmetric: instances **1, 3, 6** (clear X / mirrored hourglass). Acceptable: **2, 4, 5** (irregular; 4 borderline).
Every instance is one connected component.

## 3. Attribution

Member layout logged at spawn (`CG_MEMBER`, `t251-metrics.txt`): anchor + three base shoulders, two MIDDLE lobes,
two CROWNS. From above the tall upper lobes own the silhouette (the base is hidden under them), and they formed two
matched pairs:

* both middles rise from the SAME shoulder with opposite tangent signs - a mirror pair about that shoulder, with
  near-equal radii (e.g. instance 6: 19.7 / 20.2 blocks);
* one crown per middle with a shared tangent sign and near-equal radii (17.8 / 17.9) - a rotational pair.

Together: four similar protrusions at roughly 90 deg - the X. So the cause is E/F (the upper-lobe placement rule)
with A/B (equal radii / spacing within each pair), not the base angles (C) or vertical alignment (D).

Pixel metrics (`t251_sym.py`: best mirror IoU, IoU under 120 / 90 deg rotation, PCA aspect) only loosely track the
visual X; visual judgement is the acceptance criterion (as the brief requires).

## 4-5. Candidates and rejected reasons

| id | change (mediocris only, no extra random draws) | result |
|---|---|---|
| B1 | second middle / crown tangent ratio x0.55 | footprint p-max 1.076 > 1.04 (a smaller tangent makes the offset more RADIAL at the same separation - wider) - rejected |
| B1' | second middle separation x0.70..0.85, second crown x0.70 | middle separation 0.39 / 0.424 < 0.43 (middles merge), upper-parent separation 0.152 < 0.17 (crown swallowed) - rejected; these bands encode distinct cauliflower lobes and were kept |
| B1'' | second crown separation x0.85 only | passes, but the top silhouette barely changes (instance 6 still an X) - rejected |
| B2 | second middle / crown radius x0.82 (also with proportional height) | upper minor rendered width/height 0.55..0.60 < 0.65 (needle turrets) - rejected; band kept |
| **B3** | **both crowns on the FIRST middle, on either side of it (second crown's tangent sign flipped)** | **passes every band; the X breaks** |

No sandbox band was widened.

## 6. Final mechanism

`createHierarchicalPuffLobes`: for mediocris the crown support is `middleLobes.get(0)` for both crowns, and the second
crown uses `-crownTangentSign`. Result: one dominant turret mass (middle + two crowns) and a lone middle - a
lopsided cauliflower instead of two matched pairs. Same lobe count, same random stream, same radii / separation
ranges; humilis and congestus untouched (congestus returns through `createCongestusTiers` before the crown code;
humilis sandbox metrics identical to T250).

## 7-9. Before / after (same six seeded clouds; `t251_b3_above`, `t251_b3_views`)

* Above: instance 6's X becomes an irregular elongated cauliflower mass; instance 3's mirrored hourglass a lopsided
  cluster; instances 2 and 4 more irregular; 5 similar; instance 1 still shows two lobes over a lower mass
  (improved least - its bilateral IoU rises 0.73 -> 0.85 as it elongates).
* Mean PCA aspect 1.33 -> 1.44 (slightly more elongated; group width/height max 1.4987, unchanged band).
* Side / close / low_angle / below: natural compact cauliflower towers, slightly more lopsided; below outlines more
  irregular; one connected component everywhere.
* Sandbox: upper minor width/height 0.666 (>= 0.65), crown separation 0.428 (>= 0.32), all other bands pass.

## 10. Regression checks

`campaignGate` 0, `check` 0 (cloudMorphologyTopologySandbox included), `stormMorphologySandbox` 0
(`gate-t251.log`, `check-build-t251.log`, `sandbox-t251.log`). Humilis topology / analytic metrics identical to T250.

## 11. Performance sanity

CPU layout only, same lobe count. Lean FINAL, same seeded clouds, mean of per-view median gpuMs over 12 views:
base 4.24, B3 4.28 ms - within noise.

## 12. Limitations

* About a third of seeds (instance 1 here) still read fairly regular from above: the base trefoil and the remaining
  middle+crown cluster can still line up.
* Gallery: `validation/t246-final-gallery/cumulus_mediocris` regenerated from the B3 session (first instance).
