# T214 - Anvil macro displacement compensation

One question: does compensating the measured ~2.69x anvil vertical-clamp
attenuation turn the already-confirmed macro band into actual large-scale
billowing?

**Answer: the question is already answered by what T213 shipped, and the
remaining amplitude lever is exhausted.** The attenuation was *already*
compensated - T213's block-space displacement is clamp-independent by
construction. Raising amplitude beyond it does not produce billowing: it
produces clipping artefacts. **CASE C. Both arms REJECTED, production
unchanged.**

## 1. Starting HEAD

`f8679a3` on `quality/t213-macro-envelope-vertical`. Working tree carried only
the preserved `PAchangelog.md` edit.

The task flagged "an unstaged/new check-build-related file". Inspected: they
are four of my own build transcripts, `check-build-t210.log` through
`check-build-t213.log`, each a `BUILD SUCCESSFUL` Gradle log. **Not staged** -
zero `check-build` files are tracked in this repo, and they are the same class
as the other 190 untracked run/gate logs the project deliberately leaves
loose. Preserved, not added.

## 2. Clamp geometry

From the T212 `T212_EDGE_WIDTH` trace of the real fixture descriptors:

| role | horizontal half-width | vertical half-height | vertical bound (x0.75) | actual edgeWidth | bound by |
|---|---|---|---|---|---|
| 0 BASE | 141.1 - 145.3 | 223.8 | 167.8 | 141.1 - 145.3 | horizontal |
| 1 CORE | 63.0 - 72.0 | 156.4 - 164.2 | 117.3 - 123.1 | 63.0 - 72.0 | horizontal |
| 2 TOWER | 37.5 - 44.5 | 181.7 - 197.3 | 136.3 - 148.0 | 37.5 - 44.5 | horizontal |
| **3 ANVIL** | **240.9 - 242.4** | **119.3** | **89.475** | **89.475** | **VERTICAL** |

## 3. Measured attenuation

`240.9 / 89.475 = 2.69x`, and the anvil is the only role subject to it.

## 4. Compensation equation - and the correction this task turns on

T213's banked mode 3 computes:

```glsl
bandWidth = max(paLastStormUnionSoftness, STORM_MIN_EDGE_BLOCKS);   // = w
shift     = (deckWeight * STORM_MACRO_BLOCKS_PER_SD * macroSigned) / (2.0 * bandWidth);
```

t-space maps to distance as `d = (2t - 1) * w`, so `Delta d = 2w * Delta t`,
and the `2 * bandWidth` divisor **cancels the band's own scaling exactly**:

```
Delta d = 2w * (deckWeight * 38 * macroSigned) / (2w)
        = deckWeight * 38 * macroSigned   blocks
```

Verified numerically - the same formula moves the boundary by **38.000 blocks
at w = 89.475, 141.0 and 240.9 alike**. The displacement is expressed in
absolute blocks and is independent of the envelope half-width.

**So the 2.69x attenuation is already fully compensated in production.** The
coverage-space arms (modes 1-2) were the attenuated ones, because a coverage
shift maps to distance through `2w`; that is precisely why T212's candidate
underperformed and why T213's move to block space fixed it. An independent
check confirms the reading: if mode 3 were attenuated 2.69x its effective
displacement would be 38/2.69 = 14 blocks/sd, *less* than T212's 21.8, yet it
produced far more macro energy.

**What the clamp still does is CAP the displacement**, since `tShifted` is
clamped to [0,1], i.e. `|Delta d| <= w = 89.475 blocks` at the anvil. That has
a concrete consequence for this task: literal "full 2.69x compensation" would
be `2.69 * 38 = 102 blocks/sd`, which is **above the cap and unreachable**.
The only free parameter left is amplitude, tested up to the cap.

## 5. Candidate multipliers

Paired same-storm arms, J0 always production:

| arm | blocks/sd | vs production | samples clipped at the cap |
|---|---|---|---|
| M0 production (mode 3) | 38 | 1.00x | ~1.9% |
| **M1 (mode 4)** | 55 | 1.45x | ~10.4% |
| **M2 (mode 5)** | 89 | 2.34x (the cap itself) | ~31.5% |

## 6. Zero-bias result

| arm | area SIDE | area LATERAL | area ABOVE |
|---|---|---|---|
| M1 (55) | -0.5% | -0.3% | -1.4% |
| M2 (89) | **-1.7%** | -0.1% | **-3.3%** |

M1 is effectively unbiased. **M2 reintroduces shrinkage** - the asymmetric
consequence of clipping against the band, the same failure mode T213 removed
by leaving the coverage channel. Displacement statistics remain zero-mean by
construction (a zero-mean term times a positive weight); the bias is
introduced by the cap, not by the term.

## 7-10. Visual results

Judged at 1:1 on paired same-storm frames
(`t214-campaign/m1-55/`, `t214-campaign/m2-89/`).

**7. SIDE.** M1: no visible change. M2: the crown **flattens**, the outline
squares off, and the ellipse term rises rather than falls.

**8. LATERAL.** M1: **indistinguishable from production** - same outline, same
lobes (IoU 0.9860). M2: the crown is visibly flattened into a plateau and the
silhouette reads truncated, not billowed.

**9. ABOVE.** M1: mid +19%, fine +49%. M2: mid **+49%**, fine **+39%**. The
extra amplitude lands almost entirely in high-frequency edge energy - the
signature of lobes clipping against the cap and sharpening their transitions
instead of deepening.

**10. NEAR_EDGE.** No new wisps; no improvement either.

Neither arm produces broad protrusions, broad recessions or coherent billows.
M2 produces the opposite of the visual target: plateaus and truncation.

## 11-13. Frequency attribution

Against production (38 blocks/sd), same storm:

| pose | arm | ellipse | macro (3-8) | mid (9-32) | fine (33+) | ellipse/macro |
|---|---|---|---|---|---|---|
| SIDE | M1 (55) | 19.40 -> 19.69 (+1.5%) | 8.44 -> 8.91 (+5.6%) | -0.6% | -1.0% | 2.30 -> 2.21 |
| SIDE | M2 (89) | 23.42 -> **25.29 (+8.0%)** | 7.73 -> 9.24 (+19.5%) | +3.8% | +4.8% | 3.03 -> 2.74 |
| LATERAL | M1 (55) | 28.81 -> 28.76 (-0.2%) | 8.54 -> **8.64 (+1.2%)** | -1.0% | -7.3% | 3.37 -> 3.33 |
| LATERAL | M2 (89) | 24.76 -> 24.86 (+0.4%) | 8.09 -> 8.52 (+5.3%) | +0.2% | +1.5% | 3.06 -> 2.92 |
| ABOVE | M2 (89) | 78.87 -> 80.19 | 47.79 -> 51.55 | 15.35 -> **22.90 (+49%)** | 6.68 -> **9.30 (+39%)** | 1.65 -> 1.56 |

**13. The ratio barely closes.** T213 established ellipse/macro at 2.7-3.9x.
M1 moves it 2.30 -> 2.21 and 3.37 -> 3.33; M2 moves it 3.03 -> 2.74 and
3.06 -> 2.92 - and buys that partly by *raising* the ellipse term at SIDE.

**The decisive comparison is the gradient.** T213's step from 0 to 38
blocks/sd produced macro **+23.8%** at LATERAL. Going 38 -> 55 produces
**+1.2%**; 38 -> 89 produces **+5.3%**. Amplitude has sharply diminishing
returns and is exhausted as a lever.

## 14. Projected area

See item 6. M1 neutral; M2 shrinks 1.7-3.3%.

## 15. Tower connectivity - an independent reason to reject M2

| arm | pose | lower-body occupancy | neck mean |
|---|---|---|---|
| M1 (55) | SIDE | 46174 -> 45892 (-0.6%) | 31 -> 29 |
| M1 (55) | LATERAL | 33397 -> 33377 (-0.1%) | 107 -> 107 |
| **M2 (89)** | SIDE | 53073 -> 51809 (-2.4%) | **24 -> 18 (-25%)** | 
| M2 (89) | LATERAL | 37277 -> 37070 (-0.6%) | 100 -> 97 |

M2 narrows the tower/anvil neck by a quarter at SIDE. Task 7 rules out buying
a silhouette change by thinning the storm; M2 does exactly that.

## 16. Holes / components

M1: holes 1902 -> 1886 SIDE, 590 -> 611 LATERAL; components 124 -> 111,
117 -> 122. M2: holes 2163 -> 1770 SIDE, 824 -> 832 LATERAL; components
172 -> 155, 112 -> 118. No detached-island explosion in either arm.

## 17. Static stability

| arm | pose | production mean / max | candidate mean / max | px > 2/255 |
|---|---|---|---|---|
| M1 | SIDE | 0.00925 / 1.67 | 0.00696 / 1.33 | 0.00% |
| M1 | LATERAL | 0.02311 / 2.00 | 0.02745 / 1.00 | 0.00% |
| M2 | SIDE | 0.00956 / 1.67 | 0.00663 / 1.00 | 0.00% |
| M2 | LATERAL | 0.02339 / 1.33 | 0.02452 / 2.00 | 0.00% |

Both converge; no pixel moves more than 2/255. As T211 established, this is a
prefilter only.

## 18-19. Motion - and the most useful result of this task

T214M, T152 route, 2200 frames per arm, one storm, comparing production
(38 blocks/sd) against the cap arm (89 blocks/sd):

| segment | 38 b/sd | 89 b/sd | change |
|---|---|---|---|
| OUTSIDE | 0.00027 | 0.00028 | +3.4% |
| APPROACH | 0.00081 | 0.00083 | +2.2% |
| ENTRY | 0.00150 | 0.00152 | +1.2% |
| INTERIOR | 0.00539 | 0.00553 | +2.5% |
| **ROUTE** | **0.00246** | **0.00251** | **+2.2%** |

(Production 0.50 reference 0.00213.)

**19. Edge crawl: essentially none.** One frame where production is still and
the candidate crawls, zero the other way; perfectly-still frames 634 vs 618.

**This isolates what actually drives the temporal cost.** T212's
coverage-space candidate cost **+10.2%** route flicker. T213's block-space at
38 blocks/sd cost **+2.5%**. Raising displacement 2.34x on top of that costs
only **+2.2% more**. Temporal instability therefore tracks the **modulation
channel** - coverage clipping - and **not** displacement magnitude. A future
change that increases achievable displacement is not, on this evidence,
pre-emptively disqualified on stability grounds.

Ghost and disocclusion remain appearance-confounded and were not used.

## 20-24. Performance

Production is unchanged by this task, so no candidate cost ships. The arms
differ only by which constant a ternary selects inside a branch production
already takes, so the added work is two integer comparisons per boundary
sample.

| | SIDE | PLAY_VIS_NEAR |
|---|---|---|
| **20/21. production (this session)** | **7.8431** | **7.3888** |
| T213 post-bank reference | 7.8567 | 7.1112 |
| **24. <= 10 ms** | **YES** (2.16 headroom) | **YES** (2.61 headroom) |

`rawCloud` moves 5.06-5.38 between sessions, so the difference from the T213
reference is storm variance, not a regression. Items 22/23 are not separately
reported because no candidate is proposed for production.

## 25. KEEP / REJECT: both arms REJECTED

- **M1 (55 blocks/sd): REJECT** - visually indistinguishable from production
  (IoU 0.986, LATERAL macro +1.2%). No gain to weigh against any cost.
- **M2 (89 blocks/sd, the cap): REJECT** - visually *worse*. The crown
  flattens into a plateau, the ellipse term rises at SIDE, mid/fine energy
  jumps +49%/+39% at ABOVE, area shrinks up to 3.3%, and the tower neck
  narrows 25%.

`PRODUCTION_MACRO_ENVELOPE_MODE` stays **3** at 38 blocks/sd. Modes 4 and 5
are retained as diagnostics, the way T209-T213 retained theirs.

## 26. Hypothesis: DISPROVED as an amplitude factor

Two distinct findings, and it matters that they are separate:

1. **There was no attenuation left to compensate.** T213's block-space
   displacement is clamp-independent - verified algebraically and numerically
   (38.000 blocks at w = 89.5, 141, 240.9). The premise that a 2.69x
   compensation was still owed does not hold against the shipped code.
2. **Amplitude is exhausted as a lever.** Macro energy rises +23.8% for the
   first 38 blocks/sd and only +1.2% / +5.3% for the next 17 and 51. The
   clamp now binds as a **cap**, and pushing against a cap flattens lobes
   rather than deepening them - which is why the extra amplitude shows up as
   mid/fine energy and plateau artefacts instead of billowing.

Per CASE C, amplitude modification stops here.

## 27. Production constant

`PRODUCTION_MACRO_ENVELOPE_MODE = 3` (unchanged). All other production
constants unchanged: `PRODUCTION_TEMPORAL_MODE = 1`,
`PRODUCTION_SPATIAL_UPSCALE = -1`, `PRODUCTION_RAIN_LATTICE_MODE = 0`,
`PRODUCTION_ORIGIN_JITTER_MODE = 0`. No pinned morphology constant touched.

## 28. Exact next visual target

**T215 - the envelope basis, not its modulation.** CASE C directs the
investigation at the basis itself, and the measurements point at two specific
options, in order:

1. **Make the anvil footprint non-elliptical at descriptor level.** Every
   attempt from T212 to T214 has modulated a smooth ellipse and been limited
   by how far the boundary may move. The ellipse harmonic is 2.2-3.3x the
   macro band *because the underlying geometry is an ellipse*. Generating a
   lobed anvil footprint on the CPU (in `StormLobeDescriptor` / the role
   profile) changes the basis rather than displacing it, and is bounded by
   descriptor geometry rather than by the envelope band.
2. **Widen the anvil band anisotropically (horizontal only).** The cap is
   `|Delta d| <= w = 89.475`; relaxing the T098 vertical clamp horizontally
   would raise it. T214 makes this materially more attractive than it looked:
   the flicker gradient shows displacement magnitude is nearly free
   (+2.2% for 2.34x), so the stability objection is weak. It must still be
   judged against the failure the clamp was added to prevent - ~150 blocks of
   low-coverage haze hanging below the anvil base.

Option 1 is preferred: it attacks the ellipse dominance directly rather than
buying more room to push against it.

## 29-33. Validation

**29. `campaignGate`: PASS** (`gate-t214.log`), run before any client
launched: `T170_WIRING campaigns=60 | armMatrices=50 | violations=0`.

**30. `check build`: PASS** (`check-build-t214.log`).

**31. Morphology sandbox: PASS** - `T100 measured noise thresholds | PASSED |
all recorded statistics and derivations reproduced`. No pinned constant was
edited.

**32. Git status.** T214 sources, this document, `t214-campaign/` evidence,
the launchers, and loose run logs. `PAchangelog.md` preserved and
uncommitted; the four `check-build-*.log` transcripts deliberately left
untracked. Every edited file is pure CRLF.

**33. Commit.** On `quality/t214-anvil-macro-compensation`, not merged.

### Evidence

| path | what it is |
|---|---|
| `t214-campaign/m1-55/` | production vs 55 blocks/sd (visually indistinguishable) |
| `t214-campaign/m2-89/` | production vs 89 blocks/sd at the cap (flattened crown) |
| `t214-campaign/t214-amplitude-motion.log` | the flicker-vs-amplitude gradient: +2.2% for 2.34x |
