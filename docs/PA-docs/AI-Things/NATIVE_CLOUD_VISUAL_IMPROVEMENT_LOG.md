# Native cloud visual improvement log

## Scope

- Backend under test: Project Atmosphere native volumetric renderer.
- Runtime: Minecraft 1.20.1, Forge 47.4.20, Java 17.
- Simple Clouds and CrackersLib were absent from the development client classpath.
- The dedicated test save is `run/saves/PANativeVisualLab`.
- The user will perform the final capture matrix. The images already present under
  `run/screenshots/pa-native-visual-audit` are diagnostic references only.

## Iteration 0 - backend ownership gate

- Result: the native hooks, native shadow pass, native whiteout and native visual
  density publication are not called while Simple Clouds owns cloud rendering.
- The tested native session reported `cloudOwner=PA_VOLUMETRIC` and selected the
  native PA cloud service.
- Known boundary: the broader Simple Clouds density-provider gap remains outside
  this native-only visual task; no Simple Clouds integration code is changed here.

## Iteration 1 - native baseline

- Launch: the client reached an integrated world without Simple Clouds, registered
  every native volumetric shader, baked the 3D noise textures and composited the
  native pass successfully.
- Depth diagnostic: detached `vanilla_main` depth, valid at the active framebuffer
  size, with no fallback.
- Representative native fields were spawned with `/pa cloud spawn <id>`, server
  drift was frozen, the daylight cycle was stopped and the camera was positioned
  from the actual `baseTop` and `center` returned by `/pa system cloudStatus`.
- Confirmed visual defects:
  - `cumulus_humilis` is a small disconnected group of bright lobes;
  - `stratus_nebulosus` forms a huge overexposed sheet with pointed radial edges;
  - `stratocumulus` is a nearly white continuous ceiling without readable cells;
  - the attempted `nimbostratus` scene was split into isolated bright oval slabs,
    but diagnostics later showed two visible fields, so it is not accepted as an
    isolated-profile reference;
  - both cumulonimbus variants are compact, fragmented and lack a coherent tower,
    dark base and readable anvil;
  - `cirrus_fibratus` resolved to `vapor_cluster`; that capture is invalid and no
    claim about native `cirrus` is based on it;
  - no native `supercell` type could initially be spawned; an unknown id fell back
    to `vapor_cluster`.
- The current environment was not suitable for an FPS/VRAM baseline because a
  separate GPU-heavy application was active. Only the renderer's asynchronous
  raymarch timer is retained as diagnostic evidence, not as a benchmark.

## Root causes confirmed before the first code change

- `CloudletLayout.generate` uses the same disk and height distribution for every
  morphology family.
- `VolumetricRenderCell.fromFieldCloudlet` applies almost the same horizontal ratio,
  orientation and edge softness to every type.
- `cloud_weather_splat.fsh` collapses both base and top by 42 percent at footprint
  edges, explicitly creating ellipsoidal volumes and rounded stratus bases.
- The morphology texture averages categorical profile ids and does not retain
  explicit anvil strength, local storm material or rotation.
- The main density shader has shared branches for stratus/stratocumulus/nimbostratus
  and for cumulonimbus/supercell.
- An early coverage return and final local-coverage multiplication prevent an anvil
  from extending beyond the original footprint.
- Fixed daylight floors keep dense storms bright; storm darkening is camera-global.
- Precipitation only multiplies in-cloud density. No under-base rain shaft or virga
  volume exists.
- Internal noise is advected opposite to field motion and also has a second
  time-only animation, producing texture sliding.
- Far LOD can request zero cloudlets without a macro-volume substitute.

## Next attempt

Implement family-specific deterministic cloudlet layouts, family-aware weather-map
envelopes, distinct density functions, local storm lighting, wind-coherent noise and
precipitation shafts. Compile and validate shader resources before the next runtime
launch.

## Iteration 2 - family layout and macro continuity

- Attempt: replace the universal disk layout with deterministic family layouts and
  preserve one macro envelope per visible field before allocating detail cloudlets.
- Implemented stable structural roles: core, lobe, base, tower, anvil, sheet tile and
  filament. Cirrus and anvils align to field wind; sheet heights have low variance;
  convective cores occupy the first stable IDs retained by low budgets.
- The vertical span of a cloudlet is now derived from the field thickness instead of
  its horizontal radius.
- FAR_PROCEDURAL and HAZE retain the macro field envelope while requesting zero
  identifiable detail cloudlets.
- Validation fixture correction: the translation self-check no longer changes field
  Y while claiming to test horizontal translation, and it compares every new layout
  component.
- Result: `compileJava` succeeded on Forge 47.4.20/Minecraft 1.20.1. The remaining
  warnings are pre-existing mixin/deprecation warnings.

## Iteration 3 - weather map, raymarch morphology and lighting

- Attempt: make footprint harmonics and vertical edge collapse depend on the
  categorical profile, preserve the dominant profile in overlaps, then replace the
  shared density function and fixed lighting floors in the main raymarch shader.
- The primary weather map now carries the categorical profile explicitly. The
  morphology pass selects the dominant profile instead of averaging type ids at
  field overlaps.
- Seven family envelopes now have distinct density functions: stratus,
  stratocumulus, cumulus, cumulonimbus, nimbostratus, cirrus and supercell. Their
  vertical profiles, edge erosion and protected macro cores are no longer the same
  lobe with different scalar multipliers.
- Added a native `supercell` type and a `cirrus_fibratus` command alias so the two
  requested visual cases can be spawned without falling back to `vapor_cluster`.
- Precipitation-driven shafts extend below storm bases and use the field's
  precipitation/condensate channels. They are excluded from the light cone to
  contain their GPU cost.
- Noise advection now follows the same signed wind displacement as the field and
  no longer includes a separate time-only translation. Storm lighting is local,
  fixed white radiance floors were removed, and silver lining is limited to the
  illuminated shell.
- Validation result before runtime: `compileJava processResources` succeeded;
  all shader JSON files parse; braces and parentheses balance in the three modified
  fragment shaders; `git diff --check` reports no whitespace errors. Gradle does
  not compile GLSL, so the next attempt is a client smoke launch without Simple
  Clouds to validate the shaders in the actual OpenGL context.

## Iteration 4 - native runtime smoke test

- Launch: `runClient --args=--quickPlaySingleplayer=PANativeVisualLab` with the
  default development classpath, where Simple Clouds and CrackersLib are compile
  only and therefore absent at runtime.
- Runtime ownership: the integrated server logged `Simple Clouds absent; using
  native PA cloud service`.
- Shader validation: Minecraft registered the volumetric shader programs without
  a shader/uniform error. The asynchronous noise bake completed and uploaded the
  base, detail and blue-noise textures.
- Render-path validation: `/pa cloud spawn supercell` resolved to the new native
  `supercell` definition. The first complete frame reported a valid detached
  `vanilla_main` scene depth, `source=fields`, `weatherCells=26` and
  `composited=true`; later frames exercised the strict 64-cloudlet budget with two
  fields and remained composited without a render exception.
- Timing observation: the existing asynchronous raymarch timer reported roughly
  1.10--1.42 ms for the sampled frames. This measures the raymarch fullscreen draw
  only and is not accepted as a comparative benchmark: another GPU-heavy program
  remained active, and the renderer currently has no separate weather-map,
  composite or shadow timer, no FPS/VRAM capture, and no average executed-step
  counter.
- Shutdown: the development client and integrated server stopped cleanly and saved
  every dimension.
- Visual acceptance remains intentionally open. Per user direction, the final
  before/after capture matrix will be performed manually and is not inferred from
  successful GPU execution.

## Iteration 5 - macro/detail capacity audit

- Review found that macro envelopes were appended before detail cells while the
  detail allocator could still consume all 96 weather-map slots. With a large
  number of visible fields, the tail of an allocation reported as accepted could
  therefore be clipped silently by the hard GPU limit.
- Fix: reserve one macro slot per visible field (up to the hard limit) before
  computing the detail budget. Accepted/rejected/remaining diagnostics now describe
  detail cells that can actually reach the weather map.
- Result: `compileJava processResources` succeeded after the capacity correction;
  only the repository's existing mixin/deprecation warnings remain.

## Final programmatic validation

- `gradle test --no-daemon`: successful. The repository has no automated unit
  test suite with assertions for this renderer, so this validates test-source
  compilation/task execution only.
- `gradle build --no-daemon`: successful; reobfuscated mod jar produced.
- `git diff --check`: no whitespace error (line-ending conversion warnings only).
- Every shader JSON parses and delimiter counts remain balanced in the three
  modified fragment shaders.
- The runtime log contains no PA shader compile/link/uniform error and no native
  render exception.
- Remaining acceptance gate: user-performed visual capture matrix and comparable
  performance measurements with other GPU-heavy applications closed.

## Iteration 6 - convective transition continuity

- Review after the first smoke test found a probable temporal pop: the same stable
  cloudlet ids used different angular domains and the storm layout assigned id 1 to
  the anvil while the tower layout assigned it to the base.
- Attempt: make a developing cumulus core converge continuously toward tower height,
  span and aspect using `verticalDevelopment`; use the same deterministic angular
  domain for non-spiral convective detail; preserve the stable structural order
  core, base, then anvil across tower and storm families.
- Expected effect: smaller spatial jumps during cumulus -> congestus ->
  cumulonimbus evolution. A categorical family switch still exists and remains a
  manual video-validation target.
- Result: `compileJava processResources` succeeded. The existing standalone
  CloudField lab now runs `CloudFieldValidation.runSelfCheck()` before generating
  its preview, and `gradle cloudFieldSandbox --no-daemon` completed with
  `CloudField self-check passed`.

## Iteration 7 - morphology-wide self-check coverage

- The first executable self-check exercised only the puff/cumulus layout. Before
  relying on it as the validation gate for the new family switch, extend it across
  representative tower, storm anvil, sheet, cellular sheet, filament and spiral
  storm snapshots, including finite/bounded output and stable storm role ordering.
- Result: the expanded check covers cumulus, congestus, cumulonimbus,
  stratus, nimbostratus, stratocumulus, cirrus and supercell layouts. It validates
  ids 0--7 per case plus tower/base and core/base/anvil ordering.
  `gradle cloudFieldSandbox --no-daemon` completed with
  `CloudField self-check passed`.

## Post-transition final validation

- `gradle build --no-daemon`: successful after the transition and self-check
  changes; reobfuscated jar regenerated.
- `git diff --check`: no whitespace errors; only the repository's Windows
  line-ending conversion warnings are printed.
- Modified shader JSON files still parse, shader delimiters remain balanced, and
  the built jar contains the three shader programs plus the updated layout,
  validation, render-cell and type-registry classes.

## Iteration 8 - production JAR visual-validation setup

- User explicitly authorized direct in-game visual validation and Windows control.
- `runClient` is not accepted for this iteration because ForgeGradle mounts
  `sourceSets.main` through `MOD_CLASSES`; it does not prove the reobfuscated JAR.
- Prepare an isolated Minecraft 1.20.1 / Forge 47.4.20 production game directory
  with the built Project Atmosphere JAR plus only its mandatory runtime chain:
  Gaboulibs, Architectury and Cool Rain.
- Simple Clouds, CrackersLib, Oculus, shader packs and unrelated renderer mods must
  be absent. Verify the copied PA JAR hash before launch and verify the loaded mod
  list from the production log before accepting any image.
- The integrated Computer Use pipe is unavailable in this session. Window input
  and capture will use the authorized Win32/screenshot-helper fallback, with every
  target window identified by process id and handle before interaction.

## Iteration 9 - production JAR visual baseline without Simple Clouds

- Launch proof: the isolated Forge 47.4.20 production client loaded the reobfuscated
  `Forge-projectatmosphere-0.9.1.1-alpha.jar`. Its mod directory contained only PA,
  Architectury, Gaboulibs and Cool Rain. The runtime log reported
  `Simple Clouds absent; using native PA cloud service`, registered both native
  volumetric shader programs and selected a valid detached `vanilla_main` depth.
- Capture method correction: captures made directly from a partially occluded
  window handle displayed false black rectangles. A maximized desktop capture and
  the source PNGs proved those rectangles were not present in Minecraft. They are
  excluded from all findings below.
- Deterministic setup: clouds were cleared and frozen, time was fixed at noon, each
  requested type was spawned at `(0, 190, 0)`, then observed from a fixed spectator
  position on negative Z. Baseline images are under
  `run-native-jar-visual/screenshots/before/`.
- Visually confirmed results:
  - `cumulus_humilis` is recognizable at 250 blocks, with a gray underside, but its
    silhouette has only a few broad lobes and a soft, white fringe.
  - `stratus_nebulosus` becomes a nearly featureless white ceiling with a curved
    radial lower boundary instead of a horizontally detailed layer.
  - `stratocumulus` is a thin bright tablet: some broad bumps are present, but its
    carrier ellipse and smooth top dominate the cellular structure.
  - `cirrus` is effectively invisible at both 500 and 250 blocks in the tested
    view, despite the renderer reporting an active field.
  - `nimbostratus` looks like a localized dark disk with a thick white rim and no
    visible distant rain shaft; the recorded raymarch time reached about 103 ms in
    one two-field synchronization frame.
  - `cumulonimbus_capillatus` is tall, but fragments into several black finger-like
    towers with white outlines. Its base, central column and anvil do not read as
    one continuous storm mass.
  - `supercell` has a distinct asymmetric tower and shelf, but resembles a dark
    hard-edged object rather than a rotating cloud. Vertical precipitation streaks
    stop abruptly at the sampled footprint.
- Code/visual correlation before the next fix:
  - half-resolution upsampling renormalizes only accepted opaque neighbours,
    dilating thin bright edges;
  - temporal history assigns a fabricated midpoint depth to an empty ray and can
    reintroduce a previous edge;
  - sentinel base/top values are linearly blended into footprint fringes and are
    enabled by default;
  - the categorical morphology id is linearly filtered and rounded, allowing cloud
    families to change inside a one-texel edge;
  - per-sample radiance is peak-normalized, collapsing energetic sun/silver values
    to white;
  - macro ellipses remain dense enough to hide sheet cloudlets, while severe-storm
    cloudlets remain too disconnected;
  - lateral rain rays do not extend below the cloud slab unless a camera-position
    probe is already inside precipitation.
- Next attempt: fix those causes as one internally consistent shader/packing pass,
  rebuild the production JAR, then repeat the same captures before tuning secondary
  details. No visual result will be inferred from compilation alone.

## Iteration 10 - first evidence-driven visual correction pass

- Edge/composition corrections:
  - preserve absolute bilinear coverage during the depth-aware half-resolution
    composite instead of renormalizing only opaque neighbours;
  - assign depth 1 and reject temporal history when the current ray has no cloud
    contribution;
  - make real fringe heights the default, retain legacy sentinel heights only as
    an A/B switch, and include that switch in the weather-map cache signature;
  - fetch the categorical profile channel with nearest semantics while continuing
    to filter continuous morphology traits.
- Morphology/data corrections:
  - send a data-driven material darkness trait from each cloud type to the
    morphology map;
  - reduce the broad macro carrier while identifiable cloudlets are active, but
    retain the stronger carrier for far procedural LOD;
  - increase overlap for storm, sheet and filament cloudlets and make hydration
    visibility actually approach zero instead of retaining 72 percent density;
  - smooth the weather-map height weighting and protect severe-storm continuity;
  - reduce destructive erosion for cirrus and severe storms, broaden convective
    lower masses and anvils, and strengthen the cirrus filament profile.
- Lighting correction: replace per-sample peak normalization with monotonic
  exponential highlight compression, reduce the white silver-lining term, apply
  real material/underside darkness, and retain enough multiple-scattered ambient
  light to prevent severe cores from becoming featureless black objects.
- Precipitation correction:
  - CloudField sources now carry a dynamic precipitation value; render-data fields
    use the synchronized precipitation tier instead of an unconditional type
    constant;
  - a frame-level precipitation bound extends the ray slab for distant lateral
    shafts;
  - shafts sample the upwind source footprint and use coarse steps plus a cheap
    lighting path so the extra depth does not force full cloud-light marches.
- Programmatic result: both logical groups passed `compileJava processResources`.
  `cloudFieldSandbox` reported `CloudField self-check passed`, `test` completed,
  and `build` produced the reobfuscated production JAR. Shader JSON parsing,
  delimiter checks and `git diff --check` passed; GLSL acceptance remains pending
  the next real client resource load.
- Next attempt: install this exact JAR in the isolated no-Simple-Clouds instance,
  verify shader registration, then repeat humilis, stratus, cirrus, nimbostratus,
  cumulonimbus and supercell at the same camera coordinates.

## Iteration 11 - first corrected-JAR shader load rejected

- Production JAR SHA-256 installed for this attempt:
  `0C109B880605668B1C42C9A8CBF38361A15B9030F6432E3998EC8B05D5966FAA`.
- The Forge 47.4.20 client reached shader registration, then the real OpenGL
  compiler rejected `cloud_atmosphere_volume.fsh` at source line 392:
  `precipitation` was referenced before declaration and subsequently redefined.
- Root cause: the new derived-condensate expression was placed before the existing
  precipitation declaration inside `cloudDensity`; Java compilation and delimiter
  checks cannot detect GLSL declaration order.
- Result: no world was entered and no visual result was accepted. Next attempt is
  limited to moving the declaration before its first use, rebuilding and repeating
  the production shader-load gate.

## Iteration 12 - first visual pass observed, partially rejected

- The declaration-order fix was rebuilt and the production OpenGL load succeeded:
  volumetric shaders registered, the native world loaded, and the runtime again
  confirmed that Simple Clouds was absent.
- Capture reliability: subsequent comparisons use Minecraft's native F2 framebuffer
  captures. Desktop/window captures are no longer used because focus and occlusion
  can show unrelated applications or false black regions.
- Accepted improvements visible in `screenshots/after-pass1/`:
  - humilis no longer has a clipped white rim; underside contrast is smoother and
    the cloud remains recognizable;
  - stratus and nimbostratus no longer saturate to pure white;
  - the nimbostratus raymarch fell from the earlier roughly 103 ms two-field sample
    to roughly 27 ms in the comparable transient two-field frame, despite the
    later capture running at a larger framebuffer;
  - cumulonimbus/supercell highlights retain gray variation instead of becoming a
    uniformly white outline.
- Rejected remaining results:
  - stratus is still a radially curved, nearly featureless ceiling;
  - cirrus remains almost invisible from both end-on and side views; alpha debug
    shows only a tiny low-opacity footprint;
  - the new distant precipitation volume creates a conspicuous horizontal
    stippled band at the nimbostratus base;
  - cumulonimbus remains a narrow disconnected tower with detached lobes;
  - supercell cloudlets still combine into a hard winged/UFO-like object with a
    black cavity and vertical sampling streaks.
- Root cause for the second pass: cloudlet base/top values still compete directly
  with the field carrier in the weather-map envelope. The renderer has no explicit
  carrier tag, so severe anvil/tower footprints can pull the local slab through
  most of the field height and form hard geometric wings. The next attempt will
  tag the carrier, let it stabilize only the height envelope, restore moderate
  storm cloudlet radii, strengthen cirrus coverage without thickening its vertical
  profile, and fade rain in below the base instead of starting as a dense sheet.

## Iteration 13 - carrier envelope pass observed

- The carrier-tag build compiled, passed the CloudField self-check, registered its
  shaders in the production client and rendered with the native backend only.
- Accepted result: cirrus is now plainly visible from a side view as a thin,
  wind-aligned set of wispy filaments with a brighter central streak. It remains
  inexpensive (about 0.4 ms in the sampled raymarch frame) and is no longer based
  on cumulus lobes.
- Partial result: cumulonimbus changed from disconnected fingers into one continuous
  tall core, proving that a stable carrier envelope is necessary. Its sides are
  still too rectangular and its anvil is too weak.
- Rejected result: supercell remains a collection of tall ear-like cylinders.
  Stabilizing the envelope alone cannot compensate for 20+ severe satellite
  cloudlets whose vertical spans and coverage remain close to the core.
- Rejected precipitation result: the horizontal stipple band is thinner but still
  visible, and the finer shaft stride increased the one-field nimbostratus sample
  to about 59 ms. The next pass restores coarse stepping, makes attached rain zero
  at the base and requires actual streak noise before a shaft contributes.
- Next attempt: keep one large storm core/base carrier, make secondary towers
  smaller and lower-coverage, broaden but thin the dedicated anvil, apply stronger
  top collapse only to the macro carrier, and sparsify precipitation. This targets
  the observed structures rather than applying another global density multiplier.

## Iteration 14 - storm/rain pass observed, rejected

- Production shader registration and native-only ownership still pass with the
  rebuilt JAR. A same-position stratus control has no stippled band below its
  base, while nimbostratus does; this confirms that the remaining horizontal band
  is generated by `rainShaftDensityAt`, not by the common composite.
- The precipitation cost did improve materially: the transient nimbostratus
  raymarch sample fell to about 14 ms from about 59 ms in iteration 13. The visual
  result is still rejected because the low-alpha two-dimensional rain background
  forms a broad blue/gray curtain before its sparse streaks become visible.
- The first severe captures were briefly contaminated by stale synchronized fields
  because clear and spawn were issued in the same synchronization interval. They
  are not used as morphology evidence. After the client reached one synchronized
  field, the defects remained: capillatus separates into a dark blocky lower mass
  and a detached top, and supercell separates into a hard shelf/base plus an
  isolated mushroom-like tower/anvil.
- `/pa cloud list` also showed that the spawned supercell region had a 565.7-block
  radius. The original 800-block camera was inside its horizontal reach, explaining
  the apparently screen-height upper slab; clean 1200- and 1600-block captures
  remove that framing error but still show the disconnected hard-object silhouette.
- Root causes for the next attempt: rain retains a non-zero `mix(0.02, ...)`
  contribution across the whole precipitation footprint; severe cloudlet height
  intervals are still averaged into locally abrupt slabs; and the macro carrier
  contributes coverage as a separate full-height ellipse instead of only bridging
  the lower mass, tower and anvil. The next build will make rain strictly
  streak/column gated and replace the severe profile's single weather-map interval
  with a continuous family envelope that varies with horizontal storm structure.

## Iteration 15 - expanded severe carrier rejected; rain gate accepted

- The production JAR compiled, passed the CloudField self-check, registered the
  GLSL programs and again ran with `Simple Clouds absent; using native PA cloud
  service`.
- Accepted precipitation result: a clean nimbostratus capture at 1300 blocks no
  longer contains the horizon-wide blue/stippled band. Only sparse, data-driven
  shafts remain below the cloud footprint. This validates removing the non-zero
  rain floor and gating density with the coherent Worley column mask.
- Rejected severe result: expanding the carrier to 0.96/1.00 field radius and
  lowering its nominal density removed the detached stack, but produced a smooth,
  dark rectangular dome with almost no readable tower or anvil. The volume
  shader's coverage remap amplified the supposedly low-density carrier, so the
  result was a monolithic ellipse rather than a storm.
- Additional code-path confirmation: `CloudletRole` is discarded after choosing
  edge softness in `VolumetricRenderCell`. The weather splat therefore unions
  coverage but averages every BASE, CORE, TOWER and ANVIL into one untyped base/top
  interval. Applying the same severe macro profile to each resulting local slice
  explains both the former stack and the new carrier dome.
- Next attempt: revert the oversized carrier, transport a compact envelope role in
  the already-uploaded dynamics value, and perform a role-aware vertical union in
  the cached weather-map pass. Only a local CORE/TOWER/carrier footprint may bridge
  the low base to the high anvil; the anvil overhang remains thin outside it. This
  adds no screen-space raymarch samples or textures.

## Iteration 16 - role-aware height union observed, still rejected

- Java compilation, tests, CloudField self-check, production GLSL registration and
  native-only backend selection all passed.
- The role-aware weather-map union prevents a detached top, but clean capillatus
  and supercell captures still form dark, nearly vertical-sided cuboids with small
  spikes on top. The capillatus sample was about 0.8 ms GPU at its adapted view,
  so the failure is morphology rather than excessive raymarch cost.
- Confirmed remaining cause: role information currently influences only base/top
  construction. The morphology texture still stores only the family id, so the
  volume shader runs the complete storm base/updraft/anvil function inside a local
  BASE slice and again inside an ANVIL slice. In the central carrier interval,
  remapped coverage is near one and overwhelms the height-dependent noise
  threshold, leaving vertical walls.
- Next attempt: encode `(profile, envelopeRole)` together in the categorical R
  channel of the RGBA8 morphology map (64 representable states), decode it with
  nearest sampling, give BASE and ANVIL genuinely separate density functions, and
  taper CORE/TOWER density with unsaturated raw weather coverage as height rises.
  No new target, sampler, pass or per-ray texture lookup is required.

## Iteration 17 - role-aware density observed, still too cylindrical

- The 64-state categorical encoding compiled in the production OpenGL driver;
  shaders registered and native-only ownership remained valid.
- BASE and ANVIL no longer create obvious miniature copies of the full storm, but
  both capillatus and supercell remain dark vertical-sided masses. A closer
  capillatus capture makes the geometry unambiguous: several tall cloudlets end in
  bright rounded caps but keep nearly straight walls down to a hard flat base.
- Root cause confirmed in the weather splat: severe non-carrier cloudlets use only
  `baseCollapse=0.018` and `topCollapse=0.16`. Their base/top interval therefore
  changes very little from center to footprint edge, so no later noise threshold
  can remove the large opaque cylindrical wall reliably.
- Next attempt: use the now-available role to build different height envelopes:
  strong top collapse for CORE/TOWER, low collapse for the flat BASE, symmetric
  thin-edge collapse for ANVIL, and a moderate rounded carrier. Reduce severe
  carrier coverage so cloudlets, rather than the hidden support ellipse, define
  the visible outline.

## Iteration 18 - anvil recognizable, central core still too monolithic

- Production GLSL registration and the native-only backend gate passed again.
- The adapted capillatus capture now has a clearly readable wind-swept anvil and a
  narrower upper tower instead of a full carrier dome. This is the first severe
  pass whose family is identifiable without relying on its command name.
- The result is not yet accepted as final: the central core remains one tall dark
  rectangular wall with vertical internal seams. The dedicated id-0 storm core is
  itself 0.42/0.46 field radii wide, spans 96% of the field height, and still has
  density/coverage 1.10/1.00. It therefore hides the smaller tower cloudlets even
  after the field carrier was reduced.
- Next attempt: retain that core as a lower-density connector, shrink its radius,
  soften severe CORE/TOWER footprints, and let the deterministic secondary towers
  provide the visible billows. The role-aware union continues to guarantee
  connectivity even when the connector is no longer the dominant opaque mass.

## Iteration 19 - reduced core observed; support coverage still leaks into shape

- Build, tests, self-check, production GLSL registration and native ownership all
  passed. The smaller core exposes multiple severe components and preserves the
  capillatus anvil, but the central mass remains too rectangular and the anvil can
  still read as a second lobe beside it.
- The remaining coupling is explicit in the data path: the field-level carrier is
  intended to stabilize base/top, yet its density still participates in the same
  smooth coverage union and morphology-role competition as visible cloudlets.
  Lowering its scalar cannot solve both continuity and silhouette at once.
- Next attempt: introduce an explicit `CARRIER_ONLY` envelope role. In detailed
  severe fields it contributes no coverage, opacity, morphology or material; it
  only provides a broad cached height envelope where a visible CORE/TOWER stamp
  authorizes the connection. Far procedural fallback keeps the normal visible
  MACRO role. This fixes the semantic cause without adding a GPU resource or a
  screen-space sample.

## Iteration 20 - invisible support carrier validated; material/rain refinement needed

- Production GLSL accepted `CARRIER_ONLY`; the isolated client remained native PA
  only. The clean capillatus capture now reads as a tall central updraft, broad
  lower base and wind-swept anvil. The hidden support no longer appears as an
  ellipse and the family is visually identifiable.
- Full-resolution A/B preserves the same silhouette defects, proving they are not
  caused by half-resolution upsampling. Alpha/depth debug shows that the regular
  vertical teeth below the base are precipitation columns, while the parent cloud
  is nearly fully opaque. The new zero-background rain mask is correct, but its
  cross-wind frequency is too high and rain lighting is too dark.
- Remaining quality issues for the next attempt: widen rain columns and lower their
  extinction without restoring a background floor; slightly increase storm edge
  erosion; reduce severe bulk density; and retain more ambient/multiple-scattered
  light in the core. This is a material pass over the now-accepted structural
  representation, not another geometry rewrite.

## Iteration 21 - precipitation/material pass accepted; severe sheets still too solid

- Wider, lower-density shafts remove most of the picket-fence appearance below the
  capillatus base. The cloud core retains dark gray information instead of reading
  as a uniformly black cutout, while the family remains visibly more severe than
  fair-weather clouds.
- History-off and full-resolution diagnostics both preserve the main geometry, so
  neither temporal reprojection nor half-resolution composition is the source of
  the remaining vertical tower seams.
- A valid supercell capture shows a broad low shelf, asymmetric tower group and
  displaced upper outflow, but both the dedicated BASE and ANVIL cloudlets are too
  opaque and narrow in one axis, giving the storm an architectural stacked-slab
  appearance. The next small geometry adjustment lowers their density/coverage,
  broadens the anvil cross-wind aspect, and increases vertical overlap. Invalid
  top-down/menu captures caused by cursor recapture are excluded; the F2 helper now
  recenters the cursor before focusing Minecraft.

## Iteration 22 - final geometry build observed; severe silhouette still rejected

- The exact production JAR copied into the isolated Forge 47.4.20 instance loaded
  with `cloudOwner=PA_VOLUMETRIC`; the instance contains no Simple Clouds JAR.
- Cirrus remains visible as a thin, wind-aligned filament, although the isolated
  sample is still too bright at its dense center and too small at the adapted
  600-block framing.
- A fresh capillatus capture shows a tall dark precipitation-bearing column and a
  separate bright rounded top. The family is identifiable, but the core retains
  horizontal strata and nearly vertical walls, while the anvil is too round and
  insufficiently blended into the tower.
- A fresh supercell capture at Y=300 confirms that the last BASE/ANVIL adjustment
  is not acceptable as a final result: the left outflow is a long, uniformly
  bright shelf with a hard bend into a dark rectangular core. The desired
  asymmetry is present, but it reads as stacked geometry rather than cloud mass.
- Several earlier supercell images from this session are excluded: one was taken
  after the saved severe region had expired during a long interrupted session,
  and one was captured on the main menu after the world disconnected. A new F2
  fallback using the Minecraft window event queue now captures without requiring
  Windows foreground focus; the fresh Y=300 image is the valid comparison.
- Next attempt: keep the already-validated role transport and invisible support,
  but make BASE/ANVIL opacity depend on low-frequency horizontal modulation,
  soften role transitions, and reduce the hard contribution of a single dominant
  stamp. Ordinary sheet profiles will be adjusted separately so noise adds
  horizontal structure without destroying their continuous meteorological mass.

## Iteration 23 - low-frequency severe sheets improved; full-height core rejected

- Build, tests and CloudField self-check passed; the production driver registered
  all shaders and the client again reported `cloudOwner=PA_VOLUMETRIC` without
  Simple Clouds.
- The supercell outflow is shorter, less uniformly opaque and carries more stable
  horizontal texture than iteration 22. The capillatus anvil also has a cleaner
  windward extension.
- The result is still rejected. Fresh elevated captures isolate a nearly black,
  rectangular central column in both families. In capillatus it spans from the
  flat base almost to the detached-looking bright top; in supercell it remains a
  broad cuboid behind the softened outflow.
- Code-path confirmation: the dedicated id-0 CORE still spans 96% of the field
  height, and the invisible carrier can restore the carrier's full base/top range
  anywhere that CORE coverage authorizes a link. Together they recreate the same
  full-height extrusion even though neither BASE nor ANVIL is now dominant.
- Next attempt: contract id-0 CORE to a lower/middle connector, limit carrier
  support to a partial bridge and cap its blend weight, then permit controlled
  storm-only detail erosion inside dense regions. Secondary deterministic towers
  and the overlapping anvil will define the visible upper silhouette.

## Iteration 24 - carrier contraction accepted; optically full floors rejected

- The contracted carrier and shorter id-0 core compile and load correctly. A
  fresh capillatus now exposes several upper lobes and windward outflows instead
  of one full-height black prism. This confirms that carrier height ownership was
  a real part of the defect.
- The severe result remains incomplete: from both west and south, the lower/mid
  cloudlets still appear as adjacent vertical columns with straight seams and a
  flat common base. Their macro masks retain non-trivial density floors and the
  storm ray integrates that floor through hundreds of blocks, making the 3-D
  noise visually subordinate.
- The same mechanism is visible in the layer families. Stratus is still a smooth
  bright lens and nimbostratus a thin bright/dark pancake: their macro functions
  retain respectively 44% and 62% density even where horizontal structure is at
  its minimum, while detail erosion is restricted to the exposed edge.
- Accepted progress: nimbostratus has a darker underside and greater physical
  thickness than the earlier 36-block profile; its precipitation remains sparse
  and no horizon-wide rain band returned.
- Next attempt: lower the optical floors while retaining a small continuity
  reserve, multiply severe tower mass by world-stable 3-D billow noise with a
  protected narrow bridge, and let sheet/storm detail act weakly inside dense
  regions. The weather envelope remains the primary silhouette and no additional
  texture lookup or pass is introduced.

## Iteration 25 - 3-D billow present but hidden by optical depth

- Production GLSL accepted the world-stable billow modulation and all automated
  build checks passed. A fresh capillatus shows vertical density variation and
  rounded upper lobes, proving the 3-D signal is active.
- The visual result is still rejected: the core remains an optically full narrow
  tower, and the isolated stratus remains a bright smooth lens. The first floor
  reduction was numerically too conservative.
- Independent read-only review quantified the cause. With density 1.45,
  extinction 0.115 and a 200-block storm path, a macro reserve of 0.08 already
  gives optical depth near 2 (about 86% opacity). A 0.16 stratus reserve across a
  broad field is effectively opaque. The protected severe bridges at 0.16–0.18
  are therefore prisms even though their scalar values look small.
- Next attempt: reduce only valley/continuity reserves to roughly 0.01–0.03 while
  leaving all peaks at 1.0, and lower dense-region erosion exposure so detail
  noise cannot perforate the newly translucent valleys. No global density change
  is used because that would regress already-recognizable cumulus.

## Iteration 26 - optical floors fixed; structural signal still saturated

- The production build with 0.01–0.03 continuity reserves loads and renders
  without shader errors. It does not fragment or introduce temporal holes.
- Fresh stratus and capillatus captures change less than expected: stratus gains
  a modest irregular top, but remains a bright lens; capillatus remains a dark
  narrow tower with only subtle internal billow separation.
- This rules out the valley floor as the only remaining cause. The structural
  noise inputs themselves sit mostly on the high side of their current
  `smoothstep` ranges, and weather coverage is still remapped to 1 above about
  0.336 after `CoverageMul=1.25`. Up to 48 unioned cloudlets exceed that value
  throughout most of a field.
- Next attempt: preserve the accepted cumulus mapping, but widen the sheet/storm
  coverage response to roughly 0.70–0.78 and move their world-stable billow
  thresholds upward. This restores usable gradients before ray integration
  rather than lowering global density or adding higher-frequency detail.

## Iteration 27 - generic thresholds rejected; exact noise distribution measured

- The widened coverage response and generic 0.50–0.76 structure thresholds load
  correctly, but fresh captures reject the balance: isolated stratus becomes too
  faint/thin while capillatus remains a mostly full dark column.
- The baked PA noise functions were evaluated directly with their exact Java hash,
  Perlin-Worley and Worley-FBM formulas. Across 4,000 uniform 3-D samples, the
  `baseCarrier` percentiles are strongly high-biased (median 0.783, 5–95% range
  0.713–0.845). The severe `0.60*baseCarrier + 0.40*lowFbm` input has median
  0.661 and 5–95% range 0.605–0.717.
- This proves that 0.50–0.76 still maps most storm samples to substantial density;
  it was not calibrated to the actual texture. Conversely, combining that high
  threshold with a 0.72 sheet coverage endpoint removed too much stratus mass.
- Next attempt: place severe billow thresholds around measured percentiles
  (approximately 0.63–0.72), use 0.52–0.68 for severe sheets, and restore an
  intermediate coverage endpoint (about 0.58 sheets, 0.68 storms). This is a
  deterministic calibration, not a new noise model.

## Iteration 28 - percentile threshold exposes shape, but becomes stippled

- Percentile-calibrated billow thresholds make the capillatus core visibly porous
  and reveal its wind-swept anvil in south/close views. This confirms that the
  earlier opaque wall was not an inactive shader branch.
- The result is still rejected: the macro transition is too binary and produces
  a strong checker/stipple pattern through the core and anvil. Full-resolution,
  history-off capture retains the same pattern, proving it is intrinsic density
  thresholding rather than half-resolution composition or temporal reprojection.
- The dedicated id-0 CORE still contributes enough density/coverage to remain the
  dominant rectangular mass even after its geometry was shortened. In parallel,
  `baseLink`/`topLink` can apply severe min/max heights at full strength, extending
  a locally dominant role through the entire connected interval.
- Next attempt: make id-0 CORE a genuinely low-density connector, soften the
  severe billow interval, reduce dense-region detail exposure, and cap severe
  min/max height blending while retaining the partial carrier bridge. Restore
  intermediate sheet coverage so stratus remains visible at normal distance.

## Iteration 29 - low-density connector accepted; role interval misuse confirmed

- The genuinely low-density id-0 connector removes the single dominant storm
  prism. Close west/south captures now expose multiple deterministic towers and
  a wind-directed outflow; the macro is no longer one opaque stamp.
- The result remains visually wrong: several bright anvil/outflow components read
  as tall vertical fins, while the lower dark mass still occupies a rectangular
  connected block. Moderate billow thresholds reduce the checker pattern but do
  not fix those role-shaped walls.
- Root cause confirmed in the shader contract: severe height union deliberately
  expands the local weather interval toward the low BASE and high ANVIL. The
  role-specific volume functions then normalize `h01` inside that expanded
  interval. Current BASE bands cover most of it from the bottom, and ANVIL uses
  `verticalBand` from roughly 0.06 to 0.91, so an anvil selected inside the
  connector becomes a tall curtain instead of a top sheet.
- Next attempt: define explicit low-only BASE masks and high-only ANVIL masks
  inside connected intervals. Outside the connector the same masks operate on
  the role's already-thin local interval, preserving overhangs. CORE/TOWER remain
  responsible for the middle; no weather-map format or resource changes.

## Iteration 30 - low/high role masks accepted; high-biased carrier creates fins

- Explicit low-only BASE and high-only ANVIL masks remove the former full-height
  anvil curtain in the west view. The central storm can no longer be filled by a
  BASE or ANVIL role across the whole connected interval.
- The close south view is still rejected: upper outflows collapse into several
  thin bright vertical fins and the storm can become too faint from the orthogonal
  side. This is the same high-biased-noise problem in a different form.
- Measured distributions explain it: `baseCarrier` is centered near 0.783, while
  `lowFbm` is centered near 0.479 with a useful 5–95% range of 0.351–0.615.
  Thresholding combinations dominated by `baseCarrier` creates narrow transition
  isosurfaces instead of broad smooth billows.
- Next attempt: drive macro billows primarily from normalized `lowFbm`, use
  directional carrier only as a secondary anisotropic term, restore a modest
  low-density connector, and remove forced dense-region detail exposure. This
  preserves the corrected role intervals while replacing binary fins with smooth
  volumetric gradients.

## Iteration 31 - centered low-FBM experiment rejected; rollback to observed balance

- Driving every severe role primarily from centered `lowFbm` removes the bright
  fins, but makes fresh capillatus nearly invisible at the normal adapted camera
  distance. The experiment is rejected as a regression.
- The full sequence establishes a practical boundary of the current 2.5-D weather
  representation: aggressive 3-D thresholds alternate between opaque extrusions,
  stochastic holes/fins and insufficient mass because one base/top interval and
  one categorical role must represent overlapping base, towers and anvil.
- The next build deliberately restores the last visually readable balance from
  iteration 24 (multiple upper lobes, visible storm mass, stable rain and no full
  carrier ellipse), while retaining safe improvements that remained valid across
  every test: native-only ownership, invisible/contracted carrier support, thicker
  nimbostratus, coherent zero-background shafts, material lighting and resource
  stability. The rejected threshold experiments will not ship.

## Iteration 32 - production-JAR visual matrix and stability baseline

- The rollback build was rebuilt, copied into the isolated Forge 47.4.20 game
  directory and observed directly in singleplayer with Simple Clouds absent.
  Runtime diagnostics reported `cloudOwner=PA_VOLUMETRIC`,
  `volumetricActive=true`, `source=fields` and `composited=true`.
- The complete accepted matrix is stored under
  `build/visual-test/captures/final-matrix`. It confirms that cirrus is thin and
  wind-aligned, stratocumulus has broad low lobes, nimbostratus is a continuous
  dark precipitation deck without the former horizon-wide rain curtain, and
  severe profiles expose a base, several towers and wind-directed outflow.
- The same matrix also rejects any claim of final visual quality: humilis is too
  small and smooth at its adapted overview distance; stratus and nimbostratus
  remain smooth lens/tablet silhouettes; capillatus and supercell structures
  remain visibly assembled from architectural cloudlet extrusions; the view
  above a severe cloud is nearly featureless white.
- Below/inside/above, sunrise, sunset and night were all captured. Below and
  inside correctly enter a dense dark whiteout, sunset receives warm pink/orange
  light and night stays readable, but sunrise can reduce the severe cloud to an
  almost black silhouette.
- A real DYNAMIC-to-TRANSITION threshold check is stored under
  `build/visual-test/captures/lod-transition`. At field-edge distances of about
  450 and 550 blocks, neither the immediate nor the six-second stabilized image
  shows a hydration pop. The expected perspective/terrain-streaming changes are
  visible; the cloud silhouette itself remains continuous. This is a still-image
  check, not proof of every frame during a flown crossing.
- A fixed-camera, unfrozen 20-second motion check is stored under
  `build/visual-test/captures/motion-stability`. With measured regional wind of
  7.3 m/s NW, the nimbostratus envelope translates coherently and remains stable;
  no independent high-frequency texture slide is apparent in the two endpoints.
  The field also advanced substantially in lifecycle when unfreezing, so this
  pair cannot isolate advection from lifecycle evolution.
- Current HIGH-preset raymarch samples are normally around 0.59-1.95 ms for the
  isolated nimbostratus/supercell scenes. Fresh-field warm-up produced two
  26.9-31.7 ms transients. Process GPU memory was stable around 639.4 MiB. Global
  FPS/frametime could not be captured because PresentMon's ETW session was denied
  without elevation; no value is inferred.
- Next attempt: replace the single categorical storm role with a cached role
  coverage map. The current weather map can retain the overall envelope while
  independent BASE, CORE, TOWER and ANVIL support reaches the raymarch. This
  directly addresses the confirmed 2.5-D information loss instead of repeating
  rejected scalar threshold changes.

## Iteration 33 - carrier fix retained; support-only storm map rejected

- A fresh production JAR added one cached RGBA8 severe-role map with independent
  BASE, CORE, TOWER and ANVIL support. It also made `CARRIER_ONLY` invisible for
  every morphology once at least three cloudlets were actually accepted by the
  current frame budget. Shader registration and world entry completed without a
  PA shader error; Simple Clouds remained absent.
- The support-only severe result under
  `build/visual-test/captures/storm-role-map/after-*.png` is rejected. A south
  view makes the upper outflow more recognizable, but the core still becomes
  several vertical extrusions and the close west view retains large wing/fins.
  Separate 2-D supports cannot repair vertical shape while every role still uses
  the same fused base/top interval.
- The non-severe carrier correction is retained. Fresh captures under
  `build/visual-test/captures/carrier-removal` show that humilis survives as
  several small lobes, stratus loses the single full-radius macro ellipse,
  stratocumulus separates into shallow patches, and nimbostratus retains a dark
  continuous rain deck. All four remain too smooth at overview distance, but the
  former hidden macro tablet is no longer the dominant visible mass.
- Conservative family adjustments are retained with that carrier fix: smaller
  central sheet tiles, more vertical tile jitter, lower layer density floors and
  a less coverage-dominated cumulus billow signal. They add no texture samples.
- The still LOD/motion screenshots from iteration 32 also contain large dark or
  black terrain polygons. Their cause is not yet confirmed. They prevent those
  image pairs from being called a clean stability validation even though no
  obvious cloud-silhouette pop appears between the endpoints.
- Next attempt: preserve the role support map but add one RGBA16F map containing
  premultiplied per-layer height endpoints. BASE, combined CORE/TOWER and ANVIL
  will then be evaluated only inside their actual vertical intervals; the global
  weather envelope remains the pretest/fallback rather than an extrusion volume.

## Iteration 34 - exact storm heights accepted structurally, morphology rejected

- A second cached map now stores premultiplied half-float endpoints for the
  severe BASE, combined CORE/TOWER and ANVIL layers. The volume decodes those
  endpoints only for profiles 4/7 and falls back to the original weather
  envelope when layer data is absent. The build, self-check and production GLSL
  load all succeed.
- Captures under `build/visual-test/captures/storm-height-map` confirm that exact
  intervals remove several former full-height role walls. They also reject the
  current morphology: numerous upper-outflow cloudlets now appear honestly as
  detached floating lobes, while the one large BASE cloudlet remains a smooth
  cylinder/lens. The support-only approximation had hidden both facts inside its
  fused interval.
- Root cause is now in the deterministic layout rather than the layer transport:
  up to roughly one third of all non-reserved storm cloudlets can become ANVIL,
  the primary anvil is displaced too far from the tower, and only one oversized
  cloudlet owns BASE. Exact transport cannot turn that source geometry into one
  attached storm.
- Next attempt: reduce secondary outflow frequency and size, pull/elongate the
  main anvil into the core, classify low storm cloudlets as additional BASE
  lobes, shrink the reserved base tile, and feed the invisible carrier into the
  convective layer only as a very weak continuity bridge.

## Iteration 35 - source layout improved; missing endpoint exposed

- Fewer/smaller secondary outflows, an attached primary-anvil placement,
  additional low BASE lobes and a 16% carrier bridge produce a much more compact
  storm in the south view. The former forest of upper fins is substantially
  reduced, so these deterministic layout changes are retained.
- The close west capture under
  `build/visual-test/captures/storm-layout-bridge/03-close-west.png` is still
  rejected: the primary anvil becomes one oversized dark floating lobe. The
  current height map stores its base but still borrows the weather map's fused
  global top. Severe top-linking can raise that value with a different role, so
  this is not an exact ANVIL interval after all.
- Next attempt: repack the two severe maps as full premultiplied layer records.
  Map 0 will carry BASE support/top and CORE+TOWER support/base; map 1 will carry
  CORE+TOWER top plus ANVIL support/base/top. Both are RGBA16F, eliminating the
  last fused endpoint while keeping the same two extra cached draws/samplers.

## Iteration 36 - full exact layers retained; capillatus core still fragmented

- The two RGBA16F maps now contain complete premultiplied records: BASE
  support/top, convective support/base/top and ANVIL support/base/top. The number
  of additional cached passes/samplers is unchanged from iteration 34.
- Supercell captures under `build/visual-test/captures/storm-exact-layers/01-03`
  are the strongest severe result so far: the primary anvil is flat, wind-swept
  and attached; secondary fins are rare; several rounded towers and low base
  lobes remain visible. It is still smoother and more cloudlet-like than desired.
- A separately forced `cumulonimbus_capillatus` capture (the ordinary helper had
  allowed an automatic supercell to repopulate during its six-second clear wait)
  is not accepted. The exact renderer exposes several narrow vertical columns
  without enough shared central mass, and its anvil is weak from two azimuths.
- Next attempt: reinterpret the already-existing invisible storm carrier only in
  the severe layer maps as a narrow, profile-dependent analytic core. It will be
  spatially shrunk before splatting, vertically contracted and given moderate
  support, so it joins towers without recreating the former field-wide ellipse.

## Iteration 37 - narrow carrier core retained; solid role lobes remain

- The narrow carrier produces a continuous central capillatus column without
  restoring field-wide coverage, so its role-map geometry is retained. A fresh
  explicitly spawned capillatus was captured before automatic repopulation or
  late lifecycle decay under `build/visual-test/captures/storm-carrier-core-fresh`.
- The result is still not accepted: the exact primary anvil and reserved base are
  optically saturated enough to reveal their individual ellipsoidal stamps. The
  west close view contains a large smooth dark anvil lobe even though its interval
  and attachment are now correct.
- Next attempt: keep the exact meteorological endpoints, but reduce the protected
  density floors per severe layer (base, convective, anvil) and slightly increase
  storm edge erosion. The 3-D noise will add internal/boundary variation instead
  of the layout being read as solid geometry; no new lookup or pass is added.

## Iteration 38 - lower severe material floors add detail but do not hide the lobes

- A fresh explicitly spawned `cumulonimbus_capillatus` was observed from west,
  south and a close western camera under
  `build/visual-test/captures/storm-material-erosion`. The test JAR contains the
  exact-layer maps and reduces the protected density floors from 0.22/0.20/0.14
  to 0.10/0.12/0.04 for BASE/CORE-AND-TOWER/ANVIL. Severe edge erosion was raised
  from 0.18 to 0.22, with 0.26 for the categorical anvil role.
- The distant west and south views are moderately improved: the storm remains
  vertically developed, the base is broader than the tower, the wind-swept top
  stays attached and more internal breakup is visible. These properties are
  retained.
- The close view is still rejected. Its primary anvil reads as one smooth oval,
  the lower BASE cloudlets read as rounded stamps, and the central tower remains
  visibly assembled from analytic volumes. Lowering scalar floors further would
  risk stipple and holes without changing those analytic boundaries.
- The terrain also contains large dark cut faces in the close view. Their cause
  is not yet attributed to the cloud composite; the next test is a fixed-camera
  native-volumetric ON/OFF comparison before any new shader change.
- Next attempt, if the black-terrain A/B test does not implicate the cloud pass:
  deform severe BASE and ANVIL footprints coherently in both exact-layer splat
  shaders with low-frequency world-stable boundary warping. This targets the
  confirmed oval boundaries without adding raymarch texture samples or changing
  the exact layer endpoints.

## Iteration 39 - dark terrain cuts are not produced by the native cloud pass

- A fixed-camera A/B/A capture is stored under
  `build/visual-test/captures/terrain-artifact-ab`. The first and third images
  have `/pa cloud render on`; the middle image has `/pa cloud render off`.
- The large exposed dark terrain faces are pixel-for-pixel stable in all three
  observations while the command feedback confirms that the volume renderer was
  disabled and re-enabled. This rules out the native volume raymarch/composite as
  their cause for this scene.
- The shapes coincide with unloaded or not-yet-rendered terrain boundaries seen
  from a very high camera. Their exact vanilla/Forge cause is outside this visual
  cloud pass and remains unconfirmed, so this result is not generalized beyond
  ruling out PA's native volumetric renderer in the controlled A/B scene.
- Next attempt: add identical, world-stable low-frequency boundary deformation to
  both severe exact-layer splat shaders, stronger for BASE and ANVIL and weaker
  for the convective core. The two maps must remain geometrically congruent so
  premultiplied height endpoints decode against the same support.

## Iteration 40 - shared inward contour erosion retained but insufficient

- The production JAR now loads one namespaced GLSL include from all four cached
  weather-map passes. Forge 47.4.20 resolved the import and registered every PA
  shader in the real client. The contour deformation only erodes inward, applies
  in the outer footprint ring and therefore keeps weather coverage, morphology,
  severe support and premultiplied endpoints congruent.
- A fresh three-cluster `cumulonimbus_capillatus` was observed west, south and
  close-west under `build/visual-test/captures/storm-contour-erosion`. The lower
  support has a less perfectly analytic fringe and no new holes or detached
  endpoint artefacts are visible, so the conservative shared erosion is retained.
- The visual result remains rejected. At close range the primary anvil is still
  one very large smooth lobe, and the storm base reads as a nearly straight
  horizontal bar assembled from overlapping ovals. Directional erosion cannot
  turn one oversized source cloudlet into a naturally branching outflow.
- Next attempt: replace the one-lobe primary anvil in the deterministic severe
  layout with a small chain of attached, overlapping ANVIL cloudlets whose sizes,
  offsets and vertical thickness remain deterministic. Apply the same principle
  conservatively to the reserved base support, while preserving exact layer roles
  and the strict global cloudlet budget.

## Iteration 41 - deterministic multi-lobe severe layout rejected

- The first three-view capture was contaminated by automatic fields, so no visual
  conclusion was drawn from it. A second sequence then cleared the regions,
  froze native cloud movement, parsed the newly spawned capillatus centre and
  captured west/south/close-west within seconds. Those controlled images are in
  `build/visual-test/captures/storm-multilobe-isolated-fast`.
- The layout reserved IDs 2-4 for a wind-aligned anvil chain and IDs 5-6 for side
  base lobes while retaining the same requested/accepted cloudlet budget. The
  Gradle build and expanded stable-role self-check passed.
- The result is rejected. From west, the upper lobes separate into a detached
  petal while the middle layer forms a platform; from south, the anvil loses its
  recognizable outflow; close-west, the convective body is cohesive but the
  intended anvil is a very faint detached remnant and the base remains a shelf.
- The previous single attached anvil source layout is therefore restored. The
  next attempt must reshape density inside that continuous support in the volume
  shader, where noise and wind direction can scallop/taper the outflow without
  splitting exact meteorological layers into disconnected cloudlets.

## Iteration 42 - overlap-only cohesion removes stamps but over-erodes the anvil

- The rejected multi-lobe layout was fully restored and its temporary validation
  assertions removed. The original deterministic core/base/anvil ordering again
  passes the CloudField self-check.
- In the exact severe material, uniform BASE/convective/ANVIL density floors were
  replaced with 3-D texture-driven mass plus small cohesion masks that activate
  only at strong support overlap. The production JAR registered successfully and
  a fast frozen capillatus sequence was captured under
  `build/visual-test/captures/storm-cohesive-material`.
- The isolated close view is more cohesive and no longer exposes the previous
  forest of solid role stamps. The base is less bar-like and the convective body
  reads as one continuous updraft. This part of the material change is retained.
- The result is still rejected: the new anvil texture threshold is too strict,
  so the wind-swept top is absent or only a faint remnant from west/south. The
  surviving convective envelope is also too smooth and reads as one tall rounded
  mushroom rather than a billowing tower.
- Next attempt: restore moderate anvil mass without reintroducing a constant
  footprint floor, and raise the storm base-noise spatial frequency modestly so
  the existing lookup yields multiple medium-scale billows across the tower.

## Iteration 43 - overlap material retained; material-driven height cutoffs rejected

- The severe base-noise scale was raised from 0.0046 to 0.0062 with no extra
  lookup. Global per-role cohesion floors were replaced by soft fills restricted
  to BASE/convective and convective/ANVIL support intersections. The anvil texture
  threshold was also relaxed without restoring a footprint-wide floor.
- A frozen fresh field was captured at `typeTicks=14` under
  `build/visual-test/captures/storm-overlap-material`, then the same surviving
  field was recaptured mature at `typeTicks=2022`, growth 1.00 and density 0.95
  under `build/visual-test/captures/storm-overlap-material-mature`.
- The mature result confirms that the separated horizontal bands are not merely
  an early lifecycle state. BASE, convective body and the small outflow remain
  vertically disconnected from west/south, and the close view contains a clear
  empty seam between the lower and middle masses.
- Root cause: exact endpoints describe each role honestly but do not guarantee
  that adjacent role intervals overlap. Material-driven early top cutoffs and a
  slow anvil fade-in amplify that source gap. The overlap-only material and the
  denser severe noise are retained; the variable base/tower cutoffs are rejected.
- Next attempt: where adjacent 2-D supports overlap, lower the convective and
  anvil interval bases just enough to overlap the preceding exact layer. Restore
  near-full BASE/tower vertical bands and make the anvil begin promptly. Pixels
  without role overlap keep their original exact endpoints.

## Iteration 44 - vertical bridges retained; constant-width extrusion rejected

- Convective bases were lowered by at most eight blocks into overlapping BASE
  support, and ANVIL bases by at most ten blocks into overlapping convective
  support. BASE/tower bands again use nearly their full exact interval and the
  anvil begins promptly. The build and real GLSL load succeeded.
- Fresh west/south/close images are stored under
  `build/visual-test/captures/storm-vertical-bridges-fresh`; the same surviving
  field at `typeTicks=2413`, growth 1.00, density 0.96 and radius 423.6 is under
  `build/visual-test/captures/storm-vertical-bridges-mature`.
- The former empty horizontal seams are gone, so the overlap-restricted endpoint
  bridges are retained. They do not inflate pixels where adjacent role supports
  are separate.
- Both lifecycle states remain visually rejected. The exact convective support is
  effectively extruded at constant horizontal width through most of its vertical
  interval, creating tall parallel pillars. The single primary anvil is still a
  large smooth detached leaf above the tower in the mature close view.
- Next attempt: add a height-dependent horizontal taper to the exact convective
  support, matching the already-proven fallback severe profile; lower, shrink and
  pull the one primary anvil closer to the core; and reduce storm noise frequency
  from the over-sharp 0.0062 to an intermediate 0.0052.

## Iteration 45 - tapered fresh tower improved; mature carrier monolith rejected

- Exact convective support now narrows with height, the primary anvil is lower,
  smaller and closer to the core, its overlap-only root fill is stronger, and the
  storm base-noise scale is 0.0052. Build, self-check and production shader load
  all passed.
- Fresh captures under `build/visual-test/captures/storm-tapered-core-fresh` are
  the best early-stage result in this sequence: the former parallel pillars merge
  into several tapered billows and the lateral outflow is attached rather than a
  leaf far above the core.
- The same field at `typeTicks=2070`, growth 1.00 was captured under
  `build/visual-test/captures/storm-tapered-core-mature`. It is rejected: at
  maturity the central mass becomes a tall flat-topped analytic block and the
  reserved base becomes one oversized round lobe.
- This matches the remaining severe-only carrier encoding: for profile 4 the
  invisible field carrier is still splatted into the convective exact maps at
  radius scale 0.34, support 0.72 and vertical range 0.08..0.92. At mature density
  it dominates real CORE/TOWER cloudlets even though it is hidden elsewhere.
- Next attempt: retain a much narrower/weaker carrier only as a continuity hint,
  contract its vertical interval, and moderately shrink the one reserved BASE
  lobe. Do not alter the real tower cloudlets, global budget or fallback LOD.

## Iteration 46 - weak carrier removes the block but exposes one extruded core

- The shared severe carrier is now radius 0.24, support 0.38 for capillatus and
  vertical range 0.14..0.86; the reserved non-spiral BASE radius/span is 0.37/0.20.
  Both exact-layer passes use the same include constants. Build, self-check and
  production shader load passed.
- Fresh captures under `build/visual-test/captures/storm-weak-carrier-fresh` and
  the same field mature at `typeTicks=2930` under
  `build/visual-test/captures/storm-weak-carrier-mature` are rejected. The mature
  central block is gone, but the storm becomes one tall pointed sail/horn; the
  fresh close view similarly contains only a few sharp vertical teeth.
- The remaining source is no longer the carrier. Reserved cloudlet ID 0 is one
  CORE spanning 82% of the full field height. Exact support plus height taper can
  only turn that single extrusion into a cone; it cannot create stacked billows.
- Next attempt: shorten the primary CORE while preserving low-LOD continuity,
  reserve IDs 3 and 4 as overlapping mid/upper TOWER anchors with decreasing
  radius, and reduce the carrier to a genuinely weak last-resort bridge. BASE and
  the single attached ANVIL remain separate roles and the total cloudlet count is
  unchanged.

## Iteration 47 - stacked tower anchors improve vertical staging but remain 2.5-D

- Reserved severe cloudlet IDs 3 and 4 are now deterministic mid/upper TOWER
  anchors. The primary CORE is shorter, while BASE and ANVIL keep their distinct
  roles. The strict 64-cloudlet budget is unchanged and the expanded stable-role
  self-check passes.
- A fresh four-cluster `cumulonimbus_capillatus` at `typeTicks=15`, radius 393.3,
  density 0.81 and growth 0.03 was observed west, south and close-west under
  `build/visual-test/captures/storm-stacked-towers-fresh`. The same surviving
  field was observed mature under
  `build/visual-test/captures/storm-stacked-towers-mature`.
- The change is a real but insufficient improvement over iteration 46: the
  previous single sail/horn becomes a staged vertical body. From the south,
  however, it reads as a pyramid or rocket capped by a round ball. From the west
  and at close range, the stacked anchors merge into a rectangular extruded
  mass. The reserved BASE remains an oversized rounded stamp, and the ANVIL is
  either a thin side projection or a detached speck rather than a broad attached
  outflow.
- The scenes report 49 weather cells, 48 requested/accepted cloudlets and one
  visible field. At 1920x1009, log samples adjacent to the captures are 3.665 ms
  fresh-distant and 6.318 ms fresh-close, then 3.016 ms mature-distant and
  4.511 ms mature-close (with a 5.947 ms pre-teleport sample). These are
  GPU-query times for the raymarch draw only, not a controlled inter-iteration
  benchmark, whole-frame frametimes or FPS.
- The result is rejected. Both exact storm passes accumulate all CORE/TOWER
  contributions into one support and one weighted base/top interval per texel.
  Consequently, vertically distinct anchors collapse back into a single 2.5-D
  extrusion before the raymarch samples them. This packing path must be verified
  and corrected before another morphology tuning attempt.
- Next attempt: preserve separate lower-core and upper-tower support/height
  intervals through the cached weather maps, or prove an equivalent packing that
  does not average vertically separate towers together. Do not add more layout
  anchors until the renderer can retain their separation.

## Iteration 48 - exact CORE/TOWER split works technically but remains a cone

- A third cached RGBA16F severe map now carries TOWER support plus premultiplied
  base/top endpoints. The original structure/height maps retain BASE, CORE and
  ANVIL. The volume shader decodes CORE and TOWER as separate intervals, and the
  two 3-D noise samplers moved from texture units 8/9 to 9/10 after the ninth JSON
  sampler was added.
- The full Gradle build and CloudField self-check passed. In the real Forge
  47.4.20 client, all PA shaders registered, Simple Clouds was absent, the GPU
  reported 32 fragment texture units for 11 required, and the native pass rendered
  after one expected resource-creation frame logged as `raymarch_not_ready`.
- The three observed views are stored under
  `build/visual-test/captures/storm-split-core-tower-fresh`. Despite the directory
  name, they are not a fresh-state validation: the list immediately before the
  captures reports `typeTicks=669`, growth 1.00, density 0.90 and radius 225.2.
- The result is rejected. Separating CORE from TOWER removes the earlier stacked
  rectangular block, but the surviving convective body reads as one tall tapered
  cone/veil with a flat lower cutoff. West and south also show a dark detached
  rectangular layer below the upper body in some frames. It may be BASE or a
  precipitation contribution; its source is not yet confirmed and must be
  isolated before changing either system. No broad attached capillatus anvil is
  recognizable.
- Samples adjacent to the captures are approximately 1.652 ms distant-west,
  3.033 ms distant-south and 5.491 ms close-west for the raymarch draw at
  1920x1009. Initial/warm-up samples reached 24-34 ms. These are observations,
  not a controlled before/after benchmark.
- Next attempt: perform a fixed-camera precipitation/BASE isolation and capture a
  genuinely early lifecycle field within a few seconds. If the detached slab is
  BASE, correct its interval connection; if it is precipitation, correct the rain
  shaft envelope independently. The cone itself shows that aggregating every
  TOWER into one interval still loses too much vertical staging.

## Iteration 49 - fixed-camera A/B rules out precipitation as the detached slab

- The client was positioned at a fixed south view of the surviving capillatus.
  `build/visual-test/captures/storm-split-precip-ab/01-precip-on.png` uses the
  production shader. For the paired diagnostic build only, the primary view ray
  called `cloudDensity(..., includePrecipitation=false)`; the saved player camera
  and field were then reloaded without changing morphology code. Its capture is
  `02-precip-off.png`.
- The pale falling tail disappears when precipitation density is disabled, but
  the dark rectangular slab remains detached below the upper cloud in the same
  screen position. This confirms that the tail is precipitation while the slab
  is cloud mass, not a rain shaft. Given the exact-layer packing and its low
  position, BASE is the leading source; that attribution remains to be verified
  by correcting only the BASE/convective intersection rather than by assumption.
- The diagnostic precipitation disable is not a fix and is reverted immediately.
  Rain shafts remain part of the native production path.
- Next attempt: keep BASE density unchanged outside intersections, but add a
  material bridge only where BASE overlaps a lower convective support. Reclassify
  high TOWER cloudlets separately so the third exact map represents an upper
  interval instead of averaging every tower from bottom to top into one cone.

## Iteration 50 - BASE root retained; upper interval rejected

- High random towers plus reserved ID 4 were classified as `UPPER_TOWER` and
  written to the third exact map; CORE and lower TOWER cloudlets shared the lower
  convective interval. A narrow material root was added only inside BASE/lower or
  BASE/upper support intersections. Build, self-check and real shader loading
  passed.
- A genuinely fresh four-cluster field at `typeTicks=5`, growth 0.01, density
  0.77 and radius 352.2 is stored under
  `build/visual-test/captures/storm-upper-root-fresh`. The same field at
  `typeTicks=1186`, growth 1.00 and density 0.92 is stored under
  `storm-upper-root-mature`.
- The detached dark BASE slab is gone in both lifecycle states. The narrow root
  joins the low shelf without filling its whole footprint and is retained.
- The upper interval is visually rejected. Fresh, the west silhouette is a
  triangular mountain, the south view is a compact bundle of cylinders/boxes,
  and the close view is a smooth obelisk. Mature, it becomes a tall rectangular
  wall with a rounded cap and small side lobes. The anvil remains too small and
  no multi-level billowing is recognizable.
- The exact maps still expose one height-independent XZ support throughout each
  encoded interval. Splitting that extrusion twice changes staging but not the
  underlying vertical-wall silhouette. A further exact layer would repeat the
  same limitation.
- Adjacent raymarch samples are about 4.644 ms mature-distant and 5.905 ms
  mature-close at 1920x1009; fresh close samples were roughly 6.2-7.2 ms after
  warm-up. This is slower than the approximately 3.0/4.5 ms distant/close samples
  from iteration 47, although the scenes are not a strict benchmark.
- Next attempt: remove the visually ineffective third target and recover its
  sampler/pass cost. Keep the proven BASE root, then make the existing severe
  support cross-section vary continuously with height using world-stable wind
  shear and cross-wind displacement. The macro support must stop being a straight
  vertical extrusion before any further layer count or scalar tuning.

## Iteration 51 - height-sheared support rejected

- The third target, shader pass, sampler and `UPPER_TOWER` role were removed.
  Texture units returned to the original 0-7 2-D plus 8/9 3-D layout; the runtime
  check now requires 10 fragment units. The retained two-map severe support was
  sampled along a modest wind/height shear curve, while a world-stable phase
  perturbed only the outer convective support ring.
- Fresh captures at `typeTicks=5` are under
  `build/visual-test/captures/storm-height-shear-fresh`; the same field mature at
  `typeTicks=1007`, growth 1.00 and density 0.97 is under
  `storm-height-shear-mature`.
- The result is rejected. The fresh body remains an obelisk with a thin curved
  side extrusion. Mature, the south view is still a capped tapered block and the
  close west view develops an extreme needle plus a curved vertical curtain.
  Height-dependent lookup displacement changes where the extrusion appears but
  does not create independent billows.
- The BASE root remains attached, confirming that fix independently. The shear
  and sinusoidal support perturbation are removed.
- Next attempt: change the exact map reduction itself. For each role group and
  texel, retain the strongest cloudlet's support and its base/top endpoints in
  both passes instead of smooth-union support plus support-squared averaged
  heights. The identical winner rule must preserve structure/height parity.

## Iteration 52 - dominant-cloudlet endpoints retained; max support too sparse

- Both exact shaders selected the same strongest cloudlet per role group and
  texel, using its support and premultiplied endpoints. No extra target or
  sampler was used. Build, self-check and production GLSL load passed.
- Fresh captures at `typeTicks=5`, radius 364.0 and density 0.79 are under
  `build/visual-test/captures/storm-dominant-cloudlet-fresh`; the same field at
  `typeTicks=1346`, radius 403.6 and density 0.96 is under
  `storm-dominant-cloudlet-mature`.
- No winner-boundary seams are visible. The fresh south silhouette is more
  cumulus-like than the averaged slab, which supports retaining dominant local
  endpoints. Max-only support is nevertheless too sparse: secondary lobes vanish
  and the large reserved CORE/BASE shapes dominate west and close views.
- Mature, the single dominant ANVIL becomes a very thick analytic roof attached
  to a vertical wall. Its reserved vertical span is 24% of the full severe field,
  while the primary CORE still spans 62%; the renderer is now exposing these
  source proportions rather than hiding them in averages.
- Next attempt: retain dominant endpoints but restore smooth-union support so
  neighboring lobes contribute mass. Reduce only the oversized deterministic
  severe anchors: shorten CORE, make BASE smaller/thinner, and make ANVIL
  broader horizontally but substantially thinner vertically. Do not restore
  endpoint averaging.

## Iteration 53 - hybrid support is continuous but still exposes vertical sheets

- The two exact severe passes now use smooth-union support while preserving the
  strongest local cloudlet's base/top endpoints. The deterministic capillatus
  CORE was shortened from 62% to 46% of the field depth, BASE from 20% to 14%,
  and ANVIL from 24% to 11-14%; the anvil footprint was widened and elongated
  along the real wind. The full Gradle build, tests and CloudField self-check
  passed, and the production Forge 47.4.20 client loaded every PA shader with
  Simple Clouds absent.
- Early-growth observations are stored under
  `build/visual-test/storm-hybrid-dominant-fresh`. The field was listed at
  `typeTicks=5` immediately before positioning and at `typeTicks=713` after the
  three captures, so these images represent approximately the first 15-20
  seconds of growth rather than the exact five-tick state. Mature observations
  at `typeTicks=1362`, density 0.95 and radius 424.2 are under
  `build/visual-test/storm-hybrid-dominant-mature`.
- The result is rejected. The smooth support union improves continuity, but the
  early distant silhouette remains a narrow dark column with a detached-looking
  mushroom cap. Close inspection exposes several vertical fins, a circular
  cavity and a large smooth upper lobe. Mature, the west view is still a curved
  wall with a thin roof; the close view shows an elongated analytic surfboard
  anvil, horizontal banding and an almost planar convective face. The north view
  collapses into a compact dark cube. None is a recognizable natural
  cumulonimbus.
- Reducing anchor spans alone therefore cannot repair the macro shape. Each
  cloudlet still contributes one XZ footprint extruded between a base and top;
  unioning these supports preserves that extrusion as a vertical sheet. The
  volume shader's noise erodes the sheet but does not define a height-dependent
  macro cross-section. The isolated-looking top also shows that the single broad
  deterministic ANVIL remains too dominant even after thinning.
- An adjacent mature distant sample was 2.846 ms and stable close/near samples
  were roughly 6.8-9.4 ms at 1920x1009. These samples are not a controlled FPS
  benchmark, but the two-map path has recovered the sampler/pass cost of the
  rejected third target.
- Next attempt: stop treating the severe exact intervals as height-independent
  prisms in the raymarch. Preserve the exact base/top/support maps, but apply a
  role-specific, height-dependent macro envelope inside each decoded interval:
  a broad attached lower base, narrowing then billowing convective tower, and a
  wind-aligned anvil that grows laterally only near its own altitude. The
  envelope must operate on world-stable coordinates and must not displace the
  entire support lookup as the rejected iteration 51 did.

## Iteration 54 - coherent dominant support is the better reduction

- This controlled A/B kept every reduced deterministic anchor from iteration 53
  but made each exact output use the dominant cloudlet's support together with
  that same cloudlet's endpoints. This removes the invalid combination of a
  multi-cloudlet union footprint and one winner's vertical interval. Build,
  tests, self-check and production shader loading all passed.
- Early-growth observations are under
  `build/visual-test/storm-dominant-reduced-fresh`; the spawned field was listed
  at `typeTicks=4`, radius 337.8 and density 0.80 immediately before camera
  positioning. Mature observations at `typeTicks=1595`, radius 380.1 and density
  0.98 are under `build/visual-test/storm-dominant-reduced-mature`.
- The result is materially better than iteration 53 and the dominant reduction
  is retained. The fresh west view is a connected triangular convective tower
  with a dark base and side lobes instead of a mushroom, fins and a circular
  cavity. The mature close-west view remains connected and has readable lateral
  billows. This validates both the reduced anchors and local support/endpoint
  coherence.
- It is not acceptable yet. Fresh close-west still exposes a tall rectilinear
  central face; north shows stacked rounded blocks. Mature west is too narrow,
  mature north becomes a detached-looking `T`, and the upper lobe can float
  optically above the tower. The section of each winning footprint is still
  nearly constant through most of its encoded interval.
- Adjacent mature samples were about 2.755 ms distant and 5.718 ms close at
  1920x1009. These are observations rather than a controlled benchmark.
- Next attempt: keep this coherent reduction and add only an in-volume
  height-dependent contour. Use the unsaturated raw role support as an implicit
  horizontal distance: BASE contracts gently near its top, the convective tower
  narrows through its waist and re-expands into an upper billow, and ANVIL opens
  gradually from a real core overlap. Do not add samplers or move the map lookup.

## Iteration 55 - raw-support height contour fragments the lifecycle

- `stormStructureShape()` received a height-dependent contour with no new
  texture fetch: BASE contracts near its crown, convection narrows through a
  waist and releases into an upper billow, and ANVIL opens from a core overlap.
  The contour threshold used the unsaturated raw support. Build, tests,
  self-check and production GLSL loading passed.
- Early-growth observations are under
  `build/visual-test/storm-height-contour-fresh`; the large field was listed at
  `typeTicks=4`, radius 396.1 and density 0.78 before positioning. Mature
  observations at `typeTicks=942`, radius 410.0 and density 0.95 are under
  `build/visual-test/storm-height-contour-mature`.
- The result is rejected. During growth, the raw support is still multiplied by
  lifecycle/hydration, so the new geometric thresholds remove entire portions
  of the parent volume. Distant views show a small core, floating roof and
  separate side pieces; close views show holes, spikes and detached bright
  fragments. At maturity the west/north silhouettes collapse back into a small
  capped block, while the close view retains the same disconnected pieces.
- This is not a failure of shader loading or the two-map reduction. It is a
  scale mismatch: the contour treated a lifecycle-weighted optical support as a
  stable signed-distance field. The already remapped `baseSupport`,
  `coreSupport` and `anvilSupport` deliberately normalize the normal support
  range and are safer geometric signals.
- Adjacent mature samples were about 2.690 ms distant and 5.864 ms close at
  1920x1009. The extra ALU did not create an obvious raymarch regression in this
  scene, but the visual result is invalid.
- Next attempt: keep the same smooth height curves but feed them the normalized
  role supports and lower the waist thresholds. The root bridges remain
  authoritative at BASE/CORE and CORE/ANVIL intersections. If this still
  fragments, remove the height contour and retain iteration 54 as the stable
  baseline rather than hiding the defect with fill floors.

## Iteration 56 - normalized contour preserves mass but detaches the cap

- The contour now uses normalized `baseSupport`, `coreSupport` and
  `anvilSupport`; its thresholds were lowered. Build, tests, self-check and the
  real GLSL reload passed. Early-growth captures are under
  `build/visual-test/storm-normalized-height-contour-fresh` for a field listed at
  `typeTicks=5`, radius 401.3 and density 0.80. Mature captures are under
  `storm-normalized-height-contour-mature` at `typeTicks=1258`, radius 437.3 and
  density 0.98.
- Normalization fixes iteration 55's missing chunks: the main body survives from
  youth through maturity. The result is nevertheless rejected. West remains a
  narrow capped column, north reads as a compact box with a thin roof, and the
  close views show a huge arched upper mass separated from the convective body
  by a visible gap. The anvil-height lens turned an already dominant broad
  anchor into an isolated optical cap instead of a connected outflow layer.
- The convective body itself is somewhat less rectilinear than iteration 54, so
  its normalized non-monotonic contour remains worth isolating. BASE did not
  gain visible value from its additional contour. ANVIL requires an explicit
  material root and a smaller deterministic footprint, not a second vertical
  lens inside its already-thin interval.
- Adjacent mature samples were about 3.042 ms distant and 8.189 ms close at
  1920x1009. Scene differences prevent treating this as a strict regression.
- Next attempt: restore the direct BASE and ANVIL masses, retain only the
  normalized convective contour, add a narrow CORE-to-ANVIL root across real
  overlap, and reduce the deterministic anvil footprint. This is a role-local
  correction; no support floors, look-up shifts, textures or passes are added.

## Iteration 57 - role-local contour removes the arch; cap root becomes a shelf

- BASE and ANVIL returned to their direct interval material, only the normalized
  convective contour remained, the deterministic anvil was reduced, and a
  textured CORE-to-ANVIL root was added only at support overlap. Build, tests,
  self-check and real GLSL loading passed.
- Early-growth captures are under
  `build/visual-test/storm-role-local-contour-fresh` for a field listed at
  `typeTicks=4`, radius 310.3 and density 0.78. Mature captures are under
  `storm-role-local-contour-mature` at `typeTicks=1514`, radius 355.7 and density
  0.95.
- The huge detached arch from iterations 55-56 is gone. Fresh distant west is a
  connected rising tower and north has several readable lobes. This validates
  restoring the direct anvil and reducing its anchor. The new cap root is still
  rejected: close fresh and mature views expose it as a bright horizontal shelf
  spanning the middle of the cloud. Mature north also shows two flat bright
  bands, while west remains an unnaturally narrow upright column.
- The shelf confirms that a root built after 2-D role reduction cannot recover
  the lost per-cloudlet cross-section. In both exact splat shaders,
  `edge01 = 1 - footprint` stays zero across most of a cloudlet interior, so
  `localBase/localTop` remain constant until the thin falloff ring. The maps
  therefore encode flat prisms before `stormStructureShape()` runs.
- Adjacent mature samples were about 2.782 ms distant and 5.714 ms close at
  1920x1009.
- Next attempt: remove the convective contour and cap root. While each splat
  shader still has the exact rotated local radius `r`, use one shared include
  function to curve base/top endpoints continuously across each role footprint:
  nearly-flat BASE, domed CORE/TOWER and thin lens ANVIL. This preserves the
  current two maps and winner coherence while fixing the source representation
  rather than post-processing its prism.

## Iteration 58 - curved endpoints remove shelves but expose carrier/monolith

- A shared `paSevereCurvedLayerRange()` now computes role-specific radial
  base/top endpoints while exact splat shaders still have the rotated local
  radius. BASE remains nearly flat, CORE/TOWER use domed tops, and ANVIL is a
  thin lens. The post-reduction core contour and cap root were removed. Build,
  tests, self-check and real GLSL loading passed.
- Early-growth captures are under
  `build/visual-test/storm-curved-endpoints-fresh` for a field listed at
  `typeTicks=5`, radius 263.2 and density 0.78. Mature captures are under
  `storm-curved-endpoints-mature` at `typeTicks=1885`, radius 323.2 and density
  0.95.
- This is a partial improvement. The bright horizontal shelf is gone and close
  mature views have a continuously rounded crown rather than a flat roof. The
  shared endpoint function also eliminates structure/height formula drift.
- Two defects remain confirmed. Fresh west/north show a narrow rectangular tail
  below the cloud; it disappears at maturity as real cloudlets strengthen. Role
  6 (`CARRIER_ONLY`) was the leading hypothesis here, not yet a confirmed cause.
  Mature close views still read as one tall smooth
  monolith because the deterministic CORE and two reserved TOWER anchors overlap
  too near the same axis. North shows the thin wind-aligned anvil, but secondary
  convective billows do not separate enough to break the central wall.
- Adjacent mature samples were about 2.911 ms distant and 5.777 ms close at
  1920x1009.
- Next attempt: exclude role 6 only from the exact severe maps; the ordinary
  weather/morphology fallback remains available if no real cloudlet exists.
  Narrow the primary CORE and move the two reserved TOWER anchors farther from
  the axis so the already-curved endpoints form overlapping billows rather than
  one coincident column.

## Iteration 59 - carrier exclusion works; separated towers do not

- Role 6 was removed from both exact severe passes while remaining in the
  ordinary weather/morphology fallback. The primary CORE was narrowed and the
  two deterministic TOWER anchors were moved farther across/along the wind.
  Build, tests, self-check and real GLSL loading passed.
- Early-growth captures are under
  `build/visual-test/storm-curved-separated-fresh` for a field listed at
  `typeTicks=4`, radius 283.1 and density 0.80. Mature captures are under
  `storm-curved-separated-mature` at `typeTicks=1457`, radius 311.2 and density
  0.97.
- The most obvious single tail from iteration 58 was absent in this field, but
  later iteration 60 shows a similar tail even with role 6 excluded; carrier
  attribution is therefore not confirmed. The result is rejected. Farther tower offsets create
  several narrow hanging legs, a split upper body and rectangular notches. At
  maturity west/north still read as stacked blocks, while close west becomes a
  tall wall with deep slots instead of overlapping convective billows.
- The endpoint rounding is retained because it removed flat crowns and shelves.
  The tower separation is reverted: in this two-map representation, overlapping
  anchors are necessary for continuous mass, even if their combined silhouette
  remains smoother than ideal.
- Next attempt: restore the iteration 58 CORE/TOWER positions and sizes, keep
  exact carrier exclusion and curved endpoints, then revalidate fresh and mature
  states. Treat that as the stable severe baseline; larger visual gains require
  a representation with more than one convective interval or a true volume, not
  more layout offsets hidden by the same reduction.

## Iteration 60 - overlapping curved baseline remains an extruded wall

- The iteration 58 overlapping CORE/TOWER positions were restored while curved
  endpoints and exact carrier exclusion remained. Build, tests, self-check and
  real GLSL loading passed. Early-growth captures are under
  `build/visual-test/storm-stable-curved-fresh` for a field listed at
  `typeTicks=4`, radius 285.3 and density 0.78. Mature captures are under
  `storm-stable-curved-mature` at `typeTicks=2335`, radius 294.2 and density 0.94.
- The result is rejected as a final severe renderer. Curved crowns help locally,
  but fresh and mature close views still contain a large vertical wall, bright
  internal spires and rectangular side protrusions. The narrow falling tail is
  also present with role 6 excluded, disproving the earlier carrier attribution;
  it is compatible with the precipitation tail isolated by the iteration 49 A/B
  and requires a separate rain-shaft validation.
- The exact severe reduction has now failed under averaged endpoints, dominant
  endpoints, support union, max support, an extra interval, height shear,
  raymarch contours, radial endpoint curves and several anchor layouts. Before
  investing in a true volume representation, the existing family-level severe
  macro shape must be compared directly against the same field.
- Next attempt: controlled shader A/B with exact severe decoding disabled and
  `familyMacroShape()` handling profiles 4/7. Do not delete exact resources or
  claim an improvement until the fallback is observed fresh and mature.

## Iteration 61 - family-level severe fallback is worse

- For this controlled A/B only, `useStormStructure` was forced false while the
  same native field data, renderer, noise, lighting and precipitation paths
  remained active. Build, tests, self-check and production shader loading passed.
- Early-growth captures are under
  `build/visual-test/storm-family-fallback-fresh` for a field listed at
  `typeTicks=5`, radius 324.6 and density 0.79.
- The fallback is rejected without needing to promote it. Distant views still
  show a compact block, while close west develops an extreme bright needle far
  above a dark rectangular base. This is visibly worse than the exact curved
  baseline and confirms that `familyMacroShape()` cannot replace the role maps
  for severe profiles in its current form.
- The exact path is restored. The verified stable severe baseline is therefore:
  coherent dominant support/endpoints, shared radial endpoint curves, overlapping
  deterministic towers, reduced anvil and no exact carrier. It is an improvement
  over the earlier walls/shelves/arches but remains visually incomplete.
- Required future architecture: retain at least two independent convective
  intervals per texel or sample a true sparse/3-D severe density representation.
  A single combined CORE/TOWER interval cannot preserve multi-level billows.

## Iteration 62 - clean native cumulus baseline confirms a flattened envelope

- The severe field was removed and a fresh `cumulus_humilis` was spawned alone
  in `region[0,0]`, with cloud drift and daylight frozen at noon. The field reported
  `typeTicks=5`, center `(13.5, 257.1, -8.6)`, radius `63.8`, density `0.48`,
  coverage `0.47` and growth `0.01`. The renderer slab was approximately
  `238.1..290.9`, with about 21 accepted weather cells.
- Real in-game captures from west, north and close west are stored under
  `build/visual-test/cumulus-humilis-clean`. They are not contaminated by the
  previous capillatus field.
- The profile is visible, but rejected as a final cumulus. Distant views read as
  a thin, bright horizontal lozenge. The close view exposes one broad white
  upper mass, a nearly planar dark underside and a thin secondary strip below
  it. Individual lobes do not preserve enough independent vertical relief to
  make the cloud unmistakably cellular.
- Code inspection confirms two contributing mechanisms. In
  `cloud_weather_splat.fsh`, the comment says the dominant cloudlet decides the
  envelope, but `baseAccum/topAccum` are still divided by the sum of squared
  weights. Overlapping cloudlets therefore average their different endpoints.
  In addition, `edge01 = 1 - footprint` remains zero through most of each
  cloudlet interior, so the nominal 48% cumulus top collapse occurs only in the
  narrow footprint fade rather than producing a dome across the cloudlet.
- Next attempt: keep the cumulus base nearly level, curve each PUFF endpoint
  continuously with its already-available local radius, and use a smooth
  high-order cumulus weight so the local strongest lobe controls the envelope
  without a hard winner switch. Sheet, filament and severe profiles remain on
  their existing reductions. The same field type and viewpoints must be
  repeated after a full shader reload before accepting the change.

## Iteration 63 - quartic PUFF weighting creates a dominant spike

- The first targeted PUFF change curved the top continuously from local radius,
  kept the base nearly flat and raised cumulus endpoint weighting from squared
  to quartic coverage. Build, tests, sandbox, packaging and the production GLSL
  reload all passed.
- The restored field entered dissipation during an initial capture set, so that
  set under `cumulus-puff-radial-01` is diagnostic only. A new isolated field
  was then spawned and allowed to mature. The accepted comparison under
  `cumulus-puff-radial-mature` was captured at `growth=1.00`, `decay=0.00`,
  `typeTicks=750..2167`, center `(-21.0, 254.7, 17.0)`, radius `61.9`, density
  `0.55`, coverage `0.58`, 20 accepted cloudlets and slab `236.7..290.4`.
- The result is rejected. West is less slab-like and has a more distinct central
  crown, proving that the radial endpoint acts on the visible silhouette. North
  remains a smooth horizontal lozenge. Close west exposes a narrow triangular
  spike above a broad soft body, plus the old thin underside strip. The quartic
  weighting and 60% radial top collapse let one high local lobe control too
  small an area instead of producing several fused billows.
- The two west captures five seconds apart retain the same topology. No temporal
  double contour is visible in this stationary test. Stable nearby GPU samples
  after the region-generation spike were about `2.97 ms`; this is not yet a
  controlled before/after performance comparison.
- Next attempt: use a dedicated cubic PUFF reduction, selected only when the
  categorical profile winner is profile 3. Reduce the radial top collapse and
  vary it by role (LOBE, CORE, TOWER, BASE/MACRO). All other families keep their
  original averaged endpoints.

## Iteration 64 - cubic role-aware endpoints soften but do not remove the spike

- PUFF now has separate cubic endpoint/energy accumulators, selected only when
  profile 3 wins the same cubic categorical competition used by the morphology
  map. Regional sheet coverage participates in that decision. Radial top
  collapse was reduced and split by envelope role; other families retain their
  previous reduction. Build, tests, sandbox, packaging and production shader
  loading passed.
- The same mature field was captured at `typeTicks=6882..7918`, `growth=1.00`,
  `decay=0.00`, with 20 accepted cloudlets. Captures are under
  `build/visual-test/cumulus-puff-cubic-role-mature`.
- The result is still rejected. The dominant central point is slightly shorter
  and the body stays connected, but close west still reads as two broad soft
  lobes joined by a narrow triangular peak. North remains a smooth low
  ellipsoid. The five-second stationary west pair is topologically stable.
- This persistence after reducing endpoint selectivity identifies the next
  limiter in `familyMacroShape()`: profile 3 derives `billow` primarily from
  3-D `baseCarrier` noise and raises its threshold with height. A locally strong
  noise column can therefore define the upper silhouette, even though the
  weather envelope is already domed. The meteorological footprint contributes
  too little to protect broad fused lobes.
- Stable samples were about `3.03..3.07 ms` at the close view and about
  `1.55..1.61 ms` before viewpoint changes. No controlled GPU regression is
  established.
- Next attempt: make the profile-3 macro silhouette primarily support-driven
  from weather footprint and its curved local height interval. Limit 3-D noise
  to bounded material modulation so it cannot grow a needle or drill a large
  upper hole.

## Iteration 65 - support-driven PUFF removes the needle, exposes height seams

- Profile 3 now lets weather footprint plus the curved local base/top interval
  define macro presence. The former height-dependent 3-D noise threshold was
  removed from the silhouette; base noise is bounded to `0.74..1.0` material
  modulation. Profile 0 retains the old fallback. Build, tests, sandbox,
  packaging and production GLSL loading passed.
- The prior field dissipated before validation, so a new isolated mature
  `cumulus_humilis` was used. Captures under
  `build/visual-test/cumulus-support-driven-mature` span `typeTicks=765..2164`,
  `growth=1.00`, `decay=0.00`, center `(-14.6, 257.3, -10.0)`, radius `62.5`,
  density `0.59`, coverage `0.60`, 20 accepted cloudlets and slab
  `238.7..291.4`.
- This is a real but incomplete improvement. The narrow triangular noise needle
  is gone. Close west now shows several connected upper lobes and a clearly
  taller central cumulus body, so profile 3 is more recognizable than the
  original flat lozenge. The stationary west pair remains stable.
- The result is not accepted as final. Cubic endpoint selection now exposes
  discontinuities between cloudlet height intervals as broad near-vertical
  faces. Close west has a squared central tower, an over-soft uniformly white
  facade and several thin lower fragments. North remains horizontally stretched
  with small tail-like projections. The profile has gained morphology but not
  natural billow transitions or internal depth.
- Stable samples were about `1.78..1.84 ms` distant and `2.88..3.05 ms` close at
  1920x1009. This does not show an obvious cost increase over the preceding
  iteration.
- Next attempt: blend a PUFF-only quadratic endpoint envelope with the cubic
  envelope so adjacent cloudlet height winners transition continuously while
  retaining more relief than the old all-family average. Then address lighting
  separately; do not hide geometry seams by increasing blur or brightness.

## Iteration 66 - normalized quadratic/cubic blend is visually subtle

- A PUFF-only quadratic endpoint average was blended with the cubic endpoint
  average at 44/56. The cubic categorical winner remains authoritative, so
  sheets cannot select the PUFF envelope accidentally. Build, tests, sandbox,
  packaging and production GLSL loading passed.
- The same field was captured under `build/visual-test/cumulus-soft-cubic-mature`
  at `typeTicks=8822..10013`, `growth=1.00`, `decay=0.00` and 20 accepted
  cloudlets.
- The field remains stable and connected, but the visual change from iteration
  65 is mixed. Distant west height jumps shrink, while close central height and
  north elongation increase. The broad white facade and lower peripheral
  fragments remain. Quantitative comparison found close ratio `1.681 -> 1.608`
  but north ratio `4.12 -> 4.19`; the tallest close contour jump only fell
  `24 -> 22 px`. The blend is rejected and reverted because its extra
  accumulators enlarge the central mass without solving the seams.
- Stable samples were about `1.84..1.97 ms` distant and `3.11 ms` close at
  1920x1009, comparable to the preceding scene.
- Geometry is now sufficiently improved to expose the next independent defect:
  optical depth and multi-scattering compress most of the fair-weather facade
  into a narrow white range. Next attempt corrects the first light-march sample,
  makes ambient occlusion follow the already-computed direct transmission, and
  lowers daytime tone exposure. Morphology values remain unchanged for that A/B.

## Iteration 67 - optical-depth lighting improves contrast but regresses close cost

- Lighting now sampled the midpoint of each light-march segment, replaced the
  local-density ambient factor with bounded direct-transmission occlusion, and
  reduced tone exposure from `1.55` to `1.30` by day (`1.48` at night). The
  failed PUFF soft blend was removed before this test. Build, tests, sandbox,
  packaging and production GLSL loading passed.
- A new mature isolated `cumulus_humilis` was captured under
  `build/visual-test/cumulus-lighting-optical-depth` at `typeTicks=785..4679`,
  `growth=1.00`, `decay=0.00`, center `(-19.6, 255.9, 2.0)`, radius `86.5`,
  density `0.60`, coverage `0.58` and 20 accepted cloudlets.
- External lighting is visibly improved. The close side view now has a readable
  grey underside and white illuminated crowns instead of one nearly uniform
  white facade. It remains bounded rather than black, and distant stationary
  views are stable. The geometric north elongation and lower appendices remain.
- The full change is rejected on performance and inside-view stability. Looking
  up from below cost about `13.6 ms`, looking down from above about `8.6 ms`, and
  placing the camera in the cloud reached about `107 ms`. The inside capture
  also shows a blue stipple over most of the screen and a broad horizontal gap;
  the above view exposes coarse translucent lobes. These artifacts are not
  accepted as a valid in-cloud result.
- Next A/B: restore the original light-tap position, retaining only the cheap
  ambient-transmission and tone-exposure changes. Re-run the same below/inside/
  above positions on the same field. If the catastrophic cost remains, it is a
  pre-existing main-ray/inside path defect rather than the midpoint change.

## Iteration 68 - origin clamp removes the in-cloud stipple, cost remains excessive

- The light tap returned to its original end-of-segment position. Bounded
  ambient-transmission occlusion and reduced tone exposure remain. Separately,
  rays beginning inside the slab now pretest coverage at `t0 + 0.5`, and coarse
  surface refinement clamps its backtrack to `t0` instead of stepping behind
  the camera. Build, tests, sandbox, packaging and GLSL loading passed.
- On the previous field, restoring the original light tap reduced the stable
  above-cloud sample from about `8.6 ms` to about `4.5 ms`; the midpoint change
  is therefore rejected on measured cost. A new mature field was then tested
  under `build/visual-test/cumulus-inside-origin-clamp` at `typeTicks=784..2355`,
  center `(-13.3, 255.6, 4.6)`, radius `64.5`, density `0.58`, coverage `0.60`
  and 20 accepted cloudlets.
- The in-cloud visual fix is substantial but incomplete. The full-screen blue
  stipple and broad empty horizon band from iteration 67 are gone. Two captures
  five seconds apart show a stable white/grey interior. Thin blue horizontal
  seams remain where depth-guided low-resolution taps reject one another.
- Cost is still unacceptable: about `8.27 ms` looking up from below, stable
  `79.7..79.9 ms` inside, and `5.76 ms` above. The reduction from `107 ms` is
  real, but the in-cloud path still shades almost the full screen, disables
  temporal confidence and runs the full light cone for each dense primary step.
- Next attempt is restricted to rays that start inside the slab: reduce origin
  jitter, cap light taps, early-out an optically saturated cone, and let a fully
  opaque four-tap composite neighbourhood blend without inter-tap depth rejection.
  Exterior silhouette depth rejection remains unchanged.

## Iteration 69 - opaque-neighbour composite removes the remaining blue seams

- Rays starting inside the slab now limit origin jitter to at most `0.75` world
  units, cap the light cone to four taps and stop it once scaled optical depth
  reaches `28.0`. The depth-guided composite also accepts all four paired taps
  without inter-tap depth rejection only when every tap is scene-visible and at
  least `0.18` opaque. Exterior silhouette neighbourhoods retain the original
  depth test. Build, tests, sandbox, packaging and production GLSL loading passed.
- A new mature isolated `cumulus_humilis` was captured under
  `build/visual-test/cumulus-inside-composite-cap` at `typeTicks=878+`, center
  `(-13.6, 257.8, 9.9)`, radius `68.8`, density `0.58`, coverage `0.60` and 20
  accepted cloudlets.
- The visual result is accepted for continuity, not for final performance. Two
  stationary in-cloud captures six seconds apart show a continuous pale-grey
  interior: the thin blue horizontal seams from iteration 68 are gone, and the
  former stipple/broad gap do not return. The result is temporally stable at the
  tested orientation. The external silhouette remains unchanged by the
  inside-only branch; its blocky height seams and overly white lower view remain.
- Stable GPU samples were about `69.0..70.0 ms` inside, versus `79.7..79.9 ms`
  in iteration 68. This is a measured reduction of roughly 12-14%, but is still
  unacceptable. Below-cloud samples ranged about `4.8..9.5 ms` and above-cloud
  samples about `6.6..6.8 ms`; those views are not a controlled same-field A/B
  and therefore do not establish an external regression.
- Next attempt must target dense full-screen shading rather than the composite:
  verify the actual runtime light-step/detail settings, then replace or further
  bound repeated light-cone density evaluations only for rays that start inside
  the slab. Preserve the now-correct opaque-neighbour composite and exterior
  lighting path.

## Iteration 70 - canonical camera density cuts the in-cloud light cone cost

- Runtime inspection established that the visual client is configured at
  `ULTRA`: 96 primary steps, six light taps, three scattering octaves, two
  detail levels and a 0.75 resolution scale. At 1920x1009 the cloud target is
  therefore about 1.09 million fragments. The previous four-tap cap only
  removed two of six taps, and its scaled optical-depth threshold of `28.0`
  cannot be reached by an ordinary cumulus within the four sampled segments.
- The renderer now sends the canonical smoothed camera density to the shader.
  Only when the camera both starts inside the slab and that density exceeds
  `0.08`, the light cone becomes one detached forward sample plus an analytic
  local/forward optical tail. The unresolved second detail octave is also
  omitted and the first primary sample starts in fine mode. All exterior rays
  retain the original light cone and detail path. Diagnostics now report camera
  density and governor scale. Build, tests, sandbox, packaging and production
  shader loading passed.
- A new mature isolated `cumulus_humilis` was captured under
  `build/visual-test/cumulus-inside-one-probe` at `typeTicks=785+`, center
  `(-22.7, 254.2, 0.2)`, radius `74.3`, density `0.56`, coverage `0.64` and 20
  accepted cloudlets. Focus-safe recaptures are `05-under-focused.png` and
  `06-above-focused.png`; the earlier external frames were invalidated by mouse
  recentering and incomplete chunk loading.
- With `cameraDensity=1.000`, stable in-cloud samples fell from iteration 69's
  `69.0..70.0 ms` to `47.8..48.1 ms` after the governor reached `0.5`, a further
  reduction of about 30%. The continuous interior remains free of the blue
  stipple, broad gap and horizontal seams. Exterior captures are unaffected by
  construction because `cameraDensity=0.000` there.
- The result remains incomplete. Roughly 48 ms is still excessive, and the two
  in-cloud captures are a nearly uniform medium-grey whiteout with little depth.
  That flatness is dominated by the CPU fog handler at density 1.0 (14-block
  visibility and full colour blend), not evidence that the directional probe is
  visually sufficient. The above view also continues to expose coarse fused
  cloudlet regions already present before this inside-only change.
- Next attempt: introduce a hysteretic 0.50 render scale only while canonical
  camera density confirms dense whiteout, relying on the existing resize
  history invalidation. Validate entry/exit popping before retaining it. Do not
  alter exterior ULTRA resolution or the whiteout parameters in the same A/B.

## Iteration 71 - dense-whiteout half scale reaches about 22 ms

- The native renderer now enters a 0.50 cloud-target scale when canonical
  camera density rises above `0.12` and leaves it below `0.04`. The normal
  quality scale remains unchanged outside; at ULTRA it stays 0.75. Existing
  target recreation invalidates temporal history on the one resize, while the
  density hysteresis prevents repeated boundary rebuilds. Runtime status now
  reports the active resolution scale. Build, tests, sandbox and packaging
  passed, and the new JAR loaded its production shaders without errors.
- Validation reused the still-active iteration-70 field under
  `build/visual-test/cumulus-inside-half-scale`, at center
  `(-22.7, 254.2, 0.2)`. Once `cameraDensity=1.000`, the log confirms
  `resolutionScale=0.500`, `governorScale=0.500` and stable GPU samples around
  `21.7..22.1 ms`. This is about 54% below iteration 70's `47.8..48.1 ms` and
  about 69% below iteration 69's `69.0..70.0 ms`.
- The valid in-cloud capture remains continuous and does not expose additional
  low-resolution stipple or seams. Its appearance is still a nearly uniform
  medium-grey because the full-density whiteout fog dominates; the resolution
  reduction is therefore retained as a performance improvement but does not
  solve interior depth by itself.
- Entry switched from 0.75 to 0.50 with history invalidated and no crash or
  logged shader/FBO error. A reliable visual exit A/B was not obtained: two F2
  files were copied while still zero-length, then the old field reached its
  lifetime limit and dissipated. The capture helper now waits for non-empty PNG
  files, but exit popping remains explicitly unconfirmed until another field is
  available.
- Next work leaves this performance path unchanged and establishes the actual
  sheet-family visuals (stratus, stratocumulus, nimbostratus, then cirrus).
  Whiteout visibility must be tuned separately only after a thin-layer interior
  test proves how much scene depth can safely remain visible.

## Iteration 72 - stratus baseline is an opaque featureless sheet

- A mature native `stratus_nebulosus` was created without Simple Clouds and
  captured under `build/visual-test/stratus-nebulosus-baseline`. The field had
  center `(57.2, 256.0, -9.3)`, radius `696.1`, density `0.63`, coverage `0.85`,
  three clusters and 37 accepted cloudlets. Its active slab was
  `243.7..282.8`.
- The defect is visually confirmed. From below, the deck is a broad smooth
  grey ceiling with an almost perfectly featureless base; its only strong shape
  cue is the curved outer boundary. From above, the view is filled by a uniform
  pale-grey surface with no recognizable horizontal condensate bands, openings
  or top relief. It is distinct from cumulus only because it is flat and huge,
  not because it has convincing stratiform detail.
- The shader explains the result. Profile 1 returns one continuous
  `verticalBand()` whose local top varies with low-frequency noise, but the
  resulting macro density can still approach one across a roughly 39-block
  layer. With field extinction `0.115`, long vertical paths saturate alpha and
  hide the encoded top/density variation. Detail erosion is only `0.10` and is
  confined to exposed density edges, so it cannot texture the protected broad
  interior.
- Performance is also unacceptable. With the camera below the deck,
  `cameraDensity=0`, so ULTRA keeps `resolutionScale=0.750`; nearly the full
  screen intersects the sheet and each dense primary sample still pays the full
  exterior six-tap light cone. Stable samples were roughly `59.6..62.2 ms` even
  after the step governor reached `0.5`.
- This is a baseline observation only; no stratus shader change has yet been
  accepted. Next attempt must modify profile-1 density structure and its light
  path together: preserve continuous coverage, expose broad horizontal bands
  at the underside and top, and use a cheaper stratiform self-shadow probe.
  Merely lowering opacity or adding high-frequency erosion would either worsen
  cost or create unphysical holes.

## Iteration 73 - invalid hot-JAR reload, no visual result

- A profile-1 shader change compiled and packaged successfully, but the first
  runtime attempt used an invalid deployment procedure: the mod JAR was
  overwritten while the running JVM still held its resource provider, followed
  by F3+T. The reload then reported
  `Invalid shaders/core/cloud_weather_splat.json: File not found` and the client
  stopped after the partially replaced class path also failed to resolve
  `ProjectAtmosphereCrashHandler`.
- This is not evidence of a missing packaged resource or a shader compile
  failure. The same build task had packaged successfully; the open JAR was
  mutated underneath the live class/resource loader. No after-change stratus
  frame was produced, so the shader change remains unvalidated.
- Corrective test procedure: never replace the runtime JAR in a live client.
  Close the client, copy the completed JAR, relaunch, and confirm shader
  registration before creating the next field. The failed attempt is excluded
  from visual and performance comparisons.

## Iteration 74 - lower stratus density is rejected

- After a clean restart, profile 1 used a cross-wind-weighted band signal,
  local top range `0.58..0.91` and optical-mass range `0.34..0.72`. Profiles 1,
  2 and 5 also used one mip-biased directional light probe plus an analytic
  optical tail instead of the six-tap convective cone. The production shaders
  registered successfully from the complete JAR.
- The carried-over old field was already late in its lifecycle and is excluded
  from the visual judgement. A fresh mature `stratus_nebulosus` was then created:
  center `(98.4, 256.1, 26.9)`, radius `790.3`, density `0.61`, coverage `0.91`,
  three clusters and 24-27 accepted cloudlets during the measured interval.
  The valid fresh underside capture is
  `build/visual-test/stratus-nebulosus-banded/03-under-fresh.png`.
- The change is rejected. Looking straight up from Y 220 produces a nearly
  full-screen washed pale-blue/grey veil with the sun visible through it, but
  still no readable horizontal bands or stratiform base texture. Lowering the
  volume density exposed sky colour instead of revealing internal structure.
- Performance also regressed on the fresh field: stable below-deck samples were
  roughly `66.3..68.6 ms` at governor 0.5, compared with the baseline field's
  approximately `59.6..62.2 ms`. The single light probe is cheaper per dense
  hit, but the reduced optical mass requires substantially more primary samples
  before transmittance terminates, overwhelming that saving.
- Next A/B restores the original high-density profile-1 mass while retaining
  the cheap sheet light probe. The ULTRA-only fine detail octave will be skipped
  for sheet profiles because it is paid at nearly every near-camera primary
  step but cannot survive the broad deck integration. Visual band contrast must
  then come from lighting/material response rather than reducing alpha.

## Iteration 75 - dense profile restores opacity but primary fill still dominates

- Profile 1 returned to its original dense `0.10..1.0` mass and `0.62..0.90`
  top range. The one-probe stratiform light path remains, while the second ULTRA
  detail octave is skipped for sheet profiles. Build, tests, sandbox, packaging
  and clean production shader loading passed.
- The same fresh field from iteration 74 remained active across the clean
  restart. The valid underside frame is
  `build/visual-test/stratus-nebulosus-dense-probe/01-under.png`; its debug
  overlay confirms position `(98.4, 200, 26.9)`, pitch `-90` and 1920x1009.
- Opacity is restored, but visual quality is not. The straight-up view remains
  an almost uniform pale-grey ceiling with the sun showing through a smooth
  halo; no useful horizontal base texture is visible. The rejected transparent
  blue wash is reduced, but the deck still reads as a flat screen.
- Focused active-render samples remained about `62.0..63.5 ms` at governor
  0.5. The expected large benefit from replacing six light probes did not
  occur because this stratus is classified as precipitating: `sampleLighting()`
  already takes the `rainFraction > 0.05` analytic branch and skips the full
  light cone. The real dominant cost is therefore the near-fullscreen primary
  march itself, including roughly twenty 2-block fine samples through the thin
  slab. Later `0.24 ms` samples occurred while the pause menu was open and are
  invalid.
- Next attempt adds a CPU-confirmed broad-sheet scene contract. Only when a
  stratiform footprint covers the camera XZ, the smooth deck may render at 0.50
  scale and use a larger minimum primary step. Convective/cirrus views and sheet
  edges outside the footprint retain their quality profile. This performance
  change must be validated before adding a separate lighting contrast term.

## Iteration 76 - broad-sheet optimization built, runtime blocked by display loss

- A spatial broad-sheet contract is now present in the build. The CPU tests the
  camera XZ against the oriented ellipse of actual profile 1, 2 or 5 render
  cells; a distant sheet cannot trigger it. When covered, the cloud target is
  limited to 0.50 and the shader raises only the stratiform scene's minimum
  primary step from 2.0 to 3.5 blocks. Dense-camera half resolution remains
  unchanged. The active decision is exposed as `broadSheet` in status logs.
- Java compilation, shader resource processing, sandbox, tests, full build and
  JAR entry inspection passed. The packaged JAR contains the updated volume
  shader/JSON and both updated renderer classes.
- No in-game result exists yet. After the clean shutdown, Forge failed before
  mod loading because GLFW could no longer enumerate a primary monitor:
  `glfwGetPrimaryMonitor failed`. Three clean retries and a Windows extended-
  display re-enumeration produced the same pre-Forge error. Windows currently
  exposes only the virtual primary screen `WinDisc`; the earlier physical/GPU
  display context is unavailable.
- This is an external validation blocker, not a passing result. The broad-sheet
  change must not be accepted, and no material-contrast change will be layered
  on top of it, until a client can launch and the same below/above stratus views
  confirm GPU time, edge quality and resolution-transition stability.

## Iteration 77 - broad-sheet optimization rejected by code review

- Read-only review found confirmed architectural faults in the unvalidated
  broad-sheet contract. It counted positive-density `CARRIER_ONLY` cells even
  though the splat forces their visible coverage to zero; its raw ellipse also
  omitted GPU domain warp and adaptive footprint scaling. The binary boundary
  had no hysteresis and would rebuild both ULTRA cloud targets at 0.75/0.50,
  invalidating history and likely hitching at a drifting edge.
- The flag was global to the fullscreen pass. A sheet under the camera would
  therefore reduce the step and resolution of unrelated cumulus, severe and
  cirrus clouds in the same view. It also missed regional-only layers, changed
  nimbostratus shaft spacing, risked erasing stratocumulus openings, and
  overrode the full-resolution diagnostic contract.
- Because these are code-proven defects and no runtime validation exists, the
  entire broad-sheet CPU/uniform/step/scale change has been removed. The
  unproven sheet one-probe and ULTRA-detail omission have also been removed.
  Profile 1 is back to the last observed dense implementation. The accepted
  camera-inside optimization, opaque-neighbour composite and diagnostics remain.
- Compilation, resource processing, sandbox, tests and full packaging pass
  after the revert. Runtime visual validation remains blocked by the missing
  GLFW primary monitor. A future sheet optimization must either be per-profile
  within the raymarch or use GPU-consistent occupancy metadata, with hysteresis
  and explicit mixed-scene/fullres semantics.

## Iteration 78 - physical display restored and stratus baseline re-established

- Windows again exposes a real primary `\\.\DISPLAY1` at 1920x1080 on the
  RTX 4070 Ti SUPER. The previous failure is now correlated with the physical
  MSI monitor leaving the PnP bus, followed by the last virtual display being
  removed; GLFW therefore had no attached primary monitor. A reboot restored
  the physical display. The production client launches again, registers both
  native PA cloud shaders and explicitly reports Simple Clouds absent.
- A fresh mature and frozen `stratus_nebulosus` was created without Simple
  Clouds. The saved region is centred at `(-26.7, 256.4, 33.2)`, radius
  `566.5`, density `0.59`, coverage `0.87`, growth `1.00`; the rendered slab is
  `243.7..282.3`. Reference images are under
  `build/visual-test/stratus-material-ab`.
- Personal visual inspection confirms the iteration-72 defect on the current
  safe build. `before-under-vertical.png` and `before-under-oblique.png` show a
  nearly uniform pale-grey ceiling with only a broad solar halo. The deck base
  has no readable horizontal condensate bands or material depth.
  `before-above-down.png` is likewise a weakly structured grey veil, while
  `before-edge-oblique.png` shows a smooth broad edge without stratiform
  texture. This baseline is valid; no shader change has yet been applied.
- Current GPU samples are about `2.05..2.38 ms` for the full-screen centred
  under/above views and `1.10..1.48 ms` near the edge at 1920x1009,
  `resolutionScale=0.750`, `governorScale=1.000`, 30 weather cells and 29
  accepted cloudlets. These numbers must replace, not be compared directly to,
  the old approximately 60 ms measurements because the recovered display now
  runs directly on the RTX path whereas the earlier Windows display topology
  differed.
- The next isolated A/B will keep density, extinction, morphology and opacity
  unchanged. It will add only a profile-1 material-light contrast derived from
  density normalized independently of the FIELDS/CELLS extinction tuning. The
  attempt is accepted only if broad base variation becomes visibly readable,
  mean luminance stays within eight percent, silhouettes remain intact and GPU
  time regresses by less than two percent or one millisecond.

## Iteration 79 - stratus ambient material contrast rejected

- The isolated B build passed Java compilation, resource processing, sandbox,
  tests and packaging. Its production shader registered successfully after a
  clean client restart. The change passed `profileId` into `sampleLighting()`
  and, only for profile 1, normalized local density by the profile density
  scale before gently brightening exposed ambient material and darkening dense
  material. It changed neither density, extinction, alpha nor ray steps.
- The exact field and views from iteration 78 were recaptured as
  `after-under-vertical.png`, `after-under-oblique.png`,
  `after-above-down.png` and `after-edge-oblique.png` under
  `build/visual-test/stratus-material-ab`.
- Personal A/B inspection rejects the change. The centred and oblique
  underside views remain a featureless pale-grey ceiling with the same broad
  solar halo; no horizontal condensate band is recognizable at normal size.
  The top and edge also remain materially flat. The only perceptible effect is
  a very small global shading shift, which is not a morphology improvement.
- A sampled central-80-percent luminance check supports that judgement. The
  vertical view changed mean luminance from `0.84126` to `0.84215` and P90-P10
  spread only from `0.03608` to `0.03626`. The oblique spread rose from
  `0.03507` to `0.04104`, but remained below the twenty-percent acceptance
  threshold and includes different terrain/chat pixels near the frame edge.
- The attempt did not cause a performance regression: centred under-deck B
  samples were approximately `1.90..1.98 ms` versus A's
  `2.05..2.38 ms`; above B was about `2.07..2.27 ms` and the edge about
  `1.06..1.15 ms`. All used 30 weather cells, 29 accepted cloudlets,
  `resolutionScale=0.750`, `governorScale=1.000` and valid history.
- Because radiance modulation cannot expose structure that the optically dense
  volume does not present at its first visible surface, the entire profile-1
  material-light branch and the extra `sampleLighting()` parameter are removed.
  The next attempt must alter only the geometric position/shape of the lower
  stratiform boundary while retaining the dense core and existing extinction.

## Iteration 80 - variable stratus condensation ramp rejected

- After removing iteration 79 completely, profile 1 was changed only at its
  lower fade. A broad existing wind-aligned signal varied the end of the
  condensation ramp from `0.012` to `0.075` of the local layer (about
  `0.46..2.84` blocks in the observed slab), while the dense body, horizontal
  mass multiplier, top boundary, extinction and all sampling remained
  unchanged. Build, resources, sandbox, tests, packaging and clean production
  shader registration passed.
- The previous field expired, so a fresh mature frozen `stratus_nebulosus` was
  created at `(15.7, 256.1, 18.2)`, radius `782.4`, density `0.61`, coverage
  `0.82`, growth `1.00`, slab `243.5..281.4`. The test fixes sunlight, weather,
  render distance 17 and hides command feedback. Captures are under
  `build/visual-test/stratus-base-ramp-a`.
- Personal inspection rejects the variable-ramp result. The vertical underside
  is still an almost uniform pale ceiling. The oblique underside gains only a
  very faint narrow dark band near the lower boundary, not several readable
  stratiform structures. The above view remains a uniform veil, and the
  level edge view still exposes the cloud as an extremely thin, nearly
  perfectly straight horizontal strip.
- Performance remains healthy on the recovered RTX path: centred underside
  samples were approximately `1.80..2.38 ms`, with 40 weather cells, 39
  accepted cloudlets, valid history, `resolutionScale=0.750` and
  `governorScale=1.000`. The attempt adds no texture lookup and no observed GPU
  regression, but it does not satisfy the visual objective.
- Merely changing fade thickness cannot move the encoded `weather.g` plane.
  The next bounded attempt replaces this ramp with a real positive-only local
  base displacement of at most about `2.1` blocks, plus a roughly `0.9`-block
  feather, while retaining more than 34 blocks of identical dense core. If
  that remains visually flat, larger profile-local displacement will be
  rejected in favour of a coherent effective-base contract shared by cloud,
  precipitation and CPU visual-density paths.

## Iteration 81 - profile-local stratus base lift rejected

- The next isolated build replaced the variable fade with a real local lower
  boundary: the existing broad directional signal lifted the profile-1 base
  from `0.000` to at most `0.055` of the observed layer, followed by a fixed
  `0.024` feather. It did not change the encoded weather-map base/top,
  coverage, top profile, extinction, ray steps or lighting. Compilation,
  resources, sandbox, tests, packaging and clean production shader
  registration passed.
- The same frozen stratus field was captured under
  `build/visual-test/stratus-base-lift-b` as vertical underside, oblique
  underside, level edge and above-down views. Personal A/B inspection against
  iteration 80 rejects this change: the vertical base remains a featureless
  pale ceiling, the oblique view gains no clearly recognizable structures,
  the edge is still a thin nearly straight line, and the upper surface remains
  a uniform veil. Small terrain/chunk differences after restart do not alter
  that cloud-shape conclusion.
- Centred views remained approximately `1.69..2.27 ms` on the direct RTX path
  with 40 weather cells, 39 accepted cloudlets, `resolutionScale=0.750` and
  `governorScale=1.000`; no performance regression is visible. The field then
  reached its normal lifetime limit despite simulation freeze, so later log
  samples are not part of this A/B.
- This second failure confirms that moving density only inside one already
  averaged `weather.g..b` interval cannot recover the lost stratiform
  silhouette. The profile-local experiment is removed. Read-only pipeline
  tracing identifies the actual loss earlier in `cloud_weather_splat.fsh`:
  dozens of broad sheet tiles contribute independently randomized heights that
  are collapsed to one weighted mean base and top per texel. The next change
  must preserve a coherent field-local surface in the weather map itself and
  mirror that contract in CPU visual-density queries; merely increasing the
  local lift amplitude is explicitly rejected.

## Iteration 82 - field-local stratus weather surface partially accepted

- The rejected profile-local lift was removed. A new surface contract is now
  built in `cloud_weather_splat.fsh` from the field's always-present macro
  carrier. Its signal uses non-warped coordinates relative to the real field
  centre, rotates with the field orientation, is seeded by the field and never
  depends on the accepted cloudlet count or `WorldTime`. Detail cloudlets still
  own coverage; the carrier only supplies coherent `Weather.g/b` targets for
  profile 1. This makes volume, rain attachment and GPU shadows consume the
  same base and top automatically.
- `ClientCloudVisualDensity` mirrors that surface and now also excludes
  `CARRIER_ONLY` from visible coverage, applies the weather-map lifecycle and
  precipitation packing, and uses the profile-1 lobes/collapses. Its comment
  no longer claims to reproduce unavailable 3-D material noise. The fallback
  density branch keeps its previous generic weighting/profile behaviour.
- The full Java/resources/sandbox/tests/build chain passed. The exact packaged
  JAR hash was `3CCFD1E67AA65B2F0AFB9B3665BAD284424C21F325314B799EBFDB2A4BA054B3`.
  A clean Forge 47.4.20 client then registered the shader, reported Simple
  Clouds absent and selected the native PA service.
- Personal runtime inspection used a mature frozen `stratus_nebulosus` centred
  near `(-48.6, 256.0, 30.5)`, radius `571.1`, density `0.61`, coverage `0.83`,
  slab `243.63..282.66`, with 29 accepted cloudlets. Captures are under
  `build/visual-test/stratus-weather-surface-c`.
- The structural result is real but only partially successful. The oblique
  underside and distant level view expose a gently varying lower boundary
  instead of the previous perfectly straight strip. A requested weather-map
  readback confirms `baseHeightRange=252.97..255.87`,
  `topHeightRange=263.99..267.81`, `thicknessRange=9.34..12.86` and variances
  `base/top/thickness=0.29/0.65/0.23`. This validates that the variation now
  exists in `Weather.g/b`, so the field-local surface foundation is retained.
- It is not a final visual acceptance. The vertical underside remains almost
  featureless except for the broad sun halo; the distant deck is still an
  extremely thin pancake; the above-down view is an over-bright translucent
  veil through which terrain remains conspicuous. Geometry alone cannot expose
  broad condensate bands once the dense material tone-maps to nearly the same
  luminance. The next bounded pass will use the already encoded world-space
  layer thickness as a zero-extra-texture optical-mass signal, darken thick
  underside bands, reduce profile-1 overexposure and raise stratus extinction
  modestly so the upper view has believable opacity.
- Stable centred exterior samples were approximately `1.80..2.28 ms` after
  history convergence (one `3.24 ms` transient occurred during view/target
  changes); the distant level view was about `0.17 ms`. The weather-surface
  calculations are confined to the cached 512x512 map and caused no visible
  recurring raymarch regression.

## Iteration 83 - stratus optical-mass shading rejected

- The bounded follow-up used the world-space `Weather.g..b` thickness already
  available at each primary hit. For profile 1 it reduced direct and ambient
  light on thick lower material, softened the upper response, reduced the
  final profile radiance by ten percent and raised the family density scale
  from `0.62` to `0.72`. The CPU visual-density approximation used the same
  family scale. No texture lookup, ray step or pass was added. Compilation,
  resource processing, sandbox, tests, packaging and clean shader registration
  all passed; the tested JAR hash was
  `CBABD1FCF08FA77F4F9BC9B30AE1118DC7145F292F2F184B8F992FD3618C6C8C`.
- The same saved, mature stratus field was recaptured as vertical underside,
  oblique underside, level edge and above-down views under
  `build/visual-test/stratus-optical-mass-d`. Personal in-game comparison
  rejects the change. The lower deck is only slightly greyer, with no readable
  broad band or relief in the vertical view. The oblique view gains a weak
  global lower-band darkening rather than surface structure; the distant edge
  remains a thin pancake; the top remains a pale translucent veil with terrain
  conspicuous below it. Raising density did not visibly cure that top-view
  transparency.
- The stable centred underside samples after history convergence were normally
  about `1.79..1.90 ms` (occasional samples reached `2.13..2.37 ms`), with 30
  weather cells, 29 accepted cloudlets, `resolutionScale=0.750` and
  `governorScale=1.000`. The edge view remained about `0.18 ms`. There is no
  demonstrated recurring GPU regression, but there is also no material visual
  benefit.
- Thickness is too smooth a scalar to encode surface orientation, and an
  already saturated first-hit material cannot reveal geometry through global
  brightness multipliers. This entire optical-mass experiment is therefore
  removed, including its extra lighting parameters and the density-scale
  change. Iteration 82's coherent weather surface remains. The next experiment
  must derive a bounded directional response from the actual neighbouring
  weather-map surface, or diagnose the top-view alpha path directly; further
  global tone or density multipliers are explicitly rejected.

## Iteration 84 - raw alpha diagnostic isolates the translucent top

- After restoring iteration 82 exactly, the complete build and test chain
  passed again and the production shader registered from JAR
  `8A390E58C3B6E563B0A855E1D8A3D14678A8328A68E3FA2640BF843CAD422DEC`.
  Simple Clouds is absent and the native PA backend is the only visual owner.
  A fresh `stratus_nebulosus` was spawned and frozen near `(45.8,255.8,11.9)`;
  at the diagnostic it had radius `610.3`, density `0.62`, coverage `0.86`
  and growth `0.69`.
- The exact same above-down camera was captured in final, raw-alpha and
  straight-colour composite modes under
  `build/visual-test/stratus-alpha-diagnostic`. Personal inspection shows the
  final image as a pale veil with terrain conspicuous through its centre. The
  alpha image independently shows that centre as the least opaque part of the
  deck, whereas straight colour is nearly spatially uniform.
- A sparse full-frame pixel measurement quantifies the diagnosis. Raw alpha
  has mean `0.8725`, P05 `0.6784`, median `0.8980`, P95 `0.9765`, and centre
  `0.6235`. Straight-colour luminance has mean `0.8506`, P05 `0.8444`, median
  `0.8510`, P95 `0.8575`. The visible terrain is therefore caused primarily by
  insufficient and spatially inconsistent optical depth, not by a composite
  alpha clamp, an 8-bit target or a large colour variation.
- Code tracing confirms the inconsistency. Profile 1 calls itself continuous
  but multiplies its macro body by `mix(0.10,1.0,horizontalDetail)`, then by
  `coverageMod`, an edge-erosion term and a second coverage gate before the
  family density scale. Low horizontal-detail areas can consequently retain
  only a small fraction of the intended sheet mass. Raising the global density
  scale in iteration 83 changed all material slightly but did not repair this
  morphologically generated hole.
- The next isolated correction will preserve the current family density and
  lighting, raise only the profile-1 macro mass floor, and replace its linear
  coverage multiplication with a smooth interior sheet gate that still fades
  at the weather-map boundary. The CPU visual-density model must mirror that
  coverage contract. Raw alpha and final top/underside views will be repeated
  before any surface-lighting work.

## Iteration 85 - continuous stratus mass contract accepted

- Profile 1 now keeps a `0.32` condensate floor instead of allowing its broad
  3-D material signal to reduce the macro body to `0.10`. Its weather coverage
  is no longer reused as a linear second density multiplier: a square-root
  interior response preserves sheet mass while `smoothstep(0.008,0.12)` and the
  existing final gate retain a soft field boundary. Family density,
  extinction, lighting, ray steps and all other profiles are unchanged.
  `ClientCloudVisualDensity` mirrors the same coverage envelope for native
  camera fog and whiteout queries.
- Compilation, resources, cloud-field sandbox, tests and packaging passed. The
  production shader registered from JAR
  `8D7A3999417E50508F08135CF637BB3FD86EE7A7D1671CBE7E0CBD266DCA4231`;
  the runtime again confirmed Simple Clouds absent and the native PA service.
  The same mature frozen field and fixed camera views were recaptured under
  `build/visual-test/stratus-mass-floor-e`.
- Personal A/B inspection accepts this correction for opacity. In the
  above-down final image the formerly conspicuous central terrain disappears,
  while the deck boundary remains soft. Raw alpha mean rises from `0.8661` to
  `0.8895`, P05 from `0.6471` to `0.7333`, median from `0.8980` to `0.9098`,
  and centre from `0.5843` to `0.7020`; P95 remains essentially unchanged
  (`0.9804` to `0.9765`). This is the intended selective repair of weak sheet
  material rather than a global opaque clamp. Straight/final mean luminance
  stays effectively unchanged (`0.8499` to `0.8495`).
- The underside does not acquire a hard edge or a new hole. It remains too
  visually uniform, so this iteration is not presented as a complete stratus
  improvement. It only establishes believable continuity and removes the
  top-view terrain leak before surface relief is introduced.
- Stable mature samples are approximately `2.32..2.55 ms` for the centred
  above view and commonly `1.65..2.07 ms` for the underside after convergence,
  with 35 weather cells, 34 accepted cloudlets, `resolutionScale=0.750` and
  `governorScale=1.000`. Occasional transition samples near `3.1 ms` are not
  treated as steady state. No recurring regression is demonstrated relative
  to the prior approximately `2.2..2.4 ms` mature views.
- The next isolated pass may now consume the actual neighbouring `Weather.g/b`
  surface once at the first profile-1 hit per pixel. It must not add weather
  fetches per ray step or per light-cone sample. Its only purpose is to expose
  the already encoded slope/curvature before tone mapping; alpha and the new
  mass contract remain fixed.

## Iteration 86 - cached weather-surface differential not yet accepted

- The first relief implementation adds four neighbouring weather-map samples
  only at the first fine profile-1 hit of an exterior pixel. It derives a
  bounded slope and signed curvature cue, caches that cue for the ray, and
  applies it only in the visible lower or upper shell before tone mapping.
  Horizontal rays and in-cloud cameras receive no cue. No fetch is added to
  `cloudDensity`, the per-step light cone, other profiles or precipitation.
  Build, tests and shader registration passed from JAR
  `7328093A03569526B68C498AD1C7D55E21C56B29C90B514099E26034250C6D82`.
- The first post-restart capture set under
  `build/visual-test/stratus-surface-relief-f` is explicitly invalid: the old
  field reached age `12000` before capture and the log reports `no_clouds`.
  Those blue-sky images are retained only as lifecycle evidence and are not
  used to judge the shader.
- A fresh field was then created, allowed to reach growth `1.00`, and captured
  under `build/visual-test/stratus-surface-relief-f2`. It was centred near
  `(68.1,255.9,36.8)`, radius `584.9`, density `0.61`, coverage `0.87`, with 31
  accepted cloudlets. Personal inspection at noon still finds the vertical
  underside essentially featureless and the top a pale veil. The oblique lower
  boundary retains its broad geometric undulation but does not show a clearly
  new interior relief pattern.
- Sunrise and sunset tests confirm that the existing atmosphere supplies
  strong cool/warm coloration and a darker oblique underside. A few horizontal
  bands are readable near the grazing boundary, but the centred surface remains
  too smooth to attribute a material improvement to the new differential.
  This scale is therefore not accepted as a visual correction.
- Stable GPU samples with the differential are commonly `1.53..2.19 ms`, with
  occasional `2.30..2.40 ms` samples. They overlap the pre-differential range,
  so the four cached fetches do not demonstrate a recurring regression. A
  cross-field luminance comparison is not a valid A/B, but it also shows no
  increased spread: the new field's central P90-P10 spread is `0.0405` versus
  `0.0680` on the previous field at noon.
- Before increasing gains blindly, the next build is a reverse visual control
  on this same saved field: it keeps the exact code and fetch cost but passes a
  zero relief cue into lighting. If the images are indistinguishable, the
  initial cue is removed or recalibrated from a direct diagnostic; if a real
  difference appears, only then can its direction and strength be judged.

## Iteration 87 - reverse control confirms a negligible relief signal

- The same iteration-86 shader was rebuilt with its cached surface cue passed
  as a literal zero to `sampleLighting`; density, alpha, weather morphology,
  camera, time, field and all other lighting inputs remained unchanged. The
  complete build and test chain passed and the control JAR hash was
  `7CBD0322CD9E37CABBE9A527A0B3322B84363A11C29B1169E83EA4258567B19F`.
- The saved mature stratus field was recaptured at the exact noon top,
  vertical-underside and oblique-underside viewpoints under
  `build/visual-test/stratus-surface-relief-control-g`. Personal side-by-side
  inspection against `stratus-surface-relief-f2` finds the vertical images
  indistinguishable and the oblique images indistinguishable, including the
  lower boundary undulations. The visible low-frequency banding in the first
  test was therefore existing atmosphere/cloud geometry, not a demonstrated
  contribution from the new differential.
- Iteration 86 is rejected at its original gain. Its four-tap sampling design
  remains a valid bounded experiment, but the derived cue is too small after
  normal construction, clamping, shell weighting, sun/ambient mixing and tone
  mapping to create a readable surface response. No visual benefit justifies
  retaining that calibration.
- The next isolated build will restore the cue and increase only its geometric
  response within conservative clamps. Density, opacity, material darkness,
  extinction and the accepted iteration-85 mass contract remain fixed. It is
  accepted only if the same-field A/B exposes broad coherent relief without
  embossed texels, hard seams, global darkening or a recurring GPU-time
  regression.

## Iteration 88 - post-integrated surface response remains imperceptible

- A new frozen `stratus_nebulosus` field was used because the iteration-87
  field had completed its lifecycle. The stable field remained centred near
  `(180.4,255.7,33.1)`, with reported base/top near `244.1/276.8`, 44 weather
  cells and 43 accepted cloudlets. The control build contained the new
  four-tap, 48-block surface differential but passed no visual cue. Its exact
  top final/alpha and underside vertical/oblique views are under
  `build/visual-test/stratus-relief-calibration-control-h`.
- The candidate moved the bounded response after ray integration and before
  temporal history. It applied only when the first visible material was
  profile-1 stratus, modulated premultiplied RGB by at most `0.88..1.10`, and
  clamped RGB back to alpha. Density, extinction, transmittance, alpha and all
  accepted stratus mass constants were untouched. The complete build passed;
  the candidate JAR hash was
  `38C6B0938C310EA853B03ED4FC08874524F8E6FAA5CF329DFE716FDE9A5D599E`,
  and the production shader registered cleanly without Simple Clouds.
- Personal in-game A/B inspection rejects the candidate as a visible
  improvement. The top and vertical underside remain effectively
  indistinguishable. The oblique underside keeps the same pre-existing edge
  striations but gains no clearly new broad interior structure. No embossing,
  seam or hard boundary appears, so the experiment is visually safe but not
  useful.
- Cropped image analysis agrees. Top central contrast changes only
  `0.00693 -> 0.00756`; vertical left/right changes by `+0.00070/+0.00449`;
  oblique changes by `+0.00430`, while its high-pass standard deviation
  actually falls (`0.00893 -> 0.00821`). Fewer than `2.4%` of top pixels move
  by more than one 8-bit level and none by more than two. The alpha control,
  which this shader cannot alter, drifts by `-0.01282` in the central mean and
  `+0.02745` in contrast across the restart. Those larger alpha changes prove
  that the remaining tiny RGB differences cannot be assigned confidently to
  the relief cue.
- Twelve converged oblique samples give control median/P90
  `1.7608/1.8084 ms` and candidate `1.8447/2.0634 ms`, with identical 44
  weather cells, 43 accepted cloudlets, `resolutionScale=0.750` and
  `governorScale=1.000`. The median remains within the predeclared tolerance;
  P90 is approximately `0.005 ms` beyond its `+0.25 ms` tolerance. That small
  isolated miss is not treated as a confirmed regression, but no visual
  benefit exists to justify the four fetches at this stage.
- No further blind gain change is allowed. The next build must display the
  signed cached cue directly, with density/alpha still available for context,
  so its real range and spatial coherence can be judged before deciding
  whether to recalibrate or remove the entire differential.

## Iteration 89 - signed-cue diagnostic proves the stratus branch is not entered

- A temporary diagnostic replaced profile-1 post-lighting with a signed colour
  map while retaining the real ray alpha: neutral grey means zero cue, blue a
  negative cue and red a positive cue, with `abs(cue)=0.05` reaching full
  colour. Straight-colour composite mode removes alpha from that display, so
  even a weak executing branch must differ from the normal atmospheric
  radiance. Build/tests passed and the shader registered from diagnostic JAR
  `48126AA23BCE3236FFE62F43B6273A2C670B9CDC0F0ED9B3F4EBC3B0C6228BD9`.
- A fresh native-only field was captured from above and below under
  `build/visual-test/stratus-relief-cue-diagnostic-j`. It was reported as
  `stratus_nebulosus`, centred near `(211.3,256.0,15.5)`, with base/top
  `244.6/277.2` and up to 35 accepted cloudlets during the diagnostic. History
  was disabled and the composite was explicitly set to straight colour.
- Personal inspection finds no diagnostic hue at all. All three images retain
  the original neutral atmospheric/cloud radiance, including the bright sun
  region and the normal oblique edge shading. This is categorically different
  from a near-zero cue, which would have replaced the straight colour with the
  explicit neutral `vec3(0.5)` diagnostic output.
- The guarded post-lighting block therefore did not execute for the visible
  ray. The two previous relief attempts were ineffective primarily because
  `firstMaterialIsStratus` was false, not merely because their gain was weak.
  CPU inspection confirms that canonical field snapshots do encode
  `stratus_nebulosus` as profile 1, so the mismatch is downstream: either the
  first fine density hit samples a different/empty categorical morphology
  texel, or the GPU categorical map/lookup does not correspond to the weather
  density hit. The exact failure point is not yet confirmed.
- The next diagnostic must expose the first resolved GPU profile/category
  directly, without a `profileId == 1` guard. No visual relief code will be
  accepted until that CPU-to-weather-map-to-ray classification path is proven.

## Iteration 90 - first-material profile diagnostic is absent from the displayed image

- The next temporary build recorded the first non-precipitation material
  profile resolved by the volume ray, then replaced that ray's premultiplied
  RGB with an unmistakable categorical palette whenever both
  `currentCloudHit` and `firstMaterialResolved` were true. The palette mapped
  profile 0 to magenta, 1 to green, 2 to cyan, 3 to white, 4 to red, 5 to
  blue, 6 to yellow and 7 to orange. Density, alpha and the raymarch itself
  were unchanged. The complete build/test chain passed; the diagnostic JAR
  hash was
  `0D366606AA881FCEC6F8D0086A8709F25CE418FA3A5BF4412EADBEA075AA597C`.
- The same native-only `stratus_nebulosus` field centred near
  `(211.3,256.0,15.5)`, with reported base/top near `244.6/277.2`, was
  captured from above, directly below and obliquely below. Temporal history
  was disabled and the composite was explicitly set to straight colour. The
  three captures are under
  `build/visual-test/stratus-first-profile-diagnostic-k`.
- Personal inspection finds no categorical colour in any view. The top image
  remains the normal pale grey radiance and both underside images retain the
  normal cloud/atmosphere shading. This excludes a simple profile-1 mismatch:
  even profile 0 or any other resolved value would have produced a saturated
  palette colour.
- The observation proves only that the guarded replacement does not reach the
  displayed pixels. It does **not** yet prove whether
  `currentCloudHit && firstMaterialResolved` is false, whether a later output
  path replaces the value, or whether straight-colour composite mode samples
  a different render target. The next isolated diagnostic will write a
  saturated colour for every non-empty final volume pixel immediately before
  `fragColor`, outside all material/profile guards. If that colour appears,
  the active shader/output path is confirmed and the failure is in the hit
  state. If it does not, target selection or resource deployment must be
  traced before any visual calibration continues.

## Validation correction - iterations 88 through 90 used a stale runtime JAR

- A direct hash audit found that the launcher script only starts Forge in
  `run-native-jar-visual`; it does not deploy the newly built artifact. The
  client used for iterations 88 through 90 loaded
  `run-native-jar-visual/mods/Forge-projectatmosphere-0.9.1.1-alpha.jar` with
  SHA-256
  `7CBD0322CD9E37CABBE9A527A0B3322B84363A11C29B1169E83EA4258567B19F`,
  the iteration-87 control artifact. The later candidate and diagnostic JARs
  existed only under `build/libs`.
- The runtime JAR's embedded `cloud_atmosphere_volume.fsh` contains neither
  the iteration-89 signed-cue diagnostic nor the iteration-90 profile
  diagnostic. Consequently, their absent colours do not establish any shader
  branch or GPU-category failure. The iteration-88 visual comparison and GPU
  timing comparison also did not compare the claimed candidate against its
  control; both sessions ran the same stale control.
- All visual conclusions and comparative performance numbers from iterations
  88 through 90 are therefore marked **invalid** and must not guide production
  changes. Their captures remain retained as provenance only. Iteration 87's
  same-artifact control conclusion remains valid.
- From this point, every visual run must explicitly copy the built JAR into
  the instance `mods` directory while the client is closed, then prove that
  build and runtime SHA-256 hashes match before launch. The current
  iteration-91 reachability artifact has build hash
  `FA8E574F94FC3309CF530FDD35F4445F4B208767C38EEA4DB3E7D47D83FA27E2`
  and its embedded shader was independently verified to contain the
  unconditional magenta output.

## Iteration 91 - deployed-shader reachability is visually confirmed

- The client was closed before deployment. The build artifact was copied
  explicitly to the instance and both paths were verified as SHA-256
  `FA8E574F94FC3309CF530FDD35F4445F4B208767C38EEA4DB3E7D47D83FA27E2`
  before launch. Source, processed resource, build-JAR entry and runtime-JAR
  entry also contained the same iteration-91 shader. `latest.log` confirms
  that the new production client compiled and registered that shader without
  Simple Clouds.
- The temporary diagnostic replaced premultiplied RGB with magenta for every
  final volume pixel whose alpha survived the normal `0.002` output cutoff.
  It was written immediately before `fragColor`, outside profile, material and
  history conditions. Temporal history was disabled and the composite was set
  to straight colour.
- A live native `stratus_nebulosus` was reported at centre
  `(495.4,255.8,47.3)`, base/top `244.0/277.8`, profile density `0.76`, with
  one visible field and 28 requested/accepted cloudlets. After teleporting the
  camera above its centre and looking vertically down, the personally observed
  and captured result is uniformly saturated magenta. The evidence is
  `build/visual-test/stratus-reachability-diagnostic-l/reachability-field-top-color.png`.
- This visually confirms the complete edited-shader -> volume target ->
  current-target composite path. There is no competing stale framebuffer or
  hidden shader output in this test. The pre-teleport terrain capture is not
  evidence against the shader: diagnostics placed the field roughly 284
  blocks away from that camera, outside the viewed footprint.
- The unconditional marker is diagnostic-only and must now be removed. The
  frozen saved field will be reused with the categorical first-material
  palette still present, after another hash-verified deployment, to establish
  the actual GPU profile before any stratus relief calibration resumes.

## Iteration 92 - first visible stratus material resolves as GPU profile 1

- The unconditional reachability marker was removed while retaining the
  first-non-precipitation-material categorical palette. Build, resources,
  sandbox, tests and packaging passed. The client was closed during staging;
  the build and runtime JAR hashes were both verified as
  `0C31EF616E10B8FCF9A1CF1388623B4BD2BA9741127DB63E7B497CE7D9AA715A`
  before launch.
- The frozen iteration-91 `stratus_nebulosus` was reused at the same reported
  centre and slab. With history disabled and straight-colour composite mode,
  the first resolved profile palette is saturated green from above, directly
  below and obliquely below. Profile 1 is the palette's green entry. Captures
  are under `build/visual-test/stratus-first-profile-diagnostic-m`.
- Personal inspection therefore confirms that the rendered stratus density
  and the categorical morphology map agree in all three representative rays.
  `currentCloudHit`, `firstMaterialResolved`, the non-precipitation material
  path and `cloudProfileId(morphology)` all execute as intended for the saved
  field. The earlier categorical-mismatch theory is rejected; it was an
  artifact of the stale iteration-87 runtime JAR.
- The diagnostic also exposes the actual carrier silhouette without lighting:
  the top view is a broad continuous mass with a smooth large-scale opening,
  while the oblique underside has a coherent but gently curved lower edge.
  This is useful geometric context, but categorical colour cannot judge
  production relief or lighting.
- All profile-palette code must now be removed. The real post-integrated
  stratus relief candidate from iteration 88 will be rebuilt and deployed with
  hash verification, then compared against a true no-cue control on this same
  frozen field. The invalid iteration-88 screenshots and timing numbers will
  not be reused.

## Iteration 93 - valid production stratus control and acceptance thresholds

- All categorical diagnostics were removed. The differential remained
  calculated in source but was not consumed, so the optimizing shader could
  produce no relief response. The complete build passed and the runtime JAR
  was hash-matched to the build at
  `3E0A6F89B0300ADE49FB1A935FD9AC19C5C22226EA6A5C3AEA4BAD504C3A08C1`.
  This is the first valid production-colour control after the stale-JAR issue.
- With history disabled, final composite, clear noon lighting and the saved
  profile-1 field, exact top, vertical-underside and oblique-underside captures
  were recorded under `build/visual-test/stratus-relief-valid-control-n`.
  Personal inspection confirms a broad coherent stratus deck, but the top is
  an almost featureless pale sheet. The vertical underside is dominated by a
  wide solar glow, and only the oblique view exposes broad lower-edge bands;
  there is little readable relief inside the mass.
- Quantitative analysis agrees. On the main top mass, mean luminance is
  `0.83567`, raw p5-p95 contrast `0.02014`, and the low-frequency relief band
  has only `std=0.00155`, range `0.00330`. At the top centre the band falls to
  `std=0.000575`, range `0.001738`. The vertical underside band is
  `std=0.00286`, range `0.00954`; the oblique upper mass is `std=0.00462`,
  range `0.01409`. The oblique base is darker by `0.13612`, so vertical
  shading exists, but it does not create interior surface relief.
- White clipping is currently bounded: all-channel `>=254` is `0%` top,
  `0.0435%` vertical and `0.1183%` oblique. No black clipping is present. The
  candidate must not achieve contrast through global exposure: mean luminance
  may move by at most `0.015` top / `0.020` underside, top all-channel clipping
  must remain below `0.01%`, and cloud saturation p95 must remain `<=0.12`.
- Acceptance requires a top low-frequency band of at least
  `std=0.0035/range=0.010` (centre `0.002/0.006`), vertical underside at least
  `0.0045/0.015`, and oblique upper mass at least `0.0065/0.020`, while
  retaining the current upper-to-base difference within `0.11..0.17` and the
  smooth base silhouette.
- Twelve stable oblique samples with 33 weather cells, 32 accepted cloudlets,
  history off, `resolutionScale=0.750` and `governorScale=1.000` give raymarch
  median/P90 `3.0085/3.3905 ms` (range `2.5508..3.4519 ms`). This is the valid
  control cost for the next candidate, not the invalid iteration-88 timing.
- Movement freeze does not freeze lifecycle evolution. While analysis was in
  progress, coverage later fell from `0.348` toward zero; therefore this saved
  field cannot support a causal same-state candidate capture. A fresh field
  must be spawned for a rapid control-save-close/candidate-reopen sequence.
  No production relief change will be accepted from the decayed field.

## Iteration 94 - paired fresh-field control saved for causal A/B

- Still on the verified iteration-93 control JAR, all previous cloud regions
  were cleared and a fresh native `stratus_nebulosus` was spawned with movement
  frozen. The client reported profile density `0.76`, base/top `244.2/275.9`,
  centre `(789.9,255.7,41.7)`, one visible field and 24 accepted cloudlets at
  the status capture.
- Clear noon, final composite and history-off top, vertical-underside and
  oblique-underside views were captured rapidly under
  `build/visual-test/stratus-relief-paired-control-o`. The world was then saved
  and the client closed immediately, limiting further lifecycle evolution
  before the candidate restart.
- Personal inspection again finds a nearly uniform pale upper mass. A broad
  central opening exposes terrain in the top view, which provides an exact
  high-contrast registration landmark for the paired candidate. The underside
  is predominantly smooth grey-white; the oblique lower boundary contains
  thin horizontal bands but little broad interior relief.
- This is the causal visual control for the next build. The candidate must use
  the saved world, exact centre coordinates and camera rotations immediately
  after launch. No new cloud may be spawned and no history may be consumed
  before its three captures.

## Iteration 95 - bounded post-integrated relief is still not visibly sufficient

- The candidate added a 48-block four-neighbour base/top differential only for
  the first visible profile-1 material. It corrected top/bottom orientation,
  normalized the solar azimuth so high-noon slopes retain a bounded response,
  added signed curvature, and faded the cue smoothly near weather-map edges.
  The response was applied once to straight RGB after integration and then
  re-premultiplied; density, extinction, alpha, representative depth and all
  non-stratus profiles were unchanged. Build/tests passed and build/runtime
  JAR hashes matched at
  `E13182C5C790DA5396038DD9E3330E7FF88379252A156620D6B8300EB725ECB6`.
- The saved iteration-94 world was opened and captured immediately at the exact
  top, vertical-underside and oblique-underside coordinates, with clear noon,
  history off and final composite. Candidate images are under
  `build/visual-test/stratus-relief-paired-candidate-p`.
- Personal side-by-side inspection does not accept the candidate as a material
  visual improvement. The top opening, pale mass and surrounding terrain are
  effectively unchanged. The vertical underside remains dominated by the same
  solar gradient. The oblique lower centre may be fractionally darker, but no
  new broad relief is confidently distinguishable and no acceptance threshold
  is visibly approached. No hard weather-map ring, hue shift, black cloud or
  new whiteout appears.
- Registered image analysis confirms that the response is real rather than
  jitter, but still below acceptance. The top relief reaches only
  `std=0.00191..0.00211/range=0.00756` versus required `0.0035/0.010`;
  vertical underside `0.00389/0.00784` versus `0.0045/0.015`; oblique upper
  `0.00692/0.01564` versus `0.0065/0.020`. Polynomial exposure-compensated
  residuals are far above prior control drift, so the shader does act, but the
  visible change remains too broad and exposure-like. Mean shifts remain
  bounded (`-0.00874` top, `+0.01723` vertical, `+0.00620` oblique), saturation
  p95 remains `0.111..0.116`, and no white/black clipping is introduced.
- Twelve stable candidate samples with 35 weather cells, 34 accepted
  cloudlets, history off, `resolutionScale=0.750` and
  `governorScale=1.000` give median/P90 `2.0060/2.2333 ms` (range
  `1.7295..2.3839 ms`). The paired control was captured earlier in its growth
  with 24 cloudlets and therefore cannot supply a causal GPU-cost delta; the
  prior 33-cell control remains a conservative but different-field benchmark.
- The field was saved and the client closed before further evolution. Because
  a visible benefit is still unproven, the next build will not blindly
  increase the multiplier. It will encode the signed cached cue directly as a
  recoverable red/grey/blue diagnostic in straight-colour composite mode. Its
  measured range and spatial coherence will determine whether this differential
  can be calibrated or should be removed entirely.

## Iteration 96 - signed relief cue is strong, coherent and calibratable

- The production response was temporarily replaced by a recoverable diagnostic
  in straight-colour composite mode: zero cue is neutral grey, `+0.16` reaches
  saturated red and `-0.16` saturated blue. Alpha and all density/depth state
  remained real. Build/tests passed; build/runtime JAR hashes matched at
  `DFB631F7BC10E2CCB9A820B600FED506AEEBE87679820690DF141CB3DB425F25`.
- The first captures under `stratus-relief-cue-diagnostic-q` are rejected for
  cue analysis because the iteration-95 field had already dissipated to a few
  small remnants. No inference is taken from those images.
- A fresh native `stratus_nebulosus` was then spawned and captured immediately.
  The client reported profile density `0.76`, base/top `243.7/279.8`, centre
  `(339.6,255.6,58.2)`, one visible field and 42 accepted cloudlets. Exact top,
  vertical-underside and oblique-underside diagnostics are under
  `build/visual-test/stratus-relief-cue-diagnostic-r`.
- Personal inspection conclusively validates the geometric signal. The top
  contains broad red and blue lobes spanning large fractions of the field,
  not texel noise. The vertical underside retains similarly coherent opposing
  regions. The oblique underside exposes multiple smooth red/blue bands across
  hundreds of screen pixels, aligned with the deck's broad lower-surface
  undulations. There is no hard inner weather-map ring in these views.
- Decoding the exact diagnostic colour gives cue mean/std
  `+0.01064/0.01991` top, `-0.01029/0.01309` vertical underside and
  `-0.00951/0.01406` oblique underside. No shader clamp is reached; observed
  extrema remain within about `-0.063..+0.050`. After removing a degree-3
  screen polynomial, substantial real structure remains with
  `std=0.00920/0.00955/0.01209` and p5-p95
  `0.03064/0.03091/0.04094`. Correlation lengths are hundreds of pixels, which
  quantitatively confirms broad relief rather than texture-scale noise.
- The distribution has a real face-dependent DC component: top is mostly
  positive, while both underside views are mostly negative. A raw multiplier
  therefore spends part of its visible range on global exposure. The next
  mapping will subtract a small daylight- and verticality-weighted side bias,
  then use a bounded soft response before re-premultiplication. This is derived
  from the measured cue, not an arbitrary gain increase.
- The differential is therefore not too weak and should not be removed. The
  iteration-95 failure lies in mapping this strong signed geometry into the
  already compressed integrated radiance: its effect was measurable but below
  the predeclared contrast thresholds. The next production candidate will use
  the decoded per-view cue distribution to increase contrast while compensating
  face-specific mean bias. Density, alpha, extinction and morphology will stay
  unchanged.

## Iteration 97 - second paired production control for centered response

- The signed-colour diagnostic was removed and a no-response production
  control was rebuilt. Build/tests passed; build/runtime JAR hashes matched at
  `94D7897EB594BA070A55C7CE1014078F3BD6E241D8E07909BA00B061623FA38F`.
- The saved fresh field from iteration 96 was opened with clear noon, history
  off and final composite. Exact top, vertical-underside and oblique-underside
  views were captured under
  `build/visual-test/stratus-relief-centered-control-s`, then the world was
  saved and the client closed immediately.
- Personal inspection again shows the production deficiency: the upper mass
  is nearly indistinguishable from the pale sky except where terrain faintly
  shows through, the vertical underside is a smooth radial light gradient, and
  the oblique underside exposes lower-edge streaks without enough broad
  interior depth. These images are the causal control for the measured,
  centered soft-response candidate; their exact saved state and camera
  coordinates will be reused on the next launch.

## Iteration 98 - centered stratus relief reaches the declared contrast floor

- The calibrated production mapping subtracts the measured face-dependent DC
  component (`sideSign * 0.0102 * rayVerticality * daylight`) from the cached
  stratus surface cue. It then applies a slope of `1.5` through asymmetric
  soft caps (`+0.050/-0.065`) to straight RGB only, fades the response in below
  alpha `0.25`, clamps highlights below `0.98`, and re-premultiplies. Density,
  extinction, alpha, representative depth, precipitation and every non-stratus
  profile remain unchanged. The complete build passed and the build/runtime
  JAR hashes matched at
  `3685B240B517405073D88CD1EDDFF2966B1BBA234CC6316B0C57AD996598A266`.
- The saved iteration-97 field was opened without Simple Clouds and captured
  immediately at the exact top, vertical-underside and oblique-underside
  coordinates, with clear noon, history off and final composite. Candidate
  evidence is under `build/visual-test/stratus-relief-centered-candidate-t`;
  the causal control is `stratus-relief-centered-control-s`.
- Personal inspection finds no aggressive embossing, hard weather-map ring,
  hue discontinuity, black cloud or new white clipping. The top and direct
  underside remain deliberately subtle at normal scale; the oblique underside
  now exposes broader regional light/dark bands instead of relying only on its
  thin lower-edge streaks. The change is measurable more readily than it is
  dramatic, so it is accepted only as a minimum stratus-relief correction,
  not as completion of the native-cloud visual task.
- Registered image analysis finds zero-pixel translation for all three pairs.
  On the fixed cloud ROIs, mean luminance moves by `-0.00555` top,
  `+0.00274` vertical and `+0.00341` oblique, inside the declared
  `+/-0.015` top and `+/-0.020` underside exposure bounds. The candidate's
  broad-band relief reaches `std/range=0.00359/0.01123` top (required
  `0.0035/0.010`), `0.00382/0.01131` at the top centre (required
  `0.002/0.006`), `0.00620/0.02009` vertical underside (required
  `0.0045/0.015`) and `0.00928/0.03036` oblique upper mass (required
  `0.0065/0.020`). Every predeclared contrast floor is crossed, but several
  only narrowly. On the narrower `sigma8..sigma64` band the top reaches only
  `0.00283/0.00814`, so the improvement is broad relief rather than medium
  detail. Independent-field validation is mandatory before treating the
  calibrated bias as robust.
- The oblique pair contains real lower-boundary/terrain evolution that a
  straight-RGB-only shader response cannot cause. That changing edge is not
  counted as causal relief evidence; measurements use a fixed upper-mass ROI.
- Full-frame candidate clipping is `0%` white and `0%` black for all three
  views. HSV saturation p95 is `0.0995` top, `0.1746` vertical and `0.1667`
  oblique. The two underside values exceed the historical `0.12` target, but
  their controls were already `0.1575/0.1569` under this metric and the exact
  Iter93 saturation formula was not archived. No obvious chromatic shift is
  personally visible; underside saturation remains a monitored uncertainty.
- The closest stable performance windows use 43 cells/coverage `0.353` for the
  control and 42 cells/coverage `0.351` for the candidate, with history valid.
  Raymarch median/P90 moves from `0.718848/0.753664 ms` (`n=11`) to
  `0.772096/0.800768 ms` (`n=15`): about `+0.053/+0.047 ms`. Confidence in
  attribution is medium because the states are close rather than identical.
  This timer excludes weather-map generation, shadow passes, composition,
  CPU frametime, FPS and VRAM; those costs cannot be inferred from it.
- Backend ownership was re-read before continuing. `ClientCloudRenderOwnership`
  resolves Simple Clouds or vanilla whenever Simple Clouds is installed, so
  neither PA volumetric nor PA fallback passes can render in that runtime.
  Without Simple Clouds it selects at most one PA pass, vanilla clouds are
  cancelled only for that selected owner, and native whiteout requires visual
  density actually published by the selected PA renderer. No hybrid native /
  Simple Clouds visual path was found in these hooks.

## Iteration 99 - independent stratus rejects visual robustness

- The same hash-verified production candidate
  `3685B240B517405073D88CD1EDDFF2966B1BBA234CC6316B0C57AD996598A266`
  was launched again with no Simple Clouds, CrackersLib, Oculus or Iris in the
  instance. Ownership remained PA volumetric, final composite and Ultra at
  `0.75` resolution scale; history was disabled for the captures.
- Old fields were cleared and a new independently randomized
  `stratus_nebulosus` was spawned. After client-cache convergence exactly one
  field was rendered: UUID prefix `1b008536`, centre `(227.86,255.80,75.02)`,
  base/top `249.75..269.84`, 24 active/rendered cloudlets plus its macro cell.
  At the diagnostic frame its weather map had 5,379 active texels, base-height
  variance `0.19`, top-height variance `0.58` and thickness variance `0.30`.
- Exact noon top, vertical-underside and oblique-underside captures are under
  `build/visual-test/stratus-independent-candidate-u`. These are personal
  in-game observations, not a compile/log-only validation.
- The independent field fails the intended visual robustness test. Its top is
  again an almost featureless pale sheet with only a few faint terrain marks.
  The direct underside is a smooth solar gradient. The oblique underside has a
  broad darker lower band and a gently undulating edge, but very little
  readable relief inside the cloud mass. No hard ring, white/black clipping,
  depth leak or gross edge artifact is visible in these three views.
- This does not invalidate the causal A/B result on the iteration-98 field: the
  centered response acted there and crossed the declared broad-band floors.
  It does show that a cue calibrated to one field is insufficient to solve the
  general production appearance. The next change must address the shared
  radiance/contrast compression or another verified common cause; the stratus
  gain will not simply be increased. Quantitative independent-field metrics and
  shader root-cause review are pending before the next code modification.
- The same oblique camera was also personally inspected at time `0`, `12000`
  and `18000`; evidence is stored beside the noon captures. Sunrise produces a
  convincing darker grey underside and a narrow silver edge toward the sun,
  while sunset gives a coherent warm pink underside with directional bands.
  Night remains readable as a blue-grey deck, but the world below becomes
  nearly black and the cloud interior is again broad and smooth. Thus the
  day-cycle colour transport is visibly active and the noon flatness is not a
  dead lighting path; the remaining defect is insufficient local contrast
  under strong ambient/daylight compression. A straight horizontal distant
  fog/sky boundary is visible low in the sunrise/sunset frames and will be
  tracked separately from the cloud silhouette.

## Iteration 100 - late same-field control rejected after lifecycle expiry

- Before editing lighting, the iteration-99 field was returned to noon and the
  exact top/vertical/oblique cameras were captured under
  `build/visual-test/stratus-lighting-control-v`, followed immediately by save
  and clean client shutdown.
- Personal inspection shows that the cloud had already completed its lifecycle:
  the top view is unobstructed terrain and both underside views are open blue
  sky. These images contain no stratus material and are invalid as a shader
  control. They will not be used for visual, colour, contrast or performance
  comparison.
- This confirms again that movement freeze does not freeze field ageing. A new
  independent field must be spawned and the entire control-capture-save-close
  sequence completed immediately; no source modification follows from this
  rejected attempt.

## Iteration 101 - fresh causal control for stratus noon lighting

- Still on the iteration-98 production JAR, a fresh independent
  `stratus_nebulosus` was created after clearing old fields. The renderer
  stabilized with one field, 36 cloudlets plus its macro cell, coverage
  `0.357`, history off and final Ultra composite. Diagnostic UUID prefix is
  `46ec0e50`, centre `(454.11,255.81,65.56)`, base/top `249.66..270.27` and
  radius `651.38` at capture preparation.
- Exact top, vertical-underside and oblique-underside noon controls were taken
  within seconds under `build/visual-test/stratus-lighting-fresh-control-w`.
  The world was flushed and the client closed immediately, before any source
  edit, so the next build can reopen the same saved state and camera geometry.
- Personal inspection shows the target defect clearly. From above, terrain is
  visible through a nearly white, low-contrast veil. Directly below, the deck
  is visually reduced to a smooth grey/blue solar gradient. Obliquely, a broad
  continuous lower mass and gentle base undulations are present, but its
  interior remains pale and shallow. No clipping or hard weather-map ring is
  visible.
- This is the valid causal control for the first profile-1 lighting-compression
  change. The candidate will change only stratus daylight radiance mapping:
  density, alpha, extinction, depth, morphology and all other profiles remain
  fixed. It must reopen this saved field and capture the exact coordinates
  before any new spawn or long diagnostic pause.

## Iteration 102 - profile-local noon headroom candidate

- `sampleLighting` now receives the already-resolved categorical profile. For
  profile 1 only, a high-sun weight (`LightDir.y 0.58..0.90`, suppressed by
  `NightFactor`) lowers tone exposure from `1.30` toward `1.02` and lowers the
  optically buried ambient-retention endpoint toward `0.52`. Material with
  direct light transmission still keeps full ambient; low sun, sunset, night,
  all other profiles, density, extinction, alpha and depth are untouched.
- Full compile, resource processing, cloud-field sandbox, tests and packaging
  passed. The packaged shader was inspected for both new expressions; the
  build/runtime JAR hashes matched at
  `42AA3DDB541FF00A96C2045ADC982E778C7A00A71AE4F0C56EF090E465C98F6C`.
- The iteration-101 saved field was reopened without Simple Clouds and captured
  immediately at its exact top, vertical-underside and oblique-underside noon
  coordinates, with history off. Candidate evidence is under
  `build/visual-test/stratus-lighting-candidate-x`.
- Personal side-by-side inspection does not yet establish a large improvement.
  The top remains a pale translucent veil over terrain and the vertical view
  remains a smooth solar gradient. The oblique underside appears fractionally
  darker with its central broad bands slightly easier to locate, but it is not
  a decisive qualitative change at normal scale. No new clipping, hue break,
  silhouette discontinuity or black cloud is visible.
- A registered causal image comparison is pending. No stronger exposure,
  ambient or relief change will be made until it determines whether this
  profile-local headroom created real local contrast or merely shifted the
  mean luminance.
- Registered A/B analysis gives zero-pixel translation on all views and rejects
  the candidate as a general relief correction. Mean luminance falls from
  `0.8442` to `0.7772` top (`-7.9%`), `0.8474` to `0.7911` vertical
  (`-6.6%`) and `0.8517` to `0.8069` oblique upper mass (`-5.3%`). This exceeds
  the accepted exposure bounds by a wide margin.
- The top's detrended relief changes by only `-1.6%` overall and approximately
  zero at medium scale. Vertical broad contrast increases `22..25%`, but is
  dominated by the smooth solar gradient; oblique broad contrast improves only
  `4..8%` and medium detail is effectively unchanged. A degree-4 spatial
  polynomial explains `85.8..88.1%` of the difference variance, proving the
  change is predominantly exposure/gradient rather than morphology.
- The oblique silhouette is stable (median displacement `0 px`, p5/p95
  `-3/+3 px`, correlation `0.9961`) and no black/white clipping is introduced.
  HSV saturation p95 rises from `0.101/0.150/0.194` to
  `0.122/0.168/0.214` for top/vertical/oblique.
- Iteration 102 is therefore rejected and its profile parameter, reduced
  ambient-retention endpoint and reduced tone exposure must be removed. The
  lighting-path diagnosis remains useful, but a production fix must add
  world-stable local material contrast instead of globally darkening noon.

## Iteration 103 - exact stratus material signal is strong but hidden by radiance

- All rejected iteration-102 lighting changes were removed. A temporary
  diagnostic reconstructs once, at the first visible profile-1 hit, the exact
  horizontal material expression already used by `familyMacroShape`: advected
  profile-1 base noise, low FBM, remapped base carrier, wind-oriented carrier
  and condensate. It outputs that scalar directly as opaque greyscale so scene
  alpha and the final composite cannot contaminate its measured value.
- Full compile, resource processing, sandbox, tests and packaging passed. The
  diagnostic marker was verified inside the packaged shader and build/runtime
  hashes matched at
  `FA9CD7CE419383FCDB637DD2FD11E32A66297CE7EE301A47E4B4E8C42FCC7465`.
- The same saved field and exact cameras were reopened immediately, with
  history off. Captures are under
  `build/visual-test/stratus-material-signal-diagnostic-y`. Personal inspection
  finds strong, coherent dark/light structures across the whole top and direct
  underside. The oblique view contains large connected bands and cells aligned
  with the cloud mass rather than an unrelated screen-space gradient.
- This proves that usable material morphology reaches the density function and
  is spatially rich; the production lighting path is failing to communicate it
  in final colour. The next production mapping can reuse this existing signal
  without inventing a second silhouette or changing alpha/density.
- The diagnostic also exposes dense high-frequency stippling. Because the
  scalar is sampled at the jittered first material hit, that fine component can
  vary with ray-entry position even though the underlying domain is
  world-stable. Production must suppress that component through a stable
  footprint/mip or a bounded low-frequency mapping; it must not transfer the
  diagnostic grain directly into radiance.
- Exact distribution, frequency decomposition and centered response calibration
  are pending. The opaque greyscale override is diagnostic-only and must be
  removed before any production validation.

## Iteration 104 - first local high-pass capture rejected after field expiry

- The raw-signal distribution is strongly field/face biased: mean values are
  `0.793` top, `0.746` vertical and `0.758` oblique, with p5/p95 spanning
  roughly `0.49..0.96`. Fine jitter contributes RMS `0.040..0.066`, while
  strong useful structure remains above 16 pixels. A fixed global centre would
  therefore transfer both field bias and grain into production colour.
- A second diagnostic anchors material Y to the actual weather base/top and
  computes a signed local high-pass: centre minus the mean of four symmetric
  profile-1 samples 64 world blocks away. It encodes zero as opaque 0.5 grey.
  Full build/tests passed and build/runtime hashes matched at
  `9A2B13535F6CE822DCA7A44A112E19CBB5BB6CD03DF95BD1AB26A621FDA2F4C8`.
- The saved field had expired before the exact cameras were recaptured. Images
  under `build/visual-test/stratus-material-highpass-diagnostic-z` show open
  sky rather than diagnostic cloud material and are invalid for signal
  analysis. No visual or quantitative conclusion is taken from them.
- The diagnostic JAR remains active. A fresh independent stratus will be
  spawned and captured immediately without another code change; only that live
  field can validate the local high-pass.

### Iteration 104 valid fresh-field capture

- Without changing the diagnostic shader or runtime JAR, a new native
  `stratus_nebulosus` was spawned at noon with movement frozen and history off.
  After a short synchronization overlap, the renderer settled to one visible
  field with 25 rendered cells, coverage `0.340`, centre approximately
  `(336.95,255.65,75.42)` and base/top `249.67..271.03`. Simple Clouds is not
  installed in this instance.
- The exact top, direct-underside and oblique-underside diagnostic captures are
  stored under
  `build/visual-test/stratus-material-highpass-fresh-diagnostic-aa`. They were
  taken within seconds of one another, then the client was closed cleanly;
  shutdown saved all three dimensions.
- Personal inspection confirms that the signed local signal is alive and
  materially stronger than the final noon radiance. Both normal-incidence
  views contain broad connected light/dark masses rather than a uniform grey
  field. Their average remains visually close to neutral grey, so neighbour
  subtraction removes the large field/face DC offset exposed by iteration 103.
- The signal is not yet safe to copy literally into production. Fine stipple
  remains visible, especially along strong transitions. The oblique view shows
  extreme perspective-aligned streaking toward the horizon. Some geometric
  stretching is expected for a shallow layer, but keeping `p.xz` from the
  jittered first hit makes this diagnostic unsuitable as a world-stable colour
  cue at grazing angles.
- This validates the high-pass concept while rejecting the current sampling
  position as the final implementation. Before production mapping, XZ must be
  anchored to a deterministic base/top ray intersection (with a grazing-angle
  fade), and the response must be strongly bounded. Quantitative distribution
  and frequency measurements are pending; no production parameter is selected
  from visual inspection alone.

## Iteration 105 - deterministic material-surface diagnostic

- The diagnostic-only material lookup now intersects each exterior stratus ray
  with its selected weather-map face, refines that height once at the projected
  XZ, and evaluates the centre plus the same four 64-block neighbours there.
  The signed result is multiplied by `smoothstep(0.08,0.25,abs(rayDir.y))`;
  rays below that grazing threshold keep the normal cloud result. The existing
  weather-surface differential was moved to the same stable XZ. No production
  colour or density parameter was changed.
- Compile, resource processing, sandbox, tests and packaging passed. The
  packaged diagnostic marker was verified and build/runtime JAR hashes matched
  at `1303CB79C397459EF947FF5AFB332A4EE03C9D2C96A12D0A5BEB30222D018B4C`.
  The runtime mod directory contained no Simple Clouds, CrackersLib, Oculus or
  Iris JAR.
- A new noon `stratus_nebulosus` was captured with history off and one visible
  field (24 cloudlets plus macro, coverage about `0.357`, centre approximately
  `(314.37,256.12,-186.68)`, base/top `249.85..270.73`). Images are under
  `build/visual-test/stratus-material-anchored-diagnostic-ab`; the client then
  shut down cleanly and saved all dimensions.
- Personal inspection confirms that the normal-incidence top and underside
  still retain broad coherent bands while the dense pixel stipple is no longer
  apparent at ordinary scale. The oblique image no longer has the previous
  dense black/white radial texture; it retains broad perspective-stretched
  bands near the shallow layer, as expected from projecting a horizontal
  material field toward the horizon. The diagnostic's one-for-one amplitude
  exaggerates those bands; production will use only a few percent response.
- The screenshots expose a capture-harness nuisance rather than a renderer
  defect: F2 toast text is visible at the bottom of the latter two frames and
  must be excluded from quantitative masks. Cross-iteration comparison is also
  non-causal because iterations 104 and 105 use different generated fields.
  Distribution/frequency analysis of this fresh series is pending before the
  opaque override is removed.
- Quantitative analysis excluding the toast region confirms the intended
  stabilization despite the different fields. Approximate 2-pixel residual RMS
  falls from `0.00513` to `0.00172` top (`-66%`), `0.00400` to `0.00164`
  vertical (`-59%`) and `0.00441` to `0.00133` oblique (`-70%`). Fine-to-broad
  signal ratios fall to `2.1%`, `1.9%` and `2.3%`, respectively. Oblique
  gradient anisotropy does not increase (`2.00 -> 1.86` at radius 4 and
  `1.71 -> 1.45` at radius 16), so the remaining large streaks are consistent
  with perspective rather than reintroduced high-frequency jitter.
- There is no diagnostic clipping. A bounded production mapping is now
  justified; the opaque override can be removed. The first colour candidate
  will combine the existing geometric and material drives before one soft cap,
  rather than adding two independently saturated responses.

## Iteration 106 - fresh production-colour control

- The opaque diagnostic output was removed, leaving deterministic material
  sampling present but not connected to colour. Full build/tests passed; the
  packaged shader was checked to contain no iteration-104/105 diagnostic
  marker and build/runtime hashes matched at
  `A8479F57A3E0E9F7D89B0E74A36A535BF5511AA3AFA82D4AC8172EC718E1F5A8`.
- A fresh noon stratus was stabilized with history off and one visible field
  (24 cloudlets plus macro, coverage about `0.349`, centre approximately
  `(215.70,255.95,-456.59)`, base/top `250.06..269.64`). Exact top, direct
  underside and oblique underside controls are under
  `build/visual-test/stratus-material-production-control-ac`. HUD was hidden;
  no screenshot toasts contaminate this series. The client was then closed
  immediately and saved all dimensions for the causal candidate reopen.
- Personal inspection confirms a severe noon radiance-compression defect in
  this field. From above, the frame is almost entirely a white translucent
  veil; terrain is only faintly perceptible near the centre and no broad
  material bands are recognizable. Directly below, the sun-facing deck is
  likewise nearly uniform white. The oblique view preserves a coherent shallow
  underside silhouette but its interior is a very smooth pale gradient with
  only weak broad shading.
- This is the causal colour control for a material-response candidate. The next
  source change will keep density, alpha, extinction, weather data, cameras and
  the stabilized material signal fixed, and will only combine that signal with
  the existing geometric response before one bounded tone multiplier.

## Iteration 107 - bounded production material response

- The stratus post-integration response now combines
  `1.5 * (surfaceRelief - measuredBias)` with
  `0.22 * stableMaterialRelief` before one `tanh`, capped at `+0.055/-0.075`.
  Density, alpha, extinction, ray steps and the five material samples are
  identical to the iteration-106 control. Full build/tests passed; packaged
  expressions were verified and build/runtime hashes matched at
  `D8BADC3D79AEC8CB3BF4F7F9C1404F5EB2D5A4584FAF4B79DC09596F0F2C81E6`.
- The saved iteration-106 field reopened with the same 24 cloudlets, coverage
  `0.349`, centre and base/top. History was disabled and the exact three noon
  cameras were captured under
  `build/visual-test/stratus-material-production-candidate-ad` before clean
  shutdown. This is a causal same-field, same-camera comparison.
- Personal comparison finds a real but bounded improvement. Broad material
  bands become visible in the previously near-uniform top veil, with no pixel
  grain or hard embossing. The oblique underside gains coherent shallow bands
  while preserving its silhouette and smooth layer boundary. The direct
  sun-facing underside remains almost uniform white; this local material cue
  alone does not solve the remaining high-sun lighting compression.
- No obvious black clipping, white contour, hue discontinuity or new alpha edge
  is visible. Registered image analysis is pending before accepting the gain.
- GPU timing does not establish a candidate regression. Both control and
  candidate already pay for the identical five stable material samples; only a
  few scalar ALU operations differ. Matching control samples have median/p90
  `0.881/0.905 ms` (`n=14`). The last stable candidate window is
  `0.905/0.939 ms` (`n=12`), while a longer candidate window contains sporadic
  `1.1..1.6 ms` outliers. The approximately `0.02..0.03 ms` shift is below the
  noise demonstrated by those outliers and is therefore inconclusive, not a
  confirmed performance cost.
- Registered A/B comparison has zero-pixel translation in all three views and
  accepts the cue. Mean luminance changes only `-0.28%` top, `-0.53%` in the
  sun-facing vertical view and approximately zero oblique. Detrended contrast
  increases `2.18x` top, `1.49x` vertical and `1.30x` oblique; medium/large
  structure rises `2.63x/3.00x`, `1.88x/1.56x` and `1.32x/1.19x`, while fine
  structure remains `1.02x`, `not elevated`, and `0.95x`, respectively. This
  confirms that the candidate exposes coherent morphology rather than grain or
  a global exposure shift.
- No black/white clipping or saturation regression is measured. The common
  oblique silhouette remains essentially identical (correlation `0.99984`,
  median edge shift about `+1 px`, RMSE `2.44 px` attributable to colour/TAA
  classification). Iteration 107 is accepted as the production stratus
  material cue, with moving-camera validation still required for the mildly
  brushed-looking oblique bands.

## Iteration 108 - temporal and lighting validation of accepted stratus cue

- The accepted iteration-107 JAR was reopened on the same saved field with
  temporal history enabled. The sequence under
  `build/visual-test/stratus-material-temporal-ae` contains a stable oblique
  reference, a 32-block lateral teleport captured immediately and after two
  seconds, a 45-degree yaw captured immediately and after two seconds, and an
  in-cloud teleport captured immediately and after two seconds.
- Personal inspection finds no doubled cloud edge, stale material imprint,
  high-frequency shimmer or delayed ribbon after the lateral move. The broad
  bands move with the projected world surface. Immediate/settled yaw frames are
  visually indistinguishable at normal scale, with no old-view ghost. In-cloud
  frames converge to the same smooth blue-grey whiteout without a bright flash
  or black frame. Quantitative same-camera differencing is pending.
- The in-cloud result remains intentionally low-contrast, but is visually very
  uniform: it communicates whiteout rather than internal depth. This is a
  remaining quality limitation, not a regression caused by the new exterior
  stratus cue (that cue is explicitly disabled for `cameraInsideCloud`).
- The same field was captured at sunrise, sunset and midnight under
  `build/visual-test/stratus-material-lighting-af`. At low sun, the underside
  becomes substantially darker and the illuminated horizon develops a bounded
  silver/warm rim; the interior material bands remain readable. Sunset colour
  is warm without turning the whole deck orange. At night the deck stays
  blue-grey and distinguishable from the nearly black sky rather than becoming
  pure black or white.
- The top low-sun views reveal the loaded-terrain footprint through the
  translucent deck as a large stepped blue/grey patch. Its block/chunk-shaped
  boundary matches scene visibility under the straight-down camera rather than
  the smooth weather-map/cloud silhouette, so it is recorded as a likely scene
  render-distance/fog visibility artifact, not yet a confirmed cloud-density
  discontinuity. It must be checked with a larger view distance or an opaque
  reference before assigning a cloud fix.

## Iteration 109 - current-JAR native family and raw-composite validation

- The accepted iteration-107 production JAR
  (`D8BADC3D79AEC8CB3BF4F7F9C1404F5EB2D5A4584FAF4B79DC09596F0F2C81E6`)
  was exercised again in the dedicated native instance. The runtime mod
  directory still contained no Simple Clouds, CrackersLib, Oculus or Iris JAR.
  Ultra quality, noon lighting, frozen native drift and disabled temporal
  history were used. Images are under
  `build/visual-test/final-current-jar-matrix-ag`; the client then closed
  cleanly and saved all dimensions.
- Personal inspection rejects the first overview of `cumulus_humilis` as a
  useful morphology reference because the generated field was only about 54
  blocks in radius and was framed from 450 blocks. A valid close replacement
  (`01-cumulus-humilis-close.png`) confirms a real remaining defect: the cloud
  is a smooth, shallow, almost uniformly white lens. It has a reasonably flat
  base but no recognizable merged cauliflower lobes or shaded interior.
- `stratocumulus` is immediately distinct from humilis by its approximately
  566-block horizontal extent, broad low deck and dark underside. It remains
  too smooth and lens-like, with weak medium-scale cells/openings. `cirrus` is
  correctly thin and horizontally anisotropic rather than reusing a cumulus
  lobe, but the noon sample is so faint that it is barely readable against the
  sky. These are accepted family distinctions with calibration limitations,
  not evidence that every profile is finished.
- `nimbostratus` forms a broad continuous rain shield with a substantially
  darker base than stratocumulus, a modestly uneven top and precipitation tied
  to the field. The visible rain/virga shafts are nevertheless too evenly
  spaced, vertically rectangular and detached-looking at their lower ends.
  Its two-lobe lens silhouette is still more like a tablet than a frontal
  weather mass.
- Fresh single-visible-field close captures conclusively reject the current
  severe morphology. `cumulonimbus_capillatus` becomes a compact group of
  narrow cone/spire columns with no broad, continuous anvil. A medium-distance
  `supercell` view shows hard stacked shelves, isolated floating fragments and
  tall hook-like needles. A below-envelope view amplifies the same failure into
  a huge smooth extrusion. These are renderer/data-shape defects rather than
  framing artifacts; diagnostics reported one rendered/partial PA field at the
  time of each accepted close capture.
- Raw-composite discrimination was repeated on a fresh stratus after a
  15-second top-camera settle. `final`, `color` and `alpha` all show the same
  broad, continuous material/density organization; no stepped chunk outline is
  present in this settled series. Thus the earlier top stepped patch is not a
  reproducible weather-map edge. The raw alpha does contain intentionally
  broad opacity variation, while the earlier terrain-shaped form remains most
  consistently explained by the translucent deck revealing loaded scene/fog.
  No cloud-boundary fix is justified from that earlier image.
- GPU query samples remain view-coverage dependent. Small/far humilis/cirrus
  frames were commonly about `0.14..0.19 ms`; medium severe views commonly
  settled around `0.36..0.45 ms`; close severe or full-screen sheet views rose
  to roughly `0.9..2.2 ms`. These are in-session diagnostics rather than a
  controlled before/after benchmark. No performance regression can be assigned
  to this validation-only iteration.
- Next corrective target is confirmed morphology, not exposure: remove the
  disconnected severe needles/shelves and recover a continuous convective
  core plus bounded anvil, then validate the result on the same close/medium
  camera classes. In-cloud internal depth remains separately confirmed weak and
  will only receive a bounded cue that does not alter alpha, fog or extinction.

## Iteration 110 - secondary severe-outflow geometry accepted partially

- Diagnostics tied the most obvious detached blades to the randomized
  secondary `ANVIL` branch in `CloudletLayout.stormLayout()`. The current 48-cell
  supercell and capillatus layouts each produced six such cloudlets. Before the
  change their horizontal minor radii were commonly only `8.5..15` blocks while
  their vertical spans were `47..70` blocks; at eight world units per weather
  texel, some supports were only one or two texels wide.
- Only that branch was changed. Its probability and role count remain
  deterministic and unchanged, but its longitudinal offset contracts from as
  much as `0.42R` to `0.26R`, cross-wind offset from `0.12R` to `0.055R`, height
  span from `0.12..0.20` to `0.08..0.13`, and horizontal aspect increases from
  `0.14..0.22` to `0.40..0.58`. Density/coverage were reduced to `0.46/0.50`.
  No shader, sampler, pass, Simple Clouds path or non-severe family changed.
- Compile, resource processing, CloudField sandbox, tests and packaging passed.
  The deployed build/runtime JAR hashes matched at
  `1E148B2EAFA1C02EF8B9D1EFC996E588183179384E1DF3895D325C0DB797509F`.
  The isolated client loaded the native owner, all PA shaders and the required
  ten fragment texture units with no Simple Clouds JAR present.
- Fresh one-visible-field observations are under
  `build/visual-test/severe-outflow-geometry-candidate-ah`. The first
  supercell medium frame is excluded because command diagnostics remained on
  screen and the selected framing did not contain the visible mass. Clean west
  and north frames confirm that the former scattered thin blades and floating
  petals are absent. The secondary outflows now merge into the main support.
- The change is accepted as a causal local improvement, not as a complete
  severe fix. Supercell still exposes several smooth conical central towers and
  its primary anvil is a large analytic shelf/panache whose apparent slope
  changes strongly with azimuth. Capillatus is more compact and recognizable as
  a dark precipitation-bearing tower with a multi-lobed crown, but the main
  mass still lacks naturally stacked medium-scale billows and its anvil can be
  weak from both tested azimuths.
- Stable capillatus samples with one visible field, 49 weather cells, Ultra
  quality, 0.75 scale and history off were commonly `0.37..0.48 ms`, with
  occasional samples to about `0.61 ms`. The comparable validation-only
  baseline was commonly `0.36..0.45 ms`; scene/camera differences are larger
  than the shift, so no GPU regression is confirmed. The geometry change adds
  no work or cloudlets.
- The capillatus field and north camera were saved by a clean shutdown for a
  causal base-shape A/B. The next candidate changes only the existing radial
  BASE endpoint coefficients so its central floor remains flat while its upper
  shoulders stop reading as a cuboid. Primary anvil and convective intervals
  remain unchanged for that A/B.

## Iteration 111 - severe BASE candidate blocked by a confirmed reload crash

- Candidate B changed only the severe `BASE` endpoint curve in
  `cloud_weather_footprint.glsl`: `baseLift 0.02 -> 0.035`, `topDrop 0.34 ->
  0.48`, and `curvePower 2.60 -> 1.70`. Compilation, resource processing,
  sandbox, tests and packaging passed. Build/runtime hashes matched at
  `9FC9EE20903ADB143AB5A4E25F718EB0CE34948065542B829F0AAC855C860C39`.
- The first B captures are under
  `build/visual-test/severe-base-rounding-candidate-ai`. They cannot be used as
  a causal A/B: the saved field continued to evolve during startup before the
  freeze command, changing its slab from the prior `221.4..590.6` class to
  `215.4..602.6` and altering the whole tower layout much more than three
  endpoint coefficients can. They do show that the current severe result is
  still visibly geometric: rectangular/planar shelves, monolithic columns,
  horizontal banding and regular precipitation curtains remain.
- To remove weather evolution from the comparison, a temporary resource-pack
  fixture was selected on the same frozen 49-cell capillatus field. Control A
  successfully loaded and two valid views were captured at the exact north and
  west cameras under
  `build/visual-test/severe-base-rounding-exact-ab-aj`.
- The fixture was then changed to candidate B and reloaded with F3+T. Minecraft
  selected the same pack and rebuilt all PA shader programs successfully, but
  the process crashed before the first B frame. The JVM fatal log is
  `run-native-jar-visual/hs_err_pid33872.log`: an NVIDIA OpenGL access violation
  occurs on the render thread in `GL12C.glTexImage3D`, called by
  `CloudNoiseTextureManager.upload3d()` from `ensureReady()` immediately after
  the asynchronous noise bake completed. There is no Java crash report and no
  shader compile error.
- Candidate B is therefore neither accepted nor rejected. No visual conclusion
  is drawn from the incomplete exact A/B. The confirmed native resource-reload
  crash must be corrected and retested before this morphology coefficient can
  be evaluated. The production source still contains B provisionally; the
  temporary runtime fixture is not a production resource.

## Iteration 112 - resource reload fixed; severe BASE candidate accepted locally

- The fatal reload path was traced against the exact Forge 47.4.20/Minecraft
  1.20.1 `NativeImage._upload()` source. Atlas uploads leave
  `GL_UNPACK_ROW_LENGTH`, `GL_UNPACK_SKIP_PIXELS`, `GL_UNPACK_SKIP_ROWS` and
  `GL_UNPACK_ALIGNMENT` configured for their last sub-image. PA previously
  passed an exactly sized direct buffer to `glTexImage3D` without resetting any
  pixel-unpack state or detaching a possible pixel-unpack buffer. The NVIDIA
  driver therefore had a valid route to read beyond the PA buffer.
- `CloudNoiseTextureManager` now validates all payload lengths, makes the three
  texture publication transactional, deletes partial uploads, preserves the
  prior 2D/3D binding, and wraps every CPU upload in a guard that detaches and
  restores `GL_PIXEL_UNPACK_BUFFER` plus all relevant row/image/skip/alignment
  state. Texture deletion now goes through Mojang's cache-aware path.
  `VolumetricCloudClientLifecycle` keeps these world-independent procedural
  textures resident across F3+T rather than rebaking eight MiB unnecessarily.
- Compile, resource processing, CloudField sandbox, tests and packaging passed.
  Build/runtime JAR hashes matched at
  `CBD392F008EF402277664DE8E98DF5EAE3653A910C030B70E67CFA0599756B8B`.
  A fresh native-only launch uploaded base/detail/blue noise successfully. One
  A-to-B reload and five additional F3+T stress reloads all completed while the
  client remained alive. The stress window contained five resource-manager
  reloads, five PA cleanup callbacks, zero noise rebakes, zero errors and zero
  new `hs_err_pid` files. The client then shut down cleanly.
- The completed same-session morphology pair is under
  `build/visual-test/severe-base-rounding-exact-ab-ak`. It uses the same frozen
  49-cell capillatus field, unchanged camera coordinates, Ultra quality and
  disabled history. Control A and candidate B retain almost the same total
  mass: the cloud mask changes by `-0.11%` north and `-1.46%` west. A registered
  silhouette analysis nevertheless confirms the intended local response. The
  upper shoulder regions contract by about `5.2..13.1%` across the four tested
  sides, while the low base changes by only `+0.5..1.5%`; mean luminance changes
  by less than one 8-bit level. B therefore removes some prismatic shoulder
  overhang without thinning the precipitation base.
- Shader material still advects from absolute `WorldTime` while the simulation
  freeze option is active, so tiny high-frequency pixel differences across the
  reload cannot be assigned exclusively to B. That limitation does not hide a
  general severe improvement: the same rectangular towers, planar layers,
  horizontal banding and regular rain curtains still dominate both results.
  Candidate B is accepted only as a measured local correction. The next
  experiment must act on the combined convective core rather than retuning the
  BASE perimeter again.

## Iteration 113 - CORE/TOWER contract fixed; monolithic severe shape remains

- `VolumetricRenderCell.EnvelopeRole` and `fromCloudletRole()` encode `CORE=3`
  and `TOWER=4`, but `paSevereCurvedLayerRange()` applied the compact secondary-
  tower curve to role 3 and the attached primary-core curve to role 4. The
  production include now assigns the primary core `baseLift=0.08`,
  `topDrop=0.68`, `curvePower=1.35`, and the secondary towers `0.14`, `0.70`,
  `1.25`. This corrects an actual CPU/GPU role-contract inversion and adds no
  pass, sample, cloudlet or texture.
- Compilation, resource processing, CloudField sandbox, tests and packaging all
  passed. The build and native-only runtime JAR hashes matched at
  `F4F9BF5FA4B35CB9CF9EC693E779C02521F32EBDB9DE11EB15EB76AB187CC561`.
  The runtime contained no Simple Clouds, CrackersLib, Oculus or Iris JAR.
- The same-session A/B is stored under
  `build/visual-test/severe-role-contract-exact-ab-al`. A temporary selected
  resource pack supplied the old role assignment for the control, then F3+T
  loaded the corrected assignment on the same frozen 49-weather-cell
  `cumulonimbus_capillatus`, at Ultra quality with temporal history disabled.
  The reload completed without a new procedural-noise bake, shader error or
  crash. The pack was deselected after the test and the client shut down
  cleanly.
- Personal inspection of the close north and west pairs confirms that this is
  not the requested major visual improvement. Both variants remain a dark,
  monolithic triangular/prismatic tower with nearly planar flanks, horizontal
  terraces, a hard BASE/core separation and a regular precipitation shaft. The
  corrected variant changes only small translucent upper shoulders.
- Registered mask measurements agree. North total area changes by about
  `-0.43..-0.57%` with `IoU=0.987`; west changes by `-0.35%` at the dense
  threshold and `-2.82%` only at the faint-edge threshold. The low BASE changes
  by at most `0.32%`. The west upper-left shoulder contracts about `15.2%`, but
  the dense bounding box is unchanged. The contract fix is therefore retained
  as a safe local correction, not credited as a recognizable morphology gain.
- Internal luminance cannot be assigned to the coefficient swap: shader
  material still advects from absolute `WorldTime` while `/pa cloud freeze
  true` is active, and the close pairs were separated by roughly 140 seconds.
  Their common interior changes by about `9.8..14.5/255` in absolute value even
  though the silhouette remains registered. This reiterates that the visual
  freeze command does not freeze procedural material time.
- Source inspection confirms the dominant remaining cause. Both exact severe
  passes select one shared maximum-support winner across `CORE` and `TOWER` for
  each weather-map texel. Every other overlapping convective billow is discarded
  before raymarching. The raymarch then remaps ordinary support to a saturated
  `coreSupport`, so its height-dependent taper is `1` through most mature
  interiors. The next candidate must preserve CORE and TOWER as independent
  intervals; another perimeter coefficient adjustment cannot recover the lost
  volumes.

## Iteration 114 - independent TOWER interval accepted; severe terracing remains

- Source inspection confirmed that the two exact severe maps reduced every
  overlapping `CORE` and `TOWER` cloudlet to one shared maximum-support winner
  per weather texel. The renderer therefore discarded the secondary convective
  billows before raymarching even though the CPU layout generated them.
- Severe geometry now preserves `CORE` and `TOWER` as independent vertical
  intervals. A new `RGBA16F` tower target stores premultiplied support, base and
  top, is generated only for severe fields, participates in the weather-map
  cache key and is destroyed with the other render targets. The volume shader
  decodes it through a dedicated sampler and unions `towerMass` with the prior
  BASE/CORE/ANVIL result using `max`, never additive density. The added target
  costs exactly 2 MiB at the Ultra 512-square weather-map resolution. Manual 3D
  noise bindings moved to units 9/10 and the verified fragment-unit contract is
  now 11.
- Compilation, resource processing, CloudField sandbox, tests and packaging all
  passed. The deployed build and native-only runtime JAR hashes matched at
  `873601E3A9F849E9888793C1C746E4F79A4731706DBF9E7D4F850D0625A3B922`.
  The isolated instance contained no Simple Clouds, CrackersLib, Oculus or Iris
  JAR. PA registered the new shader set successfully and reported 32 available
  fragment texture units for 11 required units.
- The same-session comparison is stored under
  `build/visual-test/severe-independent-tower-exact-ab-am`. A temporary selected
  resource pack changed only `paUnionIndependentStormTower()`: control A returned
  the existing BASE/CORE/ANVIL mass, while candidate B returned
  `max(existingStormMass, towerMass)`. The field remained the same frozen
  49-weather-cell `cumulonimbus_capillatus`, at noon, Ultra 0.75 scale and with
  temporal history disabled. Resource reloads rebuilt the PA shaders without a
  procedural-noise rebake, shader error or crash.
- The first west/north pair was separated by several minutes and is not accepted
  as an exact geometric comparison: the west apparent scale changed in a way a
  `max` union cannot cause, demonstrating residual field/material advection.
  A tighter north-facing B-A-B bracket was therefore captured within consecutive
  reloads as `05`, `06` and `07`. The two B frames agree with one another. In A,
  the upper convection is one rectangular dark monolith; both B frames recover
  several attached rounded TOWER lobes on its right shoulder and a smaller
  secondary shoulder on the left. Because the operation is a maximum union,
  these are preserved volumes rather than a global opacity increase.
- Registered bracket measurements confirm the causal reading. Candidate B1 and
  B2 agree at `IoU=0.9792`, while each candidate versus A is only
  `IoU=0.9122..0.9201`. Against A, their mean silhouette grows `+14.9%` at the
  summit band, `+13.1%` in the upper band, `+1.7%` through the middle and
  `+7.0%` at the base (`+7.5%` total). Of the stable changed pixels, 4,628 are
  additions and only 160 are removals; 99.5% of additions touch the original
  mass. The added lobes are therefore connected and do not form a second cloud.
  The absolute top is not higher, so this restores shoulders/billows rather
  than proving additional vertical growth.
- The architectural correction is accepted as a causal, visible morphology
  improvement. It is still far from a satisfactory severe cloud. All three
  bracket frames retain broad analytic shelves, hard horizontal terraces,
  strongly banded raymarch surfaces, an abrupt BASE/core junction, a very dark
  central slab and a regular precipitation curtain. Candidate B adds billows but
  does not make the storm naturally continuous. Strong horizontal-edge density
  actually rises from about `12.0%` in A to `14.2..15.0%` in B because the new
  towers inherit the same terraced material/taper response.
- Sparse same-view GPU query samples across the bracket occupy roughly
  `0.55..1.16 ms`, and the candidate samples overlap the control samples. These
  status samples are too few and too sensitive to material advection to assign a
  reliable delta. No disproportionate raymarch regression is observed, but the
  new weather-map pass still requires a dedicated cache-miss timing before its
  generation cost can be confirmed.
- Production retains the independent interval and `max` union. The next
  experiment must target the saturated convective taper and/or the confirmed
  source of the horizontal terracing in an isolated A/B; the independent-map
  correction is not credited as completion of the native visual task.

## Iteration 115 - severe history/full-resolution diagnostic; camera context bug confirmed

- A fresh native-only production session was launched with no A/B resource pack
  selected. Simple Clouds remained absent and PA selected its native service.
  A new `cumulonimbus_capillatus` was observed from the north under Ultra at
  `build/visual-test/severe-history-fullres-diagnostic-an`.
- Temporal history at the normal 0.75 Ultra scale slightly integrates the fine
  dither but leaves the dominant horizontal terraces, analytic shelves and hard
  BASE/core junction intact. Forcing the volume target to full display
  resolution produces the same large-scale silhouette and terraces. Turning
  history off again at full resolution increases fine grain but does not create
  the broad layers. The dominant severe defect is therefore not caused by the
  low-resolution composite or temporal reprojection alone.
- Source/runtime correlation exposed a separate confirmed error in rainy views.
  The camera was at `Y≈199.4` while the actual cloud slab began near `Y≈215.7`.
  `precipitationRayPadding()` nevertheless lowered the raymarch volume by up to
  180 blocks, after which `cameraStartsInsideSlab = t0 <= 1.0` classified this
  below-cloud camera as inside the cloud slab. That flag then capped ray-origin
  jitter at 0.75 block instead of the exterior `fineStep` and reduced the Ultra
  light cone from six taps to four. The result is spatially coherent sampling
  planes under precisely the heavy-rain clouds where the banding is worst.
- The next A/B will change only camera-context classification and the ray-origin
  jitter contract: actual cloud-slab membership will use `SlabBaseY..SlabTopY`,
  while the 0.75-block jitter cap will apply only when canonical visual density
  confirms the camera is genuinely inside rendered cloud. The extended
  precipitation march bounds remain unchanged. Convective taper, density,
  lighting coefficients and morphology maps will not change in that A/B.

## Iteration 116 - exterior ray-origin jitter accepted; structural shelves remain

- The volume shader now routes ray-origin phase through
  `cloud_ray_origin_jitter.glsl`. Production keeps the 0.75-block origin only
  when canonical `CameraCloudDensity` says the camera is genuinely inside cloud
  material; every exterior ray uses the complete `fineStep` blue-noise phase.
  A temporary selected resource pack retained the former
  `cameraStartsInsideRayVolume` condition for control A. No density, morphology,
  lighting, step-count, sampler or render-target setting changed.
- Compilation, resource processing, CloudField sandbox, tests and packaging all
  passed. The build/runtime JAR hashes matched at
  `CF8EFCCA17A83613A9C3BD0697C65B1E349ECF3FF776A19348C4A21E6C6E005C`.
  The native-only client selected the control pack, registered all PA shaders,
  reported 32 available fragment units for 11 required, and loaded the same
  saved rainy capillatus field without Simple Clouds.
- The valid exact pair is
  `build/visual-test/severe-ray-origin-jitter-exact-ab-ao/02` and `03`.
  Both use the same camera (`Y=199.4` below a `Y≈215.7` slab), frozen movement,
  noon, Ultra 0.75 scale and history off. Capture `01` is excluded because the
  old F2 helper recentered the grabbed mouse and rotated the camera; `02/03`
  were taken through the window event queue after reapplying the exact pose.
- Personal inspection shows a strong causal sampling improvement. Control A
  contains long, coherent horizontal/diagonal hatching across the lower BASE
  and convective shoulders. Candidate B keeps the same meteorological silhouette
  but breaks those aligned shells into fine spatially decorrelated blue-noise
  grain. The rain curtain and broad role geometry remain in place, proving that
  the change did not merely erase cloud density.
- Candidate B with history enabled was also observed on a fresh capillatus as
  capture `05`. This is not a geometric A/B because it is a new field, but it
  confirms the expected runtime behavior: most fine exterior grain integrates
  into a smoother surface while the large BASE shelves, hard interval junctions
  and slab-like lobes remain visible. The jitter correction fixes sampling
  banding; it does not fix the structural terraces.
- Registered image measurements support that reading. The cloud silhouette is
  stable (`IoU=0.956..0.970`, identical bounding boxes and total area within
  `99.2..101.1%`), while strong horizontal-edge density falls from `42.5%` to
  `32.5%` (`-23.6%`). Directional edge coherence falls from `0.222` to `0.066`
  (`-70%`), the mean aligned run shortens from `2.35` to `1.65` pixels and its
  95th percentile from `6` to `4` pixels. The longest large-scale aligned run
  drops from `127` to `78` pixels. Laplacian RMS rises `17.7%`, as expected when
  a coherent band is exchanged for finer dither; mean luminance changes by only
  `-0.9%`, body area by `-0.22%` and dense area by `+1.1%`. The rain shafts are
  preserved.
- The correction adds no texture read, loop iteration or allocation. Sparse GPU
  status samples remain view/coverage dominated and cannot assign a reliable
  timing delta, but there is no theoretical raymarch-work increase from changing
  only the blue-noise phase distance.
- Production retains the exterior full-step jitter. The next morphology A/B
  must be separate: use unsaturated raw TOWER support for height-dependent taper
  and widen its vertical edge transition, without changing CORE or BASE in the
  same experiment.

## Iteration 117 - raw TOWER taper rejected; no crown narrowing demonstrated

- Production candidate `paStormTowerTaper()` replaced the former taper input
  (`towerSupport`, already remapped and saturated) with
  `clamp(rawTowerSupport * CoverageMul, 0, 1)`. Its thresholds preserved roughly
  the former demand at the root while increasing raw-support demand toward the
  crown. A selected resource-pack include supplied the old remapped-support
  function as control. No other density, band, material, map or lighting term
  changed.
- The native-only client loaded the candidate build successfully without Simple
  Clouds. Compile, resource processing, CloudField sandbox, tests and packaging
  had already passed for build/runtime hash
  `18F8386EF1340757214ABDF44669AB9016AA6671ABF011B25C180DE2F886A31C`.
  Both F3+T transitions re-registered the PA shaders without a shader error,
  procedural-noise rebake or crash. The exact 1280x720 control-candidate-control
  bracket is under `build/visual-test/severe-tower-taper-exact-ab-ap/01..03`.
  Every capture reapplied camera position `(-488.18, 199.44, -6427.43)`, yaw
  `0`, pitch `-34`, and used direct F2 window messages; the mouse-recentering
  helper was not used.
- Personal inspection does not show a reliably narrower or more natural crown.
  All three frames retain the same bright stacked upper lobes, triangular dark
  CORE, hard BASE/core shelf and regular rain curtain. The candidate slightly
  changes faint perimeter material but does not remove the monolithic severe
  silhouette.
- Registered bracket measurements confirm rejection. Alignment is exact and
  clear sky is identical. At the approximately `0.20` opacity mask, crown areas
  are `9076 / 9167 / 9247` pixels and upper areas
  `21748 / 21824 / 21940` for control-1 / candidate / control-2: the candidate
  lies strictly between temporal controls. Mean shoulder widths are
  `253.44 / 253.14 / 253.86` pixels (candidate residual `-0.17%` versus linear
  control interpolation), while crown widths are
  `185.45 / 187.42 / 191.28` (`+0.10%`). At a denser `0.40` mask the candidate
  crown is instead about `+2.1%` versus interpolation and total area `+0.26%`.
  Its `-2.39%` faint-mask total-area residual comes primarily from the lower
  cloud (`-4.54%`), not the summit. Upper additions/removals remain
  `98..100%` attached within three to five pixels; no detached tower appears.
- Strong horizontal-edge fraction falls to `45.1%` in the candidate versus
  `49.8% / 49.1%` in the two controls, and shoulder edges to `45.8%` versus
  `55.4% / 51.8%`. With only one candidate material phase and no demonstrated
  geometric crown response, this secondary change cannot be assigned safely to
  the taper.
- The raw-support taper is therefore rejected and must be restored to the former
  remapped-support implementation. The next experiment will isolate only the
  TOWER vertical top fade (`topStart 0.96` control versus `0.90` candidate),
  leaving CORE, BASE, ANVIL, support, material and lighting unchanged.

## Iteration 118 - wider TOWER top fade rejected; mean profile alone is insufficient

- The rejected raw-support taper was restored first. A new isolated include
  changed only the TOWER vertical band's top fade from the former last `4%` of
  each interval (`topStart=0.96`, resource-pack control) to the last `10%`
  (`topStart=0.90`, production candidate). Its base fade remained `0.016` and
  CORE, BASE, ANVIL, supports, endpoints, material, erosion, density and lighting
  were unchanged. On the observed `216.0..598.0` severe slab this should have
  expanded representative TOWER fades from roughly `4..7` blocks to
  `10..17` blocks without extra samples or targets.
- Compile, resource processing, CloudField sandbox, tests and packaging passed.
  Build and deployed native-only runtime hashes matched at
  `749BEB2BE8CDEC113991A77CBFE1812E707FF99ED2AE00FE2DFED839BB97D4AE`.
  The instance contained only Architectury, Cool Rain, PA and Gabou's libraries;
  Simple Clouds, CrackersLib, Oculus and Iris remained absent. The selected
  control pack loaded at startup and every F3+T transition re-registered the PA
  shaders without an error or crash.
- The valid far bracket is
  `build/visual-test/severe-tower-band-exact-ab-aq/01..03`. It is an exact
  1280x720 control-candidate-control sequence separated by 44 and 46 seconds,
  with the same capillatus, pose, noon lighting, Ultra 0.75 scale and history
  disabled. Alignment is exactly zero; terrain and clear sky are unchanged.
  A later closer control (`04`) confirms how severe the remaining analytic
  morphology is, but the attempted close candidate bracket was not completed
  deterministically and is excluded from the causal result.
- Personal inspection finds no recognizable change in the TOWER silhouette.
  The same dark triangular central column, stacked spherical shoulders, bright
  thin anvil stroke, hard horizontal role shelves and regular rain curtain remain
  in all three valid frames. The candidate does not visibly turn their abrupt
  terminations into natural billows.
- Registered measurements agree. Within the central TOWER ROI, candidate
  residuals versus interpolated controls are only `+0.18%` for the upper area,
  `-0.17%` for the crown and `+0.10%` for the shoulder at the `0.20` opacity
  threshold. At `0.40`, upper/crown changes are `-0.28%/-1.28%`. The broad mean
  vertical crown gradient decreases about `13..21%`, but local gradients do not:
  their 95th percentile is `0.219` versus `0.187/0.233` controls, and the local
  maximum is higher than control 1. The improvement is therefore a small profile
  average, not a robust visible edge correction.
- Horizontal-edge fraction falls to `49.4%` from `54.6/52.8%`, but CORE also
  falls even though this include cannot change CORE. Concurrent precipitation
  redistribution is very large (left shaft `+230%`, central shaft `-28.6%`) and
  a candidate-only distant blob appears at `x=287..302,y=494..504`. These are
  strong evidence that material/precipitation time confounds the one candidate
  frame, so the apparent banding delta is not credited to the top fade.
- Candidate `0.90` is rejected. Production must return to the former `0.96`
  vertical band. The next experiment will keep TOWER support and total mass
  unchanged but make endpoint selection continuous when the best and runner-up
  TOWER supports are nearly tied and vertically overlap. This targets the
  confirmed winner-switch discontinuity rather than another optical-edge tweak.

## Iteration 119 - top-two TOWER endpoint blend rejected; terraces unchanged

- The candidate retained the strongest TOWER support as the output opacity but
  blended its vertical endpoints toward a runner-up only when both candidates
  overlapped and their supports were close. The implementation added no pass,
  sampler, target, allocation or raymarch sample; its only cost was a small
  amount of weather-map ALU on cache misses.
- Compilation, resource processing, CloudField sandbox, tests and packaging
  passed for build/runtime hash
  `DD732AC736987F8D10259DDF609308B476923BE2261CE763C3C8D5E2AE3ABA93`.
  The native-only runtime contained no Simple Clouds, CrackersLib, Oculus or
  Iris. Both candidate and resource-pack control shaders registered without a
  shader error.
- The only valid causal bracket is
  `build/visual-test/severe-tower-winner-blend-exact-ab-as/01-candidate-blended-winner-far-clientcenter.png`,
  `02-control-hard-winner-far-screen.png` and
  `03-candidate-blended-winner-far-screen.png`. The older `ar` directory and the
  `04/05` close captures are excluded because their pose or cloud lifetime was
  not comparable. Terrain and sun alignment is exactly zero pixels in the valid
  bracket.
- Personal inspection finds the same narrow severe column, stacked ellipsoidal
  lobes, broad horizontal terraces, hard dark base and thin analytic anvil in
  all three frames. The endpoint blend is not a recognizable visual correction.
- Registered measurements confirm that result. At the dense `alpha >= 0.40`
  mask, global areas are `24414 / 24459 / 24531` pixels for candidate-1,
  hard-winner control and candidate-2: the control residual versus interpolated
  candidates is only `-0.08%`. Crown, shoulder and core residuals are
  `+0.46% / -0.31% / +0.42%`. Strong horizontal-edge fractions are
  `52.63% / 52.88% / 52.96%`; the control residual is only `+0.06` percentage
  point and changes direction at a stronger threshold. No run-length or contour
  metric shows a robust reduction of the terraces.
- Large differences below the base are dominated by precipitation motion:
  control faint-mask base area is `+36.4%` and dense rain area is `+202.6%`
  versus the temporal interpolation, while rain moves substantially between the
  two candidate controls. Those values are not assigned causally to endpoint
  selection.
- The top-two blend is therefore rejected as a demonstrated visual improvement
  with confidence `0.85`. It must not remain in production merely because it is
  mathematically continuous. The next source correction must target the actual
  macro geometry/density construction.
- Production was restored to the original single strongest TOWER endpoint and
  the now-unreferenced winner-blend include was removed. With JDK 17 and Gradle
  8.10, `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and
  `build` all passed; the only output was the pre-existing deprecation/mixin
  warning set.

## Iteration 120 - expanded visual baseline from the native severe captures

- Review of the current in-game native-only captures establishes stricter
  blocking criteria. The visible cloud has an implausibly tall and narrow
  central spike, flat horizontal/diagonal fins, stacked stretched volumes and a
  nearly black lower slab with a hard boundary. Other views can collapse toward
  a diffuse horizontal smear rather than preserving one recognizable cumulus
  morphology.
- The captures also show screen-coherent square clusters, stippling and broken
  fragments along high-contrast boundaries. These are now treated separately
  from the already accepted exterior jitter correction: raw low-resolution,
  spatial-upscale, temporal-history and final-composite stages must be isolated
  before assigning a root cause.
- The observed runtime motion is reported as wave-like internal density and
  rapidly changing edges while the macro cloud remains approximately fixed.
  This is a user-observed temporal defect, not something proven by one still
  frame. Weather-map fingerprints, cloudlet identities, world-space noise
  domains, history acceptance and snapped origins must be instrumented before
  claiming a cause.
- Rain is visibly present beyond the displayed dense cloud footprint and the
  streak presentation itself may be too sparse, long and wind-slanted. Gameplay
  precipitation coverage and visual rain rendering are separate investigations;
  neither will be considered fixed solely by enlarging the cloud.
- Acceptance now requires the same native cloud viewed from below, side, above,
  close and medium range, plus stationary and lateral-motion sequences and rain
  start/end. A successful result must remove the needle, fins, black slab,
  obvious reconstruction blocks and rapid silhouette reshaping while retaining
  stable lobe structure and spatially aligned precipitation.

## Iteration 121 - stage isolation proves the malformed source volume

- The baseline defects were traced before another visual correction. A native
  region currently aggregates the positions, radius and vertical bounds of all
  of its simulated clusters into one `CloudField`, keeps the family of only the
  primary cluster, and then generates a second deterministic `CloudletLayout`
  inside that aggregate envelope. For a TOWER-family field the generated core
  spans `90%` of the already amplified regional height at only `28%` of the
  regional radius, while the generated BASE spans `24%` of the height at `46%`
  of the radius. This is a direct source for the observed needle and shelf; it
  must be corrected in the source geometry rather than blurred in composition.
- The LOD path also changes topology at `acceptedDetailCount >= 3`: below that
  threshold the aggregate macro ellipse remains visible, whereas at or above it
  the macro becomes carrier-only and the regenerated cloudlets define the
  silhouette. This explains a deterministic tower-to-smear/popping risk.
- Temporary runtime views now isolate actual nearest low-resolution colour,
  alpha and cloud depth; spatial-only upscale; selected 2x2 neighbour; scene
  rejection; current raymarch; frozen valid history; and temporal rejection.
  The temporal views never enter the production ping-pong history. Entering a
  diagnostic preserves the last valid production history, diagnostic frames do
  not swap targets or advance the production reprojection matrices, and
  returning to final invalidates the stale interval. Current-miss pixels with
  surviving screen-space history are explicitly visible in the rejection mask.
- The scene-rejection view is evaluated before the normal no-neighbour discard,
  so it can expose a full four-neighbour rejection rather than only partial
  cases. Raw colour/alpha/depth views use `texelFetch`, not the former linear
  sampler that could conceal the low-resolution grid.
- With JDK 17 and Gradle 8.10, `compileJava`, `processResources`,
  `cloudFieldSandbox`, `test` and `build` all passed. The only output was the
  pre-existing deprecation/mixin warning set. The exact built and deployed JAR
  SHA-256 was
  `F21AC58727A827D9E115506C34263FB92FB3F9B4E31250CE0793A7B310CA4DDA`.
- The JAR was run in a production Forge 47.4.20 instance containing only PA,
  Architectury, Gaboulibs and Cool Rain. Simple Clouds, CrackersLib, Oculus and
  Iris were absent. The log selected the native PA service, registered every PA
  shader, and reported a valid detached `1280x720` vanilla-main scene depth.
- A frozen `cumulus_congestus` TOWER field was captured from one fixed pose in
  `build/visual-test/iteration-121-stage-isolation/`. Its generated field used a
  roughly `70.2` block radius, renderer slab `236.0..468.7`, 26 accepted
  cloudlets and 27 weather-map cells. Personal inspection of final, raw colour,
  raw alpha and raw cloud-depth captures shows the same four disconnected
  analytic structures: a narrow bright cap, a broad dark shelf, a second thin
  shelf and two long tapered vertical legs. Large clear gaps separate them. The
  result resembles stacked props rather than a cauliflower cumulus.
- The malformed topology is already present in raw low-resolution colour,
  alpha and representative cloud depth (`05..07`). It remains in spatial-only
  current output (`08`) and in full-resolution output with temporal history
  disabled (`13..15`). Consequently neither temporal reprojection nor the final
  depth-guided composite creates the needle, shelves or disconnected legs.
  This is direct runtime confirmation of a source-layout/density failure.
- Raw alpha is nearly opaque throughout each detached analytic piece and drops
  sharply across the gaps. Raw depth contains the same disjoint intervals.
  Therefore the black shelf is not merely straight-versus-premultiplied-alpha
  confusion, and the gaps are not solely lighting. The geometry supplied to the
  raymarch has disjoint vertical roles before composition.
- The selected-neighbour view (`11`) exposes a regular RGB subpixel/checker
  pattern across every cloud edge, while history rejection (`10`) changes state
  sharply over significant portions of the lower legs. These diagnostics
  confirm that the 2x2 winner and binary history acceptance can amplify screen
  grain, but their effect is secondary to the already malformed source volume.
  Scene depth rejects none of the sky-only cloud body (`12`), as expected.
- The exact full-resolution, history-off, frozen-pose images at `t=0` and
  `t=30s` (`14` and `15`) preserve the same silhouette and lobe locations. The
  deterministic cloudlet layout does not regenerate during this controlled
  freeze. Reported wave-like motion in normal play must therefore be isolated
  from live advection/material time and temporal reconstruction; it is not
  attributed to layout regeneration by this test.
- Lighting is independently unacceptable in the diagnostic baseline: the cap
  clips close to white while the two legs and shelf approach black, with long
  internal streaks. Full resolution and history-off do not restore tonal detail.
- The world was exited through Minecraft's **Save and Quit to Title** path and
  the client was closed before source modification. The next correction will
  replace the aggregate-region/second-layout geometry with a canonical
  cluster-derived render primitive before changing reconstruction, erosion or
  lighting.

## Iteration 122 - direct cluster projection exposes destructive simulation merge and banded density

- The first canonical-cluster candidate was built and deployed as exact SHA-256
  `81CB6DE8A334257F991F1498BCEC721318F6C49841E04CDF8FF7EB0EB64C94E2`.
  `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and `build`
  passed before deployment. The production Forge 47.4.20 instance contained
  only PA, Architectury, Gaboulibs and Cool Rain; Simple Clouds, CrackersLib,
  Oculus, Iris and Distant Horizons were absent.
- The renderer no longer generated a second `CloudletLayout`: the live status
  reported `requested=0`, `accepted=0`, `remaining=64`. Immediately after
  spawning `cumulus_congestus`, five PA cluster fields and five render cells
  were visible. Within a few ticks the server retained one cluster, the
  collector reported `sampledPaClusters=1`, and four field IDs were removed as
  `MISSING_SOURCE_GRACE_EXPIRED`. This is direct runtime confirmation that
  `CloudRegionMergeController.mergeWithinRegion()` is destroying the generated
  multi-lobe TOWER morphology before rendering.
- The retained cluster at the first list sample had centre roughly
  `(6.3, 285.8, -8.3)`, radius `29.2`, and was nevertheless rendered in the
  global `236.0..476.4` slab. The direct render-cell projection uses the whole
  merged base/top interval and does not use the cluster centre Y to preserve its
  original vertical tier. The resulting aspect is still an extremely tall,
  narrow primitive.
- Personal in-game inspection from below, close side, medium side and above is
  recorded in the runtime screenshots at `17.59.23`, `17.59.27`, `17.59.30`
  and `17.59.34`. The direct-below view is an almost uniformly dark bilobed
  footprint. The side views are a rocket-like column with a pointed white cap,
  crushed dark lower section and two complete horizontal gaps. The top view is
  only a small soft bilobed patch. It is not a recognizable cumulus from any
  tested angle.
- The same medium-side silhouette and both gaps are present in raymarch debug
  view `current` (`18.00.38`) at full resolution with temporal history disabled.
  Therefore the vertical splits, needle and tonal crushing are generated by
  current-frame geometry/density/light evaluation before temporal reprojection
  and before low-resolution composition. Reconstruction work must remain
  secondary until this source defect is removed.
- The canonical-cluster change did remove the old 27-cell second-layout path,
  but it is not accepted as a visual correction by itself. The next correction
  must preserve the generated lobe set as one cloud entity instead of merging
  sibling lobes, retain each lobe's own centre and vertical extent, and prevent
  a regular TOWER cloud from being evaluated through disjoint role bands.
- The client was closed through `WM_CLOSE`; Minecraft disconnected the
  integrated client, shut down PA worker pools, saved all three dimensions and
  logged `Stopping!` before the Java process exited. No source correction was
  started while the client was running.

## Iteration 123 - coverage pretest is the confirmed source of the horizontal gaps

- The exact Iteration 122 JAR was relaunched without a source change. A fresh
  `cumulus_congestus` again collapsed from its initial sibling set to one TOWER
  cluster. The camera, noon lighting, full-resolution current-frame view and
  disabled temporal history matched the preceding side test.
- With the six-sample coverage pretest disabled at runtime, the two complete
  horizontal gaps disappeared. The captured current raymarch at `18.06.01`
  contains one continuous volume from base to crown. It remains a very tall,
  narrow, pointed column, proving independently that the simulation/morphology
  defect remains while the sliced gaps do not originate in `familyMacroShape()`
  or temporal reconstruction.
- Re-enabling the pretest with its maximum exposed count of 16 samples did not
  make it conservative. The `18.06.44` capture still has several thin complete
  horizontal cuts through the same column. The uniform probes cover the entire
  slab/render-distance ray interval; a narrow cloud footprint can lie between
  probes, especially for near-horizontal views.
- The measured full-resolution GPU query for this deliberately pathological
  side view was approximately `0.55..0.61 ms` with 16-sample pretest and
  `1.15..1.57 ms` with it disabled. This is not a final performance comparison,
  because the current single needle has an unrepresentative footprint and the
  diagnostic uses full resolution, but it confirms that simply disabling the
  optimization has a real cost.
- Correctness takes priority: the current pretest cannot remain enabled by
  default while it has false negatives. It will be disabled in the production
  default until a conservative occupancy structure or bounded-ray test can
  prove that clear-ray rejection never removes cloud pixels. Increasing the
  existing uniform sample count is rejected as a fix.
- The client was again closed through `WM_CLOSE`; the integrated server saved
  all dimensions and terminated PA workers before process exit. Source work may
  now resume.

## Iteration 124 - persistent local lobes remove the needle, but the result remains cuboid

- The candidate preserves one stable morphology-group identifier plus lobe
  index/count in every spawned cluster, skips destructive absorption between
  siblings, removes the secondary-radius double scale, assigns PUFF/TOWER
  bounds locally around each lobe centre, and raises TOWER centres through the
  column. The fixed 42-block warp was removed from the analytic coverage,
  morphology and severe-support footprints. Detail erosion is now bounded and
  multiplicative for non-cirrus material. The known false-negative coverage
  pretest defaults off.
- `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and `build`
  passed with the existing warning set. The exact built and deployed native-only
  JAR SHA-256 was
  `99EC31E64367D45D5B8A20D0FB0A64C911DA7929A559787B175C84E4DDB56DA8`.
- The Forge 47.4.20 client loaded only PA, Architectury, Gaboulibs and Cool Rain;
  Simple Clouds and the other optional visual backends were absent. All PA
  shaders registered and the native service was selected. A freshly cleared and
  spawned `cumulus_congestus` retained all four generated clusters for the full
  observation instead of collapsing. The direct renderer stayed at four cells
  and zero derived cloudlets. Its global slab was approximately `236.0..368.1`,
  versus the approximately 222-block malformed merged interval in Iteration 122.
- Personal inspection is recorded in `build/visual-test/iter124/`: medium side,
  close side, raw current colour, raw alpha, below, above and a repeated close
  side pose after well over 30 seconds. The vertical needle and complete
  horizontal density cuts are gone. The silhouette and lobe placement remain
  stable at the repeated frozen pose, so sibling identity is now persistent.
- This candidate is still rejected visually. From the side it is a nearly
  rectangular dark body with a few pointed white peaks; it is not a cauliflower
  cumulus. From above, two symmetric triangular fins remain. Their direct code
  correlate is the strong per-primitive angular harmonic deformation in the
  weather maps; it is now redundant because the authoritative cluster set
  already supplies separate lobes. From below, two broad masses connect through
  a narrow waist instead of forming one compact base.
- The close-side stippled horizontal transition is present in raw current colour
  but not in raw alpha. It is therefore a radiance/sampling defect, not a missing
  density band and not temporal reconstruction. Alpha is nearly saturated over
  most of the cuboid, while noon colour clips the upper half close to white and
  pushes the lower half toward dark gray. Full-resolution history-off GPU query
  samples at the final fixed pose were about `1.10..1.47 ms`; these are diagnostic
  samples, not the final quality-profile comparison.
- The client was closed through `WM_CLOSE`; Minecraft logged `Stopping!`, shut
  down all PA pools and saved every dimension before the Java process exited.
  The next source correction may now reduce the redundant angular fins, replace
  the cuboid TOWER height response with overlapping local domes, and isolate the
  horizon-linked radiance discontinuity without changing reconstruction first.

## Iteration 125 - fin suppression exposes destructive height averaging

- This shape-only candidate increased the TOWER layout to six-to-eight balanced
  golden-angle siblings, spread the lower shoulders while converging the upper
  tiers, reduced the now-redundant per-primitive cumulus angular harmonic, and
  replaced the cuboid TOWER vertical threshold with a gradual full-height crown
  taper. No light, reconstruction, history or precipitation parameter changed.
- `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and `build`
  passed with the existing warning set. The exact built and deployed native-only
  JAR SHA-256 was
  `8A1DE5C60AD84DF68C23D0BD9B8082D3FD4A1515F1EBC224315657D0B2B484B0`.
- The Forge 47.4.20 client again contained only PA, Architectury, Gaboulibs and
  Cool Rain. A fresh frozen `cumulus_congestus` retained six stable sibling
  clusters for the whole observation. The direct renderer reported six fields,
  six weather cells, zero derived cloudlets and a slab of approximately
  `236.0..366.5`.
- Personal inspection is recorded in `build/visual-test/iter125/`: close side,
  close above, below, and the repeated close-side pose after more than four
  minutes. The triangular fins and sharp wings from Iteration 124 are gone, and
  no vertical needle or complete density cut returned. The frozen outline did
  not regenerate during the observation.
- This candidate is still rejected visually. The side silhouette is one smooth
  helmet or dome rather than several cauliflower lobes, the top view is an
  almost circular white disc, and the bottom view is an overly uniform round
  footprint. The weather-height splat currently divides the sum of local base
  and top heights by total coverage weight. Overlapping lower and upper
  siblings are consequently collapsed to their weighted mean instead of
  preserving supported local maxima/minima; the runtime result matches that
  destructive aggregation.
- The former hard black rectangular shelf is no longer present, but the lower
  half remains too dark and the edge remains visibly stippled. The same
  horizon-linked bright/dark transition is still present even though only
  shape parameters changed. This further isolates it from the removed angular
  fins and from temporal history; lighting/sampling remains a separate defect.
- Full-resolution, history-disabled GPU query samples at the observed side pose
  were approximately `1.0..1.5 ms`. These diagnostic values are comparable to
  Iteration 124 but are not a complete quality-profile benchmark.
- The client was closed through `WM_CLOSE`; Minecraft logged `Stopping!`, shut
  down the weather, storm and client pools, and saved all dimensions before the
  Java process exited. The next source correction will replace destructive
  weighted height averaging with a support-guarded smooth union of sibling
  envelopes before any lighting correction is attempted.

## Iteration 126 - smooth interval union restores shoulders but remains monolithic

- This candidate replaced the destructive cumulus base/top mean with a
  coverage-cubed, log-sum-exp smooth minimum/maximum blended back into the mean.
  It also widened deterministic TOWER offsets from `0.28R..0.10R` to
  `0.58R..0.18R`, made canonical MACRO tops return farther toward the local
  centre, and mirrored the GPU footprint, harmonic, role taper and cumulus
  envelope in `ClientCloudVisualDensity`. The CPU-only 42-block footprint warp
  was removed so whiteout/fog no longer sample a displaced obsolete shape.
- `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and `build`
  all passed. The exact built and deployed native-only JAR SHA-256 was
  `84CE06F2DED45CECBDCE4C95C5C70EAFB84D8D6DD3AAE5B3B4717C090024D3FE`.
  The client loaded PA, Architectury, Gaboulibs and Cool Rain only and selected
  the native PA service; all cloud shaders loaded without an error.
- A fresh frozen `cumulus_congestus` retained seven canonical sibling fields
  and seven weather cells with zero derived cloudlets. The observed slab was
  approximately `236.0..363.8`. Field evolution diagnostics showed distinct
  stable centres, radii and local intervals rather than a server-side merge;
  examples included lower lobes around `base=242, top=314..324` and higher
  lobes around `base=269..300, top=333..351`.
- Personal inspection is recorded in `build/visual-test/iter126/`: side after
  more than 30 seconds, above, below and raw alpha. No vertical needle, complete
  horizontal density cut or old flat triangular wing returned. The side now
  exposes several broad shoulders and the top is no longer a perfect disc.
- This candidate is still rejected. The side resembles a ridged rock with one
  sloping crown rather than rounded cauliflower lobes; the top resolves mainly
  two heart-shaped lobes; the underside is a cluster of very dark, oversized
  masses. Raw alpha is almost fully saturated inside one continuous polygonal
  volume and contains only minor crown relief. Thus the interval union restores
  some real cluster geometry but the single RGBA weather envelope plus saturated
  union coverage and the subsequent coverage-based TOWER taper still destroy
  too much lobe identity.
- The horizon-linked stippled radiance transition and vertical light streaks
  remain. A separate shader audit tied this to the full-ray-length step size:
  near-horizontal rays use approximately 10-block fine steps and a full-step
  animated origin jitter, while the coarse hit rewinds only 60% of a step. The
  raw-alpha core stays saturated across the transition, confirming again that
  this is a current-frame light/sampling defect rather than reconstruction.
- Full-resolution, history-disabled raymarch queries were roughly `1.0..1.6 ms`
  above/side and `1.6..2.0 ms` below for this seven-field diagnostic. The added
  exponential weather-envelope reduction did not create an obvious raymarch
  regression, but its weather-map ALU cost and the remaining shape do not
  justify retaining it without another comparison.
- The client was closed through `WM_CLOSE`; Minecraft logged `Stopping!`, shut
  down all PA worker pools and saved all dimensions. The next iteration will
  use a support-guarded bounded union and reduce the second coverage taper so
  the local top envelope, rather than saturated union coverage, owns the crown.
  The ray-entry bracket/step defect will then be corrected and measured as a
  separate sampling change.

## Iteration 127 - bounded union and tiered layout regress to a solid dome

- This candidate removed the exponential height reduction in favour of a
  support-guarded bounded min/max, generated eight-to-ten deterministic TOWER
  clusters as three low shoulders plus paired crown tiers, and lowered the
  secondary crown coverage threshold from `0.045..0.34` to `0.025..0.10` so
  local top heights were intended to own the silhouette. The CPU visual-density
  query received the identical bounded envelope.
- `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and `build`
  passed with only the existing warnings. The exact built/deployed JAR SHA-256
  was `49881A1CA5CE983BBE22FABB2B4A709B3AB6925C26C40E2EC975D650AEBE8527`.
  The native-only client loaded all shaders and selected native PA without a
  Simple Clouds installation.
- A newly cleared and spawned field retained ten canonical sibling clusters,
  ten weather cells and no derived cloudlets. The slab was approximately
  `236.0..358.8`; the fixed side pose used full resolution, history off and the
  current-frame raymarch. Captures are under `build/visual-test/iter127/`.
- Personal inspection rejects this candidate. From the side, the crown is one
  smooth dome over two dark bottom notches. From above, it is an almost perfect
  pale disc. From below, it is a broad dark block with long nearly vertical
  walls and only shallow edge scallops. Raw alpha is saturated throughout the
  same monolithic dome and also exposes a small detached sliver at the right
  edge. The relaxed crown threshold therefore allowed unioned coverage to fill
  the lobe group instead of exposing cauliflower detail.
- Runtime field data also found that random secondary scale and radius jitter
  can still invert the intended tier hierarchy. In this field, visible samples
  included radii `39.9`, `45.1`, `28.5`, `50.7`, `45.4` across increasing
  tiers. This comes from the `0.72..1.14` spawn scale compounded with the
  existing `0.92..1.08` jitter and is large enough for a higher lobe to engulf
  a lower neighbour despite deterministic tier positions.
- The raymarch query remained about `0.95..1.52 ms` at the tested full-resolution
  exterior poses; no useful performance conclusion offsets the visual
  regression. Lighting remained unchanged and the familiar stippled
  horizon transition is still visible.
- The client was closed through `WM_CLOSE`; Minecraft saved all dimensions and
  terminated every PA pool. The relaxed secondary coverage threshold will not
  be retained. The next candidate will restore the prior crown gate, compress
  TOWER-only scale/jitter so radii decrease predictably, and increase the
  separation of supported crown tops without allowing a thin top primitive.

## Iteration 128 - stable nine-lobe hierarchy removes the old needle but forms a pointed arch

- This candidate fixed the TOWER topology at nine canonical lobes (one core,
  three shoulders, two lower-crown lobes, two upper-crown lobes and one cap),
  compressed TOWER-only spawn scale and radius jitter to approximately four
  percent, used the same deterministic tiers during retargeting, restored the
  stricter crown support gate, and assigned a shared condensation base to the
  three shoulders. No lighting, temporal reconstruction or precipitation code
  changed in this iteration.
- After a forced recompilation repaired the partial class output left by an
  interrupted Gradle process, `compileJava`, `processResources`,
  `cloudFieldSandbox`, `test` and `build` all passed. The sandbox self-check
  passed and the exact built/deployed native-only JAR SHA-256 was
  `A495B10803EF523828F5BA48DA1D2437780ED3A5FB7BBC10FD7FD9C8BDD2D6FE`.
  The Forge 47.4.20 instance again contained only PA, Architectury, Gaboulibs
  and Cool Rain; its log explicitly selected the native PA service and all
  relevant shaders loaded.
- A freshly cleared, frozen `cumulus_congestus` retained exactly nine canonical
  fields and nine weather cells, with zero derived cloudlets. The field centre
  was approximately `(-1.3, 283.9, -217.6)`, its reported group radius was
  `69.6`, and the render slab was approximately `236.0..365.1`. The radius
  hierarchy was materially better controlled: the core was about `55.3`, the
  lower shoulders about `39.3..42.9`, and the displayed upper sample about
  `36.0`, rather than the large random inversions in Iteration 127.
- Personal inspection is recorded in `build/visual-test/iter128/`: below,
  medium side, above, close side after more than 30 seconds, and raw composite
  alpha. The old thin vertical needle, triangular horizontal fins and black
  rectangular bottom shelf did not return. From below, the footprint now
  contains several connected rounded masses instead of a cuboid.
- This candidate is nevertheless rejected. The stable side view is a pointed
  teardrop or heart with one triangular crown and a large dark arch cut out of
  its underside, not a cauliflower cumulus. The top remains one small pale
  blob. Raw alpha contains the same pointed, almost fully opaque silhouette and
  underside arch, proving that this macro defect already exists in the current
  volumetric density and is not introduced by final compositing.
- Code review found two remaining envelope defects consistent with that image.
  Retargeting shares `groupBaseY` with shoulder indices 1..3 but not with the
  primary index 0, so the core base can be raised after spawning. In addition,
  weak extrema candidates collapse toward the opposite endpoint of their own
  high local interval; a weak upper fringe can therefore still raise the global
  top when another lobe supplies the global union blend. The GPU weather splat
  and CPU visual-density query mirror this flaw exactly.
- The familiar horizontal stippled light band crosses the side silhouette in
  both final colour and alpha-edge diagnostics. Full-resolution, history-off
  exterior queries were approximately `1.6..2.3 ms` around the initial below
  pose; these remain diagnostic samples rather than a quality-profile
  benchmark. The separately verified full-ray-derived fine step is still the
  direct cause of the view-angle-dependent band and must be corrected before
  judging lighting contrast.
- The next correction will give the primary and shoulders the same retargeted
  base, use neutral bounded extrema candidates in both GPU and CPU paths, and
  replace the horizon-dependent exterior fine step and arbitrary hit rewind
  with a world-space fine step plus a bracketed entry. The client must be
  closed cleanly and this entry committed before those source edits begin.

## Iteration 129 - stable ray entry removes the screen-space band but exposes a monolithic cone

- This candidate gave the primary TOWER lobe and the three shoulders the same
  retargeted condensation base, made weak envelope extrema collapse to neutral
  candidates in both the weather-map shader and `ClientCloudVisualDensity`,
  removed the second height-dependent TOWER coverage cone, and replaced the
  view-length-derived exterior step with a bounded world-space step. The coarse
  traversal now records a real last-clear/first-hit bracket and bisects it four
  times. With temporal history disabled, the diagnostic jitter is fixed rather
  than advancing every frame. No lighting or precipitation parameter changed.
- `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and `build`
  passed. The sandbox self-check passed and the exact built/deployed native-only
  JAR SHA-256 was
  `F19546D766BD6C513E095508D1BDC49C6A3ED32BAB4833C09BE16414C0F9B0C0`.
  Forge loaded PA, Architectury, Gaboulibs and Cool Rain without Simple Clouds;
  the native PA service and all volumetric shaders initialized successfully.
- The same frozen `cumulus_congestus` from Iteration 128 initially retained nine
  canonical fields and nine weather cells, zero derived cloudlets, and the same
  approximately `236.0..365.1` slab. Personal inspection is recorded under
  `build/visual-test/iter129/`. The clean stable side frame no longer contains
  the former horizon-linked stippled band or coarse square grid. Raw alpha also
  shows that the deep bottom arch became much shallower. Full-resolution,
  history-disabled exterior GPU query samples were mostly about `1.24..1.71 ms`
  at the initial side pose, with later mostly empty views lower; this is a
  diagnostic comparison, not a completed quality-profile benchmark.
- This candidate remains rejected. The stable side silhouette is a clean but
  fundamentally wrong teardrop/cone, with nearly saturated raw alpha and no
  distinct cauliflower crown. The ray-entry correction therefore fixes the
  screen-space band without fixing macro morphology. Code and runtime-data
  comparison confirm why: all nine canonical cluster snapshots become
  `EnvelopeRole.MACRO`; morphology group/index/tier identity is discarded by
  the `CloudField` snapshot path, then nine local volumes are reduced to one
  RGBA coverage/base/top/energy envelope. A single `h01` profile cannot recover
  the original vertical lobes afterward.
- The attempted high view is not accepted as visual evidence. Teleporting to
  `Y=480` removed the fields from the client render set; moving back to `Y=400`
  and `Y=360` did not repopulate them, and `/pa cloud list` reported no saved
  cloud regions. The small white square in `above.png` is therefore not treated
  as a cloud capture. This also prevents a valid same-field underside capture
  for this iteration and will be kept separate from the morphology verdict.
- Minecraft was closed with `WM_CLOSE`; it logged `Stopping!`, terminated all
  three PA worker pools, saved overworld, Nether and End chunks, and the Java
  process exited normally. The next source correction may now preserve explicit
  morphology-stage membership through the canonical field/snapshot/network
  path and encode separate base/core/tower/crown supports instead of trying to
  tune the already-collapsed envelope.

## Iteration 130 - stage identity survives, but the first four-stage union becomes a tiered stack

- This candidate preserved a canonical morphology-group UUID, member index and
  member count from `CloudClusterState` through field snapshots and network
  packets, bumped the PA protocol to version 11, assigned a fixed twelve-lobe
  TOWER layout (four BASE, three CORE, three TOWER and two CROWN lobes), and
  encoded those stages in three dedicated RGBA16F support/base/top weather
  maps. The volume shader reconstructed the four stage intervals rather than
  using the old single collapsed `h01` envelope. A runtime role summary was
  added solely to prove that the topology arriving at the renderer is stable.
- `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and `build`
  passed. The sandbox self-check passed and the exact built/deployed native-only
  JAR SHA-256 was
  `C25BBBA297D5004EF196A2F08A1437E91580EB43925821C3C7B49B5FDD9EFDBC`.
  The Forge 47.4.20 instance contained only PA, Architectury, Gaboulibs and Cool
  Rain. Forge selected the native PA service, registered the new shaders, found
  32 fragment texture units against 14 required, and reported no shader compile
  or link failure.
- A fresh frozen noon `cumulus_congestus` retained exactly twelve canonical
  fields and twelve render cells. Runtime repeatedly reported
  `roles[base=4,core=3,tower=3,crown=2,other=0]`, zero derived cloudlets and the
  same `234.8..386.2` render slab. The field centre was approximately
  `(-0.8, 294.1, -218.2)` with a 68.7-block group radius. Thus the former loss
  of morphology-stage identity is confirmed fixed in the running client.
- Personal visual inspection is recorded in `build/visual-test/iter130/`.
  `side-initial.png` shows that the old single cone/apex was replaced, but the
  new silhouette is an obvious vertical stack of horizontal discs. The closer
  `side-close-final.png` exposes separated base, core, tower and crown shelves,
  very dark/grainy lower cutouts and bright banded caps. Raw
  `side-close-alpha.png` contains the same horizontal separations and saturated
  tier shapes, proving that this regression exists in the reconstructed density
  field before final colour composition.
- This candidate is rejected. The separate stage maps correctly reveal the
  source lobes, but their independently curved vertical intervals do not retain
  enough overlap at the sampled horizontal footprint. The probabilistic union
  cannot bridge genuinely empty gaps, so the stage boundaries become visible
  rings. The visual result is still not a believable cauliflower cumulus and
  the lower mass still has coarse dark voids. This must be corrected in the
  source envelopes and stage reconstruction, not hidden with blur.
- Full-resolution, history-disabled exterior GPU queries were about
  `2.1..3.5 ms` at the tested medium/close side poses, with an initial transient
  near `5.1 ms`. The 4/3/3/2 role counts remained unchanged for more than three
  minutes; however movement freeze does not freeze lifecycle/coverage, so this
  is topology-stability evidence rather than a pixel-identical temporal A/B.
- Minecraft was closed through `WM_CLOSE`; it logged `Stopping!`, terminated
  all PA pools, saved overworld, Nether and End chunks, and the Java process
  exited. The next correction may now overlap the canonical source envelopes,
  remove the four visible material shelves and restore the OpenGL texture-unit
  guard through units 12 and 13 before another runtime test.

## Iteration 131 - rejected before visual capture: Minecraft texture-state cache overflow

- This candidate widened and vertically overlapped the upper lobes of the
  twelve-member template and attempted to extend `CloudRenderStateGuard` from
  texture unit 11 through PA's manually bound noise units 12 and 13. The full
  Gradle validation passed and the exact built/deployed JAR SHA-256 was
  `6E43AAF593FCA40DB9C5741755E1A4F0A0FD0393AC89595215C2B5E9FA0ED5E1`.
- The runtime test is rejected before morphology evaluation. Minecraft 1.20.1
  loaded the shaders and entered the native-only world, but the first state
  restore threw `ArrayIndexOutOfBoundsException: Index 12 out of bounds for
  length 12` in `GlStateManager._bindTexture`, called from
  `CloudRenderStateGuard.State.close`. Minecraft's tracked texture cache has
  slots only for units 0..11; using its cached bind helper on raw PA units
  12/13 also left subsequent vignette and GUI texture binds in the invalid
  active-unit state.
- No screenshot from this launch is accepted and the overlapped morphology has
  not yet been visually judged. The crash report is direct proof that units
  12/13 must be captured/restored with raw `glActiveTexture/glBindTexture`,
  while only 0..11 may pass through Minecraft's `GlStateManager` cache. The
  process exited after Forge stopped the integrated server and saved all
  dimensions. This state-path correction is required before relaunching the
  same visual candidate.

## Iteration 132 - real lobe overlap removes detached shelves but exposes a stable symmetric cone

- This relaunch retained the widened/overlapped twelve-lobe source envelope
  from the failed Iteration 131, but restored units 0..11 through Minecraft's
  tracked `GlStateManager` cache and units 12/13 through raw OpenGL only. The
  full Gradle validation passed and the exact built/deployed JAR SHA-256 was
  `C4143EEB8B02743DD4F9F2805A4E7947D35C6794FF8FAC780871C76ED6C15ED8`.
- Forge entered the same native-only world without a render exception, loaded
  all PA shaders and repeatedly rendered twelve fields with the exact
  `base=4,core=3,tower=3,crown=2` role distribution. A fresh frozen
  `cumulus_congestus` centred near `(-0.8, 285.2, -350.8)` used a
  `234.8..374.0` slab. Its sampled source intervals now genuinely overlap: for
  example a lower lobe reached about `242.6..301.4`, a middle lobe
  `274.7..344.8`, and upper lobes about `292.4..360.8` and `299.9..362.0`.
- Personal captures are in `build/visual-test/iter131/` (the directory name
  predates the failed-launch split). Compared with Iteration 130,
  `side-close-alpha.png` is now one connected mass rather than four detached
  horizontal bands. This confirms that correcting the physical interval
  overlap fixed the shelf separation without blur. The former long needle and
  flat diagonal fins also remain absent.
- This candidate is still rejected. `side-close-final.png` and
  `side-orthogonal-final.png` show a highly symmetric conical or fir-tree
  silhouette. From one axis the two crown lobes form two sharp peaks; from the
  orthogonal axis they align into one pointed apex. The nearly linear curved
  top profiles and monotonically shrinking, centrally converging stage
  footprints are therefore still exposing the generator hierarchy instead of
  producing irregular cauliflower lobes. The underside also retains coarse
  dark fragments and the interior lighting remains horizontally banded.
- `stability-t0.png` and `stability-t30.png` were captured from the exact same
  full-resolution, history-disabled pose after thirty seconds without camera
  movement. Apart from the first frame's chat overlay, the visible silhouette
  and internal bands remain practically unchanged. Runtime role counts also
  stayed 4/3/3/2. This rejects rapid topology/noise regeneration as the cause
  of the current cone in the frozen test; the defect is the stable morphology
  itself.
- Full-resolution exterior GPU samples were commonly about `2.6..3.6 ms` at
  the close/orthogonal poses. Minecraft was closed through `WM_CLOSE`, all PA
  pools terminated, every dimension was saved and the process exited. The next
  source correction may now use three non-collinear crown lobes, a less
  symmetric horizontal layout and parabolic rather than nearly linear dome
  endpoints, while preserving the proven interval overlap.

## Iteration 133 - fence-gated stage-map evidence isolates stable winner seams

- No morphology, density, lighting or precipitation parameter changed in this
  iteration. It added an explicit
  `/pa system volumetric diagnostics cumulus` capture which transfers the
  RGBA16F cumulus support/base/top maps through one pixel-pack buffer, polls a
  GPU fence without waiting, copies the buffer only after it is signalled, and
  performs the numerical analysis off the render thread. The capture restores
  the active texture, texture binding, pixel-pack buffer and pixel-pack state;
  the PBO/fence are also connected to client resource shutdown. Ordinary
  diagnostics no longer perform an implicit synchronous texture readback.
- `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and `build`
  passed. The sandbox self-check passed and the exact built/deployed native-only
  JAR SHA-256 was
  `7E076E5DAC9B9A7E1AC8D2F0CE9D079E8F392BB033BEF96EB5EEB2F39AE87C8D`.
  Forge 47.4.20 loaded only PA, Architectury, Gaboulibs and Cool Rain, selected
  the native PA service, registered all volumetric shaders and rendered the
  persisted Iteration 132 cloud with twelve fields and the unchanged
  `base=4,core=3,tower=3,crown=2` roles.
- The first real GPU capture at game time 137841 used the exact weather-map
  cache signature `1ac96ed6a00c6dfd`, a `512x512` map over 4096 blocks and the
  same `234.844..373.988` slab. Its stage maps quantify the discontinuities
  previously inferred from source data: BASE had 148/302 neighbour pairs over
  a four-block endpoint jump (49.0%, maximum top jump 16.33 blocks); CORE had
  149/207 (72.0%, maximum base/top 7.31/19.42); TOWER had 108/142 (76.1%,
  13.16/22.22); and CROWN had 69/88 (78.4%, 13.02/19.53). The TOWER-to-CROWN
  overlap also contained four positive gaps, averaging 6.08 and reaching 8.64
  blocks. These values prove that abrupt stage endpoints exist in the actual
  rasterized textures before raymarch lighting or final reconstruction.
- Four captures covering 864 game ticks (43.2 seconds) retained the identical
  input signature and identical full-map hashes:
  support `81d9b859fc8d8325`, base `fe6b227900c08325`, top
  `3d472ea10a2c4325`. Weather-map misses stayed at 119 while cache hits advanced.
  This rules out weather-map regeneration, changing membership and unstable
  splat data as the cause of the frozen cloud's bands or silhouette changes.
- An independent calculation against the exact persisted NBT and Iteration 132
  JAR also measured the source topology rather than judging screenshots. Stage
  reach decreases almost linearly with height (BASE/CORE/TOWER/CROWN
  `69.42/55.33/44.36/41.03` blocks, regression `R^2=0.9396`), so the cone is
  explicitly encoded. The two crown centres are separated by 22.88 blocks at
  179.24 degrees, proving why one view yields a single apex and the orthogonal
  view yields two horns. More importantly, the shader's one-winner-per-role
  selection produces continuous-domain endpoint changes up to about 32 blocks;
  the GPU capture above confirms that these survive rasterization as 13--22
  block neighbour jumps.
- This diagnostic baseline is accepted. It falsifies temporal regeneration as
  the primary culprit and identifies the first correction boundary: replace
  the discontinuous single-winner stage endpoint selection with a continuous,
  support-consistent reconstruction and verify that the measured jumps fall
  before changing topology, crown curves, lighting or blur. Minecraft was then
  unfrozen and closed through `WM_CLOSE`; it logged `Stopping!`, terminated all
  PA pools, saved overworld, Nether and End, and the Java process exited.

## Iteration 134 - hardened readback exposes a client-presentation signature cycle

- This iteration still changed no cloud morphology, material, lighting or
  precipitation parameter. A read-only review of the Iteration 133 tool found
  that `GL_WAIT_FAILED` could leave its PBO permanently busy, a failed
  `glUnmapBuffer` was ignored, exceptional paths did not guarantee an unmap,
  and the raw support threshold did not account for the renderer's
  `CoverageMul`. These defects were corrected before using the tool for a
  source A/B. Pixel-pack byte swapping and transfer GL errors are now guarded,
  the diagnostic applies the raymarch's one-block minimum thickness, reports
  invalid/inverted endpoints, and limits its role summary to the first 96
  profile-3 cells actually eligible for the cumulus shader.
- `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and `build`
  passed. The exact built/deployed native-only JAR SHA-256 was
  `B593E4A6B6C8DE1A70C9E041675C9B95CC7144A0C9D48FDDA0FF34E83EB7E9DC`.
  Forge again selected native PA without Simple Clouds and rendered twelve
  fields with roles `4/3/3/2`; the hardened PBO capture completed twice without
  a GL, fence, map or unmap failure.
- With `CoverageMul=1.25`, the true raw visibility threshold is 0.0096 (logged
  as 0.010), not 0.012. The corrected baseline still finds the same defect:
  BASE 47.9%, CORE 75.2%, TOWER 76.2% and CROWN 72.3% of compared active
  neighbour pairs exceed a four-block endpoint delta. Maximum base/top deltas
  remain `1.39/16.08`, `7.68/20.13`, `11.77/21.63` and `9.68/15.97` blocks.
  Every stage reports zero invalid and zero inverted endpoints, ruling malformed
  encoded values out while retaining the winner-boundary discontinuity.
- A new, separate stability fact emerged from the logs. Immediately after the
  server command froze movement, capture signatures and all three map hashes
  followed A -> B -> A over roughly 49 seconds:
  `5b2afce1e9dbf72e` / `7572d36ff3f86a69` /
  `5b2afce1e9dbf72e`. The A support/base/top hashes were exactly
  `9b5696087c2c4325/0f280c8fbb908325/c5d31eabe37a6325`; the B hashes were
  `67c0f77998948325/7937ebd6dc742325/9e38560863c74325`. Map misses rose from
  455 to 493 despite a stationary camera and frozen server movement. The
  source `FieldInfo` centres and bounds remained identical in the later frame,
  so this is not evidence of the generator rebuilding the twelve-lobe topology.
- The Iteration 133 long-term stable capture began from an already settled
  presentation state; Iteration 134 proves that an immediate post-freeze A/B is
  invalid because a client-side interpolated/media input still changes. Before
  the endpoint-blend correction, the input signature must be split into domain,
  position/radius, shape/height, media, morphology and dynamics hashes to name
  the changing channel. The frozen setting was intentionally left enabled
  across shutdown so the next launch starts from the same server-side state.
  Minecraft closed through `WM_CLOSE`, terminated all PA pools, saved all
  dimensions and exited normally.

## Iteration 135 - component counters prove that only presented X/Z oscillate

- No rendering formula changed. `CloudWeatherMapRenderer` split its exact
  quantized cache key into domain, position/radius, shape/height, media,
  morphology and dynamics hashes, then counted transitions independently for
  every vec4 slot. The normal five-second status remains concise; the expanded
  breakdown is emitted only by explicit diagnostics. The full Gradle group
  (`compileJava`, resources, sandbox, tests and build) passed, and the deployed
  native-only JAR SHA-256 was
  `6EAF10BB8492D4A68F8054891F16800ACB00F2894281390E30DC379A290A87A3`.
- The first counter sample included startup's temporary autonomous-cell path
  and the switch to twelve fields, so it was not used causally. During the next
  settled 30-second window, with a stationary camera, frozen server movement,
  twelve fields and unchanged `4/3/3/2` roles, weather-map misses increased by
  18. Only two counters advanced: position X from 23 to 31 and position Z from
  41 to 63. Radius-major/minor stayed 12/12; all four shape slots, all media
  slots, all morphology slots and all dynamics slots remained unchanged.
  Domain, current component hashes and the final full texture hashes also
  returned to their original A values.
- This names the temporal culprit without visual inference. In
  `ClientCloudFieldCache.PresentationTrack.present`, a delayed frame beyond the
  newest snapshot calls `extrapolateSnapshot`, which adds
  `snapshot.windVector() * deltaTicks` to the centre. A subsequent frozen packet
  publishes the same authoritative centre again, producing forward motion then
  reset. The interpolation path can reinforce the loop because
  `interpolateCenter` uses non-zero wind tangents even when older and newer
  centres are identical; equal endpoints with equal non-zero Hermite tangents
  mathematically leave and return to the endpoint. The measured X/Z-only
  transitions are the exact signature of these two paths.
- The next correction is deliberately outside the shader: interpolate
  authoritative snapshot centres without overshoot and extrapolate from the
  velocity actually observed between the two latest centres. When movement is
  frozen, observed velocity is zero; when it is active, it represents the
  server's real displacement. Success requires zero new X/Z component
  transitions and zero weather-map misses over a settled frozen 30-second
  window. Minecraft closed normally through `WM_CLOSE`; all pools terminated
  and all dimensions were saved. The freeze setting remains enabled for the
  controlled before/after test.

## Iteration 136 - authoritative-centre presentation removes the frozen A/B cycle

- `ClientCloudFieldCache.PresentationTrack` now treats a field centre as an
  authoritative position rather than using atmospheric wind as a position
  tangent. Snapshot-to-snapshot centres are interpolated linearly, which cannot
  overshoot equal endpoints. Extrapolation uses the displacement observed
  between the two latest authoritative snapshots; missing, discontinuous or
  zero-duration pairs resolve to zero velocity. Atmospheric wind remains in the
  snapshot for internal cloud animation and was not repurposed or discarded.
- `compileJava`, `processResources`, `cloudFieldSandbox`, `test` and `build`
  all passed. The exact native-only JAR SHA-256 was
  `4F65A2376DC8B2D7B52C15C6C90DDF800CE7AB2D45636C12EFE2CABC101F9345`.
  Forge loaded PA without Simple Clouds and a new frozen
  `cumulus_congestus` produced twelve fields with the required
  `base=4,core=3,tower=3,crown=2` distribution. The command reported an
  atmospheric wind of 11.8 m/s SSW; the frozen snapshots themselves exposed
  `windXZ=0,0`, so this run validates the stationary-authority case, not active
  moving-field extrapolation.
- The first capture after spawning was correctly rejected as a stability
  witness: lifecycle/density and one radius were still settling, producing 22
  additional weather-map misses. Once settled, capture 2 at game time 149910
  and capture 3 at 151011 were separated by 1101 ticks (about 55 seconds).
  Across that window weather-map misses stayed exactly at 8128 while hits rose
  from 67746 to 117225. Every transition counter stayed identical, including
  position/radius `106/106/347/310`; domain, position, shape, media,
  morphology and dynamics component hashes were unchanged.
- The actual GPU textures were also bit-stable across the same window. Both
  captures retained input signature `d8ac76af16b79ed8` and support/base/top
  hashes
  `c00daa8149086325/d48d1bc25fd70325/9a0ff6e41775a325`.
  This satisfies the frozen before/after criterion and removes the measured
  client-presentation wave source instead of masking it in the shader.
- The morphology defect remains deliberately untouched. On the settled map,
  endpoint jumps above four blocks still affect 45.2% of BASE neighbours,
  73.5% of CORE, 74.8% of TOWER and 73.2% of CROWN, with maximum top jumps of
  13.04, 33.21, 27.91 and 22.88 blocks respectively. These stable values define
  the next controlled A/B: change only the discontinuous per-stage endpoint
  winner while preserving the maximum support field. Minecraft then closed
  through `WM_CLOSE`, logged `Stopping!`, terminated all three PA pools, saved
  all dimensions and exited normally.

## Iteration 137 - endpoint blending confirms the seam cause but fails strict support isolation

- This candidate changed only the profile-3 stage-map aggregation. It retained
  the maximum lobe support and replaced the base/top endpoint of the single
  winning lobe with a continuous `support^8` weighted endpoint. A candidate at
  half the dominant support therefore contributes about 0.39%, while two
  equal-support lobes blend continuously across their former winner boundary.
  No layout, vertical profile, density, noise, lighting or reconstruction code
  changed.
- The complete Gradle group passed and the exact deployed JAR SHA-256 was
  `D028135384222C11DDFEFA6541BB2DC21EE5620A50E54DEA195047A91876308F`.
  Forge loaded the native-only backend and compiled/registered all volumetric
  shaders. The persisted frozen field had the exact Iteration 136 input
  signature `d8ac76af16b79ed8`, component hashes, twelve cells and `4/3/3/2`
  role distribution.
- The causal prediction was partly confirmed. Compared with the exact settled
  Iteration 136 map, maximum top-neighbour jumps fell from
  `13.04/33.21/27.91/22.88` to `8.43/18.08/19.44/16.94` blocks for
  BASE/CORE/TOWER/CROWN. The fraction above four blocks also fell from
  `45.2/73.5/74.8/73.2%` to `38.6/66.8/70.6/70.1%`. All stages retained zero
  invalid and zero inverted endpoints. Continuous endpoint aggregation is
  therefore acting on the measured defect rather than merely changing colour
  or blur.
- This candidate is not yet accepted as a controlled isolation. Although
  active texel counts, support means/maxima and centroids remained equal at the
  reported precision, the support hash changed from
  `c00daa8149086325` to `a782a312ae3a6325`. The source input was bit-identical,
  and support was intended to be unchanged. The implementation had replaced
  the historical conditional winner assignment with GLSL `max`; the next
  candidate must restore the exact conditional support path while retaining
  endpoint accumulation, then repeat the same hash test. Minecraft was closed
  normally through `WM_CLOSE`; all pools terminated and all dimensions were
  saved before the next source edit.

## Iteration 138 - historical support branch disproves the `max` lowering hypothesis

- The endpoint blend remained identical, but its support assignment was changed
  back to the exact historical `if (support > bestSupport[channel])` branch.
  This was a targeted falsification of the Iteration 137 hypothesis that GLSL
  `max` alone had changed support bits.
- The complete Gradle group passed. Build and deployed JAR hashes matched at
  `6434B6C6DDDF9B114C781F2992E12988EC90BA361F112D57CB02A17F73C303CF`.
  Forge loaded the native-only backend, registered the shader and rendered the
  same frozen source signature `d8ac76af16b79ed8` with the same component
  hashes and `4/3/3/2` role distribution.
- The result exactly matched Iteration 137, including support/base/top hashes
  `a782a312ae3a6325/3e8c590976a82325/ba38349068352325`, every active texel
  count, endpoint statistic and neighbour-jump metric. The `max` instruction
  hypothesis is therefore rejected.
- The remaining full-float support hash difference cannot yet be classified as
  a semantic footprint change. The current hash includes every IEEE float bit,
  while reported support means/maxima have only three decimal places. Adding
  endpoint arithmetic can change driver optimisation and last-bit rounding even
  when the mathematical maximum is unchanged. The next diagnostic must report
  a quantised support hash and higher-precision support sums. It will capture the
  exact original winner shader and the blended shader against the same frozen
  input before this candidate is accepted or rejected. Minecraft closed through
  `WM_CLOSE`; all pools terminated and all dimensions were saved.

## Iteration 139 - exact winner rollback proves the support hash change was not caused by blending

- The cumulus layer shader was restored exactly to its pre-blend single-winner
  implementation. Independently, the read-only diagnostic gained Q10/Q12
  quantised support hashes and nine-decimal stage support sums. This run is the
  controlled original-shader side of the comparison; no rendered formula other
  than the explicit rollback was present.
- The full Gradle group passed. The deployed native-only JAR SHA-256 was
  `3D926D07B480B9466D66A3BEA8120CB80B0D124DDF645677E2A091FF9A610873`.
  Forge loaded the same frozen input signature `d8ac76af16b79ed8`, component
  hashes and `4/3/3/2` roles, then completed the fence-gated readback without a
  GL failure.
- Contrary to the earlier assumption, the exact historical shader produced raw
  support hash `a782a312ae3a6325`, precisely the same hash produced by both
  Iteration 137 and 138 blended shaders. The original-shader Q10/Q12 reference
  is `c36e5b5c79b054e7/7787a2ad5bfbffd8`; stage support sums are
  `121.626342773`, `76.221084595`, `50.304656982` and `31.342514038`.
- This rollback falsifies endpoint accumulation as the cause of the earlier
  `c00d...` versus `a782...` cross-session discrepancy. Its exact origin remains
  unconfirmed and is not being presented as fact, but the controlled old/new
  builds now agree on every raw support bit. The endpoint blend can be reapplied
  and judged against the explicit Q10/Q12 and support-sum reference above.
  Minecraft closed normally; all pools terminated and all dimensions were
  saved before the next shader edit.

## Iteration 140 - controlled `support^8` endpoint blend is bit-isolated from footprint support

- The continuous endpoint blend from Iterations 137/138 was reapplied without
  any other rendered-formula change, now alongside the strengthened diagnostic.
  The full Gradle group passed and the exact deployed native-only JAR SHA-256
  was `DC04561E2173DAEEF25F134195CF55430C4FA70F0F429CDDBA9FB62CFF5E21E5`.
  Forge loaded all shaders and rendered the same frozen input signature and
  `4/3/3/2` stage layout.
- Support isolation is now proven at every recorded level. Original and blended
  shaders both produced raw support hash `a782a312ae3a6325`, Q10/Q12 hashes
  `c36e5b5c79b054e7/7787a2ad5bfbffd8`, active texel counts
  `206/140/93/57`, and stage sums `121.626342773`, `76.221084595`,
  `50.304656982`, `31.342514038`. Thus the macro footprint and coverage are
  bit-identical; only base/top maps differ.
- Against the exact original shader, BASE jump pairs fell from 170/376 to
  145/376 and maximum top jump from 13.07 to 8.43 blocks. CORE fell from
  186/253 to 169/253 and 33.21 to 18.08. TOWER fell from 122/163 to 115/163
  and 27.91 to 19.44. CROWN fell from 71/97 to 68/97 and 22.88 to 16.94.
  No stage gained invalid or inverted endpoints. This accepts continuous
  endpoint aggregation as a root-cause correction, not a footprint blur.
- The eighth-power selector is still too close to a hard winner for the upper
  stages: 66.8--70.6% of compared neighbours remain above the four-block
  threshold. The next controlled candidate will use fourth-power weighting.
  A half-strength lobe then contributes 6.25%, still favouring the dominant
  lobe but broadening the continuity zone. Support hashes/sums must remain
  exactly unchanged. Minecraft closed normally and saved every dimension.

## Iteration 141 - `support^4` widens continuity without changing the footprint

- Only the endpoint weighting exponent changed from eight to four. The maximum
  support calculation, lobe layout and every downstream density/lighting path
  remained untouched. The full Gradle group passed and the exact deployed JAR
  SHA-256 was
  `CF0254640B8D4925ADB9D921D58A6FE61F35EE1564A82BF113BE4A8284B33926`.
  Forge compiled the shader and rendered the same frozen source signature with
  the unchanged `4/3/3/2` roles.
- Footprint isolation remained exact: raw support hash
  `a782a312ae3a6325`, Q10/Q12
  `c36e5b5c79b054e7/7787a2ad5bfbffd8`, all active counts and all four
  nine-decimal support sums match the historical shader and the eighth-power
  candidate bit for bit.
- Relative to `support^8`, neighbour pairs above four blocks changed from
  `145/169/115/68` to `131/164/109/68` for BASE/CORE/TOWER/CROWN. Maximum top
  jumps changed from `8.43/18.08/19.44/16.94` to
  `8.43/14.42/15.07/16.15`. No invalid or inverted endpoint appeared.
  Fourth-power weighting is retained as the better measured continuity result.
- The remaining `>4` count is not treated as a discontinuity count. It also
  includes legitimate continuous curvature sampled eight blocks apart; chasing
  it toward zero by lowering the exponent again would be an unmeasured blur.
  The stable macro topology is now the next named defect: its stage radius
  decreases almost linearly with height (`R^2=0.9396`) and its two opposed crown
  lobes encode the cone/twin-horn silhouette. That generator path will be
  inspected and changed independently. Minecraft closed through `WM_CLOSE`,
  all pools terminated and all dimensions were saved.

## Iteration 142 - frozen-envelope logs isolate an absolute-time material drift

- This iteration deliberately made no rendered-formula change. The persisted
  twelve-member tower was unfrozen briefly, then frozen again, so its presented
  centres could become stationary while its snapshots retained non-zero wind.
  At game time `159778`, all twelve fields still reported either approximately
  `(0.12,-0.21)` or `(-0.22,0.23)` blocks/tick. The exact resolved uniform is
  not yet printed by the current diagnostic, so its weighted value is not being
  inferred from those rounded per-field values.
- Two general diagnostics were captured at wall-clock `22:20:24` and
  `22:21:14`, 50 seconds apart (`gameTime 159778 -> 160161`). Every presented
  centre was identical to the displayed precision across the interval. For
  example member 0 stayed at `(-0.14,256.00,-487.19)`, member 1 at
  `(-9.49,260.96,-517.78)`, and the three tower members stayed at
  `(-3.72,304.04,-503.39)`, `(8.45,309.19,-479.73)` and
  `(-8.08,305.34,-478.75)`. Thus neither cloudlet regeneration nor continuing
  presentation movement can explain material motion during this frozen window.
  The field lifecycle did continue to decay; that independently explains the
  falling density and eventual `no_clouds` status and is not being conflated
  with horizontal domain motion.
- Direct source tracing identifies a separate time-varying path after the
  stable weather maps: both stratus and the main density function execute
  `samplePos.xz -= WindVec.xz * WorldTime * (1 + 0.30 * h01)` in
  `cloud_atmosphere_volume.fsh`. `WindVec` is an instantaneous weighted field
  velocity, while `WorldTime` is absolute time. A freeze stops the centres but
  does not clear their velocities. More generally, changing the instantaneous
  velocity changes the historical displacement retroactively by
  `WorldTime * deltaWind`. The weather/stage-map hashes cannot expose this
  path because their native-field input signature contains neither this
  raymarch uniform nor the raymarch time.
- This is a causal diagnosis, not yet an accepted correction. Before replacing
  the formula, the next source-only step will add a six-decimal diagnostic for
  the exact resolved `WindVec`, the presented centroid delta, the legacy
  `WindVec * WorldTime` offset and its frame-to-frame slip. The same
  unfreeze/freeze reproduction must show a stationary envelope and a changing
  legacy offset in `latest.log`. Only then will advection be changed to a
  lifecycle-managed integrated displacement tied to the actually presented
  field movement. Minecraft closed normally through `WM_CLOSE`, logged
  `Stopping!`, terminated all three PA pools and saved every dimension.
- The diagnostic-only implementation then passed `compileJava`,
  `processResources`, `cloudFieldSandbox`, `test` and `build`; the sandbox
  self-check passed. The exact built and deployed native-only JAR SHA-256 was
  `31BA412CAA79614A091A1B8EE3D7CD7B2C5505F37D5287F0A75FE19F7036FB6A`.
  No shader, uniform value or rendered formula changed in this build.
- A fresh `cumulus_congestus` was spawned while movement was enabled, allowed
  to move, then frozen. Once presentation settled, the log repeatedly measured
  `dCentroid=(0.000000,0.000000)`. Over one 20.031-tick window, a resolved-wind
  change of only `(0.000040,-0.000044)` produced
  `dLegacy=(5.970947,-6.675781)` blocks and a matching stationary-envelope
  `slip=(5.970947,-6.675781)`; the isolated absolute-time amplification term
  was `(6.473257,-7.120067)` blocks. Subsequent tiny wind changes produced
  7--9-block slips per roughly 20 ticks while the centroid remained exactly
  fixed.
- After the resolved wind itself stabilized at
  `(-0.024065,0.021066)` blocks/tick, two further 20-tick windows still measured
  approximately `dLegacy=(-0.481,0.421)` with zero centroid displacement and
  zero `WorldTime * deltaWind`. This independently proves both terms of the
  defect: ordinary `wind * deltaTime` slides material through a frozen
  envelope, while `WorldTime * deltaWind` causes the much larger phase jumps.
  The correction may therefore remove the absolute-time formula without an
  artistic A/B or blur experiment. The diagnostic centroid itself will not be
  reused as the production integrator: production must average displacement of
  UUID-matched fields so an entering or leaving field cannot move the material
  domain. The client again closed normally and saved all dimensions before the
  corrective edit.

## Iteration 143 - UUID-matched material advection removes the measured drift

- The two absolute-time translations in the stratus and main-density paths
  were replaced by one lifecycle-managed `MaterialOffset`. Its increment is
  the weighted mean displacement of UUID-matched members that were actually
  selected for rendering; newly entered and removed members cannot translate
  the domain. The matching pass also reports per-member residual RMS/max, and
  loss of source identity or a dimension discontinuity invalidates temporal
  history. Regional-only rendering integrates velocity over frame delta rather
  than multiplying an instantaneous velocity by absolute world time.
- Temporal reprojection now subtracts the corresponding
  `MaterialFrameDelta`, so a material sample follows the same integrated
  displacement in the previous frame. Two independent read-only reviews
  confirmed the offset sign, reprojection sign, matrix convention, uniform
  declarations/uploads, lifecycle invalidation and absence of any remaining
  `WindVec * WorldTime` translation. One pre-existing temporal-depth-space
  mismatch remains separately identified; it does not invalidate this
  advection A/B and was deliberately not mixed into this correction.
- `compileJava processResources testClasses materialAdvectionSandbox` passed,
  including rigid shuffled UUID motion, a 600-frame frozen invariant,
  membership churn, divergent motion residuals, dimension reset, regional
  delta-time integration at a large absolute time, and a shader source guard.
  The complete `compileJava processResources cloudFieldSandbox test build`
  group also passed. JSON parsing and the uniform/source static checks passed.
  The exact built and deployed native-only JAR SHA-256 was
  `8419564301021F70F2F02259A01586ADB98B5393D504974C7B827001F0556E91`.
- Forge loaded the native backend with Simple Clouds absent, registered the
  volumetric shaders, baked all noise textures and rendered the newly spawned
  twelve-member `cumulus_congestus` with the expected `4/3/3/2` roles. During
  live movement the actual tracker followed only presented member motion. Its
  observed residual maximum stayed between roughly 0.004 and 0.049 block,
  while the counterfactual legacy offset jumped by thousands of blocks when
  the resolved wind changed. For example, one logged window measured
  `dCentroid=(0.102701,-0.555283)`, legacy slip
  `(6118.383627,-12466.813858)`, but actual per-frame material delta only
  `(0.001022,-0.003168)` with `residualMax=0.014027`.
- After `/pa cloud freeze true` settled, seven consecutive five-second status
  windows retained all twelve UUID matches, no entries/leaves/rejections,
  `dCentroid=(0,0)`, `MaterialOffset=(1.704292,-11.796872)`, zero actual delta,
  and `residualRms=residualMax=0`. Over the same windows the retained
  counterfactual formula continued to slip by approximately
  `(0.438,-0.783)` block per 20 ticks despite the stationary envelope. This is
  the predicted discriminating result: the production material domain is now
  bit-stable under freeze while the diagnosed legacy path would not be.
- This accepts the material-advection correction on numerical runtime
  evidence. It does not claim the macro silhouette, reconstruction or rain
  footprint are fixed. Minecraft closed through `WM_CLOSE` at `22:44:50`,
  logged `Stopping!`, terminated all three PA pools, saved overworld, Nether
  and End, and exited normally before the next source change.

## Iteration 144 - frozen fresh-spawn logs isolate the staged cone topology

- No source or JAR changed in this diagnostic iteration; the deployed hash
  remained `8419564301021F70F2F02259A01586ADB98B5393D504974C7B827001F0556E91`.
  The first persisted tower inspected was almost collinear in X/Z after about
  2,000 simulation ticks. `/pa cloud evolution` showed that its persistent
  `CloudField` centres already exactly matched their live backend targets, so
  the network, client interpolation and renderer were excluded as the place
  where that particular alignment was introduced. Its historical cause is not
  yet confirmed and is not being conflated with initial morphology.
- To remove age and motion from the topology test, all clouds were cleared,
  movement was frozen before spawn, and a new `cumulus_congestus` was created
  and synchronised. Its twelve authoritative fields had zero velocity and the
  expected full two-dimensional layout immediately: four BASE, three CORE,
  three TOWER and two CROWN members. For example, relative to the primary near
  `(-1,-695)`, the three shoulders occupied approximately
  `(23.8,-16.5)`, `(2.4,28.4)` and `(-27.1,-14.3)` blocks. This rejects an
  initial angle-decoding or coordinate-unit collapse.
- The fresh layout instead reproduces the generator tables' staged taper. The
  theoretical lobe-radius scale has correlation `-0.9811` with height
  (`R^2=0.9625`). Excluding the zero-radius primary, centre radial distance has
  correlation `-0.9406` with height (`R^2=0.8847`). Mean radius scale falls
  `0.925 -> 0.817 -> 0.717 -> 0.610` from BASE through CROWN, while mean radial
  scale falls `0.390 -> 0.340 -> 0.247 -> 0.220`. Thus both the lobe size and
  its distance from the updraft contract together; this is a measured cone,
  not a shader-name inference.
- A fence-gated GPU capture of exactly that frozen fresh spawn confirmed the
  taper survived all CPU/GPU transforms. Active stage areas were
  `196/132/105/62` texels, so successive upper/lower area ratios were
  `0.673/0.795/0.590`. CROWN was the most anisotropic stage with
  `sigmaMajorMinor=19.428/12.821`, consistent with the two opposed crown
  members, and three TOWER-to-CROWN texels had vertical gaps up to 10.427
  blocks. The support/base/top hashes were
  `285bc5c4bde00325/42e9fdae3c23e325/9ade5dead3728325`; these and the complete
  stage statistics are the numerical baseline for the next isolated topology
  A/B.
- The next candidate may therefore change only the deterministic angles,
  radial offsets and lobe-radius scales. Heights, lower/upper envelopes,
  `4/3/3/2` membership, density, noise, lighting, reconstruction and the
  accepted `support^4` endpoint blend must remain unchanged. Acceptance
  requires reduced height/radius and height/radial correlation, no opposed
  two-lobe apex, a less severe CROWN area collapse, no new invalid/inverted
  endpoints, and unchanged temporal/material invariants. Minecraft closed via
  `WM_CLOSE` at `22:52:20`, terminated all three PA pools and saved every
  dimension before the edit.

## Iteration 145 - authoritative per-lobe wind collapses morphology at a region boundary

- No source, shader or JAR changed in this diagnostic iteration; the deployed
  native-only hash remained
  `8419564301021F70F2F02259A01586ADB98B5393D504974C7B827001F0556E91`.
  A new twelve-member `cumulus_congestus` was created with movement frozen,
  synchronised, and captured at game time `169126`. Its authoritative/client
  centres still reproduced the fresh two-dimensional layout: X ranged from
  `-28.05` to `+22.76`, Z from `-715.37` to `-666.60`, and the similarity fit
  to the deterministic generator table was `R^2=0.998970` with `0.627` block
  RMS error. This is the pre-motion control, not a visual inference.
- Movement was then enabled for 30 seconds, disabled again, and the same UUIDs
  were captured at game time `169960`. All twelve retained their morphology
  group and member indices, but eleven centres rounded to `X=-0.00` and the
  remaining centre to `X=+0.01`. Their renderer cells copied those values
  exactly. `/pa cloud evolution` also reported every persistent field centre
  equal to its live backend target. The destructive deformation therefore
  exists in the authoritative `CloudClusterState` before field adaptation,
  packets, client interpolation, weather-map construction or raymarching.
- Source tracing explains the exact boundary. `CloudRegionMotionController`
  iterates clusters separately and calls `resolveWindVelocity` at each lobe's
  current centre. This cloud straddled the X=0 boundary between forecast
  regions `(-1,-1)` and `(0,-1)`. Their resolved winds were respectively
  approximately `(+0.019376,-0.034671)` and
  `(-0.121184,+0.127653)` blocks/tick: both X components advect their local
  lobes toward the same discontinuity. Once a member crosses, its independently
  sampled velocity reverses, trapping/oscillating the sibling geometry near
  X=0 and destroying all relative offsets. Saved authoritative NBT from an
  older occurrence independently contained the same collapse, including
  member centres and velocities on opposite sides of the boundary.
- `CloudRegionMergeController` excludes morphology siblings, and the complete
  downstream path preserves the supplied coordinates. Absorption, morphology
  decoding, coordinate units, shader union and reconstruction are therefore
  ruled out as causes of this measured 30-second collapse. They may still have
  separate defects, but changing them cannot repair this server-side topology
  loss.
- The next isolated correction will advect a morphology group as one rigid
  body. Every active sibling must receive the exact same per-tick delta from a
  stable group anchor; standalone clusters keep their existing behaviour. A
  deterministic invariant will compare UUID-relative offsets before and after
  motion and log the group ID, anchor/source region, sampled wind, boundary
  state and maximum relative-offset drift. Merely sampling the raw wind at a
  centroid exactly on the discontinuity is not accepted, because that could
  translate the whole group back and forth. Source-region semantics and tick
  order will be verified before choosing the stable anchor.
- Minecraft closed through `WM_CLOSE` at `22:58:30`, logged `Stopping!`, saved
  overworld, Nether and End, reported all dimensions saved, and exited normally
  before the corrective edit.

## Iteration 146 - first rigid-motion sandbox run exposes a missing Minecraft bootstrap

- The isolated corrective implementation groups active multi-member clusters by
  persistent `morphologyGroupId`, resolves one velocity from the owning
  `CloudRegionState.sourceRegionKey`, and applies that exact delta to every
  member. Legacy states without a source latch one fallback region instead of
  resampling across a discontinuity. Standalone single-member clusters retain
  their previous local-region lookup. A boundary-only runtime diagnostic records
  sampled region, occupied-region count, relative-offset hash and maximum drift.
- `compileJava` and `testClasses` passed. The first
  `cloudRegionMotionSandbox` launch failed before reaching a movement assertion:
  constructing `Level.OVERWORLD` outside a launched server triggered
  `IllegalArgumentException: Not bootstrapped` from Minecraft's registry
  initialisation. This is a test-harness bootstrap defect, not an accepted or
  rejected movement result.
- The exact Forge 47.4.20 mapped Minecraft 1.20.1 source was inspected at
  `forge-1.20.1-47.4.20_mapped_official_1.20.1-sources.jar` rather than assuming
  the API. `net.minecraft.server.Bootstrap` declares
  `public static void bootStrap()`. The next change will call that verified
  bootstrap once at sandbox startup, then rerun the same assertions without
  changing the production motion formula.

## Iteration 147 - registry bootstrap requires the detected 1.20.1 game version

- Adding the verified `Bootstrap.bootStrap()` call advanced the standalone
  harness into vanilla registry initialisation, but it again failed before any
  motion assertion. `DataFixers` raised `IllegalStateException: Game version
  not set` while `ComposterBlock.bootStrap()` initialised item/entity data.
- The exact mapped Forge 47.4.20 source for
  `net.minecraft.SharedConstants` was inspected. It exposes
  `public static void tryDetectVersion()`, which installs
  `DetectedVersion.tryDetectVersion()` only when no current version exists.
  The next harness-only change will call this verified method before
  `Bootstrap.bootStrap()`. The production controller remains unchanged between
  these two harness attempts.

## Iteration 148 - Forge network bootstrap is not a valid standalone motion harness

- Calling `SharedConstants.tryDetectVersion()` resolved the version failure,
  but Forge 47.4.20's patched `Bootstrap.bootStrap()` then invoked
  `NetworkHooks.init()`. The standalone `JavaExec` has no fully launched FML
  event environment, so event-list construction failed with
  `NoSuchMethodException: NetworkEvent.<init>()`. Movement assertions again did
  not execute.
- This rules out full Minecraft bootstrap as an appropriate unit boundary for
  the server motion contract. The next change will extract the production
  velocity plan into package-private pure data: persistent cluster/group UUIDs,
  centres, region keys and resolved `Vec3` deltas. The live controller will
  consume that exact plan to mutate `CloudClusterState`; the sandbox will test
  the same planner without loading `Level`, registries or Forge networking.
  This changes the harness boundary only, not the chosen source-region motion
  rule.

## Iteration 149 - pure production motion plan preserves offsets across the convergent boundary

- The live controller now builds and consumes a package-private pure motion
  plan. Each active multi-member `morphologyGroupId` performs exactly one
  canonical source-region velocity lookup and receives one common delta;
  standalone clusters still resolve their own local region. A legacy group
  with neither source nor current key derives one arithmetic-group anchor and
  latches that key once.
- `compileJava`, `testClasses` and `cloudRegionMotionSandbox` passed. The
  sandbox ran the explicit convergent-boundary counterexample for 400 ticks:
  members began on both sides of X=0, the negative region supplied
  `(+1.0,-0.25)` while the positive region supplied `(-5.0,+0.75)`, and the
  source was the negative region. Exactly 400 resolver calls occurred for 400
  group ticks, the primary translated from X=-25 to X=375, and all UUID-relative
  offsets remained exactly equal at zero tolerance. Thus the planner cannot
  reproduce the old per-lobe convergence.
- Separate assertions confirmed that two standalone clusters still sampled
  their respective local regions, and that a legacy two-member group latched
  the negative fallback region before crossing X=0. No Minecraft bootstrap,
  registry or Forge network environment is now required by this deterministic
  contract test.
- This is not yet runtime acceptance. The next steps are an independent source
  review, the complete Gradle validation group, deployment of the resulting
  hash, and repetition of the exact fresh-spawn/freeze/30-second A/B. Runtime
  acceptance requires stable relative offsets and matching boundary diagnostic
  hashes in `latest.log`; compilation alone is insufficient.
- The complete `compileJava processResources cloudFieldSandbox test build`
  group then passed. It executed the CloudField, material-advection and new
  cloud-region-motion sandboxes; both Java compilation and resource processing
  completed without new errors. The resulting native-only JAR SHA-256 is
  `615C7EAE3027972D9D55473EDE8B9355B089FF245D4E86366AABC4637FFD60B9`.
- An independent read-only review found no blocker for the runtime A/B and
  confirmed that standalone clusters retain their local-region sampling. It
  also identified limits that remain explicit: the sandbox does not execute
  `CloudClusterState` setters, the boundary hash is intra-tick rather than a
  retained cumulative baseline, source-region wind intentionally postpones a
  future long-distance transition policy, and the planner allocates more than
  the old loop. These are reasons to require runtime UUID comparison and later
  profiling, not reasons to alter this isolated correction before testing it.

## Iteration 150 - native runtime A/B accepts rigid morphology motion

- The exact deployed native-only JAR hash was
  `615C7EAE3027972D9D55473EDE8B9355B089FF245D4E86366AABC4637FFD60B9`.
  The instance contained PA, Architectury, Cool Rain and Gaboulibs only; no
  Simple Clouds, CrackersLib, Oculus, Iris or Distant Horizons JAR was present.
  Forge 47.4.20 loaded the volumetric shaders and completed the native noise
  bake without a new mixin, shader or startup error.
- With movement frozen before spawn, a new twelve-member
  `cumulus_congestus` group `8dcf33d3...` was captured at game time `172373`.
  It straddled the exact problematic boundary with X centres from `-27.63` to
  `+25.32`, retained all `4/3/3/2` BASE/CORE/TOWER/CROWN roles, and declared
  source `region[-1,-1]@2000`. This was a fresh control; the previously
  collapsed saved group was cleared.
- Movement ran for 30.839 wall-clock seconds and was frozen again before the
  second capture at game time `173513`. All twelve UUIDs remained present.
  Their logged mean translation was `(36.567500,25.745000)` blocks. After
  removing that common translation, residual RMS was `0.006614378` block and
  residual maximum `0.009013878` block, both bounded by the diagnostic's
  two-decimal centre output. The horizontal PCA eigenvalue ratio remained
  `1.172279 -> 1.172027`. In contrast, the unchanged old-JAR control in
  Iteration 145 had moved every X centre to approximately zero over the same
  interval.
- Boundary diagnostics during live motion independently recorded a single
  sampled region, two occupied regions, identical quantised relative-offset
  hashes `3bdd3c8fd5a84936 -> 3bdd3c8fd5a84936`, and
  `maxOffsetDrift=0.000000000`. `/pa cloud evolution` after the run again
  showed each persistent field equal to its live backend target, and renderer
  cells copied the translated two-dimensional centres rather than a collapsed
  line.
- The authoritative deformation cause and correction are therefore accepted
  on runtime log evidence. This does not accept the initial staged cone table,
  reconstruction, lighting or rain alignment. A future long-distance regional
  wind-transition policy must be continuous and separately measured; the
  current correction deliberately retains source-region wind because no such
  API exists and the raw current-region selector is discontinuous.
- Minecraft closed through `WM_CLOSE` at `23:19:31`, logged `Stopping!`, saved
  overworld, Nether and End, reported all dimensions saved, and exited normally
  before the next source edit.

## Iteration 151 - source-table sandbox reproduces the frozen cone baseline exactly

- No morphology value changed in this diagnostic step. A read-only accessor and
  standalone topology reporter were added around the exact four structured
  TOWER arrays consumed by `towerCell`, `towerTier` and `towerRadiusScale`.
  `compileJava`, `testClasses` and `cloudMorphologyTopologySandbox` passed.
- The reporter independently reproduced the Iteration 144 calculations from
  source: height/radius `R^2=0.962537`, height/radial-distance
  `R^2=0.884712` (primary excluded), and a `180.000` degree separation between
  the two crown lobes. Stage mean radius scales were
  `0.925000/0.816667/0.716667/0.610000`; stage mean radial scales were
  `0.390000/0.340000/0.246667/0.220000`.
- This establishes one repeatable numerical oracle for the initial cone: both
  lobe size and centre distance contract almost monotonically with height, and
  the apex is an opposed pair. The next isolated A/B may change only angles,
  radial scales and radius scales. Height, lower/upper envelopes, stage count,
  density, noise, lighting, reconstruction and the accepted rigid motion remain
  fixed. Acceptance first requires materially lower correlations and no
  180-degree crown; GPU stage maps must then confirm the upper support no longer
  collapses as severely.

## Iteration 152 - isolated tower table removes the double source-space taper

- Only the deterministic structured TOWER angle, radial and radius arrays
  changed. Heights, lower/upper envelopes, member/stage count, all random-jitter
  amplitudes, field adaptation, shaders, density, lighting, reconstruction and
  rigid group motion remained untouched.
- `compileJava`, `testClasses`, `cloudMorphologyTopologySandbox` and
  `cloudRegionMotionSandbox` passed. Height/radius `R^2` fell from
  `0.962537` to `0.271449`; height/radial `R^2` fell from `0.884712` to
  `0.223182`. Crown separation changed from an exactly opposed `180` degrees
  to `135` degrees. Stage mean radius scales are now
  `0.900000/0.866667/0.820000/0.830000`, so CROWN is no longer the smallest
  stage, and radial means are `0.305000/0.373333/0.366667/0.330000` rather
  than a monotonic contraction.
- The accepted rigid-motion sandbox still passed, proving this table-only A/B
  did not disturb the x=0 correction. These source metrics accept the candidate
  for GPU testing, not yet for final rendering. The next harness-only change
  will turn the measured limits into regression assertions and wire the
  topology reporter into `check`; then a fresh frozen spawn must be captured
  through the actual weather/stage-map pipeline.
- Regression limits now enforce both correlations at or below `0.40`, crown
  separation between `90` and `155` degrees, and CROWN mean radius at least 95%
  of TOWER mean radius. The complete
  `compileJava processResources cloudFieldSandbox test build` group passed and
  executed all three native cloud sandboxes. The candidate JAR SHA-256 is
  `FEDA249F1FC82793B3B039F622FADAA5CB01EBF11D93908755191DFA51EDC88A`.

## Iteration 153 - GPU stage maps accept the topology and localise the remaining waves downstream

- The exact deployed native-only candidate was
  `FEDA249F1FC82793B3B039F622FADAA5CB01EBF11D93908755191DFA51EDC88A`.
  The instance again contained PA, Architectury, Cool Rain and Gaboulibs only;
  startup explicitly selected the native PA service, loaded every volumetric
  shader and completed the noise bake without a new shader, mixin or startup
  error.
- All old cloud regions were cleared, movement was frozen before creation, and
  a fresh `cumulus_congestus` was synchronised. The production renderer received
  exactly twelve fields with the expected `4/3/3/2`
  BASE/CORE/TOWER/CROWN roles. The first asynchronous, fence-gated GPU capture
  reported no invalid or inverted vertical endpoint and no gap between any
  adjacent stage.
- Compared with the unchanged old-table frozen baseline from Iteration 144,
  BASE/CORE/TOWER/CROWN active areas changed from `196/132/105/62` texels to
  `170/152/130/107` during the growth phase. The consecutive upper/lower area
  ratios improved from `0.673/0.795/0.590` to
  `0.894/0.855/0.823`. Most importantly, TOWER-to-CROWN gaps fell from three
  texels (`4.839%`, maximum gap `10.427` blocks) to zero. Crown covariance
  became `26.052/18.041` rather than the former `19.428/12.821`; the upper
  support therefore no longer collapses into the old narrow opposed apex.
- A second capture after lifecycle growth reached its plateau produced active
  areas `187/174/150/125` and ratios `0.930/0.862/0.833`, again with zero
  inter-stage gaps and zero invalid endpoints. This accepts the isolated table
  correction through the actual generator -> field -> packet -> render-cell ->
  shader -> GPU texture path. It does not claim that final raymarched lighting
  or reconstruction is visually accepted.
- The first and second captures had different complete input signatures while
  the new cloud grew (`57b41fefe1157c3d -> 1a2005a4cbc7b450`), and support
  increased from roughly `0.09` mean to `0.54`. `/pa cloud list` reported
  `typeTicks=812`, `growth=1.00` and stable centres; this is expected lifecycle
  ramp-up because `/pa cloud freeze true` freezes translation, not simulation.
  It must not be mistaken for cloudlet regeneration.
- The decisive stability control repeated the capture about 70 seconds later,
  after coverage plateaued at `0.786`. Captures 2 and 3 were bit-for-bit equal:
  complete input signature `1a2005a4cbc7b450`, all six component signatures,
  support/base/top hashes
  `9017b68c1c266325/97d8cab9bd9de325/aa6d29e6b2f08325`, every stage statistic
  and every pair statistic. Weather-map misses stayed exactly `5433` while hits
  rose from `8012` to `31111`. Field statistics simultaneously reported twelve
  unchanged sources and no creation, update, removal, duplicate or rejection.
- Therefore the reported rapid wave-like motion and square edge instability do
  not originate in post-growth cloudlet layout, field transport or weather-map
  regeneration. They are now localised downstream to volume sampling,
  per-frame jitter/history or low-resolution reconstruction. The next change
  will instrument numerical per-stage frame deltas at raw raymarch, temporal
  history and final composite boundaries before changing any density, noise or
  reconstruction formula.
- One separate cache-key defect is confirmed by source inspection: the domain
  hash includes `regionalEnergy` even when `includeRegionalLayer` is false, so
  an invisible regional value can trigger needless field-map rebuilds. It did
  not affect the plateau control (no new misses), and it will not be mixed into
  the downstream stability diagnostic until its actual cost/impact is measured.
- The test cloud was cleared, movement was restored to its persisted active
  state, and Minecraft closed through `WM_CLOSE` at `23:29:41`. The client
  logged `Stopping!`, saved overworld, Nether and End, reported all dimensions
  saved and exited before source editing resumed.

## Iteration 154 - authoritative PA lifecycle is no longer applied twice

- The capture sequence established two temporal regimes rather than one vague
  instability. During the first 30 seconds, `CloudRegionLifecycleController`
  integrated growth directly into authoritative cluster radius, coverage and
  density. The PA cluster adapter copied those already-integrated scalars into
  `CloudField`, but `CloudFieldSnapshot.effectiveDensity()` and
  `effectiveCoverage()` multiplied them by `growth * (1-decay)` a second time.
  This is why the first GPU capture began near zero support even though the
  morphology generator deliberately starts TOWER lobes at 94% final radius and
  82% final coverage/density.
- The double envelope is quantitatively tied to the observed early waves:
  capture 1 at roughly +11 seconds had BASE support mean/max `0.089/0.113`;
  capture 2 after the 600-tick growth interval had `0.539/0.707`. Between them
  the cache recorded 3,576 additional weather-map misses, including thousands
  of media/lifecycle changes. The changing support repeatedly crossed density
  and erosion thresholds even though lobe centres were fixed. Capture 3 then
  proved the post-growth map stable.
- `CloudFieldSnapshot` now recognises `PA_CLUSTER` and `PA_REGION` as sources
  whose lifecycle scalars are already authoritative and returns their stored
  density/coverage directly. Growth and decay metadata remain present for
  `lifecycleStage()` and diagnostics. Manual, summary and unknown/derived fields
  keep the previous `density|coverage * growth * (1-decay)` behaviour.
- `CloudFieldValidation` now enforces both sides of that contract using the same
  projected field: a PA cluster snapshot with non-trivial growth/decay must
  preserve its authoritative scalars, while an UNKNOWN snapshot must retain the
  derived lifecycle envelope. `compileJava`, `cloudFieldSandbox` and `test` all
  passed; the sandbox reported `CloudField self-check passed`. Existing mixin
  and deprecation warnings were unchanged.
- Runtime acceptance remains required. The next native JAR test must capture a
  fresh cloud before and after the 30-second mark and verify that initial GPU
  support begins close to the generator's 82% birth values, changes smoothly by
  only the intended residual amount, and does not regress lifecycle or decay.
  Post-growth crawling is a separate question and is not attributed to this
  correction.

## Iteration 155 - runtime A/B finds one remaining shader-side lifecycle envelope

- The complete build passed `compileJava`, `processResources`,
  `cloudFieldSandbox`, `test`, `cloudMorphologyTopologySandbox`,
  `cloudRegionMotionSandbox`, `materialAdvectionSandbox` and `build`. The
  deployed native-only JAR SHA-256 was
  `BF8D25A2AB6BC29B65B453DF80FF9EF947CFB97F426A129BAB94AE8084AFF175`.
  Startup selected native PA, loaded the shaders and baked noise successfully.
- A fresh `cumulus_congestus` was spawned after movement was frozen. Capture 1
  occurred about seven seconds later. Removing the snapshot-side growth
  multiplication raised BASE support mean/max from the old comparable
  `0.089/0.113` to `0.229/0.292`, so the correction is active in the real
  packet/render path and materially reduces the near-zero birth state.
- Capture 2 used the same fixed group after `growth=1.00` and reported BASE
  support mean/max `0.571/0.730`. Early-to-mature support sum was
  `38.898300171/104.532379150`, only 37.2%, despite generator birth
  density/coverage being 82% of final. The group retained all twelve roles and
  both captures had valid endpoints; this deficit is not a missing member or
  failed sync.
- Source inspection after that measured residual found the exact second
  multiplier. `cloud_weather_splat.fsh`,
  `cloud_weather_morphology.fsh` and
  `cloud_weather_cumulus_layers.fsh` all decode the authoritative growth value
  from `CellDynamics.z` and apply another lifecycle envelope from `0.30` at
  birth to `1.00` at maturity. The server has already integrated growth into
  the PA cluster radius/density/coverage, so native PA fields still receive a
  duplicate visual growth envelope in every map consumed by the raymarch.
- Decay cannot simply be discarded: authoritative server integration retains
  about 35% of density/coverage at the terminal tick before removal. The next
  isolated correction will therefore distinguish formation from dissipation:
  authoritative PA snapshots use their already-grown density/coverage without
  a shader formation envelope, but retain one explicit `(1-decay)` fade to
  zero. Their GPU lifecycle stage will be mature (`0.5`) so the three map
  shaders do not apply a third envelope. Derived sources retain the existing
  bidirectional lifecycle semantics.
- The cloud was cleared, movement was restored and Minecraft closed through
  `WM_CLOSE` at `23:38:51`; `Stopping!` and all-dimensions-saved were confirmed
  before the next edit.

## Iteration 156 - authoritative formation is bypassed in all three GPU maps

- The Iteration 155 residual is now corrected at the CPU/GPU contract rather
  than by tuning a shader threshold. `CloudFieldSnapshot.effectiveDensity()`
  and `effectiveCoverage()` preserve the already-integrated PA cluster/region
  formation state and apply only one explicit `(1-decay)` terminal fade. This
  retains a continuous removal phase instead of making a mature field pop when
  the server reaches its terminal lifecycle tick.
- A new `visualLifecycleStage()` distinguishes authoritative PA projections
  from derived fields. PA cluster and PA region snapshots expose the mature
  stage `0.5` to the weather-map shaders, so the existing lifecycle envelope in
  `cloud_weather_splat.fsh`, `cloud_weather_morphology.fsh` and
  `cloud_weather_cumulus_layers.fsh` evaluates to one. Derived/UNKNOWN fields
  continue to expose their real lifecycle stage and retain the former
  formation/dissipation behaviour.
- Every native render-cell projection now uploads `visualLifecycleStage()`;
  no shader formula or morphology/noise/lighting/reconstruction parameter was
  changed in this isolation step. `CloudFieldValidation` asserts both sides of
  the contract: authoritative PA scalars receive exactly one decay fade and a
  mature GPU stage, while derived scalars retain `growth * (1-decay)` and their
  real lifecycle stage.
- `compileJava`, `cloudFieldSandbox` and `test` passed, and the sandbox reported
  `CloudField self-check passed`. This source result is not runtime acceptance.
  The next A/B must use one fresh frozen twelve-lobe group and compare the early
  and mature GPU support hashes/statistics. It will be accepted only if the
  large 37.2% early/mature deficit is removed without missing members, invalid
  endpoints or a new shader/cache failure.

## Iteration 157 - runtime logs accept the single authoritative lifecycle

- The full validation group passed `compileJava`, `processResources`,
  `cloudFieldSandbox`, `test`, `cloudMorphologyTopologySandbox`,
  `cloudRegionMotionSandbox`, `materialAdvectionSandbox` and `build`. The exact
  deployed native-only JAR SHA-256 was
  `AC08AEC7062DDCFFF0DDE4513238F796B42FC19C8B7B2F91918A9A3C74CB4529`.
  The instance contained PA, Architectury, Cool Rain and Gaboulibs only. Forge
  selected the native PA service, registered the volumetric shaders and baked
  all three noise textures without a new startup, mixin or shader error.
- Movement was frozen before a fresh twelve-member `cumulus_congestus` group
  `a2689fec...` was created. The early server snapshot at `typeTicks=173`
  reported `growth=0.29`, density `0.78` and coverage `0.68`. The asynchronous
  GPU capture retained all `4/3/3/2` BASE/CORE/TOWER/CROWN members and produced
  support sums `91.2800/87.5124/77.1534/61.2837`. Mean/max support was already
  `0.563/0.716`, `0.554/0.718`, `0.563/0.733`, `0.538/0.726` respectively.
- At `typeTicks=1269`, the same UUID reached `growth=1.00`, density `0.80` and
  coverage `0.70`. Its mature support sums were
  `98.7871/94.9761/83.8365/66.9660`. Early support was therefore
  `92.4%/92.1%/92.0%/91.5%` of mature support across the four stages. Before
  the correction, the comparable BASE support was only 37.2% of mature. The
  remaining small difference now agrees with the authoritative generator's
  deliberately smaller birth radius/density/coverage rather than a second
  shader lifecycle envelope.
- Both captures had zero invalid/inverted endpoints and zero gaps for every
  adjacent stage pair. Active areas changed only from `162/158/137/114` to
  `178/171/147/119`; no lobe disappeared. This rules out missing sync or stage
  decode as an explanation for the measured lifecycle delta.
- A third capture at `typeTicks=2618`, approximately 68 seconds after the first
  mature capture, was bit-for-bit equal to capture 2: complete input signature
  `bb82ae274e57abfa`, every component signature, support/base/top hashes
  `330d6f1abdb8a325/abcef0a3b11f2325/f1d2e0c044a4e325`, support Q10/Q12 hashes
  and every stage statistic matched. Weather-map misses remained exactly
  `2073` while hits rose from `14946` to `33375`.
- The duplicate lifecycle application is accepted as fixed through the actual
  server -> packet -> field -> render-cell -> shader -> GPU-map path. It also
  proves that any post-maturity crawling or square reconstruction artifact is
  downstream of the stable weather/stage maps. No density, erosion, temporal
  or composite formula will be changed until the next diagnostic measures raw
  raymarch, temporal output and reconstruction separately.
- The test group was cleared, movement was restored, and Minecraft closed via
  `WM_CLOSE` at `23:47:53`. `Stopping!`, overworld/Nether/End saves and
  `All dimensions are saved` were confirmed before the next source edit.

## Iteration 158 - rewind finds that the correct decay contract had already existed

- Before beginning downstream temporal instrumentation, the complete
  lifecycle sequence was retraced from the journal and current source. This
  found a regression in the reasoning, not a new visual hypothesis. Iteration
  154 already returned authoritative PA density/coverage directly. Iteration
  155 correctly proved that a separate shader-side formation envelope remained.
  Iteration 156 then retained the direct-growth rule and neutralised the shader
  envelope, but incorrectly added `(1-decay)` back to the snapshot.
- The erroneous premise in Iteration 155 was that server integration leaves
  approximately 35% at the terminal tick. That considered only the instantaneous
  factor `1 - 0.65 * decay`. In production,
  `CloudRegionLifecycleController.integrateGrowth()` feeds the previously faded
  density/coverage back into an 8% smoothing recurrence and multiplies the
  result by that factor on every one of the 600 decay ticks. The stored PA
  scalar is therefore already the dissipating scalar.
- A direct reproduction of the unchanged production recurrence with target and
  initial density `0.8` gives stored density approximately `0.454` at
  `decay=0.10`, `0.236` at `0.25`, `0.108` at `0.50`, `0.059` at `0.75`,
  `0.042` at `0.90` and `0.027` on the last visible tick. The current snapshot
  then reduces those again to approximately `0.409`, `0.177`, `0.054`, `0.015`,
  `0.004` and near zero. This is a confirmed double decay.
- Historical runtime notes independently agree with the recurrence: frozen
  fields previously showed their authoritative coverage falling toward zero
  and eventually reached `no_clouds` without a renderer-side lifecycle being
  required. The server removes inactive clusters at the lifetime boundary, one
  tick after the last already-small visible scalar.
- Iteration 157 remains valid evidence for formation only: all its samples had
  `decay=0.00`, and the early/mature support ratios prove the duplicate growth
  envelopes are gone. It did not and could not accept dissipation. The correct
  combined contract is the direct authoritative scalar from Iteration 154 plus
  the mature GPU lifecycle stage from Iteration 156. Derived/UNKNOWN fields
  retain their own `growth * (1-decay)` envelope.
- The next isolated source correction will restore that previously established
  direct PA scalar invariant and update the self-check accordingly. No shader,
  morphology, density, noise, temporal or reconstruction formula is part of
  this correction.
- The direct scalar contract is now restored: authoritative PA snapshots return
  stored density/coverage, while `visualLifecycleStage()` remains fixed at the
  shader's mature value. The non-zero-decay self-check again expects the stored
  `0.72/0.68` scalars exactly; derived/UNKNOWN snapshots still expect
  `growth * (1-decay)`. `compileJava`, `cloudFieldSandbox` and `test` passed,
  with `CloudField self-check passed`; existing mixin/deprecation warnings were
  unchanged.
- The complete `build` then passed all tests plus the topology, rigid-motion and
  material-advection sandboxes. `processResources`, reobfuscation and packaging
  also passed; `git diff --check` found no whitespace error. The resulting JAR
  SHA-256 is
  `73464E60C55363BA6E2581B3E72D82935FDFC4E3B77DD572FB1C24E5093CEFB3`.

## Iteration 159 - rewind preserves earlier reconstruction fixes and reuses existing diagnostics

- The earlier visual/remediation journal was reread before designing a new
  pipeline diagnostic. Several requested fixes already exist and must not be
  reimplemented: Iteration 10 retained absolute bilinear weights at accepted
  low-resolution neighbours, assigns depth one to empty rays and rejects their
  temporal history; the current shaders still contain those exact contracts.
- Existing debug modes also already cover most requested isolation views.
  `cloud_field_composite.fsh` exposes raw colour, raw alpha, raw cloud depth,
  paired depth/colour alignment, selected upscale neighbour and scene-depth
  rejection. `cloud_atmosphere_volume.fsh` exposes current raymarch, sampled
  history and per-pixel history rejection state. Adding duplicate visual modes
  would create more code without identifying a culprit.
- Prior full-resolution/history-off A/Bs established only that the old severe
  tower seams and density checker pattern survived both switches. They do not
  prove the current post-topology square-edge report is absent, but they do rule
  out blindly reverting the accepted composite weighting or simply disabling
  history as a general morphology fix.
- The next diagnostic will therefore read the already-existing boundaries
  numerically. It must associate frame-to-frame hashes/deltas with stable input,
  camera, material-offset and weather-map signatures and separately report raw
  raymarch, temporal output/history and selected-neighbour reconstruction. No
  temporal or upscale formula changes are authorised until those logs identify
  which boundary introduces the instability.

## Iteration 160 - rewind localises what is already fixed before new instrumentation

- The investigation history was retraced again before changing renderer code.
  This found two previously measured temporal defects that are already fixed in
  the current tree and must not be rediscovered through shader tuning. Iterations
  135--136 proved that Hermite wind tangents plus wind extrapolation made frozen
  authoritative centres move forward and reset; current
  `ClientCloudFieldCache.PresentationTrack` uses non-overshooting authoritative
  centre presentation. Iterations 142--143 proved that
  `WindVec * WorldTime` moved the material domain through a stationary envelope;
  current production uses UUID-matched `MaterialOffset` and
  `MaterialFrameDelta`, with a deterministic sandbox guarding the old formula.
- The present plateau captures independently retain bit-identical weather and
  cumulus-stage maps, zero material motion under freeze, and stable topology.
  Therefore another position interpolator, noise-time removal, blur, history
  disable, or morphology multiplier would be a blind duplicate rather than a
  causal correction.
- Source review also reconfirmed a separate, still-open contract noted in
  Iteration 143: temporal reprojection samples depth at the reprojected previous
  UV but compares it directly with the current frame's projected cloud depth.
  That is a coordinate-space mismatch, but source proof alone does not establish
  its contribution to the reported blocks or edge churn. The existing
  `HISTORY_REJECTION` view exposes the relevant per-pixel states and will be
  measured before that equation changes.
- The next source group is diagnostic only. It will asynchronously read the
  existing low-resolution raymarch colour/depth target and the already-composited
  main colour target through a fence-gated PBO. Reports will include exact
  weather, camera/matrix, material, lighting, target, quality, debug-mode and
  history metadata; quantised hashes; colour/depth pairing; premultiplied-alpha
  violations; frame-to-frame alpha/luminance/depth deltas; macro versus
  high-frequency churn; reconstruction-grid gradients; and the history-rejection
  state histogram. Readback and analysis are explicitly on-demand and must never
  block the render thread.
- No rendered formula, shader parameter, density profile, light response,
  precipitation footprint, temporal weight or composite selection rule is
  authorised in this instrumentation group. The diagnostic must compile, pass a
  deterministic CPU sandbox, restore all touched pixel-pack/texture state, close
  every PBO/fence on lifecycle reset, and produce runtime logs before a visual
  correction is considered.

## Iteration 161 - first diagnostic compile exposes a self-check accessor typo

- The diagnostic-only implementation added one on-demand PBO transfer for the
  existing cloud RGBA16F target, cloud depth and detached scene depth. Its worker
  analysis emulates the current production composite, records stable/dynamic
  signatures and hashes, and measures raw/reconstructed deltas without another
  draw. A deterministic sandbox supplies an identical pair and one known alpha
  perturbation.
- The first `compileJava testClasses volumetricStabilityDiagnosticsSandbox`
  attempt failed before packaging. Six errors all came from the same mechanical
  typo in that sandbox path: `FrameDigest` owns its `PairStats` through
  `summary().pair()`, while the assertions called a nonexistent direct
  `pair()` accessor. No rendering API, shader signature or production equation
  failed compilation.
- The next isolated correction changes only those six accessor chains to
  `summary().pair()` and repeats the identical Gradle group. No diagnostic
  metric or rendered behavior is being adjusted in response to this failure.
- The repeated group passed: `compileJava`, `testClasses` and
  `volumetricStabilityDiagnosticsSandbox` completed successfully, and the
  sandbox reported `Volumetric stability diagnostics self-check passed` for an
  identical raw/reconstructed pair plus a known alpha perturbation. The only
  compiler output was the repository's existing mixin/deprecation warnings.
- This is programmatic acceptance of the CPU analyzer only. PBO format,
  detached-scene-depth transfer, fence lifecycle, shader/CPU composite parity
  and runtime cost still require a native client run. A separate read-only code
  review is in progress before packaging so defects in the diagnostic cannot be
  misreported as cloud instability.

## Iteration 162 - rewind rejects the first stability probe before runtime use

- The complete investigation sequence was retraced before deploying the new
  diagnostic. This reconfirmed that the authoritative lifecycle, rigid group
  motion, material advection and stable weather/stage maps have already been
  corrected and measured. No morphology, density, erosion, lighting, temporal
  or composite equation will be changed until a downstream boundary is
  identified numerically.
- A read-only line-by-line review found that the first stability probe could not
  yet support that attribution. Its comparison signature excluded `WorldTime`
  even though precipitation and funnel density consume it, omitted several
  effective shader uniforms, and reread camera density after the hook had
  already updated the tracker. Its material-frame delta was likewise taken from
  the advection tracker rather than the exact value uploaded by the renderer.
- The CPU reconstruction always assumed the FINAL depth-guided branch, accepted
  scene depth that production rejects when it is attached, and used the main
  target dimensions instead of Forge's captured framebuffer viewport. It also
  measured premultiplied RGB as reconstructed straight luminance. These are
  confirmed diagnostic parity defects, not cloud-rendering defects.
- Acquisition was additionally gated on completion of the previous full-frame
  CPU analysis, so nominal frame pairs could be separated by an unknown number
  of rendered frames. At high resolution, retained input/output arrays could
  exceed 250 MiB. Finally, a run with zero comparable pairs was formatted with
  zero RMS values, which could be mistaken for perfect stability.
- The next correction remains instrumentation-only. The renderer will publish
  an immutable snapshot of the exact draw inputs; the diagnostic will record
  Forge's captured viewport/FBO and the effective composite branch, distinguish
  a full input signature from a deliberately FrameIndex-excluded comparison
  signature, report temporal gaps, enforce a memory/resolution ceiling, use
  straight reconstructed luminance and mark zero-comparable-pair runs
  inconclusive. A bounded capture pipeline is required before runtime A/Bs.
- That instrumentation hardening is now implemented without changing a render
  equation. `VolumetricCloudRenderer.LastDrawInputs` fingerprints the exact 46
  non-sampler uniforms at upload time, including previous-frame matrices,
  camera density, material delta, precipitation, pretest controls and funnel
  payload. Its comparison fingerprint excludes FrameIndex and excludes
  WorldTime only when both precipitation and funnel branches are provably
  inactive. `CloudFieldCompositeRenderer.LastDrawInputs` records the effective
  mode, depth flags, detached depth provenance, Forge FBO and viewport.
- Acquisition now reserves a bounded batch of PBOs, dispatches the requested
  frames on consecutive hooks, and delays mapping/CPU analysis until the batch
  is complete. The batch is rejected above 128 MiB instead of risking an
  unbounded 4K heap/VRAM spike. Every fence and buffer is released on failure or
  lifecycle shutdown, and allocation errors are checked immediately.
- CPU emulation now follows the actually drawn FINAL/SPATIAL branch: plain
  bilinear when depth composite is disabled, depth-guided neighbour selection
  plus the final fixed `GL_LEQUAL` test when detached depth is available, and
  explicitly unavailable when a debug branch or unattached destination depth
  cannot be reproduced. Reconstructed luminance divides premultiplied RGB by
  alpha exactly like the shader. Scene-depth content changes and frame gaps make
  a pair incomparable; a run with no comparable pair reports `n/a` and
  `inconclusive_no_comparable_pairs`.
- `compileJava`, `testClasses` and
  `volumetricStabilityDiagnosticsSandbox` passed. The sandbox now covers exact
  depth rejection, the depth-off bilinear branch, premultiplied-to-straight
  luminance, rejection of non-consecutive captures, a known raw/reconstructed
  alpha change, and the zero-comparable-pair report. Existing mixin and
  deprecation warnings are unchanged. Runtime PBO/fence behaviour and the
  actual A/B attribution remain unaccepted until the native client logs them.
- The complete validation group then passed `compileJava`,
  `processResources`, `cloudFieldSandbox`, `test` and `build`. Through `check`,
  the structured-topology, rigid-motion, material-advection and stability
  sandboxes all passed; reobfuscation and packaging also completed. The fresh
  JAR SHA-256 is
  `173459F0652370D7F7C0BDDD46763F8DE540745AE7B7FF1E15EDA2E688E51F68`.
  `git diff --check` reports no whitespace error (only the worktree's existing
  LF-to-CRLF notices). The native visual instance still contains the older
  `AC08AEC...` JAR and must receive this exact fresh artifact before any runtime
  result is accepted.

## Iteration 163 - runtime A/B localises stationary churn to the history-enabled jitter path

- The hardened diagnostic JAR was deployed to the native-only instance and its
  SHA-256 was rechecked as
  `173459F0652370D7F7C0BDDD46763F8DE540745AE7B7FF1E15EDA2E688E51F68`.
  The instance contained PA, Architectury, Cool Rain and Gaboulibs only; startup
  selected the native PA backend and produced no new mixin or shader error.
- The first history-off run used a frozen, mature twelve-member
  `cumulus_congestus`. Across eight consecutive hooks, weather, scene depth,
  low-resolution colour/alpha/depth/macro hashes and reconstructed
  alpha/selected-neighbour hashes were unchanged. These hashes are quantised,
  so this proves stability to at least `1/4095` for colour/alpha and `1/65535`
  for depth rather than literal float bit equality. The weather map remained
  `989f8661e2cc479f`, the scene-depth hash remained
  `71663704a0f55325`, and the material offset/delta remained fixed.
- That first region then reached its normal lifetime boundary. The command
  `/pa cloud freeze true` was confirmed to pause drift only, not lifecycle;
  its disappearance after roughly 12000 ticks is therefore expected and is
  not a renderer regression. A new group `e6d91681...` was created. Its list
  entry reported twelve clusters, `growth=1.00`, `decay=0.00`, density `0.79`
  and coverage `0.71`. The player was explicitly verified as
  `playerGameType=3` at `[0.5,330.0,-399.5]`; renderer status retained
  `camY=331.6`, removing the falling-camera confound from the first setup.
- Runs 2 and 3 measured that same camera/cloud with history enabled. The weather
  hash (`98e3cbe691a1ac3f`), scene-depth hash, material offset/delta, quality,
  viewport and step scale stayed fixed. In production `FINAL`, all eight
  colour/alpha/depth/macro and reconstructed hashes differed and active
  low-resolution pixels ranged from 2056 to 2070. In `CURRENT`, the same
  hashes also differed on every frame and active pixels ranged from 2048 to
  2058. Source inspection ties this branch to `jitterFrame=FrameIndex` only
  when history is valid; the current ray origin is then displaced by up to the
  2.5-block exterior fine step.
- Run 4 measured the frozen production history through
  `HISTORY_REJECTION`. Summed over eight frames, 16018 current cloud hits were
  accepted, 414 had missing history depth, 366 failed transmittance confidence,
  zero failed the depth-confidence threshold and 128 stale-history pixels
  occurred on current misses. Thus 95.4% of current material hits accepted
  history; widespread rejection is not the cause of the stationary churn.
  This stationary test does not validate camera-motion depth reprojection.
- Run 5 disabled history again without changing the second cloud or camera.
  All eight frames retained exactly the same quantised alpha, depth, macro,
  reconstructed-alpha and selected-neighbour hashes, with exactly 2053 active
  pixels each time. The quantised colour hash alternated between two values
  while `WorldTime` and resolved visual wind remained live, so colour stability
  is not yet accepted as a controlled result. Alpha/silhouette stability is
  nevertheless isolated to the history-off branch on the same geometry.
- Formal pair counts remained zero because the renderer correctly includes
  `WorldTime` when `maxPrecipitation` is about `0.0728`, and the resolved visual
  wind also changes despite frozen field drift. The changing hashes in the
  history-on runs therefore establish an observational A/B, not a controlled
  RMS value. The next change remains diagnostic-only: report observational
  frame deltas separately from strict controlled deltas, so `CURRENT` and
  `FINAL` variance reduction can be compared numerically without weakening the
  input signature. No jitter amplitude, history blend, depth equation,
  morphology, lighting or composite formula is changed yet.
- The client was saved and closed through a PID-targeted `WM_CLOSE`. `Stopping!`,
  overworld/Nether/End saves and `All dimensions are saved` were confirmed.

## Iteration 164 - strict and observational temporal metrics are separated

- The first runtime batch correctly refused every strict pair because
  `WorldTime` can animate precipitation and `WindVec` changed. Hash differences
  alone cannot quantify whether FINAL history reduces CURRENT jitter, and
  weakening the existing comparison signature would mislabel uncontrolled
  frames as controlled.
- `VolumetricCloudRenderer.LastDrawInputs` now publishes a third, explicit
  observation signature. It retains projection/view/previous-view matrices,
  camera, weather domain and slab, weather precipitation strength, lighting,
  material offset/delta, quality and step budget, scene-depth controls, history
  state/blend, debug mode, tuning and funnel payload. It excludes only
  `FrameIndex`, `WorldTime` and `WindVec`; texture-history content is necessarily
  dynamic and is called out in the report.
- `VolumetricStabilityDiagnostics` preserves the original strict `pair` result
  and adds `observedPair` rather than reusing or weakening it. Observational
  deltas still require consecutive hook/shader frames, identical observation
  signatures, equal buffer dimensions and stable scene-depth contents. Reports
  now aggregate strict and observational raw/reconstructed alpha RMS separately
  and print an explicit non-controlled caveat.
- The deterministic self-check now proves: identical strict/observational pairs
  remain zero; a known alpha perturbation crosses both the raw and reconstructed
  boundaries; skipped frames invalidate both paths; and a changed strict
  signature with an unchanged observation signature yields zero strict pairs,
  one labelled observational pair and retains the
  `inconclusive_no_comparable_pairs` strict conclusion.
- `compileJava`, `testClasses` and
  `volumetricStabilityDiagnosticsSandbox` passed. The complete group then passed
  `compileJava`, `processResources`, `cloudFieldSandbox`, `test`, every sandbox
  reached through `check`, reobfuscation, packaging and `build`. Existing mixin
  and deprecation warnings were unchanged.
- A separate read-only call trace confirmed why frozen PA fields still uploaded
  changing visual wind. `resolveVisualWind()` falls through to regional
  `ForecastOrchestrator` wind whenever the average field wind equals zero,
  despite its own contract reserving that fallback for the absence of fields
  and cells. The twelve frozen PA_CLUSTER members all carried exact zero wind,
  while runtime uniforms remained non-zero and variable. The next isolated
  production correction will preserve exact zero as authoritative whenever a
  field or cell source exists; regional fallback will remain only for truly
  empty source lists.
- The approximately `0.073` precipitation on `cumulus_congestus` is not a random
  leak: the registry explicitly classifies congestus as precipitating and the
  backend derives its low intensity from density, coverage and storm potential.
  That value will not be removed. It animates rain-shaft noise and prevents
  WorldTime from being excluded from strict comparisons, so a non-precipitating
  cumulus will be used for the fully controlled temporal run.

## Iteration 165 - authoritative calm wind and footprint-weighted stability telemetry

- The source of the changing wind uniform from Iteration 163 was confirmed in
  `VolumetricCloudRenderHook.resolveVisualWind()`. The old branch accepted an
  averaged field/cell wind only when its vector magnitude was non-zero. A real
  calm field, or several authoritative vectors that cancel, therefore fell
  through to live regional forecast wind. This contradicted the method's own
  contract and made a frozen field's material/rain direction change while its
  geometry stayed fixed.
- Wind selection now follows source ownership rather than magnitude. A non-empty
  rendered field list always supplies the average field wind, including exact
  zero; cells do the same when fields are absent. Regional forecast wind remains
  the fallback only when neither rendered fields nor rendered cells exist. This
  is an isolated production correction; precipitation, morphology, density,
  erosion, lighting, temporal and composite equations remain unchanged.
- The observational uniform signature was tightened after read-only review.
  It now controls `WindVec` and excludes only `FrameIndex` and `WorldTime` from
  scalar uniforms. Reports state separately that the changing history colour
  and depth sampler contents cannot be fingerprinted; other sampler contents
  are assumed immutable during a capture.
- Raw and reconstructed alpha deltas now include an active-union RMS over pixels
  where either frame exceeds alpha `0.002`. Aggregate values are sample-weighted
  across that union, so roughly 2,000 cloud pixels cannot be diluted by more
  than 500,000 empty target pixels or by an empty-sky pair. The report includes
  the contributing sample count. Self-checks cover identical and perturbed
  active unions, independent rejection of a changed observation signature, and
  `n/a` active-union output for an empty sky.
- A separate backend-contract review found no native-test confound: ownership is
  exclusive, native hooks/whiteout/density are registered only without Simple
  Clouds, and the existing native instance contains only Project Atmosphere,
  Architectury, Cool Rain and Gaboulibs. The generic atmospheric fog handler is
  not a second cloud backend.
- Targeted `compileJava`, `testClasses` and
  `volumetricStabilityDiagnosticsSandbox` passed after the telemetry changes.
  The complete required group then passed `compileJava`, `processResources`,
  `cloudFieldSandbox`, `test`, every sandbox reached by `check`, reobfuscation,
  packaging and `build`. Existing mixin/deprecation warnings are unchanged.
  `git diff --check` reports no whitespace errors (only existing LF-to-CRLF
  notices). The fresh JAR SHA-256 is
  `32272445186258D02872A57416DDE27A7C2A4D6C972E48E05EC14C3E54AE29C7`.
- Runtime acceptance is deliberately still open. The native instance still has
  the older `173459F...` artifact. The next run must deploy the exact new hash,
  use a non-precipitating `cumulus_humilis`, prove `WindVec=0` remains stable,
  and collect controlled CURRENT/FINAL/history-off active-union RMS. No global
  keyboard automation will be used; commands will go only through the verified
  PID/window-targeted Win32 helper.

## Iteration 166 - controlled GPU A/B identifies animated search-phase misses

- The exact `32272445186258D02872A57416DDE27A7C2A4D6C972E48E05EC14C3E54AE29C7`
  JAR was deployed and re-hashed in the native-only instance. Startup contained
  Project Atmosphere, Architectury, Cool Rain and Gaboulibs only, selected the
  native PA service, registered the volumetric shaders and reported no new
  shader/mixin failure. Every command was sent to Minecraft PID `30512` through
  the verified window-targeted `PostMessage` helper; no global keyboard input
  was used.
- The old restored congestus immediately uploaded exact zero field wind, then a
  controlled non-precipitating `cumulus_humilis` was created after movement was
  frozen. Its single saved group had three clusters, `growth=1.00`,
  `decay=0.00`, density `0.58`, coverage `0.59` and `maxPrecipitation=0`. The
  spectator camera remained at `[0.5,270.0,-299.5]`; noon and weather cycles
  were disabled. Across all captures the weather fingerprint stayed
  `66f6b054a9d2372d`, `WindVec`, material offset and material delta stayed exact
  zero, scene depth stayed `vanilla_main`, and `worldTimeAffectsDensity=false`.
  This accepts the Iteration 165 wind-ownership correction at runtime.
- A requested 16-frame Ultra capture was safely refused by the existing memory
  guard because its `140544000` bytes exceed the `134217728`-byte limit. The
  otherwise identical A/B therefore used eight frames. This is a diagnostic
  capacity result, not a renderer failure.
- With history OFF, every comparable raw and reconstructed alpha metric was
  exactly zero, including active-union RMS (`0/4470` raw samples and `0/8796`
  reconstructed samples). Six pairs were accepted; one pair was conservatively
  excluded when the comparison signature changed once. The output itself did
  not change on any accepted pair. The exact scalar responsible for that one
  signature transition remains unassigned and is not treated as stability.
- With history ON and raymarch view CURRENT, all seven pairs were controlled.
  Raw active-union alpha RMS was `0.11004368` (per-pair max `0.12621419`, 5267
  samples); depth-guided reconstruction reduced it only to `0.06909902` (max
  `0.08466210`, 10404 samples). With production FINAL, all seven pairs were
  again controlled, but the corresponding values were `0.11246244` and
  `0.07040040`. Temporal accumulation therefore does not attenuate the measured
  stationary silhouette churn; FINAL is effectively as unstable as CURRENT.
- HISTORY_REJECTION localised why. Across eight frames, 5899 current cloud hits
  accepted history, 82 had no matching history depth, zero failed depth
  confidence, zero failed transmittance confidence, and 77 pixels contained
  screen-space cloud history while the current jittered ray missed. Only about
  6..14 binary miss pixels per frame are enough to dominate the active-union
  RMS because the production history branch is entered only when
  `currentCloudHit` is true. On a current miss it writes empty output and never
  gives history a chance to integrate the edge.
- Source tracing connects those misses to the sampling phase, not cloud data.
  `jitterFrame=FrameIndex` animates the full exterior `fineStep` origin offset;
  the coarse search can therefore step over a thin edge on one frame and hit it
  on the next. The four-step bracket refinement runs only after a coarse sample
  already found material, so it cannot recover a completely skipped lobe.
  Weather topology, density coordinates and history acceptance stayed stable.
- Simply freezing all blue noise or retaining screen-space history on every miss
  is rejected as the next action: the former would regress the accepted
  Iteration 116 spatial de-correlation, while the latter can ghost during camera
  movement. The next isolated candidate will split the phase: a static
  screen-spatial full-step blue phase controls exterior material search, while
  the animated phase is used only inside an already-confirmed clear/material
  bracket for sub-step integration. No morphology, density, erosion, lighting,
  step count, history confidence or composite formula will change in that A/B.
- Minecraft was closed through a PID-targeted `WM_CLOSE`. `Stopping!`, player
  save, world save and `All dimensions are saved` were confirmed before source
  editing.

## Iteration 167 - static search phase candidate prepared for exact replay

- The isolated candidate changes only blue-noise phase ownership in
  `cloud_atmosphere_volume.fsh`. `searchBlue` is sampled from the original
  per-pixel blue-noise texture without a frame offset and controls the complete
  exterior origin phase. `integrationBlue` retains the existing FrameIndex
  offset/golden-ratio sequence and is consumed only after a deterministic coarse
  hit has established a clear/material bracket. History-off behavior is
  unchanged because both phases reduce to the same frame-zero sample there.
- This retains the full-step spatial de-correlation accepted in Iteration 116;
  it does not blur density, shrink the jitter distance, invent a history pixel
  on a miss or add a raymarch step. The causal prediction is narrow: history-on
  CURRENT/HISTORY_REJECTION occupancy churn must collapse while stable scene,
  weather, wind and performance remain unchanged. Interior radiance may still
  vary from animated integration and should then be reduced by FINAL history.
- The full required validation group passed `compileJava`, `processResources`,
  `cloudFieldSandbox`, `test`, all topology/motion/material/stability sandboxes,
  reobfuscation, packaging and `build`. `git diff --check` reports no whitespace
  errors, only the existing line-ending notices. The candidate JAR SHA-256 is
  `6E58D5E5336F5C725CF7835618FBF2320ACD4E918991F8C5DBD55236DF6E4BFA`.
  GLSL compilation and the predicted runtime delta were still unaccepted at
  this point, pending the native replay recorded below.
- The exact candidate hash was then deployed to the native-only instance and
  re-hashed before launch. Project Atmosphere selected its native service with
  Simple Clouds absent; the volume/composite programs and noise textures loaded,
  detached `vanilla_main` depth remained valid, and no PA shader, OpenGL or mixin
  error appeared. Commands were sent only to Minecraft PID `19268` through the
  window-targeted `PostMessage` helper.
- The restored baseline group reached the end of its saved lifecycle before the
  replay. It was not silently reused as an empty-sky sample. A fresh isolated
  `cumulus_humilis` was spawned at the origin and the spectator camera returned
  to `[0.5,270.0,-299.5]`. The replacement matured to `growth=1.00`,
  `decay=0.00`, density `0.58`, coverage `0.60`, five clusters and
  `maxPrecipitation=0`. This differs from Iteration 166's three-cluster group,
  so absolute GPU time and footprint size are not claimed as an exact scene A/B.
  The controlled renderer inputs did match the intended test contract: noon,
  camera density zero, Ultra `960x540` target into `1280x720`, weather fingerprint
  `7c82ae1fc62b2d9a`, zero uploaded wind, zero material offset/delta, detached
  `vanilla_main` depth and `worldTimeAffectsDensity=false`.
- The history-off control produced identical colour, alpha, cloud-depth, macro
  and reconstructed hashes in all eight captures. Its opaque comparison hash
  changed every frame despite the bit-identical outputs, so the diagnostic
  conservatively reported zero comparable pairs rather than inventing a metric.
  This exposes a separate observability gap: future reports need per-component
  signature deltas instead of one unlabelled hash. It is not counted as renderer
  instability or as proof that an unknown input was harmless.
- The causal HISTORY_REJECTION prediction passed. Across eight frames the new
  candidate recorded 9,255 current hits that accepted history, three missing
  history-depth samples, zero depth mismatches, zero transmittance mismatches and
  only one stale screen-space history pixel on a current miss. Iteration 166 had
  5,899 accepted hits, 82 missing depths and 77 stale current-miss pixels. The
  active footprint itself stayed within 1,156..1,158 low-resolution pixels. This
  localises the improvement to deterministic exterior occupancy rather than a
  blurred or reduced cloud.
- Production FINAL also passed once the fresh field and history had settled.
  Six consecutive controlled pairs measured raw active-union alpha RMS
  `0.00046817` (max `0.00054946`, 6,942 samples) and reconstructed active-union
  alpha RMS `0.00205809` (max `0.00494417`, 13,434 samples). One final pair was
  conservatively excluded when the comparison signature changed. The prior
  three-cluster baseline measured `0.11246244` raw and `0.07040040`
  reconstructed. Because the replacement field is not geometrically identical,
  these ratios are supporting evidence rather than an exact performance-style
  A/B; the rejection-state collapse is the direct causal acceptance criterion.
- Runtime volume GPU samples were approximately `0.66 ms` for the five-field
  candidate versus roughly `0.57 ms` for the earlier three-field baseline. The
  scenes differ and the candidate adds one blue-noise texture lookup, so no
  statistically valid performance delta is claimed. A moving-camera/ghosting
  validation and close-range visual confirmation remain open. Minecraft was
  closed through PID-targeted `WM_CLOSE`; `Stopping!`, player/world saves and
  `All dimensions are saved` were confirmed before further editing.

## Iteration 168 - rejected-pair signatures made causally attributable

- The Iteration 167 history-off replay produced bit-identical colour, alpha,
  cloud-depth, macro and reconstructed hashes while its aggregate ray-uniform
  comparison signature changed. Two independent read-only traces localised the
  opaque delta to `LastDrawInputs.comparisonUniformSignature()`: the aggregate
  always included `PrevViewProjMat` and `MaterialFrameDelta` even when
  `HistoryValid=0`, although the shader cannot consume either inactive input.
  The aggregate also offered no field-level evidence when any genuinely active
  value changed. No morphology or shader equation was changed on this basis.
- The strict and observational signatures now include previous view-projection
  and per-frame material delta only when temporal history is active. The exact
  audit signature still records them unconditionally. This is a diagnostic
  contract correction, not a relaxation of renderer controls: current camera
  matrices, camera position/density, weather uniforms, every lighting input,
  resolved wind, material offset, quality flags and funnel data remain hashed.
- On-demand captures now retain separate component signatures for projection,
  view rotation, both inverse matrices, active previous view-projection,
  camera position/density, weather uniforms, individual lighting groups, wind,
  material offset/delta, quality flags and funnels. An incomparable pair reports
  the exact changed names and before/after hashes instead of only
  `controlled_input_changed`. The detailed record is created only while a
  requested batch still needs frames; ordinary gameplay keeps the shared empty
  record and gains no per-frame diagnostic allocation.
- Branch self-checks prove that different previous matrices/material deltas
  collapse to the same inactive signature and remain distinguishable when
  history is active. Targeted `compileJava`, `testClasses` and
  `volumetricStabilityDiagnosticsSandbox` passed, including the existing
  reconstruction, memory-guard and controlled/observational comparison checks.
  `git diff --check` reports no whitespace errors, only the repository's
  existing LF-to-CRLF notices.
- Runtime attribution is deliberately pending. The next native replay will use
  a frozen non-precipitating cloud and history off to prove whether the prior
  false invalidations disappear or, if an active value still changes, print its
  exact component. Only after that logged result will the moving-camera
  previous-depth-space hypothesis be instrumented. Visual validation will use
  60--120-block side views plus close under/inside views; the 300-block camera
  remains a numerical stability rig only.
- The complete required validation subsequently passed `compileJava`,
  `processResources`, `cloudFieldSandbox`, `test`, topology, regional-motion,
  material-advection and volumetric-stability sandboxes, reobfuscation,
  packaging and `build`. The packaged JAR SHA-256 is
  `A0D5BBB98299A282F119B3AA4DBF52F6628182E49FCDEB7B3676E46C702F822E`.
  An independent read-only review found no blocking constructor, publication,
  branch or allocation defect. It did identify non-blocking future hardening:
  attribute independent scene-depth-size changes, mirror the exact
  history-weight/debug predicate when history is valid, and broaden the
  attribution self-test. None affects the planned history-off replay, so they
  are recorded rather than mixed into the candidate before runtime evidence.

## Iteration 169 - runtime attribution identifies a subnormal camera-density tail

- The exact diagnostic JAR
  `A0D5BBB98299A282F119B3AA4DBF52F6628182E49FCDEB7B3676E46C702F822E`
  was deployed and re-hashed in the native-only instance. Forge loaded Project
  Atmosphere, Architectury, Cool Rain and Gaboulibs only, selected the native PA
  service with Simple Clouds absent, compiled the volumetric programs, baked all
  three noise textures and resolved detached `vanilla_main` depth without a new
  shader, OpenGL or mixin error. Commands targeted Minecraft PID `15192` only.
- The first setup was rejected from the logs: `/weather clear` was intercepted
  as a PA clear-cloud spawn and created a `vapor_cluster` around the camera,
  forcing `cameraDensity=1` and the dense-camera 0.50 target. Both saved regions
  and nine runtime fields were cleared. A single `cumulus_humilis` was then
  spawned at the origin, movement frozen, and the spectator camera returned to
  `[0.5,270.0,-299.5]`. The accepted field had four clusters centred near
  `[8.4,255.4,13.5]`, radius about `63`, zero precipitation and exact zero
  visual wind/material offset/delta. Ultra rendered `960x540` into `1280x720`.
- In the eight-frame history-off capture, weather
  (`4e58b78f88651790`), current matrices, camera position, lighting, advection,
  quality, funnels, composite inputs and scene depth stayed fixed. Every frame
  had identical low-resolution colour/alpha/depth/macro hashes
  (`7d48c4f202ff3e48/d630b8397e459827/d28cf8cf9c6e31d0/8ed5c20f998467c5`),
  exactly 853 paired active pixels, and identical reconstructed alpha/selected
  hashes (`5b1651341427d559/8030c7129f43ac96`). The rendered result was therefore
  stationary, but all seven pairs were correctly refused as uncontrolled.
- Component attribution names the sole changing input:
  `CameraCloudDensity`. Its raw float bits stepped through positive subnormal
  values such as `0x3a -> 0x39 -> 0x37 ...`, although decimal status rounded it
  to `0.000`. The camera was more than 300 blocks from a roughly 63-block field,
  so `ClientCloudVisualDensity.densityAt()` returns exact zero; the residue came
  from the earlier inside-cloud sample. `CameraCloudDensityTracker.update()`
  applies an exponential release and never settles an exact-zero target, so the
  state eventually decays one subnormal representation at a time.
- This residue is below every production decision boundary: whiteout starts at
  `0.03`, dense-target release and history-confidence attenuation at `0.04`,
  and the shader's inside-cloud branch at `0.08`. It cannot explain the visible
  cloud instability and did not change a captured pixel, but it prevents a
  genuinely controlled diagnostic and needlessly uploads denormals. The root
  correction will make an exact-zero raw target settle to exact zero once the
  smoothed release is far below all observable thresholds, with a deterministic
  regression test. It will not remove smoothing at a real cloud boundary.
- Minecraft was closed through PID-targeted `WM_CLOSE`; `Stopping!`, player and
  world saves, all three dimensions and `All dimensions are saved` were
  confirmed before source editing.
- The isolated tracker candidate retains the original fast-attack/slow-release
  interpolation, then canonicalises only an exact-zero raw target whose released
  value is at or below `1e-4`. A non-zero raw sample, even below that epsilon,
  is explicitly retained; the diagnostic signature is not weakened. The
  sandbox checks canonical `+0`, the exact runtime `0x3a` subnormal, retention
  above the settle threshold and retention of a small non-zero raw sample.
- Targeted compilation and the volumetric-stability sandbox passed, followed by
  the complete required `compileJava processResources cloudFieldSandbox test
  build` group. Topology, motion, material-advection and stability sandboxes,
  reobfuscation and packaging all passed; existing warnings are unchanged and
  `git diff --check` is clean apart from line-ending notices. Candidate JAR
  SHA-256: `09C45AB4790A2F29958BBA19FF3B69AC2ECF740D2A85FA14497CAF7EE3A5AF88`.
  Runtime acceptance used the same history-off numerical replay described below.
- The exact candidate JAR was deployed and re-hashed in the native-only instance,
  then launched as Minecraft PID `54896`. Project Atmosphere selected its native
  backend with Simple Clouds absent; the volume, composite, noise and detached
  `vanilla_main` depth resources loaded without a PA shader, OpenGL or mixin
  error. The saved mature `cumulus_humilis` retained UUID
  `765fffdf-44e5-4159-97f0-33c2cd2bfd2b`, four clusters, centre approximately
  `[8.4,255.4,13.5]`, radius `65.9`, density `0.56`, coverage `0.60`, full
  growth and zero precipitation.
- The camera was first placed inside the field, where runtime telemetry proved
  `cameraDensity=1.000` and the intended dense-camera resolution scale `0.50`.
  It was then moved to `[0.5,270.0,-299.5]` and allowed nine seconds to release.
  Telemetry reached canonical `cameraDensity=0.000` and the normal `0.75`
  resolution scale, exercising the actual non-zero-to-clear path rather than a
  process-start zero.
- The subsequent eight-frame capture produced seven controlled, reconstructed
  and observed pairs. The comparison signature remained
  `e6fdd0b2d3fc9f1b`, the ray-comparable signature remained
  `7665afccf92290bd`, the camera-density component remained
  `fdea163b9834838e`, and the weather fingerprint remained
  `4e58b78f88651790`. Every low-resolution colour, alpha, depth and macro hash
  was stable. Raw alpha RMS, raw active-union alpha RMS, reconstructed alpha RMS
  and reconstructed active-union alpha RMS were all exactly `0`; the active
  sample counts were `5,978` raw and `11,550` reconstructed. This causally
  accepts the exact-zero settling fix and restores controlled stationary tests;
  it does not claim a visual morphology improvement.
- Minecraft PID `54896` was closed through targeted `WM_CLOSE`. `Stopping!`,
  player/world saves, all dimensions and `All dimensions are saved` were
  confirmed. The next isolated investigation is the moving-camera temporal
  depth-space comparison; no production depth equation has yet been changed.

## Iteration 170 - instrument the temporal depth-space mismatch before correcting it

- The rewind found this defect was already isolated in Iterations 143 and 160,
  but deliberately left unfixed until a moving-camera measurement existed.
  `cloud_atmosphere_volume.fsh` samples `HistoryDepthSampler` at the reprojected
  previous-frame coordinate `prevUv`, then compares that stored previous-frame
  depth with `resultDepth` produced by `depthAt(relRepresentative)` through the
  current `CloudProjMat * ViewRotMat`. Source inspection therefore proves a
  coordinate-space mismatch; it does not yet prove how many live pixels it
  falsely accepts or rejects.
- The next candidate is diagnostic only. A `history_depth_space` raymarch view
  will evaluate the unchanged production comparison and, beside it, the
  previous-projection reference derived directly from the same `prevClip.z/w`
  that produced `prevUv`. Both paths will use the same existing tolerance so
  this A/B changes only the depth coordinate space. It will encode and log eight
  mutually exclusive states: history unavailable, projection off-screen,
  missing depth, both tests reject, current-space rejects/previous-space accepts,
  current-space accepts/previous-space rejects, both accept, and no current hit.
- This instrumentation will not alter `depthConfidence`, history weight,
  temporal blending, raymarch density, reconstruction or production output.
  The existing diagnostic-view contract freezes the last production target and
  its matching previous projection/camera/material state, so camera movement can
  be measured against a coherent history frame. Movement will be delivered only
  to the verified Minecraft PID/HWND through `PostMessage`; global keyboard
  injection and `SendKeys` remain prohibited.
- The diagnostic implementation exposes continuous current-space and
  previous-space confidence in the low-resolution target, plus explicit status
  values for unavailable, off-screen and missing-depth cases. CPU analysis
  reports named accept/reject combinations, confidence means/deltas and an
  aggregate independent of frame-pair comparability. A synthetic eight-pixel
  fixture covers every category, including the exact `0.5` acceptance boundary.
  Targeted `compileJava`, `processResources`, `testClasses` and
  `volumetricStabilityDiagnosticsSandbox` passed. The sandbox explicitly
  accepted the new decoder; runtime GLSL compilation and moving-camera counts
  remain pending.
- The complete required `compileJava processResources cloudFieldSandbox test
  build` group also passed, including topology, regional-motion,
  material-advection and volumetric-stability sandboxes, reobfuscation and
  packaging. `git diff --check` reports no whitespace error, only the existing
  line-ending notices. The diagnostic JAR SHA-256 is
  `FE1AAD54FE9920772C77DD33FED091E23AD4EA7C7FBB682455BA933CA76133F8`.
- A read-only implementation review found no Java/GLSL wiring blocker, but it
  identified two diagnostic-precision issues to correct before runtime. The
  production depth confidence is a continuous weight, so the report must call
  its `0.5` split “below/at-least-half” rather than imply a hard production
  accept/reject. The reprojected Z coordinate also needs an explicit NDC range
  check so a point outside the previous clip volume is not counted as an
  evaluated candidate after clamping. The review also noted pre-existing
  screen-space history reads on current misses in `FINAL`; that performance
  issue is recorded but will not be mixed into this causal depth experiment.
