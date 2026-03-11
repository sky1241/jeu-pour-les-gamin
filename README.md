# SuperTux Multi (fork pour les gamins)

Fork de [SuperTux](https://github.com/SuperTux/supertux) adapte pour le **multijoueur local** : chaque telephone = une manette, la TV = ecran partage.

## Architecture

```
[Phone 1] --WiFi/WebSocket--> [PC/TV: SuperTux] <--WiFi/WebSocket-- [Phone 2]
   App Flutter manette              Ecran partage split-screen
```

## Fonctionnalites

### Jeu (PC/TV)
- **Split-screen dynamique** : 1 joueur = plein ecran, 2 = gauche/droite, 3 = 2 en haut + 1 en bas, 4 = 4 quadrants
- **Serveur WebSocket** integre (port 9876) — les telephones se connectent automatiquement
- **Decouverte UDP** (port 9877) — zero IP a taper, les telephones trouvent le serveur tout seuls
- **Spawn dynamique** des joueurs quand un telephone se connecte
- Credits et noms de devs retires des menus

### App Manette (telephone)
- **Joystick** a gauche (thumb-follow, retour au centre)
- **Bouton A** (saut, vert, 96dp) + **Bouton B** (action, orange, 80dp)
- **Auto-discovery** : trouve le serveur sur le reseau WiFi automatiquement
- **60 Hz** delta-encoded (envoie seulement quand l'input change)
- **UX Infernal Wheel** : touch targets 48dp+, espacement 8dp, contraste WCAG

### Mobile solo
- **Smart View** : la gamine lance SuperTux sur le telephone, active Smart View, sa s'affiche sur la TV Samsung. Zero code en plus.
- **Croix directionnelle (D-pad)** en bas a gauche, toujours visible
- **Bouton de saut** en bas a droite
- **Tilt desactive** par defaut

## Avancement

| Brique | Description | Status |
|--------|-------------|--------|
| 1 | Protocole reseau (JSON/WebSocket) | OK |
| 2 | Serveur WebSocket C++ (IXWebSocket) | OK |
| 3 | Input Dispatcher (phone -> Controller) | OK |
| 4 | App Flutter manette + UDP discovery | OK |
| 5 | Multi-joueur (spawn + multi-controller) | OK |
| 6 | Split-screen (1-4 joueurs) | OK |
| 7 | Ghost renderer (40% opacite) | A faire |
| 8 | Victory sync | A faire |
| 9 | Lobby + discovery | A faire |
| 10 | Polish (reconnexion, ping, vibration) | A faire |

## Build Desktop (Windows)

```batch
call "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
cmake .. -G Ninja -DCMAKE_BUILD_TYPE=Release ^
  -DCMAKE_TOOLCHAIN_FILE=C:\vcpkg\scripts\buildsystems\vcpkg.cmake ^
  -DVCPKG_TARGET_TRIPLET=x64-windows-nomodules ^
  -DVCPKG_OVERLAY_PORTS=C:\Users\ludov\vcpkg-overlay
cmake --build . --parallel
```

## Build App Manette (Flutter)

```bash
cd controller_app
flutter pub get
flutter build apk --release
adb install build/app/outputs/apk/release/app-release.apk
```

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
