# Architecture des types de nuages évolutifs

## Objectif

Project Atmosphere ne doit pas revenir vers un système de profils fixes comme `cumulus`, `stratus` ou `storm` appliqués une fois pour toute. Un nuage PA est un objet météo vivant. Il naît avec un type, garde un état backend persistant, transporte seulement les données nécessaires au rendu, puis peut évoluer si la météo le permet.

Cette étape ne cherche pas à rendre le shader parfait visuellement. Le but immédiat est que le type du nuage contrôle sa forme et ses paramètres visuels, sans casser le pipeline backend vers frontend.

## Pourquoi les profils fixes ne suffisent pas

Un profil fixe décrit une apparence, mais pas une histoire météo. Il ne peut pas exprimer qu'un petit cumulus peut grossir, devenir plus vertical, puis former un cumulonimbus si humidité, instabilité et potentiel orageux augmentent. Un système fixe force aussi le renderer à deviner la météo à partir de quelques paramètres comme densité et couverture.

Le nouveau modèle sépare ces responsabilités:

- Le backend stocke l'état vivant et l'identifiant du type.
- Le registre de types décrit ce que ce type signifie.
- Le transport réseau envoie des paramètres déjà résolus et sûrs pour le client.
- Le frontend construit des snapshots.
- Le renderer transforme ces snapshots en uniforms shader.

## Type de nuage

Un type de nuage est une définition nommée et stable, par exemple `cumulus_humilis` ou `cumulonimbus_calvus`. Le backend stocke seulement son identifiant sous forme de chaîne.

Un type contient:

- un `id` stable;
- un `displayName` lisible;
- une `CloudFamily`;
- un `CloudVisualProfile`;
- des `CloudSpawnConditions`;
- des `CloudEvolutionRules`.

Le type n'est pas un état mutable. Deux régions de nuage peuvent partager le même type tout en ayant des âges, positions, densités et cycles de vie différents.

## Famille de nuage

Une famille regroupe des types proches: `CUMULUS`, `CUMULONIMBUS`, `STRATUS`, `STRATOCUMULUS`, `ALTOCUMULUS`, `CIRRUS`, `NIMBOSTRATUS`.

La famille sert à organiser les définitions et à permettre plus tard des comportements communs. Elle ne remplace pas le type précis. Par exemple, `cumulus_humilis` et `cumulus_congestus` sont tous les deux dans la famille `CUMULUS`, mais ils n'ont pas la même forme ni les mêmes évolutions.

## Profil visuel

`CloudVisualProfile` décrit les paramètres que le renderer peut utiliser pour donner une forme différente à chaque type:

- épaisseur verticale;
- érosion des bords;
- douceur de base et de sommet;
- assombrissement de base;
- échelles de bruit;
- multiplicateurs de densité et couverture;
- écrasement vertical;
- force de tour;
- force d'enclume;
- noyau de précipitation.

Le profil visuel ne simule pas la météo. Il traduit un type en paramètres de shader.

## Conditions de naissance

`CloudSpawnConditions` décrit quand un type peut apparaître. Les premières valeurs sont volontairement simples:

- humidité minimale et maximale;
- température minimale et maximale;
- pression minimale et maximale;
- probabilité d'orage minimale;
- instabilité minimale;
- soulèvement minimal.

L'instabilité et le soulèvement ne sont pas encore exposés proprement par tous les systèmes PA. Les champs existent maintenant pour éviter de redessiner l'API quand la météo avancée sera prête.

## Règles d'évolution

`CloudEvolutionRules` contient une liste de `CloudEvolutionTarget`. Chaque cible décrit:

- le type cible;
- l'âge minimal dans le type courant;
- l'humidité minimale;
- l'instabilité minimale;
- la pression maximale;
- la probabilité d'orage minimale;
- une chance par vérification.

La première chaîne implémentée est:

`cumulus_humilis` -> `cumulus_mediocris` -> `cumulus_congestus` -> `cumulonimbus_calvus` -> `cumulonimbus_capillatus`

Ces seuils sont des placeholders de développement. Ils doivent être faciles à ajuster avec les futures valeurs météo.

## Influence des valeurs météo

La météo influence deux moments:

- la naissance du nuage, via `CloudSpawnConditions`;
- l'évolution du nuage, via `CloudEvolutionTarget`.

Pour l'instant, le contrôleur d'évolution utilise des valeurs sûres quand l'instabilité ou le soulèvement ne sont pas encore disponibles. Cette décision garde le système fonctionnel sans inventer une simulation complète trop tôt.

Plus tard, le contrôleur devra lire les vraies valeurs de région météo PA:

- humidité;
- température;
- pression;
- chance d'orage;
- instabilité;
- lift.

## Stockage backend

`CloudRegionState` reste backend uniquement. Il stocke l'état vivant:

- identifiant de région;
- dimension;
- région météo source et région météo courante;
- centre courant et précédent;
- vitesse;
- rayon et bornes verticales;
- densité, couverture et douceur des bords;
- état actif;
- âge, durée de vie, croissance et decay;
- `cloudTypeId`;
- `previousCloudTypeId`;
- `cloudTypeTicks`.

Le backend ne stocke pas de `CloudTypeDefinition` complet dans la sauvegarde. Il stocke seulement l'identifiant. Au chargement, un identifiant manquant ou invalide retombe sur `cumulus_humilis`.

## Données de rendu

`CloudRegionRenderData` est la frontière réseau sûre. Il transporte:

- l'état visuel courant nécessaire au client;
- les ids de type;
- les compteurs d'évolution;
- les paramètres résolus du `CloudVisualProfile`.

`CloudRegionRenderDataFactory` lit `CloudRegionState`, résout le type via `CloudTypeRegistry`, puis copie les valeurs visuelles dans `CloudRegionRenderData`. Le client n'a donc pas besoin de lire le backend et ne reçoit jamais `CloudRegionState`.

## Frontend et rendu

Le client reçoit `SyncCloudRegionsPacket`, qui transporte uniquement une liste de `CloudRegionRenderData`. Le packet écrit ces données dans `ClientCloudRegionDataCache`.

Chaque frame:

- `CloudRenderHook` crée un `CloudRenderFrameContext`;
- `CloudRenderStateUpdater` lit `ClientCloudRegionDataCache`;
- `CloudRenderSnapshotBuilder` convertit chaque render data en `CloudRenderSnapshot`;
- `CloudRenderer` récupère les snapshots live rendables;
- `CloudRaymarchRenderer` et `CloudUniformUploader` envoient les paramètres au shader.

`CloudRenderSnapshot` est frontend uniquement. Il ne doit jamais être créé côté serveur.

## Pourquoi cela fait avancer Project Atmosphere

Cette architecture avance vers une simulation météo vivante. Elle évite de réduire les nuages à un interrupteur visuel. Les types deviennent des objets de design météo: ils peuvent naître, évoluer, et contrôler la forme rendue sans mélanger simulation et renderer.

Le renderer reste responsable des pixels. Le backend reste responsable de la vie du nuage. Le réseau reste une frontière de données sûre.

## Packages

`net.Gabou.projectatmosphere.clouds.api`

API publique stable future. Minimal pour l'instant.

`net.Gabou.projectatmosphere.clouds.simulation`

Entrées et logique de simulation backend:

- `CloudRegionManager`
- `CloudRegionMotionController`
- `CloudRegionLifecycleController`
- `CloudRegionEvolutionController`
- `CloudRegionSpawner`

`net.Gabou.projectatmosphere.clouds.state`

État backend et persistance:

- `CloudRegionState`
- `CloudRegionRegistry`
- `CloudRegionBackend`
- `CloudRegionStorage`
- `CloudRegionSavedData`
- `CloudRegionStateStore`

`CloudRegionRegistry`, `CloudRegionBackend`, `CloudRegionStorage` et `CloudRegionSavedData` restent internes au package.

`net.Gabou.projectatmosphere.clouds.type`

Types et registres:

- `CloudFamily`
- `CloudTypeDefinition`
- `CloudTypeRegistry`
- `CloudVisualProfile`
- `CloudSpawnConditions`
- `CloudEvolutionRules`
- `CloudEvolutionTarget`

`net.Gabou.projectatmosphere.clouds.transport`

Données réseau sûres:

- `CloudRegionRenderData`
- `CloudRegionRenderDataFactory`

`net.Gabou.projectatmosphere.clouds.network`

Packets et synchronisation:

- `SyncCloudRegionsPacket`
- `CloudRegionSyncManager`

`net.Gabou.projectatmosphere.clouds.client`

Cache client et état de rendu:

- `ClientCloudRegionDataCache`
- `CloudRenderSnapshot`
- `CloudRenderSnapshotBuilder`
- `CloudRenderStateCache`
- `CloudRenderStateHolder`
- `CloudRenderStateUpdater`
- `CloudRenderController`
- `CloudRenderFrameContext`

`net.Gabou.projectatmosphere.clouds.client.render`

Rendu live:

- `CloudRenderHook`
- `CloudRenderer`
- `CloudRaymarchRenderer`
- `CloudDensityProvider`
- `CloudLightingBridge`
- `CloudRenderProfile`
- `CloudRenderTargetManager`
- `CloudShadowRenderer`
- `CloudUniformUploader`
- `FallbackDarkeningPass`
- `CloudRenderDiagnostics`

`net.Gabou.projectatmosphere.clouds.client.debug`

Rendu debug uniquement:

- `CloudDebugRenderHook`
- `CloudDebugSnapshotFactory`
- `CloudDebugStateInitializer`
- `CloudWireframeRenderer`

## API publique et classes internes

API publique actuelle:

- `CloudRegionManager`, point d'entrée externe pour créer, supprimer, lister et ticker les régions;
- `CloudRegionState`, modèle backend persistant retourné par certaines commandes de debug;
- `CloudRegionRenderData`, modèle réseau sûr;
- classes client nécessaires au rendu et au cache.

Internes:

- `CloudRegionRegistry`;
- `CloudRegionBackend`;
- `CloudRegionStorage`;
- `CloudRegionSavedData`;
- les contrôleurs de simulation;
- les factories et helpers de rendu quand ils n'ont pas besoin d'être appelés de l'extérieur.

Les systèmes externes ne doivent jamais accéder directement au registre. Ils doivent passer par `CloudRegionManager`.

## Implémenté maintenant

- Registre initial de types;
- première chaîne d'évolution cumulus vers cumulonimbus;
- stockage de `cloudTypeId`, `previousCloudTypeId` et `cloudTypeTicks`;
- résolution du profil visuel dans les données de rendu;
- transport des paramètres visuels au client;
- snapshot frontend enrichi;
- uniforms shader de profil visuel;
- variation de forme simple dans `sampleCloudField`.

## Travail futur

- Lire de vraies valeurs météo de région pour humidité, pression, instabilité et lift;
- gérer la naissance automatique des types via `CloudRegionSpawner`;
- ajouter des transitions visuelles entre types;
- brancher précipitation, ombres cloud et éclairage avancé;
- remplacer les placeholders par des valeurs calibrées;
- déplacer les définitions vers data pack ou config si nécessaire.
