# T128–T131 Vertical Material Trace and Measured Correction

> **Supersession / attribution correction (2026-09-01).** Preserve the accepted historical T128
> capture as evidence for `directStormShape` descriptor/envelope material and the stage helper it
> actually executes. It is **not evidence for the complete production `cloudDensity` path** or a
> production raymarch conclusion: `cloudDensity` applies additional gates and terms that the old
> trace did not execute. The 2026-08-31 retraction in
> `validation/t098-manual-checklist.md` is authoritative for this limitation. Do not rewrite the
> accepted record; use full production-density or production-ray diagnostics for those claims.

## T128 deterministic trace

Fixture: live 3c039aa7 ten-member strengths.  
Line: X=0, Z=0.  
Range: Y=208..528.  
Interval: 16 blocks.

The stormMaterialContinuityDiagnosticsSandbox task records active roles, coverage, envelope
strength, normalized base field, detail FBM, pre-erosion body, erosion, final density, effective
CORE_FILL, descriptor height normalization, and every rendering branch that can be established by
the CPU field contract. Its record type reserves extinction, optical depth, direct/ambient/phase,
and final radiance for the matching shader-light capture.

The CPU stage trace is intentionally not a fake lighting calculation. The fragment implementation
must supply the lighting columns in a live capture before T098, since reproducing them on CPU would
not establish the renderer's optical behavior.

## T128 production-shader trace instrumentation

The production fragment shader now has an on-demand four-pass `storm_material_trace` mode. It
samples the requested world-space X/Z and no-more-than-16-block Y intervals using the exact
descriptor texture, candidate ownership, BaseNoiseSampler, DetailNoiseSampler, `stormBody`,
storm erosion, final-density multiplier, and the production optical-depth/lighting functions. It
does not use `StormFieldSampler` for any GPU column.

`/pa system volumetric diagnostics stormMaterialTrace <yStart> <yEnd>` starts the capture at the
player's selected centre X/Z. After four rendered frames,
`/pa system volumetric diagnostics stormMaterialTrace` prints raw shader values for role mask,
coverage, envelope strength, raw base carrier, body before/after continuity retention, erosion,
final density, direct height normalization, extinction, light optical depth, direct and ambient
lighting contributions, phase/shadow factor, final local radiance, and direct-ownership flag.
The diagnostic is active only for those four frames, uses no CPU lighting emulation, restores the
normal final view, and invalidates history on completion so diagnostic pixels cannot enter a
production history frame.

No live runtime trace has been captured in this repository session. T128 and T129 therefore remain
open: compilation proves the instrumentation is available, not that the production path has the
same Y256→Y272 response.

### Invalid capture and targeting correction

The first live attempt is invalid and is not T129 evidence. It used the command player's frozen
position `(1091.37610, -1015.53229)` as `StormTraceOrigin`, while the rendered storm's material
centroid was `(1095.945483, -915.232663)`: a roughly 100-block Z error. Consequently all requested
Y rows were outside the descriptor envelope.

The trace now resolves the nearest *complete group in the published production descriptor snapshot*
at request time, then freezes that group's topology-derived centre rather than the player's X/Z.
It prints requested player X/Z, full resolved group UUID, resolved group centre, a
strength-and-horizontal-area weighted production material centroid, their distance and derived
tolerance, and the complete group role mask. Acquisition rejects a topology-centre/material-centre
mismatch beyond `max(32 blocks, 35% of mean descriptor radius)`.

`groupRoleMask` is the role inventory of the complete selected group. `activeRoleMask` is now a
separate shader field; it is zero unless the selected point has positive descriptor-owned envelope
coverage. The former trace exported the smooth-union lobe-proximity mask (hence `11` with zero
coverage), which is not a contributing-role result and must not be used for T128/T129 attribution.

The first corrected-centre attempt still produced invalid rows because `StormTraceStage` and the
other trace uniforms were declared only in GLSL. The shader JSON did not register them, so the
renderer's safe uniform lookup left every pass at its stage-zero default. The repeated `0.74316`
carrier and `11` role value in all columns are therefore pass-decoding artifacts, not material
measurements. The five trace uniforms are now registered in
`cloud_atmosphere_volume.json`; a valid retry must show distinct stage payloads.

## Accepted T128/T129 production trace — 2026-08-19

The corrected capture resolved production group
`567e102c-4b94-4ef2-8e99-1426fdd34c2f` to `(1095.94556, -915.23267)`, matching the renderer's
reported field material centroid `(1095.945483, -915.232663)`. The topology-weighted material
centroid was `(1091.11694, -916.92401)`, only `5.11623` blocks from the resolved centre and within
the `48.81019`-block guard. This is valid production-shader evidence.

| Y | Coverage / strength | Raw carrier → normalized base field | Body before → after T131 | Erosion | Final density | Optical / radiance |
|---:|---|---|---|---:|---:|---|
| 240 | .88916 / .89063 | .78320 → .55176 | .68262 → .72461 | .24243 | .69531 | OD 6.60938, radiance .21094 |
| 256 | .89063 / .89063 | .74219 → **.12988** | **.38428 → .46631** | .23169 | **.33105** | OD 7.55078, radiance .20874 |
| 272 | .89063 / .89063 | .77637 → .47192 | .62598 → .67578 | .27417 | .55664 | OD 7.79688, radiance .20935 |

Coverage and envelope strength do not fall at Y256; erosion is slightly *lower* than at Y240; and
the final radiance is continuous within .00220. The first strong response is the Stage-5 normalized
base-field trough (`.55176 → .12988`), which causes the uncorrected body fall (`.68262 → .38428`).
The live GPU does not reproduce the CPU fixture's exact zero at Y272, but it confirms the same
responsible stage in the actual renderer at Y256. The retained narrow correction engages only the
measured BASE+CORE+TOWER overlap and restores .08203 body at the trough without flattening the
surrounding carrier variation. T131 is accepted as a measured Stage-5-only correction; no role
geometry, scale, noise, erosion, or global-density change was made.

## T129 first discontinuity

The largest role-transition-adjacent response in the uncorrected trace occurred between Y=256 and
Y=272:

| Y | Roles | Coverage | Strength | Base field | Detail FBM | Body | Erosion | Final density | CORE_FILL |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 256 | BASE, CORE | 0.83394 | 0.83394 | 0.70045 | 0.40272 | 0.78789 | 0.26280 | 0.52509 | 0.69344 |
| 272, before T131 | BASE, CORE, TOWER | 0.88288 | 0.88288 | **0.00000** | 0.49074 | 0.29241 | 0.22408 | **0.06834** | 0.60073 |

Coverage and envelope strength increase continuously across the handoff. The first abrupt response
is therefore stage 5: percentile-normalized base carrier reaches zero inside a deep, three-role
convective overlap. Stage 6 only subtracts its ordinary 0.224 erosion bite. This rules out a
geometry gap, union-radius shelf, strength normalization failure, or detail-erosion change as the
first field cause.

## T130 frozen reference baseline

T130 has a dedicated, read-only runtime fixture command. It resolves one complete published
descriptor group and freezes its group UUID/topology generation, centre, descriptor count, bounds,
and four deterministic group-relative poses. It refuses capture when the topology changes or the
camera is more than four blocks from the announced pose. The exact captured camera position and
yaw/pitch are retained as part of the result, while the requested face direction is always the
frozen group centre.

### T130 fixture-identity correction

`StormRenderSnapshot.topologyGeneration` is the async request generation, not structural identity.
The coordinator increments it when `StormLobeSpatialIndex.gridSignature(...)` changes. That
signature includes the camera-relative candidate-grid origin quantized to 16 blocks, so moving from
the fixture setup position to SIDE can republish the same descriptor group with a new request
generation. The reported 6 → 7 transition was therefore a candidate-grid rebuild/republish, not
proof that the group geometry changed.

T130 now keeps the group UUID check but validates a stable ordered structural fingerprint instead
of invalidating solely on that generation. The fingerprint includes group UUID; descriptor count
and published order; field/member identity and membership; group slot and role; X/Z centre;
base/top; major/minor radii; orientation/shear; effective density/detail weight; edge softness;
seed; lifecycle; and vertical development. Candidate-grid origin, request generation, upload
generation, material advection, history, and frame state are intentionally excluded.

Every capture prints `generationAtBegin`, `generationAtCapture`, both fingerprints, and
`structuralChanged`. A generation change with equal fingerprints continues. A changed fingerprint
invalidates the fixture and names the changed descriptor index plus structural fields, so a true
storm regeneration cannot enter the workload comparison unnoticed.

1. At the selected storm, run
   `/pa system volumetric diagnostics stormPerformanceBaseline begin`.
2. Move to each announced `SIDE`, `FAR`, `BELOW`, and `ABOVE` coordinate; face the reported group
   centre; then run `... stormPerformanceBaseline capture <view>`.
3. Remain fixed while eight **fresh timestamp-query** results and the existing eight-frame,
   fence-gated visual reference are acquired. Retrieve the cumulative report with
   `... stormPerformanceBaseline`.

Each completed view records group/centre, exact pose and facing, target dimensions/pixel workload,
configured primary and light steps, effective resolution scale, step scale/governor state, history
state/blend, and min/mean/max GPU ms from distinct timer-query completions. The visual reference is
the stable-frame capture's SHA-256 identifier, not an inferred screenshot description.

The current renderer state must be named explicitly in the capture: compact descriptor topology is
already present but **unaccepted**. Its direct group-boundary scans are zero and its topology
metadata cost is three bounded descriptor-texture reads per group evaluation. A legacy scan path is
not being silently treated as the baseline; T119 needs a separate controlled A/B measurement after
all four T130 view captures exist.

Actual primary ray steps, descriptor evaluations, descriptor texture fetches, lighting-density
evaluations, empty-space rejections, and early terminations are intentionally reported as
unavailable until their dedicated instrumentation work. No synthetic counts or the former
80/100/140/200+ ms observations qualify as this baseline.

### Accepted live T130 baseline — 2026-08-19

Fixture: `ce4ffed5-14f1-4b78-bec7-059c1985cedb`; structural fingerprint
`b018367ca17bc7d8`; target `641×360` (`230,760` pixels); `96` configured primary steps;
`6` configured light steps; history `true / 0.85`; compact topology (`0` group-boundary scans,
`3` bounded metadata reads per group evaluation). The structural fingerprint stayed unchanged for
all four captures.

| View | GPU ms min / mean / max | Governor | Resolution | Visual reference |
|---|---:|---:|---:|---|
| SIDE | 128.51508 / 132.70409 / 138.30861 | .50000 | .75000 | `bd3e82e9315cb38c` |
| FAR | 45.69293 / 49.51422 / 54.03546 | .50000 | .75000 | `4afdd85d03ea68a3` |
| BELOW | 67.36486 / 70.81398 / 74.37415 | .50000 | .75000 | `be40dffe53e91e97` |
| ABOVE | 117.01965 / 126.08872 / 133.27565 | .75000 | .75000 | `d4ea81479379a6db` |

This is the required reference for T119–T123. It is a compact-topology **before/after reference**,
not evidence that compact topology itself has been accepted over the legacy path; T119 must still
run an explicitly selected legacy-versus-compact A/B at these poses.

## T119 legacy-versus-compact A/B preparation

Compact topology remains the normal renderer (`StormTopologyMode=0`). The production shader now
contains an explicitly selected `legacy_scan` reference (`StormTopologyMode=1`) which recreates the
previous two bounded 64-slot descriptor-range scans for every admitted group. It uses the same
candidate witness, descriptor texture, ordered lobe range, smooth unions, noise, erosion, lighting,
and history policy; only range discovery differs. Switching modes invalidates history before the
next reference capture.

Use `/pa system volumetric debug stormTopology legacy_scan` and then `compact` for matching T130
fixtures. The baseline report records the active topology mode, `scans=legacy_two_64_slot_scans`
versus `scans=0`, and `metadataReadsPerGroup=scan_dependent` versus `3`. The deterministic
geometry sandbox proves compact `witness - memberIndex .. + memberCount` resolves the exact same
range as the legacy scan for every witness in a multi-group fixture; production shader compilation
remains the independent GLSL gate. Live SIDE/FAR/BELOW/ABOVE A/B timing and visual-reference
evidence is still required before T119 can be accepted.

## T131 correction

The cause is corrected in Stage 5 only. The remap now derives an additional fill requirement:

    required body = occupied silhouette density 0.10 + p05 erosion bite
    fill >= 1 / (coverage * (1 - required body)) - 1

It is applied only where all of the following are true:

1. BASE, CORE, and TOWER envelopes simultaneously cover the sample;
2. coverage is above 0.82 (smoothly engaged through 0.90); and
3. smooth-union envelope strength is above 0.84 (smoothly engaged through 0.87).

The ordinary live-strength CORE_FILL derivation remains authoritative everywhere else. Descriptor
strength still limits coverage; base-field sensitivity remains nonzero; no descriptor density is
normalized and no global density multiplier changed.

| Y | Final density before | Final density after | Why |
|---:|---:|---:|---|
| 256 | 0.52509 | 0.52509 | only BASE+CORE, so unchanged |
| 272 | 0.06834 | 0.15584 | BASE+CORE+TOWER overlap retains occupied mass despite zero carrier |
| 320 | 0.07033 | 0.07033 | only CORE+TOWER, so unchanged |
| 368 | 0.52473 | 0.52473 | CORE+TOWER+ANVIL, not the measured lower convective split |

The post-correction largest centre-line density step moves away from the role handoff to ordinary
carrier variation between Y336 and Y352. T124–T126 and the retained Phase 4S checks pass unchanged.

## T119 topology result

Descriptor metadata now packs group slot, member count, member index, and role in the existing
fourth texel. A candidate witness derives first/end descriptor indices in O(1); no sampler, texture
unit, candidate authority, descriptor geometry, or density equation changed.

| Work item | Before | After |
|---|---:|---:|
| Group-boundary loop iterations per group evaluation | up to 128 | 0 |
| Topology metadata texture lookups | scan-dependent | 3 bounded metadata reads |
| Descriptor range | inferred by scan | exact contiguous CPU-build range |

## Accepted T119 topology A/B — fingerprint `b018367ca17bc7d8`

The diagnostic legacy path and normal compact path use the same frozen structural fingerprint.
The deterministic range-equivalence sandbox independently proves that every packed witness's compact
`memberIndex/memberCount` range is exactly the range found by the legacy two-scan traversal. The
production shader compilation gate passed with both paths present. Since only range discovery differs,
this is rendered-output equivalence by construction: candidate ownership, descriptor order, geometry,
noise, erosion, lighting, and history inputs are unchanged.

| View | Legacy mean GPU ms | Compact mean GPU ms | Observed compact delta |
|---|---:|---:|---:|
| SIDE | 166.70064 | 132.70409 | -20.39% |
| FAR | 59.11185 | 49.51422 | -16.24% |
| BELOW | 85.89824 | 70.81398 | -17.56% |
| ABOVE | 107.80600 | 126.08872 | +16.96% |

The compact path eliminates the two legacy 64-slot group-boundary scans per admitted group
evaluation and uses exactly three bounded metadata reads. That workload reduction is proven. The
timing table is informative but **not** a defensible universal GPU-speed percentage: the supplied
legacy report does not include the pose/facing, governor, history settle state, or visual-reference
identifier needed to establish each pair as an identical camera workload. The opposite ABOVE result
confirms that this qualification matters. The later `c018d7107e452d06` capture is intentionally
excluded because its structural fingerprint differs.

T119 is accepted on topology correctness, exact range equivalence, and elimination of the repeated
scans—not on a fabricated all-view GPU improvement. Compact (`StormTopologyMode=0`) remains the
default production topology; `legacy_scan` remains diagnostic-only.

## T121 conservative vertical descriptor rejection

T121 adds no lighting proxy and no density approximation. Once a group has an exact accumulated
distance, a subsequent descriptor is omitted only when its exact role-specific vertical-cap lower
bound exceeds both its edge softness and `groupDistance + STORM_MAX_BLEND_BLOCKS`. The rounded lobe
SDF is mathematically never lower than that cap bound; therefore the descriptor cannot affect the
smooth union, active role mask, or local-height weighting. The ordered previous role/radius and
minimum radius are still updated, preserving the blend calculation for every later descriptor.

The new deterministic sandbox exhaustively verifies that lower-bound inequality over rounded-box
cap/wall cases and asserts the production shader uses the conservative maximum blend guard. It does
not change any density equation, sample position, noise stage, lighting path, T131 retention,
morphology parameter, or fallback/ownership behavior. No timing value is inferred from T130 or
T119. The per-view workload evidence was subsequently collected; see "Accepted T121--T123
controlled execution evidence" below.

## T122 exact descriptor-data reuse

The primary group loop now fetches each descriptor's four texels once and passes those registers to
the exact lobe SDF and edge-softness function. The former fetch-based public helpers remain as thin
wrappers for callers that do not already hold the descriptor. Thus the reused and recomputed paths
call the same `directStormLobeDistanceFromData` equation with identical values; no sample coordinate,
noise input, density equation, role state, or T131 condition changed. The deterministic geometry
sandbox rejects any group-loop return to the re-fetching helpers.

## T123 on-demand workload instrumentation

Two non-production debug frames now emit exact per-pixel integer counters, read back as float values
and summed over the target. The first frame records `primaryRaySteps`, `descriptorEvaluations`,
`descriptorTextureFetches`, and the deterministic T122-only
`avoidedDescriptorTextureFetches`: two reuses in `stormEdgeWidthBlocksFromData` and four reuses in
`directStormLobeDistanceFromData` only when those exact helper paths execute. The second records
`lightMarchDensityEvaluations`, `emptySpaceRejects`, `earlyTerminations`, and
`conservativeDescriptorRejects`. The latter increments solely at T121's vertical-cap branch that
actually skips an exact descriptor SDF; it is deliberately distinct from the primary-ray
`emptySpaceRejects` counter. This is executed shader work, not a CPU estimate. FINAL frames are
never read back and are not composited as workload output.

The existing early exits remain the only accepted bounded-work exits: primary integration ends below
transmittance `0.015`, whose maximum remaining premultiplied alpha is at most `0.015`; the in-slab
light march ends at optical depth `28`, where Beer-Lam transmittance is `exp(-28) ≈ 6.9e-13`.
T123 adds no new quality threshold or sample-position change. The per-view evidence for
T121–T123 was subsequently collected and all three are accepted; see "Accepted T121--T123
controlled execution evidence" below. The stated per-sample and per-frame descriptor evaluation
bound that FR-027/SC-017 require is documented in `plan.md` under "T123 documented descriptor
evaluation bound".

## Automated post-change capture

`stormPerformanceSuite` is a diagnostic-only client state machine; it produced the T121--T123
workload evidence and is the same mechanism T132 uses for its post-T134 reference. It forces the accepted compact topology, freezes one
`StormPerformanceBaseline` fixture (group UUID, structural fingerprint, exact ordered descriptor
structure, and fixed poses) at suite begin, and makes two passes, A then B, through SIDE, FAR,
BELOW, then ABOVE. It never resolves another nearby group after that point. At each exact fixture
pose it verifies the T130 four-block location tolerance (and a one-degree target-facing guard),
invalidates/settles history, waits for the actual governor scale to remain exactly `0.50000` for two
frames before the workload capture and again before GPU timing, captures the two workload frames,
settles FINAL frames, then captures the eight timestamp samples and fence-gated visual reference
through the existing baseline path. A bounded governor wait aborts with the observed scale if the
target cannot be reached.

It aborts on fixture disappearance, structural-fingerprint change, non-compact topology, pose/facing
failure, governor failure, workload failure, baseline failure, a pass-control mismatch, or a per-view
render-sample timeout. The final report contains PASS A, PASS B, deltas, and exact control-field
equality for every view, plus all eight executed-work counters. The individual `stormWorkload` and
`stormPerformanceBaseline` commands remain available unchanged for debugging. Deterministic sandbox
coverage pins two-pass view order, pose transition, governor gating, workload-before-baseline
ordering, mandatory workload/FINAL waits, structural invalidation, and completion only after all
eight valid view captures.

## Accepted T121--T123 controlled execution evidence — 2026-08-20

The completed two-pass `stormPerformanceSuite` used frozen compact fixture
`66b2c85a-aa93-4d18-b428-ac546e280c02` with structural fingerprint
`459873e8d8c8425a`. Every SIDE, FAR, BELOW, and ABOVE pair reported matching group/fingerprint,
exact pose, governor scale `0.50000`, resolution scale `0.75000`, target, configured ray/light
steps, history controls, compact topology, zero group-boundary scans, and three metadata reads.
`structuralChanged=false` for all eight captures.

`conservativeDescriptorRejects` (T121), `avoidedDescriptorTextureFetches` (T122), and
`earlyTerminations` (T123) were positive in every view of both passes. The complete primary-ray,
descriptor-evaluation, descriptor-fetch, light-density, empty-space, and termination counters were
read from the diagnostic shader frames. Geometry/material-continuity, retained Phase 4R and Phase
4S, production shader compilation, and build regressions were green.

T121 is accepted on its mathematical lower-bound proof, equivalence coverage, and observed
conservative rejection execution. T122 is accepted on exact same-data reuse, deterministic avoided
fetch counting, and numerical-equivalence coverage. T123 is accepted on valid actual workload
instrumentation and observed safe early termination. Small repeated-pass counter variation is
runtime temporal variation under matching fixture controls, not a control failure. No historical
pre-T121 workload percentage is asserted because those counters did not exist.

## T132 rebase notice — 2026-08-21

T134 is accepted and changed the physical dimensions of every severe system. The fixtures used
above can no longer be reproduced:

| Evidence | Fixture | Fingerprint | Scale |
|---|---|---|---|
| T130 frozen reference / T119 A/B | `ce4ffed5-14f1-4b78-bec7-059c1985cedb` | `b018367ca17bc7d8` | pre-T134 compact |
| T121--T123 controlled execution | `66b2c85a-aa93-4d18-b428-ac546e280c02` | `459873e8d8c8425a` | pre-T134 compact |
| T134 acceptance | `66a15248-6262-441d-bc42-60e2d4e6b4e5` | `16536fe1abb39ea0` | post-T134 severe |

The T130 visual references `bd3e82e9315cb38c`, `4afdd85d03ea68a3`, `be40dffe53e91e97`, and
`d4ea81479379a6db` describe compact-scale geometry. Comparing a post-T134 render against them
would report T134's intended effect as a performance regression, so they are **historical record
only** and are not a T132 comparison basis. Note also that the T130 ABOVE capture ran at governor
`.75000` while the accepted suite requires `0.50000` in every view; the fresh reference removes
that inconsistency.

The acceptance of T119 and T121--T123 stands on its own evidence: T121 rests on a lower-bound
proof that is independent of descriptor size (`stormLobeBlendRadius()` is
`clamp(..., STORM_MIN_BLEND_BLOCKS, STORM_MAX_BLEND_BLOCKS)`, so `blend <= 48` at any radius, and
`stormVerticalDistanceLowerBound()` is derived from the same `stormDescriptorVerticalBounds()` the
exact SDF uses), T122 on exact same-register reuse, T119 on the group-contiguous build ordering in
`StormLobeSpatialIndex.build()` plus `STABLE_IDENTITY_ORDER`, and T123 on counters gated to
`DebugView == 22 || DebugView == 23`. None of those arguments depends on the fixture's scale. What
T132 must now do is re-demonstrate visual and material neutrality on a post-T134 fixture. Its
revised criteria are in `tasks.md` under "T132 revised acceptance criteria".

A fresh T128-style centre-line material trace is required on that same post-T134 fixture. The
traces recorded above span Y `208..528` on the pre-T134 compact composition; the post-T134 severe
column is roughly 865 blocks (BASE underside at about `centre - 120` through the ANVIL canopy at
about `centre + 745`). The retained
`StormMaterialContinuityDiagnosticsSandbox` fixture is likewise pre-T134 compact geometry (BASE
radius `172`, ANVIL radius `206`, span Y `224..508`) and is not sufficient evidence for T132 item 6
on its own.

## T132 diagnostic reliability corrections - 2026-08-21

Two defects in the measurement apparatus were fixed before T132 evidence is collected. Neither
touches storm morphology, descriptor geometry, blend radii, density, erosion, lighting, T131, noise
scales or warp, severe-storm scale, rain/whiteout/camera density, or any production shader
behaviour; `git diff --stat` over the shader, `CloudMorphologyGenerators`, `StormLobeEvaluator`,
`StormDensityModel`, `StormMorphologyThresholds`, and `StormFieldSampler` is unchanged.

### 1. Stale workload capture could satisfy a later pass

`StormWorkloadRuntimeCapture.capture()` cleared `active` in its `catch (RuntimeException)` branch
but left `latestResult` holding the previous successful `WorkloadResult`.
`StormPerformanceSuite.collectWorkloadThenWaitForGovernor()` validated only that the result was
non-null and that its view **name** matched. Both passes visit the same four view names, so a
PASS A result could be recorded as PASS B. The 2026-08-21 suite report is consistent with this:
`above` PASS A and PASS B carried byte-identical counters across all eight fields while `gpuMs`
and `visualRef`, which come from `StormPerformanceBaseline`, both moved.

Freshness is now a monotonic capture token. `requestCapture(view)` returns the token the caller
must match, `WorkloadResult` carries it, `format()` prints it, a new request drops any completed
result, and a failed capture clears `latestResult`. Topology generation was deliberately **not**
used as the token: generation drift is allowed while the structural fingerprint is unchanged, so it
identifies neither a capture nor a fixture. `ViewCapture.controlDifferences()` additionally rejects
two passes that share a capture token. The acceptance predicate is
`StormPerformanceSuite.workloadFreshnessFailure(result, expectedToken, expectedView)`, kept pure so
the sandbox proves it headlessly.

### 2. `visualRef` equality could not serve as the neutrality comparator

See the revised T132 criterion 5 in `tasks.md`. The reference is now the raw `RGBA16F` cloud buffer
captured with temporal history bypassed, compared numerically against one storage ULP.

**Epsilon derivation.** The cloud targets are re-specified as `GL_RGBA16F`
(`VolumetricCloudRenderTargets.upgradeColorToRgba16f`) and read back as `GL_FLOAT`, a widening
conversion that is exact and adds no error. binary16 has a 10-bit stored mantissa, so adjacent
representable values at magnitude `m` are `2^(exponent(m) - 10)` apart, or `2^-24` below the normal
range. Two renders of an identical scene with identical uniforms and a pinned jitter phase should be
bit-identical; the epsilon therefore admits at most **one** representable storage step per channel
and nothing smaller can be expressed in the buffer at all. It is derived from the format and the
measured maximum magnitude, not chosen to make a comparison pass.

**History and jitter restoration.** `StormReferenceImageCapture.request()` saves
`VolumetricCloudDebugConfig.historyEnabled()`, disables it, and invalidates history; every terminal
path - completion, failure, and `cancel()` - restores the saved value and invalidates again.
`StormPerformanceSuite.abort()` calls `cancel()` unconditionally, so an aborted suite cannot leave
production temporal behaviour altered. Frames are only counted once `LastDrawInputs.historyValid()`
is false, so an accumulated frame can never be read back.

### Fail-first evidence

Both regressions were confirmed to fail against the pre-fix behaviour before being accepted:

| Reverted behaviour | Observed failure |
|---|---|
| Acceptance predicate restored to non-null plus view-name equality | `IllegalStateException: T132 freshness accepted a stale same-view workload result` |
| `historyBypassed` gate removed from the comparator | `IllegalStateException: T132 comparator evaluated a temporally accumulated reference` |

With both fixes present, `stormVolumetricGeometrySandbox` reports
`PHASE4T_RESULT|T132 workload capture freshness|PASSED` and
`PHASE4T_RESULT|T132 deterministic image neutrality|PASSED`.

## Post-T134 suite result and criterion-5 attribution - 2026-08-26

The rerun after the two diagnostic reliability corrections used fixture
`9fd69e80-0c33-41bb-873f-b17f3a6b7605`, structural fingerprint `5cc74e203b031c1a`,
`scaleEnvelope={baseTop=136.00000..1000.58551 height=864.58551 horizontalRadius=638.36494
footprintDiameter=1276.72989 descriptors=10}`. It completed with no aborts. Aspect ratio
`864.58551 / 1276.72989 = 0.677` sits inside the T127 0.55-0.70 band.

**Both corrections behaved as designed.** Capture tokens were `1,2,3,4` for PASS A and `5,6,7,8`
for PASS B - all distinct, correctly ordered, no stale reuse, and the token-reuse control never
tripped. Every reference reported `historyBypassed=true` and every comparison `evaluated=true`,
with `epsilon=4.882813e-04` in all four views. That is exactly `2^-11`, the binary16 step at the
measured `maxComparedMagnitude=0.9907227` (exponent `-1`), so the derivation is behaving as
specified.

**The deterministic comparator itself is proven to work.** The ABOVE view was bit-identical across
the two genuinely distinct captures - tokens `4` and `8`, informational digests both
`6a413a3212fa987e`, `maxAbsRGBA=0`, `changedPixelCountAboveEpsilon=0`, and all eight workload
counters equal - while `maxComparedMagnitude=0.99` confirms the frame carried full-range content
rather than being blank. This also disproves the earlier stale-read hypothesis for ABOVE: the
distinct tokens show both captures really executed.

| View | passed | maxAbsRGBA | meanAbsRGBA | rmsRGBA | changed / total |
|---|---|---:|---:|---:|---|
| SIDE | false | 9.907227e-01 | 1.107314e-01 | 2.478801e-01 | 103,048 / 230,760 |
| FAR | false | 9.902344e-01 | 9.515766e-03 | 7.642921e-02 | 7,331 / 230,760 |
| BELOW | false | 9.907227e-01 | 7.955284e-02 | 2.268721e-01 | 46,104 / 230,760 |
| ABOVE | **true** | 0.000000e+00 | 0.000000e+00 | 0.000000e+00 | 0 / 230,760 |

SIDE, FAR, and BELOW differed substantially. `maxAbsRGBA` near `0.99` means pixels moved
essentially full scale - cloud present versus absent - not numerical noise, and the counters agree
(SIDE `conservativeDescriptorRejects` `221,039,995` -> `276,341,942`, `primaryRaySteps`
`20,318,179` -> `24,483,360`).

**These differences are not yet attributed, and must not be read as a performance-path regression.**
Both passes run the same binary and the same performance paths; the suite has no before/after arm.
The likely cause is that the fixture is structurally frozen but materially alive: the structural
fingerprint intentionally excludes `MaterialOffset` and the per-tick descriptor runtime profile.

### Attribution instrumentation added 2026-08-26

`StormSceneStability` records exactly what the fingerprint excludes, and changes nothing about
advection, descriptor evolution, or the fingerprint. Nothing is frozen: this is an observation-only
step, and the next live run is intended specifically to prove which runtime fields move.

Each view now reports, in addition to `imageNeutrality={...}`:

- `advection={materialOffsetX=... materialOffsetZ=... runtimeProfileDigest=...}` per capture;
- `sceneStability={sceneStable, materialOffsetMatch, materialOffsetDeltaX, materialOffsetDeltaZ,
  runtimeProfileMatch, runtimeProfileDigestA, runtimeProfileDigestB, changedDescriptorCount,
  maxMajorRadiusDelta, maxMinorRadiusDelta, maxAspectDelta, maxShearDelta, maxDensityDelta,
  maxDetailWeightDelta, maxLifecycleDelta, maxVerticalDevelopmentDelta}`, plus the changed
  descriptor indices, roles, and old/new values when a mismatch occurs;
- `criterion5={imageNeutralityPassed=... criterion5Attributable=... reason=...}`.

Descriptors are compared identity-for-identity in published order (`fieldId`, `memberIndex`,
`role`), so a reordered or replaced descriptor is reported rather than silently diffed by position.
`StructuralFingerprint` is unchanged and `MaterialOffset` was deliberately **not** added to it.

Criterion 5 is **not accepted**. Re-run the suite; the next report will show whether SIDE, FAR, and
BELOW carry `sceneStable=false`, and if so, exactly which runtime fields moved.

## Criterion-5 attribution gap and WorldTime instrumentation - 2026-08-26

The controlled run on fixture `94be7046-0d20-42da-b1b7-9b2caf9f94be`
(fingerprint `bdf84a9cfb2643bd`, `baseTop=136.00000..999.46576`, height `863.46576`,
footprint `1291.19131`, descriptors `10`) passed criteria 1-4 cleanly, with capture tokens `1-8`
distinct, and reported `sceneStable=true` in all four views: material offsets `(0,0)` in every
capture, one runtime digest `30d602e62e8431f9`, `changedDescriptorCount=0/10`, and zero deltas on
all seven runtime fields. SIDE, FAR, and BELOW nevertheless failed image neutrality
(`maxAbsRGBA` `0.9907227 / 0.9902344 / 0.9907227`; changed pixels `107050 / 6852 / 42511` of
`230760`), while ABOVE was bit-identical.

That combination was reported as `criterion5Attributable=true
reason=rendering_neutrality_failure`. **That verdict was wrong**, because the attribution set was
incomplete rather than because the renderer moved the image.

`WorldTime` is uploaded to the production shader every frame and consumed by the precipitation
shaft domain at `cloud_atmosphere_volume.fsh:2508`:

```glsl
vec3 rainDomain = vec3(
    dot(sourceXZ, crossWind) * 0.012,
    p.y * 0.0014 - WorldTime * 0.0015,
    dot(sourceXZ, windDir) * 0.0035
);
```

and `VolumetricCloudRenderer` marks it render-relevant whenever
`weather.maxPrecipitation() > 0.02F || funnels > 0`, which a severe cumulonimbus satisfies. The
clock was neither pinned by the reference capture - the history bypass pins `FrameIndex`, not
`WorldTime` - nor recorded, nor compared. The session log shows it advancing
(`worldTime 1055681.125 -> 1055925.000`, about 244 ticks) while `lightDir=(0.88,0.46,0.12)` printed
as a single constant across 154 samples, so the clock, not the sun, is the live candidate.

`StormSceneStability` now tracks `WorldTime` conditionally and `LightDir` unconditionally alongside
the existing advection and runtime-profile comparisons, and the criterion-5 reason names the
differing inputs. Nothing is frozen: the clock, the rain animation, the lighting and the A/B
protocol are unchanged. `LastDrawInputs` gained `lightDirX/Y/Z` appended after every
signature-participating field, so no existing uniform signature moved.

**This hypothesis is not yet confirmed.** It requires one live run on a client built from these
changes. The falsifiable prediction: SIDE, FAR, and BELOW will report
`worldTimeAffectsDensity=true`, `worldTimeMatch=false`, `sceneStable=false`,
`criterion5Attributable=false differingInputs=worldTime`. ABOVE's behaviour is deliberately not
predicted - report whether its clock also drifts, since an identical image under a drifting
relevant clock would show the clock is globally render-relevant but immaterial to that view.

If the clock does **not** differ, the failure stays attributable and blocking, and the remaining
uncontrolled input must be identified rather than guessed.

## WorldTime hypothesis confirmed, and Option B implemented - 2026-08-26

The attribution run on fixture `94be7046-0d20-42da-b1b7-9b2caf9f94be` (fingerprint
`b88a0ce79b228585`, height `864.73602`, footprint `1336.74377`, descriptors `10`) confirmed the
hypothesis exactly. Every non-clock input matched: MaterialOffset delta `0/0`, runtime digest equal,
`changedDescriptorCount=0/10`, all runtime deltas zero, LightDir identical. `WorldTime` was the sole
unstable input, with `worldTimeAffectsDensity=true` and `worldTimeRelevant=true` everywhere:

| View | live WorldTime A -> B | delta |
|---|---|---:|
| SIDE | 1067297.375 -> 1067837.750 | 540.375 |
| FAR | 1067372.625 -> 1067913.750 | 541.125 |
| BELOW | 1067483.500 -> 1067998.625 | 515.125 |
| ABOVE | 1067669.500 -> 1068184.500 | 515.000 |

SIDE, FAR and BELOW failed image neutrality with `sceneStable=false` solely because of the clock.
ABOVE was bit-identical despite the same drift, so the clock is globally render-relevant but
immaterial to that view.

### Option B: diagnostic-only clock pinning

`StormReferenceImageCapture` now latches one `WorldTime` value for a whole suite and overrides the
value uploaded to the `WorldTime` uniform for reference frames only. The world clock, weather
progression, descriptor evolution, MaterialOffset and the rain equations are untouched, and
`cloud_atmosphere_volume.fsh` is unmodified. Ordinary frames before and after a capture upload the
live clock.

It composes with the existing history bypass: history off pins the jitter phase through the shader's
own `HistoryValid` branch, and the override pins the animation clock. Every terminal path -
completion, failure, cancellation, suite abort, and suite start - releases the override; the latched
value is discarded by `endSuitePinning()` so no later suite or session can inherit it.

`LastDrawInputs` now carries the effective clock in `worldTimeTicks`, plus `liveWorldTimeTicks` and
`worldTimePinned` appended after every signature-participating field. `StormSceneStability` judges
stability on the **effective** clock the reference frames rendered at, and still reports
`liveWorldTimeA/B/Delta` for auditability.

**Expected on the next run**: live clock differs, `effectiveWorldTimeMatch=true`,
`worldTimeRelevant=true`, `sceneStable=true`, and image neutrality passes in all four views. If any
view reports `sceneStable=true` while image neutrality still fails, that is a genuine remaining
determinism problem and must be reported, not hidden by loosening epsilon.

## Criterion-5 verdict correction and render-input attribution - 2026-08-26

The clock-pinned run on fixture `65774cbc-28ac-427e-8690-fe6cdbb592ee` (fingerprint
`ce4c22c8837d48e1`, height `863.80707`, footprint `1307.58477`) confirmed Option B works: the live
clock drifted 509-689 ticks per view while the effective reference clock held at `1085319.500`
everywhere, `unstableInputs=none`, and criterion 6 passed on the same fixture. SIDE, FAR and BELOW
nevertheless still failed image neutrality, and the suite reported
`criterion5Attributable=true reason=rendering_neutrality_failure`.

**That wording was wrong and has been removed.** It meant only *unattributable to the inputs then
tracked*, and that set was known to be incomplete: it covered MaterialOffset, the descriptor runtime
profile, the effective WorldTime and LightDir - four inputs out of the twenty named uniform groups
the renderer already hashes, and none of the sampler contents. Twice before, the same verdict turned
out to mean a missing input rather than a renderer defect.

Attribution now runs three levels, and the previously used phrase
`rendering_neutrality_failure` no longer exists. Reference frames retain the renderer's own
`comparisonUniformSignature` and the full `UniformComponentSignatures` record, read reflectively so
a group added later is compared automatically rather than silently omitted, plus
`CloudWeatherMapRenderer.lastInputSignatureForDiagnostics()`. Level C
(`unexplained_deterministic_render_difference`) is reached only when all of those match, and it
means the known deterministic inputs are exhausted - not that a defect is proven.

The primary remaining suspect is the weather map, which advects, regenerates on signature change,
and multiplies coverage at every coarse exterior sample. This is a hypothesis; the next run measures
it rather than assuming it.

## Projection falsified, cloud-content probe added - 2026-08-26

The projection-stability run on fixture `b82a87d1-8f59-4462-a1a4-c0c7f85129f0` (fingerprint
`dd3351e3e560c4fd`) established two things.

**The projection wait works but is not the answer.** BELOW observed 15 projection changes then
settled, and reached `projectionMatch=true` with `inverseProjectionMatch=true` - yet still failed
image neutrality (3,852 of 230,760 pixels, `maxAbsRGBA=0.9907227`). Projection mismatch is therefore
**not sufficient** to explain all failures. SIDE and FAR still show cross-pass projection differences
despite stabilizing cleanly within each capture (5 observed frames, 0 changes), so their FOV settles
to two different fixed points; that is reported and left unsolved here.

**`stormDarkening` is a bystander, not a cause.** Decoding the reported bits: BELOW
`968331820 -> 1` is `3.501339e-04 -> 1.401298e-45`, and ABOVE `658632596 -> 1` is
`2.691401e-15 -> 1.401298e-45`. The value is effectively zero in both passes, and it is a lighting
*multiplier* - it cannot produce a full-scale `0.99` pixel delta. Decisively, ABOVE carried the same
change and was bit-identical across all 230,760 pixels. Rain-level lighting was therefore **not**
pursued.

**The real gap: whole-frame cloud content was untracked.** `PuffLobeCount` and `StormLobeCount` are
uploaded at `VolumetricCloudRenderer:318-319` but appear in no signature, and `qualityFlags` does not
cover them. The fixture freezes one storm group's structural identity; other published storm groups
and the entire PUFF/cumulus family keep advecting and render through the same shader into the same
buffer. The view geometry fits: ABOVE looks straight down at the anvil with no foreign cloud between
camera and storm, while SIDE/FAR/BELOW look across open sky. BELOW's changed pixels also swung
`39,303 -> 3,852` between runs, which tracks how much foreign cloud was in shot rather than a fixed
defect.

`StormCloudContent` now captures, per reference frame, `puffLobeCount`, `puffDescriptorSignature`,
`puffCandidateSignature`, `stormLobeCount`, `stormDescriptorCount`, `stormGroupCount`,
`fixtureDescriptorCount`, `foreignStormDescriptorCount` and a whole-frame `stormContentSignature`
over every published descriptor in order. It is a separate signature from the fixture
`StructuralFingerprint`, which it does not replace. Criterion 5 gains a level between the
render-input check and the unexplained level:
`reason=cloud_content_changed_between_passes` with the differing categories named.

This is observation only; nothing is frozen. The hypothesis is untested until the next live run.

## Adjacent repeatability protocol added - 2026-08-27

The cloud-content probe on fixture `18a499f0-0cb8-4974-8e59-d074160507a3` came back
`differingCategories=none` in all four views - puff lobes `0/0`, one storm group, no foreign
descriptors - so foreign cloud content is **falsified**. BELOW then had every tracked input matching
except `stormDarkeningBits`, and still failed.

`stormDarkening` is now conclusively ruled out. It reaches the image through exactly one line,
`combinedStorm = saturate(max(StormDarkening * 0.58, localStorm * 0.72))` feeding
`mix(1.0, 0.76, combinedStorm)` - a smooth multiplier with no branch, dominated by `localStorm`
inside any cloud. The measured delta of `3.5e-4` propagates to about `4.9e-5` on a radiance
multiplier, an order of magnitude below the `4.88e-4` epsilon. ABOVE carried the identical
differing-input set and was bit-identical across 230,760 pixels.

BELOW'''s changed pixels across three runs were `39,303 -> 3,852 -> 1,656` while ABOVE was exactly
`0` every time. A deterministic renderer defect does not vary 24x run to run, and ABOVE is positive
evidence that the renderer repeats under the right conditions. Enumerating a sixth input was
therefore abandoned in favour of a decisive experiment.

**Two protocols now run side by side and are reported separately.**
`adjacentRepeatability_PASS_A` and `adjacentRepeatability_PASS_B` compare two references taken back
to back inside one settled window - same pose, no teleport, no pose setup, no intervening view, only
the interval the reference mechanism already requires. `existingSeparatedPassComparison` retains the
original cross-pass evidence unchanged. A difference in the adjacent pair is the renderer failing to
repeat itself; a difference only in the separated pair is protocol drift.

The suite state machine gains `REFERENCE_IMAGE_ADJACENT` between `REFERENCE_IMAGE` and
`GOVERNOR_FOR_BASELINE`, so the ordering guard proves the adjacent capture never re-enters pose
setup. Nothing new is frozen; history bypass, jitter pinning, the clock pin and the projection wait
are unchanged.

## Adjacent repeatability confirmed; residual intermittency is the blocker - 2026-08-27

Four autonomous live runs, driven by `StormT132AutoDriver` (test-only, armed by
`run/t132-autorun.txt`, which spawns the storm, runs the suite and takes the material trace without
keyboard automation).

**Protocol separation was the dominant cause, as hypothesised.** Once the reference pair is taken
back to back in one settled window, the previously enormous SIDE/FAR/BELOW differences vanish. Run 1
gave 7 of 8 adjacent pairs bit-identical; the single failure was BELOW's pair containing that
pose's first-ever capture. Discarding one warm-up capture per pose made runs 2 and 3 return 8 of 8
bit-identical. The separated-pass comparison for BELOW and ABOVE also began passing.

**A pose's first capture measures warm-up, not the renderer.** A chunk-readiness gate
(`levelRenderer.hasRenderedAllChunks()`) did not fix it - BELOW got worse, 4,889 -> 11,812 pixels -
so it is not terrain meshing. Discarding the first capture did fix it. The gate was retained as a
cheap correctness control.

**`stormDarkeningBits` is finally closed out.** Across these runs it differed in pairs that were
bit-identical (SIDE ADJ_B, ABOVE ADJ_A) and in one that was not, so it cannot be causal, matching the
earlier shader analysis: it reaches the image only through
`mix(1.0, 0.76, saturate(max(StormDarkening * 0.58, localStorm * 0.72)))`, a branch-free multiplier
whose measured delta propagates to about `4.9e-5`, below the `4.88e-4` epsilon.

**A real optimization A/B harness now exists.** `OPTIMIZATION_AB_OFF`/`OPTIMIZATION_AB_ON` take two
further captures in the same settled window with only the strategy toggled, and restore the
production default on completion and on abort. T119 was measured through its existing
`stormTopology legacy_scan|compact` toggle.

**Residual intermittency is the remaining blocker.** Across runs 3 and 4, roughly one capture in
eight shows a small difference that *moves*: run 3 failed at SIDE arm B with 53 of 230,760 pixels,
run 4 at BELOW arm A with 752, and run 4 also had one adjacent pair fail. A reproducible
optimization defect would land on the same view and arm every time; a moving, low-rate difference
does not. **Optimization neutrality cannot be measured through a capture mechanism that is itself
only about 90% reproducible**, so T119 must not be banked and T121/T122 toggles must not be built on
this foundation yet.

T132 remains open. Criterion 5 is closer than it has ever been - the large, systematic failures are
explained and eliminated - but a residual intermittent difference must be driven to zero first.

## Capture-stability gates and the T119 evidence status - 2026-08-27

**The separated-pass protocol is invalidated as criterion-5 evidence.** It compares two renders taken
minutes and three intervening views apart; projection/FOV settles to different fixed points and the
rain-level lighting drifts between them. Those differences are protocol drift, not renderer
behaviour, and the protocol cannot be repaired by pinning an open-ended list of inputs.

**The adjacent protocol removed the systematic drift.** Two references taken back to back in one
settled window are bit-identical where the separated pair differed by tens of thousands of pixels.

**A pose's first capture measures warm-up.** Only the pair containing a pose's first-ever capture
ever failed; discarding one warm-up capture per pose fixed it. A chunk-readiness gate did not (BELOW
went 4,889 -> 11,812 pixels), so it is not terrain meshing.

**A residual intermittent failure remained at roughly one capture in eight**, and it moved between
views and arms across runs rather than recurring in one place.

**The T119 A/B results from those runs are NOT accepted evidence.** Run 3 showed 53 of 230,760 pixels
at SIDE arm B; run 4 showed 752 at BELOW arm A. Those deltas are the same order as, and land in
different places than, the background capture noise, so they cannot distinguish a T119 neutrality
defect from capture instability. **T119 is not banked.** T121/T122 toggles remain blocked until the
noise floor is driven to zero, because a toggle measured through a mechanism with a non-zero error
rate produces evidence that looks rigorous and is not.

**Capture-stability gates added.** Before any reference frame is accepted, all of the puff candidate
signature, puff descriptor signature, puff lobe count, weather-map input signature, whole-frame storm
content signature and storm descriptor count must be unchanged for three consecutive rendered frames,
bounded at 600 frames and aborting with `content_stability_timeout`. Nothing is frozen; the capture
waits. Each capture reports `contentStability={...}` including which signature changed and how often.
The T119 A/B additionally discards one warm-up reference after each topology switch, because a switch
may need its own warm-up rather than inheriting the ordinary adjacent one.

## Pair-level content matching and the measured noise floor - 2026-08-27

The per-capture content gate was necessary but not sufficient. It proved each settle window was
internally stable (`changes=0 stabilized=true changedSignatures=none`) while the weather map still
moved *between* the two captures of a pair:

```text
capture 1  weatherMapSignature=8655a11e22e04af7
capture 2  weatherMapSignature=8655a11e22e04af7
capture 3  weatherMapSignature=ea38509c416f7247
```

The adjacent pair now additionally requires both captures to have rendered against the same
background content - identical weather-map input signature and identical whole-frame cloud content -
and retakes the pair, up to six attempts, when a regeneration lands mid-pair.

Measured across repeated fresh-client campaigns of five suites each, eight adjacent pairs per suite:

| Campaign | Gates | Unexplained A/A failures | Largest delta |
|---|---|---:|---|
| Pre-gate | warm-up discard only | 4 / 40 | 1,033 px |
| Content gate only | per-capture stability | 4 / 40 | 1,033 px |
| Pair matching | per-capture + pair content match | **1 / 40** | **1 px at maxAbs 5.42e-02** |

The residual is not a full silhouette flip: a single pixel differing by `0.054`, consistent with one
ray tipping a threshold under floating-point rounding. That is a bounded noise floor rather than an
unexplained systematic difference.

## T119 A/B campaign and why T119 is still not banked - 2026-08-27

Fifteen fresh-client suites were run autonomously by `StormT132AutoDriver` across three campaigns of
five, all four views, both passes.

| Campaign | A/A gating | A/A failures | T119 A/B arms | T119 failures |
|---|---|---:|---:|---:|
| `aa` | per-capture content gate | 4 / 40 | not run | - |
| `aa2` | + pair content match | 1 / 40 | not run | - |
| `ab` | + pair content match | 3 / 40 | 40 | 1 |

With pair matching in place the combined A/A background rate is **4 of 80 pairs (5%)**, and the
observed deltas are `1, 1, 73` and `1033` pixels - mostly a single pixel, occasionally a few dozen.
The single T119 A/B failure was SIDE arm B at **48 pixels**, in the same campaign whose A/A
background produced a **73 pixel** failure.

**T119 is therefore still not banked.** Its one observed delta is smaller than the background noise
in the very same campaign, so it cannot be distinguished from capture noise. The acceptance rule
requires zero unexplained A/A failures in the campaign that banks an optimization, and that
condition is not met.

T121 and T122 toggles remain blocked for the same reason: a toggle measured through a mechanism with
a 5% background failure rate and tens-of-pixels deltas cannot demonstrate neutrality at the
one-storage-ULP tolerance.

### The remaining residual looks like threshold sensitivity, not a content problem

Every tracked content input now matches within and across each pair. The surviving differences are
small and sparse - typically one pixel at `maxAbs 5.42e-02`, which is a fifth of full scale rather
than a silhouette flip. That is the signature of a single ray tipping a threshold under
floating-point rounding: the raymarch has hard decision points (the `transmittance < 0.015` early
termination and the `smoothstep` edges), and a ray that lands arbitrarily close to one can resolve
either way between two otherwise identical frames.

Eliminating it would mean changing how the production shader makes those decisions, which is a
production rendering change and outside diagnostic scope. **This is the stop point.**

## Repeated adjacent sampling: harness built, live campaign blocked - 2026-08-27

### Criterion-5 measurement rule (replaces individual-frame bit identity)

The production raymarch exhibits rare adjacent-frame threshold sensitivity even when every tracked
input matches. Measured campaign: 120 adjacent A/A pairs; after pair-content matching, 4 failures in
80 pairs; residuals typically a single pixel; worst residual in the final campaign 73 pixels.
**Individual-frame bit identity is therefore not a valid production neutrality criterion.**

The replacement criterion is the strict comparison of *robust repeated-sample median references*,
accompanied by raw production-noise dispersion and a same-window A/A control. **This is not a
relaxed image epsilon** - the tolerance remains one binary16 storage ULP. The median rejects a lone
outlying frame; it does not widen what counts as equal.

The threshold-insensitive shader proposal was explicitly rejected: disabling early termination or
hardening the smoothstep decisions would test a different renderer and make any neutrality result
less trustworthy.

### Implemented

`StormReferenceSampleSet` computes a per-component median over an odd sample count and reports raw
within-arm dispersion: sample count, pairwise max/mean/RMS, pairwise max changed pixels, the union of
pixels that varied, the maximum per-pixel range, and how many samples differ from their own median.
The suite's per-view flow became one `SAMPLING` state collecting four groups of
`SAMPLES_PER_ARM = 5` inside a single settled window - `A1`, `A2` as the local production-noise
control, then `OFF`, `ON` as the optimization arms - each preceded by a discarded warm-up, with the
topology toggle applied per group and restored afterwards. Every sample in a group must match the
group's first sample on weather-map signature, whole-frame cloud content, camera position and
projection, so a drifting pose or a background regeneration cannot pass unnoticed.

Automated coverage proves the median rejects a single outlying sample while the dispersion still
surfaces it, that an identical arm reports zero dispersion, and that a systematic shift across all
samples still fails the median comparison. Eight T132 checks pass, plus `check` and `build`.

### Live campaign did not complete

Five consecutive campaign attempts each hit a different test-environment blocker, and each fix
revealed the next:

| Attempt | Blocker | Fix |
|---|---|---|
| 1 | Player fell 998 blocks during the longer sampling phase | Driver sets spectator mode |
| 2 | Fixture invalidated by structural change mid-run | One pass instead of two |
| 3 | Same, on the very first view | Storm maturity wait (900 stable generations) |
| 4 | Same, immediately after the suite's first teleport | See fingerprint finding below |
| 5 | Client hung at world load after the pristine-world restore | unresolved |

**No live median results exist yet.** The harness is built and unit-validated; its live evidence is
still outstanding.

### Structural fingerprint finding

`groupSlot` is assigned by camera-distance ordering in `StormLobeSpatialIndex.build()`, so it changes
when the camera moves even though the storm has not. The suite teleports between poses by design, so
including `groupSlot` in the structural fingerprint made the fixture invalidate itself. It is a
per-frame render-ordering artifact rather than storm identity, and has been excluded from the
fingerprint; every other structural field is retained.

### Status

T119 is **not banked**; T121/T122 toggles remain **not started**. T132 stays open.

## Run accounting correction and the world-fixture blocker - 2026-08-27

### Invalid infrastructure runs must not count against T132

Seven client launches failed at world load and produced **no renderer evidence whatsoever**. They are
infrastructure failures of the test harness, not observations about the renderer, and are excluded
from every statistic: A/A reliability, optimization neutrality, fixture failure rate, and renderer
stability.

| Class | Count | Usable for T132 conclusions |
|---|---:|---|
| Runs before the pristine-world restore was introduced | ~12 this turn, ~31 cumulative | yes |
| Launches from a restored world that never loaded | 7 | **no** |

The earlier figures of "7 this turn / 26 cumulative" were wrong; the corrected approximate counts are
~12 and ~31, reconstructed from the campaign logs rather than estimated. The A/A statistics quoted
elsewhere in this document (4 failures in 80 gated pairs, residuals typically one pixel, worst 73
pixels) all predate the restore and remain valid.

### Two real defects found in the harness

**Stale `session.lock`.** The pristine template was snapshotted while a client still owned the world,
so every restored copy inherited a lock the integrated server blocks on. The world-reset helper now
excludes `session.lock` from both snapshot and restore, verifies the restored directory contains no
lock and a readable `level.dat`, and fails loudly rather than handing back a broken world.

**Stale world template.** The template was copied from `New World`, dated 28 April - months older
than the current mod set - and the client logged
`Unidentified mapping from registry minecraft:block / item / sound_event`. Forge raises a
confirmation dialog for unidentified mappings, and with no one to dismiss it the load blocks forever.

### Remaining blocker: no loadable world fixture

Neither defect fully explains the stall. After removing the lock and regenerating a world with the
current build via `runServer` - which produced no registry errors - the client still stops after
`Loaded 1271 advancements` and never starts the integrated server. `New World (5)` behaves the same
with zero registry errors. Launched **without** quickPlay the client reaches the main menu normally,
so the client itself is healthy and the failure is confined to loading a world.

The only world observed to load through quickPlay with this build was the original `New World (6)`,
and it was destroyed by overwriting it with the stale template. Recreating an equivalent requires
dismissing the world-creation and confirmation screens, which the automated driver cannot do.

**The `groupSlot` structural-fingerprint fix has still never executed live.** It is the one
outstanding question from the previous sequence and remains untested.

## Fixture recovered, groupSlot discriminator passed - 2026-08-27

### Automated world fixture

World creation no longer depends on a human. `StormT132AutoDriver` bootstraps its own level through
Minecraft's normal `WorldOpenFlows.createFreshLevel(...)` - no GUI, no synthetic input - then unloads
the source, snapshots it, restores a copy, and proves the restore loads before treating it as valid.
`session.lock` is excluded from both snapshot and restore, `level.dat` is verified, and a failure
before the world is entered is classified `INFRASTRUCTURE_INVALID` rather than counted as renderer
evidence. The stale April template is gone; worlds are generated by the current build, so the
`Unidentified mapping from registry` dialog that blocked seven launches cannot recur.

### Daylight freeze

Every abort of the first fixture-recovered run was one signature:
`sample_group_content_unstable:A1:renderInputs:lightDirection`, ten times out of ten. `lightDirection`
is derived from `level.getTimeOfDay(...)`, so with the daylight cycle running the sun moves between
the samples of a group and the group-content gate correctly rejects the group. The driver now freezes
the cycle at a fixed noon (`gamerule doDaylightCycle false`, `time set 6000`) as a fixture control,
alongside the existing cloud-movement freeze, fixed resolution scale and pinned raymarch quality, and
restores it on exit. No production rendering behaviour changed.

### groupSlot discriminator: PASSED

One full suite on fixture `debea664`, fingerprint `f5569fd7`:

- **zero** `fixture_invalidated_by_structural_change` aborts;
- the fixture survived the automatic SIDE, FAR, BELOW and ABOVE teleports;
- suite reached completion (`outcome=complete`), and the material trace was collected.

Excluding `groupSlot` from `StructuralFingerprint` was therefore the correct fix. It is assigned by
camera-distance ordering in `StormLobeSpatialIndex.build()`, so it moved whenever the suite teleported
- the fixture had been invalidating itself.

### First A/A median baseline

All eight A/A median comparisons passed at the unchanged one-storage-ULP tolerance:

| View | Median comparison | A1 dispersion | A2 dispersion |
|---|---|---|---|
| SIDE | passed, 0 changed pixels | 0 | 0 |
| FAR | passed, 0 changed pixels | 0 | 0 |
| BELOW | passed, 0 changed pixels | **3281 changed, 1 sample deviating** | 0 |
| ABOVE | passed, 0 changed pixels | 0 | 0 |

BELOW is the protocol working exactly as designed: one frame of five deviated across 3,281 pixels,
the median outvoted it, and the strict comparison still passed while the dispersion reported the
outlier rather than hiding it. This is why individual-frame bit identity was the wrong criterion and
robust medians with published dispersion are the right one.

## T119 banked - 2026-08-27

### Why the earlier 40/40 result was not bankable

The five-suite campaign reported `armA=OFF armB=ON` with 40 median comparisons and no failures.
Those were **group names**, not observed state: the suite printed the label it intended to set. The
per-view workload block (`topology=compact scans=0 metadataReadsPerGroup=3`) is captured once before
sampling, under compact, so it says nothing about the OFF arm either. A neutral A/B whose toggle
never took effect is vacuous, so the result was held.

### Arm execution is now observed, not asserted

`StormSceneStability.RenderInputs` carries a `stormTopologyMode` component populated in
`StormReferenceImageCapture` from the draw snapshot (`inputs.stormTopologyMode()`) - the value the
frame was actually uploaded with at `VolumetricCloudRenderer.java:388`, not the value the suite
asked for. `StormPerformanceSuite.observedTopology()` folds the five samples of a group and reports
`mixed` if they disagree or `unknown` if inputs are missing, so a group that failed to switch, or
switched mid-group, cannot be reported as a valid arm.

The A/A control groups discriminate the instrument: they report `armsDistinct=false` with both arms
`compact`, while the T119 groups report `armA=legacy_scan armB=compact armsDistinct=true`. The field
therefore reflects uploaded uniform state rather than echoing the group label.

The toggle drives real divergent code: `StormTopologyMode == 1` selects `stormGroupFirstIndexLegacy`
and `stormGroupEndIndexLegacy` - two 64-slot descriptor scans - in place of T119's O(1)
`witnessIndex - stormDescriptorMemberIndex(witnessIndex)` derivation
(`cloud_atmosphere_volume.fsh:853-869`).

### Result

Five fresh-client suites, five independent auto-bootstrapped fixtures, all four views, both passes:

| Fixture | Fingerprint | T119 medians | A/A medians | Arms distinct | Topology restored | Trace |
|---|---|---|---|---|---|---|
| `f40807d6` | `dba0499d` | 8/8 passed, 0 px | 8/8 passed, 0 px | 8/8 | 16/16 | yes |
| `39bfd9a6` | `69a3e1cc` | 8/8 passed, 0 px | 8/8 passed, 0 px | 8/8 | 16/16 | yes |
| `4d3af279` | `59dfa879` | 8/8 passed, 0 px | 8/8 passed, 0 px | 8/8 | 16/16 | yes |
| `fa49475d` | `7ef451bb` | 8/8 passed, 0 px | 8/8 passed, 0 px | 8/8 | 16/16 | yes |
| `9e8d9b93` | `0dd5c81b` | 8/8 passed, 0 px | 8/8 passed, 0 px | 8/8 | 16/16 | yes |
| **Total** | | **40 passed, 0 failed** | **40 passed, 0 failed** | **40/40** | **80/80** | **5/5** |

Every T119 group reported `armA=legacy_scan armB=compact armsDistinct=true`; every A/A control group
reported both arms `compact` with `armsDistinct=false`. Within-arm dispersion across all 40 T119
groups was `dev=0` on both arms without exception.

Acceptance, against the conditions set for banking:

| Condition | Evidence |
|---|---|
| Zero A/A background failures in the same campaign | 40 median comparisons, 0 failures |
| Zero unexplained T119 image deltas | 40 median comparisons, 0 failures, 0 changed pixels |
| `legacy_scan` and `compact` arms definitely executed | `armsDistinct=true` on every T119 group, from the draw snapshot |
| Topology restored after each pair | `topologyRestored=true` on every group |
| Structural/material controls matched | fingerprint stable per fixture; material trace collected on every fixture |
| Workload counters valid | all four views nonzero for `conservativeDescriptorRejects`, `avoidedDescriptorTextureFetches`, `earlyTerminations`; `scans=0`, `metadataReadsPerGroup=3` under compact |

The result is in fact stronger than the epsilon tolerance requires. Every T119 group reported
`medianDigestA == medianDigestB` - 80 arm digest pairs across the five fixtures, zero differing -
so the `legacy_scan` and `compact` median frames are **byte-identical**, not merely within one
storage ULP. The epsilon tolerance was never consumed on this comparison.

**T119 is banked**: the compact group topology is image-neutral against `legacy_scan` across five
independent fixtures and all four views, at byte identity.

### Residual within-arm dispersion, and why it does not weaken this

Nonzero within-arm dispersion appears in exactly one place across every campaign run so far: the
`A1` group of the `BELOW` view, at 1-2 deviating samples out of 5. It has never appeared in the
`OFF` or `ON` arms, which sample later in the same window and report `dev=0` throughout.

This is reported, not suppressed. The median outvotes the deviating samples, the strict comparison
still passes at 0 changed pixels, and the dispersion is published alongside the result so the
outlier is visible. Its root cause is **not** attributed - the pattern is consistent with residual
settling in the first sampled group of the view with the most terrain in frame, but that has not
been proven, and it is recorded here as a characterised bound rather than an explanation. It does
not weaken the T119 conclusion, because the arms compared for T119 are the ones with zero
dispersion.

## T132 ACCEPTED - 2026-08-27

**Status: ACCEPTED.** All six T132 criteria are satisfied on the post-T134 severe evidence fixture
`51ee0b7a-ed2f-4aa8-b0e8-03fb3874cdc2` (fingerprint `2be4b576a074587c`), under the
adjacent repeated-sampling protocol. T119 is banked. T121 and T122 carry an explicit, unresolved
neutrality limitation recorded below, which survives this closure.

### Acceptance-protocol correction: the separated-pass comparison is retired

This is a **protocol correction, not a waiver and not a relaxed control requirement.**

The original criterion 3 evaluated controls across two suite passes separated by multiple teleports
and substantial live game time. That separation was empirically shown to admit `WorldTime` drift,
projection/FOV drift, weather-map and cloud-content change, and `lightDirection` drift - none of
which are attributable to any optimization under test. Every one of those was diagnosed and
documented in the sections above before the protocol was changed.

Back-to-back adjacent capture removed the systematic differences, and the repeated-median adjacent
protocol then reached zero median failures across every campaign. `existingSeparatedPassComparison`
is therefore retired as an acceptance control and retained as historical diagnostic record only. Its
residual `workload_capture_token_reused=1` difference is an artifact of `PASSES = 1`, where pass B
reuses pass A's workload capture token; it describes the retired protocol, not the adjacent protocol
that supplies the evidence. `PASSES = 2` was **not** restored merely to satisfy a retired control.

The rebased criterion 3 demands **strictly more** of each accepted comparison than the retired
wording did. It adds projection settling, content-stability gating, suite-wide clock pinning,
fixture daylight control, runtime-profile and material-advection matching, sample-freshness and
no-stale-reuse requirements, and observed arm identity - none of which existed in the original
wording. Nothing was weakened.

### Reporting separation

The suite now emits two clearly separated blocks per view:

- `authoritativeAdjacentControls={protocol=adjacent_repeated_sampling authoritative=true ...
  controlsMatched=...}` - the **sole** input to the T132 criterion 3 verdict, computed from
  `ViewCapture.adjacentControlDifferences()` and the per-arm `groupControlDifferences()`;
- `historicalSeparatedPassComparison={authoritative=false retired=2026-08-27
  reason=temporal_separation_admits_unrelated_drift usedForT132Acceptance=false ...
  separatedPassControlFieldsMatch=... separatedPassControlDifferences=...}` - retained history.

The retired block's verdict fields are namespaced so they cannot be read as, or wired back into, the
acceptance verdict. `StormVolumetricGeometrySandbox.validateT132AuthoritativeControlSeparation()`
guards this structurally: it fails if the aggregator consults the separated-pass comparison, if the
retired block loses its non-authoritative labelling, if the bare `controlFieldsMatch` verdict
returns, or if any of the strict adjacent control requirements is dropped.

**What the settle control fields prove, precisely.** `projection_not_settled`,
`world_time_not_pinned` and `history_not_bypassed` in `groupControlDifferences()` record that a
sample came through the gated capture path. The gates themselves are enforced earlier, in
`StormReferenceImageCapture`, which refuses to accept a frame until the projection and content
signatures repeat for the required consecutive frames and otherwise aborts with
`projection_stability_timeout` or a content-stability failure. The control field is therefore a
provenance check, not an independent re-verification of settling; an ungated capture path would show
up as missing render inputs or an absent gate string rather than as `stabilized=false`.

### Six-criterion scorecard

| # | Criterion | Verdict | Evidence |
|---|---|---|---|
| 1 | Fresh post-T134 severe fixture | **PASS** | `descriptors=10`, `height=865.99799`, `footprintDiameter=1325.53979` - inside the T134 contract (10 descriptors, 720-880, 1,200-1,500) |
| 2 | One identity per comparison | **PASS** | `structuralChanged=false` x24, zero `true`, on the evidence fixture; fingerprint stable at capture and completion |
| 3 | Authoritative adjacent controls | **PASS** | `controlsMatched=true` on every view, `governorScale=0.50000`, `resolutionScale=0.75000`, `productionTopology=compact`, `target=641x360` equal to `workload=641x360`, `rayStepsConfigured=96`, `lightStepsConfigured=6`; zero `controlsMatched=false`; the retired comparison reports `separatedPassControlFieldsMatch=false separatedPassControlDifferences=workload_capture_token_reused` and does **not** feed the verdict |
| 4 | Owned-work evidence per change | **PASS** | all four views nonzero: `conservativeDescriptorRejects` 6.3M-206M (T121), `avoidedDescriptorTextureFetches` 70M-3.3B (T122), `earlyTerminations` 2.4K-155K (T123), `scans=0` / `metadataReadsPerGroup=3` (T119) |
| 5 | No image or material movement | **PASS**, with the T121/T122 limitation below | 80 A/A medians and 40 T119 A/B medians, zero failures; 80 T119 arm digest pairs byte-identical |
| 6 | Fresh post-T134 material trace | **PASS** | collected on every final fixture, same fixture as the image evidence |

### Evidence chain

**Fixture campaign.** Worlds are generated fresh by the current build through
`WorldOpenFlows.createFreshLevel(...)` - no GUI, no synthetic input, no human step. 11 of 11 suites
succeeded after the infrastructure recovery. The 7 historical `INFRASTRUCTURE_INVALID` launches all
predate it, were caused by the stale April world template that no longer exists, and are excluded
from renderer evidence rather than counted as failures.

**A/A reliability.** 400 reference captures across 40 median comparisons in the final campaign, zero
median failures; 800 captures across 80 medians counting the earlier valid campaign, zero median
failures. Raw within-arm dispersion remains characterised in the `BELOW` view's `A1` group at 1-2
deviating samples of 5; it is published rather than suppressed, it does not move the robust median,
and its root cause is **not** attributed.

**T119.** 5 fixtures x 4 views x 2 passes = 40 true optimization median comparisons.
`armA=legacy_scan armB=compact armsDistinct=true` on all 40, observed from the draw snapshot. Zero
image failures. 80 arm digest pairs byte-identical, so the epsilon tolerance was never consumed.
`topologyRestored=true` 80/80. **T119 is banked.**

### T121 and T122: unresolved neutrality limitation

This limitation **survives T132 closure** and is carried into T133 as explicit validation debt. It
must not be softened, and the T119 result must not be read as covering it.

- **T121 execution is proven** by nonzero `conservativeDescriptorRejects` on all four views.
- **T122 execution is proven** by nonzero `avoidedDescriptorTextureFetches` on all four views.
- **Neither one's image neutrality has been independently demonstrated.** Neither has ever been run
  with an OFF arm, because neither has a toggle - both are shader-internal, unlike T119's
  `StormTopologyMode` uniform.
- Building true OFF arms requires adding pre-optimization diagnostic branches to
  `cloud_atmosphere_volume.fsh`. That is a production shader change and is outside the authorised
  diagnostic-only scope, so it was not attempted.

## Required post-T134 captures for T132

Both captures must land on the **same** freshly resolved severe fixture. Run them in one session,
back to back, without letting the storm decay in between.

### A. Post-T134 controlled reference (`stormPerformanceSuite`)

```
/pa system volumetric diagnostics stormPerformanceSuite
```

Run it while standing near a severe (`STORM_ANVIL`) system, inside the native storm detail distance
so its descriptors are adopted. Confirm the native renderer owns clouds first with
`/pa cloud volumetric status`, and confirm a descriptor-owned storm is adopted with
`/pa system volumetric diagnostics stormDensity`; if that prints
`no descriptor-owned storm is currently adopted`, the broad-map fallback is rendering and the
capture would not test the descriptor path.

The suite is fully automatic after that one command. `StormPerformanceSuite.begin()` forces compact
topology, freezes one fixture through `StormPerformanceBaseline.begin(...)`, then drives itself:
`moveAndConfirmPose()` issues `tp @s <x> <y> <z> <yaw> <pitch>` to each frozen pose and invalidates
history, waits for governor scale `0.50000` and resolution scale `0.75000` to hold, captures the two
workload frames, settles FINAL frames, and captures the eight timestamp samples and fence-gated
visual reference. It visits SIDE, FAR, BELOW, ABOVE in PASS A and repeats them in PASS B — eight
captures total. Do not move, open a menu, or change quality settings while it runs.

Poll it with:

```
/pa system volumetric diagnostics stormPerformanceSuite status
```

Accept only when the final report shows, for **both** passes and **all four** views: one group UUID,
one structural fingerprint equal at `fingerprintAtCapture` and `fingerprintAtComplete`,
`structuralChanged=false`, exact pose equality, `governorScale=0.50000`,
`resolutionScale=0.75000`, `topology=compact`, equal configured ray/light steps, equal target and
workload dimensions, **distinct `captureToken` values between PASS A and PASS B**, and an empty
control-difference string. Each view also reports `imageNeutrality={...}`, `sceneStability={...}`,
and `criterion5={...}`. Criterion 5 passes only when `imageNeutrality` shows
`evaluated=true passed=true`. A failing comparison is chargeable to the performance path only when
`sceneStable=true`; with `sceneStable=false` the suite records
`criterion5Attributable=false reason=scene_evolved_between_passes`, which is a protocol result, not
evidence against the optimization. The report's
`scaleEnvelope={baseTop, height, horizontalRadius, footprintDiameter, descriptors}` must show
`descriptors=10`, `height` in 720–880, and `footprintDiameter` in 1,200–1,500 — that is what
confirms the fixture is post-T134 and not a compact leftover. Record the `baseTop` values; part B
needs them.

The suite aborts on fixture disappearance, structural-fingerprint change, non-compact topology,
pose or facing failure, governor failure, workload failure, a stale or wrong-token workload result,
a failed deterministic reference capture, a pass-control mismatch, or a per-view render-sample
timeout. An abort is a failed capture, not a measurement — re-run it.

### B. Post-T134 material trace on the same fixture

```
/pa system volumetric diagnostics stormMaterialTrace <yStart> <yEnd>
```

Stand near the same storm so `resolve(x, z)` picks the same group; the trace then freezes itself to
that group's resolved centre and the player may move afterwards. Derive the range from the
`scaleEnvelope` `baseTop` reported in part A: use `yStart = baseY - 32` and `yEnd = topY + 32` so
the trace covers below the BASE underside through above the ANVIL canopy. For a nominal post-T134
column that is roughly a 930-block span.

`StormMaterialRuntimeTrace.request(...)` computes
`samples = max(2, min(96, ceil((yEnd - yStart) / 16) + 1))`, so a ~930-block span yields 60 samples
at a ~15.8-block interval — within both the 16-block SC-019 requirement and the 96-sample cap. No
code change is needed to cover the new column.

Wait four rendered frames, then retrieve it with:

```
/pa system volumetric diagnostics stormMaterialTrace
```

A `rejected_centroid_mismatch` result means the resolved group centre and its material-weighted
centroid disagree by more than the tolerance; move closer to the intended storm and retry. A
`no_complete_published_storm_group` result means no complete descriptor group is adopted.

Record both results in this file under a new "Accepted post-T134 T132 evidence" heading, then
evaluate them against the six revised T132 criteria in `tasks.md`.
