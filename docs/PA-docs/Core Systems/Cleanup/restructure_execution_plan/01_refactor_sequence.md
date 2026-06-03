# Refactor Sequence

This sequence is ordered by safety and leverage. Step 1 is intentionally the lowest-risk meaningful real refactor.

## Step 1

| Step number | Refactor target | Why this step comes now | Files/classes involved | Allowed changes | Forbidden changes | Expected result | Build command | Rollback condition |
|---|---|---|---|---|---|---|---|---|
| 1 | Move the debug-only particle atlas logger into the debug tools package | It is a pure debug utility with no known external references, and moving it improves package ownership without touching behavior | `util/ParticleAtlasDebugger` -> `tools/debug/ParticleAtlasDebugger` | Move file, update package declaration, preserve event subscription and logging behavior | No logic change, no behavior change, no extra refactor, no call-site rewiring beyond import/package correctness | Debug utility lives with other debug tools and no longer sits in `util/` | `.\gradlew.bat build` | Revert the move if the build fails or if any hidden reference appears |

## Step 2

| Step number | Refactor target | Why this step comes now | Files/classes involved | Allowed changes | Forbidden changes | Expected result | Build command | Rollback condition |
|---|---|---|---|---|---|---|---|---|
| 2 | Begin the cloud render boundary package cleanup | After the debug utility move, the next safest structural win is to group temporary cloud boundary classes away from `util/`-style space | `clouds/CloudRenderSnapshot`, `clouds/CloudRenderStateCache`, `clouds/CloudDebugSnapshotFactory` | Move the temporary cloud boundary classes to a clearer future boundary package, update packages/imports, keep behavior stubbed | No renderer implementation, no snapshot semantics change, no real cloud pipeline changes | Temporary cloud boundary code is isolated from general utilities | `.\gradlew.bat build` | Revert if the package move creates any unexpected dependency churn |

## Step 3

| Step number | Refactor target | Why this step comes now | Files/classes involved | Allowed changes | Forbidden changes | Expected result | Build command | Rollback condition |
|---|---|---|---|---|---|---|---|---|
| 3 | Narrow client cache boundaries around cloud-facing state | Once the boundary scaffolding is isolated, the client cache story can be made explicit without changing render behavior | `client/AtmosphereClientState`, `client/AtmosphereFogState`, `client/hurricane/ClientHurricaneStateCache`, `client/loading/*` | Reorder methods and split clearly unrelated debug helpers if safe | No packet behavior, no fog behavior, no render logic change | Client-side state reads become easier to trace | `.\gradlew.bat build` | Revert if any lifecycle order becomes unclear or build fails |

## Step 4

| Step number | Refactor target | Why this step comes now | Files/classes involved | Allowed changes | Forbidden changes | Expected result | Build command | Rollback condition |
|---|---|---|---|---|---|---|---|---|
| 4 | Reduce broad manager ownership one class at a time | Managers were identified as the biggest long-term architectural risk, but they should only be split once the safer boundaries are in place | `manager/AtmosphereManager`, `manager/ForecastOrchestrator`, `manager/ForecastGenerator`, `manager/SimpleCloudSpawner` | Internal helper extraction and safe sectioning only | No behavior change, no gameplay change, no sync format change | Managers become easier to audit for future cloud work | `.\gradlew.bat build` | Revert if the diff becomes noisy or risky |

## Step 5

| Step number | Refactor target | Why this step comes now | Files/classes involved | Allowed changes | Forbidden changes | Expected result | Build command | Rollback condition |
|---|---|---|---|---|---|---|---|---|
| 5 | Later cloud renderer boundary alignment | This is only safe after the temporary boundary classes, client cache boundaries, and manager boundaries are clearer | `client/render/*`, `modules/tornado/*`, `modules/hurricane/*`, `clouds/*` | Small contract alignment and helper extraction only | No cloud rendering implementation, no shader logic change, no pipeline changes | Renderer-facing contracts become easier to wire later | `.\gradlew.bat build` | Revert if any render path behavior changes |

