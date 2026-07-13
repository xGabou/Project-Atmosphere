# Rapport final de remédiation du système de nuages

Date de validation : 2026-07-11  
Cible : Minecraft 1.20.1, Forge 47.4.20, Simple Clouds 0.7.3

Ce rapport décrit l'état du code après la remédiation. « Corrigé » signifie que
la cause racine a été modifiée et que le chemin a passé la compilation et les
contrôles applicables. Il ne signifie pas qu'un résultat visuel a été observé
dans toutes les combinaisons de mods. Ces cas sont explicitement marqués
« validation en monde requise ».

## A. Corrections terminées

| Problème original | Cause racine | Fichiers principaux | Solution appliquée | Validation | Limites restantes |
| --- | --- | --- | --- | --- | --- |
| Simple Clouds pouvait perdre ses deux passes sans remplaçant PA | Les annulations opaque/transparente dépendaient de drapeaux séparés et non de l'état réel du renderer | `ClientCloudRenderOwnership`, mixins de pipeline Simple Clouds, `VolumetricCloudRenderHook` | Contrat central d'ownership; une passe SC n'est annulée que si PA possède réellement cette passe dans la dimension courante | Client natif et client SC démarrés; mixins SC appliqués; renderer SC initialisé | Changement dynamique en monde à retester visuellement |
| Dépendances Simple Clouds/Serene/client chargées depuis les chemins communs | Références directes et initialisation commune trop précoce | `AtmosphereCloudServices`, `AtmosphereCloudService`, `ClientBootstrap`, `PAMixinPlugin`, `SeasonBootstrap`, intégrations `compat/simpleclouds` | Services optionnels chargés par réflexion après détection; bootstrap client isolé; mixins filtrés par mod et par distribution | Serveur sans SC/Serene et serveur SC atteignent `Done`; aucun linkage error | Les classes d'implémentation SC conservent volontairement les types SC, mais ne sont chargées que derrière la frontière optionnelle |
| Le scheduler différé et la sirène pouvaient encore résoudre `TornadoManager` sans SC | Des appels tardifs communs contournaient le service de backend | `TornadoSpawnScheduler`, `StormSirenBlock`, services natif et SC | Comptage et proximité des tornades ajoutés au contrat de service; funnels natifs comptés depuis les cellules; sirène compatible avec les deux backends | Compilation et deux serveurs propres, avec génération météo complète | Déclenchement sonore réel à vérifier en monde |
| Le renderer restaurait le framebuffer principal au lieu de l'état entrant | État OpenGL partiellement capturé et cible vanilla supposée | `CloudRenderStateGuard`, hooks volumétriques et field fallback | Capture/restauration exacte des FBO read/draw, viewport, depth, masks, blend, cull, scissor, programme, texture active et états indexés | Compilation, clients natif/SC et chargement réel des shaders | Matrice Fast/Fancy/Fabulous et pipelines externes à observer en monde |
| La profondeur provenait toujours du main target vanilla | Pas d'abstraction de profondeur ni copie de la cible active | `SceneDepthResolver`, `SceneDepthFrame`, `SceneDepthProvider` | Résolution centrale; priorité aux fournisseurs enregistrés; copie détachée de la profondeur du FBO Forge actif; fallback main; diagnostics source/FBO/taille/validité | Client réel OpenGL; aucune boucle de feedback signalée; shaders chargés | Pas encore d'adaptateur projection-aware spécifique DH/Iris/Oculus |
| Composite basse résolution avec halos et débordement | Échantillonnage non guidé par la profondeur scène haute résolution | `cloud_field_composite.fsh`, `CloudFieldCompositeRenderer` | Sélection de voisinage basse résolution guidée par profondeur nuage et profondeur scène HR, avec rejet de silhouette | Compilation GLSL réelle sur le client | Montagnes, liquides et silhouettes à comparer en monde |
| Feedback de profondeur ouragan/ombres | Une passe pouvait lire la profondeur attachée à sa cible d'écriture | `SimpleCloudsHurricaneRenderer`, `VolumetricCloudShadowRenderer`, cibles scratch | Ping-pong/copie de profondeur détachée avant les passes; ombres appliquées avec une source détachée | Shaders ouragan chargés avec SC; deux pipelines serveur valides | Opaque/translucide/Fabulous et ombre terrain à observer en monde |
| Whiteout et fog ne correspondaient pas aux nuages rendus | Requêtes CPU basées sur les cellules même lorsque les fields étaient visibles | `ClientCloudVisualDensity`, `CameraCloudDensityTracker`, handlers de fog | Publication seulement après composition réussie; évaluation Field quand Field est rendu, Cell uniquement en fallback | Compilation et chemins de publication vérifiés | Traversée réelle d'un volume à tester |
| Vignette vanilla annulée sans remplacement valide | Injection conditionnée par une configuration, non par un rendu affiché | suppression de `MixinGuiVignette`, ownership central | L'annulation inactive a été supprimée; PA ne supprime plus la vignette Simple Clouds/vanilla par erreur | Config mixin vérifiée; clients démarrés | Apparence exacte avec shader packs à vérifier |
| Ressources et historiques survivaient aux transitions | Nettoyage dispersé et incomplet | `VolumetricCloudClientLifecycle`, targets, queries, caches, renderers SC | Nettoyage connexion/déconnexion, monde/dimension, reload, resize, shutdown et changement de backend; fermeture des queries et renderers optionnels | Reload de ressources pendant le lancement client; compilation des hooks | Tests répétés de changement de dimension/backend nécessaires |
| Budget cloudlet dépassable et LOD zéro ignoré | Repli local réinjectant des cloudlets et absence de plafond global | `VolumetricCloudRenderHook`, statistiques de budget | Plafond frame-wide strict, file déterministe pondérée, respect de zéro, priorité distance/couverture/intensité, compteurs demandés/acceptés/rejetés/restants | Compilation; compteurs exposés | Popping et équité à mesurer en mouvement |
| Tornades natives non déclenchables | Éligibilité non reliée au spawn et funnel sans cycle complet | `CloudCellSimulationManager`, `CloudCellClassifier`, `NativeTornadoEffects`, commandes natives | Activation/force d'un cumulonimbus natif, croissance/stabilisation/dissipation de `funnelStrength`, sync client, rendu et effets physiques | Compilation; serveur natif stable; commandes enregistrées | Spawn, dégâts, audio et rendu à valider avec un joueur |
| Règles datapack ignorées | Les deux branches retournaient les règles de base | `CloudTypeDataReloadListener`, `CloudTypeRegistry` | Parsing atomique, modes replace/merge, fallback base et conservation en cas d'erreur | Compilation et inspection du chemin reload | Reload `/reload` et cohérence multijoueur à tester |
| Vent visuel codé en dur | Animation GPU et déplacement macro utilisaient des sources différentes | snapshots Field/Cell, uploaders volumétriques, shaders | Vent réel régional synchronisé, utilisé par translation, bruit, cisaillement, enclume et rotation | Compilation et shader réel | Cohérence perceptuelle à observer pendant les transitions |
| Trois simulations de masse nuageuse divergeaient | Regions, Fields et Cells évoluaient en parallèle | `CloudField*`, `CloudCellSimulationManager`, `ClientCloudVisualDensity`, document d'architecture | Region/cluster = vérité météo; Field = dérivé rendu; Cell = dérivé convectif/funnel; requête client = représentation compositée | Compilation, sync et architecture documentée | Densité serveur encore basée sur le dérivé Cell, pas l'enveloppe cloudlet GPU exacte |
| Morphologie perdue avant le GPU | Snapshots limités aux paramètres génériques | `CloudFieldSnapshot`, `VolumetricRenderCell`, weather map morphologique, shaders volumétriques/fallback | Type, développement vertical, densité, humidité, énergie, orage, base/sommet, enclume, précipitation et cycle transportés; profils stratus/cumulus/CB/cirrus/supercell | Les shaders natif et fallback compilent sur le pilote réel | Direction artistique et calibration à valider visuellement |
| Coûts GPU/réseau répétés | Weather map/shadow recalculées, listes complètes et historique aveugle | `CloudWeatherMapRenderer`, `VolumetricCloudRenderer`, sync Field/Cell, `CloudFieldDeltaPacket` | Cache fingerprint/origine, passes extrêmes conditionnelles, delta + intérêt spatial, protocole stable 10, validation temporelle profondeur/transmittance, noise domain warp | Build et clients/serveurs valides | Aucun benchmark FPS/VRAM ni test multijoueur de charge n'a encore été exécuté |
| Code mort et contrats shader incohérents | Anciennes générations de renderers et uniforms restaient dans l'arbre | anciens renderers/pipelines/shaders supprimés; JSON hurricane/tornado nettoyés | Suppression après recherche de références; contrats JSON/GLSL alignés; couleur tornado consommée; commandes anciennes remplacées supprimées | 13 JSON shader valides; aucun warning uniform PA au client SC | Les warnings SC upstream `FadeStart/FadeEnd`, `ColorModulator` et storm fog restent externes à PA |

### Classes auparavant dangereuses et protections

- `ProjectAtmosphere` ne référence plus directement le bootstrap client; il le charge
  uniquement sur `Dist.CLIENT`.
- `AtmosphereCloudServices` charge le backend SC par nom après `ModList` et retombe
  sur un service désactivé si l'intégration échoue.
- `ForecastOrchestrator`, `StormSirenBlock` et `TornadoSpawnScheduler` passent par
  `AtmosphereCloudService`, sans type Simple Clouds dans leur signature.
- `SimpleCloudsClientHooks`, `OptionalSimpleCloudsCommands`, les handlers sévères et
  les bridges de télémétrie sont neutres; les implémentations typées SC sont chargées
  par réflexion uniquement quand le mod existe.
- `SeasonBootstrap` sélectionne Serene Seasons par réflexion; le fallback neutre est
  utilisé quand le mod est absent.
- `PAMixinPlugin` désactive les mixins client sur serveur et les mixins SC/Serene
  lorsque leurs classes cibles sont absentes.

### Nettoyage confirmé

Les anciens renderers non enregistrés, leurs targets/shaders, les anciens adapters
DH/shader/Voxy, les états visuels parallèles, les mixins vignette/température vides,
les diagnostics abandonnés et les anciennes commandes `weatherdebug`/tornade/
ouragan remplacées ont été supprimés après recherche de leurs appels. Le compute
`cloud_regions` et `cube_mesh` est conservé car les deux sont effectivement remplacés
par des mixins Simple Clouds distincts.

## B. Problèmes non corrigés ou non validables automatiquement

1. **Fusion explicite de profondeurs DH + vanilla/shader pack — partielle.** La cible
   Forge active est copiée correctement et un contrat de provider existe. Une fusion
   de textures provenant de projections ou conventions de profondeur différentes ne
   peut pas être implémentée honnêtement sans valider l'API et les matrices exactes de
   la version DH/Iris/Oculus réellement installée.
2. **Compatibilité shader packs — non validée en monde.** Aucun type Iris/Oculus n'est
   lié directement et l'état entrant est restauré, mais le résultat Fabulous et les
   packs qui remplacent les targets doivent être testé visuellement.
3. **Qualité visuelle — non quantifiée.** Les profils, l'upsampling, l'éclairage,
   l'œil, les bandes, les ombres et la reprojection compilent, mais aucune série de
   captures contrôlées n'a été produite.
4. **Gameplay extrême — non validé avec joueur.** Les tornades natives, ouragans,
   dégâts, audio, sirènes et transitions de caméra exigent une session en monde.
5. **Réseau — non validé à plusieurs clients.** L'intérêt spatial et les deltas sont
   implémentés, mais les pertes/reconnexions, changements de dimension et charge à
   plusieurs joueurs doivent être mesurés.
6. **Performance — améliorée mais non benchmarkée.** Aucun profil RenderDoc/Nsight,
   Spark comparatif, métrique VRAM ou série FPS avant/après n'a été exécuté.
7. **Tests automatisés — absents.** La tâche Gradle `test` passe, mais ne découvre
   actuellement aucune suite JUnit/GameTest.

## C. Tests en jeu à effectuer

### Backend natif

1. Démarrer sans Simple Clouds et sans Serene Seasons; créer un monde propre.
2. Confirmer dans le log `Simple Clouds absent; using native PA cloud service`.
3. Tester Fast, Fancy puis Fabulous; regarder nuages devant/derrière montagnes,
   arbres, vitres et eau depuis dessous, dedans et au-dessus.
4. Exécuter `/pa tornado spawn`, `/pa tornado list`, `/pa tornado remove 256` et
   `/pa tornado clear`; vérifier funnel, croissance, dissipation, forces, dégâts,
   particules, fog/whiteout et sirène.
5. Changer Overworld/Nether/End dix fois, redimensionner, `F3+T`, quitter/rejoindre;
   surveiller VRAM, erreurs GL et rémanence temporelle.
6. Modifier un datapack de type/règle, exécuter `/reload`, vérifier replace, merge,
   fallback et conservation de l'ancienne table sur JSON invalide.

### Simple Clouds 0.7.3

1. Démarrer avec exactement SC 0.7.3 et CrackersLib correspondant.
2. Vérifier qu'une seule base nuageuse est visible et que les passes opaque et
   transparente ne disparaissent jamais pendant les changements de mode.
3. Créer tornade et ouragan; inspecter œil, eyewall, bandes, transparence, profondeur
   terrain/eau, passage caméra dans la tempête et disparition complète à la fin.
4. Alterner backend/mode si l'UI le permet, puis `F3+T`; vérifier ownership, caches,
   histories et ressources.

### Fabulous, shader packs et Distant Horizons

1. Répéter la scène montagne + eau en Fabulous, puis avec Oculus/Iris sans pack,
   puis avec au moins un pack utilisant des targets translucides personnalisées.
2. Activer DH à courte puis longue distance; comparer terrain vanilla/DH aux
   silhouettes et vérifier qu'aucun nuage ne passe devant une surface plus proche.
3. Activer les diagnostics de profondeur et relever source, FBO, texture, taille,
   validité et fallback pour chaque combinaison.
4. Si DH fournit une profondeur dans une projection distincte, capturer matrices et
   convention avant d'écrire l'adaptateur de fusion dédié.

### Performance et réseau

1. Mesurer FPS, frametime GPU, VRAM et temps weather/shadow avec 0, 1, 8 et 32 fields,
   avec/sans tornade/ouragan, histoire temporelle activée/désactivée.
2. Vérifier les compteurs cloudlets demandés/acceptés/rejetés et la stabilité lors du
   franchissement des seuils LOD.
3. Lancer serveur + deux clients éloignés, enregistrer octets/s et paquets/s; déplacer
   les joueurs, changer de dimension, reconnecter et vérifier full snapshot puis deltas.

## D. Risques de régression

- La frontière Simple Clouds est volontairement stricte et la plage déclarée est
  `[0.7.3,0.7.4)`; une nouvelle version doit être revalidée avant élargissement.
- Le protocole réseau 10 est incompatible avec les anciens clients/serveurs PA.
- L'upsampling dépend de conventions de profondeur compatibles; un target externe
  non standard doit utiliser un provider validé.
- Les seuils de cache weather map peuvent produire un mouvement par paliers s'ils
  sont trop agressifs; surveiller lors des vents rapides.
- Le budget pondéré peut changer la distribution visuelle lorsque plusieurs orages
  entrent simultanément dans le champ.
- Les cellules réconciliées conservent les responsabilités tornade; toute suppression
  future de `CloudCell` doit d'abord transférer funnel, physique et analytics.
- Les nettoyages de reload/dimension touchent beaucoup de ressources GPU; surveiller
  double-destruction et handles réutilisés par un pipeline externe.

## E. État final des priorités

| Priorité | Problème | État final | Preuve principale |
| --- | --- | --- | --- |
| P0 | Ownership Simple Clouds | Corrigé | Contrat central + client SC initialisé |
| P0 | Dépendances optionnelles / absence SC et Serene | Corrigé | Serveur natif propre atteint `Done` |
| P0 | Serveur dédié / classes client | Corrigé | Deux serveurs propres, aucun linkage error |
| P1 | Restauration framebuffer/GL | Corrigé dans le code; validation visuelle requise | Guard exact + clients démarrés |
| P1 | Stratégie de profondeur | Partiellement corrigé | FBO actif détaché + provider; fusion DH spécifique ouverte |
| P1 | Upsampling guidé | Corrigé dans le code; validation visuelle requise | Shader compilé sur pilote réel |
| P1 | Feedback profondeur ouragan/ombres | Corrigé dans le code; validation visuelle requise | Scratch depth détachée + shaders SC chargés |
| P1 | Whiteout / fog / vignette | Corrigé dans le code; validation en monde requise | Source visuelle canonique; mixin vignette retiré |
| P1 | Cycle de vie client | Corrigé dans le code; stress test requis | Hooks complets + reload client réussi |
| P2 | Budget cloudlets / LOD | Corrigé dans le code; profilage requis | Plafond strict + diagnostics |
| P1 | Tornades natives | Partiellement validé | Code/sync/commandes compilent; gameplay non testé |
| P1 | Datapacks | Corrigé dans le code; reload en jeu requis | Parser merge/replace/fallback |
| P1 | Ombres et vent | Corrigé dans le code; validation visuelle requise | Pass détachée + vent réel uploadé |
| P2 | Source canonique / morphologie | Corrigé | Field rendu, Cell convectif, contrats GPU étendus |
| P2 | GPU / réseau / temporel / bruit | Partiellement validé | Build/runtime OK; benchmark et multijoueur ouverts |
| P3 | Code mort / uniforms / ressources | Corrigé | Recherche de références, build, JSON et runtime GLSL |

## Résultats de validation enregistrés

- `gradle build --no-daemon` : succès complet en 26 s, puis validation finale
  incrémentale en 14 s; jar reobfusqué généré.
- Compilation finale après la dernière centralisation : succès en 22 s.
- Client Simple Clouds 0.7.3 : menu atteint, shaders PA enregistrés, renderer SC
  `Finished initialization` en 247 ms; aucun warning uniform PA.
- Serveur natif propre : `Done (22.057s)`, service natif sélectionné, forecast généré
  pour 111 régions.
- Serveur SC propre : `Done (24.552s)`, service SC sélectionné, forecast généré pour
  100 régions.
- 13 fichiers JSON shader parsés; 42 classes mixin configurées présentes;
  `minVersion=0.8.5`; aucun processus de validation laissé actif.
- La tâche Gradle `test` passe mais découvre zéro test automatisé.

Les détails itératifs et les premiers échecs GLSL corrigés sont consignés dans
`CLOUD_RENDERING_REMEDIATION_LOG.md`. Le contrat de données canonique est décrit dans
`CLOUD_CANONICAL_ARCHITECTURE.md`.
