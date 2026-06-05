# Project Atmosphere Cloud Renderer Layout Index

## Objectif

Ce dossier sépare le plan du système de nuages Project Atmosphere en fichiers courts.

Le but est de garder une architecture propre entre le backend, le frontend, le debug, le rendu live, les shadows, les shaders et les passes fallback.

## Fichiers

| Fichier | Contenu |
|---|---|
| `01_architecture_globale.md` | Vision globale et règles principales |
| `02_backend_layout.md` | Classes backend, responsabilités, interactions et UML |
| `03_frontend_layout.md` | Classes frontend, responsabilités, interactions et UML |
| `04_debug_vs_live.md` | Séparation stricte entre debugSnapshot et currentSnapshots |
| `05_renderer_shadows_shaders.md` | Rendu live futur, density provider, shadows, lighting bridge et fallback |
| `06_ordre_implementation.md` | Ordre propre pour continuer sans mélanger les couches |

## Règle centrale

```text
Backend owns clouds
Frontend owns snapshots
Renderer owns visuals
Density provider owns cloud shape interpretation
Shadow renderer reuses the same density
Lighting bridge exposes the result
Debug stays isolated
```
