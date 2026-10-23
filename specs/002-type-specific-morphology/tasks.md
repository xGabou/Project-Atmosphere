# Tasks: T255 storm morphology architecture prototype

Scope is the first-phase delivery boundary in spec.md, not the all-family goal. No commits, merges, pushes or production switch. Checkboxes record executed work, not intended success.

## Phase 1 — Audit and design

- [X] T001 Record existing shader/changelog edits and preserve them; document hashes in specs/001-native-storm-rendering/validation/t255-cloud-morphology-architecture.md.
- [X] T002 Map the complete production path and audit all 15 CloudShapeProfile fields in specs/001-native-storm-rendering/validation/t255-cloud-morphology-architecture.md.
- [X] T003 Define explicit geometry, transport alternatives, family models, migration and approval boundary in specs/002-type-specific-morphology/{plan,research,data-model}.md and contracts/morphology.md.

## Phase 2 — Foundation

- [X] T004 Verify prerequisites, read-only checklist gate and existing ignores; execute specs/002-type-specific-morphology/quickstart.md progressively.
- [X] T005 Add failing contract/anatomy/control tests in src/test/java/net/Gabou/projectatmosphere/clouds/morphology/StormMorphologyPrototypeSandbox.java and its task in build.gradle.
- [X] T006 Implement shape-only immutable recipe, scales, finite-input validation, deterministic reconstruction and conservative bounds in src/main/java/net/Gabou/projectatmosphere/clouds/morphology/StormMorphologyPrototype.java.

## Phase 3 — US1: distinct storm anatomy

- [X] T007 [US1] Implement developing crown, mature canopy/fibres and organised sheared supercell macro/meso models in src/main/java/net/Gabou/projectatmosphere/clouds/morphology/StormMorphologyPrototype.java; pass T005.
- [X] T008 [US1] Generate and inspect preliminary neutral CPU diagnostic views across three seeds in specs/001-native-storm-rendering/validation/t255-storm-prototype/; do not count these as native acceptance. V4: 90 views, signatures match native; diagnostics expose simplified anatomy.
- [X] T009 [US1] Add default-off native prototype adapter and shared GLSL evaluator in src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ and src/main/resources/assets/projectatmosphere/shaders/; preserve production fallback and user edits.
- [X] T010 [US1] Capture native flat-lit nine-view galleries plus storm-specific views with identical framing across three seeds in specs/001-native-storm-rendering/validation/t255-storm-prototype/; review against credited IRL references. V4: complete 90-view gallery plus eight control/attribution arms; framing passes, meteorological shape review FAILS. Checkbox records capture/review execution, not visual acceptance.

## Phase 4 — US2: effective controls

- [X] T011 [US2] Extend StormMorphologyPrototypeSandbox.java with independent geometry-response checks for tilt, wind shear, splitting, narrowing, wall and remaining applicable shape fields; document metadata/non-applicability.
- [X] T012 [US2] Capture native fixed-seed control arms in specs/001-native-storm-rendering/validation/t255-storm-prototype/; verify transport-to-kernel effect, not only stored descriptors. All six V4 arms captured/inspected with real GPU density parity; this is fixture texture transport, not production network migration.

## Phase 5 — US3: bounded integration and feasibility

- [ ] T013 [US3] Verify packed CPU/GPU parity, conservative bounds, whole-program residency, primary/probe/light/camera agreement and reset/fallback in src/test/java/net/Gabou/projectatmosphere/clouds/morphology/ and existing volumetric sandboxes; retain exact fallback where acceleration is unproven.
- [X] T014 [US3] Measure real renderer GPU median/p95/worst-view and cost attribution against 10 ms; record hardware, resolution and timer validity in specs/001-native-storm-rendering/validation/t255-cloud-morphology-architecture.md. Measurement complete: 3/90 required p95 failures, 4 maximum-sample failures; ceiling acceptance remains open.

## Phase 6 — Handoff, not productionization

- [X] T015 Run relevant legacy regressions/build and update evidence ledger/risks in specs/001-native-storm-rendering/validation/t255-cloud-morphology-architecture.md. Final stormMorphologyPrototypePreview/Sandbox/check/build pass; 89,617 assertions, 90 CPU diagnostics; no production switch.
- [ ] T016 Audit first-phase requirements, preserve PAchangelog.md uncommitted, verify no merge/push, and request architecture approval before productionization; leave incomplete gates explicitly open.

## Dependencies and execution

T001–T004 precede code. T005 must fail before T006/T007; T008 follows passing pure model. T009 needs approved-for-experiment recipe design, not production approval, and precedes native T010/T012/T013/T014. T011 can run after T007; T015/T016 follow all evidence gates. Same-file work is sequential. Read-only checks and captures can be batched; no additional implementation agents are required.

Migration after this phase (not tasks silently completed here): approved storm production migration, then cumulus, cirrus, stratiform; each repeats prototype → native flat gallery → multi-seed anatomy → GPU performance → approved productionization.

## T256 — Growth-defined storm body (experimental, supersedes shell refinement)

- [X] T256.1 Preserve T255 and changelog; document growth representation, independent canopy, merge, transport and review boundary in validation/t256-growth-defined-storm-body.md.
- [X] T256.2 Add failing body-ownership, attachment, canopy independence, multi-seed, bounds, control and envelope A/B tests before implementing the new pure model. Initial red compile is retained in t256-red.log; further control/anatomy coverage remains part of validation.
- [X] T256.3 Implement a compact hierarchy of variable-width rising paths whose union alone defines the body; independent swept canopy and branching ice; explicit macro/meso/micro scales. Structural implementation, NOT visual acceptance; 488,479 assertions pass.
- [X] T256.4 Inspect CPU diagnostics, then add isolated CPU/GPU-matched native fixtures, packing and specialized GLSL. Keep ordinary production unchanged. V1/V2 rejected; V3 full campaign finished; old CPU model/kernel hashes unchanged.
- [X] T256.5 Capture native flat-light 16 required views plus oblique across three seeds/type; compare T255 under identical framing and old-envelope A/B with identical growth programs. Complete: 153 multi-seed views, 18 matched comparison pairs; capture/review execution PASS, meteorological anatomy FAIL. Primitive leakage remains.
- [X] T256.6 Measure evaluations/counts/merge/canopy cost, compiler instruction/register evidence where available, and native ray/whole-hook p50/p95 against 10 ms. Measurement executed: 12/144 required p95 failures (13/153 including oblique), worst whole-hook p95 19.529 ms. Actual shader logical counts and component ablations recorded; hardware register/instruction counters UNAVAILABLE and remain an open coverage limitation. Ceiling acceptance FAIL.
- [X] T256.7 Run regression/build/parity/reset checks, record remaining defects and feasibility, and stop for architecture review without productionization, changelog edits, commit, merge or push. Final replay 193/193 signatures, new suite 488,479 assertions, old suite 89,617, check/build PASS; 43 native density-parity and 193 camera/framing checks plus interval/exact comparison/reset PASS. Rejected candidate documented; approval requested, no production switch.

Dependencies: T256.1 → .2 (red) → .3 (green + CPU inspection) → .4 → .5/.6 → .7. Tests and captures are evidence, not automatic anatomy approval. Existing unresolved T255 acceptance remains unresolved; it is the comparison baseline.

## Acceptance still open

Native V4 silhouettes differ, but the filled macro body, planar fan/shield boundaries and exposed ice fringe still fail meteorological anatomy review. A dominant-growth body with independent canopy/ice organisation needs another prototype, not coefficient tuning. Native 10 ms feasibility fails in three required views. T013 has bounds/residency, actual sampled GPU density, camera/dimension isolation and cleanup evidence, but production light-cache/shadow/transport agreement is not validated by flat lighting. T016 retains the pre-production architecture approval boundary. Completed execution tasks do not mean the first-phase contract or full all-family goal is achieved.

T256 removes envelope ownership and independently models canopy, but smooth streamtube/blade anatomy still fails multi-seed realism. Required whole-hook p95 fails in 12/144 views, including a 19.529 ms worst case. Removing ice still fails the close-supercell ceiling; hard merge saves little. T256 done conditions are NOT met. Stop for review before richer staged analytic growth or a substantial cached hierarchical field/storage redesign; neither is authorized or productionized. Hardware profiler evidence remains unavailable. PAchangelog.md hash is preserved and uncommitted; nothing committed, merged or pushed.

## T257 recovery — frozen Candidate B native visual review only

Scope: preserve rejected A and experimental B exactly; complete the interrupted 350-arm A/B campaign, not architecture redesign or Candidate C. No optimization, production edits, changelog edits or commits.

- [X] T257.R1 Inspect partial logs/harness and preserve 76 original arms (152 PNGs); freeze CPU A/B and GLSL hashes. Log shows an unattributed clean shutdown, not a proven harness timeout/crash; clipping is independently observed.
- [X] T257.R2 Add failing resume-selection and conservative A/B camera-framing tests; repair capture-only framing/selection without geometry edits. Initial red compile retained; 725 capture-only assertions and two gallery tests pass. Resume list selects 276 arms, retaining 74 valid originals and replacing the one clipped A/B pair.
- [X] T257.R3 Rerun 104 CPU geometry checks with no client active; validate capture repair and source hashes, then resume only missing/invalid pairs. Initial/final pre-launch builds pass, source freeze verifies 894 files unchanged; 276-arm resume launched as session 14156. No concurrent Gradle tasks while the client is active.
- [X] T257.R4 Complete all 19 existing views × three seeds × three types × A/B, plus retained traversal/control evidence. Resume completed 276 arms normally; final selection has 350 unique arms / 171 matching-camera anatomy pairs, zero missing/invalid or occupied border pixels. Original 76 arms preserved; 74 reused and clipped pair replaced. Historical lens logging limitation disclosed.
- [X] T257.R5 Use t257_gallery.py for unchanged individual raw files, labelled side-by-side images, per-type and overall contact sheets and navigable HTML; publish A/B geometry measurements, not ratio-based realism claims. `native/ab-v2-complete` exports 700 original PNG copies, 171 full pairs plus 171 shared-crop detail pairs, 14 sheets, 14 HTML pages and 18 A/B anatomy records. Completeness/link/preservation gates PASS.
- [X] T257.R6 Inspect native evidence, update t257-convective-growth-field.md with limitations/exit diagnosis/counts/parity, verify frozen sources/production/changelog and STOP for visual review. Post-client 104 geometry checks and 1,429 capture assertions / 352 recipe replays PASS; 16 B parity arms max error 0.0004892945, retained traversal exact; 894 protected files and 152 originals unchanged. Smooth billows, shield-like canopies, limited creases and shared banding/ring artifacts documented. A REJECTED, B EXPERIMENTAL / NOT ACCEPTED. No Candidate C, optimization, production change, changelog edit, commit, merge or push.

## T258 — Storm anatomy Candidate C (experimental, stop for visual review)

Scope: new independent Candidate C beside frozen A, B v2, B v3 and the D0-D3 evidence. Hierarchical growth (macro regions, major turrets, secondary turrets, micro) and a hierarchical canopy that move the actual boundary. Precomputed geometry plus occupancy-grid skipping, p95 under 10 ms. No production, lighting, rain or temporal change; no changelog edit, commit, merge or push; no Candidate D.

- [X] T258.1 Freeze baselines: plumbing byte snapshots, historical gallery tree hashes and the changelog hash (`validation/t258-storm-anatomy-candidate-c/frozen`).
- [X] T258.2 Candidate C CPU construction (macro/meso/micro hierarchy, hierarchical canopy, levels C0-C3) baked into a precomputed distance volume plus conservative occupancy grid; GLSL mirror and isolated fixture variants. Shared plumbing changes are additive only.
- [X] T258.3 CPU sandbox: determinism, conservative bounds and grid, CPU mirror of the GPU sampler, connectedness and topology across seeds, level nesting, B v3 fingerprints unchanged.
- [X] T258.4 Iterate the anatomy on native-camera CPU silhouettes (effective lens) before native runs; record rejected variants.
- [ ] T258.5 STOPPED by the user after 697/929 arms (captures kept, run/screenshots/t098). Native campaign: B v3 vs C0/C1/C2/C3 at identical cameras with the corrected effective lens, 3 types x 3 seeds x 19 views, native 4x close views, parity and traversal pairs, timing.
- [ ] T258.6 NOT RUN (candidate rejected). Diagnostics and gallery: silhouette displacement per layer, major turret count, valley depth, head overlap, canopy planform irregularity, canopy thickness variation, upper tower visibility, connectedness; versus B v3.
- [ ] T258.7 NOT WRITTEN (candidate rejected). Report t258-storm-anatomy-candidate-c.md, preservation checks, STOP for visual review.

**T258 outcome (14 Oct 2026): Candidate C REJECTED by the user on native screenshots** - "a rounded blob with a few protrusions, no convincing vertical growth"; "a horizontal oval with a vertical cylindrical extrusion"; not realistic cumulonimbus silhouettes. Code and partial captures are preserved, uncommitted. Performance was not the problem (C ray p95 0.4-2.9 ms, parity <= 4.9e-4, traversal exact). No further candidate until the reference study (T259) is approved.

## T259 — Reference-driven storm morphology study (no geometry changes)

- [X] T259.1 Collect >= 5 distinct real storms per category (calvus, capillatus, supercell) from freely licensed photographs, with attribution.
- [X] T259.2 Silhouette masks of the references; annotate anatomy (base, tower, turrets, crown, anvil, overshoot, flanking line, wall cloud).
- [X] T259.3 Normalised, view-matched comparison against B v3 and the rejected C; quantify the structural differences.
- [X] T259.4 Measurable target proportions and a proposed construction strategy; STOP for approval.

T259 result (14 Oct 2026): 22 masked references (7 calvus, 9 capillatus, 6 supercell) + 7 anatomy-only; robust gaps: calvus too narrow at base / too wide at crown (w85/wmax 0.33 ref vs 0.60 B v3), one-sided anvils (upwind overhang 0.30 H ref vs ~0 models), single-summit smooth crowns (3 summits, 2 notches ref vs ~1/0), plateau tops, uniform anvil thickness, bubbly instead of laminar supercell bases. Report validation/t259-reference-morphology.md; gallery validation/t259-reference-morphology/index.html. STOPPED for approval.

## T260 — Complete 3D convective-system architecture (strategy only, no geometry)

Authoring unit changed from one lofted storm silhouette (T259 §7, superseded) to a complete convective system: primary, secondary, daughter, flanking and associated cells; lower cloud mass; outflow; type features; attachment links. Baked into T258's volume as sparse bricks.

- [X] T260.0 Revised architecture documented: validation/t260-convective-system-architecture.md. STOPPED for approval.
- [ ] T260.1 Reference revision: system annotations (t260-system-annotations.json, T259 masks untouched), cell/system metric split, multi-view/above/time-series sources, O/M/L/U relationship table.
- [ ] T260.2 Performance spike: sparse-brick bake of shapeless placeholder components at full system extent; p95, memory, bake time vs 10 ms.
- [ ] T260.3 System recipe on CPU: organisation (gate B), then cell anatomy (gate A), on all 19 native-camera views.
- [ ] T260.4 Native fixture gallery; STOP for visual review.

### T260.1 (as approved 15 Oct 2026) — first complete 3D convective system: calvus only

The approved order supersedes the planned T260.1 to T260.4 above. The reference-revision step was not part of the approved T260.1 and is still open.

- [X] T260.1a Sparse-brick feasibility on shapeless placeholders.
  - Checks: addressing, packing, seams, conservative skipping, boxes, parity, traversal, texture reads, GPU p50/p95, bake time, memory, incremental updates.
  - Result: 35/35 CPU checks; native parity ≤ 4.9e-4; bit-exact upload; traversal pairs 0.0; worst spike ray p95 4.70 ms.
- [X] T260.1b Individual calvus growth cell: a 3D cluster of rising turrets (main leaning updraft, near-summit turrets, stepped outer turrets), exposure-guarded cauliflower lumps, soft crown.
- [X] T260.1c Complete calvus system: primary, 2 attached secondaries, irregular lower mass, a detached developing cell, 2–4 associated cumulus. Attachment groups (smooth) vs separate groups (hard union, 70-block clearance). Deterministic seed, one frozen volume for all cameras.
- [X] T260.1d Gates (CPU, `cpu/t260-gates.json`).
  - Gate B passes on 3/3 seeds.
  - Gate A passes on 2/3. Seed 20262741 fails multi-summit only: 2 of 4 distinct projections.
- [X] T260.1e Native gallery: 3 seeds × 13 views, isolated primary × 6 at the same cameras, z4 crown close-ups, component identification, top-down layout, 2400×1350 silhouettes, primary cross-sections, comparison with the T259 masks (`validation/t260-calvus-system/gallery/index.html`).
- [X] T260.1f Performance.
  - Static calvus: ray p95 ≤ 4.53 ms, renderer p95 ≤ 4.99 ms (exact traversal 5.54 / 5.99 ms).
  - 24.5–26.5 MB per system; bake 430–560 ms.
  - Developing system: 260/338 ms CPU per step on the render thread, 0.5–0.8 ms per 3 MB sub-upload; frames with an update reach 5.70 ms ray / 6.53 ms renderer p95. Needs an off-thread rebake, rate limiting and smaller dirty regions.
- [X] T260.1g Report `validation/t260-calvus-system-prototype.md`; preservation PASS. **STOPPED for visual review.** No capillatus or supercell, no production change, no changelog edit, no commit, merge or push.

### T260.2 — Calvus morphology refinement (A = T260.1 frozen, B = refinement)

- [X] T260.2a Freeze T260.1 as A: `calvusA(seed)` verbatim, with volume signatures matching T260.1 for 3 seeds (CPU and native); evidence hash-frozen (`validation/t260-calvus-refinement/frozen`).
- [X] T260.2b B recipe (`CalvusSystemB`):
  - turrets 15 % wider;
  - outer shoulders stand outside the near-summit ring and step down;
  - 3 near-summit turrets at about 120°;
  - no-neck rule (top thermals neither wander nor shrink);
  - crisp dome tops on primary and secondary cells;
  - clustered lower field, with cells in a triangle instead of a line.
- [X] T260.2c Gates. B passes A and B on 3/3 seeds; A failed Gate A on seed 20262741. The lower-mass streak is fixed (aspect 2.41 → 1.58).
- [X] T260.2d Native A/B campaign at identical cameras: 135 shots, plus 6 close recaptures after a close-camera-inside-cloud fix; z4 aim fixed.
  - B renderer p95 ≤ 4.85 ms (exact traversal 5.40 ms).
  - Parity ≤ 4.9e-4; 14/14 bit-exact uploads; traversal 0.0.
- [X] T260.2e Report `validation/t260-calvus-refinement.md` and gallery `validation/t260-calvus-refinement/gallery/index.html`. Preservation PASS. **STOPPED for visual review.**

### T260.3 — Complete calvus system layout (baseline = T260.2 B frozen, candidate = C)

- [X] T260.3a Freeze T260.2 B: `calvusB(seed)` = `CalvusSystemB` unchanged; signatures match T260.2 on CPU and natively; code and evidence hash-frozen (`validation/t260-calvus-layout/frozen`, chained to the T260.2/T260.1 freezes).
- [X] T260.3b C layout (`CalvusSystemC`, iteration C25; cell anatomy = B):
  - secondary 1 slightly overlapping the primary;
  - flank cell and far shoulder;
  - tail plus a forked low cell off secondary 1;
  - outer growth arc (3–5 cells) and an outer row of low cells, each joined to its nearest cell by turret overlap;
  - lower field on the line side, with small mounds for arc cells.
  - The isolated primary no longer depends on the layout.
- [X] T260.3c Crown study: no measurable cross-seed repetition; ±26° asymmetry gave no clear improvement, so the crown is unchanged.
- [X] T260.3d Gates on 6 seeds (3 frozen + 20265193, 20266607, 20268011): C passes A and B on 6/6; B fails Gate A on 20265193.
  - Ortho aspect 2.23 → 2.51; connected W/H 1.41 → 1.53 (photographs 2.43).
  - Lower-mass deficit 0.36 → 0.28.
  - Rejected: C12 (line capsule), C19 (no width), C20 (mound-joined arc, smooth lobes) and others.
- [X] T260.3e Native B/C campaign at identical cameras: 269 shots, plus a same-session developing-system B/C pair.
  - C static renderer p95 ≤ 5.33 ms (z4 5.80, exact traversal 7.81).
  - Parity ≤ 4.89e-4; all uploads bit-exact; traversal 0.0.
  - Developing-system arm with the synchronous rebake: over 10 ms in 3 of 8 C runs (max 12.51).
  - Diagnosis: the render-thread stall alone inflates GPU time about 3.5× (B 9.33 ms with no update).
  - Fix in the T260 harness: off-thread rebake (worker + volume lock). Over 15–23 chained steps: C 4.53–4.85 ms, B 2.52–3.25 ms; stall ≤ 2.3 ms per step; parity and readback pass.
- [X] T260.3e2 Broader alternative C26 (`calvusBroad`: broad growth regions).
  - Connected W/H 1.72; broadest views 1.95–3.02, all inside the photographic range.
  - Gates pass on 6/6; static ≤ 4.72 ms; parity and uploads pass.
  - Memory +29–47 %.
  - **Fails the fast/exact traversal pair** (0.074, inside the base-streak band only). This proves the streak artefact is march aliasing. Kept as an alternative; `validation/t260-calvus-layout/broad-c26/`.
- [X] T260.3f Report `validation/t260-calvus-system-layout.md` and gallery `validation/t260-calvus-layout/gallery/index.html`. Preservation PASS. **STOPPED for visual review.**

**T260.3 decision (user, 20 Oct 2026):** C25 accepted as the new experimental baseline (not production). T260.1 A, T260.2 B, T260.3 C25 and the rejected C26 with its diagnostics are kept. The T260.3e2 statement that C26's failure "proves march aliasing" is superseded by T260.4: the failure was a renderer world-weather leak.

### T260.4 — Lower-cloud anatomy and artefact attribution (baseline = C25 frozen, candidate = D)

- [X] T260.4a Freeze C25 / C26 code, T260.3 evidence and report, plus pre-fix renderer copies (`validation/t260-lower-anatomy/frozen`, chained to the T260.3 → T255 freezes).
- [X] T260.4b Attribution:
  - CPU replica of the fixture march (`T2604Attribution`): exact, interval, lattice, converged volume and analytic field.
  - Native legacy-vs-fixed arms at identical cameras.
  - Result: the streaks, rings, slabs and fast/exact failure came from the production stratus relief driven by the live world's weather map (colour only; alpha identical). Not march aliasing, not sparse-brick sampling. The tongues are genuine geometry: lower-mass lobes plus outer-row spokes.
- [X] T260.4c Renderer fix, fixture- and T260-only, shader insertion-only:
  - no world relief on the fixture;
  - integer sample lattice;
  - interval box test at the sample.
  - Native proof: fast/exact bit-identical (0/3.24 M channels) for C25, D and C26 on 6/6 seeds. The T260.3 renderer fails on 5/6 for both C25 and C26.
- [X] T260.4d Candidate D (`CalvusSystemD`, `calvusD`): lower-mass mounds stay under their cell and lean inward; outer-row cells fill arc notches. Same draws, no added primitives.
  - Shelves 0.02–0.08 → 0; exposed lower mass 0.07–0.20 → 0.01–0.06; low-end max ≤ 0.29 (C25 0.48); low-footprint convexity 0.237 → 0.189.
  - Connected W/H 1.53 → 1.48; ortho aspect 2.51 → 2.34.
  - Gates pass on 6/6; isolated-primary signatures equal C25's.
- [X] T260.4e Native session (259 shots, 10 views × 6 seeds × {C25 legacy renderer, C25 fixed, D}, pairs, D developing chain).
  - D static ≤ 4.77 ms (C25 6.10); developing chain ≤ 3.83 ms; parity ≤ 5.0e-4; uploads bit-exact; 35.9 MB.
  - Report `validation/t260-lower-cloud-anatomy.md`, gallery `validation/t260-lower-anatomy/gallery/index.html`. Preservation PASS. **STOPPED for visual approval.**

**T260.4 decision (user, 20 Oct 2026):** candidate D is the accepted experimental calvus baseline (not production).

### T260.5 — Controlled neutral volumetric lighting (geometry = D, frozen)

- [X] T260.5a Freeze D geometry, every density-defining source, the six D signatures and the T260.4 evidence. Pre-T260.5 lighting/harness copies are kept (`validation/t260-neutral-lighting/frozen`, chained to T260.4 and earlier).
- [X] T260.5b Fixture-only neutral volumetric lighting, PaDiagnosticLightingMode 20–25 (shader insertion-only under `PA_CONVECTIVE_SYSTEM_T260`; new fixture variant `..._convective_system_neutral`). The flat program is unchanged.
  - White sun at 35° elevation, single scattering, isotropic phase.
  - Self-shadowing and ambient visibility are marched through the fixture density with the march's own extinction.
  - Two-stream sky/ground ambient; one fixed exposure; no world data, rain or history.
  - Diagnostics: density, direct, ambient and sun optical depth. Mode 25 is a full-rate reference.
  - Contamination probe (bit 2): world maps are mirrored and world lighting uniforms scrambled.
  - Budget path: the light is baked per frozen system on the CPU (`NeutralLightVolume`, 10-block lattice, 0.14–0.64 s) and appended to the fixture texture (`NeutralLitProgram`). B runs in a constant-mode variant (`..._neutral_final`). The in-shader per-sample shortcuts reached 18 ms and were abandoned.
- [X] T260.5c Native session: 475 shots, none rejected.
  - Alpha A=B 114/114 (float hash); cameras 114/114.
  - Fast/exact under B bit-identical 6/6; contamination probes bit-identical 18/18; decomposition B = direct + ambient within 1/255.
  - Parity ≤ 5.0e-4; uploads bit-exact; D signatures 372/372.
  - Baked vs per-sample reference: mean 2.4/255.
  - Renderer p95: A ≤ 6.52 ms; B ≤ 7.77 ms (heavy-view pass ≤ 9.31 ms).
- [X] T260.5d Attribution: the visible structure is the geometry (a smooth bubble union with seams), shown by self-shadowing. The density field has no interior structure (12-block surface ramp, 1 inside); the lighting adds none. D reads as a pile of smooth balls.
  - Report `validation/t260-neutral-lighting.md`, gallery `validation/t260-neutral-lighting/gallery/index.html`. Preservation PASS. **STOPPED for visual review.**

### T260.6 — Convective volume reconstruction (baseline = D frozen, lighting = T260.5 frozen)

- [X] T260.6a Freeze D, every density-defining source, the sparse-brick renderer, the T260.5 lighting (bake and shader) and all prior evidence. Harness copies are kept (`validation/t260-volume-reconstruction/frozen`, chained to T260.5).
- [X] T260.6b Reconstruction framework `ConvectiveVolumeT2606`: every arm is a transform of D's recipe.
  - D's anatomy is recovered exactly (turrets, lump parents).
  - With all mechanisms off, it reproduces D's signatures bit for bit (and T260.5's lit signature).
  - Arms:
    - **A:** hierarchical, scale-relative blending.
    - **B:** asymmetric growth column per turret plus a Poisson-packed growth-dome population (two tiers).
    - **C:** interior density m(x) through the brick pseudo-distance, with no shader change.
  - CPU preview, checks and metrics: `T2606Preview`, `T2606Metrics`.
- [X] T260.6c Native session: 1208 shots plus 250 (heavy-view pass and 2 retakes). Six seeds × {D, A, B, C, ABC} × every T260.5 camera; diagnostics; fast/exact pairs; probes.
  - Cameras 114/114; alpha equal across lighting modes 336/336; decomposition within 1/255.
  - Parity ≤ 5.0e-4; uploads bit-exact; fast/exact 12/12; probes 7/7.
  - D arm = T260.5 D (228/228 signatures, frames within 1/255).
  - Volumetric p95: ABC ≤ 8.45 ms (D 7.89 ms; C 8.64 ms worst).
- [X] T260.6d Attribution:
  - **B removes the sphere stack:** columns plus overlapping domes, i.e. cauliflower.
  - **A:** hard seams −75 % alone, −18–24 % creases on top of B; valleys and gaps kept.
  - **C:** interior CV 0.017 → 0.18, visually an optical-depth softening only.
  - **ABC is the combined candidate.**
  - **Limits:**
    - uniform dome texture at whole-system scale;
    - the isolated primary reads as parallel columns;
    - ABC update about 12 s per growth step.
  - Report `validation/t260-convective-volume-reconstruction.md`, gallery `validation/t260-volume-reconstruction/gallery/index.html`. Preservation PASS. **STOPPED for visual review.**
- [X] T260.6e Validation gaps (user, 21 Oct 2026: B and ABC promising, not accepted; no new morphology).
  - **Exhaustive conservative-skip audit** (`T2606Audit`, every cell and sample of every skipped cell, via the bake's own evaluator).
    - Validated against the frozen auditor (D/A agree; a broken field is flagged identically: 841 = 841) and local vs global (0 code differences).
    - Found FULL-skip over-fills in AB/BC/ABC (1–9 interior cells per volume); EMPTY skips never failed.
    - Fixed with a stored-value floor at −60 blocks (no stored code changes; A/B/C bakes bit-identical). Final audit: 0 bad cells over 3.0 G samples.
  - **B vs ABC (+AB, BC) native session:** 1075 shots plus 2 retakes, every T260.5 view plus `far` and a 3 km player-lens `gameplay` view.
    - A is practically invisible beyond B (about 2/255). C is subtle (4–6/255: softer edges and bases).
    - Neither fixes the parallel columns or the uniform texture.
    - Fast/exact 24/24; probes 12/12; signatures 822/822 equal the audited bakes.
  - **CPU cost per candidate:**
    - Bakes: B 12.6–15.2 s, C 1.0–1.2 s, ABC 16.9–20.8 s; light 0.24–0.43 s.
    - Update per growth step: B 10–14 s, ABC 13–18 s (D 0.4 s). Explained by 2.1× region, about 15× per-cell cost (~100 k dome elements), 0.8 s full field rebuild, and C's probe.
  - **Structure:**
    - Parallel columns = D's shared lean (axes within 5–7.5°) + three near-identical crown-ring turrets + near-constant column radius (CV 0.06).
    - Uniform texture = dome size tied to the column radius (narrow factor), two fixed size classes, Poisson spacing, one shape and one upward axis bias.
  - **Recommendation:** B as the next experimental baseline to iterate from; not ABC; neither acceptable yet. **STOPPED for review.**

## T260.7 — Seed-driven natural variation (experimental, 21 Oct 2026)

- [X] T260.7a Quick-preview candidates on frozen B (CPU preview, 3 seeds × side / three-quarter / close-up): V1 irregular billows, V2 + growth activity, V3 + undercuts / column valleys; S system characters (COMPACT / DOMINANT / MULTICELL / WIDE / ASYMMETRIC); persistent per-slot billow streams; same-seed growth demo. Code: `ConvectiveVolumeT2607` (all switches off = frozen B signature), `SystemDiversityT2607`, `T2607Preview` (test sources). Gallery `validation/t260-seed-variation/`. User verdict: V2 leading candidate, not accepted.
- [X] T260.7R1 V2 + compound billows (0–3 seeded sub-lobes per billow fused by soft blends, half shear, rounder floor) + element- and turret-local relief (replaces V2's world-anchored relief).
- [X] T260.7R2 R1 + regional anatomy: per-turret regime (active / mature / weakening) and region scale, contrast-shaped activity, heavy-tailed but capped billow sizes, jittered spacing, large billows always compound, broad turret-local swells on mature / weakening turrets.
- [X] T260.7R3 R2 + per-turret tower/crown transform (width profile, curved lean, non-primary height factor, asymmetric crown dome), keyed by turret identity.
- [X] T260.7R4 Quick gallery V2 | R1 | R2 | R3 (3 seeds × side / three-quarter / close-up / gameplay) and same-seed growth V2P vs R3P. **STOPPED for visual review; no winner selected; no Level-2 validation.**

## T260.8 Persistent convective growth and identity-preserving merging (experimental prototype)

- [X] T260.8a Persistent cell model `PersistentConvectionT2608` (test sources): cell specs with stable id/seed/potentials, growth column in absolute cell heights, fixed billow slots born by the rising top, pair attachment alpha, pair blend + connecting elements with pair identity, daughter cells, dominance/spread interaction, dissipation.
- [X] T260.8b Scenarios S1 single growth, S2 two-cell merge, S3 three-cell organisation (+2 seeds, + seed-driven `three_free` variant on 3 seeds), fixed domain/cameras, animations; smoke: determinism + translation invariance. Gallery `validation/t260-persistent-growth-merging/`. **STOPPED for visual review; nothing accepted; no production merging.**
- [X] T260.8c Review iteration 1 (foundation accepted, morphology not): `VARIANT` switch (0 = frozen T260.8); C1 multi-scale growth geometry (macro tower field, two-octave cell warp, billow-noise fine relief, per-cell condensation level) and C2 = C1 + persistent sub-thermals and subordinate lower masses; three_free x 3 seeds x 3 stages x 3 cameras + pair fixture; smoke determinism/translation/coarse-skip audit. Gallery `.../t260-persistent-growth-merging/review1/`. **STOPPED for visual review; no full validation.**
- [X] T260.8d Review 2 (C1 selected, C2 kept as reference only): C3 = `VARIANT 3` = C1 + crown development (collar + seeded lead/competing domes, broad masses, maturity-gated) + regional surface character (strong/quiet regime field shifting activity, blends, fold depth, relief; billow size decoupled). three_free x 3 seeds x 3 stages x 3 cameras + pair fixture vs C1; smoke determinism/translation/coarse-skip audit/parity. Gallery `.../t260-persistent-growth-merging/review2/`. **STOPPED for visual review.**
- [X] T260.8e Review 3 (C3 not accepted, C1 stays baseline): C4 = `VARIANT 4` = C1 + organic upper-tower growth (seeded upper-growth lobes widen the upper trunk, set broad/narrow crown, lift the cap; no separate dome) + regional character with quiet = broad 150-block folds, never wider blends. C1 | C3 | C4 x 3 seeds x 2 stages x 4 views (incl. tower->crown transition); smoke determinism/translation/coarse-skip/parity. Gallery `.../t260-persistent-growth-merging/review3/`. **STOPPED for visual review.**

## T260.9 Natural storm-scale morphology (experimental)

- [X] T260.9a C4 accepted as experimental visual baseline (not production); C1/C3/C4 preserved. `SystemGrowthT2609`: seed -> environment (instability, shear, moisture, inhibition, wind) -> persistent cells initiated on mature cells' propagation flank, environment-set lifetimes, T260.8 merging; C4 cells. Fix: dissolving T260.9 cells thin past their interior (floor -60) and vanish when fully dissipated (was 10-41 wrong coarse skips). `T2609Preview world`: C4 vs T260.9, 4 seeds x 2 stages, side / three-quarter at one camera distance, ground gameplay view at 6 km. Gallery `validation/t260-storm-scale-morphology/`. **STOPPED for visual review.**

## T260.10 Environment-driven vertical development (experimental)

- [X] T260.10a T260.9 accepted as experimental baseline (not production). `VerticalGrowthT26010`: T260.9 environment, initiation streams and cell identities unchanged; documented experimental profile approximations (LCL base, equilibrium-level depth from instability, CAPE proxy, glaciation level) and an entraining-plume depth per cell (depth = Dn exp(-K 0.2/R Dn (0.3 + 0.7 dryness))): wide moist updrafts reach the equilibrium level, narrow / dry ones stop as congestus, inhibited ones stay shallow cumulus. Deep cells (opt-in `CellSpec` fields, all off for C1-C4 / T260.9): milder taper + plume spreading, competing sub-towers (`towers()`), tower-scale warp, width-scaled billow relief, shear tilt, calvus at the glaciation level, equilibrium-level ceiling, plume-limited junction daughters. Bake: deeper interior clamp (240) and Lipschitz 3.0 only with deep cells (interior coarse cells were never skippable: volume-bound bakes); dissolving deep cells erode with a radius-scaled floor and 0.45 rPot noise (the flat -60 floor drew a blocky iso-surface). Bake cost recorded as a limitation: up to 34 min / 2.4 GB for the 12 km system. `T26010Preview world`: T260.9 vs T260.10, 4 seeds x 2 stages, T260.9 gallery cameras + shared overview + ground player at 6 km, measured dimensions per image; sparse preview light. Gallery `validation/t260-vertical-development/`. **STOPPED for visual review.**

## Experimental interactive cloud preview (in-game, before T260.11)

- [X] T260.P1 In-game preview of the experimental candidates, OFF by default (`-PpaCloudPreview` / `PA_CLOUD_PREVIEW=1`). The T260.8-T260.10 model classes moved from test to main sources, contents unchanged (T260.9 parity `477687157d0f1411` re-checked). `CloudPreviewModels`: candidate (t10 / t9), seed, stage, configurable model-metre -> block conversion (default 0.085 both axes, proportions preserved), bake on the compressed lattice through a scaled field (no full-scale bake; 12 km system ~20 s instead of 34 min), size guard. `ExperimentalCloudPreview`: `/cloudpreview` client command (preset / spawn / view / scale / quality / base / regenerate / clear / info / hud), worker-thread bake, T260.5 baked neutral light through the existing fixture renderer, HUD dimensions, unattended screenshot demo (`-PpaCloudPreviewDemo`). Gates: fixture install + fixture shader registration accept the preview flag; T132 / T098 autorun markers stay inert in preview sessions. Limitation: the candidate never exceeds ~14 km (1236 blocks), so the 2500-5000 block large-system target is not reached at proportion-preserving scale. Evidence `specs/001-native-storm-rendering/validation/t260-cloud-preview/`. **STOPPED: preview functional, awaiting review.**

## T260.11 Minecraft-scale multicell organisation (experimental)

- [X] T260.11a T260.P1 preview accepted as the visual workflow. `MulticellSystemT26011`: a system = persistent convective groups, each an unchanged T260.10 cell cluster in a perturbed local environment (own seed, birth, position, 2-8 cells); seed environment (same as T260.10) sets the forcing extent (~9-35 km, rarely ~60), shear sets line vs cluster triggering on the outflow boundary, per-system flank bias, gaps kept (no group within 0.75 spacing), inhibited groups stay shallow, old groups weaken while new ones develop. Region-based volumes: every live group baked as its own region on a shared lattice, cached per group, then `SparseBrickVolume.union` (brick copy, min codes; 3-47 ms) for the single fixture texture; light lattice spacing scales with the assembly. Preview: candidate `t11`, stages to 150 min, group HUD + projected group frames, views ground300/800/1500 (from the footprint edge, terrain-clearance lift, never inside cloud), three_quarter, above; comparison demo t10 vs t11 on 4 seeds. Smoke: deterministic (cold re-bake), union vs monolithic min-field bake 0 / 28-of-1M differing samples (max 0.027, overlap trilinear ramp), 0 bad coarse skips, T260.9 + T260.10 parity. Limits: no shared lower mass between groups, ~3000-block ceiling, no GPU timing, one-texture union (no region-native shader path). Gallery `specs/001-native-storm-rendering/validation/t260-multicell-organization/`. **STOPPED for visual review.**

## T260.12 Organic multicell connections (experimental)

- [X] T260.12a T260.11 accepted as experimental baseline. `ConnectionsT26012`: persistent pair connections between neighbouring groups, strength integrated over system time from measured footprint gap, base compatibility (per-group LCL), relative maturity, moisture, shear, overlap state (merger vs bridge), seeded per-pair threshold (many pairs never connect); geometry = 2-7 shallow broad persistent C4 cells activating at their own strength levels (near the towers first) and dissipating when strength falls; cells duplicated into both neighbouring group scenarios (T260.8 pair merging), regions unsnapped (domains snapped) so duplicates coincide; per-group base offsets. Preview `t12` / `t12off`, connection HUD/markers, close view, demo with frozen per-seed cameras, beauty + one diagnostic capture, fixture-pass GPU timing. Smoke: deterministic, union vs monolithic 33/2.5M samples (max 0.026, none > 0.05), 0 bad skips; GPU t11 vs t12 within noise. Result: technically sound but VISUALLY WEAK at system scale (mostly mergers inside overlapping bases; bridges small vs towers). Gallery `specs/001-native-storm-rendering/validation/t260-organic-connections/`. **STOPPED for visual review.**

## T260.13 Organic lower cloud mass morphology (experimental)

- [X] T260.13a T260.12 not accepted (kept for comparison); T260.11 stays the reference. `LowerMassT26013`: dedicated lower-cloud-mass geometry (pseudo-distance: footprint grown from both groups' lower regions, uneven base, variable thickness with folds, own-scale billows) in three constructions - A broad warped mass with a carved bay, B 4-6 overlapping pads activating at their own strength levels (gaps), C two curved tongues from both groups growing at different rates and meeting off-centre. Inputs: T260.12 pair strength with maturity from LIVE cells; pair chosen once per system, roots fixed at formation (persistent growth / dissipation). PROTOTYPE OVERRIDE: base-compatibility term ignored (no separated 2609196 pair connects under full rules); pair G3-G6. Own region volume, field min(smin(L, groupA), smin(L, groupB)) (seamless at the region boundary; the chained smin bug that blended the towers was found by the overlap check and fixed). Checks: deterministic, union vs monolithic <= 158 / 2.5M samples, none > 0.05, 0 bad skips; lower-mass bake 3.6-4.8 s, 5-6.5 MB, group regions reused from cache; GPU within ~0.3 ms. Result: clear in the close view, subtle at system scale. Gallery `specs/001-native-storm-rendering/validation/t260-lower-cloud-mass/`. **STOPPED: three variants compared, no winner selected.**
