# PROMPT POUR CLAUDE CODE — SuperTux Mobile (version gamin)

## CONTEXTE

Tu es sur la machine locale de Sky (Windows ou Linux). Le repo `jeu-pour-les-gamin` est un fork de SuperTux (jeu C++ SDL2 open source GPL v3) adapte pour des **enfants sur mobile Android**.

Le jeu est en mode **boutons tactiles uniquement** (D-pad + Jump). Le gyroscope/accelerometre a ete desactive volontairement parce que c'est trop galere pour les gamins.

### Ce qui a ete modifie vs SuperTux upstream

| Fichier | Quoi |
|---------|------|
| `src/control/mobile_controller.hpp` | Header controles mobiles, champs accelerometre gardes mais desactives |
| `src/control/mobile_controller.cpp` | Controles tactiles : D-pad (bas-gauche) + bouton Jump (bas-droite) + Pause. Accelerometre desactive (`use_tilt()` retourne toujours `false`, `SDL_INIT_SENSOR` retire) |
| `src/supertux/gameconfig.hpp` | +3 champs config tilt (gardes pour compat, `tilt_enabled` = false par defaut) |
| `src/supertux/gameconfig.cpp` | Defaults + read/write des configs tilt, `tilt_enabled(false)` force |
| `src/supertux/screen_manager.cpp` | Fix ecran tactile : quand un **menu est actif** (titre, menu principal, options...), les touches tactiles passent en mode souris pour que la gamine puisse **taper sur les boutons du menu**. Pendant le gameplay, les touches sont capturees par le mobile_controller pour le D-pad et le saut. |
| `src/network/` | Serveur WebSocket (port 9876) + UDP discovery (port 9877) pour mode multijoueur local (telephones = manettes) |
| `controller_app/` | App Flutter manette (pour le mode multi, pas obligatoire) |

### Controles en jeu (tactile)

- **D-pad** (bas-gauche) : gauche/droite/haut/bas
- **Bouton Jump** (bas-droite) : appui court = petit saut, appui long = grand saut
- **Bouton Pause** (haut-gauche) : menu pause

### Bug fix important : ecran titre tactile

Le probleme etait que sur l'ecran titre et les menus, les touches tactiles etaient **capturees par le mobile_controller** (il pensait que c'etait des appuis sur le D-pad ou le bouton Jump). Du coup, les evenements souris n'etaient jamais generes et la gamine ne pouvait pas taper sur "Start Game" ou les autres boutons du menu.

**Le fix** (dans `screen_manager.cpp`) : quand `MenuManager::instance().is_active()` ou `has_dialog()` est vrai, on **skip** le mobile_controller et on laisse tous les touchers se convertir en evenements souris. Le menu fonctionne normalement au toucher. Quand le gameplay commence (menu ferme), le mobile_controller reprend la main pour le D-pad et le saut.

## TA MISSION

Compiler ce projet en un fichier .apk Android installable. Le jeu doit tourner 100% offline sur telephone, avec les controles tactiles (D-pad + Jump).

## ETAPES

### 1. Clone et submodules
```bash
git clone https://github.com/sky1241/jeu-pour-les-gamin.git
cd jeu-pour-les-gamin
git submodule update --init --recursive
```

### 2. Installer les prerequis (si pas deja fait)

**Android Studio** (le plus simple pour tout avoir) :
- Telecharger depuis https://developer.android.com/studio
- Pendant l'install, cocher : Android SDK, Android NDK, CMake
- Ou via sdkmanager en ligne de commande :
```bash
sdkmanager "platforms;android-34" "ndk;29.0.14206865" "build-tools;34.0.0" "cmake;3.22.1"
```

**vcpkg** (gestionnaire de dependances C++) :
```bash
git clone https://github.com/microsoft/vcpkg.git
cd vcpkg
./bootstrap-vcpkg.sh  # Linux/Mac
# ou bootstrap-vcpkg.bat sur Windows
```

Installer les dependances SuperTux pour Android ARM64 :
```bash
# Depuis le dossier vcpkg
./vcpkg install --triplet=arm64-android sdl2 sdl2-image libogg libvorbis openal-soft freetype curl glew harfbuzz fribidi glm zlib libraqm
```

Note: si des erreurs avec `-lc++`, lancer :
```bash
find $VCPKG_ROOT/installed/*-android -name '*.pc' -exec sed -i 's/-lc[+][+]//g' {} \;
```

### 3. Configurer le build Android

```bash
cd jeu-pour-les-gamin/mk/android
```

Creer/editer `local.properties` :
```properties
sdk.dir=/chemin/vers/Android/Sdk
vcpkg_root=/chemin/vers/vcpkg
ndk_home=/chemin/vers/Android/Sdk/ndk/29.0.14206865
```

### 4. Bootstrap le projet Android
```bash
cd jeu-pour-les-gamin
./tools/bootstrap-android-project.sh
```

### 5. Build l'APK
```bash
cd mk/android
./gradlew assembleDebug -Pcpuarch=arm64-v8a
```

L'APK sera dans : `mk/android/app/build/outputs/apk/debug/app-debug.apk`

### 6. Installer sur le telephone

Option A — USB :
```bash
# Activer "Mode developpeur" et "Debogage USB" sur le telephone
adb install mk/android/app/build/outputs/apk/debug/app-debug.apk
# ou simplement :
./deploy-debug-adb.sh
```

Option B — Transfert direct :
- Copier le .apk sur le telephone (email, drive, cable USB)
- Ouvrir le .apk sur le telephone
- Autoriser "Sources inconnues" si demande
- Installer

## ARCHITECTURE RAPIDE

```
src/control/mobile_controller.cpp   <- COEUR DES CONTROLES TACTILES
  |- activate_widget_at_pos()       <- D-pad : detecte gauche/droite/haut/bas
  |- update()                       <- Frame loop: lit les doigts + applique D-pad
  |- process_finger_down_event()    <- Detecte appui sur Jump / Pause / D-pad
  |- process_finger_up_event()      <- Release du saut quand doigt lache
  |- draw()                         <- Dessine D-pad + bouton Jump + Pause

src/supertux/screen_manager.cpp     <- DISPATCH DES EVENTS TACTILES
  |- process_events()               <- Converti touch -> souris quand menu actif,
                                       sinon laisse mobile_controller gerer

src/object/player.cpp               <- PAS TOUCHE — le saut variable fonctionne deja
  |- handle_vertical_input()        <- hold(JUMP) court = petit saut, long = grand saut
```

## PARAMETRES DE TUNING

Dans `gameconfig.cpp` :
- `m_mobile_controls_scale` (defaut 1.3) — taille des boutons tactiles
- `tilt_enabled` (defaut `false`) — NE PAS ACTIVER, le gyroscope est desactive expres

## PROBLEMES COURANTS

- **"libc++.so cannot find libdl.so.2"** -> voir le fix sed dans la section vcpkg ci-dessus
- **OpenSLES not found** -> reinstaller le port openal-soft vcpkg avec ANDROID_NDK_HOME set
- **Submodules vides** -> refaire `git submodule update --init --recursive`
- **Gradle echoue** -> verifier que local.properties pointe vers les bons chemins
- **La gamine arrive pas a lancer le jeu depuis le menu** -> verifier que le fix dans screen_manager.cpp est bien la (les touches tactiles doivent passer en mode souris quand un menu est actif)

## BUT FINAL

Un .apk installable. L'utilisateur ouvre l'app, le jeu se lance en plein ecran paysage. La gamine tape sur "Start Game" dans le menu (ca marche au toucher grace au fix). En jeu : D-pad en bas a gauche pour bouger, bouton Jump en bas a droite pour sauter, bouton Pause en haut a gauche. Zero gyroscope. Zero internet requis pour jouer.
