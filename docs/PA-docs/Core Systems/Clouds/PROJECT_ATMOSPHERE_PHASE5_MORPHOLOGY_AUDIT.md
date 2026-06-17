# Project Atmosphere Phase 5 Morphology Audit

Audit date: 2026-06-15

Scope: audit only. No implementation was performed.

## Sources Inspected

- PA native cloud type registry: `src/main/java/net/Gabou/projectatmosphere/clouds/type/CloudTypeRegistry.java`
- PA shape profile defaults: `src/main/java/net/Gabou/projectatmosphere/clouds/type/CloudShapeProfile.java`
- PA spawn morphology: `src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudGroupSpawner.java`
- PA backend geometry applier: `src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudRegionTypeGeometry.java`
- PA live cloud shader: `src/main/resources/assets/projectatmosphere/shaders/core/cloud_volume.fsh`
- PA/Simple Clouds region footprint compute shader: `src/main/resources/assets/projectatmosphere/shaders/compute/cloud_regions.comp`
- Simple Clouds JSON cloud types: `src/main/resources/data/simpleclouds/cloud_types/*.json`
- Project Atmosphere datapack cloud type: `src/main/resources/data/projectatmosphere/cloud_types/hurricane.json`

## Executive Finding

The project currently has two morphology stacks:

1. PA native live clouds: 10 built-in cloud types using a shared raymarched volumetric field. These have useful profile data for lobes, sheets, towers, anvils, flattening, shear, and storm walls, but most type identity is still parameter-driven inside one generic shader function.
2. Simple Clouds datapack clouds: 38 JSON cloud types plus one PA hurricane JSON using layered noise settings. These are mostly parameter variants of the same layered noise mesh generator. They can produce different texture density and vertical depth, but they do not provide strong meteorological silhouettes by cloud family.

The strongest unique silhouette today is hurricane, because it has a dedicated spiral/eye/band footprint path in the compute shader and hurricane semantics. The PA native cumulonimbus types are closest to acceptable because the shader already has tower and anvil controls. Most sheet clouds and legacy Simple Clouds types remain effectively circular or cylindrical regions filled with layered noise.

## Cloud Types Currently Existing

### PA Native Types

Defined in `CloudTypeRegistry`:

- `vapor_cluster`
- `cumulus_humilis`
- `cumulus_mediocris`
- `cumulus_congestus`
- `cumulonimbus_calvus`
- `cumulonimbus_capillatus`
- `stratus_nebulosus`
- `stratocumulus`
- `nimbostratus`
- `cirrus`

### Simple Clouds Datapack Types

Defined under `src/main/resources/data/simpleclouds/cloud_types`:

- `altocumulus`
- `altostratus`
- `altostratus_dry`
- `balls`
- `cookie`
- `cumulus_congestus`
- `cumulus_humilis`
- `cumulus_mediocris`
- `cumulus_noise`
- `custom_cumulonimbus`
- `dark_wall`
- `dense_cumulus`
- `dense_itty_bitty`
- `dense_stratocumulus`
- `dense_tsegrus`
- `floating_farlands`
- `heavy_stratus`
- `islands`
- `itty_bitty_bigger`
- `mammatus_thin`
- `matrix`
- `nimbostratus`
- `overcast`
- `pathway`
- `pattern`
- `real_itty_bitty`
- `severe_cumulonimbus`
- `severe_nimbostratus`
- `smaller_stratocumulus`
- `snow`
- `spots`
- `spotted`
- `stratocumulus_opacus`
- `stronger_stratus`
- `tall_noise`
- `tall_weirdness`
- `thicker_stratocumulus`
- `tsegrus`

### PA Datapack Special Type

- `projectatmosphere:hurricane`

This uses a Simple Clouds-style JSON layer definition, but hurricane morphology also has a dedicated spiral/eye/band path in shader and simulation code.

## Generators Currently Creating Them

| Generator | Cloud Types | Current Role |
|---|---|---|
| `CloudGroupSpawner.resolveMorphology` | PA native types spawned by PA commands/systems | Chooses cluster count, radius, group spread, base/top drops, density, coverage, and edge softness from family and id heuristics. |
| `CloudRegionTypeGeometry.apply` | PA native types | Applies `CloudShapeProfile` radius and vertical bounds to backend clusters. This is geometric sizing, not a full silhouette generator. |
| `cloud_volume.fsh::sampleCloudField` | PA native live render | Shared volumetric raymarch density function. Uses profile uniforms for lobes, vertical shaping, sheet bias, tower narrowing, anvil spread, storm wall, and noise erosion. |
| Simple Clouds layered noise mesh generator | All `simpleclouds:cloud_types/*.json` | Uses `noise_settings` layers to generate voxel/mesh cloud volume. Most variation is layer count, scale, height, offset, and storminess. |
| `cloud_regions.comp::circle` | PA/Simple Clouds region footprint | Gives most region clouds a circular footprint before 3D layer noise is sampled. |
| `cloud_regions.comp::projectatmosphere_sampleHurricane` and hurricane semantics | Hurricane | Dedicated spiral footprint with eye, eyewall, arms, outer bands, and anvil-edge coverage. |

## Morphology Systems Currently Used

| System | Status | Evidence |
|---|---|---|
| Layered | Used heavily by Simple Clouds JSON and partially by PA shader layer bias | Simple Clouds `noise_settings`; PA shader `layerBias`, `layerRadius`, and sheet profiles. |
| Volumetric | Used by PA native raymarch; Simple Clouds uses 3D noise mesh volumes | PA `cloud_volume.fsh` raymarches density through a volume. |
| Noise-based | Used everywhere | PA shader FBM/noise; Simple Clouds noise layers. |
| Anvil-based | Partially used | PA cumulonimbus profiles expose `anvilStrength`/`anvilSpread`; hurricane region shader has `anvilEdge`. |
| Tower-based | Partially used | PA cumulus/cumulonimbus profiles expose `towerStrength`/`towerNarrowing`; Simple Clouds tall storm JSON fakes tower by tall layer stacks. |
| Sheet-based | Used | PA stratus/stratocumulus/nimbostratus/cirrus use flattening/height squash; Simple Clouds stratus/nimbus/overcast types use broad low layers. |

## Shared Geometry And Parameter-Only Families

### PA Native

All PA native cloud types share the same active renderer silhouette function in `cloud_volume.fsh`. They differ through `CloudVisualProfile` and `CloudShapeProfile` parameters.

Parameter-only groups:

- Cumulus growth chain: `cumulus_humilis`, `cumulus_mediocris`, `cumulus_congestus`
- Thunder tower chain: `cumulonimbus_calvus`, `cumulonimbus_capillatus`
- Sheet/layer group: `stratus_nebulosus`, `stratocumulus`, `nimbostratus`, `cirrus`
- Default/fair-weather seed: `vapor_cluster`

Unique silhouette status:

- `cumulonimbus_capillatus` has the strongest parameter-level silhouette because it uses high tower/anvil/storm wall values.
- `cumulonimbus_calvus` is a tower but still not a truly separate tower generator.
- `cirrus` has shear and thin profile data but no dedicated filament/streak generator.
- Sheet types are distinct mainly by height, radius, flattening, darkness, and density, not by true frontal sheet geometry.

### Simple Clouds Datapack

All Simple Clouds JSON cloud types share the same layered noise mesh generator. Most are parameter-only variants.

Near-identical or strongly shared geometries:

- `pattern` and `cumulus_humilis`: identical one-layer settings.
- `altostratus_dry` and `cumulus_mediocris`: identical layer settings with different weather/storm labels.
- `nimbostratus` and `severe_nimbostratus`: same layered nimbostratus structure with stronger scales/storm values.
- `tsegrus` and `dense_tsegrus`: same first two layers, third layer adjusted for density/scale.
- `dense_stratocumulus`, `smaller_stratocumulus`, `thicker_stratocumulus`: same stratocumulus family with size/thickness/detail changes.
- `dense_itty_bitty`, `real_itty_bitty`, `itty_bitty_bigger`, `cumulus_noise`: small noise-cloud variants.
- `spots` and `spotted`: spot/noise coverage variants.
- `heavy_stratus`, `stronger_stratus`, `overcast`: sheet/stratus variants with parameter changes.

## Effectively Cylindrical Cloud Types

The current region footprint path is circular for normal region clouds. PA native raymarching uses an AABB around a circular radius and then sculpts density inside it. Simple Clouds uses circular region coverage and layered noise. Therefore, "effectively cylinders" means clouds whose final visible form still reads as a round or flattened round volume instead of a distinct meteorological silhouette.

Most cylinder-like PA native types:

- `vapor_cluster`
- `stratus_nebulosus`
- `stratocumulus`
- `nimbostratus`
- `cirrus`

Partially cylinder-like PA native types:

- `cumulus_humilis`
- `cumulus_mediocris`
- `cumulus_congestus`
- `cumulonimbus_calvus`
- `cumulonimbus_capillatus`

The cumulus and cumulonimbus types have lobes/tower/anvil controls, so they are less cylindrical than sheet types, but they still use the same circular base footprint and generic radial field.

Most Simple Clouds datapack types are effectively layered cylinders or circular noise blobs. The main exception is hurricane, which has a dedicated spiral footprint outside the ordinary circle path.

## PA Native Cloud Type Audit

| Cloud Type | Current Generator | Current Shape | Visual Quality | Reuse Potential | Recommended Final Shape |
|---|---|---|---|---|---|
| `vapor_cluster` | PA native `CloudGroupSpawner` + `CloudRegionTypeGeometry` + `cloud_volume.fsh` | Small low-density puffy/ragged circular volume with weak lobes. | Acceptable as transient vapor seed, not acceptable as a named final cloud silhouette. | High for lifecycle seed and fair-weather precursor. | Wispy broken vapor puffs with sparse uneven lobes and high transparency. |
| `cumulus_humilis` | PA native shared volumetric shader with cumulus profile. | Low puffy lobe cap over flattened base, circular footprint. | Acceptable with tuning. Reads as fair-weather cumulus if scale is right. | High. Keep shader profile and cluster grouping. | Small cotton-like individual cells, flat base, shallow vertical development, separated clusters. |
| `cumulus_mediocris` | PA native shared volumetric shader with stronger cumulus profile. | Medium puffy mound, more vertical lift than humilis. | Acceptable with tuning, but too close to humilis/congestus if only radius/top changes. | High. Reuse lobe/noise/tower controls. | Taller cauliflower mound with visible vertical growth and still no storm anvil. |
| `cumulus_congestus` | PA native shared volumetric shader with tower profile. | Multi-cluster tall cumulus tower, no real hard tower generator. | Borderline acceptable. Needs stronger tower silhouette but not total rewrite. | Medium-high. Reuse tower controls but improve generator separation. | Towering cumulus with stacked turrets, narrow waist, strong vertical column, no mature anvil. |
| `cumulonimbus_calvus` | PA native shared volumetric shader with tower/storm profile. | Tall storm tower with some narrowing and storm-wall shaping. | Acceptable foundation, but silhouette still generic. | Medium. Reuse shader storm/tower controls. | Massive vertical storm tower, dark base, smooth rounded upper dome, developing overshoot but little/no fibrous anvil. |
| `cumulonimbus_capillatus` | PA native shared volumetric shader with strongest tower/anvil/storm values. | Tall tower plus anvil spread, still radial around one circular footprint. | Best PA native storm candidate; acceptable as placeholder. | Medium. Reuse anvil/storm controls but needs dedicated storm generator for final. | Mature thunderstorm with wide wind-sheared anvil, dense vertical core, dark precipitation shaft, asymmetric top spread. |
| `stratus_nebulosus` | PA native shared volumetric shader with sheet profile. | Wide, shallow, flattened circular sheet. | Needs tuning and probably a sheet generator; currently reads like flat fog disk. | Medium. Reuse material/density/flattening values. | Continuous low gray deck, broad non-circular sheet, soft lower boundary, minimal puffs. |
| `stratocumulus` | PA native shared volumetric shader with sheet plus lobes. | Flattened sheet with lumpy profile, circular region footprint. | Needs tuning. Profile direction is right but silhouette is not distinct enough from stratus/nimbostratus. | Medium-high. Reuse lobe plus flattening controls. | Low broken sheet of rounded cells with gaps, cellular texture, wider than tall. |
| `nimbostratus` | PA native shared volumetric shader with dark sheet/rain profile. | Broad dense flattened disk with precipitation core. | Needs tuning and a final sheet/frontal generator. | Medium. Reuse darkness/precipitation/material profile. | Deep layered rain deck, continuous horizontal mass, dark underside, broad non-circular frontal extent. |
| `cirrus` | PA native shared volumetric shader with shear/anvil-like thin profile. | Thin sheared volume, still broad radial sheet. | Requires full generator rewrite for final cirrus. Current profile cannot create believable wisps. | Low-medium. Reuse high-altitude material and shear parameters only. | Thin fibrous streaks, hooks/mares tails, elongated filaments aligned with upper wind. |

## Simple Clouds Datapack Type Audit

| Cloud Type | Current Generator | Current Shape | Visual Quality | Reuse Potential | Recommended Final Shape |
|---|---|---|---|---|---|
| `altocumulus` | Simple Clouds layered noise mesh | Three stacked broad layers. | Tuning candidate, currently layer/noise-defined. | Medium. | Mid-level patch deck of small rounded cells, separated clusters, no rain core unless weather demands it. |
| `altostratus` | Simple Clouds layered noise mesh | Two broad rain layers. | Parameter-only sheet, weak silhouette. | Medium. | Smooth mid-level gray sheet, broad horizontal veil, subtle thickness changes. |
| `altostratus_dry` | Simple Clouds layered noise mesh | Same geometry as `cumulus_mediocris`. | Misclassified visually. | Low-medium. | Dry mid-level sheet/veil, not cumulus mound. |
| `balls` | Simple Clouds layered noise mesh | Experimental tall noise blobs. | Not meteorologically acceptable. | Low. | Remove from final taxonomy or map to test/debug only. |
| `cookie` | Simple Clouds layered noise mesh | Experimental dense layered storm/blob. | Not meteorologically acceptable as named cloud. | Low. | Debug/test only, or rewrite as storm cell if retained. |
| `cumulus_congestus` | Simple Clouds layered noise mesh | Tall stacked noisy layers. | Needs rewrite for true tower. | Medium. | Towering cumulus with cauliflower turrets and narrow vertical growth. |
| `cumulus_humilis` | Simple Clouds layered noise mesh | Single small noise layer. | Too simple but usable as placeholder. | Medium. | Small fair-weather puffs with flat bases and separated cells. |
| `cumulus_mediocris` | Simple Clouds layered noise mesh | Same geometry as `altostratus_dry`. | Incorrect identity. | Medium-low. | Medium cumulus mound with stronger puffy vertical structure. |
| `cumulus_noise` | Simple Clouds layered noise mesh | Small noisy puffs. | Acceptable only as generic small cloud. | Medium. | Irregular small cumulus fragments. |
| `custom_cumulonimbus` | Simple Clouds layered noise mesh | Large tall storm mass with several broad layers. | Better than most Simple Clouds storms, still not a true anvil/tower. | Medium. | Mature cumulonimbus with explicit core, overshoot, anvil, and rain shaft. |
| `dark_wall` | Simple Clouds layered noise mesh | Dense elongated storm wall. | Useful as storm-wall placeholder. | Medium. | Low dark shelf/wall cloud with elongated leading edge. |
| `dense_cumulus` | Simple Clouds layered noise mesh | Dense small/medium puffy stack. | Tuning candidate. | Medium. | Dense cumulus field with clear lobe cells and flat bases. |
| `dense_itty_bitty` | Simple Clouds layered noise mesh | Small dense noise blob. | Parameter-only small cloud. | Low-medium. | Tiny cumulus fractus fragments. |
| `dense_stratocumulus` | Simple Clouds layered noise mesh | Dense low sheet/cell layer. | Tuning candidate. | Medium. | Low dense cellular stratocumulus deck. |
| `dense_tsegrus` | Simple Clouds layered noise mesh | Variant of `tsegrus` with denser third layer. | Stormy placeholder, not final morphology. | Low-medium. | If retained, map to severe turbulent storm deck or remove. |
| `floating_farlands` | Simple Clouds layered noise mesh | Experimental island/farlands noise. | Not meteorological. | Low. | Debug/fantasy only, not final weather cloud. |
| `heavy_stratus` | Simple Clouds layered noise mesh | Heavy low sheet. | Tuning candidate. | Medium. | Thick low gray stratus deck with wide sheet footprint. |
| `islands` | Simple Clouds layered noise mesh | Broken island-like noise patches. | Acceptable for stylized broken clouds, not typed meteorology. | Medium-low. | Broken cloud islands or fair-weather scattered patches. |
| `itty_bitty_bigger` | Simple Clouds layered noise mesh | Small cloudlets. | Parameter-only small cloud. | Low-medium. | Small cumulus fragments with individual cell spacing. |
| `mammatus_thin` | Simple Clouds layered noise mesh | Thin layered underside variation. | Needs full rewrite for mammatus. | Low. | Hanging pouch-like lobes under anvil/base, not just a flat layer. |
| `matrix` | Simple Clouds layered noise mesh | Artificial patterned noise. | Not meteorological. | Low. | Debug/test only. |
| `nimbostratus` | Simple Clouds layered noise mesh | Broad stacked rain sheet. | Usable placeholder. | Medium. | Deep continuous rain shield with dark underside and non-circular frontal sheet. |
| `overcast` | Simple Clouds layered noise mesh | Broad layered overcast. | Usable placeholder. | Medium. | Continuous overcast deck, smooth coverage, subtle vertical texture. |
| `pathway` | Simple Clouds layered noise mesh | Long/broad pathway-like noise. | Not meteorological as named. | Low. | Debug/test only or rewrite as cloud street bands. |
| `pattern` | Simple Clouds layered noise mesh | Identical to `cumulus_humilis`. | Duplicate. | Low. | Remove, debug-only, or map to small cumulus variant. |
| `real_itty_bitty` | Simple Clouds layered noise mesh | Tiny sparse blob. | Parameter-only fragment. | Low-medium. | Tiny fair-weather fragments. |
| `severe_cumulonimbus` | Simple Clouds layered noise mesh | Tall severe storm stack, but generic layered mass. | Needs rewrite for final severe storm. | Medium-low. | Explosive storm tower with anvil, overshooting top, wall cloud, rain/hail core. |
| `severe_nimbostratus` | Simple Clouds layered noise mesh | Stronger nimbostratus parameter variant. | Usable placeholder. | Medium. | Deep saturated rain shield, darker and thicker than nimbostratus. |
| `smaller_stratocumulus` | Simple Clouds layered noise mesh | Smaller stratocumulus variant. | Tuning candidate. | Medium. | Broken low cellular deck with smaller cells and more gaps. |
| `snow` | Simple Clouds layered noise mesh | Tall/noisy snow-related layered cloud. | Needs explicit snow cloud identity. | Low-medium. | Cold stratiform snow deck or snow squall band depending gameplay role. |
| `spots` | Simple Clouds layered noise mesh | Spotted broad patches. | Placeholder only. | Low-medium. | Broken small cloud patches or altocumulus field. |
| `spotted` | Simple Clouds layered noise mesh | Spotted/noisy sheet. | Placeholder only. | Low-medium. | Broken cellular patches with uneven gaps. |
| `stratocumulus_opacus` | Simple Clouds layered noise mesh | Thick opaque stratocumulus stack. | Tuning candidate. | Medium. | Opaque low cell deck, fewer gaps, broad sheet with rounded elements. |
| `stronger_stratus` | Simple Clouds layered noise mesh | Stronger/darker stratus sheet. | Tuning candidate but storm label is questionable. | Medium. | Thick low stratus or storm scud sheet, not thunderstorm tower. |
| `tall_noise` | Simple Clouds layered noise mesh | Tall generic noise volume. | Not final. | Low. | Debug/test only or rewrite into congestus/cumulonimbus. |
| `tall_weirdness` | Simple Clouds layered noise mesh | Tall experimental noise. | Not final. | Low. | Debug/test only. |
| `thicker_stratocumulus` | Simple Clouds layered noise mesh | Thickened stratocumulus variant. | Tuning candidate. | Medium. | Thicker low cellular deck with layered vertical depth. |
| `tsegrus` | Simple Clouds layered noise mesh | Custom severe/turbulent storm-like layers. | Placeholder, unclear taxonomy. | Low-medium. | Rename/map to a real storm morphology or keep as debug/custom. |

## PA Datapack Special Type Audit

| Cloud Type | Current Generator | Current Shape | Visual Quality | Reuse Potential | Recommended Final Shape |
|---|---|---|---|---|---|
| `projectatmosphere:hurricane` | Simple Clouds JSON layers plus dedicated hurricane shader/semantics path | Broad anisotropic layered storm field; dedicated shader adds eye, eyewall, spiral bands, outer storm, and anvil-edge coverage. | Best unique silhouette in current codebase. Needs tuning, not a rewrite from zero. | High. Reuse hurricane footprint, bands, eyewall, eye, and semantic sampling. | Large rotating tropical cyclone with clear eye, dense eyewall, spiral rain bands, central dense overcast, and outer anvil shield. |

## Cloud Types With Unique Silhouettes Today

Strongest:

- `projectatmosphere:hurricane`

Partial/parameter-level unique:

- `cumulonimbus_capillatus`
- `cumulonimbus_calvus`
- `cumulus_congestus`
- `dark_wall`
- `custom_cumulonimbus`
- `severe_cumulonimbus`

Not truly unique:

- Most PA native non-storm types
- Most Simple Clouds JSON types
- All obvious debug/experimental types: `balls`, `cookie`, `matrix`, `pathway`, `tall_noise`, `tall_weirdness`, `floating_farlands`

## Cloud Types Already Acceptable

Acceptable as placeholders or near-final foundations:

- `projectatmosphere:hurricane`
- PA `cumulus_humilis`
- PA `cumulus_mediocris`
- PA `cumulonimbus_calvus`
- PA `cumulonimbus_capillatus`
- Simple Clouds `nimbostratus`
- Simple Clouds `overcast`
- Simple Clouds `dense_stratocumulus`
- Simple Clouds `stratocumulus_opacus`
- Simple Clouds `custom_cumulonimbus`
- Simple Clouds `dark_wall`

These are not all final-quality. They are acceptable enough to keep while morphology architecture is finalized.

## Full Generator Rewrite Required

Needs dedicated generator rather than parameter tuning:

- PA `cirrus`
- PA `stratus_nebulosus` if final target is a non-circular deck
- PA `nimbostratus` if final target is a true frontal/rain shield
- Simple Clouds `mammatus_thin`
- Simple Clouds `severe_cumulonimbus`
- Simple Clouds `cumulus_congestus`
- Simple Clouds `altostratus_dry`
- Simple Clouds `cumulus_mediocris`
- Simple Clouds debug/experimental types if retained as gameplay-visible clouds: `balls`, `cookie`, `floating_farlands`, `matrix`, `pathway`, `tall_noise`, `tall_weirdness`, `tsegrus`, `dense_tsegrus`

Reason: their current morphology is either a duplicate, a layered noise blob, or not meteorologically aligned with the name.

## Tuning Only Required

Can be improved mostly by parameters once final architecture supports distinct generator families:

- PA `vapor_cluster`
- PA `cumulus_humilis`
- PA `cumulus_mediocris`
- PA `cumulus_congestus`
- PA `cumulonimbus_calvus`
- PA `cumulonimbus_capillatus`
- PA `stratocumulus`
- Simple Clouds `altocumulus`
- Simple Clouds `altostratus`
- Simple Clouds `cumulus_noise`
- Simple Clouds `dense_cumulus`
- Simple Clouds `dense_itty_bitty`
- Simple Clouds `dense_stratocumulus`
- Simple Clouds `heavy_stratus`
- Simple Clouds `islands`
- Simple Clouds `itty_bitty_bigger`
- Simple Clouds `nimbostratus`
- Simple Clouds `overcast`
- Simple Clouds `real_itty_bitty`
- Simple Clouds `severe_nimbostratus`
- Simple Clouds `smaller_stratocumulus`
- Simple Clouds `snow`
- Simple Clouds `spots`
- Simple Clouds `spotted`
- Simple Clouds `stratocumulus_opacus`
- Simple Clouds `stronger_stratus`
- Simple Clouds `thicker_stratocumulus`
- `projectatmosphere:hurricane`

## What Can Be Reused

- `CloudTypeDefinition`, `CloudVisualProfile`, `CloudShapeProfile`, `CloudMaterialProfile`, and datapack loading.
- PA cloud lifecycle, cluster state, region state, cloud type transitions, and render-data transport.
- PA shader uniforms for lobe count, lobe strength, wind shear, vertical tilt, cell split, tower narrowing, anvil spread, flattening, edge raggedness, and storm wall.
- PA raymarch infrastructure and lighting/composite path.
- `CloudGroupSpawner` cluster-group spawning concept.
- Hurricane spiral/eye/eyewall/outer-band logic.
- Simple Clouds JSON layer settings as rough tuning references, not as final morphology architecture.

## What Must Be Rewritten

- A proper morphology generator dispatch layer. Current type identity is mostly parameters fed into one generic field.
- Dedicated cirrus/filament generation.
- Dedicated sheet/frontal generation for stratus, altostratus, nimbostratus, and overcast.
- Dedicated tower/cumulonimbus generation for congestus and storm clouds, including separated core, wall cloud, anvil, precipitation shaft, and overshooting top.
- Mammatus generation if mammatus remains gameplay-visible.
- Cleanup or isolation of debug/fantasy Simple Clouds types so they do not masquerade as meteorological cloud types.
- Non-circular footprint support for sheet decks, fronts, cloud streets, shear bands, and anvils.

## Minimum Work To Make All Cloud Types Visually Distinct

1. Assign every cloud type to a morphology family: `PUFF`, `TOWER`, `ANVIL_STORM`, `SHEET`, `CELLULAR_SHEET`, `FILAMENT`, `SPIRAL_STORM`, `DEBUG`.
2. Add a generator id or morphology id to each PA cloud definition and datapack cloud definition.
3. Keep the existing PA shader but branch its field shaping by morphology family instead of relying only on continuous parameters.
4. Introduce non-circular horizontal masks for sheet, filament, band, and anvil families.
5. Collapse or hide duplicate Simple Clouds entries:
   - `pattern` duplicates `cumulus_humilis`.
   - `altostratus_dry` duplicates `cumulus_mediocris`.
   - Small-cloud variants should share one fragment family with different scale/density.
6. Make cirrus a filament/streak field instead of a flattened radial volume.
7. Make nimbostratus/stratus/altostratus use broad sheet masks instead of circular disks.
8. Make cumulonimbus use explicit tower core plus anvil cap rather than only radial tower/anvil parameters.
9. Keep hurricane as its own generator family and tune it after ordinary cloud families are separated.

This is enough for visual distinction before deep shader-quality work.

## Ideal Morphology Architecture Before Shader Work Begins

Use a data-driven morphology pipeline with explicit generator families:

| Layer | Responsibility |
|---|---|
| Cloud type definition | Meteorological identity, family, material, precipitation, lifecycle rules, default morphology id. |
| Morphology profile | Generator id, footprint type, vertical profile, lobe/cell/tower/anvil parameters, noise bands, scale constraints. |
| Morphology generator | Produces normalized density or mask fields for a family: puff, tower, anvil storm, sheet, cellular sheet, filament, spiral storm. |
| Region/cluster placement | Places one or more morphology instances in world space with wind shear and lifecycle data. |
| Render shader | Samples the selected morphology output and handles lighting, transparency, shadowing, and weather integration. |
| Debug view | Shows generator id, profile id, footprint, vertical bounds, and active parameter values. |

Recommended generator families:

- `puff_lobed`: vapor, cumulus humilis, cumulus mediocris, small fragments.
- `tower_cumulus`: cumulus congestus.
- `storm_anvil`: cumulonimbus calvus/capillatus, severe cumulonimbus.
- `sheet_stratus`: stratus, altostratus, nimbostratus, overcast.
- `cellular_sheet`: stratocumulus, altocumulus.
- `filament_cirrus`: cirrus.
- `spiral_cyclone`: hurricane.
- `debug_noise`: experimental legacy types only.

The main architectural requirement is to stop treating every type as the same circular radial field. Shader polish should wait until the generator families and morphology ids are stable, otherwise the shader work will be tuning around duplicate geometry.
