# 09_aggressive_remaining_risky_resolution

## Goal
Resolve the remaining risky manual-review files with real package moves and helper extraction where the behavior surface is stable enough.

## Completed wave
- Moved render shader helpers into `client/render/shader/`
- Moved sky effect state into `client/render/sky/`
- Moved the DH pipeline selector into `client/render/pipeline/`
- Moved the client hurricane state cache into `client/hurricane/cache/`

## Remaining clusters
- Broad managers and orchestration
- Mixin targets and injection surfaces
- Renderers with shader binding and draw-order sensitivity
- Config and save-format sensitive classes
- Broad lifecycle managers for tornado, hurricane, and atmosphere runtime

## Next rule
- Only continue with another group if there is a clear package/ownership win that does not alter behavior or render flow.
