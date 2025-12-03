# Project Atmosphere

### Added
- Region-first plumbing: region orchestrator scaffolding, region-based sampling APIs, and unified region key (`RegionInstanceKey`) across wind/state/spike/orchestrator.

### Changed
- Spikes are region-only (removed from biome generation); player movement tracking is region-based (regen when entering a new region or moving ~80% of region size).
- Cloud sampling uses region centers; far clouds culled; SimpleClouds spawns hard-capped at 10k from players and biased closer.
- Removed pattern cloud type

### Fixed
- Partial fix for cloud coverage.

### Notes
- Region-first runtime: unified region key and region-based APIs simplify lookups and reduce biome churn.
- Region-only spikes and region movement tracking reduce regen spam (regen on new region or ~80% region-size move).
- Cloud behavior: sampling uses region centers, far clouds culled, SimpleClouds spawns hard-capped at 10k and biased toward nearby players to keep cloud effects relevant.
- Next steps: further optimize region management, enhance cloud dynamics, and refine spike integration with biome features.


## AS ALWAYS UPDATING TO THIS VERSION REQUIRES DELETING YOUR OVERWORLD FOLDER (created by Project Atmosphere) TO AVOID ISSUES WITH THE NEW REGION SYSTEM.
- Go to your worlds folder and delete the "overworld" folder to allow the new region system to generate fresh regions.
- Note that this will reset the current forecast and could introduce desynced weather effects if the forecast isn't regenerated properly. (/temperature regenerate)

