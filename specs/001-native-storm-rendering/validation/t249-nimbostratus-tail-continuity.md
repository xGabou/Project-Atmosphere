# T249 - Nimbostratus outer-tail continuity

Branch `quality/t217-deep-interior-lighting`, from `a7e5348e` (T248). **Outcome: NO CHANGE KEPT.** The remaining
step was attributed, seven candidates were tested on seeded clouds, and none removes it without materially
changing the accepted underside (stop condition of the brief). Production source is unchanged. Evidence:
`validation/t249-campaign/`.

## 1. Outlier reproduction

Fixed-seed harness (`CG_SEED=5000`, nine instances), member layout logged at spawn (`CG_MEMBER`,
`t249-members-seed5000.txt`, analysed by `run/t226-harness/t249_members.py`). Members are large and overlap
heavily: target radius 100..120 blocks, median centre spacing 66..81 blocks. Instances whose OUTERMOST spiral
member is isolated (nearest neighbour 90..103 blocks, radial fraction 1.0): **1, 2, 3, 8** (outlier set);
instances **4, 5, 7** have no isolated member (ordinary set). Member base / top are identical across every member
(243.0 / 299.2), radii 67..90 at spawn. The reproduction is exact across sessions (same member positions).

Where the step shows (`t249_scan`, `t249_steps`): instance 1 `top_profile`, instance 3 `side`, instance 8 `above`
(a knob at the lone member). The deck TOP is smooth after T248; the steps are on the UNDERSIDE - the deep, ragged
hanging lower layer ends in a near-vertical edge, and the thin part beyond it (the lone member, the deck ends)
reads as a narrow stepped tail.

## 2. Exact cause

Not A (the 40-block support not reaching the member): a wider rim support (T1, +-80 blocks) changes nothing
visible. Not C: no other deck term reads unsmoothed coverage except the outline gate, which is meant to.
**D - the T243 hanging lower third.** Its carve (`hang`, threshold 0.25 -> 0.55 toward the base, amplitude up to
0.75) is compared with the coverage envelope, so the ragged lower layer survives only under the thick part of the
deck and ends along a coverage contour. Under a lone outer member (low averaged coverage, no overlap) only the thin
upper layer remains - the tail. B (sparse-end support weighting) is the same mechanism seen from the coverage side.

## 3. Candidates (monolith arms on the same seeded clouds, 8 instances, 13 views)

| id | change | result | verdict |
|---|---|---|---|
| T1 (+-80) | wider 8-tap support blended in only where the 40-block coverage < 0.5 | no visible change | rejected |
| T3 / T4 | hang zone ignores the rim fall-off; smooth underside floor rising toward the rim (ramp to 0.5 / 0.7) | the wall goes, but the ragged hanging layer becomes a smooth LENS - bulge with thin wings, close to the old capsule | rejected (underside changed) |
| T6 | the smooth floor alone | same smooth lens: the deck's averaged coverage is below 0.5 almost everywhere, so the floor cuts under the whole deck | rejected |
| T8 | hang-zone envelope fades over a wider band (0.05..0.75) | ragged layer kept, edge unchanged | rejected |
| T9a / T9b | a ragged floor displaced by the carve noise over the coverage the deck spans (0.05..0.30 / 0.20) | indistinguishable from production | rejected |
| T10a / T10b | the hang carve reads a DILATED coverage (half / full max of the nine T248 taps, no extra fetches) so the layer continues under a lone member | the layer extends under the tail but turns SOLID and smooth - the ragged wisps vanish, a heavy smooth belly | rejected (underside changed) |

Sheets: `t249a_out` (T1), `t249b_out` (T3/T4), `t249c_out` (T6/T8), `t249d_out` (T9), `t249e_out` (T10).

## 4. Accepted mechanism

None. The step is the visible edge of the accepted T243 ragged rain-base layer; every candidate that removes the
edge removes the raggedness with it, and every candidate that keeps the raggedness keeps the edge. The coupling is
structural: the fragments are ragged BECAUSE the hang carve sees a moderate envelope, so anything that carries the
layer out under the tail (higher envelope there) makes it solid, and anything that keeps the envelope keeps the edge.

## 5-6. Seeded before / after, normal clouds

Production is unchanged, so ordinary clouds (instances 4, 5, 7) and the T248 broad deck are unchanged by
construction. Instance 4 also shows a mid-deck underside step of the same kind (not a lone member) - same cause.

## 7. Performance

No production change; no cost.

## 8. Validation

`campaignGate` 0, `check` 0 on the unchanged source (`gate-t249.log`, `check-build-t249.log`).

## 9. Remaining limitations / recommendation

The underside edge can only go if the hanging layer itself is redesigned: e.g. a fractus layer whose depth follows
a coverage-independent, low-frequency field (so it thins gradually toward the rim with its own noise) instead of
the coverage-keyed carve. That is a change to the accepted T243 nimbostratus underside and needs a decision; it is
not a tail fix. The seeded-spawn harness (`CG_SEED`, `CG_MEMBER`) and `t249_members.py` stay in
`run/t226-harness/` for that work.

## 10. Fractus-layer redesign (user-approved follow-up, not kept)

After the nine candidates above, the redesign recommended in section 9 was approved and tried on the same seeded
clouds: the hang carve sees a CONSTANT moderate envelope (decoupled from deck coverage) and the layer's depth comes
from its own low-frequency field (`lowFbm`, z-scored) times a wide smooth rim fade on the averaged coverage, its
bottom displaced by the carve noise.

| id | hang envelope | floor ramp | result |
|---|---|---|---|
| F1 / F2 | 0.75 / 0.65 | -0.04..+0.06 | underside walls gone in side / back views, the tail joins gradually; the light torn wisps become dense rounded lobes with hard bottoms |
| F3 / F4 | 0.60 / 0.55 | -0.10..+0.20 / -0.12..+0.28 | softer lobes (F4 most wispy), but the dark torn pannus under the base is still lost, and in `top_profile` (instances 1, 8) the deep central section still meets the thinner part with a near-vertical edge |

Sheets: `t249f_out` (F1/F2), `t249g_z` (F3/F4). Not kept: the redesign trades the realistic torn pannus for a
partial fix and the target step remains in some views. Production is unchanged.
