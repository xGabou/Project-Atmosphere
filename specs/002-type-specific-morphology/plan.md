# Implementation Plan: Type-specific morphology — T255

**Branch**: existing `quality/t217-deep-interior-lighting` | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)

## Summary

First phase: exhaustive audit and opt-in three-archetype storm geometry prototype. Geometry consumes shape/seed, not visual profile. Macro axis/crown/canopy plus unequal meso growth structures; native flat-light evidence required. Full all-family migration remains later work after approval.

## Technical Context

**Language/Version**: Java 17, GLSL 150, Forge 1.20.1.
**Primary Dependencies**: existing Minecraft/Forge/LWJGL/Gradle; no new runtime dependency.
**Storage**: existing region/cluster and field schemas unchanged for experiment; versioned future field morphology requires approval.
**Testing**: Java anatomy/control/bounds sandbox, CPU/GPU parity, existing regressions, native capture/performance.
**Target Platform**: existing client renderer; distribution-safe pure geometry.
**Project Type**: focused brownfield mod module.
**Performance Goals**: real renderer ≤10 ms, hardware/resolution and median/p95/worst-view reported; CPU timings not sufficient.
**Constraints**: no coefficient tuning, unrelated rewrite or production switch; preserve user shader/changelog and no commit/merge/push.
**Scale/Scope**: three storms × three seeds × nine views plus oblique/control arms. All 11 native types remain later goal.

## Constitution Check

Pre-research PASS: same Java/Forge baseline; modular focused geometry; production authority unchanged; no new dependencies; tests/runtime evidence required.
Post-design PASS: immutable recipes, allocation-free sampling, conservative bounds/exact fallback before acceleration; explicit save/wire approval boundary. No constitutional exception.

## Project Structure

```text
specs/001-native-storm-rendering/validation/t255-cloud-morphology-architecture.md
specs/002-type-specific-morphology/{spec,plan,research,data-model,quickstart,tasks}.md
specs/002-type-specific-morphology/contracts/morphology.md
src/main/java/net/Gabou/projectatmosphere/clouds/morphology/StormMorphologyPrototype.java
src/test/java/net/Gabou/projectatmosphere/clouds/morphology/StormMorphologyPrototypeSandbox.java
src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ (opt-in adapter/capture)
src/main/resources/assets/projectatmosphere/shaders/ (experimental shared kernel)
```

**Structure Decision**: pure compiler/evaluator outside client render code; packing/capture client-owned. Do not re-label the old skeleton as a new contract.

## Ownership / frequency / cache

Compile per type/profile/seed/version outside sample loops. Immutable features, no allocation in density sampling. GPU uploads render-thread only. Cache by content including version/bounds; placement updates separate. Controlled fixtures supply experimental data, not persistent client weather authority. Reset clears prototype adoption. All primary/light/probe/camera consumers must use matching geometry or exact fallback before final acceptance.

## Approval boundary

Measure compact recipe first; hierarchical blocks and baked volumes remain alternatives with material tradeoffs. Request approval before production schema choice or switching defaults. Stop if unrelated renderer systems are necessary.

## Complexity Tracking

No constitutional violation. CPU/GPU parity coverage is required for two-language evaluation.

## T256 experimental replacement of the rejected envelope model

Keep T255's pure compiler and GLSL intact for controlled baseline captures. A separate GrowthDefinedStormPrototype supplies curved, variable-width rooted paths whose union owns all body support, independent swept canopy lofts and branching ice. A shared inspection-only StormGeometryProgram interface adapts the old recipe without changing it. New specialized shader variants are explicit-environment-gated, not runtime branches inside production programs. Reuse the existing RGBA32F fixture row and common three-component hierarchy; do not redesign production storage/network/save transport. The old-envelope diagnostic flag is the only A/B difference; compare authored signatures separately from containment bounds. CPU diagrams, green contracts and differently shaped clouds are necessary but insufficient: native anatomical review and measured 10 ms feasibility remain independent gates.
