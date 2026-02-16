# SuperTux Mobile Port — Analyse Technique Complète
## Repo: `sky1241/jeu-pour-les-gamin`

---

## 1. ARBRE DU PROJET (Structure Critique)

```
supertux/
├── src/                          ← 4.5MB — TOUT LE CODE C++
│   ├── control/                  ← ⭐ ZONE DE TRAVAIL PRINCIPALE
│   │   ├── mobile_controller.cpp ← EXISTE DÉJÀ! Touch buttons actuels
│   │   ├── mobile_controller.hpp ← Header mobile (à modifier)
│   │   ├── controller.hpp        ← Enum Control: LEFT/RIGHT/JUMP/ACTION...
│   │   ├── controller.cpp        ← Base: hold(), pressed(), released()
│   │   ├── input_manager.cpp     ← Event dispatch (SDL events → controllers)
│   │   ├── keyboard_manager.*    ← Clavier (desktop)
│   │   ├── joystick_manager.*    ← Manettes
│   │   └── game_controller_manager.* ← SDL GameController
│   │
│   ├── object/
│   │   └── player.cpp            ← ⭐ LOGIQUE DE SAUT (3217 lignes)
│   │       └── handle_vertical_input() ligne 1483
│   │           ├── JUMP pressed → jump_button_timer.start()
│   │           ├── JUMP hold + can_jump → do_jump(-520 à -620)
│   │           ├── JUMP released → early_jump_apex() = SAUT COURT
│   │           └── JUMP hold longtemps → SAUT HAUT (full arc)
│   │
│   ├── supertux/
│   │   ├── screen_manager.cpp    ← ⭐ BOUCLE PRINCIPALE
│   │   │   ├── ligne 314: m_mobile_controller.update()
│   │   │   ├── ligne 315: m_mobile_controller.apply(controller)
│   │   │   ├── ligne 349: SDL_FINGERDOWN → process_finger_down
│   │   │   ├── ligne 371: SDL_FINGERUP → process_finger_up
│   │   │   └── ligne 394: SDL_FINGERMOTION → process_finger_motion
│   │   ├── screen_manager.hpp    ← Contient MobileController m_mobile_controller
│   │   ├── gameconfig.hpp        ← mobile_controls, touch_haptic_feedback, etc.
│   │   └── game_session.cpp      ← Session de jeu
│   │
│   ├── video/                    ← Rendu OpenGL/SDL
│   ├── badguy/                   ← Ennemis (664KB)
│   ├── collision/                ← Physique collision
│   ├── math/                     ← Vecteurs, Rect
│   └── ...
│
├── mk/android/                   ← ⭐ BUILD SYSTEM ANDROID (existe!)
│   ├── app/build.gradle          ← Config Gradle
│   ├── app/src/                  ← Java/Kotlin Android
│   ├── README.md                 ← "Good luck." (littéral)
│   └── deploy-debug-adb.sh      ← Deploy USB
│
├── data/                         ← 313MB — Assets du jeu
│   ├── images/engine/mobile/     ← ⭐ TEXTURES BOUTONS MOBILES
│   │   ├── direction.png         ← D-pad
│   │   ├── button.png            ← Bouton normal
│   │   ├── button_press.png      ← Bouton pressé
│   │   ├── jump.png              ← Icône saut
│   │   ├── action.png            ← Icône action
│   │   └── pause.png             ← Icône pause
│   ├── images/                   ← 117MB sprites/tiles
│   ├── music/                    ← 143MB musiques
│   ├── levels/                   ← 20MB niveaux
│   └── sounds/                   ← 6.9MB sons
│
├── tools/
│   └── bootstrap-android-project.sh ← Script init Android
│
├── CMakeLists.txt                ← Build principal (18KB)
├── vcpkg.json                    ← Dépendances
└── LICENSE.txt                   ← GPL v3+
```

---

## 2. COMMENT LE SAUT FONCTIONNE (Mécanisme Actuel)

C'est LA clé de ton projet. Dans `player.cpp` ligne 1483, `handle_vertical_input()`:

```
JOUEUR APPUIE SUR JUMP
    │
    ├─→ pressed(JUMP) → démarre jump_button_timer (0.25s grace)
    │
    ├─→ hold(JUMP) + can_jump → do_jump(yspeed)
    │   ├── Normal:        yspeed = -520 (marche) / -580 (course)
    │   ├── Air bonus:     yspeed = -580 (marche) / -620 (course)
    │   └── Accroupi:      yspeed = -300
    │
    ├─→ JOUEUR LÂCHE JUMP (pendant montée)
    │   └── early_jump_apex() → gravity × 3.0 = SAUT COUPÉ COURT
    │
    └─→ JOUEUR GARDE JUMP → saut complet (arc plein)
```

**En clair:** La hauteur du saut = DURÉE de pression sur JUMP.
- Appui bref → petit saut (gravity boost coupe l'arc)
- Appui long → grand saut (arc complet)

---

## 3. PLAN DE MODIFICATION — Tilt + 2 Boutons

### 3A. Remplacer D-pad par Accéléromètre

**Fichier:** `src/control/mobile_controller.cpp` + `.hpp`

Le D-pad actuel (`m_rect_directions`) détecte les zones tactiles LEFT/RIGHT/UP/DOWN.
On le remplace par la lecture du capteur accéléromètre SDL2.

**API SDL2 pour accéléromètre:**
```cpp
// SDL2 Sensor API (disponible depuis SDL 2.0.9)
SDL_NumSensors()                    // Nombre de capteurs
SDL_SensorGetDeviceType(index)      // Type: SDL_SENSOR_ACCEL
SDL_SensorOpen(index)               // Ouvrir le capteur
SDL_SensorGetData(sensor, data, 3)  // Lire X, Y, Z en m/s²
```

**Mapping tilt → mouvement:**
```
Téléphone penché à DROITE → X positif → Control::RIGHT
Téléphone penché à GAUCHE → X négatif → Control::LEFT
Dead zone: |X| < 1.5 m/s² → rien
Zone DOWN: Y > 8.0 m/s² → Control::DOWN (accroupi/butt jump)
```

### 3B. Bouton de saut unique

UN seul bouton JUMP, la hauteur dépend de la durée d'appui:

| Action | Comportement | Mécanisme |
|--------|-------------|-----------|
| **Appui court** | Petit saut (hop) | `early_jump_apex()` → gravité ×3 coupe l'arc |
| **Appui long** | Grand saut (arc complet) | JUMP maintenu → arc complet naturel |

**Comment ça marche côté code:**
- Doigt touche le bouton → JUMP=true, on track le fingerId
- Doigt maintenu → JUMP reste true chaque frame
- Doigt relâché → JUMP=false → `player.cpp` détecte le release et coupe le saut
- Le code de `player.cpp` n'a PAS besoin d'être modifié — il réagit déjà à la durée de hold(JUMP)

### 3C. Layout écran proposé

```
┌─────────────────────────────────────────────────┐
│  [PAUSE]                          [SCORE/COINS] │
│                                                  │
│                                                  │
│              ZONE DE JEU                         │
│           (tilt = mouvement)                     │
│                                                  │
│                                                  │
│                                    ┌──────────┐ │
│                                    │          │ │
│                                    │   SAUT   │ │
│                                    └──────────┘ │
│  [ACTION]                                        │
└─────────────────────────────────────────────────┘
      ↕ tilt gauche/droite = déplacement
```

---

## 4. FICHIERS MODIFIÉS — Liste Exacte

```
MODIFIER:
  src/control/mobile_controller.hpp   ← Ajouter SDL_Sensor*, m_rect_small_jump, timer
  src/control/mobile_controller.cpp   ← Accéléromètre + 2 boutons saut
  src/supertux/screen_manager.cpp     ← Ajouter SDL_SENSORUPDATE dans event loop
  src/supertux/gameconfig.hpp         ← Ajouter tilt_sensitivity, tilt_deadzone
  src/supertux/gameconfig.cpp         ← Valeurs par défaut config tilt

AJOUTER:
  data/images/engine/mobile/small_jump.png  ← Icône petit saut
  data/images/engine/mobile/big_jump.png    ← Icône grand saut

NE PAS TOUCHER:
  src/object/player.cpp               ← Le système de saut marche DÉJÀ
  src/control/controller.hpp          ← L'enum Control est suffisant
  mk/android/                         ← Build system Android OK tel quel
```

---

## 5. CODE — mobile_controller.hpp modifié

```cpp
#pragma once

#include <SDL.h>
#include <map>
#include <bitset>

#include "config.h"
#include "controller.hpp"
#include "math/rectf.hpp"
#include "math/vector.hpp"
#include "video/surface_ptr.hpp"

class Controller;
class DrawingContext;

class MobileController final
{
public:
  MobileController();
  ~MobileController();

  void draw(DrawingContext& context);
  void apply(Controller& controller) const;
  void update();

  bool process_finger_down_event(const SDL_TouchFingerEvent& event);
  bool process_finger_up_event(const SDL_TouchFingerEvent& event);
  bool process_finger_motion_event(const SDL_TouchFingerEvent& event);

  void buzz();

private:
  void activate_widget_at_pos(float x, float y);
  void update_accelerometer();

private:
  std::bitset<(size_t)Control::CONTROLCOUNT> m_input, m_input_last;
  std::map<SDL_FingerID, Vector> m_fingers;

  // === TILT CONTROL (remplace D-pad) ===
  SDL_Sensor* m_accelerometer;
  float m_tilt_x;           // Valeur brute axe X
  float m_tilt_deadzone;    // Seuil minimum (défaut 1.5 m/s²)
  float m_tilt_sensitivity; // Multiplicateur

  // === DEUX BOUTONS DE SAUT ===
  Rectf m_rect_small_jump;  // Zone petit saut
  Rectf m_rect_big_jump;    // Zone grand saut
  Rectf m_draw_small_jump;
  Rectf m_draw_big_jump;

  // Timer pour auto-release du petit saut
  bool m_small_jump_active;
  Uint32 m_small_jump_start_time;
  static const Uint32 SMALL_JUMP_DURATION_MS = 80; // 80ms = saut court

  // Tracking quel doigt est sur quel bouton
  SDL_FingerID m_big_jump_finger;
  bool m_big_jump_held;

  // === BOUTONS EXISTANTS (gardés) ===
  Rectf m_rect_action, m_rect_escape, m_rect_item;
  Rectf m_draw_action, m_draw_escape;

  // === TEXTURES ===
  const SurfacePtr m_tex_btn, m_tex_btn_press, m_tex_pause,
                   m_tex_jump, m_tex_action,
                   m_tex_small_jump, m_tex_big_jump;

  int m_screen_width, m_screen_height;
  float m_mobile_controls_scale;

  SDL_TimerID m_haptic_timer;
  std::unique_ptr<SDL_Haptic, decltype(&SDL_HapticClose)> m_haptic;

private:
  MobileController(const MobileController&) = delete;
  MobileController& operator=(const MobileController&) = delete;
};
```

---

## 6. CODE — mobile_controller.cpp modifié (core logic)

```cpp
// === CONSTRUCTEUR — init accéléromètre ===
MobileController::MobileController() :
  m_input(),
  m_input_last(),
  m_fingers(),
  // Tilt
  m_accelerometer(nullptr),
  m_tilt_x(0.0f),
  m_tilt_deadzone(1.5f),
  m_tilt_sensitivity(1.0f),
  // Deux sauts
  m_rect_small_jump(-160.f, -80.f, -96.f, -16.f),
  m_rect_big_jump(-80.f, -80.f, -16.f, -16.f),
  m_draw_small_jump(-160.f, -80.f, -96.f, -16.f),
  m_draw_big_jump(-80.f, -80.f, -16.f, -16.f),
  m_small_jump_active(false),
  m_small_jump_start_time(0),
  m_big_jump_finger(-1),
  m_big_jump_held(false),
  // Boutons gardés
  m_rect_action(-240.f, -80.f, -176.f, -16.f),
  m_rect_escape({96.f, 14.f}, Sizef{48.f, 48.f}),
  m_rect_item({0.f, 0.f}, Sizef{128.f, 128.f}),
  m_draw_action(-240.f, -80.f, -176.f, -16.f),
  m_draw_escape(Vector{192.f, 14.f}, Sizef{48.f, 48.f}),
  // Textures
  m_tex_btn(Surface::from_file("/images/engine/mobile/button.png")),
  m_tex_btn_press(Surface::from_file("/images/engine/mobile/button_press.png")),
  m_tex_pause(Surface::from_file("/images/engine/mobile/pause.png")),
  m_tex_jump(Surface::from_file("/images/engine/mobile/jump.png")),
  m_tex_action(Surface::from_file("/images/engine/mobile/action.png")),
  // TODO: créer ces assets ou réutiliser jump.png avec taille différente
  m_tex_small_jump(Surface::from_file("/images/engine/mobile/jump.png")),
  m_tex_big_jump(Surface::from_file("/images/engine/mobile/jump.png")),
  m_screen_width(),
  m_screen_height(),
  m_mobile_controls_scale(),
  m_haptic(nullptr, SDL_HapticClose),
  m_haptic_timer(0)
{
#ifdef __ANDROID__
  // Init haptic
  SDL_InitSubSystem(SDL_INIT_HAPTIC | SDL_INIT_TIMER | SDL_INIT_SENSOR);
  m_haptic.reset(SDL_HapticOpen(0));
  if (m_haptic)
  {
    if (!SDL_HapticRumbleSupported(m_haptic.get()))
      m_haptic.reset();
    if (m_haptic && SDL_HapticRumbleInit(m_haptic.get()) != 0)
      m_haptic.reset();
  }

  // Init accéléromètre
  int num_sensors = SDL_NumSensors();
  for (int i = 0; i < num_sensors; ++i)
  {
    if (SDL_SensorGetDeviceType(i) == SDL_SENSOR_ACCEL)
    {
      m_accelerometer = SDL_SensorOpen(i);
      break;
    }
  }
#endif
}

MobileController::~MobileController()
{
  if (m_accelerometer)
    SDL_SensorClose(m_accelerometer);
}

// === LECTURE ACCÉLÉROMÈTRE ===
void
MobileController::update_accelerometer()
{
  if (!m_accelerometer)
    return;

  float data[3]; // X, Y, Z en m/s²
  if (SDL_SensorGetData(m_accelerometer, data, 3) == 0)
  {
    m_tilt_x = data[0]; // Axe horizontal

    // Appliquer dead zone
    if (m_tilt_x > m_tilt_deadzone)
      m_input.set(CONTROL_INT(RIGHT), true);
    else if (m_tilt_x < -m_tilt_deadzone)
      m_input.set(CONTROL_INT(LEFT), true);

    // DOWN quand téléphone très incliné vers l'avant
    if (data[1] > 8.0f)
      m_input.set(CONTROL_INT(DOWN), true);
  }
}

// === UPDATE — appelé chaque frame ===
void
MobileController::update()
{
  if (!g_config->mobile_controls)
    return;

  m_input_last = m_input;
  m_input.reset();

  // 1. Lire accéléromètre pour LEFT/RIGHT/DOWN
  update_accelerometer();

  // 2. Lire doigts pour boutons
  for (auto& i : m_fingers)
  {
    activate_widget_at_pos(i.second.x, i.second.y);
  }

  // 3. Gérer auto-release du petit saut
  if (m_small_jump_active)
  {
    Uint32 now = SDL_GetTicks();
    if (now - m_small_jump_start_time < SMALL_JUMP_DURATION_MS)
    {
      // Maintenir JUMP pendant la durée
      m_input.set(CONTROL_INT(JUMP), true);
    }
    else
    {
      // Temps écoulé → release
      m_small_jump_active = false;
    }
  }

  // 4. Grand saut = JUMP maintenu tant que doigt appuyé
  if (m_big_jump_held)
  {
    m_input.set(CONTROL_INT(JUMP), true);
  }

  // Haptic feedback
  for (size_t i = 0; i < static_cast<size_t>(Control::CONTROLCOUNT); ++i)
  {
    if (m_input[i] != m_input_last[i] && m_input[i] == true)
    {
      buzz();
      break;
    }
  }
}

// === ACTIVATION DES WIDGETS ===
void
MobileController::activate_widget_at_pos(float x, float y)
{
  if (!g_config->mobile_controls)
    return;

  Vector pos(x, y);

  // PAS de D-pad ici — le mouvement vient de l'accéléromètre

  // Bouton ACTION
  if (m_rect_action.contains(pos))
    m_input.set(CONTROL_INT(ACTION), true);

  // Bouton ITEM
  if (m_rect_item.contains(pos))
    m_input.set(CONTROL_INT(ITEM), true);

  // Bouton ESCAPE/PAUSE
  if (m_rect_escape.contains(pos))
    m_input.set(CONTROL_INT(ESCAPE), true);

  // NOTE: small_jump et big_jump sont gérés dans process_finger_down/up
}

// === FINGER DOWN — Détection bouton saut ===
bool
MobileController::process_finger_down_event(const SDL_TouchFingerEvent& event)
{
  Vector pos(event.x * float(m_screen_width), event.y * float(m_screen_height));
  m_fingers[event.fingerId] = pos;

  // Petit saut: déclenche un press temporisé
  if (m_rect_small_jump.contains(pos))
  {
    m_small_jump_active = true;
    m_small_jump_start_time = SDL_GetTicks();
    buzz();
    return true;
  }

  // Grand saut: maintenu tant que doigt reste
  if (m_rect_big_jump.contains(pos))
  {
    m_big_jump_held = true;
    m_big_jump_finger = event.fingerId;
    buzz();
    return true;
  }

  return m_rect_action.contains(pos) ||
    m_rect_escape.contains(pos) ||
    m_rect_item.contains(pos);
}

// === FINGER UP — Release grand saut ===
bool
MobileController::process_finger_up_event(const SDL_TouchFingerEvent& event)
{
  Vector pos(event.x * float(m_screen_width), event.y * float(m_screen_height));
  m_fingers.erase(event.fingerId);

  // Si c'est le doigt du grand saut qui lâche → release JUMP
  if (event.fingerId == m_big_jump_finger)
  {
    m_big_jump_held = false;
    m_big_jump_finger = -1;
    return true;
  }

  return m_rect_small_jump.contains(pos) ||
    m_rect_action.contains(pos) ||
    m_rect_escape.contains(pos) ||
    m_rect_item.contains(pos);
}
```

---

## 7. MODIFICATION screen_manager.cpp

Ajouter dans le `switch(event.type)` de `process_events()`:

```cpp
case SDL_SENSORUPDATE:
  // Les données capteur sont lues dans mobile_controller.update_accelerometer()
  // Pas besoin de traitement ici, mais on pourrait filtrer par sensor ID
  break;
```

---

## 8. ÉTAPES POUR BUILDER

```bash
# 1. Fork/clone ton repo
git clone git@github.com:sky1241/jeu-pour-les-gamin.git
cd jeu-pour-les-gamin

# 2. Copier tout SuperTux dedans
# (le contenu du repo supertux va dans ton repo)

# 3. Init les submodules
git submodule update --init --recursive

# 4. Bootstrap Android
./tools/bootstrap-android-project.sh

# 5. Build Android (nécessite Android NDK + vcpkg)
cd mk/android
# Configurer local.properties avec vcpkg_root et ndk_home
./gradlew assembleDebug -Pcpuarch=arm64-v8a

# 6. Deploy sur téléphone
./deploy-debug-adb.sh
```

**Prérequis sur ta machine:**
- Android SDK + NDK (v29 recommandé)
- CMake 3.16+
- vcpkg avec les dépendances cross-compilées pour Android
- Gradle

---

## 9. RÉSUMÉ RAPIDE

| Quoi | Status | Effort |
|------|--------|--------|
| Port Android | ✅ EXISTE DÉJÀ | Build only |
| Touch controls | ✅ MODIFIÉ | Dual jump + tilt |
| Accéléromètre → mouvement | ✅ CODÉ | ~100 lignes C++ |
| 1 bouton de saut (durée = hauteur) | ✅ CODÉ | ~50 lignes C++ |
| D-pad fallback (si pas d'accéléromètre) | ✅ CODÉ | ~60 lignes C++ |
| Config tilt (load/save/sync) | ✅ CODÉ | ~20 lignes |
| use_tilt() guard (tilt_enabled + sensor check) | ✅ CODÉ | ~5 lignes |
| Asset bouton saut | ✅ EXISTE | jump.png (réutilisé) |

Le gros du travail maintenant c'est le **tuning** — trouver la bonne dead zone, la bonne sensibilité du tilt, la bonne durée du petit saut (80ms? 100ms? 120ms?) pour que le gameplay soit fun.

### Bugs corrigés (session 2)
- `g_config->tilt_enabled` est maintenant vérifié via `use_tilt()` avant d'utiliser l'accéléromètre
- `tilt_deadzone` et `tilt_sensitivity` sont synchronisés depuis `g_config` à chaque frame dans `update()`
- D-pad tactile avec highlights s'affiche automatiquement quand le tilt n'est pas disponible (pas de capteur ou tilt désactivé)
