# Project Structure Overview

Project Atmosphere is organized as a backend climate simulation with client-side render and effect layers on top. The codebase is not random, but it is broader than it needs to be for a future cloud renderer because many classes still mix orchestration, state ownership, compatibility, and debug behavior.

## Plain Language Summary

- `manager/` holds top-level orchestration and server lifecycle coordination.
- `modules/` contains most of the actual simulation and weather logic.
- `client/` contains client state, tick handling, HUD/effects, and render bridges.
- `client/render/` contains renderer hooks, shader wrappers, debug render paths, and DH-aware pipeline logic.
- `client/fog/` contains fog state and fog classification helpers.
- `network/` contains packet transport and sync bridges.
- `api/` contains stable external contracts and value objects.
- `compat/` contains adapters to external mods and systems.
- `util/` contains helper utilities, scheduling, sampling, and shared cross-cutting helpers.
- `mixin/` contains compatibility hooks into Minecraft and Simple Clouds client rendering.
- `resources/assets/projectatmosphere/` contains shaders and render assets.
- `resources/data/projectatmosphere/` contains gameplay data, tags, recipes, and cloud profile resources.
- `resources/data/simpleclouds/` contains Simple Clouds cloud type and spawn definitions.

## High-Level Organization Health

- The backend climate model is the strongest part of the project.
- Wind and weather resolution are already centralized enough to support future render inputs.
- Tornado and hurricane systems already have snapshot-style data, which is good for a future renderer boundary.
- Client caches already exist, which reduces the amount of new architecture needed.
- The most confusing part is the overlap between managers, compat, render hooks, and debug paths.

## Biggest Structural Risk

The biggest structural risk is not missing data. It is unclear ownership. If future cloud renderer work reaches directly into live simulation, the renderer will become hard to maintain and hard to separate from gameplay systems.

