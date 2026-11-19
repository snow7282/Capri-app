# Capri (Android app)

Capri lives on your phone: a floating bubble, pop-ups that react to what you do
(charger, low battery, headphones, unlocking), optional voice, and a chat screen
that talks to her fine-tuned model running **on your own phone**.

Everything is local. No accounts, no analytics, no cloud.

---

## What she can do

| Feature | How it works |
|---|---|
| Floating bubble | Draggable "C" over other apps. Tap it to open chat. |
| Pop-up cards | Appear at the top of the screen, auto-hide, tap to open chat. |
| Reacts to your phone | Charger plugged in, battery low, headphones in, unlock (morning / late night). |
| Random check-ins | Every 2 to 5 hours (switchable). |
| Voice | Android text-to-speech, off by default. |
| Memory | Last chat lines saved privately on the phone, plus optional "about you" notes. |
| Real replies | Sent to your local llama.cpp server running her GGUF. |
| Works without the brain | If the server is off, pop-ups use her saved lines (edit them in `Persona.kt`). |

## What she does NOT do (on purpose)

- No reading of your screen, other apps, messages, contacts, location, mic or camera.
- No Accessibility service, no Usage Access.
- No sending anything off your phone. The only network call is to your own model server.
- No pop-ups over the lock screen, banking apps or other secure screens (Android blocks that).
- She never spams: minimum gap between pop-ups, quiet hours, a visible notification with a Stop button, and a master switch.

---

## 1. Open and run the project

1. Install **Android Studio** (current version) on your PC. It bundles the JDK.
2. `File > Open` and choose the `CapriApp` folder. Let Gradle sync finish (first time downloads a lot).
   - If Android Studio offers an "Android Gradle Plugin upgrade", you can accept it or ignore it.
3. On the phone, enable **Developer options** (Settings > About phone > tap MIUI version 7 times), then turn on
   **USB debugging** and **Install via USB** (Xiaomi needs both).
4. Plug the phone in, pick it in the device list, press **Run**.

To get a standalone APK instead: `Build > Build Bundle(s) / APK(s) > Build APK(s)`, then copy the APK to the phone and install it.

## 2. Give her permissions (in the app: Settings)

1. Allow "Display over other apps".
2. Allow notifications.
3. Don't restrict battery for Capri.
4. **Xiaomi/MIUI extras:** Autostart ON, "Display pop-up windows while running in the background" ON,
   lock the app in recents. The button "Xiaomi: Autostart + pop-up permissions" opens the right screen.
5. Switch on **"Capri is awake"**, then tap **"Make Capri pop up right now (test)"**.

If the test pop-up appears, the whole overlay system works.

## 3. Give her a real brain (Termux + llama.cpp)

The same steps are shown inside the app under Settings > Her brain. In short:

```
termux-change-repo
pkg upgrade -y
pkg install -y clang cmake git libandroid-spawn
termux-setup-storage
cd ~ && git clone https://github.com/ggml-org/llama.cpp
cd llama.cpp
cmake -B build
cmake --build build --config Release -j 3 --target llama-server

cp ~/storage/downloads/*Q4_K_M*.gguf ~/capri.gguf
termux-wake-lock
~/llama.cpp/build/bin/llama-server -m ~/capri.gguf --host 127.0.0.1 --port 8080 -c 1024 -t 4
```

- Install Termux from F-Droid or GitHub, not the old Play Store build.
- The build can take 20 to 40 minutes on a phone. Keep it plugged in with the screen on.
- Your Redmi Note 9 Pro Max is not fast: expect a few words per second, so a short reply takes 10 to 20 seconds.
- Keep Termux alive in the background (battery unrestricted for Termux too).
- If she sounds generic or garbled, add `--chat-template chatml` to the server command.
- Don't run heavy games at the same time (6 GB RAM).
- Back in Capri: Settings > Test connection. The chat screen shows "brain online" when it works.

## 4. How the code is organised

| File | Job |
|---|---|
| `CapriService.kt` | Background presence. Listens for phone events, decides when to pop up (limits, quiet hours). |
| `OverlayController.kt` | Draws the bubble and pop-up card. |
| `Brain.kt` | Chat memory, builds the messages, cleans up the model's reply. |
| `LlmEngine.kt` | `LlmEngine` interface + `LlamaServerEngine` (HTTP to llama.cpp). Swap this to change brains. |
| `Persona.kt` | Phone events phrased as `*actions*`, plus her fallback lines. **Edit her lines here.** |
| `Speaker.kt` | Text-to-speech. |
| `Notifier.kt` | Notifications. |
| `MainActivity.kt` / `ChatAdapter.kt` | Chat screen. |
| `SettingsActivity.kt` | Permissions, switches, setup steps. |
| `Prefs.kt` | Settings storage. |

Why events look like `*plugs in the charger*`: her training data had lots of `*action*` lines,
so phrasing events that way keeps the model in character.

## 5. Customising

- Her saved lines: `Persona.kt` (`fallback`).
- Random pop-up prompts: `Persona.kt` (`randomPrompts`).
- How often she checks in: `CapriService.kt` (`scheduleCheckIns`, the `120..300` minutes range).
- Bubble look: `OverlayController.kt` (`showBubble`).
- Voice: `Speaker.kt` (pitch, rate, locale).
- Reply length: `Brain.kt` (the `160` and `70` max-token numbers).

## 6. Troubleshooting

| Problem | Fix |
|---|---|
| No bubble | Overlay permission missing, or MIUI "pop-up windows in background" is off. |
| She stops popping up after a while | Xiaomi killed her. Autostart ON, battery unrestricted, lock in recents. |
| "brain offline" | Termux server not running, or wrong address in Settings. |
| Replies are very slow | Normal on this phone. Keep replies short, close other apps. |
| She sounds generic | Clear the "About you" notes; add `--chat-template chatml` to the server command. |
| Build errors in Android Studio | Copy the first red error line and send it to Claude. |

## 7. Staying legal and respectful

- This is for use on **your own phone**, with permissions **you** grant.
- Don't use it to monitor other people.
- Capri's persona is inspired by Cyberpunk: Edgerunners. That's fine for personal use. If you ever publish it,
  don't use the show's name, art or logos, and check the licences of your base model (SmolLM2) and any libraries.
- Google Play has extra rules for overlay and foreground-service apps. Sideloading to your own phone avoids them.

## 8. Next upgrades

- **Run the model inside the app** (no Termux): see `extras/EmbeddedLlamatikEngine.kt.txt`. Untested.
- **App-specific reactions** (e.g. "you've been on Instagram for an hour") need Usage Access permission.
  Possible, but more privacy-sensitive. Add only if you want it.
- Her own voice, a home-screen widget, and a quick-settings tile are all straightforward additions.

---

## No PC? Two ways to get her running from the phone alone

1. **`extras/capri.sh`**: a Termux script that makes her pop up as notifications (charger, low battery,
   random check-ins) using her real model. No app to build. See the chat message that came with this project.
2. **Build the APK in the cloud (GitHub Actions)**: `.github/workflows/build-apk.yml` is already included.
   Push this folder to a GitHub repo from Termux (`git init`, `git add .`, `git commit`, `git push`),
   open the repo's **Actions** tab, run **Build Capri APK**, then download the `Capri-debug-apk` artifact
   and install it. If the build fails, open the failed step, copy the first red error line and send it to Claude.
