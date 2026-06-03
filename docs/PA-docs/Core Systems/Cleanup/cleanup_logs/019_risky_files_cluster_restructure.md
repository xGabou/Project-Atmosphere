# Risky Files Cluster Restructure

## Risk clusters created
- `temp_project_reorganization_audit/restructure_execution_plan/04_risky_files_cluster_plan.md`

## Cluster selected
- Render pipeline and shader risk, helper subcluster

## Why selected
- It was the lowest-risk subset inside the risky set that could be changed without altering behavior.
- `SideInfo` and `VolumeBoxMesh` were plain render helpers with safe ownership improvements.

## Files changed
- `src/main/java/net/Gabou/projectatmosphere/client/render/SideInfo.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/VolumeBoxMesh.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsTornadoRenderer.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/SimpleCloudsHurricaneRenderer.java`
- `temp_project_reorganization_audit/restructure_execution_plan/02_true_handled_files.md`
- `temp_project_reorganization_audit/cleanup_logs/018_remaining_files_restructure.md`
- `temp_project_reorganization_audit/cleanup_logs/cleanup_index.md`

## Files moved
- `src/main/java/net/Gabou/projectatmosphere/client/render/SideInfo.java` -> `src/main/java/net/Gabou/projectatmosphere/client/render/mesh/SideInfo.java`
- `src/main/java/net/Gabou/projectatmosphere/client/render/VolumeBoxMesh.java` -> `src/main/java/net/Gabou/projectatmosphere/client/render/mesh/VolumeBoxMesh.java`

## Classes split
- None

## Classes deleted
- `src/main/java/net/Gabou/projectatmosphere/client/ClientRenderHook.java`

## Files still risky
- 74 risky files remain classified as `TRUE_HANDLED_RISKY_MANUAL_REVIEW`.
- The highest-risk remaining areas are mixins, broad managers, and storm lifecycle classes.

## Build result
- `.\gradlew.bat build` succeeded

## Next cluster
- Client state or tick lifecycle risk, if you want a follow-up focused on safe client-state ownership work.
