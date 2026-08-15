# Core Systems Documentation Map

This folder is the main reference point for weather, cloud, storm, and renderer-boundary work.

## Start Here

For cloud rendering specifically: `Clouds/CLOUD_RENDERING_OVERVIEW.md` — current as of 2026-08-15,
verified against a live client. It supersedes the reading list that used to be here; those planning
docs (`cloud_backend_start_here.md`, `cloud_render_data_contract.md`,
`cloud_renderer_integration_points.md`, `phase0_cloud_boundary/`) predate the native renderer's
implementation and were moved to `Clouds/archived/`.

## Supporting Material

- `Renderer/codebase_audit/backend_readiness_for_cloud_renderer.md` (note: this file's own
  "canonical entry point" pointer is now stale — see `Clouds/CLOUD_RENDERING_OVERVIEW.md` instead)
- `Renderer/codebase_audit/class_responsibility_matrix.md`
- `Renderer/codebase_audit/renderer_blockers.md`
- `Studies/runtime-atmosphere-coupling-study.md`
- `Studies/pa_realistic_cloud_renderer_design.md`

## Cleanup and Refactor History

- `Cleanup/cleanup_logs/`
- `Cleanup/restructure_execution_plan/`

Those two folders are intentionally kept separate from the main docs flow.

