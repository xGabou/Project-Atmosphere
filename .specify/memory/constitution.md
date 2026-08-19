<!--
Sync Impact Report
- Version change: template -> 1.0.0
- Established principles:
  - I. Forge 1.20.1 and Java Baseline
  - II. Architectural Integrity and Modularity
  - III. Server Authority and Explicit Synchronization
  - IV. Tick, Allocation, and Async Discipline
  - V. Compatibility and Dependency Restraint
  - VI. Regression Protection
  - VII. Configuration and Diagnostics
  - VIII. Focused Brownfield Delivery
- Added sections:
  - Technical and Compatibility Constraints
  - Feature Workflow and Quality Gates
- Removed sections: none (initial ratification)
- Templates checked: plan, specification, tasks, checklist; no changes required
- Follow-up TODOs: none
-->
# Project Atmosphere Constitution

## Core Principles

### I. Forge 1.20.1 and Java Baseline

Project Atmosphere MUST remain a Java mod targeting Minecraft Forge 1.20.1 unless a separately
approved migration specification changes that baseline. Production code, resources, mappings,
Gradle configuration, and tests MUST remain compatible with the repository's supported Java and
Forge toolchain. A feature MUST NOT silently introduce APIs from another Minecraft, Forge, or Java
version.

### II. Architectural Integrity and Modularity

Changes MUST preserve the existing Project Atmosphere architecture unless the feature plan records
a clear technical reason for changing it. New behavior MUST be placed in modules with one clear
responsibility and MUST use existing managers, APIs, services, repositories, platform boundaries,
and compatibility layers when they already own the concern. Shared behavior MUST have one
authoritative implementation; copy-pasted or independently diverging client/server logic is not
acceptable. A feature MUST NOT redesign unrelated systems.

### III. Server Authority and Explicit Synchronization

The logical server MUST remain authoritative for persistent world state, weather simulation,
dimensions, hazards, and gameplay decisions. Client prediction or presentation MUST NOT become an
untracked source of truth. Every client/server state flow MUST define its owner, packet or snapshot
contract, update trigger, ordering assumptions, validation, and behavior for login, dimension
change, disconnect, and stale or missing data. Packet and saved-data compatibility MUST be assessed
whenever their schemas or lifecycle change.

### IV. Tick, Allocation, and Async Discipline

Frequently executed code MUST avoid unnecessary per-tick work and avoid avoidable allocations in
hot paths. Work MUST be event-driven, cached, batched, rate-limited, or scheduled when equivalent
behavior permits. Expensive environmental, forecast, scanning, and aggregation calculations MUST
run asynchronously when it is safe to do so. Async systems MUST document thread ownership, use
thread-safe state transfer, avoid accessing thread-confined Minecraft objects off-thread, handle
cancellation and shutdown, and marshal mutations back to the correct game thread.

### V. Compatibility and Dependency Restraint

Supported integrations—including Serene Seasons, Simple Clouds, GeckoLib, and the repository's
other compatibility modules—MUST keep their optional-loading and ownership boundaries intact.
Features MUST work when an optional integration is absent unless the feature explicitly requires
it. New dependencies MAY be added only when existing Java, Forge, Minecraft, or repository
facilities cannot reasonably satisfy the requirement; the plan MUST justify their maintenance,
runtime, licensing, and compatibility costs.

### VI. Regression Protection

Changes affecting world loading, saved data, networking, dimensions, weather simulation, forecast
generation, or rendering MUST include proportionate regression coverage and an explicit manual or
automated validation path. Existing formats and behavior MUST be preserved unless the specification
defines a migration or intentional compatibility break. Failures in optional integrations MUST not
prevent unrelated core systems from loading where isolation is technically possible.

### VII. Configuration and Diagnostics

New configurable systems MUST expose Forge configuration options when operators or players have a
meaningful policy, quality, performance, or gameplay choice. Defaults MUST preserve established
behavior or be justified in the specification. Complex, asynchronous, stateful, simulation, and
rendering systems MUST provide useful debug commands, structured diagnostics, or bounded telemetry
when those facilities are needed to verify ownership, synchronization, performance, or failure
modes. Diagnostics MUST avoid excessive normal-operation logging.

### VIII. Focused Brownfield Delivery

Every feature or refactor MUST be scoped to a reviewable outcome with explicit acceptance criteria.
Plans and tasks MUST identify the existing code path being extended, the boundaries that remain
unchanged, and any compatibility risk. Opportunistic cleanup MAY be included only when it is small,
directly enables the requested change, and is called out in the plan. Large unrelated cleanup or
whole-project redesign requires its own specification.

## Technical and Compatibility Constraints

- The existing Gradle, Forge run configurations, Java package structure, `src/main`, `src/test`,
  assets, data resources, mixins, and mod metadata are the default project layout and MUST NOT be
  moved or replaced without an approved migration plan.
- Server-only code MUST remain safe on dedicated servers. Client classes and rendering APIs MUST
  stay behind established client registration and distribution boundaries.
- Saved data and network inputs MUST be bounded and validated. Schema changes MUST define backward
  compatibility, migration, protocol handling, or a documented intentional break.
- Optional compatibility code MUST use the repository's existing detection and adapter layers. It
  MUST NOT directly take ownership away from the selected cloud, season, platform, or rendering
  backend.
- Performance-sensitive changes MUST state the execution frequency, allocation behavior, thread,
  cache lifetime, and invalidation strategy in the plan.

## Feature Workflow and Quality Gates

- Spec Kit is used for individual Project Atmosphere features and refactors through
  `specify -> plan -> tasks -> implement`. It MUST NOT generate a specification for the entire mod.
- A feature specification MUST describe user-visible or operator-visible outcomes, edge cases,
  success criteria, compatibility expectations, and excluded unrelated work.
- A plan MUST map the feature onto current managers, services, APIs, data ownership, client/server
  boundaries, persistence, configuration, integrations, and diagnostics as applicable. Its
  Constitution Check MUST pass before implementation; justified exceptions belong in Complexity
  Tracking.
- Tasks MUST remain dependency-ordered and reviewable. Applicable tests and diagnostics MUST be
  implemented and run before the feature is considered complete.
- Validation MUST use the smallest relevant Gradle checks first, followed by broader build and
  runtime checks proportionate to the affected regression surfaces. Rendering and integration
  changes require representative in-game validation in addition to compilation.
- Implementations MUST preserve unrelated working-tree changes and report exactly which files they
  create, modify, migrate, or remove.

## Governance

This constitution is the binding engineering policy for Spec Kit artifacts in Project Atmosphere.
If a specification, plan, or task conflicts with a MUST rule, the artifact MUST be corrected before
implementation unless the constitution is formally amended.

Amendments require a documented rationale, maintainer approval, an impact review covering existing
specifications and templates, and a version update. Governance follows semantic versioning: MAJOR
for removing or redefining a principle incompatibly, MINOR for adding a principle or materially
expanding obligations, and PATCH for non-semantic clarification. Each amendment MUST update the
Sync Impact Report and Last Amended date.

Every feature review MUST verify the Constitution Check, scope discipline, compatibility impact,
client/server ownership, performance implications, and regression evidence. Complexity or an
exception without an explicit technical justification blocks completion.

**Version**: 1.0.0 | **Ratified**: 2026-08-17 | **Last Amended**: 2026-08-17
