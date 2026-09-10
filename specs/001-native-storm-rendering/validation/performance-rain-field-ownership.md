# T189 - ownership interpolation was not the problem, and an OR over 60 samples is

Branch `experiment/cloud-descriptor-k`, parent `9394ac7` (T188). Nothing merged.
Two campaigns 08Sep2026, Ultra, raw scale 0.2500, cloud target 480x270,
framebuffer 1920x1080, 96 steps, 60 samples per cell, three anchor-bracketed
blocks per arm per pose, 25 arms x 2 poses. `T189_REJECTED count=0` in both.

## 1. Headline, and a correction

**T188's stated root cause was wrong, and this report corrects it.** T188
concluded that bilinear interpolation of a boolean ownership channel dilated the
ownership union and produced its 10.6-15.6% false rain, and stated that
mechanism as identified. It is not what is happening.

Three arms - bilinear thresholded at 0.5, `texelFetch`, and bilinear thresholded
at 0.99 - render **byte-identical rain masks**, same digest, at both poses:

| Pose | arm | digest | rain IoU | false rain | missed rain |
|---|---|---|---|---|---|
| SIDE | `t188_rain_mask_field` (bilinear) | `1321e341dd510683` | 0.885990 | 12.36% | 0.45% |
| SIDE | `t189_rain_mask_nearest` (texelFetch) | `1321e341dd510683` | 0.885990 | 12.36% | 0.45% |
| SIDE | `t189_rain_mask_strict` (>= 0.99) | `1321e341dd510683` | 0.885990 | 12.36% | 0.45% |
| FAR | all three | `9dc3104c50ba6ab7` | 0.912195 | 8.47% | 1.06% |

`captureFresh=true` on every row - the capture's own view name is now checked
against the request, so this is three measurements agreeing, not one read three
times. **Changing how ownership is resolved changes the rendered rain by exactly
zero pixels.**

**The real mechanism is an OR over roughly sixty column samples per ray, and it
is quantitatively verified below.**

## 2. Why the interpolation theory fails - the premise, measured

The theory requires two things. Both were measured rather than assumed, because
T188 assumed one of them and was wrong.

**The texture is filtered.** `fieldTextureIsFiltered=true`, with a
filtered-minus-`texelFetch` delta summing to 42-92 on the ownership channel and
249-306 on support across 129,600 columns. `GL_RGBA32F` linear filtering is
honoured here, so that half of the premise holds.

**But fractional ownership is rare.**

| Quantity | SIDE | FAR |
|---|---|---|
| columns sampled | 129,600 | 129,600 |
| **fractional ownership samples** | **248 (0.19%)** | **412 (0.32%)** |

Only two to three columns in a thousand ever see an interpolated ownership value
at all. A defect present on 0.2% of columns cannot produce a 12% error, and
resolving those columns three different ways produces the same image to the bit.

## 3. The field is nearly exact, which is the surprising part

View 63 runs both paths at the same column in one invocation, over columns spread
across the whole domain at arbitrary sub-texel offsets:

| Quantity | SIDE | FAR |
|---|---|---|
| **ownership disagreements** | **32 of 129,600 (0.02%)** | 50 (0.04%) |
| columns both paths own | 14,277 | 19,714 |
| disagreement among ownership-relevant columns | **0.224%** | 0.253% |
| mean support error | 0.0014 | 0.0017 |
| **mean attach-height error** | **0.0275 blocks** | 0.0529 blocks |

**The field reproduces `directStormRainSupportAt` to within a quarter of one
percent on ownership and three hundredths of a block on attach height.** By any
per-column measure it is an excellent approximation. And it still produces 12%
false rain.

## 4. The mechanism, and it checks out numerically

Rain presence along a ray is an **existence test**: `rainSegmentMayContribute`
ORs over the segment's Gauss nodes, and a pixel shows rain if any segment
contributes. T186 measured roughly 30 segment tests per pixel with two sample
positions each, and T188 measured 15.39 surviving rain-support calls per pixel
after the T145 prune - so a ray asks the ownership question of order 60 times.

An OR over `n` independent samples turns a per-column error rate `p` into a
per-ray error rate of `1 - (1 - p)^n`. With the measured p = 0.224% and n = 60:

```
1 - (1 - 0.00224)^60 = 12.6%
```

**The observed SIDE false rain is 12.36%.** The mechanism is not a hypothesis
fitted after the fact; it predicts the measured number from an independently
measured per-column error rate.

It also explains the asymmetry T188 saw and misattributed. Under an OR, a column
the field wrongly calls *owned* propagates to the whole ray, while a column it
wrongly calls *unowned* is almost always masked by the other 59 samples. False
rain is amplified; missed rain is suppressed. That is 12.36% against 0.45%.

## 5. What this means for resolution

To bring ray-level false rain to about 1%, per-column disagreement must fall to
roughly 0.017% - a **13-fold** improvement. Boundary error scales with texel
size, so that is of order 13x finer linear resolution, which is 169x the cells:
about 44 million against today's 262,144.

The field currently pays 262,144 cell builds to remove 2,098,025 ray fetches, a
ratio of 8.0 to 1 in its favour. At 44 million cells the ratio inverts to about
1 to 21 against. **Resolution cannot fix this**, and Task 9's sweep would be
measuring the wrong axis - which is why it stays withheld, now for a firmer
reason than T188 had.

## 6. Performance - the fix is free, and the field still pays

Two runs, net ratio including the field build:

| Pose | arm | run | net ratio | spread | verdict |
|---|---|---|---|---|---|
| SIDE | `t189_field_nearest` | 1 | 1.1368 | 0.77% | accepted |
| SIDE | `t189_field_nearest` | 2 | **1.1228** | **0.15%** | **accepted** |
| SIDE | `t188_field_real` (bilinear) | 2 | 1.1296 | 1.16% | accepted |
| FAR | `t189_field_nearest` | 2 | 1.1341 | 0.40% | accepted |
| FAR | `t188_field_real` | 2 | 1.1203 | 2.96% | accepted |

**`T189_OWNERSHIP_COST`, nearest against bilinear in the same blocks:**

| Pose | run | ratio | spread | verdict |
|---|---|---|---|---|
| SIDE | 1 | 0.9981 | 0.98% | accepted |
| SIDE | 2 | 1.0030 | 0.43% | accepted |

**The discrete fetch costs nothing measurable** - 0.19% slower in one run and
0.30% faster in the other, which is zero within noise. Task 7's band is cleared:
net SIDE stays at 1.12-1.14, above 1.10, so the field remains a strong
performance candidate with discrete ownership.

Field build p50 0.2350-0.2468 ms, unchanged from T188.

## 7. Stack

| Pose | arm | run | net ratio | spread | verdict | session-local cloud+field p50 |
|---|---|---|---|---|---|---|
| SIDE | `t189_stack_nearest` | 1 | 2.1525 | 0.04% | accepted | 16.68 |
| SIDE | `t189_stack_nearest` | 2 | 2.1375 | 5.40% | REJECTED | - |
| FAR | `t189_stack_nearest` | 2 | 3.3394 | 2.87% | accepted | - |

Within-block against `t172_stack_pre`: SIDE 1.2838 at 13.06% and 1.3265 at 9.66%,
both **rejected**; FAR 1.1658 at 1.36% **accepted**. The SIDE control spread
4.62-12.13% again, so the SIDE stack composition remains unestablished - the same
instability as every campaign since T181.

**Not banked**, per the brief.

| Target | Result |
|---|---|
| SIDE <= 10 ms locally | no (16.68 ms; session anchors ran 31-34 ms) |
| SIDE <= 8 ms locally | no |
| FAR <= 8 ms locally | yes |

Session-local absolutes are not comparable across sessions and are not the result.

## 8. Decision

**Ownership interpolation was not the quality failure, and fixing it fixes
nothing visible.** That is the honest answer to Task 3's key question: false rain
does not collapse from ~12% to near zero. It does not move at all.

**Keep the discrete fetch anyway.** Ownership is a boolean and asking a filter
for it is a type error regardless of how little it currently costs; the fetch is
free (0.3% either way), it removes a class of future error, and it makes the
0.2% of fractional columns behave the way the value's semantics say they should.
It is correct, not merely harmless.

**Do not approve the rain field for production.** 8.5-12.4% false rain stands,
and the brief's bar explicitly excludes anything resembling it.

**The remaining limitation is structural, not a resolution or filtering setting.**
A 0.22% per-column error is amplified 60-fold by an existence test. Any fix has
to attack the amplification or the per-column rate by an order of magnitude, and
only two of Task 6's options can:

1. **Conservative ownership (Task 6 option C).** Store per cell whether the cell
   is *entirely* owned, *entirely* unowned, or *mixed*, and fall back to the
   exact traversal only on mixed cells. Mixed cells are the 0.2-0.3% measured
   above, so the fallback is rare and the answer becomes exact - false rain goes
   to zero by construction rather than by tuning. This is the recommended next
   step: it preserves the 8:1 build ratio, needs no resolution change, and the
   fallback path already exists and is already exercised.
2. **Signed ownership margin (option B).** Store distance to the ownership
   boundary instead of a boolean, so interpolation becomes meaningful. Cheaper to
   sample than a fallback but approximate, and it would need its own quality
   gate.

**Do not pursue option A (higher resolution) or option D (a separate discrete
texture).** Section 5 shows A inverts the cost ratio, and D changes storage
without changing the per-column error that is actually the problem.

**Post-field attribution is unchanged and not re-confirmed here**, because the
field is not quality-approved: light tap 38.41% and empty-span probe 37.96%
remain T188's measurement, and per the brief no other renderer optimization
should be selected until the field passes.

## 9. What was not measured

**The direction of the 32 ownership disagreements is not broken out.** The mask
asymmetry implies most are the field claiming ownership the exact path denies,
and the OR mechanism requires that, but the counter sums both directions into
one figure. Splitting it is a two-line change and belongs with the conservative
ownership work, where it directly sizes the mixed-cell fallback.

## 10. Harness

- `T189_REJECTED count=0` in both runs; `T189_DISCRETE_OWNERSHIP texelFetch=true|
  continuousFiltered=true|halfTexelOffset=false|clamped=true|baselineRetained=true`.
- **Capture freshness is now checked.** The mask comparison logs the capture's
  own view name against the request that produced it. Three mask arms are
  captured back to back, and a stale read would have reported three identical
  results as three measurements - indistinguishable from the genuine result here
  without the check. `captureFresh=true` on all six rows.
- Coordinate mapping is asserted, not assumed: the generation pass writes cell
  (i,j) from `texCoord` `(i+0.5)/N`, so `floor(uv*N)` is its exact inverse, and
  the invariant fails the build if a half-texel term appears on either side.
- `WorkloadResult` reached the JVM's 255-parameter ceiling, so T189's eight
  counters travel as a nested `FieldProbe` record, matching `ThresholdWork`.
- **Shader variants 133 -> 137.** Four added: the corrected field, its stack,
  and two rain-mask arms. The two T188 field arms now carry
  `PA_ARM_RAIN_OWN_BILINEAR` so the behaviour under investigation stayed
  reachable; they can be dropped with the T188 temporaries in the separate
  cleanup task.

## 11. Status

Not merged. T183 `6defa18`, T184 `88cf4f4`, T185 `4c2941e`, T186 `443e742`,
T187 `6f315fa`, T188 `9394ac7`.
