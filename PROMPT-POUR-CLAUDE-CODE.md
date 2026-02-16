# PROMPT POUR CLAUDE CODE — Build SuperTux Mobile APK

## CONTEXTE

Tu es sur la machine locale de Sky (Windows ou Linux). Le repo `jeu-pour-les-gamin` est un fork de SuperTux (jeu C++ SDL2 open source GPL v3) avec des modifications custom dans le mobile_controller pour :

1. **Accéléromètre (tilt)** : pencher le téléphone remplace le D-pad pour bouger Tux gauche/droite
2. **Deux boutons de saut** : petit saut (auto-release 80ms) + grand saut (hold = arc complet)
3. **Bouton action** : pour les power-ups
4. **Bouton pause**

Le code est DÉJÀ modifié et prêt. Les fichiers modifiés vs SuperTux upstream sont :
- `src/control/mobile_controller.hpp` — header avec accéléromètre + dual jump
- `src/control/mobile_controller.cpp` — 480 lignes, logique tilt + timer petit saut
- `src/supertux/gameconfig.hpp` — +3 champs config tilt
- `src/supertux/gameconfig.cpp` — defaults + read/write des configs tilt
- `src/supertux/screen_manager.cpp` — +SDL_SENSORUPDATE event handling

## TA MISSION

Compiler ce projet en un fichier .apk Android installable. Le jeu doit tourner 100% offline sur téléphone, avec les contrôles tilt + dual jump.

## ÉTAPES

### 1. Clone et submodules
```bash
git clone https://github.com/sky1241/jeu-pour-les-gamin.git
cd jeu-pour-les-gamin
git submodule update --init --recursive
```

### 2. Installer les prérequis (si pas déjà fait)

**Android Studio** (le plus simple pour tout avoir) :
- Télécharger depuis https://developer.android.com/studio
- Pendant l'install, cocher : Android SDK, Android NDK, CMake
- Ou via sdkmanager en ligne de commande :
```bash
sdkmanager "platforms;android-34" "ndk;29.0.14206865" "build-tools;34.0.0" "cmake;3.22.1"
```

**vcpkg** (gestionnaire de dépendances C++) :
```bash
git clone https://github.com/microsoft/vcpkg.git
cd vcpkg
./bootstrap-vcpkg.sh  # Linux/Mac
# ou bootstrap-vcpkg.bat sur Windows
```

Installer les dépendances SuperTux pour Android ARM64 :
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

Créer/éditer `local.properties` :
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

### 6. Installer sur le téléphone

Option A — USB :
```bash
# Activer "Mode développeur" et "Débogage USB" sur le téléphone
adb install mk/android/app/build/outputs/apk/debug/app-debug.apk
# ou simplement :
./deploy-debug-adb.sh
```

Option B — Transfert direct :
- Copier le .apk sur le téléphone (email, drive, câble USB)
- Ouvrir le .apk sur le téléphone
- Autoriser "Sources inconnues" si demandé
- Installer

## ARCHITECTURE RAPIDE

```
src/control/mobile_controller.cpp   ← CŒUR DES MODIFS
  ├── update_accelerometer()        ← Lit SDL_Sensor pour tilt X/Y
  ├── update()                      ← Frame loop: tilt + boutons + timer petit saut
  ├── process_finger_down_event()   ← Détecte quel bouton de saut est touché
  ├── process_finger_up_event()     ← Release grand saut quand doigt lâche
  └── draw()                        ← Dessine les boutons (pas de D-pad)

src/object/player.cpp               ← PAS TOUCHÉ — le saut variable fonctionne déjà
  └── handle_vertical_input()       ← hold(JUMP) court = petit saut, long = grand saut
```

## PARAMÈTRES DE TUNING

Dans `gameconfig.cpp`, les valeurs à ajuster si le gameplay est pas bon :
- `tilt_deadzone` (défaut 1.5) — augmenter si Tux bouge trop facilement, baisser si pas assez réactif
- `tilt_sensitivity` (défaut 1.0) — multiplicateur de vitesse du tilt
- `SMALL_JUMP_DURATION_MS` dans mobile_controller.cpp (défaut 80ms) — augmenter pour un hop plus haut, baisser pour plus court

## PROBLÈMES COURANTS

- **"libc++.so cannot find libdl.so.2"** → voir le fix sed dans la section vcpkg ci-dessus
- **OpenSLES not found** → réinstaller le port openal-soft vcpkg avec ANDROID_NDK_HOME set
- **Submodules vides** → refaire `git submodule update --init --recursive`
- **Gradle échoue** → vérifier que local.properties pointe vers les bons chemins

## BUT FINAL

Un .apk installable. L'utilisateur (Sky) ouvre l'app, le jeu se lance en plein écran paysage. Il penche le téléphone pour bouger Tux, 2 boutons en bas à droite pour sauter (petit et grand), 1 bouton action, 1 bouton pause. Zéro internet requis pour jouer.
