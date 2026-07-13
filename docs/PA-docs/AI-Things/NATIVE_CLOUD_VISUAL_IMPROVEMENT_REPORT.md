# Rapport d'amélioration visuelle des nuages natifs

## Statut

L'implémentation et le smoke test OpenGL du backend volumétrique natif sont
terminés. L'acceptation visuelle finale reste ouverte : conformément à la demande
de l'utilisateur, aucune nouvelle campagne de captures n'a été réalisée par
l'agent. Les captures avant/après et les mesures comparatives dans un environnement
GPU au repos doivent encore être fournies manuellement.

Le smoke test a confirmé un seul propriétaire visuel actif : Simple Clouds était
absent, `AtmosphereCloudServices` a choisi le service natif et
`ClientCloudRenderOwnership` a résolu `PA_VOLUMETRIC`. Aucun code d'intégration
Simple Clouds n'a été modifié dans cette passe.

Le smoke test OpenGL précède le dernier alignement des identifiants convectifs. Ce
dernier changement Java est couvert par le self-check multi-morphologie et le build,
mais la version exacte finale doit encore être relancée pendant la campagne de
captures manuelle.

## 1. État visible avant modification

Les références diagnostiques existantes sous
`run/screenshots/pa-native-visual-audit/before` ne constituent pas une matrice
finale, mais elles ont permis les constats suivants :

- `cumulus_humilis` : petits lobes brillants et déconnectés;
- `stratus_nebulosus` : grande nappe surexposée avec bord radial pointu;
- `stratocumulus` : plafond presque blanc et continu, sans cellules lisibles;
- cumulonimbus : masse compacte et fragmentée, sans tour continue, base sombre ni
  enclume clairement lisible;
- `nimbostratus` : référence non recevable, car deux champs étaient simultanément
  visibles;
- `cirrus_fibratus` : référence non recevable, car l'identifiant inconnu avait été
  résolu vers `vapor_cluster`;
- supercellule : aucun type natif spawnable n'existait.

Le contexte GPU était occupé par une autre application lourde. Les FPS et la VRAM
de cette session ne sont donc pas un avant fiable.

## 2. Défauts confirmés et causes

| Défaut | Cause confirmée | Preuve principale |
|---|---|---|
| familles trop semblables | un seul disque pseudo-aléatoire pour toutes les morphologies | `CloudletLayout.generate` |
| ellipsoïdes et bases rondes | effondrement symétrique base/sommet dans le splat | `cloud_weather_splat.fsh` |
| stratus trop bosselé | mêmes lobes et même profil vertical que les nuages convectifs | `cloud_weather_splat.fsh`, `cloud_atmosphere_volume.fsh` |
| cumulonimbus sans tour/enclume continue | cloudlets universels et branche de densité partagée | `CloudletLayout`, `familyMacroShape` avant modification |
| supercellule impossible | type natif et profil GPU 7 inaccessibles | `CloudTypeRegistry`, `VolumetricRenderCell.profileFor` |
| cirrus de test incorrect | absence d'alias `cirrus_fibratus` | `CommandCloudService.TYPE_ALIASES` |
| grandes masses détruites par le bruit | le bruit décidait la silhouette jusque dans le cœur | `cloudDensity` |
| orages trop clairs | planchers fixes de radiance et d'alpha | `sampleLighting`, sortie finale du raymarch |
| aucune pluie volumétrique sous la base | la précipitation ne faisait qu'augmenter la densité interne | `cloudDensity` |
| texture interne glissante | advection opposée au déplacement plus animation indépendante | `detailNoiseDomain`, `cloudDensity` |
| disparition au LOD lointain | zéro cloudlet ne produisait aucune enveloppe de remplacement | `requestedCloudletCount`, construction des cellules de rendu |

## 3. Fichiers et fonctions modifiés

### Morphologie CPU

- `CloudletLayout.generate` et les générateurs par famille : rôles structurels,
  silhouettes déterministes, orientation au vent et hauteur relative au champ;
- `VolumetricRenderCell.fromFieldSnapshot` : enveloppe macro par famille;
- `VolumetricRenderCell.fromFieldCloudlet` : rayon, aspect, hauteur, densité et
  douceur selon famille/rôle;
- `VolumetricCloudRenderHook.allocateFieldCloudlets` : réservation des cellules
  macro dans le plafond GPU et budget de détail strict;
- `VolumetricCloudRenderHook.addFieldMacroCell` : continuité de masse aux LOD
  `FAR_PROCEDURAL` et `HAZE`;
- `CloudFieldValidation.sameCloudlet` : validation des nouvelles propriétés.
- `CloudFieldSandbox.main` : exécution bloquante du self-check avant génération du
  laboratoire autonome.

### Données et types

- `CloudTypeRegistry` : type natif `supercell`, classification orageuse et
  précipitante;
- `CloudMorphologyFamily.defaultFor` : `supercell -> SPIRAL_STORM`;
- `CloudShapeProfile.defaultFor` : dimensions et morphologie de la supercellule;
- `CommandCloudService.TYPE_ALIASES` : `cirrus_fibratus -> cirrus`.

### Weather map et raymarch

- `CloudWeatherMapRenderer.render` : transport du profil catégoriel vers le splat;
- `cloud_weather_splat.fsh` : empreinte et effondrement vertical propres au profil;
- `cloud_weather_morphology.fsh` : profil dominant non moyenné et traits continus;
- `cloud_atmosphere_volume.fsh` : `familyMacroShape`, `cloudDensity`,
  `rainShaftDensityAt`, `sampleLighting` et advection au vent.

## 4. Changements par famille

| Famille | Structure maintenant produite |
|---|---|
| Cumulus | cœur central, lobes fusionnés, base peu effondrée, sommet bombé et érosion surtout en bordure |
| Cumulonimbus | base large, cœur vertical prioritaire, cloudlets d'enclume alignés au vent, masse basse renforcée et absorption locale d'orage |
| Supercellule | nouveau type natif, base asymétrique, tour spiralée, cœur dense et enclume plus étendue au vent |
| Stratus | grandes tuiles horizontales, faible variance de hauteur, profil vertical mince et bruit peu érosif |
| Stratocumulus | nappe cellulaire, bosses larges et basses, couverture moins uniforme que le stratus |
| Nimbostratus | tuiles plus épaisses et denses, couverture continue, base assombrie par précipitation et shafts sous la couche |
| Cirrus | filaments minces, très anisotropes, alignés au vent, profil vertical et porteur directionnel distincts des lobes cumulus |

Ces familles ne sont plus de simples multiplicateurs appliqués à une fonction de
forme commune : `familyMacroShape` contient des enveloppes verticales et des
porteurs horizontaux distincts.

## 5. Comparaison avant/après disponible

| Critère | Avant confirmé | Après vérifié techniquement | Validation visuelle |
|---|---|---|---|
| silhouettes par famille | disque/lobes universels | sept layouts CPU et sept fonctions macro distinctes | à capturer |
| continuité au LOD lointain | disparition possible | une enveloppe macro conservée par champ visible | à capturer en mouvement |
| supercellule native | absente | spawn `supercell` résolu et rendu | à capturer |
| cirrus fibratus | fallback erroné | alias vers le type natif `cirrus` | à capturer |
| pluie volumétrique | absente | densité sous la base liée aux données de précipitation | à capturer pluie/virga |
| orage sombre | planchers lumineux fixes | planchers supprimés, absorption locale et compression des pics | à capturer jour/nuit |
| mouvement interne | advection opposée et animation indépendante | bruit advecté avec le vent, sans translation temporelle secondaire | à comparer en vidéo |

Le smoke test démontre que le chemin est exécuté et composité; il ne démontre pas à
lui seul que les silhouettes sont esthétiquement acceptables.

Le self-check autonome couvre désormais les huit cas représentatifs (dont les deux
variantes `SHEET`) et les ids de cloudlets 0 à 7. Il vérifie déterminisme, valeurs
finies/bornées, position verticale et ordre structurel des tempêtes.

## 6. Résultats de performance disponibles

Configuration de smoke test : preset `ULTRA`, fenêtre principale 854 x 480,
Simple Clouds absent.

- raymarch GPU asynchrone observé : environ 1,10 à 1,42 ms;
- première supercellule rendue : 26 cellules météo, 25 cloudlets de détail acceptés;
- scène transitoire à deux champs : 66 cellules météo, plafond de 64 cloudlets de
  détail appliqué;
- profondeur : copie détachée `vanilla_main` valide;
- composition : `composited=true`;
- erreur shader/uniform/rendu : aucune;
- bake de bruit initial : 768 ms sur le worker asynchrone.

Limites des mesures :

- le timer couvre uniquement le fullscreen draw du raymarch;
- aucun timer séparé weather map, composite ou shadow n'existe actuellement;
- les FPS, la VRAM et la moyenne réelle de pas exécutés ne sont pas instrumentés;
- une autre application utilisait fortement le GPU;
- il n'existe donc aucune comparaison avant/après recevable pour les performances.

Mesures de maîtrise du coût conservées : cache de weather map, prétest de couverture,
résolution réduite selon preset, boucle bornée à 128 itérations, light march borné à
8 taps, détail fin uniquement près de la caméra, rain shafts exclus du light march,
et plafond total de 96 cellules météo incluant désormais les enveloppes macro.

## 7. Problèmes encore présents ou non confirmés

- acceptation visuelle de chaque famille, des rain shafts, du lever/coucher, de la
  nuit et des vues sous/dans/au-dessus : test manuel requis;
- transition de morphologie : les identifiants convectifs partagent maintenant leur
  domaine angulaire et l'ordre cœur/base/enclume, tandis que le cœur cumulus converge
  avec `verticalDevelopment`; le basculement catégoriel final peut encore produire
  un pop et doit être évalué en vidéo;
- le bruit utilise le vent atmosphérique réel de la zone caméra, mais pas un vecteur
  distinct encodé par texel pour plusieurs champs soumis à des vents régionaux
  différents;
- les frontières entre profils restent catégorielles; leur raccord visuel doit être
  contrôlé lorsque deux familles se chevauchent;
- la pluie volumétrique doit être vérifiée devant le terrain, les liquides et dans
  les cas de virga;
- aucune mesure fiable de FPS, VRAM, weather map, ombres ou nombre moyen de pas;
- Fabulous, shader packs et Distant Horizons ne font pas partie de cette passe
  native visuelle et restent des tests manuels de non-régression.

## 8. Paramètres recommandés par qualité

| Preset | Raymarch | Résolution | Lumière | Détail | Weather map | Cloudlets | Ombres | Usage recommandé |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| LOW | 24 | 25 % | 3 | 0 | 256 | 16 | /8 frames | GPU intégré, priorité FPS |
| LOW_24 | 32 | 37,5 % | 4 | 1 | 384 | 24 | /6 frames | bas de gamme avec temporal |
| MEDIUM | 40 | 50 % | 5 | 1 | 512 | 32 | /4 frames | valeur par défaut recommandée |
| HIGH | 64 | 50 % | 6 | 1 | 512 | 48 | /2 frames | meilleur compromis visuel |
| ULTRA | 96 | 75 % | 6 | 2 | 512 | 64 | chaque frame | captures ou GPU haut de gamme |

Commande : `/pa cloud render quality <low|low_24|medium|high|ultra>`.

## Protocole de validation manuelle restant

Pour chaque scène, vider les champs, geler le déplacement si une comparaison fixe
est souhaitée, faire apparaître exactement un type puis confirmer le nombre de
champs avec le diagnostic système. Types minimaux :

- `cumulus_humilis`;
- `stratus_nebulosus`;
- `stratocumulus`;
- `nimbostratus`;
- `cirrus` ou `cirrus_fibratus`;
- `cumulonimbus_capillatus`;
- `supercell`.

Capturer sous, dans et au-dessus de la couche, puis répéter à midi, au lever/coucher
et de nuit. Pour le mouvement, enregistrer au moins 20 secondes avec déplacement
actif afin d'évaluer le glissement interne, la reprojection et le popping de LOD.

### Matrice conseillée

| Scène | Préparation principale |
|---|---|
| dégagé | `/pa cloud clear`, `/weather clear` |
| petits cumulus | `/pa cloud clear`, `/pa cloud spawn cumulus_humilis` |
| stratus | `/pa cloud clear`, `/pa cloud spawn stratus_nebulosus` |
| stratocumulus | `/pa cloud clear`, `/pa cloud spawn stratocumulus` |
| nimbostratus | `/pa cloud clear`, `/pa cloud spawn nimbostratus` |
| cumulonimbus | `/pa cloud clear`, `/pa cloud spawn cumulonimbus_capillatus` |
| supercellule | `/pa cloud clear`, `/pa cloud spawn supercell` |
| pluie forte | `/pa cloud clear`, `/pa cloud rain 2` |
| orage fort | `/pa cloud clear`, `/pa cloud thunder 2` |

Après chaque spawn, attendre la synchronisation puis utiliser
`/pa system cloudStatus`. Ne pas accepter une référence si plusieurs champs sont
visibles alors que la scène doit isoler un profil.

Pour les angles, passer en spectateur et utiliser les valeurs `center`, `base` et
`top` du diagnostic : sous la base (`base - 40`), au milieu de la couche, puis au
dessus (`top + 40`). Geler le cycle avec `/gamerule doDaylightCycle false` et tester
au minimum les temps 0, 6000, 12000 et 18000. Conserver résolution, FOV, preset,
distance de rendu et position exactement identiques entre deux comparaisons.
