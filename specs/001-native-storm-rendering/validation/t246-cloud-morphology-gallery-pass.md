# T246 - Cloud morphology pass from the production gallery

Branch `quality/t217-deep-interior-lighting`, from HEAD `114b4909` (production gallery). Morphology only:
lighting (T244), rain / lower-trunk lighting (T226), temporal reconstruction and performance are out of
scope unless a candidate needs validation. Reference dataset:
`validation/cloud-shape-gallery/<family>/<view>.png` (9 families, 126 views, one realization per folder).

## 1. Audit (every family, all views)

| family | strongest views | weakest views | concrete defect | controlling mechanism | severity |
|---|---|---|---|---|---|
| cumulus_mediocris | side, below | close, front, above | (a) hard **vertical planar walls** and rectangular slab edges cut through the cloud (close / front / side); (b) from above / below **radial star creases** converging on each lobe axis; (c) above view is a **symmetric four-leaf X** | (a) T243 turret bumps grow a lobe's implicit surface up to 1.65 x its radius, but `directPuffLobeSample` returns 0 for `radial >= 1.05` (a vertical cylinder) and `PuffLobeSpatialIndex` registers a lobe only within radius + half a tile diagonal - the bulges are cut flat; (b) the bump `sqrt(max(sin(3θ+s)·sin(10h+…),0))` is zero on half the sectors with a kink at each zero crossing; (c) four base lobes at 90 degrees | HIGH |
| cumulus_congestus | side, opposite_side, tower | close, above | same vertical walls / slab facets on the turrets (close), star creases from above; narrow column with smeared flanks | same as mediocris (turret bump vs 1.05 cut-off and tile footprint) | HIGH |
| cumulus_humilis | side, close | below | below view shows **square-cornered, cross-shaped lobes** (planar cuts) | same cut-off; small lobes make the cut a large fraction of the outline | HIGH |
| nimbostratus | below, underside, above | side, opposite_side, close, front, back | a **tall bulbous central capsule** with thinner "wings" and flat **vertical box walls**; not one layer of uniform thickness | per-member rim taper (NS3: `edgeDrop = edge01² · 0.55 · span` per member) averaged across ~15 members - the overlapping centre keeps full height, outer members slope down; height steps between weather texels read as walls | HIGH |
| stratocumulus | below, underside, low_angle | above, close | broken, semi-connected patches (acceptable); flat dark slab edges under some clumps; above view somewhat fragmented | cellular-sheet spacing / member size (SC2) and per-member vertical interval | MEDIUM |
| cumulonimbus | side, opposite_side, close, back | far (small by design), above | architecture reads well (continuous trunk, thin wedge anvil, turret texture); tower fairly uniform in width | storm body (T240/T241) | LOW |
| supercell | side, close, back, anvil | above | as cumulonimbus | storm body | LOW |
| stratus_nebulosus | above, below, underside, low_angle | level views (thin line) | thin continuous deck; level views are a line by nature | - | LOW |
| cirrus | below, upwind, downwind, underside | level views (edge-on) | thin fibrous streaks; level views nearly empty by nature | - | LOW |

## 2. Ranking

1. **Cumulus family** - artefacts (planar walls, star creases) visible from almost every view, three
   families.
2. **Nimbostratus** - the capsule / box body dominates every level view.
3. Stratocumulus (MEDIUM), then the storm family, stratus and cirrus (LOW, no change planned unless a
   higher fix exposes something).

## 3. Iterations

All cumulus candidates were shot in-session as arms on the SAME instance (cross-session realizations differ),
monolith program with the temporary `PaShapeCandidate` switch, 12 views per family, humilis / mediocris /
congestus. Captures: `validation/t246-campaign/<family>/<candidate>/<arm>/<view>.png`.

| id | change | result | verdict |
|---|---|---|---|
| CU-A | turret bumps `sin²·sin`-shaped (no zero-crossing kink), growth saturated to the lobe's 0.95 headroom (`h·(1-exp(-raw/h))`) | radial star creases gone from above / below; walls unchanged | kept (pending) |
| CU-B | CU-A + empty tiles bordering an indexed tile stay on the analytic path (no weather-map fallback at the fringe) | no visible change against CU-A | rejected - fallback fringe is not the wall source |
| CU-C | CU-A + lobe cylinder cut-off 1.05 -> 1.25 (billows can cross 1.05) | identical to CU-A in every view | rejected - the cut-off is not the wall source |

The spatial index was confirmed complete in all three sessions (`complete=true`, ≤ 8 lobes, `overflow=0`),
so tile truncation is excluded too. Two remaining mechanisms were found by reading the path:

* **dominant-lobe height**: the primary march's lighting `h01` for a puff body (and the density material
  height) is the LOCAL height of whichever lobe is strongest at the sample. Across a junction it jumps
  (e.g. 0.9 at the top of a base lobe -> 0.1 at the root of a turret) on an almost planar surface, and
  `h01` drives the underside shade (`pow(1-h, 0.7)`), the ambient bottom/top mix and the T244 lower weight -
  a hard bright/dark wall that exposes the primitive. (CU-D: blend lobe base / top by envelope depth².)
* **raster boundary**: the puff body is still gated by the weather-map coverage (`coverage > 0.008`, the final
  `smoothstep(coverageMod)`) and the morphology profile at `p.xz` - storms were exempted from exactly this
  ("height-independent vertical wall") but puffs were not; analytic lobes that reach past the raster
  footprint are cut flat with square corners. (CU-E: an indexed puff tile owns its boundary.)

Session `t246cud` (arms A control, C CU-A, G CU-A + analytic-global (`PuffShapeMode` 2 forced), H CU-A + CU-D,
I CU-A + CU-E, J CU-A + CU-D + CU-E):

| id | result | verdict |
|---|---|---|
| G (analytic-global) | identical to C | the hybrid weather-map fallback is not the wall source |
| CU-D | identical to C at review scale | rejected as a wall fix (no visible effect) |
| CU-E | identical to C | rejected - the raster gates do not cut the walls |

The walls - and a hard-edged bright DIAMOND (a world-axis square seen from below) in humilis `below`, also
present in the production reference gallery - survive every density-path change. Both point at the shared
storm field (T196/T199), which production runs for every frame and which knows only storms:

* the primary march's empty-space **probe** (`paStormFieldMaterialPossible`, nearest 16-block node) answers
  "provably empty" for every node whose 3x3 weather-raster window is unpainted
  (`paStormFieldNonStormPossible`); an analytic puff lobe reaching past its raster is skipped there - a cut
  along the 16-block node grid. (CU-F: a node whose dual cell touches an indexed puff tile is uncovered.)
* the **light cone** reads non-storm material from the node density channel (`A`, a point sample per 16-block
  node, bilinear): a 30-block puff's shadow follows the bilinear tent's diamond / square contours.
  (CU-G: puff-indexed light samples evaluate the exact density.)

Session `t246cuf` (arms A control, C CU-A, K CU-A + CU-F, L CU-A + CU-G, M CU-A + CU-F + CU-G,
N M + CU-D + CU-E):

| id | result | verdict |
|---|---|---|
| CU-F | **the walls are gone**: humilis loses its dark sliced-off block (below and above), mediocris its dark planar slab on the turret, congestus the cut chip on its flank; the silhouettes become continuous rounded lobes | **accepted** |
| CU-G | stronger lobe contrast, but exposes a lobe DISC outline from below (humilis) and is a lighting / cost change the goal excludes | rejected |
| M / N | as K plus CU-G's discs | rejected |

The white square in humilis `below` (all arms, all sessions, reference gallery too) is the vanilla SUN quad: the
`below` camera looks up at pitch -85 at noon through a thin cloud. Not cloud geometry.

Root cause of the cumulus walls: the T196/T199 shared storm field's probe classified non-storm material only by
the weather raster, so analytic PUFF lobes outside their raster were provably "empty" and the march stepped over
them - flat, world-axis-aligned cuts on the 16-block node grid. The field and the puff candidate index share the
same 256-cell grid over `WeatherExtent` (node cell == puff tile), and a tile is registered for every lobe point
to radial `1 + halfDiagonal / r` >= the 1.05 lobe cut-off, so the production fix is ONE candidate fetch at the
node centre.

Multi-instance check (session `t246cuv`, arms A control / K CU-A + CU-F, THREE instances of every cumulus
family, 12 views each, 216 shots): K removes the cut blocks in every instance - humilis `below` dark wedge,
mediocris `below` / `above` sliced corners, congestus `above` jagged facets and the large square block in
congestus i3 `below` - and changes nothing else. Two A frames (`side` of the first instance of a family) are
stale teleport frames (a distant tiny cloud), a known harness settle issue, not a renderer difference.

### Nimbostratus

All nimbostratus arms run on the monolith (control `M` = code 99, no candidate) so the program is not a
confound (the lean FINAL arm runs the temporal lattice, the monolith does not); three instances per session.

| id | change | result | verdict |
|---|---|---|---|
| NS-A | splat: member tops flat (`edgeDrop = 0`, was `edge01² · 0.55 · span` per member); volume: the deck top tapers only where TOTAL coverage thins (`smoothstep(0.82 - 0.45 r, 1 - 0.45 r, h01)`, `r = 1 - cov / 0.5`) | against the reference gallery (same views, three instances): the tall central capsule with thin wings becomes a broad body with a much more even top; a narrower stepped tail remains at one end | **kept** |
| P (diag 31) | light cone evaluates the exact density wherever the field node is uncovered | the dark rectangular PANELS on the deck face vanish - they are the field's 16-block node density used as non-storm light; beneath them the deck is a chain of faceted lumps | diagnostic: lighting, see recommendation |
| Q (diag 33) | march probe never skips | identical to M | probe not involved |
| NS-B | coverage normalisation 0.5 -> 0.20 / 0.12 (deck interior saturates) | WORSE: the carve stops engaging, exposing a smooth lathe-turned "bottle" with bulges and steps | rejected - the lumps are in the weather base / top (per-member heights), not the coverage taper |

| NS-C | volume: deck base / top low-passed (coverage-weighted 3x3 of the weather map at +-24 / +-48 blocks) | identical to M | rejected - not the base / top either |

The silhouette steps that remain at the deck's narrow end are not the coverage taper (NS-B), not the base / top
heights (NS-C), not the probe (Q) and not the material profile (`familyMacroShape` profile 5 has no per-member
term); they are left unexplained. The dominant remaining blockiness on the deck face is the light-field panel
artefact (P), a lighting question - see section 7.

## 4. Per-family result

### Cumulus (humilis, mediocris, congestus) - KEEP, production

Production change (`cloud_atmosphere_volume.fsh`, no switch):

1. `paStormFieldNonStormPossible`: a node whose cell is an indexed puff tile
   (`decodePuffCandidate(puffCandidatesAt(nodeXZ), 0) >= 0`) is uncovered, so the field probe never declares
   analytic puff material empty. One fetch per node in the build pass.
2. T243 turret bumps: `aroundA/B = 0.5 + 0.5 sin(3θ + s / 5θ - 2.3s)`, `upA/B = 0.5 + 0.5 sin(10.05h + 1.7s /
   15.08h - s)`, `raw = (0.40 aroundA² upA + 0.25 aroundB² upB) * upperPart`; growth
   `H (1 - exp(-raw / H))` with headroom `H = 0.95 / (eq sqrt(1 - v²)) - 1`.

Before / after: the vertical planar walls, square-cornered cut blocks and dark sliced-off chunks are gone from
side / close / front / above / below in all three families; the radial star creases from above / below are gone;
the overall outline, height, lobe layout and lighting are unchanged. Evidence sheets
(`t246-campaign/sheets/`): `ab_cu_c_zoom` (CU-C no effect), `ab_cu_d_hum` / `ab_cu_d_med` (CU-D / CU-E no
effect), `ab_cu_f` (CU-F / CU-G), `v_humilis` / `v_mediocris` / `v_congestus` (three instances each, A vs K).
Raw per-arm captures stay local (`t246-campaign/<family>/<candidate>/<arm>/`, 177 MB, untracked).

Still visible after the fix (not addressed): the symmetric four-lobe "X" plan from above on mediocris
(layout), a smooth flat-shaded body below the turrets.

### Nimbostratus - KEEP NS-A, production

Production change: `cloud_weather_splat.fsh` nimbostratus members keep flat tops (`edgeDrop = 0`, the base
lift `-0.25 span` unchanged); `cloud_atmosphere_volume.fsh` profile-5 carve envelope
`min(cov / 0.5, 1 - smoothstep(0.82 - 0.45 r, 1 - 0.45 r, h01))`, `r = 1 - saturate(cov / 0.5)` (was
`min(cov / 0.5, 1 - smoothstep(0.82, 1, h01))`). The per-member rim tapers no longer average into a tall central
capsule with thin wings; the deck reads as one broad, even layer from side / front / close (three instances plus
the final gallery); above / below unchanged. Remaining: faint dark field panels on the face (section 7), a
stepped narrow end. Sheets: `ns_ref_vs_nsa` (reference vs NS-A, three instances), `ns_zoom` (P / Q
diagnostics), `nsb_zoom`, `nsc_zoom`.

### Cirrus - KEEP, production (layout)

Defect (reference `below` / `underside` / `above`): a handful of short sticks laid on ONE world-X line
(`filamentCell` ignored orientation), the wind-aligned 5:1 streaks crossing that line - a comb / cross.
In calm air (the gallery freezes motion, `windVector` 0) every member also fell back to its own seed angle.

* CI-A (`CloudMorphologyGenerators.filamentCell`): members fill a 2:1 oval on the sheet sunflower spiral,
  ~1.3 member radii apart, rotated by the plan orientation; 14..20 members (was 8..12).
* CI-B (`VolumetricRenderCell.orientationFor`): with no wind, FILAMENT members share their group's angle
  (hash of the morphology group id) with +-0.15 rad per member - parallel fibres instead of a scatter.

Result (three instances, sessions `t246ci` = CI-A only, `t246cm` = CI-A + CI-B): a broad field of
near-parallel streaks across the sky from below / underside / above / upwind; CI-A alone left the streaks
pointing everywhere (rejected on its own). Still short separate streaks rather than long merged fibres.
Sheets: `ci_a`, `ci_b`.

### Cumulus mediocris - KEEP, production (base layout)

Defect: the three base shoulders sat at exactly 120 deg (+-0.06 rad) around the anchor - with the old star
creases, a symmetric four-leaf "X" from above. MED-A: mediocris shoulder angles jitter +-0.20 rad. Wider
angles / distances were tried first and rejected by the topology sandbox (mediocris width/height 1.5005 > 1.5,
humilis anisotropy p95 2.14 / 1.97 > 1.9, mediocris primary separation 0.925 > 0.92, shoulder birth below the
pinned 0.47..0.54), so the distances and humilis are unchanged. Result (three instances, `t246cm`): irregular
hourglass / uneven trefoil plans from above instead of the symmetric X; below / side / close unchanged in
character. Moderate improvement. Sheet: `med_a`.

### Stratocumulus, cumulonimbus, supercell, stratus - NO CHANGE

No candidate was run for these families. The storms (T240/T241 architecture) were LOW in the audit and are
untouched by every change (the probe change only affects nodes whose cell holds a puff candidate, and storms
carry no puff lobes); stratus was LOW (a thin continuous deck; its level views are a line by nature). Stratocumulus (MEDIUM: dark slab edges under clumps) shares
the nimbostratus panel mechanism - non-storm light from the coarse field node density - so its fix is the
section 7 decision, not a shape change.

## 5. Validation

Static: `campaignGate` (0), `check` (0), `stormMorphologySandbox` (0) on the productionized source
(`gate-t246.log`, `check-build-t246.log`, `sandbox-t246.log`). Offline NVIDIA compile unavailable this session
(venv lost with the scratchpad); every program compiled at runtime in the validation sessions (monolith and lean
FINAL).

Performance, build against build (CU-F adds one candidate fetch per field node in the build pass and lets the
march evaluate puff material it skipped). Two real builds alternating in one sitting, `t208` marker (lean FINAL,
0.25 phase scale, ULTRA, 96 steps, 1920x1080); A = HEAD volume shader, B = T246 (only that file differs).
Log: `t246-campaign/t246-timing.log`.

| | A1 | A2 | A3 | A4 | A mean | B1 | B2 | B3 | B mean |
|---|---|---|---|---|---|---|---|---|---|
| SIDE total (ms) | 5.618 | 5.383 | 5.436 | 5.415 | 5.463 | 5.336 | 5.362 | 5.357 | 5.352 |
| SIDE raw p50 (ms) | 3.794 | 3.662 | 3.707 | 3.678 | 3.710 | 3.626 | 3.643 | 3.627 | 3.632 |
| SIDE raw p95 (ms) | 4.066 | 3.889 | 3.921 | 3.871 | 3.937 | 3.863 | 3.848 | 3.848 | 3.853 |
| SIDE primary steps (M) | 5.22 | 4.64 | 4.63 | 4.62 | | 4.63 | 4.64 | 4.64 | |
| PVN total (ms) | lost | 4.182 | 4.231 | lost | | 4.643 | 4.463 | 4.206 | |
| PVN primary steps (M) | | 1.31 | 1.61 | | | 2.05 | 2.02 | 1.28 | |

SIDE: no cost (B within / below A's spread; A1 ran a different workload). PLAY_VIS_NEAR: the step count varies
run to run independently of the build (A 1.31-1.61 M, B 1.28-2.05 M - the fixture's state, PVN is lost entirely in
two runs); at matched workload B3 (1.28 M steps, 4.21 ms) equals A2 (1.31 M, 4.18 ms). No measurable shader cost;
every frame far below the 10 ms ceiling.

Final static run (plumbing removed, NS-A in): `campaignGate` 0, `check` 0, `stormMorphologySandbox` 0
(`gate-t246b.log`, `check-build-t246b.log`, `sandbox-t246b.log`). NS-A changes only profile-5 code and is not
in the storm benchmark's workload; no further timing was needed.

## 6. Final gallery

`validation/t246-final-gallery/<family>/<view>.png` (+ `views.json`): the production build after T246 (lean
FINAL, all 9 families, 126 views, session `t246fin`), same harness and view definitions as the reference
`cloud-shape-gallery` (left in place). Side-by-side sheets: `t246-campaign/sheets/fin_a` (cumulus),
`fin_b` (nimbostratus, storms), `fin_c` (stratocumulus, stratus, cirrus).

* cumulus humilis / mediocris / congestus: the cut blocks, sliced corners and jagged facets are gone in every view.
  One small dark rectangle remains on humilis `close`: the panel artefact of section 7 (puff nodes outside the
  raster are now uncovered, so their light also comes from the node density).
* nimbostratus: one broad even deck instead of the capsule.
* cumulonimbus: unchanged. supercell, stratocumulus: this session spawned different realizations and the poses
  follow them (no code path changed for either); stratus: unchanged.
* cirrus and cumulus_mediocris folders were re-captured after CI-A/B and MED-A (session `t246cm`, first
  instance, same harness).

Every session spawns a new realization per family (cross-session realizations differ), so the gallery-to-gallery
comparison is structural; the controlled same-instance A/Bs are in sections 3-4.

## 7. Recommendation (not applied - lighting, needs a decision)

The largest remaining artefact on every NON-storm family (nimbostratus panels, stratocumulus dark slab edges, the
humilis rectangle) is lighting, not shape: the T199 shared field gives the light cone a 16-block, bilinear node
density for any material it does not model, and its tent contours print rectangles / diamonds into the shading.
Diagnostic P (exact density in the light cone wherever the node is uncovered) removes the panels on the
nimbostratus deck in all three instances. It was NOT kept: it changes the lighting of every layered family and
the light-cone cost wherever non-storm cloud is in view (the storm benchmark would not see it), the goal excludes
lighting / performance changes, and on puffs the same change (CU-G) exposes lobe discs from below. A follow-up task
should measure P's cost in a layered-cloud scene and decide; a cheaper middle ground is a finer non-storm light
field.
