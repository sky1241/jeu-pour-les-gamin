# SuperTux Mobile (fork pour les gamins)

Fork de [SuperTux](https://github.com/SuperTux/supertux) adapte pour Android avec des controles tactiles pour les enfants.

## Ce qui a change par rapport a SuperTux original

- **Croix directionnelle (D-pad)** en bas a gauche de l'ecran, toujours visible
- **Bouton de saut** en bas a droite, gros et facile a toucher (appui court = petit saut, appui long = grand saut)
- **Controles par inclinaison (tilt)** : pencher le telephone a gauche/droite pour deplacer Tux
- **Bouton pause** en haut a gauche
- **Ecran d'intro de niveau supprime** sur Android (on rentre direct dans le jeu)
- **Bouton action supprime** (inutile pour les enfants)
- Build Android avec **SDL 2.32.10**, **vcpkg arm64**, **NDK 29**

## Comment builder l'APK

Voir [PROMPT-POUR-CLAUDE-CODE.md](PROMPT-POUR-CLAUDE-CODE.md) pour les instructions de build detaillees.

En resume :
1. Installer Android SDK, NDK 29, vcpkg
2. Configurer `mk/android/local.properties` avec les chemins SDK/NDK/vcpkg
3. `cd mk/android && ./gradlew assembleDebug -Pci -Pcpuarch=arm64-v8a`
4. `adb install app/build/outputs/apk/debug/app-debug.apk`

## Fichiers modifies (par rapport a SuperTux upstream)

| Fichier | Description |
|---------|-------------|
| `src/control/mobile_controller.cpp` | D-pad, saut, tilt, layout boutons |
| `src/control/mobile_controller.hpp` | Declaration du controleur mobile |
| `src/supertux/game_session.cpp` | Skip intro sur Android |
| `src/supertux/screen_manager.cpp` | Fix switch case SDL_FINGERMOTION |
| `src/supertux/gameconfig.hpp/cpp` | Config tilt (deadzone, sensitivity) |
| `mk/cmake/SuperTux/Android.cmake` | vcpkg CACHE FORCE pour NDK |
| `CMakeLists.txt` | Alias targets vcpkg static |
| `mk/android/app/build.gradle` | NDK 29, cmake args |

## Licence

GPL v3 (herite de SuperTux) — voir [LICENSE.txt](LICENSE.txt)

---

## SuperTux original

SuperTux is a jump'n'run game with strong inspiration from the
Super Mario Bros. games for the various Nintendo platforms.

Run and jump through multiple worlds, fighting off enemies by jumping
on them, bumping them from below or tossing objects at them, grabbing
power-ups and other stuff on the way.

![Screenshot](https://www.supertux.org/images/0_7_0/github_preview.png)


## Story: Penny gets captured!

Tux and Penny were out having a nice picnic on the ice fields of
Antarctica. Suddenly, a creature jumped from behind an ice bush, there
was a flash, and Tux fell asleep!

When Tux wakes up, he finds that Penny is missing. Where she lay
before now lies a letter:
>Tux, my arch enemy! I have captured your beautiful Penny and have
>taken her to my fortress. The path to my fortress is littered with my
>minions. Give up on the thought of trying to reclaim her, you haven't
>got a chance!
>
>-Nolok

Tux looks and sees Nolok's fortress in the distance. Determined to
save his beloved Penny, he begins his journey.

## Installation

For major platforms, stable releases are built and available for download from
[supertux.org](https://www.supertux.org/download.html) or alternatively directly
from [GitHub](https://github.com/SuperTux/supertux/releases). You should be able
to install these using default tools provided by your platform. On macOS, when
Gatekeeper is enabled (default) it will refuse to open SuperTux. This is due to
the lack of a signature on the application. If you wish to open SuperTux anyway
without disabling the Gatekeeper feature entirely, you can open the application
from the context menu (control click on the icon). macOS will then remember your
choice the next time.

## Documentation

Important documentation for SuperTux is contained in multiple files.
Please see them:

* `INSTALL.md` - Requirements, compiling and installing.
* `README.md` - This file
* `NEWS.md` - Changes since the previous versions of SuperTux.
* `LICENSE.txt` - The GNU General Public License, under whose terms SuperTux is
licensed. (Most of the data subdirectory is also licensed under
CC-by-SA)
* `data/credits.stxt` - Credits for people that contributed to the creation of
SuperTux. (You can view these in the game menu as well.)


## Playing the game

Both keyboards and joysticks/gamepads are supported. You can view/change the
controls via **options > controls**. Basically, the only controls you will need
to use in-game are the following: jump, duck, move left & right, do an action,
and use the item pocket. An "action" allows you to pick up objects and use any
powerup you have, such as shooting fireballs with the fire flower, or to fire
ice pellets with the ice flower. The item pocket allows you to store a single
additional item, which can be useful in dire situations.

Other useful keys include the Esc key, which is used to go to the menu
or to go up a level in the menu. The menu can be navigated using the
arrow keys or the mouse.

## Community

In case you need help, feel free to reach out using the following means:

* **IRC:** [#supertux](ircs://irc.libera.chat/#supertux) on
  [Libera Chat](https://libera.chat) hosts most of the discussions between
  developers. Also, real-time support can be provided here. If you don't know
  how to use an IRC client, you access the channel using a web-based
  [client](https://kiwiirc.com/nextclient/irc.libera.chat:+6697/?nick=Guest?#supertux).
  Please stay around after asking questions, otherwise you will be disconnected
  and might miss potential answers.
* **[Forum](https://groups.f-hub.org/supertux):** The SuperTux
  community is also active on the forum, the discussions range from feature
  proposals to support questions. In particular, most community-contributed
  add-ons are published there, so this is worth checking.
* **Social Media:** Mostly on [Twitter](https://twitter.com/supertux_team) at
  the moment.
* **Discord:** Also, you can join our [Discord server](https://discord.com/invite/AcvtHWz) to get in touch with us.
