# Project Atmosphere Clouds — Renderer & Dynamic Cloud Architecture Notes

## 1. Current context

Project Atmosphere is moving toward a realistic cloud and weather rendering system for Minecraft Forge 1.20.1.

The current renderer work started with a GLSL volumetric cloud renderer using:

* `RenderLevelStageEvent.Stage.AFTER_WEATHER`
* fullscreen shader pass
* camera-based ray reconstruction
* debug AABB wireframe
* `bounds` debug mode
* `vertical_y01` debug mode
* `vertical_envelope` debug mode
* `primary_mass` debug mode
* `final_density` debug mode

At one point, the basic rendering path was mostly confirmed:

* camera position was stable
* ray reconstruction was working
* AABB bounds could be debugged
* vertical coordinate debug was correct
* vertical envelope debug was correct

The original assumption was that the remaining issue was mostly cloud density morphology.

However, after testing larger clouds like cumulonimbus, it became clear that the issue is deeper than just shader shape tuning.

---

## 2. Problem with the current AABB-per-cloud renderer

The current rendering model is essentially:

```text
1 CloudRenderSnapshot
→ 1 AABB
→ 1 shader pass
→ 1 cloud texture/composite
```

This works as a simple debug model, but it becomes fragile for realistic weather.

Observed problems:

* shader volume and wireframe can mismatch
* old fragments can remain visible
* multiple snapshots/passes can create artifacts
* cloud shapes can appear outside their intended area
* density tuning creates blobs, cones, eggs, or vertical towers
* clouds look trapped inside boxes
* multiple cloud volumes do not naturally merge
* interaction between clouds is not handled naturally

Conclusion:

```text
The AABB-per-cloud render model is not a good final architecture for Project Atmosphere.
```

It may stay useful for debugging, but it should not be the final visible renderer.

---

## 3. PMWeather-style rendering comparison

PMWeather does not really render clouds as independent objects.

It does not work like:

```text
cloud object
→ bounding box
→ render pass
```

Instead, it behaves more like:

```text
fullscreen pass
→ raymarch through the world
→ sample a global cloud density field
→ apply weather/noise/storm parameters
→ composite clouds
```

In that model:

```text
cloud 143 does not exist
cloud 147 does not exist
```

There is only a continuous cloud density field.

Advantages:

* no per-cloud AABB mismatch
* no object collision between clouds
* no hard seams between cloud blobs
* better for large atmospheric systems
* better for overcast, storms, and large-scale weather

Disadvantages:

* difficult to control one small cloud individually
* difficult to say “this exact cloud merges with that exact cloud”
* less direct simulation of individual small clouds

---

## 4. Simple Clouds comparison

Simple Clouds is closer to a formation/chunk/volume system.

It likely does not simulate every tiny puff as an independent weather object either. Instead, it works with larger generated formations or cloud structures.

Advantages:

* more structured than a pure procedural field
* good for organized formations
* cloud formations can feel more tangible

Disadvantages:

* can become rigid
* less ideal for fully dynamic weather systems
* not naturally designed for supercells, hurricanes, tornado funnels, cloud merging, and evolving weather logic

---

## 5. Final direction: hybrid architecture

The best direction for Project Atmosphere is not:

```text
copy PMWeather exactly
```

and not:

```text
continue AABB-per-cloud rendering
```

and not:

```text
fully copy Simple Clouds
```

The best direction is a hybrid:

```text
Project Atmosphere simulation
= weather systems, atmosphere data, dynamic clouds

Project Atmosphere rendering
= global volumetric density field rendered in fullscreen
```

Key idea:

```text
PA simulates weather systems.
The shader renders a world-space volumetric cloud field produced by those systems.
```

---

## 6. Regions should represent atmosphere data, not cloud containers

The old idea of “cloud regions” should be reframed.

A region should not mean:

```text
this region contains this cloud
this region is a cumulonimbus
this region is a stratocumulus
```

A region should mean:

```text
this area has temperature data
this area has humidity data
this area has pressure data
this area has wind data
this area has instability data
this area has dryness data
this area has rotation/vorticity data
```

The region is environmental data.

A cloud does not belong to a region.

A cloud moves through regions and evolves according to their data.

Key rule:

```text
Regions describe the atmosphere.
Clouds are dynamic masses influenced by the atmosphere.
```

---

## 7. Clouds as dynamic weather masses

A cloud should be treated as a dynamic weather mass with its own state.

A cloud can have:

* average position
* current size
* water mass
* density
* base height
* top height
* horizontal spread
* vertical development
* rotation
* maturity
* age
* velocity
* growth rate
* decay rate

A cloud should not have a permanent fixed radius or permanent fixed AABB.

It has:

```text
current size
current influence area
current center/mass position
```

Those values can evolve.

A cloud can:

* grow
* shrink
* move
* merge
* split
* dissipate
* become more organized
* become storm-like
* develop a tower
* develop a tornado state
* become hurricane-like later

---

## 8. Cloud categories should be broad, not overly specific

Project Atmosphere does not need 60 cloud types.

Major visual families are enough:

* cumulus-like
* stratocumulus-like
* stratus-like
* cumulonimbus-like
* supercell-like
* cirrus-like later
* hurricane-like later

These should not necessarily be rigid permanent types.

Better model:

```text
A cloud has visual weights or dominant visual family derived from its current state.
```

Example:

```text
warm + humid + unstable
→ more cumulus / tower / cumulonimbus

stable + layered + low vertical growth
→ more stratocumulus / stratus

dry + stable
→ erosion / dissipation

rotating + organized + intense
→ storm / supercell
```

So the cloud type becomes a result of the weather state, not a forced label.

---

## 9. Disaster and extreme weather states

Project Atmosphere does not need full real-world physics simulation for disasters.

It can use condition-based states.

### Tornado

A storm/supercell can develop a tornado if conditions are met:

* strong rotation
* high instability
* low cloud base
* strong storm organization
* strong wall cloud / funnel potential

Then the renderer can show:

* wall cloud
* funnel cloud
* tornado funnel
* rotation
* touchdown state

The tornado does not need to be simulated voxel by voxel.

It is a visual and gameplay state attached to the parent storm.

### Hurricane

A system can become hurricane-like if conditions are met:

* over warm ocean
* very high humidity
* very low pressure
* large organized rotation
* large size
* persistent structure

Then the renderer can show:

* eye
* eyewall
* spiral bands
* large cloud shield

The eye is visually a density reduction in the cloud field.

---

## 10. Real-world cloud merging behavior

Clouds do not merge like solid objects.

A cloud is visible where air is saturated and condensation occurs.

When two clouds approach each other, several things can happen.

### Humid air between them

If the air between them is humid or near saturation:

```text
cloud edges approach
→ condensation appears between them
→ a soft bridge forms
→ they visually connect
→ they may become one larger cloud mass
```

### Dry air between them

If the air between them is dry:

```text
edges erode
→ clouds remain separated
→ one or both may dissipate
```

### One cloud dominates

If one cloud is stronger:

```text
strong cloud grows
weak cloud loses mass
weak cloud is absorbed or dissipates
```

Important rule:

```text
Two clouds should not simply intersect without a reaction.
```

Possible reactions:

* soft bridge
* visual merge
* erosion
* absorption
* dissipation
* logical merge after enough time

---

## 11. Small clouds inside a cumulus field

A large cumulus field can visually contain hundreds or thousands of small clouds.

The user clarified that sometimes each small visible cloud should be treated as a “cloud”, not merely a region.

Example:

```text
CPU says: there is a cumulus field here
GPU generates: 343 small visible clouds
cloud 143 and cloud 147 approach each other
CPU should be able to decide they merge or interact
```

For this to work, the small clouds generated by the GPU must have stable identities.

Without stable identity:

```text
cloud 143 does not really exist
cloud 147 does not really exist
CPU cannot tell GPU to merge them
```

With stable identity:

```text
cloud 143 is a stable procedural slot
cloud 147 is a stable procedural slot
CPU can say: cloud 143 and cloud 147 should merge
GPU can apply that relation visually
```

So small clouds near the player can be procedural but still identifiable.

---

## 12. GPU-generated clouds with stable IDs

The idea:

```text
A cumulus field has a stable seed.
Inside it, the GPU can generate many small cloud slots.
Each slot has a stable ID.
```

Example:

```text
cumulus field #1
seed = 98421

small cloud 143
small cloud 147
small cloud 201
```

The GPU can generate each small cloud’s position, size, and detail from:

```text
field seed + small cloud ID
```

This means cloud 143 remains cloud 143 over time.

Then the CPU can send relation data:

```text
inside cumulus field #1:
cloud 143 and cloud 147
merge progress = 0.4
```

The GPU then knows which two procedural clouds to visually connect.

---

## 13. GPU feedback to CPU

The GPU should not call Java during rendering.

This is not viable:

```text
GPU renders a sample
→ detects cloud collision
→ calls Java
→ waits for response
→ continues rendering
```

Instead, the correct model is delayed feedback:

```text
GPU produces a low-resolution summary
CPU reads it later
CPU analyzes it asynchronously
CPU sends decisions back to the renderer
GPU applies those decisions in later frames
```

This is acceptable because clouds move slowly.

---

## 14. Low-resolution local cloud map

A viable idea is for the GPU/renderer to generate a low-resolution map of visible small clouds near the player.

Example:

```text
dynamic area around player:
radius = 500 blocks
diameter = 1000 blocks

cell size:
about 30 blocks
```

This produces roughly:

```text
1000 / 30 ≈ 33 cells per side
33 × 33 ≈ 1089 cells
```

This is manageable.

The map does not need to update every frame.

Possible update rate:

```text
every 10–20 seconds for normal cumulus
every 5–10 seconds for important storms
faster only for active tornado/supercell states
```

The map can contain:

* empty / occupied
* dominant small cloud ID
* density level
* average height
* strong/weak cloud presence
* maybe humidity/transition state later

The CPU can use this to decide:

```text
cloud 143 and 147 are near
conditions are humid
bridge strength should increase

or

air is dry
erosion should increase

or

one cloud should absorb the other
```

---

## 15. Local dynamic zone and LOD

The proposed distance model:

### 0–500 blocks

Rich dynamic simulation zone.

Small clouds can have:

* stable IDs
* interaction state
* bridge state
* merge progress
* erosion state
* absorption state
* feedback map participation

This is where the player can actually notice small cloud behavior.

### 500–1000 / 1200 blocks

Transition zone.

Small clouds gradually lose individual simulation.

They keep:

* seed
* approximate position
* size
* general state

But interaction detail is reduced.

### 1000–2000 blocks

Far cloud rendering.

Clouds become more PMWeather-like:

* procedural field
* animated cloud cover
* less individual identity
* no small-cloud merge simulation

This is acceptable because the player cannot clearly observe small cloud collisions at this distance.

### 2000+ blocks

Very far clouds are masked by:

* haze
* atmospheric fog
* cloud cover
* sky blending

No detailed simulation needed.

---

## 16. Viability conclusion

The system is not viable if it tries to do:

```text
entire world
10,000 individually tracked small clouds
every cloud merges with every other cloud
constant GPU feedback
high-resolution readback
```

That would be too heavy and too complex.

The system is viable if it does:

```text
local player zone only
few active cloud fields
100–500 small identifiable clouds in the important zone
slow feedback
low-resolution maps
simple interactions
LOD fallback for far clouds
```

Key rule:

```text
Only simulate individual small-cloud behavior where the player can actually notice it.
```

---

## 17. Final rendering direction

The final renderer should move away from:

```text
1 snapshot
1 AABB
1 shader pass
1 composite
```

and toward:

```text
fullscreen global raymarch
world-space cloud density field
cloud systems as density sources
small identifiable cloudlets only near the player
slow feedback map
far-cloud procedural LOD
```

The old AABB renderer can remain useful for debugging, but not as the final visible cloud renderer.

---

## 18. Final conceptual model

Project Atmosphere should be structured conceptually as:

```text
Atmosphere data
→ cloud system evolution
→ visual cloud families
→ local identifiable small clouds
→ interaction states
→ disaster states
→ global volumetric renderer
→ far cloud LOD
```

The CPU tracks:

```text
systems
states
relationships
major decisions
```

The GPU renders:

```text
density
puffs
turbulence
bridges
erosion
lighting
sunset tint
far fields
```

Key rules:

```text
Do not track every voxel.
Do not make every far puff an object.
Do not render each cloud as a separate AABB pass.
Track identities only where useful.
Render the final sky as a continuous volumetric field.
```

---

## 19. Core philosophy

```text
Regions describe the atmosphere.
Clouds are living weather masses.
Small nearby clouds can have stable visual identities.
The CPU decides slow interactions.
The GPU renders the beautiful continuous result.
Far clouds become procedural illusion.
```

This is the strongest direction for Project Atmosphere because it supports:

* realistic cumulus fields
* cloud merging
* erosion
* absorption
* dynamic growth and decay
* supercells
* tornado funnels
* hurricanes with eyes
* sunset tint
* large-scale atmospheric cloud cover
* near/far cloud LOD
* scalable performance
