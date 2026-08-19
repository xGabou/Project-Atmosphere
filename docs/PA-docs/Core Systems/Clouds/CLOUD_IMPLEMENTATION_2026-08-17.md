# Cloud and Architecture Implementation — 2026-08-17

This records the implementation and verification work performed from the two 2026-08-15
architecture assessments. It supersedes their statements that the structured storm path, Gradle
wrapper, and platform seams are still unimplemented.

## Native storm rendering

The standard `PA_CLUSTER` source already transported one `CloudFieldSnapshot` per authoritative
cluster, including `CloudMorphologyMembership`. The missing link was narrower than the assessment
inferred: `CloudMorphologyMembership.stageFor()` never assigned structured stages to
`STORM_ANVIL`, so every storm member reached the renderer as `MACRO`.

The implementation now:

- deterministically projects supported storm groups into `BASE`, `CORE`, `TOWER`, and `ANVIL`;
- maps `ANVIL` through `VolumetricRenderCell` into the existing GPU role contract;
- gives storm source members role-specific vertical envelopes, radii, and aspect ratios;
- concentrates the raised members into an overlapping wind-sheared anvil instead of scattering
  disconnected full-height volumes; and
- reports anvil cells explicitly in `/pa system cloudStatus`.

No packet schema change was required. The existing versioned morphology membership is sufficient.

Live Forge 1.20.1 validation produced a native storm with:

```text
cloudOwner=PA_VOLUMETRIC
roles[base=1,core=2,tower=2,crown=0,anvil=3,other=0]
hasSevereStructures=true
source=fields
```

The rendered result is one connected convective tower/anvil system. Direct wind-axis views still
show the small authoritative source-lobe count, so further silhouette tuning remains useful, but
the unreachable structured path and disconnected pointed-volume defect are fixed.

## Renderer ownership

The legacy Field renderer is disabled in normal installations. It can only become owner when the
JVM is launched with:

```text
-Dprojectatmosphere.dev.enableFieldRendererFallback=true
```

This is a temporary developer rollback gate, not a player setting. The Field implementation should
be deleted only after the Atmosphere renderer has survived a stable tagged build; removing it in
the same change that first disables it would eliminate the agreed rollback window. Simple Clouds
still wins ownership unconditionally when installed.

## Portability boundaries

The first whole-mod ports-and-adapters slice is implemented:

- `platform/network`: loader-neutral outbound transport and inbound packet context, with Forge
  adapters. All mod send sites use the transport, and packet data classes no longer import
  `NetworkEvent.Context` or `DistExecutor`.
- `platform/config`: a typed cloud-config port. Cloud policy, backend selection, spawning,
  movement, and synchronization no longer read `ForgeConfigSpec` globals directly.
- `clouds/state/CloudRegionStateRepository`: an injectable persistence port with a Minecraft
  1.20.1 `SavedData` adapter.
- `platform/PlatformEnvironment`: loader facts such as optional-mod presence are behind an adapter.

`architectureBoundaryCheck` runs as part of `check` and rejects raw Forge networking outside the
adapter/registration layer, as well as renewed direct common-config access in the migrated cloud
domain.

This is intentionally incremental. Renderer configuration/UI remains coupled to the current
client config representation, and other non-cloud `SavedData` stores have not been migrated merely
for architectural symmetry.

## Build and verification

The Gradle 8.8 wrapper is restored and tracked. The obsolete unpublished PM Weather compile-only
coordinate is opt-in so clean builds no longer fail while resolving an unused dependency.

Verified commands:

```text
gradlew check cloudFieldSandbox --no-daemon
gradlew compileJava testClasses cloudMorphologyTopologySandbox --no-daemon
gradlew runClient --no-daemon
```

Automated morphology, motion, material-advection, volumetric-stability, cloud-field, and
architecture checks passed. The final live client run also exercised world persistence, cloud
clear/spawn commands, server-to-client cloud synchronization, and the native renderer.

## NeoForge branch audit

The dirty Forge worktree was not switched. A read-only audit against `NeoForge-1.21.1` confirmed
that its network registration uses 1.21 payload registrars while the new domain-facing transport
and packet-context interfaces contain no Forge or NeoForge imports. Raw Forge channel/distributor
references on this branch are confined to the Forge adapter and registration class. Porting this
slice therefore requires NeoForge implementations of those adapter points, not renewed edits to
simulation, sync managers, or packet handling logic. A full NeoForge build of these changes remains
branch work because the 1.21 packet data classes use the newer custom-payload/stream-codec API.
