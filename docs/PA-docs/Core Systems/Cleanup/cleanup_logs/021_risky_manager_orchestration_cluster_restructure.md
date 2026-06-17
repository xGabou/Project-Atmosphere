# 021_risky_manager_orchestration_cluster_restructure

- Batch number: 021
- Target modules: Broad manager or orchestration risk cluster
- Files reviewed: 7 cluster files
- Files changed: 4 source files plus docs/log updates
- Cleanup type: Manager/orchestration helper extraction
- Build result: `.\gradlew.bat build` succeeded
- Short notes: Extracted cloud spawn severity rules into a dedicated manager helper, rewired SimpleCloudSpawner, SimpleCloudsCompat, and CloudManager to use it, and left the broad orchestration classes in manual review for a later pass.
