# Ordre d’implémentation recommandé

## Phase A

Stabiliser le backend cloud object.

Classes concernées :

```text
CloudRegionState
CloudRegionRegistry
CloudRegionStorage
CloudRegionSavedData
CloudRegionBackend
CloudRegionRenderData
CloudRegionRenderDataFactory
```

Résultat attendu :

```text
PA peut posséder des clouds logiques persistants sans renderer
```

## Phase B

Stabiliser le frontend state model.

Classes concernées :

```text
CloudRenderSnapshot
CloudRenderSnapshotBuilder
CloudRenderStateCache
CloudRenderStateUpdater
CloudRenderController
CloudRenderer
CloudRenderHook
```

Résultat attendu :

```text
Le client peut recevoir des snapshots live et les garder sans les rendre
```

## Phase C

Finaliser la séparation debug.

Classes concernées :

```text
CloudDebugSnapshotFactory
CloudDebugStateInitializer
CloudDebugRenderHook
CloudWireframeRenderer
```

Résultat attendu :

```text
Le debug wireframe est séparé du rendu live
```

## Phase D

Ajouter le transport.

Classes concernées :

```text
SyncCloudRegionsPacket
CloudRegionSyncManager
ClientCloudRegionPacketHandler
```

Résultat attendu :

```text
Le serveur envoie des CloudRegionRenderData au client
```

## Phase E

Ajouter le vrai rendu visible.

Classes concernées :

```text
CloudRenderFrameContext
CloudRenderProfile
CloudRenderTargetManager
CloudDensityProvider
CloudRaymarchRenderer
CloudRenderer
```

Résultat attendu :

```text
Le renderer lit currentSnapshots et produit les clouds visibles
```

## Phase F

Ajouter les shadows.

Classes concernées :

```text
CloudShadowRenderer
CloudDensityProvider
CloudRenderTargetManager
```

Résultat attendu :

```text
La shadow map vient de la même densité que les clouds visibles
```

## Phase G

Ajouter shader integration et fallback.

Classes concernées :

```text
CloudLightingBridge
CloudUniformUploader
FallbackDarkeningPass
CloudRenderDiagnostics
```

Résultat attendu :

```text
Les shaders supportés peuvent utiliser les shadows PA et les autres utilisateurs ont un fallback propre
```

## Prochaine action recommandée

Avant de continuer le code, comparer les classes actuelles avec ce layout.

Ensuite décider quoi faire dans cet ordre :

```text
1. Renommer ou déplacer les classes debug dans frontend.debug
2. Vérifier que currentSnapshots est une collection
3. Garder CloudRenderer vide tant que le rendu réel n’est pas commencé
4. Ajouter le packet seulement quand le frontend live est stable
```
