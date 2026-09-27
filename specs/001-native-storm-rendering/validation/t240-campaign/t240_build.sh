#!/bin/bash
# T240 build switch for build-vs-build runs.
#   t240_build.sh A   -> the HEAD build (742c09b5): T240 changes stashed, new files parked
#   t240_build.sh B   -> the T240 build: stash popped, new files restored
# Never touches PAchangelog.md. Refuses while a Minecraft client is running.
set -e
cd /c/Users/matga/Downloads/Project-Atmosphere
PARK="C:/Users/matga/AppData/Local/Temp/claude/C--Users-matga-Downloads-Project-Atmosphere/010bcca8-aad5-4c5d-9694-26b460daf40b/scratchpad/t240_park"
V=src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric
TRACKED="build.gradle
src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh
src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.json
src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudMorphologyGenerators.java
$V/StormGeometryBuild.java
$V/StormGeometryBuildCoordinator.java
$V/StormLobeSpatialIndex.java
$V/StormFieldSampler.java
$V/VolumetricCloudDebugConfig.java
$V/VolumetricCloudRenderer.java
src/test/java/net/Gabou/projectatmosphere/clouds/simulation/CloudMorphologyTopologySandbox.java
src/test/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormVolumetricGeometrySandbox.java"
NEW="$V/StormBodySkeleton.java
$V/StormBodyEvaluator.java
src/test/java/net/Gabou/projectatmosphere/clouds/simulation/StormBodyAnatomySandbox.java"
if tasklist | grep -i java | awk '{print $5}' | tr -d ',' | awk '$1 > 3000000 {found=1} END {exit !found}'; then
  echo "a client appears to be running; refusing"; exit 1
fi
case "$1" in
  A)
    git diff --quiet -- $TRACKED && { echo "no T240 changes to stash (already A?)"; exit 1; }
    git stash push -q -m t240-build-switch -- $TRACKED
    mkdir -p "$PARK"
    for f in $NEW; do mkdir -p "$PARK/$(dirname $f)"; mv "$f" "$PARK/$f"; done
    echo "A: HEAD build sources in place" ;;
  B)
    git stash list | grep -q t240-build-switch || { echo "no t240 stash"; exit 1; }
    git stash pop -q
    for f in $NEW; do mv "$PARK/$f" "$f"; done
    echo "B: T240 build sources in place" ;;
  *) echo "usage: A|B"; exit 2 ;;
esac
git status --short | grep -v "^??" | head -20
