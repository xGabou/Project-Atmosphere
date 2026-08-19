# Phase 4R Fail-First Validation

**Date**: 2026-08-18  
**Scope**: T074-T080 only; audited production implementation unchanged  
**Result**: PASS for the fail-first gate. All 15 isolated regressions failed against the audited implementation for the intended architectural reason.

## Execution

`./gradlew.bat testClasses --no-daemon` completed successfully.

`./gradlew.bat stormVolumetricGeometrySandbox --no-daemon` completed the full Phase 4R result collector and then exited non-zero as intended: **11/11 expected invariant failures captured**.

`./gradlew.bat volumetricStabilityDiagnosticsSandbox --no-daemon` completed the full Phase 4R result collector and then exited non-zero as intended: **4/4 expected invariant failures captured**.

The T076 fixture was compiled and executed as an independent GLSL 150 fragment shader in a hidden RGBA32F OpenGL framebuffer. It receives descriptor/probe data as uniforms and does not call `StormLobeEvaluator`, `ClientCloudVisualDensity`, the production fragment shader, or another Java equation delegate.

## T074-T078 Geometry and Agreement Regressions

### T074 storm silhouette

1. **Test name**: `T074 storm silhouette`
2. **Expected invariant**: For a fixed synthetic complete group, the tower cross-section is materially narrower than the base, the anvil is wider than the tower, each sampled section has exactly one connected component, adjacent-height radius changes are bounded, and no one-block vertical density step exceeds the continuity bound.
3. **Failed**: Yes.
4. **Exact failure reason**: `tower radius 96.751523 is not materially narrower than base 113.063367`. The measured tower/base ratio was approximately 0.856; the role-separated fixture requires at most 0.78. The remaining component, anvil-width, adjacent-radius, and vertical-step checks ran in the same test and did not need weakening to obtain this failure.
5. **Audited defect proved**: Statistical moment aggregation and `morphologyScale` retain nearly the same group ellipse through the tower band, so the descriptor-defined narrow tower is lost.
6. **Later implementation task**: T081-T085, principally T081 (per-descriptor field and smooth union), T084 (remove group ellipse/morphology scale), and T085 (GLSL mirror).

### T075 descriptor locality

1. **Test name**: `T075 descriptor locality`
2. **Expected invariant**: Adding, moving, or removing a descriptor whose support does not reach the probe leaves probe density bit-stable within `1e-9`.
3. **Failed**: Yes.
4. **Exact failure reason**: `add changed density 1.000000->0.999107; move outside support changed density 0.999107->0.979740; remove outside support changed density 0.999107->1.000000`.
5. **Audited defect proved**: `finishGroupEnvelope()` incorporates every same-group descriptor into global centers, moments, extents, and density even when that descriptor has no support at the probe.
6. **Later implementation task**: T081.

### T076 independent GLSL parity

1. **Test name**: `T076 independent GLSL parity`
2. **Expected invariant**: Java agrees with independently executed GLSL for BASE, CORE, TOWER, and ANVIL role evaluation; lobe-to-lobe smooth union; group-to-group smooth union; a local underside probe; and a boundary probe.
3. **Failed**: Yes.
4. **Exact failure reason**: Individual role equations agreed within the `0.002` float tolerance, but the architectural cases did not: `lobe union java=1.000000 glsl=0.795062; group union java=1.000000 glsl=0.804884; local underside java=0.067796 glsl=0.120492; boundary java=0.683085 glsl=0.638259`.
5. **Audited defect proved**: The current Java authority uses one statistical group envelope and alpha-style group composition rather than descriptor-local distance-like evaluation with lobe and group smooth unions; its underside and boundary therefore differ from the independent fixture.
6. **Later implementation task**: T081, T085, and T086. Reopened T032 must remain incomplete until this fixture passes after those fixes.

### T077 descriptor smooth-union composition

1. **Test name**: `T077 descriptor smooth-union composition`
2. **Expected invariant**: Two non-identical descriptors are evaluated independently; a point outside both bounded supports cannot become dense merely because their centers contribute to the same statistical moment envelope.
3. **Failed**: Yes.
4. **Exact failure reason**: Both individual descriptor densities were exactly zero at the gap probe, while the group result failed with `statistical group envelope filled a point outside both descriptor supports: density=1.000000`.
5. **Audited defect proved**: The current group ellipse invents cloud volume that no descriptor supports and therefore is not a true per-descriptor smooth union. This replaces the former duplicate-BASE same-function assertion.
6. **Later implementation task**: T081-T085, principally T081-T084.

### T078 rain and rendered-body agreement

1. **Test name**: `T078 rain and rendered-body agreement`
2. **Expected invariant**: Precipitation support comes from the exact rendered storm union, remains contained in that union, and uses the underside of the locally contributing BASE lobes for attachment.
3. **Failed**: Yes.
4. **Exact failure reason**: `visible statistical envelope and BASE-lobe rain union disagree at the same column: body=1.000000 rainSupport=0.000000`. The shader-source guard also confirms that `directStormRainSupportAt()` separately calls `directStormLobeSample()` for BASE roles instead of consuming the rendered union.
5. **Audited defect proved**: Visible geometry and precipitation are evaluated by different fields, so the statistical body can exist without any local BASE support or attachment source.
6. **Later implementation task**: T086 and T089.

## T079 Isolated Lifecycle and Acceleration Regressions

### Descriptor slot validity

1. **Test name**: `T079 descriptor slot validity`
2. **Expected invariant**: Every counted descriptor slot is a real descriptor; a missing live member is compacted or explicitly sentinel-marked and skipped.
3. **Failed**: Yes.
4. **Exact failure reason**: `missing selected member was zero-filled and decodes as group 0 BASE at origin; packed=0.0`.
5. **Audited defect proved**: `refreshLiveDescriptors()` clears a missing selected member to sixteen zeroes while `StormLobeCount` still counts the slot.
6. **Later implementation task**: T087.

### Incomplete group fallback

1. **Test name**: `T079 incomplete group fallback`
2. **Expected invariant**: Descriptor ownership is group-specific; an incomplete or omitted group falls back to `familyMacroShape` when appropriate instead of being hidden by the presence of some other descriptor.
3. **Failed**: Yes.
4. **Exact failure reason**: `directStormAvailable` is globally defined by `StormLobeCount > 0 && (directStormIndexed || stormProfile)`, so any counted descriptor plus the severe profile can suppress the macro fallback.
5. **Audited defect proved**: Fallback/ownership is global rather than tied to complete represented groups, allowing incomplete or omitted storms to disappear.
6. **Later implementation task**: T088.

### Rejected async build re-request

1. **Test name**: `T079 rejected async build re-request`
2. **Expected invariant**: Rejecting a completed build clears/preserves request state so the current grid signature is requested again.
3. **Failed**: Yes.
4. **Exact failure reason**: `rejected completed build returns without clearing the stale requested signature or re-requesting current geometry: if (!valid) { return; }`.
5. **Audited defect proved**: `requestedGridSignature` can continue to equal the desired signature after rejection even though no valid build for it was adopted or queued.
6. **Later implementation task**: T091.

### Cluster-only signatures

1. **Test name**: `T079 cluster-only signatures`
2. **Expected invariant**: Unrelated macro/LOD cloudlet changes do not affect severe-cluster grid or topology signatures.
3. **Failed**: Yes.
4. **Exact failure reason**: Adding an unrelated macro/LOD severe-shaped cell changed `grid=7664377135549979303->-3821525548775764897` and `topology=259830766777926108->5199535637401993927`.
5. **Audited defect proved**: Signature membership is based on severe role/family appearance, not the intended cluster-sourced storm-cell set, causing unnecessary rebuild and history invalidation.
6. **Later implementation task**: T092.

### Independent lifecycle generations

1. **Test name**: `T079 independent lifecycle generations`
2. **Expected invariant**: World, dimension, owner, and resource generations enter `VolumetricHistoryValidity.Key` independently.
3. **Failed**: Yes.
4. **Exact failure reason**: `nativeFrame accepts one lifecycle generation and copies it into world, dimension, owner, and resource fields`; the required six-generation factory signature does not exist.
5. **Audited defect proved**: A single coordinator lifecycle value is duplicated into four semantically independent key fields.
6. **Later implementation task**: T093.

### Same-frame history invalidation

1. **Test name**: `T079 same-frame history invalidation`
2. **Expected invariant**: A lifecycle/resource change invalidates history before the first frame under the changed state can select or composite the old history target.
3. **Failed**: Yes.
4. **Exact failure reason**: `resource reload defers history invalidation to the render-call queue, leaving a frame able to composite with the old key`; `onResourceReload()` has no immediate invalidation/pending-reset marker before `runOnRenderThread(...)`.
5. **Audited defect proved**: The queued resource release leaves a deferred reset window in which an old history key can remain eligible.
6. **Later implementation task**: T094.

### Candidate group witness coverage

1. **Test name**: `T079 candidate group witness coverage`
2. **Expected invariant**: A covered tile contains one conservative witness for every intersecting storm group, rather than consuming bounded candidate capacity with per-role/per-member duplicates.
3. **Failed**: Yes.
4. **Exact failure reason**: `candidate tile stored 8 descriptor witnesses for 2 intersecting groups instead of one witness per group` using two valid seven-member complete groups.
5. **Audited defect proved**: The restored acceleration structure does not yet exist at group granularity; the current candidate grid is descriptor-ranked and can exhaust capacity before representing all groups.
6. **Later implementation task**: T096.

### Candidate non-authority

1. **Test name**: `T079 candidate non-authority`
2. **Expected invariant**: Candidate/index results may skip irrelevant work but cannot decide whether descriptor geometry owns a sample or whether fallback density is used.
3. **Failed**: Yes.
4. **Exact failure reason**: `directStormAvailable` still contains `directStormIndexed`, so candidate/index coverage controls direct-storm ownership and fallback selection.
5. **Audited defect proved**: Although density evaluation later scans descriptors exhaustively, the candidate-derived flag can still change which authoritative geometry path supplies density.
6. **Later implementation task**: T096 (with group-specific fallback from T088).

### Bounded per-group intersection

1. **Test name**: `T079 bounded per-group intersection`
2. **Expected invariant**: Ray/segment coverage uses bounded per-group intersection records and does not scan all descriptor slots for every raymarch operation.
3. **Failed**: Yes.
4. **Exact failure reason**: `ray segment intersection scans every descriptor instead of bounded per-group geometry`; `directStormSegmentMayIntersect()` loops `descriptorIndex < MAX_STORM_LOBES` and has no `MAX_STORM_GROUPS` bound traversal.
5. **Audited defect proved**: The candidate grid has not been restored as a real group-level acceleration structure, leaving the full per-descriptor scan in the hot path.
6. **Later implementation task**: T096.

### `shaftDensity` maximum-precipitation argument

1. **Test name**: `T079 shaftDensity maxPrecipitation argument`
2. **Expected invariant**: `shaftDensity()` receives independent `maxPrecipitation` and `localPrecipitation` values and passes them in their correct positions to `rainEligible()`.
3. **Failed**: Yes.
4. **Exact failure reason**: `shaftDensity has no independent maxPrecipitation parameter and therefore passes localPrecipitation in both rainEligible positions`; the required seven-double signature does not exist.
5. **Audited defect proved**: The current method calls `rainEligible(localPrecipitation, localPrecipitation, localSupport)`, so the global maximum gate is not represented by its own argument.
6. **Later implementation task**: T095.

## Fail-First Gate

- Every new geometry regression demonstrably failed against the current audited implementation before its corresponding production fix.
- No assertion was weakened to manufacture a failure. The only test-input correction made during validation replaced incomplete four-member candidate groups with valid seven-member complete groups; the assertion remained unchanged.
- T081 and later remain blocked by this recorded T080 gate and were not implemented in this pass.
- No performance measurements or optimizations were performed.
