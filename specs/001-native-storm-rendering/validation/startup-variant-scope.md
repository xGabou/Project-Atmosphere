# T191 - 140 programs compiled at every startup, 2 are needed

Branch `experiment/cloud-descriptor-k`, base `1deb75f` (T190). Nothing merged.
Infrastructure only: no production rendering behaviour changes, and no
performance or image campaign was run because no production shader source
changed.

## 1. Headline

**Shader registration fell from 6m 46s to 5s, and from 140 live fragment
programs to 2.**

| | before | after (ordinary) | after (T190 armed) |
|---|---|---|---|
| declared variants | 140 | 140 | 140 |
| generated variants | 140 | 140 | 140 |
| **compiled and linked at startup** | **140** | **2** | **5** |
| **shader registration wall time** | **6m 46s** | **5s** | **5s** |

The before figure is not an estimate. Two prior campaign logs bracket it
directly: T190 registered shaders from 20:58:08 to 21:04:54, and T189 from
19:01:41 to 19:08:13 - **6m 46s and 6m 32s**. That window is also exactly where
the T187 launch died, six minutes into shader loading.

Nothing was deleted. Every variant is still declared in `build.gradle`, still
generated, and every validation document still stands. **Only the
`ShaderInstance` is conditional.**

## 2. Task 1 - inventory

All 141 enum programs classify without a remainder:

| Category | Count |
|---|---|
| **PRODUCTION** (`DIAGNOSTIC_MONOLITH`, `LEAN_FINAL`) | **2** |
| **CAMPAIGN ARM** (T140-T190) | **139** |
| duplicate / superseded / orphaned | **0** |

Distribution across 26 campaign prefixes: T166 has 22 arms, T170 15, T167 14,
T169 and T162 9 each, T176 6, T168/T181/T188 5 each, and the rest 1-4.

**Every one of the 139 resolves to a campaign registered in
`StormCampaignRegistry`.** That zero-orphan result is what makes scoping a
complete answer rather than a partial one: there is no arm that scoping would
strand, because there is no arm without a campaign that can arm it.

For the runtime-reference question the brief asks: **none of the 139 is
referenced at runtime outside its campaign.** `setFinalProgramOverride` is called
only by `StormT132AutoDriver`, and `CoreCostDiagnosticProgram.parse` has no
caller at all. Every arm reaches the renderer through its campaign's arm matrix
or not at all.

## 3. Task 2 - no invariant needed a live program

Audited every invariant that mentions a diagnostic program. **None requires a
linked `ShaderInstance`.** They validate:

- generated variant source (`build/generated/leanFinalResources/...`),
- the `leanFinalConstants` and `leanProgramVariants` define tables,
- enum declarations and `StormCampaignRegistry` entries,
- the shader source itself.

The sandbox has never had a GL context, so it could not have compiled anything
in the first place. **No invariant was weakened, and none needed to be** - the
compile and link work at startup was serving nothing except the possibility that
a campaign might later select an arm.

## 4. Tasks 3 and 4 - scoped rather than pruned

Pruning arms individually would have meant a judgement call per arm about
whether its evidence was still live, and would have needed that judgement again
after every future campaign. Scoping needs it once.

Registration now filters on the active campaign marker:

```java
java.util.Set<String> activeCampaigns = StormCampaignRegistry.activeCampaignIds();
for (CoreCostDiagnosticProgram program : CoreCostDiagnosticProgram.values()) {
    if (program.isProductionProgram()) { continue; }
    if (!activeCampaigns.contains(program.campaignId())) { skipped++; continue; }
    registerDiagnosticVolumeProgram(event, program);
}
```

**`campaignId()` derives from the arm's own `tNNN_` prefix** and resolves it
against the registry, so a new arm inherits its campaign from its own name.
There is no second table to keep in step, and the count cannot creep back the way
it did through T186-T190. `activeCampaignIds()` probes marker files on demand -
never from a static initializer, because the sandbox loads the registry headless
and markers appear and vanish between runs of the same client.

**A selected-but-unarmed arm now reports why.** `missingProgramReason` returns
`campaign_T188_not_armed: create its marker file and restart the client` rather
than leaving it indistinguishable from a compile failure - two conditions with
completely different fixes.

## 5. Task 5 - startup validation

Three launches, both code paths:

| Launch | Campaign | Registered | Skipped | Reload -> registered | Result |
|---|---|---|---|---|---|
| 1 | none | 0 | 139 | 22:40:32 -> 22:40:37 (**5s**) | success |
| 2 | **T190 armed** | **3** | 136 | 22:42:01 -> 22:42:06 (**5s**) | success |
| 3 | none | 0 | 139 | 22:43:09 -> 22:43:13 (**4s**) | success |

**Launch 2 is the counter-test that matters.** Scoping is only correct if arming
a campaign still loads its arms, and `activeCampaigns=T190 registered=3
skipped=136` is exactly the three T190 programs, with no
`diagnostic program ... failed to load` line. A cleanup that quietly stopped
campaigns from working would have looked identical to a successful one on an
ordinary launch.

**Peak memory during shader loading is not separately instrumented**, and is not
claimed. What is claimed is the load itself: 2 programs instead of 140, over 5
seconds instead of 406. The pressure that killed the T187 launch was 140
full-size fragment programs compiled back to back, and that no longer happens on
any run that is not deliberately running a campaign.

## 6. Task 6 - production equality

`git diff 1deb75f -- src/main/resources/assets/projectatmosphere/shaders/
build.gradle` is **empty**. `leanFinalConstants` and `leanProgramVariants` are
byte-identical to T190. FINAL's source, defines and generated output are
untouched, so no image or performance campaign was required.

The two production programs are registered **unconditionally**, ahead of the
filter, and the invariant fails the build if either ever acquires a campaign id -
ordinary rendering must never depend on a marker file.

## 7. What stops it coming back

`T191_VARIANT_SCOPE productionAlwaysLoaded=2|campaignScoped=139|unreachable=0|
ordinaryStartupPrograms=2` asserts:

1. every non-production program resolves to a **registered** campaign, so a new
   arm cannot become one no marker can activate;
2. exactly two production programs stay unconditional;
3. the registration loop is still filtered, and still reports what it loaded, so
   a regression is visible in the log rather than only in the wall clock;
4. every campaign's marker name matches the one the driver looks for - a
   mismatch would arm a campaign that registers programs the run never selects.

## 8. Status

Not merged. Base T190 `1deb75f`.

**Next**, per the brief and unchanged by this task: one final rain-field
experiment, the **closed-form ownership bound**, with a hard stop - if it cannot
reach quality-correct rain **and** >= 1.10x net SIDE, rain-field optimization
closes completely and is not followed by another micro-campaign.
