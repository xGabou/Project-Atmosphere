# T245 - Most important remaining defect: the storm lower-trunk seam (attributed, candidate rejected)

Branch `quality/t217-deep-interior-lighting`, from HEAD `d738d8a5` (T244 closed). No source change is
kept: the accepted T243 shapes and T244 L3 lighting are untouched.

## 1. Fresh production captures

Full gallery from the committed build, no experiment plumbing, production + neutral lighting
(`t245-campaign/t245_production_gallery.png`).

Two conspicuous problems were checked and set aside:

* **Dark cumulus humilis** (median sun-lit luma 86-95 / 255, darker than the cumulonimbus): a harness
  artifact. Mode 9 (log light-path optical depth) reads od p50 **33 even from ABOVE** on humilis, against
  1.6 on mediocris 2000 blocks further out - the first gallery slot sits in the shadow of the T132
  fixture storm's anvil. Not a renderer defect.
* **Black jagged UNDER views of the storms:** the UNDER pose puts the camera in terrain (known T211 pose
  limit). Not a renderer defect.

## 2. The defect

Every storm (calvus and supercell) shows a **hard horizontal seam across the lower trunk**: above it a
smooth dark-grey trunk, below it a lighter, density-mottled, flat-bottomed block that reads as a stone
pillar hanging under the cloud (`t245_seam.png`). It is opaque cloud in the neutral view.

## 3. Attribution

* **Not rain.** `setRainDisabled(true)` leaves the block unchanged (`t245_rain_off.png`).
* **It is the rain classification of cloud-body material below the weather-map base.** In the primary
  march, `rainFraction = (precipitationSample || p.y < baseY) ? 0.25 + ... : 0` (puff bodies exempt,
  because the same step once made "the hard black underside slab" on cumulus). Descriptor storm bodies
  reach below the weather-map base, so at `baseY` their material switches to rain shading: sun x0.8,
  ambient x0.68, rain tint, and - because `rainFraction > 0.05` - the light march is replaced by the
  cheap `localDensity * 8 * ExtinctionScale` optical depth, which is what makes the block lighter and
  density-mottled.
* **Proof (C1):** exempting cloud-body samples (`!precipitationSample`) from the rain classification
  changes the whole block and nothing else (nimbostratus identical).

## 4. Candidate C1 - rejected (not visibly better)

With cloud shading, the lower trunk takes the full light march under the anvil and becomes a near-black,
blotchy mass with bright puff highlights; the seam stays on the same line as a dark step instead of a
light one (`t245_c1_rejected.png`). Removed.

## 5. What a fix needs (not attempted)

The seam is a shading discontinuity at `baseY` on geometry that continues below it. A visibly better
result needs the lower trunk to read as a rain-darkened continuation of the body - e.g. a transition
band below `baseY` that keeps the light march and blends the rain tint in with depth - rather than
either shading model alone. The rain shading of storm bodies is adjacent to T226 (per-descriptor
precipitation, parked on `quality/t226-local-precipitation`), so it should be scheduled with or after
T226 rather than on top of it.
