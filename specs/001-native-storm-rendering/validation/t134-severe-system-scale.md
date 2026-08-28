# T134 Severe-System Scale Implementation

**Status**: **ACCEPTED 2026-08-21.** Source-plan implementation and deterministic validation
passed on 2026-08-20; the required controlled live SIDE/FAR/BELOW/ABOVE capture was collected on
2026-08-21 and is recorded under "Accepted live evidence" below.

## Implementation

`CloudMorphologyGenerators` now derives a mature native severe system from distinct source-plan
controls rather than a uniform descriptor multiplier:

| Source control | Value | Responsibility |
|---|---:|---|
| Member count | 10 | stable BASE/CORE/TOWER/ANVIL role allocation |
| Plan radius | 450 | BASE and role-local horizontal extents |
| Group radius | 400 | distributed system footprint and wind-aligned anvil placement |
| Base drop | 120 | lower-system vertical start |
| Top rise | 780 | convective column and canopy altitude |

The role envelopes target a 900–1,100 BASE diameter, 420–520 CORE diameter, 280–360 lower
TOWER diameter, 180–250 upper TOWER diameter, and a 150–220 anvil thickness. The anvil's own
role radius and its separate group placement create the 1,200–1,500 system footprint. Existing
50/25/12.5-block base wavelengths, approximately 22.7-to-1.4-block detail wavelengths, density
equations, lighting, T131 material continuity, and renderer ownership were not changed.

## Deterministic evidence

`cloudMorphologyTopologySandbox` replays 128 seeded production source plans and asserts:

- exact ten-member source plan and the five controls above;
- 1,200–1,500 horizontal descriptor envelope;
- the same footprint bound at both endpoints of the existing mature stable-scale and descriptor-jitter range;
- 720–880 total descriptor height;
- all role-width and anvil-thickness subranges from the T127 derivation.

It reported:

```text
T134_SCALE_CONTRACT|members=10|planRadius=450.0|groupRadius=400.0|baseDrop=120.0|topRise=780.0|footprint=1200..1500|height=720..880
```

### Centre-relative live-envelope correction (2026-08-20)

The first controlled live fixture was structurally valid but reported a centre-relative
`footprintDiameter=1198.00905` (with `height=865.14185` and ten descriptors). The earlier
deterministic envelope used a source-origin bounding span, whereas the runtime suite correctly
measures each descriptor support radius from the resolved arithmetic group centre. These are not
identical metrics, so the live result could not accept T134.

`cloudMorphologyTopologySandbox` now also measures the production source plan with that same
resolved-centre metric. A fail-first run with the prior anvil downwind position of `0.50` produced
`1252.901658`, below the derived `1255.0` source guard. The only source-plan correction is a
wind-aligned anvil placement endpoint of `0.50 -> 0.52`; no descriptor radii, density/noise
equations, vertical profile, lighting, or material-continuity input changed. The corrected run
reports:

```text
T134_RESOLVED_CENTRE_ENVELOPE|matureLowerMin=1265.701|matureUpperMax=1416.327
```

The correction passed the retained Phase 4R geometry/parity and production-shader checks, Phase
4S morphology checks, the material-continuity diagnostic, `check`, and `build` on 2026-08-20.

## Accepted live evidence — 2026-08-21

Collected from a freshly spawned severe system on this build, not from the earlier compact-cloud
fixture. The two-pass `stormPerformanceSuite` froze one fixture and visited SIDE, FAR, BELOW, and
ABOVE twice; its final report carries
`scaleEnvelope={baseTop, height, horizontalRadius, footprintDiameter, descriptors}` alongside the
fixed-pose and structural-fingerprint controls.

| Control | Recorded | Required |
|---|---|---|
| Fixture | `66a15248-6262-441d-bc42-60e2d4e6b4e5` | one frozen post-T134 severe group |
| Structural fingerprint | `16536fe1abb39ea0` | identical at capture and completion, all views |
| `descriptors` | `10` | exactly 10 |
| `height` | `865.31018` | 720–880 |
| `footprintDiameter` | `1238.61042` | 1,200–1,500 |
| Topology | `compact` | compact |
| Group-boundary scans | `0` | 0 |
| Metadata reads per group evaluation | `3` | 3 |
| SIDE / FAR / BELOW / ABOVE PASS A/B controls | matched | exact equality |
| `structuralChanged` | `false` throughout | false |

Topology generation numbers changed during acquisition. That does not invalidate the capture: the
validity criterion is the structural fingerprint, and
`StormPerformanceBaseline.StructuralFingerprint` deliberately excludes request generation,
candidate-grid origin, upload generation, material advection, history, and frame state, because any
of those can republish the same descriptor group unchanged. The fingerprint was identical at every
`fingerprintAtCapture` and `fingerprintAtComplete`, so `frozen_fixture_fingerprint_mismatch` never
fired.

The recorded `height=865.31018` matches the source-plan derivation exactly: BASE `baseY` at
`centre - 120` and the highest ANVIL `topY` at `centre + topRise * 0.805 + topRise * 0.15`, giving a
`-120..+745` relative column. The recorded `footprintDiameter=1238.61042` sits between the
deterministic resolved-centre guard (`1265.701`) and the live contract floor (`1,200`), consistent
with the measured source-to-runtime loss documented above.

## Carried forward to T133, not to T134

Two items are in scope for the renderer-wide T133 revalidation and are deliberately not claimed by
this task:

- **SC-018 three-distance clause.** T127's "Required live views" specifies 600, 900, and 1,200
  blocks. The suite derives its poses from the fixture radius (`SIDE = radius * 1.35`,
  `FAR = radius * 3.0`, `BELOW = radius * 0.8` lateral), which satisfies T134's own acceptance text
  but is not that matrix.
- **Aspect-ratio and ANVIL-span guards.** The delivered system aspect ratio is
  `865.31018 / 1238.61042 = 0.699`, at the upper edge of T127's 0.55–0.70 band.
  `CloudMorphologyTopologySandbox.validateStormPhysicalScale()` asserts footprint and height
  independently but asserts neither the aspect ratio nor the ANVIL horizontal span, so its worst
  permitted combination (height `880`, footprint `1200`, ratio `0.733`) lies outside the recorded
  T127 target.
