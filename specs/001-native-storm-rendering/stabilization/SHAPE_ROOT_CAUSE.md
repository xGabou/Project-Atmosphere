# Why every production cloud looks like "smooth objects bolted together"

26 Oct 2026, after the user's review of the s4 gallery. This builds on Codex's review
(`validation/stabilization/codex review/`), whose source findings CR-01..CR-06 are the mechanisms named below.

## User observation

- All families look alike: no irregularities, no cavities.
- Mediocris reads as "spheres put together".
- Storms look like two clouds bolted together (top / bottom).
- The supercell has the right anvil but lacks the bumps-on-bumps structure, cavities and surrounding clouds of a real
  one (reference photo supplied).

## The root problem (one sentence)

**Every production cloud's form is authored as a small, fixed-topology set of smooth analytic primitives, and the only
smaller-scale form is one world-space noise at a fixed block size that may only nudge the surface threshold by a bounded
amount, and is switched off at the joins, cores and bases.**

So there is no scale hierarchy: no billows on billows, sized relative to the cloud they sit on. Without that hierarchy
a cloud reads as its primitives, whatever the lighting.

## Evidence

### 1. Fixed primitive templates (Java)

| Family | Primitives | Source |
|---|---|---|
| Humilis | exactly 7 lobes | `CloudMorphologyGenerators.puffPlan` |
| Mediocris, congestus | exactly 8 lobes | same |
| Puff hierarchy (all three) | exactly 4 base lobes, else `IllegalStateException` | `createHierarchicalPuffLobes` |
| Tower family | 12 members | `STRUCTURED_TOWER_COUNT` |
| Storms (calvus and supercell alike) | 10 members, common radius 450 / group 400 / drop 120 / rise 780 | `STORM_SYSTEM_MEMBER_COUNT`, `stormSystemPlan` (does not read the type's shape profile) |

- **Puff lobe shape.** Each lobe is one radial/vertical profile from four tier recipes (peak height, root / equator
  radius, upper power), varied by only ±3–5 % (`directPuffLobeSample`).
- **Storm reduction (Codex CR-03).** Storm descriptors are reduced to one rounded base + one column axis + a wedge deck.
  CORE rows do not contribute, and turrets use 5 fixed angular slots with no seed / type input (`StormBodySkeleton`,
  shader `stormBodyGroupField`).

### 2. Detail is bounded, fixed-scale and disabled where it matters (shader)

`resolvePuffContinuousField`:
- the only form inputs below lobe scale are the G-channel Worley carrier (world scale) and the detail FBM
  (22 / 11 / 6-block cells);
- both may only move the surface isovalue between 0.04 and 0.40 of the lobe envelope;
- `protection = max(core, junction, base-root)` sends the isovalue to the fixed 0.012 wherever two lobes meet, in the
  core, and along the base (Codex CR-04);
- later generic erosion is skipped for direct puffs.

The joins therefore stay perfectly smooth and the lobes' own rounded shells show. The detail cells (6–22 blocks) do not
grow with the cloud, so a 900-block storm and a 120-block puff get the same "texture".

### 3. Measured in game: the outlines are smooth curves

Box-counting dimension of the cloud outline (`stabilization/tools/silhouette_dimension.py`), cloud mask from the s4
legacy-vs-candidate difference (sky and terrain are identical between arms):

| Silhouette | Dimension |
|---|---|
| **Cumulus humilis / mediocris / congestus, side and close** | **0.96–1.01** (a smooth curve is 1.0) |
| Storms (calvus, supercell) side / anvil | 1.03–1.13 |
| Stratus / stratocumulus / nimbostratus | 1.01–1.13 |
| Cirrus | 1.14–1.16 |
| User's supercell photo at the same pixel width (300–500 px) | **1.23–1.25** |

Notes on the measurement:
- The photo mask includes a few internal gaps, so its value is slightly inflated.
- Real-cloud literature gives ~1.35 for cloud perimeters (Lovejoy 1982).
- "Below" views fill the frame and are excluded.

The cumulus family measures as plain geometric primitives. Storms and sheets get a little irregularity from noise, but
remain far below a real cloud.

### 4. Independent mechanisms that make it look worse (not the root)

- **Lower storm block** shaded by another model below the weather-map base (rain classification, Codex CR-01). Gives
  the "bolted" seam.
- **Discontinuous height / material metadata** across blended lobes and regions (CR-02). Gives shelves and tone steps.
- **Light field approximation** (CR-05). Gives rectangular bands on non-storm clouds.
- **Ambient transmission uses the sun-path optical depth** (CR-06). Undersides of broad, thin decks (nimbostratus) are
  too bright under the candidate.
- **Isolated spawns.** The gallery tests one cloud per region; whether the live weather produces surrounding clouds is
  unaudited (Codex T3).

## Why "more noise" alone will not fix it

Noise is already present on the exposed shells. It cannot act at the joins (protection), and it has one fixed scale, so
it gives granular "grit" on big storms and lumps that are too big for small puffs. What a real cumulus has is
self-similar structure: large billows whose surfaces carry smaller billows, sized relative to their parent, with real
indentations and cavities between them.

## Proposed root fix (S9, as a switchable arm; production default unchanged until the user accepts a gallery)

**Scale-relative billow hierarchy applied to the envelope, everywhere except the flat condensation base:**
- displacement = Σ_k a_k · (B(p / c_k) − mean), k = 0..2, with cell size c_k = L · 0.45 · 0.5^k;
- L = the dominant lobe's horizontal radius (the local cloud scale), so every cloud gets bumps proportional to itself;
- B = rounded Worley bumps from the existing base-noise G channel (no new textures);
- a_k decreasing with k, so the bumps sit on bumps;
- junction / core protection no longer zeroes it (only the base-root corridor keeps the flat base);
- the amplitude may open indentations between billows.

**Measure:** silhouette dimension (target ≥ 1.2 for cumulus), the same s4 views and cost (build-vs-build).

**Then extend** the same hierarchy to the storm body (the skeleton keeps its anvil / column; billows come from the
hierarchy instead of the 5-slot turret ring) and to sheets with a flatter amplitude profile.
