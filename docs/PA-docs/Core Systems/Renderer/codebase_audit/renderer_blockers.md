# Renderer Blockers

## Must Fix Before Fake Debug Cloud

- There is no single documented cloud render snapshot boundary yet.
- The renderer-facing data is still spread across storm, forecast, and client cache classes.
- Debug paths are mixed into real render paths in several places.

## Must Fix Before PA Driven Clouds

- `SimpleCloudsCompat` still carries too much policy for a pure adapter.
- Tornado and hurricane render data are not centralized enough.
- The client cache story is not yet unified into one future render cache.
- It is still too easy for a renderer to reach into simulation classes directly.

## Must Fix Before Cloud Shadows

- There is no explicit lighting or shadow data contract yet.
- Fog, cloud darkening, and whiteout behavior are not separated cleanly enough.
- The backend does not yet expose a clearly named shadow hint or optical depth model.

## Must Fix Before Atmospheric Shaders Integration

- Shader-facing uniforms are not organized around one renderer contract.
- The render hook layer is fragmented across mixins, debug utilities, and pipeline selectors.
- There is no stable place where lighting, shadowing, and fallback darkening are assembled for shaders.

## Can Wait

- Cosmetic renames of broad manager classes.
- Extracting every small helper from utility-heavy classes.
- Narrowing some debug-only helpers that are not on the critical render path.
- Minor duplication cleanup in resource JSON selection, as long as the mapping rules stay documented.

