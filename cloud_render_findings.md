# Cloud Rendering Findings

## Fixed

- The random disappear/reappear behavior was caused by the Java projected-bounds and scissor path.
- Removing that path fixed the depth/culling issue.

## Still Wrong

- Each cloud snapshot is still rendered as one fullscreen raymarched volume, so the shapes still read as blobs instead of layered cloud masses.
- `CloudShapeProfile` data is not fully driving the shader yet.
- `CloudVerticalThickness` is currently not shaping the density enough, so stratus stays too thin.
- Cumulonimbus is still too dark because absorption and storm darkening stack too aggressively.
- The erosion/carve logic is creating surface spots and holes instead of soft volumetric breakup.

## Performance

- FPS is still heavy because each visible cloud is still a fullscreen raymarch pass.
- The renderer does not yet use bounded/proxy rendering to reduce the amount of shaded pixels.

## Next Fixes

1. Use `CloudVerticalThickness` in `sampleCloudField()`.
2. Upload and use more of `CloudShapeProfile`, especially vertical offsets and body shaping.
3. Reduce storm absorption so storm clouds keep readable volume.
4. Replace hard carve behavior with softer volumetric erosion.
5. Add bounded rendering or proxy volumes to cut fullscreen raymarch cost.
