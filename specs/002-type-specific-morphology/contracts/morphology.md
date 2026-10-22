# Experimental storm morphology contract — interpretation v4

Input: exact native storm ID + CloudShapeProfile + seed, never CloudVisualProfile. Output: immutable coherent recipe/program, conservative local bounds and signature. Coordinates X downwind, Y up, Z crosswind, base reference Y=0.

Macro anatomy independent of noise; meso hierarchy attached/unequal; micro breakup bounded and disabled for structure/control checks. CPU/GPU evaluate identical packed parameters/equations. Material is applied after geometry.

Tilt/shear change axes; narrowing changes width profile; splitting creates organised secondary structure; conditional storm wall changes attached lower mass. Scale/height/flattening/spread/raggedness/lobe structure have explicit geometric consumers. Metadata identity and family applicability are documented.

Whole-program residency only: no arbitrary feature drops. Prototype default off, no schema/default switch. Production packet/save/acceleration/migration choice requires approval after native image and cost evidence.

## Local GPU recipe (experiment only)

RGBA32F, one complete variable-length row on the existing managed descriptor sampler; no extra texture unit. Twelve header texels, followed by two texels per swept growth axis and two per branching ice ribbon. Size is `192 + 32 * (axisCount + ribbonCount)` bytes, up to 2240 bytes with 32 of each. Three hierarchy groups describe the complete tower/crown/branch, canopy/flank and ice-bundle supports; these are family components, not three permitted authored features. Calvus has one group. No relationship to the old eight-candidate authoring ceiling.

| Texels | Contents |
| --- | --- |
| 0 | Radius, height, base depth, archetype |
| 1 | Tilt, shear, narrowing, canopy spread |
| 2 | Flattening, micro raggedness, split, conditional lowering |
| 3 | Structure strength, axis count, ribbon count, interpretation version |
| 4 | Tower width/top bound, canopy Y interval |
| 5 | Ribbon Y interval, evaluator support allowance, group count |
| 6–11 | Three complete group AABB min/max pairs; unused pairs zero |
| 12 onward | Axis direction/birth/tip/root/tip radius, then ribbon root/length/fan/rise/cross-section |

The content signature includes the entire header, bounds, version and all features. Placement is separate. The prototype's legacy-profile adapter uses radius=2.5*baseRadius, height=4*topOffset and baseDepth=2*baseOffset only to build comparable isolated fixtures. These are **not approved physical units or new production defaults**. A production typed shape definition must expose direct dimensions and separate growth-axis policy from canopy/ice policy; the legacy `lobeCount` names must not become the permanent vocabulary of every family.

## Conservative traversal / agreement

Radial boundary distances are not certified SDFs. Do not sphere-trace them. The experiment retains 4-block exterior sampling, disables old atlas/candidate and temporal shortcuts, and optionally skips gaps between complete component ray intervals. Skips are integer multiples of the original stride, preserving the original sample lattice; camera-inside paths retain the exact fallback. Global enclosure allows the maximum smooth-min kernel, feather, bounded micro decoration and numerical safety. Existing wider evaluator-pruning allowances remain unchanged for CPU/GPU parity.

Ordinary shader variants compile the experiment out. Only opt-in fixture variants evaluate it. Primary/light/probe calls routed through the shared density function and CPU camera sampling use the same recipe; flat lighting does not validate production light-cache or ground-shadow authority. Those consumers, server ownership and wire/save migration remain explicit production approval gates.

Native evidence requires acknowledged identical poses, published prototype authority, at least 30 fresh timestamp results after warmup, sampled raw-density parity, zero occupied viewport edges, and a float RGBA interval/reference comparison. Complete galleries or passing numerical tests alone are not meteorological acceptance.

## Proposed production authoring schema — design, not adopted

The outer definition contains schema/interpretation version, exact server type ID, family-specific shape payload, seed and declared bounds/decoration allowance. An instance contains group UUID/generation and a local-to-world frame. Material/lifecycle are separate payloads. Direct lengths use documented world-block units; no visual-profile inference or undocumented multipliers.

| Family-specific payload | Geometry-facing fields / meaning |
| --- | --- |
| ConvectiveDefinition | Condensation surface, direct development height, dominant growth paths with width-by-height curves, attached secondary turrets, scale-separated breakup policy |
| StormDefinition | Developing/mature/organised archetype; condensation surface; dominant updraft tree; lean/shear frame; split organisation; crown development; independent canopy surface and ice-network policies; conditional lowered-base feature |
| LayerDefinition | Continuous lower/upper surfaces; shallow/deep thickness policy; mesoscale cellular connectivity/openings; underside raggedness; rain-body extent |
| IceDefinition | Directional branching ribbon graph; root/branch connections; length/width/depth/taper curves; fanning and shear frame |

A growth node owns a parent attachment, centreline and varying cross-sections; it is not a simulation member, sphere or tile candidate. A canopy owns coherent upper/lower surfaces and fibrous outlets, not mandatory anvil lobes. A layer is continuous even across its cellular structure. Ice branches must attach or explicitly belong to a separate strand; they are not stretched blobs. Each payload exposes MACRO/MESO/MICRO inspection independently. Complexity/count validation is an authoring/network safety policy independent of GPU storage pages.

Legacy migration is explicit: radius/offset/height map to direct dimensions using a versioned conversion; lobeCount/strength map to **convective growth** policy only, not automatically to every family's structure count; tilt/shear map to the height-varying frame; split/narrowing map to updraft organisation; spread maps to canopy geometry; flattening to the lower surface; raggedness to bounded breakup; wall to conditional lowering only where applicable. Identity is metadata. Unsupported combinations are rejected or reported as deprecated during migration, not silently stored as fake controls. Family applicability and conversion are part of the saved/wire version.

The prototype demonstrates shape-only compilation and variable-length residency but has not proved that its filled envelope is the right body model. The proposed payload deliberately allows dominant growth paths to define the actual mass, rather than merely decorate a smooth axial primitive. Choosing compact procedural evaluation, a general hierarchical program or a cached local volume remains a measured architecture decision before productionization.
