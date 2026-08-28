# Feature Specification: Native Storm Rendering

**Feature Branch**: `001-native-storm-rendering`

**Created**: 2026-08-17

**Status**: Revised — 2026-08-19 renderer-wide scale, material-continuity, and performance audit

**Revision**: The 2026-08-19 correction supersedes the earlier morphology acceptance model. The
previous criteria described only the *absence* of artifacts, so a smooth balloon-shaped storm could
satisfy every one of them while still failing the intended result. This revision adds positive,
measurable morphology requirements (FR-021 through FR-027, SC-011 through SC-017) and changes the
storm density architecture so that descriptor geometry is a bounded coverage envelope and the
volumetric noise field forms the visible storm body.

**Current correction gate**: T098 morphology tuning is paused. The native storm is now capable of
cloud-like multi-scale structure. Its physical scale was corrected by the accepted T134 severe-system
derivation; its lower and upper regions may still read as separate visual materials. No further role-local geometry correction may
be accepted until a representative vertical material trace attributes that separation to a specific
pipeline stage. Foundational, visually neutral performance architecture may proceed in parallel
with that measurement; quality reductions remain out of scope before T098/T099.

**Status 2026-08-21**: T119, T121, T122, and T123 are accepted from the controlled two-pass compact
fixture evidence: conservative rejections, exact avoided descriptor fetches, and actual
primary/light/termination workload counters executed in every view while fixture, pose, governor,
resolution, topology, and configured controls matched. This is not a historical pre/post timing
claim. **T134 is accepted**: the separately derived source-plan severe-system scale was implemented
and confirmed by controlled live SIDE/FAR/BELOW/ABOVE evidence on fixture
`66a15248-6262-441d-bc42-60e2d4e6b4e5` (fingerprint `16536fe1abb39ea0`, `descriptors=10`,
`height=865.31018`, `footprintDiameter=1238.61042`, compact topology, `structuralChanged=false`).

Because T134 changed the physical dimensions of every severe system, T132 has been rebased onto a
fresh post-T134 controlled reference and a fresh post-T134 material trace; the pre-T134 T130 and
T121--T123 fixtures are historical record only. SC-018's three reference viewing distances and the
aspect-ratio/ANVIL-span guards are carried into T133. T098 and T099 remain blocked by T133.

**Input**: User description: "Redesign Project Atmosphere's native severe-cloud rendering so storms are genuinely volumetric, visually stable, performant across quality modes, and compatible with the existing authoritative weather architecture and optional integrations."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Readable Volumetric Storms (Priority: P1)

As a player using Project Atmosphere's native clouds, I want severe storms to have connected,
natural-looking bases, rising cores and towers, and spreading anvils so that they read as coherent
weather systems rather than flat slabs, vertical walls, or disconnected shapes.

**Why this priority**: The current severe-storm silhouette is the main visible failure. It blocks
the native renderer from meeting the project's central visual goal even though the underlying
weather state and lighting systems are functioning.

**Independent Test**: Spawn or locate a severe native storm and inspect it from below, beside,
inside, and above at the documented near-horizon distances. The storm delivers value if it reads as
one large severe weather system, rather than a compact isolated object or two vertically stacked
cloud materials, while retaining the known artifact protections.

**Acceptance Scenarios**:

1. **Given** a synchronized severe storm with base, core, tower, and anvil stages, **When** the
   player views it from multiple elevations and directions, **Then** the stages form one readable
   convective system with a vertically developed tower and horizontally spreading anvil.
2. **Given** two or more members of the same severe storm overlap, **When** the player moves across
   their shared silhouette, **Then** the transition remains continuous without a hard seam or
   sudden change in height.
3. **Given** a severe storm evolves or moves with the weather, **When** its visible structure is
   updated, **Then** its stage ordering and connected silhouette are preserved.
4. **Given** a severe storm at normal viewing distance, **When** the player inspects its body and
   silhouette, **Then** the storm shows a broad continuous lower base, a dense convective core,
   vertical tower development rising out of that base, progressive vertical narrowing where the
   storm's development state calls for it, a broad upper anvil, and continuous transitions between
   all four regions.
5. **Given** any large occupied region of that storm, **When** its surface and interior are
   inspected, **Then** billowing and surface variation are visible at several distinct spatial
   scales, the silhouette curvature is irregular but coherent, and no part of the storm reads as a
   smooth balloon, a uniform-density mass, an identifiable ellipsoid or sphere, an isolated ear or
   bulb, a descriptor seam, a rectangular or vertical wall, or a flat slab.
6. **Given** a probe point well inside the storm body rather than near its edge, **When** the
   volumetric noise field changes and the storm's coverage envelope is held fixed, **Then** the
   visible density at that interior point changes, proving the interior is formed by noise rather
   than by envelope geometry alone.
7. **Given** a mature severe storm viewed from each documented several-hundred-block reference
   distance, **When** its horizon footprint, vertical span, aspect ratio, and role spans are
   measured, **Then** it meets the pre-derived severe-system scale target and visually dominates
   its surrounding horizon rather than reading as a compact cloudlet.
8. **Given** a vertical transect through a severe storm's lower base, core, tower, and anvil,
   **When** coverage, final density, noise, erosion, extinction, optical depth, and lighting are
   inspected together, **Then** the visual medium changes continuously or each measured transition
   is attributable to an intended physical cause rather than an unaccounted role boundary.

---

### User Story 2 - Stable Cloud and Rain Experience (Priority: P2)

As a player moving through severe weather, I want cloud density, whiteout, and rain effects to
remain spatially consistent and temporally stable so that the storm does not flicker, ghost, or
produce bright stippled bands disconnected from the cloud above.

**Why this priority**: Even a good silhouette fails in motion if precipitation and historical
frames create distracting artifacts or if the player is treated as inside a cloud that is not
visibly present.

**Independent Test**: Move the camera through, below, and around a raining severe storm, then hold
it stationary. The feature delivers value if visible density, whiteout, and rain remain aligned
and stable in both motion and stillness.

**Acceptance Scenarios**:

1. **Given** a locally raining portion of a severe cloud, **When** rain is viewed from below and
   beside the cloud, **Then** precipitation remains attached to that raining area without white
   dotted curtains or full-screen cloud work in unrelated clear areas.
2. **Given** the camera crosses a visible cloud boundary, **When** inside-cloud effects begin or
   end, **Then** the transition agrees with the density visible in the completed frame.
3. **Given** a stationary camera and stable weather state, **When** consecutive frames are
   observed, **Then** cloud edges and rain shafts do not shimmer, crawl, or accumulate ghosts.
4. **Given** a moving camera or a material change in storm structure or render quality, **When**
   prior-frame information is no longer valid, **Then** stale cloud silhouettes are not retained.

---

### User Story 3 - Scalable Quality Modes (Priority: P3)

As a player, I want every existing cloud quality mode to provide a predictable visual/performance
tradeoff, with Ultra remaining playable on the target laptop, so that I can select quality suited
to my hardware without severe storms causing extreme frame-time spikes.

**Why this priority**: The reproduced severe scene spends roughly 50 milliseconds on the cloud
pass and can reduce quality without recovering acceptable performance. The renderer must scale as
a complete system rather than relying on a single emergency reduction.

**Independent Test**: Run the same severe-weather scene in each quality mode and record visible
stability and frame timing. Each mode is independently successful if it stays within its intended
quality tier without persistent oscillation or catastrophic spikes.

**Acceptance Scenarios**:

1. **Given** any existing cloud quality mode, **When** a severe storm enters view, **Then** the
   renderer remains functional and preserves the essential connected storm silhouette.
2. **Given** sustained load above a mode's target, **When** adaptive quality responds, **Then** it
   reduces work gradually and later restores quality after sustained recovery without rapid
   back-and-forth changes.
3. **Given** the agreed Ultra validation environment, **When** the reproduced severe storm is
   observed after quality has converged, **Then** the game sustains at least 60 frames per second.

---

### User Story 4 - Actionable Renderer Diagnostics (Priority: P4)

As a maintainer diagnosing cloud regressions, I want bounded diagnostics that identify the active
renderer, severe structure, precipitation contribution, quality state, capacity limits, and frame
cost so that visual failures can be traced to the correct subsystem and verified in a real render.

**Why this priority**: Previous fixes were often assessed without checking the active renderer or
capturing a rendered result, allowing unreachable or incomplete paths to appear finished.

**Independent Test**: Reproduce a severe storm and request the renderer's status and diagnostic
views. The feature delivers value if the maintainer can distinguish structure, precipitation, and
final output and can detect capacity or performance fallback without enabling noisy normal logs.

**Acceptance Scenarios**:

1. **Given** the native renderer is active, **When** a maintainer requests storm diagnostics,
   **Then** the report identifies ownership, represented storm stages, capacity/overflow state,
   current quality state, camera-density state, and recent render cost.
2. **Given** a visible artifact, **When** the maintainer selects a bounded diagnostic view,
   **Then** cloud structure, precipitation, and final composition can be inspected independently.
3. **Given** normal gameplay with diagnostics unused, **When** storms render, **Then** diagnostic
   facilities do not produce sustained log noise or significant additional work.
4. **Given** a reported lower-to-upper material separation, **When** a maintainer captures the
   documented centre-line trace, **Then** the report identifies the first discontinuous rendering
   stage and distinguishes geometry, density/noise, optical depth, lighting, or sampling cause.

### Edge Cases

- The camera starts below, inside, or above the supported cloud altitude range.
- The camera crosses a cloud boundary while the storm moves or evolves.
- Several severe storms overlap in the same view or exceed the detailed representation capacity.
- A severe storm contains the minimum or maximum supported number of morphology members.
- A role is temporarily absent, duplicated, or received in a different ordering.
- Rain is globally active while the sampled view contains large clear regions.
- Quality changes while temporal history contains a frame produced at another resolution.
- The world, dimension, backend owner, resource pack, or render target changes.
- The scene contains terrain or another depth provider intersecting the cloud volume.
- Simple Clouds is installed, absent, or fails its optional compatibility checks.
- The native advanced renderer cannot initialize and the developer fallback is explicitly enabled.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The native renderer MUST present supported severe storms as connected structures with
  distinguishable base, core, tower, and anvil stages. Distinguishability MUST be demonstrated by
  the positive morphology requirements in FR-023, not only by the absence of the artifacts in
  FR-024.
- **FR-002**: Severe-cloud silhouettes MUST vary continuously in all three spatial dimensions and
  MUST NOT create full-height planar walls, rectangular cutoffs, or uniformly flat slab undersides
  at normal viewing distances.
- **FR-003**: The visible anvil MUST spread horizontally beyond the upper tower while retaining a
  curved top and underside appropriate to the storm's authoritative development state.
- **FR-004**: Overlapping members of the same storm MUST combine without winner-switch seams,
  abrupt height changes, or ordering-dependent visible results.
- **FR-005**: Storm movement and evolution MUST retain coherent stage ordering and connectivity.
- **FR-006**: Cloud erosion and lighting MUST preserve readable interior storm volume without
  producing solid black masses, textureless domes, or persistent punched-through holes. Preserving
  interior volume MUST NOT be achieved by suppressing detail inside the storm: erosion MUST remain
  active across the storm interior as required by FR-021 and FR-022.
- **FR-007**: Volumetric precipitation MUST be limited to locally raining cloud support and MUST
  not produce bright stippled vertical bands or force unrelated clear regions into dense cloud
  processing.
- **FR-008**: Visible cloud occupancy, camera density, whiteout, and inside-cloud effects MUST agree
  at sampled boundary points.
- **FR-009**: Stable scenes MUST remain temporally stable, while changes in world, dimension,
  renderer ownership, storm topology, or render resolution MUST not reuse invalid visual history.
- **FR-010**: The existing Low, Low 24, Medium, High, and Ultra quality choices MUST remain
  available and MUST preserve progressively increasing visual quality.
- **FR-011**: Adaptive quality MUST respond to sustained load and sustained recovery, MUST respect
  the selected mode's quality floor and ceiling, and MUST avoid frequent oscillation.
- **FR-012**: Ultra MUST sustain at least 60 frames per second after convergence in the defined
  target validation scene.
- **FR-013**: The renderer MUST expose on-demand diagnostics for ownership, storm-stage coverage,
  representation capacity and overflow, precipitation, camera density, quality state, and render
  timing.
- **FR-014**: The feature MUST continue deriving visible cloud mass from synchronized cloud fields
  backed by authoritative Project Atmosphere weather; it MUST NOT introduce another independently
  simulated cloud population.
- **FR-015**: Existing save compatibility, server authority, synchronization semantics, and severe
  weather gameplay ownership MUST be preserved.
- **FR-016**: When Simple Clouds is installed and selected by the existing ownership policy, it
  MUST continue to own cloud rendering and the native renderer MUST remain inactive.
- **FR-017**: The normal native development launch and the optional Simple Clouds development
  launch MUST both remain available.
- **FR-018**: The existing developer-only legacy renderer rollback window MUST not be removed by
  this feature; its eventual retirement remains a separate post-stability change.
- **FR-019**: Diagnostic and adaptive systems MUST not add unnecessary continuous logging,
  per-tick work, or allocation pressure during normal gameplay.
- **FR-020**: The feature MUST remain limited to native severe-cloud presentation, precipitation
  interaction, visual-density parity, quality adaptation, and their diagnostics.

- **FR-021**: Storm descriptor geometry MUST define a bounded coverage envelope only. It MUST NOT
  be the final visible storm density. The visible storm body inside that envelope MUST be formed by
  the volumetric noise field.
- **FR-022**: The storm density path MUST follow the ordered composition: bounded descriptor
  coverage envelope, then base volumetric noise remapping against that envelope, then multi-scale
  detail erosion, then final storm density. Density-shaping behavior that disables or floors most
  noise contribution across the storm interior - including any storm-specific edge-exposure or
  erosion-floor assumption that protects the interior from detail - MUST be removed.
- **FR-023**: The rendered storm MUST visibly contain all of the following: a broad continuous
  lower cloud base; a dense convective/core region; vertical tower development emerging naturally
  from that base; progressive vertical narrowing where the storm development state calls for it;
  a broad upper anvil; multi-scale billowing across the visible storm body; surface variation at
  multiple spatial frequencies; irregular but coherent silhouette curvature; and continuous
  transitions between base, tower, core, and anvil.
- **FR-024**: The rendered storm MUST NOT contain: large smooth balloon surfaces; large regions of
  visually uniform density; visible ellipsoid or sphere primitives; isolated ears or bulb
  protrusions; descriptor seams; rectangular or vertical walls; flat slabs; or uniformly smooth
  silhouettes.
- **FR-025**: Each descriptor lobe MUST expose a real signed or consistently scaled geometric
  distance field derived from its oriented, sheared, vertically profiled analytic volume. That
  field MUST remain valid outside the lobe surface, and lobes MUST NOT be discarded merely because
  their local density evaluates to zero. Density-space pseudo-distance - deriving the distance
  field as one minus a lobe's local density - MUST NOT be used. Smooth unions MUST operate in that
  geometric domain with blend distances expressed in world-space units or an explicitly documented
  equivalent.
- **FR-026**: FR-023 and FR-024 MUST have deterministic validation proxies wherever practical,
  including at minimum: a minimum density variance over sufficiently large occupied regions; a
  nonzero multi-scale noise contribution measured inside descriptor interiors rather than only near
  edges; measurable surface variation at each configured spatial frequency; and a check proving
  that interior storm samples respond to the volumetric noise field rather than to descriptor
  coverage alone. Every threshold MUST be derived from the documented rendering model and recorded
  with that derivation. Thresholds MUST NOT be chosen solely to make a test pass.
- **FR-027**: Storm descriptor evaluation cost MUST remain bounded per sample and per frame under
  the corrected density model, and descriptor group topology, lighting sampling, and descriptor
  texture access MUST be structured so that the corrected model is practical at the supported
  quality modes.
- **FR-028**: A mature severe storm MUST be specified and validated as a severe weather *system*,
  not a compact cloud object. Before changing dimensions, the feature MUST derive and record a
  physical-scale target covering total horizontal footprint, base/core/tower/anvil spans, total
  height, aspect ratio, descriptor count relative to occupied volume, and the relation of every
  noise wavelength to that target. The target MUST require clear horizon dominance at multiple
  documented viewing distances of several hundred blocks; uniform descriptor scaling without this
  analysis is prohibited.
- **FR-029**: BASE, CORE, TOWER, and ANVIL MUST present as one continuous volumetric medium.
  Their semantic roles may alter geometry but MUST NOT independently select a visual material. A
  representative vertical trace MUST record, at regular world-height intervals, active roles,
  coverage, final density, base noise, detail erosion, extinction, light optical depth, direct
  light, ambient light, and final rendered contribution. A material-continuity correction MUST be
  based on the first measured discontinuity in that trace, not on another unmeasured overlap or
  union-radius adjustment.
- **FR-030**: Foundational storm-performance architecture MAY proceed before T098/T099 only when
  it is visually neutral and retains the Phase 4S density composition and ownership boundaries.
  It MUST prioritize bounded group topology, descriptor admission/culling, empty-space rejection,
  reuse of already-computed envelope or density facts, bounded descriptor access, early ray
  termination, and an equivalent low-cost lighting-support path. Quality reductions or substitutions
  that change the rendered result remain separately validated work.
- **FR-031**: Physical-scale, material-continuity, morphology, rain/whiteout parity, and
  performance evidence MUST be revalidated together before T098 resumes. T099 remains blocked by
  the renewed T098 visual acceptance gate, and quality-mode work remains blocked by T099.

### Scope Boundaries

**In scope**:

- Native severe-cloud structure and silhouette.
- Severe-cloud overlap, evolution, precipitation, temporal stability, and camera-density parity.
- Existing quality-mode scalability and cloud-rendering diagnostics.
- Physical severe-storm scale derivation, vertical material-continuity attribution, and visually
  neutral storm-performance architecture required to make the corrected renderer practical.
- Regression protection for renderer ownership and optional Simple Clouds operation.

**Out of scope**:

- Redesigning atmospheric pressure, seasons, forecasting, cyclone generation, tornado gameplay,
  or other authoritative weather simulation.
- Creating a second weather or cloud population.
- Reworking ordinary cloud families except where shared behavior must remain compatible.
- Adding god rays, multiple independent altitude layers, or unrelated visual features.
- Porting the mod to another Minecraft version or loader.
- Retiring the entire legacy Field renderer before its agreed stable-release rollback window ends.
- Changing existing save or network formats unless later planning proves a compatible change is
  unavoidable and separately approved.
- Quality reductions, new quality-mode policy, or image-changing performance substitutions before
  measured material-continuity evidence identifies and closes the current architecture gate.

### Key Entities

- **Authoritative Cloud Field**: The synchronized render-mass derivation of persistent regional
  weather. It supplies storm identity, motion, morphology, development, precipitation, and
  lifecycle without becoming a second simulation.
- **Severe Storm Structure**: One coherent visual storm composed of role-bearing members that
  collectively form its base, core, tower, and anvil.
- **Storm Stage**: A semantic part of a severe structure with a distinct visual responsibility and
  an ordering relationship to the other stages.
- **Visual Density State**: The cloud occupancy successfully presented in the completed frame and
  used by client-side whiteout and inside-cloud effects.
- **Quality Mode**: A player-selected visual/performance tier with a bounded adaptive operating
  range.
- **Storm Diagnostic Capture**: An on-demand snapshot of ownership, structure, precipitation,
  capacity, quality, visual-density, and performance facts for one rendered scene.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across the below, beside, inside, and above validation views, zero reviewed frames
  contain the reproduced vertical wall, rectangular cutoff, flat slab underside, hard overlap seam,
  or white stippled rain-band defects.
- **SC-002**: In 100% of tested severe-storm lifecycle updates, the visible base, core, tower, and
  anvil remain correctly ordered and connected.
- **SC-003**: At every checkpoint in the boundary-crossing validation route, the reported
  inside-cloud/whiteout state agrees with visible cloud occupancy.
- **SC-004**: A stationary 60-second capture and a moving 60-second capture show no persistent
  ghost silhouette, crawling edge, or precipitation band after weather and quality settle.
- **SC-005**: All five quality modes complete the severe-storm validation route without rendering
  failure, and each higher mode retains at least as much visible storm detail as the mode below it.
- **SC-006**: On the plugged-in RTX 4070 laptop at 1920×1080, without an external shader pack and
  using the reproduced native severe-weather scene, Ultra maintains a 95th-percentile total frame
  time of 16.7 milliseconds or less during a ten-minute post-convergence run.
- **SC-007**: Adaptive quality performs no more than one quality transition in any 30-second stable
  workload interval and restores higher quality after sustained performance recovery.
- **SC-008**: Native-without-Simple-Clouds and Simple-Clouds-installed ownership tests both select
  the expected renderer in every tested launch and world-transition scenario.
- **SC-009**: Maintainers can identify the active renderer and isolate storm structure,
  precipitation, and final composition from one diagnostic session without enabling continuous
  debug logging.
- **SC-010**: Existing automated cloud topology, motion, synchronization, material stability,
  rendering stability, architecture-boundary, and build checks continue to pass.

- **SC-011**: In every reviewed severe-storm frame, an independent reviewer can identify all nine
  positive morphology features listed in FR-023, and none of the eight rejected forms listed in
  FR-024 is present.
- **SC-012**: Across sampled occupied storm regions large enough to contain several detail
  wavelengths, measured density variance meets or exceeds the documented minimum derived from the
  configured erosion strength and noise amplitude, in 100% of sampled regions.
- **SC-013**: For sampled probe points inside descriptor coverage - not only near the coverage
  boundary - the measured contribution of the volumetric noise field to final density is nonzero in
  100% of samples, and changing the noise field while holding the coverage envelope fixed changes
  the resulting density.
- **SC-014**: Measured surface variation is present at every configured spatial frequency band of
  the storm detail model, with no band contributing below its documented minimum share.
- **SC-015**: Each descriptor lobe's geometric distance field is finite, monotonic with world-space
  distance, and correctly signed or consistently scaled at sampled points inside, on, and outside
  its surface, and no sampled union result depends on whether a contributing lobe's local density
  evaluated to zero.
- **SC-016**: Every threshold used by SC-012, SC-013, and SC-014 has a recorded derivation from the
  rendering model in the feature validation documentation.
- **SC-017**: Storm descriptor evaluation stays within its documented per-sample and per-frame
  bound in the reference validation scene, measured with the existing storm diagnostics.
- **SC-018**: Before any physical-size correction is accepted, 100% of the documented severe-storm
  fixtures meet their recorded footprint, height, role-span, aspect-ratio, descriptor-density, and
  multi-scale wavelength targets at each of the three reference viewing distances.
- **SC-019**: The vertical material diagnostic samples the complete required field set at no more
  than 16-block intervals through every role transition, identifies any first discontinuity, and
  the corrected fixture has no unaccounted lower/upper material split in all reviewed FAR, SIDE,
  BELOW, and ABOVE captures.
- **SC-020**: Before T098 resumes, the reference viewpoint matrix records storm raymarch cost,
  primary/light-cone descriptor work, early-rejection rate, and termination behavior; every
  approved foundational optimization preserves the comparison image within its documented
  visually-neutral tolerance and reduces or bounds the measured work it owns.

## Assumptions

- The screenshot and live runtime data captured on 2026-08-17 are the primary regression fixture.
- `CloudRegionState` and `CloudClusterState` remain persistent weather truth; synchronized cloud
  fields remain the render-authoritative derivation.
- The advanced native volumetric renderer is the target when Simple Clouds is absent; Simple
  Clouds continues to win ownership when installed under the current policy.
- Existing morphology membership already carries enough authoritative identity to describe severe
  storm stages without inventing a new weather population.
- The performance reference environment is a plugged-in RTX 4070 laptop, 1920×1080, no external
  shader pack, native renderer, and approximately 2000 blocks of cloud visibility.
- Performance validation uses a controlled world and repeatable severe storm so unrelated game or
  mod load does not invalidate comparisons.
- The severe-system physical targets and pre-T098 performance budget are derived from the current
  live fixture and the reference-horizon validation route before production constants are changed.
- Existing unrelated working-tree changes are preserved throughout this feature.

## Obsolete Assumptions (superseded 2026-08-19)

The following assumptions were held by the pre-correction design and are no longer valid:

- **Descriptor geometry is the visible storm body.** Superseded: descriptors now bound a coverage
  envelope only; the noise field forms the body (FR-021).
- **A dense descriptor interior should be protected from detail erosion.** Superseded: interior
  protection is what produced the smooth balloon result. Erosion applies across the interior
  (FR-022, FR-006).
- **A lobe's local density can stand in for its distance to the lobe surface.** Superseded: a real
  geometric distance field is required and must remain valid outside the surface (FR-025).
- **A lobe that evaluates to zero density contributes nothing and can be skipped.** Superseded:
  zero local density does not mean the lobe is irrelevant to a smooth union (FR-025).
- **Smooth-union blend radii can be expressed in density units.** Superseded: blend distances are
  world-space (FR-025).
- **Absence of the known artifacts is sufficient morphology acceptance.** Superseded: positive,
  measurable morphology is required (FR-023, FR-026, SC-011 through SC-016).
- **No performance work may begin until final morphology validation passes.** Superseded:
  correctness work required by the corrected density model, including architectural work needed to
  make that model practical, is no longer blocked by the morphology validation gate. Performance
  changes remain separated from visual-correctness changes.
- **Role-envelope overlap is sufficient evidence that one storm will read as one material.**
  Superseded: geometry continuity does not prove continuity through density formation, optical
  depth, or lighting; the complete vertical material trace is now required (FR-029).
- **The current few-hundred-block storm footprint is an adequate severe-storm scale.** Superseded:
  physical system scale, role spans, and wavelength relationships require an explicit derivation
  before another geometry adjustment (FR-028).
- **Repeated local role tuning is an acceptable way to resolve a visual seam.** Superseded:
  subsequent correction is gated on measured pipeline attribution (FR-029).
