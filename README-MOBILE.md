# SuperTux Mobile — Tilt Controls + Dual Jump
## Repo: sky1241/jeu-pour-les-gamin

Fork de [SuperTux](https://github.com/SuperTux/supertux) (GPL v3+) avec des contrôles mobiles custom:
- **Accéléromètre** → pencher le téléphone pour bouger Tux (remplace le D-pad)
- **1 bouton de saut** → appui court = petit saut, appui long = grand saut

---

## Contrôles

| Input | Action |
|-------|--------|
| Pencher droite | Tux avance à droite |
| Pencher gauche | Tux avance à gauche |
| Pencher fort en avant | Tux s'accroupit (butt jump) |
| Bouton saut (appui court) | Petit saut (hop rapide) |
| Bouton saut (maintenir) | Grand saut (arc complet) |
| Bouton action | Utiliser power-up |
| Bouton pause | Menu pause |

## Architecture des modifications

Fichiers modifiés par rapport à SuperTux upstream:

```
src/control/mobile_controller.hpp   ← Accéléromètre + dual jump
src/control/mobile_controller.cpp   ← Logique tilt + timer petit saut
src/supertux/gameconfig.hpp         ← Config tilt (deadzone, sensitivity)
src/supertux/gameconfig.cpp         ← Defaults + read/write config
src/supertux/screen_manager.cpp     ← SDL_SENSORUPDATE event
```

## Build Android

```bash
git submodule update --init --recursive
./tools/bootstrap-android-project.sh
cd mk/android
# Configurer local.properties:
#   vcpkg_root=/path/to/vcpkg
#   ndk_home=/path/to/ndk/29.x
./gradlew assembleDebug -Pcpuarch=arm64-v8a
./deploy-debug-adb.sh
```

## Config

Dans le fichier config du jeu (options → contrôles):
- `tilt_enabled` : activer/désactiver l'accéléromètre
- `tilt_deadzone` : seuil minimum de tilt (défaut: 1.5 m/s²)
- `tilt_sensitivity` : multiplicateur de sensibilité (défaut: 1.0)

## Licence

GPL v3+ — Voir LICENSE.txt
