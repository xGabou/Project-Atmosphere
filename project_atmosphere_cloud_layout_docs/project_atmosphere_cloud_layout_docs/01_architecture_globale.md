# Architecture globale du système de nuages

## But

Project Atmosphere doit garder la météo et les objets de nuage comme source de vérité.

Le renderer doit seulement transformer des snapshots clients en rendu visuel, shadows, uniforms shader et fallback darkening.

## Séparation principale

```text
Backend PA
    possède la météo, les régions météo, les clouds logiques, la persistance et la source de vérité

Frontend PA
    possède les snapshots rendables, le cache client, l’interpolation et les hooks de rendu

Renderer PA
    possède les draw calls, les shaders, les render targets, la densité visuelle, les shadows et le fallback darkening
```

## Flow final visé

```text
Server weather state
    vers CloudRegionState
    vers CloudRegionRegistry
    vers CloudRegionRenderData
    vers SyncCloudRegionsPacket
    vers CloudRenderSnapshotBuilder
    vers CloudRenderStateCache
    vers CloudRenderController
    vers CloudRenderer
    vers CloudDensityProvider
    vers CloudShadowRenderer
    vers CloudLightingBridge
    vers FallbackDarkeningPass
```

## Règles strictes

| Règle | Pourquoi |
|---|---|
| Le renderer ne lit jamais `CloudRegionState` | Le serveur possède la simulation |
| Le backend ne crée jamais `CloudRenderSnapshot` | Le snapshot est client |
| `CloudRegionRenderData` est le contrat transport | Il coupe la simulation du client |
| `debugSnapshot` et `currentSnapshots` restent séparés | Le debug ne doit pas polluer le vrai rendu |
| La densité doit être partagée | Les clouds visibles et les shadows doivent matcher |
| Les shaders lisent des uniforms ou textures, pas la simulation | Le GPU ne doit pas connaître le backend |

## Ce que le système ne doit pas faire

```text
CloudRenderer lit ForecastOrchestrator
CloudRenderer lit CloudManager
CloudRenderSnapshot lit CloudRegionState
CloudDebugRenderHook lit currentSnapshots
CloudRenderHook lit debugSnapshot
CloudShadowRenderer invente une densité différente
CloudLightingBridge simule la météo
```
