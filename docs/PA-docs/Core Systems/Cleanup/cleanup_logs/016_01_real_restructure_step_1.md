# Real Restructure Step 1

## Step goal
Move the debug-only particle atlas logger into the debug tools package so `util/` no longer owns a debug-only class.

## Files changed
- `src/main/java/net/Gabou/projectatmosphere/util/ParticleAtlasDebugger.java` (deleted)
- `src/main/java/net/Gabou/projectatmosphere/tools/debug/ParticleAtlasDebugger.java` (added)

## Files moved
- `src/main/java/net/Gabou/projectatmosphere/util/ParticleAtlasDebugger.java` -> `src/main/java/net/Gabou/projectatmosphere/tools/debug/ParticleAtlasDebugger.java`

## Classes renamed
- None

## Classes split
- None

## Classes merged
- None

## New helper classes created
- None

## Methods reordered
- None

## Legacy/debug code moved
- The entire class was moved because it is a debug-only utility; no internal method-level cleanup was needed.

## Call sites updated
- None were required; no external call sites were found in the source tree.

## Imports updated
- None were required beyond the package move itself.

## Build result
- `.\gradlew.bat build` succeeded

## Behavior risk review
- Low risk. The class is event-driven debug logging only, with no known production call sites and no behavior changes.

## Rollback notes
- If a hidden reference appears later, revert the file move and restore the original package path.

## Next recommended step
- Move the temporary cloud boundary scaffolding out of the generic `clouds/` package into a clearer renderer-boundary package, if still consistent with the planned refactor sequence.

