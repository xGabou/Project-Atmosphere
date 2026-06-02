Here is a clean internal report you can keep and come back to later.
I wrote it as an investigation note, not a fix proposal.

---

Cloud Behaviour Investigation Report
Project Atmosphere
Topic Cloud apparent stacking and long term drift coherence

Context
Multiple clouds appear to visually stack or accumulate in the same area of the sky over time. This raised concern about possible convergence bugs in wind logic or cloud events handling.

Data sources used
Live cloud region state dumps
cloud_events.jsonl lifecycle and movement logs
In game visual observation
Wind and cloud runtime parameters

Observed cloud state summary
Three active cloud regions were inspected simultaneously.
Each cloud has a unique id unique biome focus position and independent lifetime.
Each cloud has its own position radius velocity and rotation values.

All clouds show
Negative X velocity
Positive Z velocity
Very similar speed magnitude
Identical accelerationFactor
No active tornadoes or external forces

Typical values
Velocity magnitude around 0.015 to 0.018 blocks per tick
Radius between roughly 217 and 271 blocks
Initial radius much larger then shrinking over time
Very long lifetimes tens of thousands of ticks

Findings from cloud events data
The cloud events file shows no evidence of convergence bugs.

Specifically
No clouds share identical world positions at any recorded time
No teleport events occur
No merge or snap events occur
No cloud ids are reused or overwritten
Position evolution per cloud is smooth and monotonic

This confirms that clouds do not collapse into a single simulation space position.

Why clouds appear stacked visually
The stacking effect is visual not logical.

Primary contributing factors

1. Shared wind vector
   All clouds sample effectively the same regional wind direction and magnitude.
   This produces a laminar flow where clouds move in parallel trajectories.

2. Extremely low relative speed
   Cloud speed is very low relative to cloud size and camera scale.
   At roughly 0.3 blocks per second large clouds take a very long time to separate visually.

3. Large cloud radius
   Cloud radii exceed 200 blocks.
   Even when cloud centers are hundreds of blocks apart their rendered volumes overlap heavily.

4. Perspective compression
   Clouds are observed near the horizon where depth perception collapses.
   Parallel motion at distance appears as overlap.

5. Radius shrink over lifetime
   Clouds start very large then shrink toward a steady radius.
   As radius decreases the visible centers appear to converge even when positions differ.

What is explicitly not happening
There is no biome focus attraction pulling clouds together.
Each cloud has a different biomeFocusPos often thousands of blocks apart.

There is no cloud to cloud interaction.
No merge logic is firing.
No position correction or snapping occurs.

There is no numerical instability.
Velocity signs and magnitudes remain stable over time.

Conclusion
Cloud stacking is an emergent visual effect caused by coherent wind direction low speed large cloud radii and camera projection.

From a simulation correctness perspective
The cloud system behaves as expected.
The cloud events file confirms no logical convergence or stacking bug.

From a visual design perspective
The system currently behaves like a single laminar air mass with no shear or divergence.
This is physically plausible but visually flat.

Status
No bug confirmed
Issue categorized as behaviour tuning or visual perception
Safe to defer investigation

Recommended future investigation directions when revisited
Examine whether clouds intentionally should sample distinct wind layers
Evaluate introducing slow directional divergence or shear
Evaluate altitude dependent wind variation
Evaluate per cloud angular drift over long timescales

---

If you want I can later turn this into a markdown doc or an internal design note format but this content is already complete and accurate for archiving.
