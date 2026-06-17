# Remaining Files Restructure

## Handled files registry created
- `temp_project_reorganization_audit/restructure_execution_plan/02_true_handled_files.md`

## Remaining files reviewed
- 332 remaining Java source files under `src/main/java/` after excluding:
  - the protected `net.Gabou.projectatmosphere.clouds` package
  - all true handled files from `02_true_handled_files.md`

## Files skipped because already truly handled
- All entries listed in `02_true_handled_files.md`

## Files changed
- `src/main/java/net/Gabou/projectatmosphere/client/ClientRenderHook.java`
- `src/main/java/net/Gabou/projectatmosphere/client/SimpleCloudsWhiteoutFogHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/client/TornadoClientEffects.java`
- `src/main/java/net/Gabou/projectatmosphere/tools/TodoGUI.java`
- `src/main/java/net/Gabou/projectatmosphere/tools/TodoPrinter.java`
- `src/main/java/net/Gabou/projectatmosphere/client/ClientTickHandler.java`

## Files moved
- `src/main/java/net/Gabou/projectatmosphere/client/SimpleCloudsWhiteoutFogHandler.java` -> `src/main/java/net/Gabou/projectatmosphere/client/fog/SimpleCloudsWhiteoutFogHandler.java`
- `src/main/java/net/Gabou/projectatmosphere/client/TornadoClientEffects.java` -> `src/main/java/net/Gabou/projectatmosphere/client/render/TornadoClientEffects.java`
- `src/main/java/net/Gabou/projectatmosphere/tools/TodoGUI.java` -> `src/main/java/net/Gabou/projectatmosphere/tools/todo/TodoGUI.java`
- `src/main/java/net/Gabou/projectatmosphere/tools/TodoPrinter.java` -> `src/main/java/net/Gabou/projectatmosphere/tools/todo/TodoPrinter.java`

## Classes renamed
- None

## Classes split
- None

## Classes merged
- None

## Empty classes deleted
- `src/main/java/net/Gabou/projectatmosphere/client/ClientRenderHook.java`

## Facades kept and why
- None. The emptied `ClientRenderHook` had no public facade requirement and was deleted.

## Imports updated
- `src/main/java/net/Gabou/projectatmosphere/client/ClientTickHandler.java`

## Call sites updated
- `src/main/java/net/Gabou/projectatmosphere/client/ClientTickHandler.java`

## Build result
- `.\gradlew.bat build` succeeded

## Remaining files that still need manual review
- 76 files were classified as `TRUE_HANDLED_RISKY_MANUAL_REVIEW` in `03_remaining_files_handling_matrix.md`.
- The other 256 remaining files were classified as `TRUE_HANDLED_GOOD_AS_IS`.
- These are broad, mixin-heavy, or render/sync-sensitive classes where automatic refactoring would be too risky without a dedicated design step.
