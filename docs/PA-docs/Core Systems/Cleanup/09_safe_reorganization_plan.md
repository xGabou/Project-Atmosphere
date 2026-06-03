# Safe Reorganization Plan

The plan below is intentionally conservative. It does not propose immediate code movement. It describes the order in which later cleanup can be done safely.

| Phase | Goal | Allowed changes later | Forbidden changes | Files involved | Risk level | Exit criteria |
|---|---|---|---|---|---|---|---|
| Phase A Documentation only | Capture current ownership and module roles | Markdown docs, comments in audit folders only | Source changes, renames, behavior changes | Audit docs only | Low | Module and class responsibilities are documented clearly |
| Phase B Unused import and dead code report | Identify suspicious debug and legacy code | Documentation of suspicious items, manual notes | Deletion, code movement, behavior changes | Audit docs plus review notes | Low | Suspicious items are listed with confidence and risk |
| Phase C Package and naming decisions | Decide which names/packages are genuinely misleading | Documentation of rename/move candidates | Actual renames or moves | Audit docs only | Low to medium | Move/rename candidates are prioritized and justified |
| Phase D Small safe cleanup | Prepare low-risk cleanup targets | Later comment blocks, possibly local helper extraction after approval | Broad refactors, cross-module moves | Only after new approval | Medium | A small set of low-risk targets is isolated |
| Phase E Dependency boundary cleanup | Separate source-of-truth layers | Narrowing services, isolating snapshots, clarifying adapters | Changing behavior or moving major gameplay logic | Managers, compat, client cache, render boundary docs | High | Snapshot/cache boundaries are explicit and documented |
| Phase F Cloud renderer boundary work | Build the fake debug cloud boundary | Isolated client boundary classes only | Real weather integration, shadow systems, Simple Clouds refactor | Future cloud boundary files only | High | Fake cloud renders from a stable cache without live simulation reads |
| Phase G Larger refactors after fake cloud works | Clean broad classes once the renderer boundary is proven | Larger split/rename/move operations | Changing the established renderer boundary or simulation authority | Broad manager/client/render classes | Very high | Fake cloud path is stable and PA-driven replacement is understood |

## Notes

- The safest reorganization sequence is documentation first, boundary first, then small isolated cleanup.
- The future cloud renderer should be the proof point before any broad structural changes are attempted.

