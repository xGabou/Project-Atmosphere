# Manual Cleanup Checklist

Use this checklist later when you are ready to reorganize safely.

1. Read `00_project_structure_overview.md` and confirm the module boundaries are still accurate.
2. Read `01_module_dependency_map.md` and mark the dependencies that are clearly clean versus clearly confusing.
3. Read `03_class_dependency_matrix.md` and choose the smallest set of classes that actually block the cloud renderer boundary.
4. Mark all debug-only or diagnostic classes in `05_unused_code_and_imports_audit.md` as documentation targets, not deletion targets.
5. Decide which names are truly misleading in `04_package_and_naming_audit.md`, and leave cosmetic concerns for later.
6. Lock in the fake cloud boundary before attempting any real cloud data flow changes.
7. Keep `SimpleCloudsTornadoRenderer`, `SimpleCloudsHurricaneRenderer`, `SimpleCloudsCompat`, `AtmosphereManager`, and `ForecastOrchestrator` untouched until the boundary is proven.
8. If any cleanup is attempted, start with documentation comments or legacy-section organization only.
9. Verify that no cleanup changes the authoritative source of weather or storm state.
10. Only after the fake cloud renderer works, revisit split/combine candidates and package moves.

