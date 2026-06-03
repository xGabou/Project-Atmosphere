# 024_aggressive_remaining_risky_restructure

- Batch number: 024
- Target modules: Remaining risky manual review set, render-side helpers and client hurricane cache
- Files reviewed: Remaining risky matrix entries touched by this pass
- Files changed: 5 source files plus docs/log updates
- Cleanup type: Risky-file package restructuring
- Build checkpoints: `.\gradlew.bat build` succeeded after the package move group
- Short notes: Moved shader helpers, sky-effect state, the DH pipeline selector, and the client hurricane cache into clearer packages. Updated imports and call sites, then reconciled the remaining-risk matrix and cleanup index.
