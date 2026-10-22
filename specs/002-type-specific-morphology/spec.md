# Feature Specification: Type-specific cloud morphology

**Feature Branch**: `quality/t217-deep-interior-lighting` (existing working branch; no automatic branch switch)

**Created**: 2026-10-08

**Status**: Storm-only architecture prototype under native validation; realism/performance acceptance open; not production-approved

**Input**: Replace the current cloud-shape authoring contract with richer type-specific morphology, preserve renderer infrastructure, connect existing shape controls to rendered geometry, and require recognisable flat-lit silhouettes across views and seeds before lighting polish. No automatic merge/push; keep PAchangelog.md uncommitted.

## Delivery boundary — T255 first phase

The subsequent maintainer instruction requires report, exhaustive field audit, proposed contract/storage design, migration and an **opt-in storm-only prototype** first. Do not implement all families or productionize yet. Report: `specs/001-native-storm-rendering/validation/t255-cloud-morphology-architecture.md`. Storm evidence requires three seeds, identical scale framing, side/opposite_side/front/back/above/below/low_angle/far/close plus family-specific views and measured 10 ms renderer feasibility. Stop for approval before productionization where choices have materially different complexity/performance, transport redesign is substantial, compatibility is expensive or unrelated systems must change. The requirements below preserve the full long-term goal; first-phase completion does not achieve it.

## User Scenarios & Testing

### User Story 1 - Recognisable cloud families and development stages (Priority: P1)

A player sees cumulus development stages, developing and mature thunderstorms, supercells, stratiform layers and high ice-cloud filaments whose structure distinguishes their type rather than merely their size or darkness.

**Why this priority**: Shape is the failure demonstrated by the 2026-10-08 real-photo review. Lighting changes cannot satisfy this story.

**Independent Test**: Compare flat-lit captures of all 11 registered native types at three fixed seeds, from side, opposite side, front, above and below, against credited real-cloud references and explicit morphology criteria.

**Acceptance Scenarios**:

1. **Given** humilis, mediocris and congestus, **When** viewed without directional lighting, **Then** their flat shared bases and progressively developed, connected cauliflower crowns are recognisable without exposed stacked discs or a handful of obvious attached balls.
2. **Given** calvus, capillatus and supercell, **When** viewed from multiple directions, **Then** a softened developing crown, fibrous spreading mature canopy, and sheared asymmetric organised storm respectively distinguish the variants; all remain coherent volumes without artificial waist divisions.
3. **Given** stratus, stratocumulus and nimbostratus, **When** viewed obliquely and from below, **Then** a uniform thin layer, rounded connected/broken cells or rolls, and thicker diffuse precipitation layer respectively are distinguishable.
4. **Given** cirrus, **When** viewed from below, **Then** long tapering, curved or branching translucent filaments appear instead of short repeated scalloped strips.
5. **Given** vapor_cluster, **When** viewed from multiple directions, **Then** small broken condensation fragments are distinguishable from mature humilis; a fractus reference remains explicitly labelled as a proxy.

### User Story 2 - Effective morphology authoring controls (Priority: P1)

An author changes the existing shape profile and sees the corresponding structural change in the rendered volume, reproducibly and without depending on camera orientation or quality tier.

**Why this priority**: The previous contract has stored controls with no geometric consumer.

**Independent Test**: At fixed seed and environmental state, vary each applicable shape control independently and measure a corresponding shape change; demonstrate representative changes in flat-lit in-game captures.

**Acceptance Scenarios**:

1. **Given** an applicable cloud family, **When** tilt, wind shear, splitting, narrowing, canopy spread, base flattening, edge raggedness, wall strength or lobe structure changes, **Then** the geometry changes in the named structural dimension rather than only in colour or density.
2. **Given** a fixed authoring definition and seed, **When** camera distance or rendering quality changes, **Then** authored identity and topology remain stable; resource limits do not silently replace the cloud with a different shape model.

### User Story 3 - Stable integration and honest verification (Priority: P1)

A player can save/reload, join a server and move between dimensions with consistent cloud shapes; the existing rendering and optional integrations continue working.

**Why this priority**: A richer authoring model must reach the actual renderer and must not become client-only persistent weather state.

**Independent Test**: Validate definition/program transport, persistence compatibility, deterministic reconstruction, bounds/culling, shared camera/light/shadow/probe density, and representative runtime transitions with the optional Simple Clouds dependency absent.

**Acceptance Scenarios**:

1. **Given** authoritative cloud state, **When** it is saved, loaded or synchronised, **Then** its morphology and seed are preserved or deliberately migrated without guessing roles from obsolete layouts.
2. **Given** cloud volume samples, **When** camera, sunlight, shadow or occupancy consumers query them, **Then** they use the same authoritative morphology, with documented conservative approximation where applicable.
3. **Given** incompatible or incomplete data, **When** encountered, **Then** bounded validation and explicit diagnostics prevent crashes and silent partial-shape truncation.

### Edge Cases

- Calm wind, strong wind/shear, minimum/maximum authoring values, malformed non-finite values.
- Legacy versioned/unversioned lobe groups, custom datapack types, evolution between cloud families, merging and dissipation.
- More visible clouds or overlapping primitives than a GPU page can contain; incomplete sync; reconnect and dimension change.
- Cameras inside a volume, thin filaments, far views, disabled temporal history and varying quality.
- Isolated layer captures cannot establish regional overcast coverage; regional coverage requires its own runtime scenario.

## Requirements

### Functional Requirements

- **FR-001**: Cover all 11 current native IDs, preserving their names and distinct meteorological intent; do not count enum capabilities or optional noise presets as extra native implementations.
- **FR-002**: Provide family-appropriate coherent volume structure: convective billow hierarchies, distinct developing/mature/sheared storms, stratiform surfaces/cellular layers, and curved tapering ice filaments.
- **FR-003**: Every existing shape-profile control MUST have a documented geometric consumer for its applicable families; non-applicable combinations MUST be explicit, not misleading silent controls.
- **FR-004**: Distinct storm variants MUST NOT reuse an identical fixed-size anatomy differentiated only by material scalars.
- **FR-005**: Authored structure MUST be independent of candidate-slot and visible-cell limits. Capacity handling MUST preserve identity and report explicit bounded behaviour rather than drop arbitrary parts or silently substitute generic geometry.
- **FR-006**: Retain existing raymarch/composite, atmosphere, lighting infrastructure and optional-integration boundaries; no unrelated renderer rewrite or lighting polish before morphology acceptance.
- **FR-007**: Preserve server ownership, deterministic seed-based reconstruction, stable motion and geometry across quality/LOD changes; define saved-data and packet compatibility explicitly.
- **FR-008**: Share morphology authority among primary, light, shadow and probe density consumers; validate bounds and acceleration conservatively.
- **FR-009**: Include numerical regression tests and flat-lit in-game evidence for all types, at minimum three seeds and five views per seed. Review those images against real references; numerical passing alone is insufficient.
- **FR-010**: Record pre-existing worktree changes and preserve them; do not automatically commit, merge or push. PAchangelog.md MUST remain uncommitted.
- **FR-011**: Provide reproducible captures, source/provenance identifiers, control-response tests and a requirement-by-requirement completion audit. Failures remain open work, not lowered acceptance thresholds.

### Key Entities

- **Morphology definition**: Type/family intent, authoring controls and versioned interpretation.
- **Morphology program**: Seeded coherent volume structure with family-appropriate elements and conservative bounds, owned by authoritative cloud state.
- **Presentation instance**: Immutable program placement and continuously evolving scale/material state, not independently simulated client weather.
- **Silhouette evidence**: Fixed seed/view/control capture, credited reference, provenance and explicit type-recognition review.

## Success Criteria

### Measurable Outcomes

- **SC-001**: All 11 native types pass documented qualitative morphology review on 15 flat-lit captures each (three seeds times five views); none depends on lighting to be recognisable.
- **SC-002**: All applicable controls show repeatable named structural effects in independent tests; representative in-game control comparisons confirm transport-to-render behaviour.
- **SC-003**: Three storm variants have distinct geometry at identical seed/environment, with connected silhouettes and no artificial waist/primitive seams in the required views.
- **SC-004**: Save/reload, sync, movement, dimension/reset, evolution, culling and capacity scenarios pass; no silent partial-volume drops or camera-dependent topology changes occur.
- **SC-005**: Existing relevant regression checks and a core build pass; representative in-game renders work without Simple Clouds and remain within a measured/documented rendering cost budget relative to the baseline.

## Assumptions

- Use procedural authored morphology; a full atmospheric fluid simulation is not required.
- The native renderer is the implementation scope. Optional compatibility presets and separate hurricane/tornado renderers retain their boundaries.
- Preserve existing authoring fields and native IDs; introduce explicit versioning where interpretation changes.
- The 2026-10-08 gallery is baseline evidence, not an acceptance gate already passed.
- The current shader and PAchangelog.md contain pre-existing user edits; record their hashes before touching overlapping files.
