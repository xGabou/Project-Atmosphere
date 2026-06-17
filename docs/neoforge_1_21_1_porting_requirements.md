# NeoForge 1.21.1 Porting Requirements

## Objective

The target is behavioral parity with the Forge 1.20.1 implementation. A successful compile is only a staging checkpoint. A file is not considered ported until the original behavior has been investigated, API replacements are justified, and runtime semantics are preserved as closely as possible.

## Required Investigation

For every changed method, class, field, event, registry, networking API, or mixin target:

1. Identify what the Forge 1.20.1 code did.
2. Locate the NeoForge 1.21.1 or Mojang 1.21.1 replacement implementation.
3. Determine why the API shape changed, including new parameters, return values, side effects, and lifecycle timing.
4. Compare vanilla callsites where possible.
5. Choose the replacement only after documenting why it preserves Project Atmosphere behavior.

Do not replace an API with the first compiling alternative unless the replacement is proven behaviorally equivalent.

## Method and Field Changes

When a signature changes, inspect both old and new implementations before choosing arguments for new parameters. Document whether Project Atmosphere should use a constant value, compute the value dynamically, or expose it through config.

Inline comments are required beside non-obvious new arguments:

```java
// NeoForge 1.21.1 added this flag to control X.
// Vanilla passes false for runtime updates; Project Atmosphere performs runtime updates here.
foo(height, level, false);
```

Deleted methods require a behavioral replacement search, not just a name search. Check former callsites, moved owner classes, helper classes, and vanilla replacements.

## Class Relocations

For every moved class, verify whether it is a package rename or a responsibility change. Document any new initialization, lifecycle, nullability, or side effects before replacing imports.

## Mixins

Broken mixins must be ported by behavior, not by name alone.

For every mixin:

1. Record the original target class and method.
2. Locate the corresponding 1.21.1 implementation.
3. Determine whether the method was renamed, split, inlined, or moved.
4. If removed, trace the old behavior through current vanilla or dependency callsites.
5. Select the closest injection point and explain why it covers the same behavior.

The Mixin annotation processor may only be restored after every included mixin has a valid 1.21.1 target analysis.

## Registries

When migrating `RegistryObject<T>` to `DeferredHolder<T, T>` or another holder:

- Verify initialization timing.
- Verify lazy resolution behavior.
- Verify nullability and error behavior.
- Verify registration bus/lifecycle differences.
- Document any behavior change.

## Events

When replacing Forge events with NeoForge events:

- Verify firing timing.
- Verify side behavior.
- Verify cancellation behavior.
- Verify old phase semantics if Forge used `TickEvent.Phase`.
- Document any shifted timing or cancellation loss.

## Networking

When replacing Forge `SimpleChannel` with NeoForge payload APIs:

- Verify login phase behavior.
- Verify play phase behavior.
- Verify packet direction.
- Verify serialization compatibility.
- Verify thread/execution guarantees.
- Verify client/server side handling.

Network files cannot be removed from the unported list until packet behavior matches Forge 1.20.1 semantics or a deliberate behavior change is documented.

## Required Per-File Evidence

Every file removed from `docs/neoforge_1_21_1_unported_sources.txt` must get an entry in `docs/neoforge_1_21_1_port_log.md` using this template:

```text
File:
<path>
Forge API Removed:
<old api>
NeoForge Replacement:
<new api>
Reason:
<why replacement is correct>
Behavior Change:
<none / describe>
Risk Level:
Low / Medium / High
Verification:
Build success / Runtime verified / Needs testing
```

If multiple APIs changed in one file, include all material changes in the same entry.

## Acceptance Criteria

A port is complete only when:

- Code compiles.
- Original behavior has been investigated.
- API changes are understood.
- Replacements are justified.
- Mixins target equivalent logic.
- Deleted methods have been traced to behavioral replacements.
- Runtime semantics are preserved as closely as possible.
