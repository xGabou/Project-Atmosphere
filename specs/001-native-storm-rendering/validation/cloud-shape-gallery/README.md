# Cloud-shape reference gallery

Production renderer only (lean FINAL): T243 shapes, T244 lighting, T226 rain / lower-trunk lighting; lighting mode 0, no experiment switches, no debug morphology, no legacy lobe union. Source commit `2a4da81c`.

One folder per cloud family, one image per camera view (1600x900). Every image inside a folder is the SAME cloud realization: the cloud is spawned once (just in time, motion frozen) and the camera is moved around it. Each folder has a `views.json` with the cloud type, spawn region id and world position, cloud bounds, shear axis, and for every view the camera position, aim point, yaw / pitch and terrain height.

## How the views are defined

* `d` = max(0.55 x extent + 0.8 x height, extent + 25) + 10 blocks from the cloud centre (`extent` = half the horizontal span of the region's clusters).
* front / back / upwind / downwind follow the cloud's own shear axis (the height-weighted offset of its clusters - the upper mass leans downwind); front = from upwind, back = from downwind; side / opposite_side / crosswind_* are perpendicular to it. upwind / downwind / crosswind_* are elevated (+0.35 d).
* above: top + 0.6 d. below / underside: between terrain and the cloud base (never under terrain, never above the base); storms put the below camera under the anvil overhang, outside the trunk.
* low_angle: 40 blocks above terrain, looking up. far: 2 d. close: max(0.6 d, extent + 20).
* Layer extras: sheet_extent (high oblique), underside, edge_profile (level, 1.4 d), top_profile (grazing the top). Convective extras: lower_body, tower, transition (level at 15 / 45 / 75 % of the height); storms add anvil.
* Every camera except below / underside is kept at least 15 blocks above the terrain heightmap.
* yaw / pitch use the Minecraft convention (negative pitch looks up, positive looks down).

## Families

### cirrus

* cloud type `cirrus`, region `91ea27f7-ebbe-453a-be92-21b39ef67000`, spawned at x 9130, z 72001; centre (9175, 71999), extent 635, base 734, top 743, downwind (0.99, -0.13)
* views (17): `side.png`, `opposite_side.png`, `front.png`, `back.png`, `above.png`, `below.png`, `low_angle.png`, `far.png`, `close.png`, `upwind.png`, `downwind.png`, `crosswind_left.png`, `crosswind_right.png`, `sheet_extent.png`, `underside.png`, `edge_profile.png`, `top_profile.png`
* observations: thin high layer: side / opposite_side / front / back / close / far / edge_profile / top_profile see it edge-on at the horizon and are nearly empty (near-identical to each other); below, upwind, downwind, underside and above show the streaks.

| view | camera (x, y, z) | yaw / pitch | note |
|---|---|---|---|
| side | (9265, 739, 72663) | -187.7 / -0.0 |  |
| opposite_side | (9086, 739, 71336) | -7.7 / -0.0 |  |
| front | (8512, 739, 72089) | -97.7 / -0.0 |  |
| back | (9839, 739, 71910) | 82.3 / -0.0 |  |
| above | (8976, 1145, 72026) | -97.7 / 63.7 |  |
| below | (9201, 399, 72188) | -187.7 / -60.6 |  |
| low_angle | (8769, 104, 72532) | -142.7 / -43.5 |  |
| far | (9728, 739, 73219) | -204.4 / -0.0 |  |
| close | (9263, 739, 72648) | -187.7 / -0.0 |  |
| upwind | (8512, 973, 72089) | -97.7 / 19.3 |  |
| downwind | (9839, 973, 71910) | 82.3 / 19.3 |  |
| crosswind_left | (9086, 973, 71336) | -7.7 / 19.3 |  |
| crosswind_right | (9265, 973, 72663) | -187.7 / 19.3 |  |
| sheet_extent | (8300, 1145, 72523) | -120.9 / 21.7 |  |
| underside | (9188, 399, 72094) | -111.7 / -40.5 |  |
| edge_profile | (9301, 739, 72928) | -187.7 / -0.0 |  |
| top_profile | (9283, 758, 72796) | -187.7 / 1.2 |  |

### cumulonimbus

* cloud type `cumulonimbus_calvus`, region `d3dc2c89-1591-45ea-8b07-c36549b46180`, spawned at x 9130, z 1; centre (9279, 0), extent 745, base 136, top 925, downwind (0.99, 0.14)
* views (17): `side.png`, `opposite_side.png`, `front.png`, `back.png`, `above.png`, `below.png`, `low_angle.png`, `far.png`, `close.png`, `upwind.png`, `downwind.png`, `crosswind_left.png`, `crosswind_right.png`, `lower_body.png`, `tower.png`, `transition.png`, `anvil.png`
* observations: terrain in the lower foreground of below / low_angle / lower_body; storm unobstructed. far is deliberately small.

| view | camera (x, y, z) | yaw / pitch | note |
|---|---|---|---|
| side | (9134, 531, 1041) | -172.0 / -0.0 |  |
| opposite_side | (9425, 531, -1041) | 8.0 / -0.0 |  |
| front | (8238, 531, -146) | -82.0 / -0.0 |  |
| back | (10320, 531, 146) | -262.0 / -0.0 |  |
| above | (8967, 1556, -44) | -82.0 / 72.9 |  |
| below | (9923, 96, 240) | -249.5 / -19.0 |  |
| low_angle | (8440, 104, 633) | -127.0 / -22.1 |  |
| far | (9599, 531, 2078) | -188.7 / -0.0 |  |
| close | (9173, 531, 758) | -172.0 / -0.0 |  |
| upwind | (8238, 899, -146) | -82.0 / 19.3 |  |
| downwind | (10320, 899, 146) | -262.0 / 19.3 |  |
| crosswind_left | (9425, 899, -1041) | 8.0 / 19.3 |  |
| crosswind_right | (9134, 899, 1041) | -172.0 / 19.3 |  |
| lower_body | (9134, 254, 1041) | -172.0 / -0.0 |  |
| tower | (9134, 491, 1041) | -172.0 / -0.0 |  |
| transition | (9134, 728, 1041) | -172.0 / 1.3 |  |
| anvil | (9105, 1135, 1249) | -172.0 / 12.9 |  |

### cumulus_congestus

* cloud type `cumulus_congestus`, region `347a2494-eb72-4ab9-917f-1aa47987f06d`, spawned at x 9130, z 36001; centre (9143, 36010), extent 62, base 239, top 364, downwind (1.00, -0.05)
* views (12): `side.png`, `opposite_side.png`, `front.png`, `back.png`, `above.png`, `below.png`, `low_angle.png`, `far.png`, `close.png`, `lower_body.png`, `tower.png`, `transition.png`
* observations: terrain in the lower foreground of low_angle / lower_body; cloud unobstructed.

| view | camera (x, y, z) | yaw / pitch | note |
|---|---|---|---|
| side | (9150, 301, 36154) | -183.0 / -0.0 |  |
| opposite_side | (9135, 301, 35866) | -3.0 / -0.0 |  |
| front | (8999, 301, 36018) | -93.0 / -0.0 |  |
| back | (9287, 301, 36003) | 87.0 / -0.0 |  |
| above | (9100, 450, 36013) | -93.0 / 73.8 |  |
| below | (9144, 153, 36029) | -183.0 / -80.9 |  |
| low_angle | (9046, 110, 36117) | -138.0 / -53.0 |  |
| far | (9240, 301, 36282) | -199.7 / -0.0 |  |
| close | (9147, 301, 36097) | -183.0 / -0.0 |  |
| lower_body | (9150, 258, 36154) | -183.0 / -0.0 |  |
| tower | (9150, 295, 36154) | -183.0 / -0.0 |  |
| transition | (9150, 333, 36154) | -183.0 / 1.5 |  |

### cumulus_humilis

* cloud type `cumulus_humilis`, region `55f91cfd-5ea9-43eb-83ab-66e6e47624a0`, spawned at x 9130, z 18001; centre (9128, 17999), extent 28, base 246, top 282, downwind (-0.97, 0.24)
* views (12): `side.png`, `opposite_side.png`, `front.png`, `back.png`, `above.png`, `below.png`, `low_angle.png`, `far.png`, `close.png`, `lower_body.png`, `tower.png`, `transition.png`
* observations: terrain (tree-covered hill) in the lower foreground of side-level views; the cloud itself is unobstructed. below and low_angle both look up at a small cloud and are similar.

| view | camera (x, y, z) | yaw / pitch | note |
|---|---|---|---|
| side | (9112, 264, 17937) | -13.9 / -0.0 |  |
| opposite_side | (9143, 264, 18060) | -193.9 / -0.0 |  |
| front | (9189, 264, 17983) | 76.1 / -0.0 |  |
| back | (9066, 264, 18014) | -103.9 / -0.0 |  |
| above | (9146, 320, 17994) | 76.1 / 71.1 |  |
| below | (9126, 159, 17990) | -13.9 / -85.0 |  |
| low_angle | (9160, 117, 17945) | 31.1 / -66.7 |  |
| far | (9063, 264, 17890) | -30.6 / -0.0 |  |
| close | (9116, 264, 17952) | -13.9 / -0.0 |  |
| lower_body | (9112, 252, 17937) | -13.9 / -0.0 |  |
| tower | (9112, 262, 17937) | -13.9 / -0.0 |  |
| transition | (9112, 273, 17937) | -13.9 / 1.0 |  |

### cumulus_mediocris

* cloud type `cumulus_mediocris`, region `de48cf1b-13f3-48c1-97ec-47f0732762c2`, spawned at x 9130, z 27001; centre (9133, 27005), extent 51, base 242, top 315, downwind (0.57, 0.82)
* views (12): `side.png`, `opposite_side.png`, `front.png`, `back.png`, `above.png`, `below.png`, `low_angle.png`, `far.png`, `close.png`, `lower_body.png`, `tower.png`, `transition.png`
* observations: terrain in the lower foreground of front / back / close / lower_body / tower / transition; cloud unobstructed. front and back are near-identical at low resolution (compact cloud).

| view | camera (x, y, z) | yaw / pitch | note |
|---|---|---|---|
| side | (9054, 278, 27060) | -125.0 / -0.0 |  |
| opposite_side | (9212, 278, 26949) | 55.0 / -0.0 |  |
| front | (9078, 278, 26926) | -35.0 / -0.0 |  |
| back | (9188, 278, 27083) | -215.0 / -0.0 |  |
| above | (9117, 373, 26981) | -35.0 / 73.0 |  |
| below | (9121, 153, 27013) | -125.0 / -81.9 |  |
| low_angle | (9038, 104, 26988) | -80.0 / -61.1 |  |
| far | (9014, 278, 27156) | -141.7 / -0.0 |  |
| close | (9075, 278, 27045) | -125.0 / -0.0 |  |
| lower_body | (9054, 253, 27060) | -125.0 / -0.0 |  |
| tower | (9054, 275, 27060) | -125.0 / -0.0 |  |
| transition | (9054, 297, 27060) | -125.0 / 1.3 |  |

### nimbostratus

* cloud type `nimbostratus`, region `805e522f-25f2-4ce3-96b4-316f46b650fc`, spawned at x 9130, z 63001; centre (9125, 63015), extent 320, base 243, top 299, downwind (1.00, 0.00)
* views (13): `side.png`, `opposite_side.png`, `front.png`, `back.png`, `above.png`, `below.png`, `low_angle.png`, `far.png`, `close.png`, `sheet_extent.png`, `underside.png`, `edge_profile.png`, `top_profile.png`
* observations: terrain in the foreground of low_angle / below / underside (below views look up from just above terrain); deck unobstructed. far and edge_profile are both distant level views.

| view | camera (x, y, z) | yaw / pitch | note |
|---|---|---|---|
| side | (9125, 271, 63370) | -180.0 / -0.0 |  |
| opposite_side | (9125, 271, 62659) | 0.0 / -0.0 |  |
| front | (8770, 271, 63015) | -90.0 / -0.0 |  |
| back | (9480, 271, 63015) | 90.0 / -0.0 |  |
| above | (9018, 512, 63015) | -90.0 / 66.2 |  |
| below | (9125, 154, 63111) | -180.0 / -47.1 |  |
| low_angle | (8874, 104, 63266) | -135.0 / -25.2 |  |
| far | (9329, 271, 63695) | -196.7 / -0.0 |  |
| close | (9125, 271, 63355) | -180.0 / -0.0 |  |
| sheet_extent | (8627, 512, 63228) | -113.2 / 24.0 |  |
| underside | (9125, 154, 63063) | -104.0 / -24.3 |  |
| edge_profile | (9125, 271, 63512) | -180.0 / -0.0 |  |
| top_profile | (9125, 314, 63441) | -180.0 / 3.5 |  |

### stratocumulus

* cloud type `stratocumulus`, region `9de8cb38-bdf2-4b95-8061-d54a04ac0fff`, spawned at x 9130, z 54001; centre (9120, 54003), extent 181, base 248, top 284, downwind (0.08, -1.00)
* views (13): `side.png`, `opposite_side.png`, `front.png`, `back.png`, `above.png`, `below.png`, `low_angle.png`, `far.png`, `close.png`, `sheet_extent.png`, `underside.png`, `edge_profile.png`, `top_profile.png`
* observations: terrain in the lower foreground of low_angle / close / edge_profile; cloud unobstructed.

| view | camera (x, y, z) | yaw / pitch | note |
|---|---|---|---|
| side | (9335, 266, 54019) | -265.6 / -0.0 |  |
| opposite_side | (8904, 266, 53987) | -85.6 / -0.0 |  |
| front | (9103, 266, 54218) | -175.6 / -0.0 |  |
| back | (9136, 266, 53788) | 4.4 / -0.0 |  |
| above | (9115, 413, 54068) | -175.6 / 66.3 |  |
| below | (9174, 160, 54007) | -265.6 / -60.9 |  |
| low_angle | (9260, 112, 54167) | -220.6 / -35.5 |  |
| far | (9541, 266, 53911) | 77.7 / -0.0 |  |
| close | (9320, 266, 54018) | -265.6 / -0.0 |  |
| sheet_extent | (9226, 413, 54314) | -198.8 / 24.1 |  |
| underside | (9147, 156, 54005) | -189.7 / -39.4 |  |
| edge_profile | (9421, 266, 54026) | -265.6 / -0.0 |  |
| top_profile | (9378, 299, 54023) | -265.6 / 4.9 |  |

### stratus_nebulosus

* cloud type `stratus_nebulosus`, region `38095f71-59ae-415e-b11a-f2e96ea6f4f0`, spawned at x 9130, z 45001; centre (9113, 44982), extent 333, base 250, top 269, downwind (1.00, 0.00)
* views (13): `side.png`, `opposite_side.png`, `front.png`, `back.png`, `above.png`, `below.png`, `low_angle.png`, `far.png`, `close.png`, `sheet_extent.png`, `underside.png`, `edge_profile.png`, `top_profile.png`
* observations: thin deck: the level side views (side, opposite_side, back, far, edge_profile, top_profile) show the same thin line; above, below, underside, low_angle carry the shape.

| view | camera (x, y, z) | yaw / pitch | note |
|---|---|---|---|
| side | (9113, 260, 45350) | -180.0 / -0.0 |  |
| opposite_side | (9113, 260, 44614) | 0.0 / -0.0 |  |
| front | (8745, 260, 44982) | -90.0 / -0.0 |  |
| back | (9481, 260, 44982) | 90.0 / -0.0 |  |
| above | (9002, 490, 44982) | -90.0 / 64.4 |  |
| below | (9113, 157, 45082) | -180.0 / -44.4 |  |
| low_angle | (8852, 104, 45242) | -135.0 / -22.9 |  |
| far | (9325, 260, 45688) | -196.7 / -0.0 |  |
| close | (9113, 260, 45335) | -180.0 / -0.0 |  |
| sheet_extent | (8597, 490, 45203) | -113.2 / 22.3 |  |
| underside | (9113, 157, 45032) | -104.0 / -24.3 |  |
| edge_profile | (9113, 260, 45498) | -180.0 / -0.0 |  |
| top_profile | (9113, 284, 45424) | -180.0 / 2.4 |  |

### supercell

* cloud type `supercell`, region `f1a73bab-09dc-4a60-a5e2-7cb207d4ae4f`, spawned at x 9158, z -13; centre (9320, -13), extent 748, base 136, top 924, downwind (1.00, 0.09)
* views (17): `side.png`, `opposite_side.png`, `front.png`, `back.png`, `above.png`, `below.png`, `low_angle.png`, `far.png`, `close.png`, `upwind.png`, `downwind.png`, `crosswind_left.png`, `crosswind_right.png`, `lower_body.png`, `tower.png`, `transition.png`, `anvil.png`
* observations: terrain in the lower foreground of below / low_angle / lower_body; storm unobstructed. far is deliberately small.

| view | camera (x, y, z) | yaw / pitch | note |
|---|---|---|---|
| side | (9226, 530, 1034) | -174.8 / -0.0 |  |
| opposite_side | (9415, 530, -1060) | 5.2 / -0.0 |  |
| front | (8273, 530, -107) | -84.8 / -0.0 |  |
| back | (10368, 530, 81) | -264.8 / -0.0 |  |
| above | (9006, 1555, -41) | -84.8 / 72.9 |  |
| below | (9977, 96, 196) | -252.3 / -18.9 |  |
| low_angle | (8513, 104, 661) | -129.8 / -22.1 |  |
| far | (9741, 530, 2048) | -191.5 / -0.0 |  |
| close | (9251, 530, 751) | -174.8 / -0.0 |  |
| upwind | (8273, 898, -107) | -84.8 / 19.3 |  |
| downwind | (10368, 898, 81) | -264.8 / 19.3 |  |
| crosswind_left | (9415, 898, -1060) | 5.2 / 19.3 |  |
| crosswind_right | (9226, 898, 1034) | -174.8 / 19.3 |  |
| lower_body | (9226, 254, 1034) | -174.8 / -0.0 |  |
| tower | (9226, 491, 1034) | -174.8 / -0.0 |  |
| transition | (9226, 727, 1034) | -174.8 / 1.3 |  |
| anvil | (9207, 1134, 1244) | -174.8 / 12.9 |  |

## Capture status

All expected views exist for every family (see `cloud_gallery_export.py` status). No camera was placed inside terrain; no below-cloud camera was above the cloud base. The first supercell capture saved a stale frame for `above` (identical to `back`); the whole supercell family was re-captured in its own session so the folder stays one realization.

## Reproducing

Harness (run from the repo root, client closed): restore `StormT098CaptureDriver.java` / `StormLobeDescriptor.java`, run `tools/t241_capture_repo.py '[]'` then `tools/cloud_gallery.py '<json family list>'`, launch `runClient`, then `tools/cloud_gallery_export.py <runClient log>` and `tools/cloud_gallery_readme.py`. The capture patches are temporary and must be reverted after the run.
