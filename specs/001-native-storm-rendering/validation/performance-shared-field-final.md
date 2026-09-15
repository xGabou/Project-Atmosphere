# T199 - the shared field becomes FINAL

Branch `experiment/cloud-descriptor-k`, base `6046ea0` (T198). Nothing merged.
Basis, fixed by T198 and not re-decided here: 256x256x32 nodes
(`StormFieldGeometry.DEFAULT_XZ`, Y 32, RGBA16F, 16 MiB); R/B = the storm
body's exact threshold L and height01 at the node from the build-mode walk,
G = the conservative probe floor, A = the T196 density; every one of the four
light taps interpolates the envelope and evaluates the base noise, the detail
(taps 0-1) and the erosion at its own point (consumer bit 8 with bit 1), the
probe reads G `NEAREST` (bit 2) - production bits **11**; probe false-empty 0
and oracle underestimates 0; pockets 0.880 SIDE / 0.923 FAR; net SIDE 2.151x,
FAR 1.580x.

## 1. Campaign-only ledger (T199a)

Read on 11Sep2026 against `6046ea0`, no code change. Line numbers are those of
that commit. Baseline confirmed: `StormFieldGeometry.java:20` `DEFAULT_XZ =
256`; `run/` holds `t132-autorun.txt` and `t132-autorun-exit.txt` and no
`t19*-*.txt` marker (`run/t197-pockets/` is an ignored PNG output directory,
not a marker); the driver resets `stormFieldXZ` to `DEFAULT_XZ`,
`stormFieldForcedOnMonolith` to 0, the audit selectors to (0, 0) and the audit
bits to 1 at every campaign start (`StormT132AutoDriver.java:4346-4361`).

**Decision.** FINAL's baked consumer bits are **11** = light (1) + probe (2) +
exact-noise tap (8). Bit 4 - the T196 hybrid that put taps 0-1 back on the
exact walk - is dead in FINAL: with bit 8 every tap already evaluates the
noise, the detail and the erosion itself, so there is nothing left for the
hybrid to restore, and T198 section 4 showed it left the far taps biased.
`PaStormFieldPass` stays a live uniform in FINAL (the renderer uploads 1 for
the build draw and 0 for the timed draw, as the arms already do);
`PaStormFieldXZ` bakes to 256 (T197 closed the resolution line); the audit
selectors bake to 0 and the audit body stays behind `PA_LIGHT_AUDIT`.

### 1.1 Shader - `src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh`

| Control | Where | Column | Note |
|---|---|---|---|
| `uniform int PaStormFieldPass;` | `:279`; build branch `:7559` (`!= 0`), oracle branch `:7574` (`== 2`) | **promote** (values 0/1 live) / **campaign-only, keep** (value 2) | FINAL needs 0 and 1 every frame. The oracle (pass 2, the 27-lattice + 16-hash reference at `:7574-7633`, the only callers of `paStormFieldReferenceFloorAt` at `:7597` and `:7620`) is a campaign measurement; with the pass live it would compile into FINAL, so T199c puts it behind `PA_STORM_FIELD_ORACLE` on the two oracle hosts only. |
| `uniform int PaStormFieldEnabled;` bit 1 (light) | `:287`; tap selector `:6568` | **promote** (baked 11) | The four-tap cone reads the field. |
| bit 2 (probe) | probe branch `:8463` | **promote** (baked 11) | `paStormFieldMaterialPossible` `:6189` reads G `NEAREST`; the audit beside it is monolith-and-capture only. |
| bit 4 (T196 hybrid) | `:6569` `!((PaStormFieldEnabled & 4) != 0 && i < 2)` | **retire** | Dead at bits 11 (statically false once baked). Removed with its program in T199h. |
| bit 8 (T198 exact-noise tap) | `:6570` -> `paStormFieldLightDensityAt` `:6130`; audit twin `:6538` | **promote** (baked 11) | The correction itself. |
| `uniform sampler2D StormFieldSampler;` | `:313` | **promote** | Not a JSON sampler (T196 found the 13th JSON sampler crashes `ShaderInstance.apply`); bound manually, see 1.3. |
| `uniform int PaStormFieldXZ;` | `:323`; reads `:6074`, `:6093`, `:6201-6202`, `:7560-7563` | **promote as constant 256** | Already `const int PaStormFieldXZ = 256;` in FINAL through `leanFinalConstants`; the field arms keep it live only for the closed T197 sweep, so T199h bakes it there too. `StormFieldGeometry.supported()` stays. |
| `uniform int PaLightAuditVariant;` / `PaLightAuditOutput;` | `:339-340`; consumers `:6544-6553`, `:9622-9634` | **campaign-only, keep** (baked 0 in FINAL) | Only `t198_light_audit` declares them; everywhere else they are dead behind the missing define. |
| `#ifdef PA_LIGHT_AUDIT` body | accumulators `:554-570`, decomposition toggles `:5387-5394` (erosion, mip), `:5480-5487` (weather, morphology), `:5874-5877` (detail), tap audit `:6533-6566`, output select `:9618-9634` | **campaign-only, keep** | The retained far-tap diagnostic (T199g). Undefined in FINAL, so none of it is compiled in. |
| Field build helpers `paStormFieldVoxelXZ/Y` `:6073/:6077`, `paStormFieldCoord` `:6082`, `paStormFieldAtlasUv` `:6092`, `paStormFieldDensityAt` `:6107`, `paStormFieldNodeAt` `:6281`, `paStormFieldFloorFromWalk` `:6260`, `paStormFieldDilationBlocks` `:6250`, `paStormFieldNonStormPossible` `:6228` | as listed | **promote** | The node equation and both lookups. `paStormFieldDensityAt` (the T196 A-channel read) is still the bit-8-off path; it stays because the audit's variant 6 and `t196_field_both` use it. |
| `paStormFieldReferenceFloorAt` | `:6307` | **campaign-only, keep** | Oracle-only (pass 2). Goes behind `PA_STORM_FIELD_ORACLE` with the branch. |
| Probe counters `paStormFieldProbeFetches/ProvablyEmpty/Fallback` | `:807-809`, `:6194-6215`, `:10005-10007` | **campaign-only, keep** | Workload counters; the workload views are monolith-only already. |

### 1.2 `VolumetricCloudDebugConfig.java`

| Control | Where | Column | Note |
|---|---|---|---|
| `stormFieldForcedOnMonolith` / `setStormFieldForcedOnMonolith` | `:80-95` | **promote, re-purposed** | Today 0 means "the monolith reads no field", so every debug view renders a different image from FINAL once FINAL reads it. T199b makes 11 the monolith's default (`PRODUCTION_FIELD_BITS`) and keeps the setter as the campaign override. |
| `requestStormFieldSoundness` / `consumeStormFieldSoundnessRequest` | `:97-104` | **campaign-only, keep** | Drives the pass-2 oracle on an oracle host. |
| `stormFieldXZ` / `setStormFieldXZ` | `:113-121` | **retire the setter's sweep use** | Keep the getter (FINAL's `leanFinalEligible` compares it to `DEFAULT_XZ`); the only non-default callers are the T197 sweep (`StormT132AutoDriver.java:3410`, `:4858`, `:4871`, `:4941`, `:6156`), retired in T199h. |
| `lightAuditVariant` / `lightAuditOutput` / `setLightAudit` | `:129-142` | **campaign-only, keep** | Audit selectors. |
| `lightAuditFieldBits` / `setLightAuditFieldBits` | `:150-157` | **campaign-only, keep** | Which lookup the audit measures; T199g sets 11. |

### 1.3 `VolumetricCloudRenderer.java`

| Control | Where | Column | Note |
|---|---|---|---|
| `stormFieldTextureId` field, reset per frame | `:38`, `:468` | **promote** | Reset before every draw so a reload or a frame without a build never binds a stale id. |
| `lastRainFieldGpuMilliseconds` charged to `stormFieldProgram(program)` | `:360-372` | **promote** | `RAIN_FIELD_TIMER` is the field clock (no program keeps both fields). FINAL joins the predicate so the status line can carry the build cost. |
| Per-frame uniform reset `PaStormFieldPass=0`, `PaStormFieldEnabled=0`, `PaStormFieldXZ`, audit selectors | `:723-734` | **promote** (the field ones) / **campaign-only, keep** (audit selectors, harmless `safeGetUniform` dummies on FINAL) | T196 found uniform state persists across frames on a program. |
| Build block: `prepareStormFieldTarget`, bits selection (monolith forced / audit / program), soundness hook, build draw with pass 1, rebind of `cloudTarget`, `PaStormFieldEnabled = enabledBits` | `:740-773` | **promote** (the build) / **campaign-only, keep** (the monolith-forced and audit-bits branches `:744-750`, the soundness call `:751-753`) | Runs for FINAL once `stormFieldProgram()` says so. A null target must raise `LeanFinalProgramUnavailableException`, not render bits 11 against nothing (T199b). |
| `leanFinalEligible()` | `:872-885` | **promote, extend** | Adds `stormFieldXZ() == DEFAULT_XZ` and audit selectors 0, in step with `leanFinalConstants`. |
| `bindManualTextures` -> `bind2dSampler("StormFieldSampler", RAIN_FIELD_TEXTURE_UNIT, ...)` | `:962-973` | **promote** | Shares unit 15 with the rain field; zero id skipped. |
| `stormFieldProgram()` | `:1019-1023` | **promote, extend** | `program.stormFieldGeneration()` must return true for `LEAN_FINAL`. |
| `runStormFieldSoundnessOracle` | `:1033-1197` | **campaign-only, keep** | CPU-side count of the pass-2 readback. |

### 1.4 `VolumetricCloudRenderTargets.java`

| Control | Where | Column | Note |
|---|---|---|---|
| `stormFieldTarget`, `stormFieldTargetXZ`, `stormFieldTargetXZ()` | `:190-197` | **promote** | |
| `prepareStormFieldTarget(xz)` - allocates `createHalfFloatMap(atlasWidth, atlasHeight)`, logs `T197_FIELD_ALLOC` | `:206-222` | **promote** (tag renamed `T199_FIELD_ALLOC` in T199d) | Re-allocates only when `xz` changes; with XZ constant it allocates once per lifetime. |
| `prepareStormFieldOracleTarget` / `releaseStormFieldOracleTarget` | `:226-243` | **campaign-only, keep** | Float target for the pass-2 pair. |
| Release in `shutdown()` | `:429-433` | **promote** | Already destroys the atlas with the other targets. |

### 1.5 `CoreCostDiagnosticProgram.java`

| Control | Where | Column | Note |
|---|---|---|---|
| `T196_GENERATE_ONLY`, `T196_FIELD_LIGHT`, `T196_FIELD_PROBE` | `:687-693` | **campaign-only, keep** unless T199f does not use them, then **retire** in T199h | Single-consumer arms; T199f's timing matrix needs only the nofield control, FINAL and `t198_field_both_exact`. |
| `T196_FIELD_BOTH` | `:696` | **campaign-only, keep** | The T196 control and an oracle host. |
| `T196_FIELD_HYBRID` | `:699`; `stormFieldEnabledBits()` case 7 `:814` | **retire** | With bit 4. |
| `T198_LIGHT_AUDIT` | `:709`; `lightAuditCapture()` `:865` | **campaign-only, keep** | The retained diagnostic. |
| `T198_FIELD_LIGHT_EXACT` | `:715`; case 9 `:815` | **retire** | Superseded by FINAL; the audit measures light-only through its live bits. |
| `T198_FIELD_BOTH_EXACT` | `:718`; case 11 `:816` | **campaign-only, keep** | The T198 comparator and the oracle host that shares FINAL's node equation. |
| `stormFieldGeneration()` | `:801-806` | **promote, extend** | Add `LEAN_FINAL`. |
| `stormFieldEnabledBits()` | `:809-817` | **promote, extend** | `LEAN_FINAL -> 11`. |
| `shaderName()` `LEAN_FINAL -> cloud_atmosphere_volume_final` | `:880` | **promote** | Unchanged. |

### 1.6 `build.gradle`

| Control | Where | Column | Note |
|---|---|---|---|
| `leanFinalConstants` row `PaStormFieldPass = 0` | `:306` | **promote by removal** | Removed from the map so FINAL declares the uniform and the generated JSON keeps it (`leanFinalRemovedUniforms` `:329` derives from the map). |
| row `PaStormFieldEnabled = 0` | `:307` | **promote as 11** | |
| row `PaStormFieldXZ = 256` | `:311` | **promote** | Unchanged. |
| rows `PaLightAuditVariant = 0`, `PaLightAuditOutput = 0` | `:314-315` | **campaign-only, keep** (baked 0) | |
| Comment block T196/T197/T198 | `:304-313` | **promote, reword** | Says the field is not in FINAL; will say it is. |
| `t196_generate_only`, `t196_field_light`, `t196_field_probe` | `:955-968` | see 1.5 | |
| `t196_field_both` | `:970-975` | **campaign-only, keep** (+ `PA_STORM_FIELD_ORACLE 1`) | |
| `t196_field_hybrid` | `:977-981` | **retire** | |
| `t198_light_audit` | `:987-993` | **campaign-only, keep** | Keeps `PaStormFieldEnabled` live so one program audits any lookup. |
| `t198_field_light_exact` | `:998-1001` | **retire** | |
| `t198_field_both_exact` | `:1003-1006` | **campaign-only, keep** (+ `PA_STORM_FIELD_ORACLE 1`) | |
| `keepUniforms: ['PaStormFieldPass', 'PaStormFieldXZ']` on every field arm | `:957-1005` | **`PaStormFieldXZ` retired from keepUniforms** in T199h | Pass stays live; XZ bakes to 256 everywhere. |
| New `t199_final_nofield` | - | **campaign-only, new** (T199f) | The pre-field FINAL reference: lean specialization with `PaStormFieldEnabled = 0`, `PaStormFieldPass = 0`. |

### 1.7 `StormT132AutoDriver.java` and registry

| Control | Where | Column | Note |
|---|---|---|---|
| T196 phase: `T196_MARKER` `:296`, `T196_POSES` `:1629`, `t196Run` `:1631`, `T196_ARMS` `:1649`, `T196_OPTIMIZATION_ARMS` `:1675`, `T196_IMAGE_ARMS` `:1688`, soundness phases `:4847-4863` | as listed | **campaign-only, keep** | Historical campaign; its rows stay selectable. |
| T197 phase: `T197_MARKER` `:1706`, `T197_XZ = {256, 384, 512}` `:1711`, `t197Run` `:1713`, `T197_ARMS` `:1731`, `T197_IMAGE_ARMS` `:1778`, `T197_POCKET_BASELINE` `:1795`, the XZ uploads `:3410`, `:4858`, `:4871`, `:4941`, `:6156` | as listed | **retire the sweep** (T199h) | Resolution line closed by T197; `t197ReportPockets`-style pocket diagnostics are reused by T198/T199 and stay. |
| T198 phase: `T198_MARKER` `:1810`, `T198_POSES` `:1812`, `t198Run` `:1814`, `T198_COUNTER_BITS = {0, 3, 11}` `:1824`, `T198_CORRECTION_ARMS` `:1832`, `T198_ARMS` `:1835`, `T198_IMAGE_ARMS` `:1895`, `lightFieldBiasRunRequested` `:1927`, audit captures `:6163-6165`, dispatch sites `:3117`, `:3375`, `:3489`, `:3610`, `:3862`, `:4352`, `:4430`, `:4444` | as listed | **campaign-only, keep**; `t199Run` is wired at every `t198Run` dispatch site (T199f) | |
| Campaign-start resets `:4346-4361` | | **campaign-only, keep** | `setStormFieldForcedOnMonolith(0)` will mean "production bits" after T199b - the reset semantics are unchanged in effect. |
| `StormCampaignRegistry.java` rows T196 `:267`, T197 `:270`, T198 `:273` | | **keep** | The wiring check reads the newest row; T199's row is appended (T199f). |
| `VolumetricCloudShaders.java` campaign-scoped loading `:86-121` (`productionAlwaysLoaded=2`: the monolith and FINAL) | | **promote, unchanged** | FINAL is always loaded; the field programs load only under an active campaign. |

### 1.8 Sandbox - `src/test/java/.../StormVolumetricGeometrySandbox.java`

| Control | Where | Column | Note |
|---|---|---|---|
| `LEAN_FINAL_SPECIALIZATIONS` | `:6484-6504` | **promote, extend** | Lacks the T188+ rows; T199c adds `PaStormFieldEnabled = 11`, `PaStormFieldXZ = 256`, both audit selectors. |
| T196 invariant `validateT196StormFieldArms` "FINAL carries no field" | `:662-663`, `:832-870` | **invert** (T199c) | FINAL now carries the field; the arms still keep `PaStormFieldPass` live. Its `PaStormFieldXZ` live check `:861-862` goes with T199h. |
| T197 `validateT197FieldResolution` (`T197_FIELD_GEOMETRY`) | `:665`, `:744-800` | **keep the geometry facts, invert the XZ-live clause** (T199h) | The 16 MiB / nesting facts still hold; its assertion at `:765` that every field arm keeps `PaStormFieldXZ` live is the sweep's contract and goes with the sweep. |
| T198 `validateT198LightAudit` | `:666-730` | **keep** | FINAL defines no audit, selectors baked 0. |

### 1.9 What the ledger implies for the rest of T199

- Promote (T199b): six edits - the `leanFinalConstants` row pair, two enum
  methods, the `stormFieldProgram()` reach, the monolith default, the
  eligibility predicate, the null-target fallback.
- Compile out (T199c): one define (`PA_STORM_FIELD_ORACLE`) on two arms; the
  audit is already out.
- Retire (T199h): bit 4 and its program, `t198_field_light_exact`, the T197
  sweep and the live XZ on the arms; nothing under `validation/` moves.

## 2. FINAL wiring and the generated-source proof (T199b, T199c)

### 2.1 Wiring (T199b)

Seven edits, no shader change:

1. `StormFieldGeometry.PRODUCTION_CONSUMER_BITS = 1 | 2 | 8` - one home for
   the value, Minecraft-free so the sandbox reads it headless. (The brief
   named `VolumetricCloudDebugConfig`; the geometry class is where the other
   field constants already live.)
2. `build.gradle` `leanFinalConstants`: `PaStormFieldEnabled = 11`;
   **`PaStormFieldPass` removed from the map**, so FINAL declares it live and
   the generated JSON keeps it (`leanFinalRemovedUniforms` derives from the
   map). The map is the shared specialization, so every lean variant inherits
   the field unless it overrides the row - which is what keeps
   `t171_dup_a/b` byte-identical to FINAL (verified with `cmp`, IDENTICAL).
3. `CoreCostDiagnosticProgram.stormFieldEnabledBits()`: default is now
   `PRODUCTION_CONSUMER_BITS`; `DIAGNOSTIC_MONOLITH` and `T196_GENERATE_ONLY`
   are the explicit zeros, the T196/T198 single-lookup arms keep their values.
   `stormFieldGeneration()` is derived, not listed: `T196_GENERATE_ONLY || (not
   the monolith && bits != 0)` - a program that bakes consumers reads the atlas
   on its first frame and must never do so against one nobody built.
4. `VolumetricCloudDebugConfig.stormFieldForcedOnMonolith` defaults to
   `PRODUCTION_CONSUMER_BITS` (a debug view now renders FINAL's image);
   `setStormFieldForcedOnMonolith(bits)` stays the campaign override (0 = no
   field); new `resetStormFieldMonolithBits()` restores production, and the
   three end-of-campaign resets in `StormT132AutoDriver` (`:5048`, `:5058`,
   `:5068`) call it. The campaign-start reset to 0 (`:4346`) and the
   mid-campaign phase transitions are unchanged, so T196-T198's "production"
   counter phases keep their pre-field meaning.
5. `VolumetricCloudRenderer.leanFinalEligible()` adds the four field rows in
   step with the map: monolith bits `== PRODUCTION_CONSUMER_BITS`, `stormFieldXZ
   == DEFAULT_XZ`, both audit selectors 0.
6. `VolumetricCloudRenderer` build block: on `LEAN_FINAL` a null atlas raises
   `LeanFinalProgramUnavailableException("storm field atlas (...) unavailable;
   FINAL bakes the field consumers on and must not draw without it")` - the
   T161 loud fallback, since bits 11 against an unbound sampler would light the
   cone and probe the scan against zeros.
7. `VolumetricCloudRenderHook.status()` carries `fieldGpuMs=` from
   `lastRainFieldGpuMilliseconds()`, which `stormFieldProgram()` now charges on
   FINAL.

Generated FINAL after `generateLeanFinalShader` (`cloud_atmosphere_volume_final.fsh`):
`:285 uniform int PaStormFieldPass;`, `:293 const int PaStormFieldEnabled = 11;`,
`:329 const int PaStormFieldXZ = 256;`, `:345-346 const int PaLightAuditVariant
= 0; PaLightAuditOutput = 0;`, defines `PA_PRECIPITATION_ABSENT`,
`PA_ARM_DESC_PRECOMPUTE` only; the JSON keeps `PaStormFieldPass` (`:478`).
`t196_generate_only` bakes 0 with the pass live; `t198_field_both_exact` bakes
11; `t198_light_audit` keeps both live. `compileJava` clean.

`campaignGate` (`gate-t199b.log`, 25 s): T161 PASSED; halts at **T196 "FINAL
does not bake the storm field out"** (`StormVolumetricGeometrySandbox.java:835`)
- the clause T199c inverts. Nothing before it regressed; T197/T198 did not run.

What FINAL still carries that it should not, for T199c: the pass-2 soundness
oracle (`.fsh:7574-7633`) is reachable through the live pass.

### 2.2 Compiling the diagnostics out, and the generated-source proof (T199c)

**Polarity.** The brief had the oracle behind an `#ifdef` that only the two
hosts define. That would have stripped it from the **monolith** - the raw
source, loaded with no defines, which is the program every T196-T198
soundness phase runs on (`setStormFieldForcedOnMonolith(3)` +
`requestStormFieldSoundness()`, `StormT132AutoDriver.java:4847-4876`). So the
gate is inverted: the raw source defines `PA_STORM_FIELD_ORACLE` at its top
(`.fsh:3-9`), the monolith keeps the oracle, and `generateLeanFinalShader`
strips the definition from every lean variant through a new
`leanFinalStrippedDefines = ['PA_STORM_FIELD_ORACLE']` list (a missing
definition fails the build, like a missed uniform); `t196_field_both` and
`t198_field_both_exact` list `'PA_STORM_FIELD_ORACLE 1'` in their `defines` and
get it back. Two regions are gated: `paStormFieldReferenceFloorAt`
(`.fsh:6313-6335`, its only callers are in the oracle) and the
`PaStormFieldPass == 2` branch of the build pass (`.fsh:7584-7645`).

The audit was already out: `PA_LIGHT_AUDIT` is defined by `t198_light_audit`
alone. Bit 4 needs no gate - at `const int PaStormFieldEnabled = 11` the test
`(PaStormFieldEnabled & 4) != 0` is a constant false and the branch is
eliminated with the other dead selectors.

**Generated FINAL** (`cloud_atmosphere_volume_final.fsh`): `:15` the strip
comment where the define was; `uniform int PaStormFieldPass;`, `const int
PaStormFieldEnabled = 11;`, `const int PaStormFieldXZ = 256;`, `const int
PaLightAuditVariant = 0;`, `const int PaLightAuditOutput = 0;`; no `#define
PA_LIGHT_AUDIT`, no `#define PA_STORM_FIELD_ORACLE`; the JSON keeps
`PaStormFieldPass` and drops `PaStormFieldEnabled` / `PaStormFieldXZ`.
`t171_dup_a` byte-identical to FINAL (T171 PASSED). The raw monolith compiles
with the oracle in (`validateProductionShaderCompiles`), both hosts compile
with it re-defined, `t196_field_light` / `_probe` / `_generate_only`,
`t198_light_audit` and `t171_dup_a` do not define it.

**Sandbox.** `LEAN_FINAL_SPECIALIZATIONS` gains the four field rows (bits from
`PRODUCTION_CONSUMER_BITS`, XZ from `DEFAULT_XZ`, both selectors 0);
`PaStormFieldPass` is deliberately absent from that table because it is not a
specialization. The T196 invariant's FINAL clause is inverted (pass live, bits
11) and its label reads "FINAL keeps the build pass live". New
`validateT199FinalField` asserts everything in the paragraph above plus the
enum (`LEAN_FINAL` generates and bakes 11; the monolith reports 0 - uploaded,
not baked; `T196_GENERATE_ONLY` generates and bakes 0), the monolith's default
bits, and compiles FINAL in GL. The T198 clause (FINAL defines no audit) is
kept.

`campaignGate` (`gate-t199c-campaign.log`): **PASS**, 0 FAILED, main-level
5.3 s; `T199_FINAL_FIELD bits=11 xz=256 passLive=1 audit=0 oracle=0 hosts=2`;
T161, T171, T191, T196, T197, T198, T199 all PASSED.

### 2.3 Reverted at T199j: the oracle stays in FINAL

The first campaign run (`t199-campaign/t199-run1-oracle-stripped.log`,
23:18-23:24 on 11Sep2026) timed the FINAL of section 2.2 against
`t198_field_both_exact`, whose generated source differed from it by **one
line** - the arm kept `#define PA_STORM_FIELD_ORACLE 1`, FINAL had it
stripped (`diff` of the two generated `.fsh` files: the header's define
list and that line; the JSONs: the fragment name). Three anchor-bracketed
repeats a pose, spreads 0.8-2.5%, nothing rejected:

| pose | `t198_field_both_exact` cloudP50 | `lean_final` (oracle out) cloudP50 | field build P50 | net vs nofield |
|---|---|---|---|---|
| SIDE | 14.12 ms | **18.55 ms** (+31%) | 2.20 / 2.51 ms | 1.990x / **1.545x** |
| FAR | 10.65 ms | **11.96 ms** (+12%) | 2.17 / 2.26 ms | 1.558x / **1.402x** |

The comparator reproduces T198's own 14.1 ms to the tenth, and the
`t199_final_nofield` anchors (32.5 / 19.9 ms) reproduce T198's pre-field
FINAL anchors, so the harness is consistent; T171 excluded program identity
(byte-identical programs within 1.07%) and the production-output path. The
only remaining difference is the dead oracle branch, and removing it made
the whole program worse - the build pass, which shares no code with it, is
14% slower too, which is what a register-allocation or occupancy change
across the program looks like. **Compiling dead code out is not free on this
driver.** The productionization must ship the program T198 accepted, so the
strip was reverted at T199j: the raw source no longer defines or gates the
oracle, `leanFinalStrippedDefines` is empty (the mechanism stays, with the
lesson attached), the two hosts drop their now-redundant define, FINAL
carries `if (PaStormFieldPass == 2)` dead behind the live pass the renderer
never uploads as 2 on FINAL, and `validateT199FinalField` asserts the branch
present, no `PA_STORM_FIELD_ORACLE` anywhere in the source, and
`t198_field_both_exact` **byte-identical** to FINAL
(`gate-t199i-campaign-2.log`: `oracleBranch=dead comparatorIdentical=true`).
The second run (section 7) prices that FINAL.

What this means for T199c's item 3: the audit is compiled out (a define only
the audit program carries, and it never reached FINAL); the hybrid is
statically false at bits 11; the resolution is a constant; the oracle is
dead-but-present, by measurement.

## 3. Lifecycle checklist (T199d)

Every release path in the client converges on
`VolumetricCloudRenderTargets.destroyAll(reason)`, which destroys the field
atlas beside the other targets; cloud-target recreation
(`prepareCloudTargets`, a resolution-scale change) calls only
`destroyCloudTargets()` and never touches it. So the atlas lives from the
first FINAL frame after any release until the next release, and T199d made
each event visible in the session log rather than adding lifecycle code:

- `T199_FIELD_ALLOC format=RGBA16F xz=256 ... megabytes=16.00` - one line per
  allocation (`prepareStormFieldTarget`, was `T197_FIELD_ALLOC`).
- `T199_FIELD_RELEASE reason=<shutdown|resize|levelChanged> xz=256` - one
  line per release, in `destroyAll`.
- `T199_FIELD_KEPT cloudTargets=WxH->WxH xz=256` - one line per cloud-target
  recreation that left the atlas in place.

| | Event | Path | Expected in the log |
|---|---|---|---|
| (a) | first FINAL frame | `VolumetricCloudRenderer` build block -> `prepareStormFieldTarget(256)` -> `createHalfFloatMap(2048, 1024, LINEAR)` + `upgradeColorToRgba16f` | exactly one `T199_FIELD_ALLOC` between consecutive `_RELEASE` lines |
| (b) | resolution-scale transition | `prepareCloudTargets` -> `destroyCloudTargets()` only; `historyValid = false`, `resolutionGeneration++` | `T199_FIELD_KEPT` lines with no `_RELEASE` between them, across the campaign's `setFixedResolutionScale` changes |
| (c) | pass disabled / enabled | `VolumetricCloudRenderHook.setRuntimeEnabled(false)` -> `VolumetricCloudRenderTargets.shutdown()` -> `destroyAll("shutdown")`; enable -> next FINAL frame allocates | `T199_LIFECYCLE step=disable`, `_RELEASE reason=shutdown`, `step=enable`, `_ALLOC` |
| (d) | resource / shader reload (F3+T) | `VolumetricCloudClientLifecycle.onResourceReload` -> `releaseRenderResources(false)` -> `VolumetricCloudRenderer.shutdown()` + `VolumetricCloudRenderTargets.shutdown()`; `RegisterShadersEvent` re-links FINAL (`VolumetricCloudShaders.onRegisterShaders`); `stormFieldTextureId` is reset every frame (`:469`) and set only by a build, so the new atlas is bound by name on unit 15 on the next frame | `step=reloadResources`, `_RELEASE reason=shutdown`, `step=reloaded failed=false`, `_ALLOC` |
| (e) | world leave / re-enter | logout or `Minecraft#setLevel` -> `resetSessionState()` -> `releaseRenderResources(false)`; login -> `setRuntimeEnabled(true)` -> first frame allocates. The campaign's world join is the entry half (`_ALLOC` after login); the driver's `Minecraft.stop()` at the end is the exit half (`shutdownClient` -> `_RELEASE reason=shutdown`) | `_ALLOC` after `T132_AUTORUN armed`; `_RELEASE reason=shutdown` before `T132_AUTORUN_EXIT` |
| (f) | atlas unavailable on FINAL | `prepareStormFieldTarget` returns null (it cannot today - `TextureTarget` throws rather than returning null - so this is the guard for a future allocation path) -> `LeanFinalProgramUnavailableException` -> the render hook's handler disables the pass for the session with the cause in `status=` | not triggerable without breaking allocation; verified by reading `VolumetricCloudRenderer.java` build block and the T161 handler |

Automation: `StormT132AutoDriver.T199LifecycleProbe` runs (c) then (d) as a
frame-stepped sequence (disable, 6 frames, enable, 6 frames,
`reloadResourcePacks()`, wait for the future, 24 frames) and prints
`T199_LIFECYCLE step=..` around each; T199f sequences it at the start of the
campaign, before any timed arm, and T199j pastes the lines here. (b) and (e)
fall out of the campaign's own scale changes and its join/exit.

Headless: `validateT199FieldAllocation` (sandbox) pins the allocation FINAL
makes - RGBA16F, 8 bytes a node, 2048x1024, 2,097,152 nodes, 16 MiB, nests in
the candidate tiles, supported - and prints `T199_FIELD_ALLOCATION ...`
(`gate-t199d-campaign.log`, PASS).

**Run 1 (23:18-23:24):** the probe ticked with the matrix's arm 0 - the
nofield anchor, which builds nothing - still pinned, so the atlas was not
resident during disable / enable / reload: no `_RELEASE reason=shutdown`,
`step=done fieldAllocatedXZ=0`. Fixed for run 2 (`step=pinFinal` first, a
settle, `applyT141Arm()` after). What run 1 did show, unprompted:

```
[23:20:11] T199_FIELD_ALLOC format=RGBA16F xz=256 ... megabytes=16.00   <- (a), (e) entry: first FINAL frame after login
[23:21:26] T199_SHARED_FIELD_FINAL_BEGIN poses=2 arms=19 ...
[23:21:26] T199_FIELD_KEPT cloudTargets=641x360->214x120 xz=256           <- (b): the campaign's scale change recreated the cloud targets, atlas kept
[23:21:26] T199_FIELD_RELEASE reason=resize xz=256                        <- the resize path through destroyAll, same as (c)/(e)
[23:21:44] T199_LIFECYCLE step=disable / step=enable
[23:21:45] T199_LIFECYCLE step=reloadResources
[23:21:47] T199_LIFECYCLE step=reloaded failed=false                     <- (d): the reload completed and the re-linked programs rendered the SIDE anchor 2 s later
```

**Run 2 (23:30-23:35, `t199-campaign/t199-run.log`), the probe resequenced:**

```
[23:32:..] T199_FIELD_ALLOC format=RGBA16F xz=256 ... megabytes=16.00        <- (a), (e) entry
[23:33:11] T199_SHARED_FIELD_FINAL_BEGIN poses=2 arms=19 ...
[23:33:11] T199_FIELD_KEPT cloudTargets=641x360->214x120 xz=256                <- (b) cloud targets recreated, atlas kept
[23:33:12] T199_FIELD_RELEASE reason=resize xz=256                             <- resize path
[23:33:29] T199_LIFECYCLE step=pinFinal
[23:33:29] T199_FIELD_ALLOC ...                                                <- FINAL's first frame re-allocates
[23:33:30] T199_LIFECYCLE step=disable fieldAllocatedXZ=256
[23:33:30] T199_FIELD_RELEASE reason=shutdown xz=256                           <- (c) setRuntimeEnabled(false)
[23:33:30] T199_LIFECYCLE step=enable
[23:33:31] T199_LIFECYCLE step=reloadResources fieldAllocatedXZ=0
[23:33:33] T199_LIFECYCLE step=reloaded failed=false                          <- (d) reload completed, programs re-linked
[23:33:34] T199_LIFECYCLE step=done fieldAllocatedXZ=0
[23:33:35] T199_FIELD_ALLOC ...                                                <- the first field-building frame after the reload
[23:35:17] T132_AUTORUN_EXIT stopping the client ...
[23:35:17] T199_FIELD_RELEASE reason=shutdown xz=256                           <- (e) exit: Minecraft.stop() -> shutdownClient
```

Every row of the table above is evidenced except (f), which is not
triggerable. One observation: after `enable` the atlas did not return within
the probe's six-frame settle (`fieldAllocatedXZ=0` at the reload step and at
`done`) - a shutdown also resets the geometry coordinator, so the pass has
no descriptors to draw for a few frames until the async rebuild lands, and
the allocation follows one second later on the first frame that builds.
That is the pass resuming correctly, not a leak: two allocations, two
releases, and one atlas resident at any time.

## 4. Rebuild policy and cost (T199e)

**Policy: FINAL rebuilds the field every frame, unconditionally, before its
timed draw.** Exact reuse is not proven: T196 Task 11's dirty-reuse
measurement on a moving fixture was deferred and never taken, and T195's
update-frequency audit found that the weather bake, the descriptor upload and
the material advection all refresh every frame while a storm moves - "every
frame in practice, and what the break-even already charges". The break-even
therefore already includes the build (T198: ~0.84 ms + 0.004 ms at SIDE on a
2.151x net), and a skip would buy back only what a stationary storm under no
wind would have cost. No skip and no dirty signature were added in T199.

What T199e added is the count that a future reuse task would start from,
never a decision on it: `VolumetricCloudRenderer.countStormFieldBuild`
composes, per build, a signature of the field's inputs - the descriptor and
candidate-grid signatures (`PuffLobeSpatialIndex.*ForDiagnostics`), the
weather rasters' generation (`CloudWeatherMapRenderer.cacheMisses()`, every
miss is a bake), `WeatherOrigin`, `SlabBaseY` and `MaterialOffset` - and
counts `builds`, `unchangedInputs` (the previous atlas was exact) and
`unchangedDescriptors` (only the descriptors held; the cheaper key T014/T021
already track). `stormFieldRebuildStatus()` / `resetStormFieldRebuildCounters()`
expose them; the campaign resets at start and prints `T199_FIELD_REBUILD
pose=.. builds=.. unchangedInputs=.. unchangedDescriptors=..` per pose. The
signature costs a handful of long multiplies per frame and reads nothing the
renderer does not already hold.

Build cost per frame is the campaign's field column
(`lastRainFieldGpuMilliseconds`, the `RAIN_FIELD_TIMER` clock) on the FINAL
arm at SIDE and FAR, and on the `lean_final@d0` repeat - the descriptor
upload capped at zero, so the same 2,097,152 nodes are built against an
empty sky, which is what an ordinary clear-sky frame now pays (chosen over a
CLEAR pose, which has no storm and would stall the fixture qualification).

**Measured (run 2, three repeats, Ultra 480x270):**

| pose | FINAL build p50 / p95 | empty-sky build p50 / p95 | FINAL march p50 | empty-sky march p50 |
|---|---|---|---|---|
| SIDE | **1.65 / 1.85 ms** | **0.53 / 0.89 ms** | 13.03 ms | 0.58 ms |
| FAR | **1.55 / 1.78 ms** | **0.53 / 0.88 ms** | 10.73 ms | 0.58 ms |

Two findings. The build with a storm resident is 1.6 ms, not T198's 0.84:
T198's field column was the T196 node (one detail-free density); T198's own
report priced its correction at "+0.004 ms build", measured in the same
session as its control, and both sessions since (run 1: 2.2 ms, run 2: 1.65
ms) read the RGBA16F build higher than that. The build's cost varies by
session more than the march does (2.2 vs 1.65 ms for the same program), so it
is the p50 of the run that produced the net ratio that counts, and the net
ratios in section 7 include it. Second, **an empty sky pays 0.53 ms a frame
for a build that produces an all-empty atlas** - 0.53 of a 1.11 ms frame at
these poses, and against the 8.0 ms Ultra budget 6.6%. That is the first
follow-up candidate named by this report: an empty-descriptor early-out in
the build pass (no descriptors resident -> skip the draw, keep the previous
all-empty atlas, exact by construction because the node equation returns
empty for `StormLobeCount <= 0`). Recorded, not acted on.

`T199_FIELD_REBUILD`: SIDE `builds=1121 unchangedInputs=1119
unchangedDescriptors=1120`, FAR `builds=1131 unchangedInputs=1130
unchangedDescriptors=1130` (run 1: 1078/1077, 1146/1145). **These are the
campaign's frozen fixture** - history off, the world clock pinned, the
material offset held - and say only that the counter works: in the harness
the inputs never move, which is the condition T196 Task 11 warned "would
have hidden the build". They are not a play number. The play measurement
(a storm moving under wind, the weather bake refreshing) is still the one
T196 Task 11 deferred, and it is what a reuse task would take first.

## 5. Quality - FINAL vs nofield and vs T198 (T199f, T199j)

Run 2, both poses, every arm against the `t199_final_nofield` anchor (the
pre-field FINAL) at 480x270; the pocket reference is the anchor's own
shadow pockets. The mask and difference images are in `t199-campaign/`
(`*_mask.png`, `*_diff.png`; run 1's carry a `.run1` suffix).

| pose | arm | pockets (pixel / region) | pocket signed err | wall px | cloud SSIM | edge SSIM | dark interior | self-shadow contrast | changed px |
|---|---|---|---|---|---|---|---|---|---|
| SIDE | light only (`t198_field_light_exact`) | 0.851 / 0.863 | +0.013 | 0.12 | 0.936 | 0.958 | 0.911 | 1.31 | 8,983 |
| SIDE | **FINAL** (both consumers) | **0.771 / 0.780** | -0.001 | 0.27 | 0.866 | 0.896 | 0.912 | 1.58 | 10,164 |
| FAR | light only | 0.904 / 0.925 | +0.006 | 0.15 | 0.957 | 0.975 | 0.902 | 1.09 | 1,383 |
| FAR | **FINAL** | **0.871 / 0.888** | +0.001 | 0.20 | 0.882 | 0.957 | 0.901 | 1.26 | 1,584 |

**Against T198, like for like (both consumers, T198's `t198_field_both_exact`):**
FAR reproduces T198 - pockets 0.871 vs 0.874, cloud SSIM 0.882 vs 0.893,
contrast 1.26 vs 1.26, dark interior 0.901 vs 0.913. SIDE reads lower -
pockets 0.771 vs 0.861, cloud SSIM 0.866 vs 0.914, contrast 1.58 vs 1.35,
dark interior 0.912 vs 0.974. **The program is byte-identical to T198's arm**
(section 2.3, asserted by the sandbox), so the SIDE difference is the
fixture: the SIDE storm is regenerated per session and its pocket reference
has been 812, 784 (T198's two launches), 679 (run 1) and 619 (run 2) pixels
across four sessions, and the same unchanged light-only program read 0.880
on T198's fixture, 0.757 on run 1's and 0.851 on run 2's. The pocket metric
is a property of the fixture as much as of the program, which is why every
report since T197 measures arms within one session and never across.

**The within-run signal**, which is what is comparable: FINAL trails the
light-only frame by **0.080 SIDE / 0.033 FAR** in pocket retention (T198:
0.019 / 0.049), with the walls displaced 0.27 vs 0.12 px and the self-shadow
contrast over-shooting further (1.58 vs 1.31). That gap is the probe
consumer's: bit 2 makes the scan skip spans the field's conservative floor
calls empty, and where the floor is loose near a pocket wall the march
lands a step later than the exact scan would. It is larger on this SIDE
fixture than on T198's, it is not a regression of the program, and it is
not worked in T199 (the tighter floor is Decision A).

Against the parent's acceptance: the "pockets >= 0.85 SIDE / >= 0.90 FAR"
figures were T198's light-only numbers on T198's fixture. On this fixture
the light-only frame reads 0.851 / 0.904 - the same band - and **FINAL, the
frame the player sees, reads 0.771 / 0.871**, which is what T199 banks: the
both-consumer frame, judged as such. Dark interior 0.912 / 0.901 (T198 both:
0.974 / 0.913; within 0.02 at FAR, fixture at SIDE).

## 6. Soundness (T199f, T199j)

`T196_SOUNDNESS` on the monolith at production's bits with the oracle, both
poses, run 2: `underestimates=1 boundaryUnderestimates=0
worstUnderestimate=0.00118 binaryMisses=0`, useful-empty 98.56% SIDE /
99.07% FAR, overestimate mean 0.302 / 0.300. One node of 2,097,152 whose
floor sits 0.0012 above its dense reference - a sub-threshold amount (T198's
launch-1 control had nine, worst 0.0106; its corrected build had zero) - at
an interior node (boundary underestimates 0), and **on the ray the probe's
false-empty count is 0 at both poses** (`stormFieldProbeFalseEmpty=0`,
counters at bits 11), so no scan advanced over material production would
have found. False-occupied 74,335 SIDE / 19,926 FAR of 474,569 / 124,537
verdicts (**15.7% / 16.0%**, T198: 79,708 SIDE); probe fetches 3.51 / 0.92
per pixel (T198: 3.94 SIDE). The probe channel is unchanged by T199 (no
edit touches G or its lookup); the one-node underestimate is the oracle's
hashed-sample reference finding a slightly lower threshold in this session's
storm than the dilation assumed, and it is below anything the ray can act
on. Recorded; the floor is Decision A.

## 7. Baseline (T199f, T199j)

**Matrix** (`T199_ARMS`, `StormT132AutoDriver.java`): the anchor is
`t199_final_nofield` - FINAL's specialization with the field's consumers and
build baked off, the pre-field FINAL - because FINAL itself is now the arm
under test. Each of three repeats runs, anchor-bracketed, 60 samples at
Ultra, 480x270: `lean_final` (the field built and read every frame),
`t198_field_both_exact@x256` (the same bits from T198's own program; FINAL
should price at 1.00x of it, which is the check that productionizing changed
cost and nothing else), and `lean_final@d0` (the descriptor upload capped at
zero: the build against an empty sky, the T199e number). `netRatio =
anchor / (cloudP50 + fieldP50)` as in T196-T198; `lean_final` opens each
block. The `lean_final` row's `netRatioMean` at SIDE and FAR is the fresh
production baseline; T198's 2.151x / 1.580x are the expectation.

Images per pose, all against the nofield anchor: `t198_field_light_exact`
(light only - T198's pocket number, 0.880 / 0.923), `lean_final` (both
consumers - what the player sees; T198 measured 0.861 / 0.874 for this
frame), and the retained audit of production's lookup (variant 0, eight
accumulators). Pockets: the light-only frame is the baseline and FINAL's frame
is reported `versusLightOnly`, so the probe consumer's share of any loss is
explicit. Counters on the monolith at bits 0 (`|nofield`) and 11 (`|final`,
with the soundness oracle), `T199_ATTRIBUTION` echoed from the latter.

**Measured, run 2** (`T199_FINAL_FIELD_DECISION`, 60 samples a cell, three
anchor-bracketed blocks a pose, anchor drift <= 3%; one SIDE `lean_final`
block rejected for a 10.8% anchor drift - the first cell after the lifecycle
probe's reload, the same warm-up run 1 showed - and nothing else):

| pose | arm | cloudP50 | cloudP95 | field build P50 / P95 | anchor P50 | local ratio | **net ratio** (min-max) | spread |
|---|---|---|---|---|---|---|---|---|
| SIDE | **`lean_final`** (FINAL) | **13.03** | 13.90 | 1.65 / 1.85 | 30.66 | 2.353x | **2.088x** (2.075-2.102) | 1.5% |
| SIDE | `t198_field_both_exact` | 13.10 | 13.94 | 1.65 / 1.90 | 30.60 | 2.336x | 2.075x (2.062-2.091) | 1.5% |
| SIDE | `lean_final@d0` (empty sky) | 0.58 | 0.59 | 0.53 / 0.89 | 30.48 | 52.7x | 27.4x | 1.3% |
| FAR | **`lean_final`** (FINAL) | **10.73** | 11.03 | 1.55 / 1.78 | 18.97 | 1.767x | **1.545x** (1.535-1.550) | 1.0% |
| FAR | `t198_field_both_exact` | 10.77 | 11.10 | 1.55 / 1.88 | 19.02 | 1.765x | 1.544x (1.539-1.550) | 0.7% |
| FAR | `lean_final@d0` (empty sky) | 0.58 | 0.59 | 0.53 / 0.88 | 18.93 | 32.7x | 17.0x | 0.7% |

**FINAL prices at 1.00x of the program T198 accepted**: SIDE 13.03 vs 13.10
ms (0.5%), FAR 10.73 vs 10.77 (0.4%), both inside the 1.07% T171 found
between byte-identical programs. That is the check that productionizing
changed cost and nothing else, and after section 2.3 it holds.

**The fresh FINAL baseline: net SIDE 2.088x, net FAR 1.545x** against the
pre-field FINAL, at Ultra 96 steps, 480x270. T198 measured 2.151x / 1.580x
in its session; the anchors here are 30.6 / 19.0 ms against T198's 32.5 /
19.9, and the field build reads 1.65 ms against the 0.84 T198's control
carried, which is where the 0.06x goes - both are session effects (the
build alone moved 2.2 -> 1.65 ms between run 1 and run 2 for the same
program). Against the parent's band: >= 1.50x at SIDE is the "excellent"
band, 1.30-1.50x at FAR "major". Run 1 (section 2.3) is the number FINAL
would have carried with the oracle compiled out: 1.545x / 1.402x.

Empty sky: FINAL with no descriptors resident marches in 0.58 ms and
**builds in 0.53 ms** - the build is 48% of a clear-sky cloud frame at these
poses. Section 4 names the early-out.

## 8. Attribution and the far-tap residual (T199f, T199g, T199j)

### 8.1 The retained audit (T199g)

`t198_light_audit` (`PA_LIGHT_AUDIT`, the selectors `PaLightAuditVariant` /
`PaLightAuditOutput`, `lightAuditFieldBits`) and `StormLightTapErrorReport`
stay as a permanent diagnostic: campaign-only, baked to 0 and undefined in
FINAL (section 2.2, `validateT198LightAudit` and `validateT199FinalField`).
T199 takes it at production's lookup - `setLightAuditFieldBits(11)`, variant 0
(the production decomposition), all eight accumulators - once per pose, after
the light-only and FINAL frames and before any timed arm, and the report
joins them with the nofield anchor's pixel classes and the light-only frame's
luminance.

**The top rim is now a region, not an eyeballed area.** T198 found the
corrected field's remaining error "confined to the anvil's top rim" in the
difference image. `StormLightTapErrorReport.flagTopRim` marks, per column,
the cloud's first pixel from the top of the frame and the cloud pixels in the
`topRimRows(height)` rows below it (six rows at 270, floor three) with a flag
bit above the class values; `inRegion` reports it as the last region,
`topRim`, overlapping the class regions it cuts through, and masks the flag
off for the class-keyed ones. So the FINAL baseline carries
`T198_TAP_ERROR pose=.. variant=production tap=2|3 region=topRim ..` and
`region=pocket ..` lines with mean, |mean|, RMS, over/under fractions and the
cone's optical-depth error. Headless check `validateT199TopRimRegion` (a
flat-topped synthetic cloud: exactly the first six cloud rows of every cloud
column flagged, sky never, classes untouched).

**Expected residual, from T198's corrected audit** (lookup `t198_exact`,
production variant, SIDE pockets): taps 0-1 mean -0.009 / -0.015, RMS 0.066 /
0.105; taps 2-3 mean **-0.037 / -0.064**, |mean| 0.042 / 0.071, RMS 0.183 /
0.241, one-sided (almost every far-tap error an under-estimate); cone -4.0 of
68.4 (-5.9%) SIDE, -5.3 of 63.9 FAR. FINAL bakes the same lookup, so the
production-variant lines should reproduce these within capture noise, and the
`topRim` rows should carry the concentration T198 saw in the image. **The
named remaining error is the envelope's vertical representation on the far
taps** - taps 62-102 blocks up the cone reaching the anvil's upper boundary,
where a node above it holds `L = 1` or lies above the slab and the
interpolated threshold under-states the body. It is not worked in T199; its
FINAL-baseline numbers are what a future vertical-envelope task starts from.

### 8.2 Attribution (T199j)

`T199_ATTRIBUTION pose=.. bits=11` - the `*Share=` and `stormFieldProbe*`
fields of the `|final` counters capture. Expected from T198's corrected
build at SIDE: rain segment 62.3%, primary 26.5%, refine 7.3%, shaft 3.8%,
light 0, probe 0, untagged 0, residual closed; false-empty 0; probe fetches
3.94 / px.

**Attribution, run 2**, from the `|final` counters at bits 11 (shape-call
shares, `shapeAccountingClosed=true`, residual 362 / 42 of 3.50M / 0.87M):

| pose | rain segment | primary | refine | shaft | light | probe | untagged |
|---|---|---|---|---|---|---|---|
| SIDE, FINAL | **65.9%** | 24.2% | 6.0% | 3.9% | 0 | 0 | 0 |
| FAR, FINAL | **66.8%** | 22.0% | 5.6% | 5.6% | 0 | 0 | 0 |
| SIDE, pre-field (`|nofield`) | 40.9% | 10.1% | 3.5% | 1.0% | 28.8% | 15.7% | 0 |
| FAR, pre-field | 46.4% | 8.8% | 3.5% | 1.6% | 20.3% | 19.3% | 0 |

T198's 62.3 / 26.5 / 7.3 / 3.8 reproduces within the fixture. The light
cone and the probe, 44.5% of the pre-field frame's shape calls at SIDE, are
**0** under the field - both consumers read the atlas - and the rain segment
test is now two thirds of what the ray evaluates. **Recorded, not acted
on**: rain is out of T199's scope (T184-T192 stand), and this is the number
the next attribution-driven task starts from. Detail fetches on the light
path 58.6% / 52.4% (`detailFetchLightShare`); probe fetches 3.51 / 0.92 per
pixel; false-empty 0.

**The far-tap audit, run 2**, production variant at bits 11, one capture
set a pose, joined with the nofield anchor's classes:

| pose | region | tap 0 mean / RMS | tap 1 | tap 2 | tap 3 | cone dOD (rel.) |
|---|---|---|---|---|---|---|
| SIDE | pocket (85,328 samples) | -0.013 / 0.080 | -0.017 / 0.107 | **-0.047 / 0.203** | **-0.096 / 0.284** | -5.67 of 65.0 (**-8.7%**) |
| SIDE | allCloud | -0.016 / 0.086 | -0.021 / 0.114 | -0.054 / 0.209 | -0.090 / 0.271 | -5.75 of 61.2 (-9.4%) |
| SIDE | **topRim** (9,981) | -0.022 / 0.050 | -0.001 / 0.005 | **-0.002 / 0.015** | **-0.011 / 0.103** | -0.84 of 72.1 (**-1.2%**) |
| FAR | pocket (21,899) | -0.009 / 0.069 | -0.016 / 0.110 | **-0.051 / 0.217** | **-0.082 / 0.280** | -5.16 of 64.7 (**-8.0%**) |
| FAR | topRim (2,578) | -0.024 / 0.055 | -0.001 / 0.003 | -0.001 / 0.011 | -0.006 / 0.085 | -0.61 of 74.8 (-0.8%) |

The residual is where T198 left it and of the same shape: taps 0-1 small,
taps 2-3 one-sided under-estimates (under-fractions 0.39-0.55 against
over-fractions 0.33-0.45 with the mean firmly negative), RMS three to four
times the mean. On this fixture it is larger than T198's (-0.047 / -0.096
against -0.037 / -0.064 at the SIDE pockets; cone -8.7% against -5.9%),
which is consistent with the pocket gap in section 5 - the same fixture
exposes more of the anvil's upper boundary to the far taps.

**A negative result from the new region**: the screen-space top rim - the
silhouette rows - carries almost none of the error (tap 2 -0.002, tap 3
-0.011, cone -1.2%), while the pocket and interior pixels carry it. That is
not a contradiction of T198's difference image, it locates the residual
properly: the far taps that reach the anvil's upper boundary belong to
pixels *beneath* it - interior and pocket pixels 62-102 blocks below the
top, whose cone climbs into nodes holding `L = 1` - and those are the
pixels the image shows brighter along the top of the anvil. The rim rows'
own taps go into empty sky, where field and exact agree. So the residual is
the vertical envelope on the far taps, as T198 said, and a future task
should bin the audit by **tap landing height** rather than by pixel rows;
`region=topRim` stays as the control that says the rows themselves are not
it. Seven `evaluated=false reason=incomplete_captures` lines a pose are
the decomposition variants T199 did not capture, by design.

**Not worked in T199**, per the brief.

## 9. Cleanup ledger and variant counts (T199h)

| Retired | Where | Why |
|---|---|---|
| Bit 4, the T196 hybrid tap selector `!((PaStormFieldEnabled & 4) != 0 && i < 2)` | `cloud_atmosphere_volume.fsh` light tap; the uniform's doc rewritten | Dead at production's 11: bit 8 evaluates the noise at every tap, so there is nothing for the hybrid to restore; T198 showed it left the far taps biased |
| `cloud_atmosphere_volume_t196_field_hybrid` | `build.gradle`; `T196_FIELD_HYBRID` and its `case 7` in `CoreCostDiagnosticProgram`; the T196 timing and image tables in the driver; the T196 sandbox row | With the bit |
| The T197 campaign: `T197_MARKER`, `T197_POSES`, `T197_XZ = {256, 384, 512}`, `t197Run`, `t197CounterPhase`, `T197_ARMS`, `T197_OPTIMIZATION_ARMS`, `T197ImageArm`, `T197_IMAGE_ARMS`, `sharedFieldResolutionRunRequested`, `buildT197ResolutionReport`, every `t197Run` dispatch site, the registry row | `StormT132AutoDriver`, `StormCampaignRegistry` | T197 closed the resolution line (384 and 512 did not recover the pockets; T198 did at 256). `T170_WIRING campaigns=42 -> 41` |
| Runtime resolution state `VolumetricCloudDebugConfig.stormFieldXZ` / `setStormFieldXZ` and every upload of it (`applyT141Arm`, the T198/T199 counter phases, the image arms) | `VolumetricCloudDebugConfig`, `StormT132AutoDriver`, `VolumetricCloudRenderer` | The grid has no runtime state; the renderer uploads `StormFieldGeometry.DEFAULT_XZ` to the monolith and every lean program bakes it. `leanFinalEligible` drops its XZ clause for the same reason |
| `'PaStormFieldXZ'` in `keepUniforms` of every field arm | `build.gradle` (`t196_*`, `t198_*`) | Baked to 256 in all seven; `validateT197FieldResolution` now asserts that (was: asserts live), and the T196 / T198 invariants no longer require it live |

| Kept | Why |
|---|---|
| `t196_generate_only`, `t196_field_light`, `t196_field_probe` | The T196 campaign's own matrix; historical, still runnable at 256 |
| `t196_field_both` | T196's control, an oracle host |
| `t198_light_audit`, `PaLightAuditVariant` / `Output`, `lightAuditFieldBits`, `StormLightTapErrorReport` | The retained far-tap diagnostic (section 8.1) |
| `t198_field_light_exact` | T199's light-only image arm - the pocket number comparable to T198's - and the audit's luminance link; the brief had it retired, T199f kept it |
| `t198_field_both_exact` | The T198 comparator in T199's matrix and an oracle host |
| `t199_final_nofield` | The pre-field FINAL, T199's anchor |
| `T166Arm.stormFieldXZ` | A label only (`@x256`, the key the T196-T199 reports group field rows by); nothing is uploaded from it |
| `t197ReportPockets`, `T197_POCKET_BASELINE`, the `T197_POCKETS` line and the `t197-pockets/` output directory | The pocket diagnostic T198 and T199 reuse |
| `StormFieldGeometry` and `supported()` | The derivation and the headless facts |
| Every `T19x_*` log tag the reports cite; everything under `validation/` | Evidence |

Variants: **156 declared** after T199 (T198: 155; +`t199_final_nofield`,
-`t196_field_hybrid`); 157 generated programs on disk (FINAL + 156). The
T199 campaign registers `t199_final_nofield`, `t198_field_light_exact`,
`t198_field_both_exact` and `t198_light_audit` (four, with FINAL and the
monolith always loaded).

## 10. Gate and build (T199i, T199j)

- `./gradlew.bat campaignGate` after every code sub-task: `gate-t199b.log`
  (the expected T196 halt, section 2.1), `gate-t199c-campaign.log`,
  `gate-t199d-campaign.log`, `gate-t199f-campaign.log`,
  `gate-t199g-campaign.log`, `gate-t199i-campaign-1.log` (after T199h,
  `T170_WIRING campaigns=41 violations=0`), `gate-t199i-campaign-2.log`
  (after the section 2.3 revert, `oracleBranch=dead comparatorIdentical=true`)
  - all PASS from T199c on, 25-45 s each, main-level 5-7 s.
- `./gradlew.bat check build` once after run 2: **PASS** (`gate-t199.log`,
  47 invariants PASSED, `build/libs/Forge-projectatmosphere-0.9.3.1.jar`).
- Campaign: run 1 (`runClient-t199-run1.out.log`, 23:18-23:24, marker armed
  23:15; a first launch at 23:15 was killed by the tool's memory guard during
  world entry and relaunched detached) measured the oracle-stripped FINAL and
  is kept as `t199-campaign/t199-run1-oracle-stripped.log`; run 2
  (`runClient-t199-run2.out.log`, 23:30-23:35, 4,467 frames, 5 min in the
  client) is `t199-campaign/t199-run.log` and the numbers above. Marker
  `run/t199-shared-field-final.txt` retired to `.done`; `run/t132-autorun.txt`
  and `run/t132-autorun-exit.txt` remain.
- Variants: 156 declared (T198: 155; +`t199_final_nofield`,
  -`t196_field_hybrid`); T199 registers 4 (`t199_final_nofield`,
  `t198_field_light_exact`, `t198_field_both_exact`, `t198_light_audit`).
  `T175_WORKLOAD_VIEWS` 47, unchanged.

## 11. Decision A - tighter probe floor (T199k)

The rule set in T199k's brief: the tighter floor is justified only if the
upper bound on what it could save - (false-occupied probe verdicts / probe
verdicts) x (primary + refine share of the frame) - is >= 10% of frame time
at either pose. From sections 6 and 8, run 2, FINAL at bits 11:

| pose | false-occupied fraction | primary + refine share | **upper bound** |
|---|---|---|---|
| SIDE | 74,335 / 474,569 = 0.157 | 24.2% + 6.0% = 30.2% | **4.7%** |
| FAR | 19,926 / 124,537 = 0.160 | 22.0% + 5.6% = 27.6% | **4.4%** |

The share is of shape calls, the frame proxy the counters give; the bound
assumes every false-occupied verdict costs a full primary-and-refine march
of the span it failed to skip, which it does not (a false-occupied span
still early-terminates and still probes). **Below 10% at both poses:
deferred**, with the number. Two-thirds of the ray's shape calls are the
rain segment test (section 8), so the next attribution-driven task is
there, not in the floor - and it is out of T199's scope by the brief. T196's
"86% of possible verdicts are false" was measured on the pre-T198 floor at
the probe's own verdict count; under production bits the false-occupied
fraction is 15.7% of all verdicts, 84% of the *occupied* verdicts (74,335 of
88,833 at SIDE), so the floor is still loose where it matters, but what it
costs is bounded by the 4.7%.

## 12. Decision B - temporal reconstruction readiness (T199k)

The brief's criteria, checked:

| criterion | state |
|---|---|
| FINAL SIDE / FAR GPU ms inside the T135 Ultra budget (8.0 ms cloud at 0.75 scale) | **No.** 13.0 + 1.65 ms SIDE, 10.7 + 1.55 ms FAR at 0.25 scale (480x270); at the shipped 0.75 scale, by T146/T147's measured exponent (cost ~ pixels^0.49-0.75, 9x the pixels), 38-68 ms SIDE, 31-55 ms FAR - 4-8x over budget at the severe poses. The budget's representative pose (PLAY_NEAR) was not in T199's matrix. |
| history path intact under the field | Yes by construction, not re-validated visually: the atlas is world-space and independent of the cloud targets (section 3: `_KEPT` across a cloud-target recreation), `renderedProductionFrame` / `swapAndMarkHistoryValid` / invalidation on a resolution transition are untouched by T199, and the campaign renders with history off as every campaign does. A history-on quality pass is not in T199. |
| lifecycle checklist | Passed (section 3, run 2). |
| open correctness residual | Only the far taps' vertical envelope (section 8), named, bounded (cone -8.7% at the SIDE pockets), not worked. |
| rain share recorded | 65.9% / 66.8% of shape calls, recorded, not acted on. |
| program stable | FINAL byte-identical to the T198-accepted arm and pricing at 1.00x of it; 0 rejected arms; spreads <= 1.5%. |

**Answer: structurally ready, not budget-ready, and the deciding number is
not in T199's data.** Nothing in the field blocks temporal work - the atlas
has its own lifecycle, the program is stable, the residual is named. But
T151 rejected interleaved reconstruction because cost fell as
pixels^0.49-0.75, so marching half the pixels returned 1.42x, and its
reopen condition was "new evidence that invalidates that ceiling". T196-T199
replaced exactly the per-sample work that shaped that exponent (light and
probe walks: 44.5% of the pre-field frame's shape calls, now 0), so the
exponent may have moved - toward 1.0 (per-pixel cost now dominated by the
march itself) temporal reconstruction's ceiling rises; unchanged, it stays
closed. T199 measured one scale.

**Exact next task**: a two-scale FINAL baseline - the T138 ladder or its
0.25 / 0.75 endpoints - at SIDE and the representative PLAY_NEAR pose, with
the field, to re-derive the pixel-scaling exponent; that number decides
between reopening reconstruction (exponent >= ~0.9) and the next per-pixel
lever (the rain segment test, two thirds of the frame). Before it, the
empty-sky build early-out (section 4, 0.53 ms a clear frame, exact by
construction) is a one-line follow-up that does not need a campaign to
justify, only one to price.

## Status

**PASS - the shared field is FINAL.** FINAL builds the 256x256x32 RGBA16F
field every frame and reads it on both consumers (bits 11), prices at 1.00x
of the T198-accepted program, nets **2.088x SIDE / 1.545x FAR** against the
pre-field FINAL, keeps the probe sound (false-empty 0), and its lifecycle is
logged and proven. Banked with two lessons: compiling dead code out of the
shipped program cost 30% (section 2.3), and the pocket metric is a fixture
property that must never be compared across sessions (section 5).

Not merged. T198 `6046ea0`.
