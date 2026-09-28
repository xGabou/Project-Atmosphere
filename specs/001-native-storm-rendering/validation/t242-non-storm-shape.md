# T242 - Non-storm cloud shape (cumulus, congestus, layers)

Branch `quality/t217-deep-interior-lighting`, from HEAD `82b44e04` (T241). Shape only. Goal carried from
T241: every cloud type should read as its real-world counterpart, not only the cumulonimbus.

## 1. Two harness traps (they invalidated the first nine galleries)

1. **Tick-settled captures after a teleport.** The T098 driver settles on 20 Hz client ticks. The
   gallery teleports thousands of blocks per type; integrated-server chunk generation then starves
   rendering (far below 300 frames in 90 s), so shots grabbed frames rendered before the arm's
   settings applied. Fix (harness): SETTLE also waits for >= 24 presented frames
   (`VolumetricCloudRenderHook.presentedFrames()`), gallery spacing 5000 -> 2000 blocks.
2. **The weather maps are cached.** `CloudWeatherMapRenderer` re-renders the weather / morphology /
   cumulus-stage maps only when its input signature changes (the render hook's "every frame" comment
   is stale). Gallery clouds are motion-frozen, so a splat-side experiment uniform never reached the
   maps. Fix (harness only): fold the candidate into the signature while experimenting.

A gross sanity arm (every member's footprint halved) proved both: before the fixes it changed
nothing; after them it visibly shrank every cloud. **N16-N22, S20, S22, S23 and the first P1 were
never fairly tested before this and were re-run.**

## 2. Measurements behind the design

* Baked noise is low contrast: base lowFbm p5/p50/p95 = 0.35 / 0.48 / 0.62, baseCarrier
  0.71 / 0.78 / 0.85, detail FBM 0.35 / 0.48 / 0.61 (sd ~0.075-0.08; `jshell` over
  `CloudNoiseFieldModel.bakeBase/bakeDetail`). Raw thresholds barely move a boundary -> z-scores.
* Weather map (RGBA8, 512 texels over 4096 blocks = 8 blocks/texel), dumped around each type:
  after the volume's `smoothstep(0.012, 0.42, r)` coverage is a **plateau** (73-83 % of a cloud's
  texels saturated) with a 1-2 texel rim - noise in the volume cannot move such an outline, and the
  per-member **morphology-category ellipse** gates density at r = 1 regardless.
* Cumulus puffs skip erosion and their billow input was wired at strength 0.

## 3. What ships (one shared outline, carved against raw coverage)

| part | where | change |
|---|---|---|
| member outline | `cloud_weather_footprint.glsl` + weather, morphology, cumulus-layer splats | `r += A * paMemberOutline(normalized, seed)`: two-octave value noise in the member's own normalised frame (2-3 bumps per radius at any size, rigid with the member), z-scored (sd 0.12), clamped +-2; A = 0.22, 0.11 on the stratus/nimbostratus decks; storms and cirrus excluded. Identical in all three splats so coverage, category and cumulus stages share one outline. |
| wide rim | weather splat | non-storm members' coverage rim starts at 0.25 of the radius (was 0.42-0.78) - the raw coverage domes instead of plateauing |
| carve | volume (`cloudDensityImpl`) | non-storm map-path density x `smoothstep(-0.08, 0.12, E - t + a * z)`, E = min(raw coverage / 0.5, per-family vertical profile), z = z-scored mix of a 2.7x base-noise tap and lowFbm; t / a = 0.5 / 0.45, decks 0.30 / 0.22 (continuous) |
| puff billows | volume (direct puffs) | the resolver's billow input fed with the detail FBM (z-stretched to its 0.28..0.68 remap) at strength 3: up to +-0.18 of lobe depth on the exposed shell, junctions / cores / base-root corridor still protected (flat base) |

Galleries (`t242-campaign/`): `t242_g12.png` (sanity + first real outline), `t242_g13.png` (all
types, production vs candidate), `t242_g14.png` (continuous decks), `t242_v1.png` (production).

Seen: congestus becomes a lumpy cauliflower tower instead of a smooth potato; stratocumulus a field of
irregular clumps; stratus / nimbostratus ragged-edged decks (nimbostratus mostly continuous) instead
of ellipse petals. Humilis / mediocris change modestly - they stay small (their rendered size is well
below their cluster bounds; the puff hierarchy is out of this step's scope).

## 3b. Crisp cumulus edges (follow-up)

The puff resolver ramped density linearly from the surface to the core, so every cumulus rendered as
a soft blur ("balloon animal"). It now reaches full density within 0.18 of lobe depth inside the
surface (`t242_c2_zoom.png`: production left, crisp right) - defined domed lobes from below. Tried and
not kept: forcing `PuffShapeMode.DIRECT_ONLY` (to rule out hybrid tile seams) - no visible difference
on the captured instance (`t242_c3_zoom.png`). Maturity checked: gallery cumulus are at their target
radius (growth 1.0); rendered humilis is ~30 blocks, about the real proportion to a 1400-block Cb.

## 3c. Fringe sheets (cirrus streaks, congestus fins)

Empty weather-map texels just beyond a member's footprint (r 1..1.45, zero weight) wrote the fixed
sentinel interval (0.35..0.45 of the slab). Bilinear filtering between a cloud's last covered texel and
that sentinel slid the interval across one 8-block texel - a thin near-vertical sheet of faint density
off every cloud far from the sentinel height: the "vertical streaks" hanging from cirrus (y ~734) and
likely the pointed fins on congestus. The splat now remembers the nearest member within its cull
radius and writes that member's own base/top into the empty fringe texel. `t242_f1_cirrus.png`: before,
only the streaks were visible; after, the streaks are gone and cirrus shows as thin elongated wisps.

## 3d. Congestus as an analytic puff tower

On the weather-map path congestus rendered as 8-block columns - straight vertical walls, a comb of
spires and horizontal banding (`t242_t1_zoomN.png`, neutral lighting, so shape not light). It is now
`PUFF` family (registry, `defaultFor`, validation case) with a tall hierarchical layout: the mediocris
four-lobe base, then two middle lobes, one upper lobe and a crown turret, each tier's root starting
inside its parent below the parent's widest point (shallower roots left floating gaps between tiers).
Exactly eight lobes: a puff candidate tile holds eight, a stacked column puts every lobe over its
central tile, and nine overflowed on some frames and dropped the whole puff index to the map fallback.
Humilis / mediocris layouts are untouched (the congestus branch leaves after the shared base section).
Result (`t242_t6.png`): one connected cumulus with a turret rising from a lumpy base; from below a
dark cauliflower mass. Profile top offset 96 -> 150 to match the tower's bounds.

## 4. Cost

One extra 3-D texture fetch per non-storm map-path density sample (the carve's base-noise tap) and
one detail fetch per direct-puff sample. Storm (descriptor) samples are untouched. Not measured this
task (shape-only mandate); flag for the next performance pass.

## 5. Validation

`campaignGate` 72/72; `cloudMorphologyTopologySandbox` and `stormVolumetricGeometrySandbox` pass;
offline NVIDIA compile of monolith, lean FINAL, rain pass and rain-gate build. Production gallery in
section 3.

## 6. Current state (production, all types)

`t242_final.png` - SIDE / UNDER / ABOVE, neutral and production lighting, one of each type:
cumulonimbus calvus and supercell are the T241 body (trunk + thin wedge anvil); congestus a
connected puff tower; humilis / mediocris crisp small lobed cumulus; stratus a ragged continuous deck
(overcast from below); stratocumulus a field of irregular clumps; nimbostratus a thick ragged deck;
cirrus thin high wisps. Known remaining weaknesses: the puff tile holds 8 candidates, so one cloud may not stack more than eight
lobes over a 16-block tile.

Follow-up: congestus middle / upper lobes 0.44-0.50 -> 0.54-0.60 of the nominal radius, crown
0.34-0.40 -> 0.44-0.50 - a broad cauliflower column instead of one narrow turret (`t242_t8.png`),
index still complete (max 8 per tile).

Follow-up: continuous decks (SHEET: stratus, nimbostratus). Members got random base / top / density /
coverage factors (tops +-17 %) and a vertical jitter, and sat 1.18 radii apart while their solid core
after rim, outline noise and carve is ~0.6 R - from above the deck was separate lobes and islands
(`t242_d1.png`). Deck members now share the deck's base, top, density and coverage (the random draws
are still consumed, so other families keep their sequences), stay level, and pack at 0.75 radii:
one continuous ragged-edged deck (`t242_d3.png`).

Follow-up: crowded cumulus skies. The puff index holds 32 lobes and truncated lobe by lobe; any
truncation marked it incomplete, so with more than about four cumulus in view EVERY cumulus fell back
to the weather-map path. It now takes whole clouds nearest the camera until the budget is full and
leaves the rest out entirely - they render through the map path tile by tile in hybrid mode - and a
whole-cloud exclusion no longer marks the index incomplete. Six clustered cumulus (43 lobes): complete,
29 lobes direct, two clouds on the map path (`t242_crowd.png`).
