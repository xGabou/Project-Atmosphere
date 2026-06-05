# Renderer, shadows et shaders

## But

Le renderer live doit utiliser les snapshots clients pour produire des clouds visibles, des shadows et des données shader.

Il ne doit pas simuler la météo.

## Rendu visible

Le rendu visible futur passe par ces classes.

```text
CloudRenderer
    vers CloudRaymarchRenderer
    vers CloudDensityProvider
    vers CloudRenderTargetManager
```

`CloudRaymarchRenderer` s’occupe du rendu volumétrique ou du rendu simplifié.

`CloudRenderer` reste un orchestrateur.

## Density provider

`CloudDensityProvider` devient le centre du rendu.

Il doit être utilisé par :

```text
CloudRaymarchRenderer
CloudShadowRenderer
CloudLightingBridge
FallbackDarkeningPass
```

## Règle de densité unique

Une seule densité doit produire :

```text
La forme visible du cloud
La shadow map
Les uniforms shader
Le fallback darkening
```

Si chaque système utilise sa propre approximation, les ombres ne matcheront jamais les clouds visibles.

## Shadows

`CloudShadowRenderer` lit les snapshots live et le density provider.

Il produit :

```text
cloudShadowMap
cloudShadowViewProjection
cloudThicknessTexture optionnelle
shadow metadata
```

Il projette la densité existante selon la direction du soleil.

Il ne simule rien.

## Lighting bridge

`CloudLightingBridge` expose les données aux shaders supportés.

Données prévues :

```text
cloudShadowMap
cloudShadowViewProjection
cloudCoverage
cloudDensityScale
cloudLightAbsorption
cloudBaseHeight
cloudTopHeight
cloudWindOffset
cloudTime
stormDarkening
precipitationDarkening
```

Envoyer des uniforms ne suffit pas pour supporter tous les shaderpacks.

Les shaders doivent lire ces uniforms.

Le mode intégré vise donc surtout les shaders supportés par Project Atmosphere.

## Fallback darkening

`FallbackDarkeningPass` existe pour les cas sans shader intégré.

Il doit utiliser les mêmes snapshots et une densité simplifiée.

Il produit une ambiance cohérente sous les clouds, sans promettre des shadows volumétriques parfaites.

## Classes futures importantes

| Classe | Rôle |
|---|---|
| `CloudRenderFrameContext` | Regroupe camera, matrices, temps monde, partial tick, soleil |
| `CloudRenderProfile` | Définit les qualités Low, Medium, High, Ultra |
| `CloudRenderTargetManager` | Gère les render targets |
| `CloudDensityProvider` | Donne la densité monde des clouds |
| `CloudRaymarchRenderer` | Produit les clouds visibles |
| `CloudShadowRenderer` | Produit la shadow map |
| `CloudLightingBridge` | Expose textures et uniforms |
| `CloudUniformUploader` | Centralise les uniforms GPU |
| `FallbackDarkeningPass` | Darkening local sans shader intégré |
| `CloudRenderDiagnostics` | Stats et debug du renderer |
