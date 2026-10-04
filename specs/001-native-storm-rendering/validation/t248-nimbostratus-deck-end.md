# T248 - Nimbostratus deck-end shape

Branch `quality/t217-deep-interior-lighting`, from `e3a54fde` (T247). Morphology only: lighting (T244 / T226 /
T247), density model, temporal reconstruction, rain shading and every other family unchanged. Evidence:
`validation/t248-campaign/`.

## Defect

From side / close / front / back / edge_profile / top_profile the nimbostratus deck broke into member-sized flat
blocks: toward its narrow end it stepped down block by block with vertical walls, with notches between members and,
on some instances, a raised block or tall lump (`ns_end_base`, and on fixed-seed identical clouds the production
build shows a tall wedge block on instance 1 `front` and a tall blob on instance 3 `close` - `t248_seeded`).

## Attribution

The profile-5 carve envelope is `min(cov / 0.5, 1 - smoothstep(0.82 - 0.45 r, 1 - 0.45 r, h01))`,
`r = 1 - saturate(cov / 0.5)`, on the LOCAL weather coverage. Each splatted member paints its own coverage
plateau with a steep edge and overlaps add up, so both terms follow member footprints: the top taper drops member
by member toward the rim (the staircase) and the coverage term notches the face between members. Ruled out
earlier (T246): member heights / base-top low-pass (NS-C, no effect), the probe, the material profile; saturating
coverage earlier (NS-B) removed the carve and exposed a lathe-turned bottle. Member placement (the sunflower oval)
and radius progression were not changed: the steps sit on coverage plateaus, not on gaps in the layout.

## Candidates (monolith arms, same instances, three fresh instances per session)

| id | change | result | verdict |
|---|---|---|---|
| NS-D1 / D2 | top taper `r` from a low-passed coverage (3x3 at +-24 / +-40) | smoother top, gentler end taper; vertical seams in the face remain | partial |
| NS-F24 | D + the envelope's coverage term from the low-passed coverage, +-24 | smoother, a little lumpy | rejected for F40 |
| **NS-F40** | D + envelope coverage term low-passed, +-40 | one broad even layer, ends ramp down gradually, lumps / peaks gone, no capsule; above outline and below / underside unchanged | **accepted** |

Sheets: `t248a_zoom` (D), `t248b_zoom` (D2 / F40 / F24), `t248b_grid1` / `t248b_grid2` (M vs F40, all required
views x three instances).

## Production change (`cloud_atmosphere_volume.fsh`)

`nsCoverage` = mean of the local coverage and the eight neighbours at +-40 blocks (saturated, `CoverageMul`); it
drives both `r` and the envelope's coverage term. The outline still follows the local coverage through the later
`smoothstep(coverageMod)` gate. Light-tap evaluations (`paDensityConsumer` 2 / 9) and the field build keep the
local value: the light needs the deck's mass, not its top contour.

## Validation

* Shape: production lean FINAL, three instances (`t248qs`, fixed seed 4242) against HEAD on the SAME clouds
  (`t248hs`): side, opposite_side, close, front, back, above, below, underside, edge_profile, top_profile - broad
  even decks, no staircase, no capsule / blob; above outline and the underside unchanged (`t248_seeded`).
* Static: `campaignGate` 0, `check` 0, `stormMorphologySandbox` 0 (`gate-t248.log`, `check-build-t248.log`,
  `sandbox-t248.log`).
* Gallery: `validation/t246-final-gallery/nimbostratus` regenerated from `t248qs`.

## Performance

Fixed-seed sessions (identical clouds; the harness seeds the level random before each spawn, `CG_SEED`), mean of
per-view median cloud-pass gpuMs over 13 views x three instances: HEAD 3.41 ms, T248 4.00 ms (+0.59). A five-tap
blur cost the same (4.04 ms): the cost is the deck's added volume (the full-height layer now runs to the rim
instead of being notched), not the fetches. Nimbostratus skies stay far below the storm scenes (~5.5 ms) and the
10 ms ceiling; the storm benchmark has no profile-5 material and is unaffected.

## Limitations

* The narrow tail is softer but can still show a small step where a lone outer member sits beyond the 40-block
  blur radius.
* The low-pass slightly fills the top between widely separated members (intended: one layer).
