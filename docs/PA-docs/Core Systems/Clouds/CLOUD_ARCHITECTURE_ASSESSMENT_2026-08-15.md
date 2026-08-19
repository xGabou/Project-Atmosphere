# Cloud Architecture Assessment — Gap to "Realistic Clouds" + Forge Isolation

> Implementation update (2026-08-17): the structured storm fix, renderer rollback gate, network
> boundary, and supporting verification are recorded in `CLOUD_IMPLEMENTATION_2026-08-17.md`.

Companion to `CLOUD_RENDERING_OVERVIEW.md` (read that first for current-state basics). This
document answers two different questions the overview doesn't: **(A)** what's actually missing,
architecturally, between where the code is today and a genuinely realistic volumetric cloud
renderer, and **(B)** a concrete design for isolating Forge-1.20.1-specific code so a future port
costs less than it does today.

Confidence is marked per item. "Confirmed" means read/tested directly in this engagement (this
session or the two prior audit docs). "Inferred" means reasoned from the ~65% of
`cloud_atmosphere_volume.fsh` actually read plus general domain knowledge of volumetric cloud
techniques — flagged for verification, not asserted as fact.

## Part A — Gap to "realistic clouds"

### What's already there (confirmed, this is not a weak foundation)

Reading `cloud_atmosphere_volume.fsh` in depth across this engagement found genuinely
production-grade technique, not the "AI slop" the length of the file might suggest:

- Real sun/moon-relative lighting (`LightDir`, `LightColor`, not hardcoded).
- Dual-lobe Henyey-Greenstein phase function + Hillaire-style multi-scattering octaves.
- Beer-powder term and a silver-lining highlight restricted to an optically-thin shell (not a
  volume-wide boost, which is the naive/wrong way to do it).
- Sky ambient gradient (top/bottom), storm darkening, sunset tinting by light-path distance.
- Temporal reprojection with blue-noise dithered sub-pixel jitter and history blending.
- A real weather-map-driven global density field (coverage, morphology category, per-family shape
  functions) rather than per-cloud primitives.
- An analytic multi-lobe (PUFF) system for cumulus with BASE/MIDDLE/CROWN tiers — this is the
  "right" way to build structured cumulus, when it's actually reachable (see Issue 2 below).
- Rain shafts, tornado funnel integration, and precipitation-aware density all folded into the same
  density function instead of bolted on separately.
- Cloud shadow mapping wired into the same shader-registration class as the live renderer
  (`VolumetricCloudShaders.java`) — present, not orphaned. Visual quality not graded this session.

This matters for calibrating the rest of this document: the gap to realistic clouds is **not**
"the renderer needs to be rewritten with better technique." The technique is already good. The gap
is specific, identified holes and one architectural fork (two renderers) diluting effort.

### Confirmed gaps (verified this engagement)

1. **The structured multi-tier storm system is dead code for the standard storm source.**
   `stormStructureShape()` — the thing that would turn a cumulonimbus into distinguishable
   base/core/tower/anvil lobes instead of one smooth mass — never activates for `PA_CLUSTER`-sourced
   fields, confirmed live via `hasSevereStructures=false`. This is almost certainly the single
   biggest lever on "realistic," because silhouette break-up, not surface shading, is what a human
   eye reads as "real cloud" vs. "lit rock." Full trace and fix options in
   `PA_STORM_STRUCTURE_PIPELINE_INVESTIGATION_2026-08-14.md`.

2. **Anvil is a density modifier, not horizontal silhouette expansion.** Even where storm shape
   code *does* run (the generic `familyMacroShape` fallback), the anvil band is a height-gated
   opacity multiplier, not a radius displacement. A real anvil visually spreads outward at the top;
   this one just gets a bit more opaque in a height band.

3. **Two fully-built parallel volumetric renderers.** Every hour spent improving
   `cloud_field_volume.fsh` (hardcoded light direction, no phase function, plain value noise — the
   objectively worse one) is an hour not spent on the renderer that's actually preferred
   (`cloud_atmosphere_volume.fsh`). This is a process/effort-dilution gap as much as a code gap.

4. **No prior fix cycle checked a rendered pixel before this engagement.** Not a code gap, but the
   reason the code gaps above went unnoticed for months — see `CLOUD_RENDERING_OVERVIEW.md`'s
   archived-docs section. Worth stating again here because it's the actual root cause underneath
   items 1-3, not a separate concern.

### Likely gaps (inferred — verify before acting on these)

Not read in full detail this session; flagged from what the ~65% coverage of the shader and its
surrounding pipeline suggests, plus what's standard in comparable techniques (Horizon Zero Dawn /
Nubis-style real-time cloud rendering, which this codebase's comments explicitly reference in
spirit).

5. **Single global weather-map slab per horizontal position.** `sampleWeather()` returns one
   `baseY`/`topY` pair per texel. That structurally means one dominant cloud layer per location —
   real skies routinely show low cumulus *and* high cirrus simultaneously in the same view. Whether
   this is a real limitation or handled elsewhere (a second slab pass, category blending) wasn't
   confirmed — worth a direct check before assuming it needs fixing.

6. **No apparent wind-shear tilt on storm towers.** Real supercells lean with height because wind
   speed/direction changes with altitude. The wind term found in the density function reads as
   uniform advection, not a height-dependent shear applied to the tower's silhouette. Minor
   realism detail, low priority.

7. **No god rays / crepuscular rays through cloud breaks.** Common in high-end volumetric cloud
   presentations (light shafts visible through gaps). Not observed in the sections read; likely
   absent, likely a deliberate scope cut rather than a bug — worth confirming it's not wanted before
   spending effort here, since it's a "nice to have," not core realism.

8. **Server-side severe-weather evaluation still uses the reconciled cell derivative, not the exact
   GPU cloudlet envelope** (documented already in `CLOUD_CANONICAL_ARCHITECTURE.md`'s Transitional
   Limitations section, and still true as far as this engagement found). This is a
   simulation/visual consistency gap: what triggers gameplay severe-weather logic and what the
   player actually sees rendered can, in principle, diverge slightly. Not a rendering-realism issue
   per se, but relevant to "does this feel like a coherent real storm."

### Priority order, if tackling this list

1. Fix or bypass the structured-storm gap (#1) — highest visual impact, root cause already fully
   traced, two concrete options already on the table.
2. Give anvil real horizontal expansion (#2) — cheap once #1 is addressed, since it's the same
   silhouette-generation code path.
3. Retire or clearly demote the Field renderer (#3) — not urgent for visuals, but every week it's
   left standing is a week future debugging effort might land on the wrong file again.
4. Investigate #5-8 only after #1-3, and only with the same live-screenshot verification discipline
   used so far — several of these might already be non-issues, and guessing wastes the exact kind of
   effort this whole engagement has been trying to stop wasting.

## Part B — Isolating Forge-1.20.1-specific code

### The ask, tested against real data instead of assumed

You asked for "isolate 1.20.1 Forge specific calls into one class." The repo already has a
`NeoForge-1.21.1` branch, which means the actual cost of *not* having done this is measurable, not
hypothetical:

```
git diff --stat Forge-1.20.1 origin/NeoForge-1.21.1 -- src/main/java
202 files changed, 2022 insertions(+), 3413 deletions(-)
```

Just within `clouds/` (173 files total), **30 files changed** to make that port. That's the real
number to design against.

### Where the coupling actually lives (not evenly spread)

Import survey across `clouds/` (173 files):

| Concern | Files touching Forge directly | Notes |
|---|---|---|
| `client/render/*` (rendering) | 9 of 67 | Expected — GL/shader submission inherently touches engine APIs |
| Network packets (`*/network/*`) | 8 files | **Every one of them hand-rolls Forge's packet API** |
| `cell/sim`, `service`, `type` | 3 files | `Dist`/`ModList`/reload-listener registration |
| `analytics`, `api`, `backend`, `simulation`, `state`, `transport` | **0 of 33** | Already clean — good sign, build on this, don't disturb it |

And the branch diff confirms it isn't just import-count noise — it's where the actual line churn
concentrated. Top of the diff, by lines changed, within `clouds/`:

1. `CloudDiagnosticsOverlay.java` — 87 lines (GUI overlay API changed significantly between
   versions)
2. `SyncCloudRegionsPacket.java` — 58 lines
3. `CloudRegionSavedData.java` — 36 lines (`SavedData` is vanilla API, not Forge — see caveat below)
4. `CloudFieldDeltaPacket.java`, `SyncCloudFieldsPacket.java`, `SyncCloudCellsPacket.java`,
   `CloudCellDeltaPacket.java`, `CloudCellAnalyticsPacket.java` — 26-32 lines each

**Six of the top eight most-churned files in the entire cloud package are network packets.** That's
not a diffuse problem needing a generic "put Forge stuff somewhere else" fix — it's one
concentrated, specific pain point (Forge's networking API, which changed substantially going into
NeoForge) hitting eight separate hand-written classes that all repeat the same registration/encode
boilerplate.

**Caveat worth internalizing:** porting cost has two independent axes, and only one of them is
"Forge." `CloudRegionSavedData` changed because vanilla's `SavedData` API itself changed between MC
1.20.1 and 1.21.1 — that has nothing to do with Forge vs. NeoForge and would have cost the same
lines even on a same-loader version bump. Isolating Forge calls reduces one axis of churn, not both.
Don't expect a Forge-isolation layer to fully flatten the diff on its own.

### Recommended shape: a few narrow ports, not one god-class

A single literal class holding "all Forge calls" for 20+ unrelated concerns (event registration,
networking, GUI overlays, dist-checking, config, resource-reload hooks) would just become a new,
worse coupling smell — everything would depend on one file, and that file would depend on
everything. The actual target, matching what the branch diff says the pain really is:

```
clouds/platform/                          (new package — the only place clouds/* may import
                                            net.minecraftforge.*, com.mojang.blaze3d.*, or
                                            org.lwjgl.* outside client/render/)
  CloudNetworkTransport.java               interface: register(), sendToServer(), sendToClient(),
                                            sendToTracking() — narrow enough that a
                                            NeoForge implementation is a same-shaped rewrite,
                                            not a redesign
  ForgeCloudNetworkTransport.java          the only class touching PacketDistributor/NetworkEvent
  CloudClientEnvironment.java              interface: isClientDist(), isModLoaded(id),
                                            runOnClient(Runnable)  — wraps Dist/OnlyIn/
                                            DistExecutor/ModList
  ForgeCloudClientEnvironment.java
  CloudReloadListenerRegistrar.java        interface: register(id, listener) — wraps
                                            AddReloadListenerEvent/RegisterClientReloadListenersEvent
  ForgeCloudReloadListenerRegistrar.java
```

Existing `*Packet.java` classes stop implementing Forge's packet contract directly and instead
become plain data classes (`encode(ByteBuf)`/`decode(ByteBuf)` only — already close to this shape
today) registered once through `CloudNetworkTransport`. That's the change that would have turned 8
files of the last port's diff into edits to 2 files.

This also builds on a pattern that already exists in the codebase in small pockets
(`CloudFieldBackendAdapter`, the `transport/` classes under `modules/seasonaltrees/`) — this isn't
introducing an unfamiliar idea, it's applying one the codebase already reaches for, consistently, at
the one place the data says it actually pays off.

### What this does *not* need to cover

`client/render/*`'s Forge event-hook usage (`RenderLevelStageEvent`, `RenderGuiOverlayEvent`, etc.)
is inherent to being a client renderer wired into Forge's render pipeline — every loader has *some*
equivalent hook, and abstracting that specific seam has much lower payoff than networking, since
render-hook registration is a handful of lines in a handful of files, not a repeated pattern across
eight. Leave it as-is; it's not where the diff hurt.
