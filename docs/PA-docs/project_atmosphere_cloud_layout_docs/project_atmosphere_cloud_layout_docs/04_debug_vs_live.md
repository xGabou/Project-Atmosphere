# Séparation debug et live

## Décision

Le debug et le live doivent être deux chemins séparés.

Ils peuvent utiliser le même type `CloudRenderSnapshot`, mais ils ne doivent pas utiliser le même champ de cache.

## Debug path

```text
CloudDebugSnapshotFactory
    vers CloudDebugStateInitializer
    vers CloudRenderStateCache.debugSnapshot
    vers CloudDebugRenderHook
    vers CloudWireframeRenderer
```

Ce chemin sert seulement à afficher une box ou un volume visuel temporaire.

Il ne doit pas utiliser `currentSnapshots`.

Il ne doit pas représenter les vrais clouds.

## Live path

```text
CloudRegionRenderData
    vers CloudRenderSnapshotBuilder
    vers CloudRenderStateUpdater
    vers CloudRenderStateCache.currentSnapshots
    vers CloudRenderController
    vers CloudRenderer
```

Ce chemin représente les vrais clouds PA.

Il ne doit pas utiliser `debugSnapshot`.

Il ne doit pas passer par `CloudDebugRenderHook`.

## Cache

`CloudRenderStateCache` doit contenir deux espaces séparés.

```text
debugSnapshot
currentSnapshots
```

`debugSnapshot` est unique parce que le debug actuel affiche un seul volume.

`currentSnapshots` est une collection parce que le monde peut contenir plusieurs masses de nuage.

## Règles

| Classe | Peut lire debugSnapshot | Peut lire currentSnapshots |
|---|---:|---:|
| `CloudDebugRenderHook` | oui | non |
| `CloudWireframeRenderer` | indirectement | non pour l’instant |
| `CloudRenderController` | non | oui |
| `CloudRenderer` | non | via controller |
| `CloudRenderStateUpdater` | non | écrit seulement |
| `CloudDebugStateInitializer` | écrit seulement | non |

## À ne pas faire

```text
CloudDebugRenderHook rend currentSnapshots
CloudRenderHook rend debugSnapshot
CloudRenderStateUpdater écrit debugSnapshot
CloudDebugStateInitializer écrit currentSnapshots
CloudRenderer appelle CloudWireframeRenderer pour le rendu réel
```
