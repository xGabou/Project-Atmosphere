# Project Atmosphere — Whole-Mod Architecture Assessment (2026-08-15)

> Implementation update (2026-08-17): the first network, typed-config, persistence, and runtime
> environment ports are recorded in `Clouds/CLOUD_IMPLEMENTATION_2026-08-17.md`.

Companion to `Clouds/CLOUD_ARCHITECTURE_ASSESSMENT_2026-08-15.md`, which covers the cloud package
specifically. This document answers the broader question: is a hexagonal or EDA/microservice-style
architecture worth moving toward for the rest of the mod, replacing the current structure.

**Verdict up front, detail below:** microservices doesn't apply here and shouldn't be pursued —
this is a single-process game mod, not a distributed system. EDA is already ~70% present
informally (Forge's event bus), so "adopt EDA" mostly means formalizing what already exists at the
seams where it currently leaks Forge coupling, not a paradigm change. Hexagonal (ports & adapters)
is the one worth actually doing, but scoped to the four seams the data below shows are where real
cost concentrates — not a wall-to-wall rewrite of a 616-file, 97,500-line mod that mostly already
works.

## The current architecture, measured rather than assumed

- 616 Java files, ~97,500 lines, top-level packages: `api`, `async`, `blocks`, `client`, `clouds`,
  `command`, `compat`, `config`, `data`, `event`, `items`, `manager`, `mixin`, `modules`, `network`,
  `particles`, `registry`, `seasons`, `telemetry`, `tools`, `util`.
- `manager/` (13 files) vs. `modules/` (163 files) is a legacy split predating the later
  reorganization that produced the dedicated `clouds/` package — `manager/` is thin orchestration,
  `modules/` holds most of the actual simulation logic. Not broken, just an artifact of the
  codebase's own history that a prior cleanup pass already partially addressed (see
  `Cleanup/cleanup_logs/`).
- 118 of 616 files (~19%) import `net.minecraftforge` directly — most of the codebase is already
  Forge-free at the import level. Coupling isn't diffuse; it's concentrated (see below).
- 67 files use a static `getInstance()` singleton-locator pattern; 64 files read the static
  `AtmoCommonConfig.X.get()` config holder directly. This is the actual dominant internal coupling
  style today — more than Forge-specific APIs, it's *global static state as the integration
  mechanism* between subsystems.
- 31 files already use `@SubscribeEvent` — the mod is already meaningfully event-reactive. But only
  4 custom (non-Forge) event classes exist anywhere in the codebase. In practice, "event-driven" here
  currently means "reacting to Forge/vanilla lifecycle events," not "modules publishing their own
  domain events for other modules to react to." Direct singleton method calls, not events, are how
  one subsystem currently talks to another.
- A real, measured porting cost already exists to point at: `git diff Forge-1.20.1
  origin/NeoForge-1.21.1 -- src/main/java` is **202 files changed, 2022 insertions(+), 3413
  deletions(-)** — about a third of the codebase touched for one loader/version port.

## Why "microservices" doesn't fit, and what to take from the instinct instead

Microservices exist to solve problems this project doesn't have: independent deployability,
independent scaling, network-boundary fault isolation, and teams that need to ship on different
schedules without coordinating a shared release. A Forge/NeoForge mod is one artifact, loaded into
one JVM, inside one Minecraft process, by one player. There's no network boundary to put between
"the cloud system" and "the tornado system" — they already share the same tick loop, the same
world state, and the same render frame. Introducing literal service boundaries (even in-process
ones with queues/message-passing between them) would add real latency and complexity to a
tick-synchronous simulation for no corresponding benefit — nothing here needs to scale
independently or survive another "service's" crash.

What's probably actually meant by reaching for that term is **modularity and reduced cross-module
coupling** — which is a real, legitimate want, and is what the "EDA" half of the question already
covers better. Read "microservices" in the rest of this document as "the modularity goal," not as a
literal architecture to adopt.

## EDA: mostly already here, formalize the seams, don't chase it as a goal in itself

The mod already behaves in an event-driven way — 31 files subscribing to lifecycle/tick/render
events is a real, working pattern, not a gap. What's missing is **domain** events: today, if the
storm system needs to know a cloud region dissolved, or the tornado system needs to know a cell was
reclassified, that almost certainly happens through a direct call into another module's singleton
(`XManager.getInstance().doThing()`), not through publishing an event the interested module
subscribed to. That's the real coupling cost — not "not enough events," but "coordination happens
through direct references to concrete singletons instead of through a boundary."

**Recommendation:** don't adopt EDA as a goal. Introduce a small internal domain event bus (plain
Java, not Forge's `EventBus`) for the handful of cross-module notifications that currently happen
through direct singleton calls across package boundaries (cloud ↔ tornado ↔ hurricane ↔ wind being
the most likely candidates, given how often those systems reference each other in the cloud
investigation this session). Forge's own lifecycle events can keep being consumed directly where
they already are — there's no value in wrapping every `@SubscribeEvent` in a translation layer.
Where this *does* pay off directly is porting: a custom domain event is zero-Forge-API by
construction, same benefit as the ports work in the clouds document.

## Hexagonal: worth it, scoped to four seams, not a rewrite

The instinct is right, but applied to the whole 616-file mod at once it's a multi-month,
high-regression-risk undertaking against a codebase that, per the numbers above, is already
Forge-clean in most places. The actual porting pain (202 files) wasn't spread evenly across all
616 — the clouds investigation found it concentrated in networking specifically (6 of the top 8
most-churned cloud files were hand-rolled packet classes). That pattern very likely repeats
mod-wide, since the same networking API is used the same way throughout. The right scope is the
seams that repeatedly cost real lines on a real port, not universal ports-and-adapters coverage:

1. **Networking** (same recommendation as the clouds document, applied mod-wide): one
   `NetworkTransport` port, one Forge adapter, every packet class becomes plain data.
   Highest-confidence ROI — it's the one already proven by the branch diff.
2. **Config access.** 64 files read `AtmoCommonConfig` directly, which means 64 files are coupled
   to `ForgeConfigSpec` whether they need to be or not — most of them just want a `float` or
   `boolean`. A `ConfigPort` interface (`getFloat(key)`, `getBoolean(key)`, …) backed by one
   `ForgeConfigAdapter` would let those 64 call sites stop caring which config framework exists
   underneath, and incidentally makes config values trivially mockable for testing (this project's
   `gradlew test` currently discovers zero automated tests, per the earlier remediation report —
   worth connecting these two facts).
3. **Persistence (`SavedData`).** Vanilla API, not Forge-specific, but it changed between MC
   versions in this project's own measured diff (`CloudRegionSavedData` alone was 36 changed lines).
   Worth a thin `RegionStateStore` port even though it sits on the "MC version" axis rather than the
   "Forge vs. NeoForge" axis — it's still real, recurring churn.
4. **Event registration/dispatch**, as described above.

Everything else — the 0%-Forge-coupled domain packages that already exist (this session confirmed
six of twelve `clouds/` subpackages have zero direct Forge imports today) — should be left alone.
They're already the shape hexagonal architecture wants; formalizing them further with ceremony
(explicit `Port`/`UseCase` interfaces for logic that has no actual second implementation or seam)
would add abstraction without a corresponding real cost it's paying down.

## Direct answer to "is it worth it"

- **Full hexagonal/microservice rewrite of the whole mod: no.** Cost (months, high regression risk
  on a mod that already works and already has one very expensive unrelated saga behind it this
  engagement) clearly exceeds benefit given most of the codebase isn't where the measured pain is.
- **Scoped ports at the four seams above, applied incrementally as those files are touched anyway
  (not a dedicated rewrite sprint): yes.** This is the same shape of recommendation as the clouds
  package, at the same confidence level, because it's backed by the same evidence — a diff you can
  already produce from your own two branches, not a hypothetical.
- **Formal EDA as an architectural goal: no. Targeted internal domain events at 3-4 identified
  cross-module coupling points: yes**, primarily because it's nearly free once the pattern exists
  and it directly reduces the same kind of static coupling the config/networking work is already
  targeting.

## Suggested order, if this is picked up

1. Networking port (clouds package first, since it's already scoped and evidenced; extend mod-wide
   once the pattern is proven there).
2. Config port — mechanical, low-risk, high file-count payoff (64 call sites), good second target.
3. Persistence port — smaller surface, do alongside whichever module is next touched for other
   reasons rather than as a dedicated pass.
4. Internal domain events — only after 1-3 are stable, since it's the least urgent and the most
   likely to be over-designed if done first without real coupling points already identified from
   experience applying 1-3.

Do not start with a "define the target architecture, then migrate everything" plan. Every
concrete recommendation in this document and its clouds companion came from measuring what actually
happened on a real port, not from designing an ideal structure in the abstract — keep using that
method for whatever comes next, the same way the storm-structure investigation used a live
diagnostic instead of a shader rewrite.
