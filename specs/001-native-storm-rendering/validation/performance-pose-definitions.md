# T142 — the performance pose contract

**Feature**: `001-native-storm-rendering`
**Task**: T142 [PERFORMANCE]
**Date**: 2026-09-02
**Supersedes**: the pose set used by `performance-budget.md` (T135),
`performance-baseline.md` (T136) and `performance-architecture.md` (T137)
**Cause**: `performance-internal-resolution.md` (T138)

---

## 1. Why the pose set had to change

The severe poses are defined as multiples of the fixture's own horizontal
radius, so that any storm is framed the same way. That is correct for the
structural poses and wrong for the gameplay ones, because the shipped
`cloudRenderDistance` is an absolute **2000 blocks** and does not scale with the
storm.

At T134 severe scale the fixture's horizontal radius is roughly **670 blocks**.
The three gameplay poses therefore place the camera:

| pose | multiple | camera distance | storm near edge | inside 2000-block cloud render distance? |
|---|---|---|---|---|
| `PLAY_NEAR` | 4.0r | ~2680 | ~2010 | **no**, by about ten blocks |
| `PLAY_HIGH` | 5.0r | ~3350 | ~2680 | **no** |
| `PLAY_MID` | 7.0r (+3r lateral) | ~5100 | ~4430 | **no** |

All three render **empty sky**. This is confirmed three independent ways:

1. the captured frame at `PLAY_NEAR` contains no cloud at all
   (`t138-resolution-ladder/PLAY_NEAR_empty_sky.png`);
2. its workload counters report **0.09 light-march density evaluations per
   pixel** and **99.9 % of march steps resolved as empty space**;
3. `PLAY_MID` and `PLAY_HIGH` cannot even hold the storm's descriptors at that
   range — the T138 sweep recorded every one of their arms as unmeasurable
   after three attempts rather than sampling a stormless scene.

**No historical measurement is deleted or rewritten.** The cells remain in
`performance-baseline.md` and stay valid as measurements of what they actually
rendered. What changes is the claim attached to them: they are **not**
representative severe-rendering evidence, and the "2–13x over budget"
conclusion drawn from them is withdrawn.

---

## 2. The corrected pose contract

Four categories. A pose belongs to exactly one, and a performance claim must
name the category it is drawn from.

### 2.1 VISIBLE GAMEPLAY — the representative budget case

A player at ordinary altitude with the severe storm inside cloud render
distance and visibly contributing to the frame. **These are the poses SC-006's
representative claim is made against.**

| pose | camera | altitude | distance at r≈670 | measured Ultra 0.75 |
|---|---|---|---|---|
| `PLAY_VIS_NEAR` | centre + 1.6r | y = 120 | ~1070 blocks | **492.8 ms** |
| `PLAY_VIS_MID` | centre + 2.4r | y = 120 | ~1610 blocks | **284.4 ms** |

Both pitch toward the storm's mid-height. Both are implemented in
`StormT132AutoDriver` and are selectable from the sweep marker.

### 2.2 SEVERE STRUCTURAL — the T098/T098a acceptance framing

Camera at the storm's own mid-height, framing the whole vertical structure.
These are the poses the structural gates are judged at, and they remain valid
for that purpose. They are **not** gameplay poses: y ≈ 570 is not an altitude a
player occupies.

| pose | camera | altitude | measured Ultra 0.75 |
|---|---|---|---|
| `SIDE` | centre + 1.7r | storm mid-height | 573.2 ms |
| `FAR` | centre + 2.6r | storm mid-height | 278.8 ms |
| `ABOVE` | centre + 0.6r, above the top | top + max(120, 0.45h) | 690.7 ms |
| `BELOW` | centre | max(base − max(90, 0.35h), 70) | 840.0 ms |

### 2.3 STRESS — deliberately pathological, reported separately

| pose | camera | measured Ultra 0.75 |
|---|---|---|
| `NEAR_EDGE` | centre + 1.12r, at 0.55 height | 967.1 ms |

A camera parked just outside the storm boundary so high-occupancy rays fill the
frame. **Never used to shape the quality architecture**, and always reported in
its own row.

### 2.4 CONTROL — what the renderer costs with no storm to draw

| pose | camera | descriptors resident? | measured Ultra 0.75 |
|---|---|---|---|
| `PLAY_NEAR` | centre + 4.0r, y = 120 | **yes** (10) | **90.3 ms** |
| `CLEAR` | centre + 14r, y = 120 | no | 3.9 ms |

These two are the most diagnostically valuable poses in the set, and they are
the reason the old `PLAY_NEAR` framing is retained rather than deleted — under
its correct label. Both render an empty sky. The only difference between them
is whether a distant storm's descriptors are resident, and that difference
costs **23x**. Renamed in every claim to
**`PLAY_NEAR` = empty-sky-with-descriptors**.

### 2.5 SUPERSEDED — withdrawn as benchmarks

| pose | reason |
|---|---|
| `PLAY_MID` (7.0r, +3r lateral, y=100) | outside cloud render distance; cannot hold descriptors; superseded by `PLAY_VIS_MID` |
| `PLAY_HIGH` (5.0r, y=320) | outside cloud render distance; cannot hold descriptors; no replacement defined — a high-altitude gameplay pose should be redefined against an absolute distance if one is wanted |

---

## 3. What this changes in the record

| claim | as recorded | corrected |
|---|---|---|
| representative gameplay over budget (Ultra) | 12.8x (`PLAY_NEAR`) | **61.6x** (`PLAY_VIS_NEAR`) |
| representative gameplay over budget (Ultra, mid) | 4.5x (`PLAY_MID`, est.) | **35.5x** (`PLAY_VIS_MID`) |
| "representative gameplay is 2–13x over budget" | T136 §8 finding 1 | **withdrawn**; the cells measured empty sky |
| "SC-006 is credible for representative gameplay" | T137 §4 | **withdrawn**; see `performance-internal-resolution.md` §11 |
| severe structural and stress figures | T136 §3 | **unchanged and still valid** |
| clear-weather control | T136 §3 row D | **unchanged**, and now paired with the empty-sky-with-descriptors control |

## 4. Rule carried forward

**A pose defined as a multiple of the storm radius is only a valid rendering
benchmark while that multiple keeps the storm inside `cloudRenderDistance`.**
Any future pose expressed in radii must state the absolute distance it produces
at the fixture's actual scale, and any reprofile must re-derive it rather than
reuse the multiple. The harness now logs the camera position and the resolved
fixture radius with every cell, so this is checkable from the run log alone.
