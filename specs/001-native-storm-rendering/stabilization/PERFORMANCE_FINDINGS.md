# Cloud renderer cost: where it goes and how to cut it

27 Oct 2026. The user's question: one storm structure change cost +5 ms. Real weather will have many clouds (e.g. 300
cumulus), so why is it so expensive, and how do we optimise without losing realism?

All numbers are lean FINAL GPU ms at 1600×900 window, 0.25 internal resolution, ULTRA, measured in game. Sessions:
`runClient-p1/p2/p3/p4/l2.out.log`; harness `ProductionCloudCapture`. Detailed entries: STABILIZATION_LOG E19–E21.

## 1. A frame is three costs, not one

The gallery numbers before E19 timed only the march.

| Pass | What it does | Measured |
|---|---|---|
| Cloud march (lean FINAL) | per pixel: step along the ray, evaluate density near cloud, light-march each lit sample | 1.4 ms empty sky … 6–7 ms storm close-up |
| Storm / rain field build | 256 × 256 × 32 storm atlas + rain gate, **rebuilt every frame** (T199e: reuse never implemented) | 0.5 (cumulus only) … 1.0–1.7 ms (storms) |
| Rain pass + lattice | rain march; runs in clear weather too | 0.04 (cumulus) … 0.4–1.7 ms (storm) |

**With nothing on screen** (storm behind the camera), production costs **~3 ms**:

| Pass | ms |
|---|---|
| march | 1.4 |
| field | 1.0–1.4 |
| rain | 0.4 |

## 2. What the march time is made of

There are two measurements:
- the regression over 24 shots of the T123 counters (R² 0.95);
- the p2 `away` floor.

| Unit | Cost | Notes |
|---|---|---|
| Empty-space step | ~35 per pixel everywhere | the coarse stride is a fixed fraction of the ray's slab span, whatever the coverage; ~1.4 ms of a 3 ms floor |
| Density evaluation | 1.57 ms per million | the expensive unit |
| Light evaluation | 0.78 ms per million | 6 taps per lit sample |

## 3. Why S11 cost so much, and what was fixed

S11 = the accepted seeded storm structure.

- **Root cause.** Every consumer of the storm shape pays its price: the march's safe-distance probe, every density
  call, every light tap, all 2 M field-atlas nodes per frame, and the rain pass.
- **The worst part was a loose bound.** The billow displacement subtracted its worst case (84 blocks) from the safe
  distance everywhere. That turned an 84-block shell around every storm into 2.5-block fine steps: density calls
  ×5.6.
- **Fix v5 (production).** Where the displaced distance is known, (d − soft) / (1 + slope) is also a valid bound;
  take the larger.
  - Supercell side: total 11.6 → **8.5 ms**. Calvus side: 10.7 → **7.3 ms**. Production was 5.7 / 5.1.
  - The field soundness audit stays at 0 false-empty.
- **Still open for S11:** the below view (+3.5 ms) and the field build (+0.3–0.7 ms).

## 4. Why cost grows with the number of clouds (p3: 1 / 16 / 49 cumulus)

| Grid | Side total | Field-low total | Field-high total |
|---|---|---|---|
| 1 cumulus | 1.78 | 0.78 | 0.70 |
| 16 | 2.75 | 2.02 | 1.82 |
| 49 | 2.76 | 2.40 | 2.13 |

- The field views have the same step count and almost no density work, yet the march grows 0.2 → 1.4 ms: the
  **per-step cost** grows with the cloud count.
- One cause found and fixed (**P1, production**): the per-step puff test looped over every puff lobe in the scene.
  It now rejects with a 32 × 32 occupancy bitmask first.
  - Image-identical on clouds (p4).
  - −0.15 ms (1 cloud) to −0.25 ms (49 clouds).
- The rest of the growth is not attributed yet. The T123 counters run on the diagnostic monolith, which is 5–6×
  slower than lean FINAL and differs from it, so lean-FINAL attribution arms are needed.

**Hard capacity limits** (an architecture issue, not tuning):

| Limit | Value | Consequence |
|---|---|---|
| Detailed puff lobes (`MAX_PUFF_LOBES`) | 32 | ≈ 4 detailed cumulus in the whole scene; all others use the weather-map field path, a different look (Codex CR-07) |
| Weather-map cells (`MAX_CELLS`) | 96 | 66 spawned cumulus already filled it |
| Storm groups / storm lobes | 8 / 64 | — |

A 300-cumulus sky cannot be drawn as authored today.

## 5. Optimisation plan (no visual change unless stated), in order of value

1. **Empty-space skipping from the weather coverage.**
   - Build a per-frame "distance to the nearest cloud" map from the 512² weather map (jump-flood or a max-mip), and
     let empty coarse steps jump to the next possible cloud.
   - Expected to remove most of the ~35 empty steps per pixel: up to ~1 ms of the floor, more with scattered
     clouds.
   - Must keep the storm-AABB, puff and rain tests conservative.
2. **Field build amortisation.**
   - Rebuild the storm atlas in slices (e.g. a quarter per frame) or only when inputs move beyond a threshold,
     dilated by the motion bound.
   - ~0.7–1 ms per frame wherever storms exist.
3. **Lean-FINAL attribution of the per-step growth with cloud count** (section 4), then fix what it finds.
4. **Bake the accepted switches as lean-FINAL constants** (E16 lighting, S11, E20, P1).
   - T232 measured ~0.7 ms for live uniforms in this program. Needs a build-vs-build timing.
5. **Capacity for many clouds** (architecture; needs the user's go-ahead).
   - Move puff lobes from uniform arrays (32) to a descriptor texture like the storms, with the existing 16-block
     candidate tiles.
   - Raise / restructure the 96-cell weather-map budget.
   - Distant clouds then keep one representation (fixes CR-07 pops), and cost stays proportional to the cloud
     covered on screen, not to the count.
6. **S11 remaining:** cheaper shape terms in the field build (no billow octave 3 at 16-block nodes) and the below
   view.

## 6. Status, 27 Oct 2026 (log E22)

| Item | Result | State |
|---|---|---|
| 3. Attribution | the cumulus-count cost was mostly capacity (96 cells ≈ 12 clouds; 32 detailed lobes), plus puff promotion / density | done |
| 2. Field bands | storm field 1.37 → 0.49 ms, cumulus 0.62 → 0.19 ms | production |
| 1. Empty-space jump | empty view −0.9 ms, others neutral | production |
| P1 puff occupancy | −0.15 to −0.25 ms | production |
| C1 capacity | 1024 weather cells via data texture + tile lists; 100 cumulus render (800 cells), march ~2–3 ms | production |
| C2 detailed puffs > 32 lobes | — | next |
| C3 far layer beyond ±2 km | — | next |
