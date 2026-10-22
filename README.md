# TV Recents

**A recent-apps switcher for Android TV and Google TV.** Hold **Home** on your remote and a clean panel
slides up over whatever you're watching. Jump back to the last app in one press, or close the apps you're
done with to free memory.

Most Android TVs don't have a recent-apps screen at all, and none of them let you close apps easily.
On a TV with 1–2 GB of RAM, that matters.

- **Hold Home** to open it, from inside any app. A short press still goes Home.
- **Switch:** the app you used before this one is already selected. Press OK.
- **Close an app:** press Menu on it. **Close all** closes everything except the app you're in.
- **Memory:** shows how much is free, and how much each close gave back.
- **Tiny:** about 5 MB of RAM when idle, one screen of plain Java, no libraries.
- **Private:** it has no internet permission. Nothing leaves your TV.

## Install

You need a computer with [adb](https://developer.android.com/tools/releases/platform-tools)
(on a Mac: `brew install android-platform-tools`) on the same Wi-Fi as the TV.

1. **Turn on developer mode on the TV:** Settings → System (or Device Preferences) → About → press
   **Build** 7 times. Then in **Developer options**, turn on **USB debugging** (and **Network
   debugging** if you see it).
2. **Download** `tv-recents.apk` from [Releases](../../releases/latest).
3. **Install it:**
   ```sh
   adb connect <TV_IP>:5555      # the TV asks "Allow debugging?": tick Always allow, press Allow
   adb install tv-recents.apk
   ```
4. **On the TV, open Recents** from your apps. A short setup walks you through two switches, each
   one press away, and comes back by itself once you've switched it on:
   - **Usage access** → turn on **Recents**.
   - **Accessibility** → **Recents: hold Home and Force stop** → On. Android warns that it can control
     the screen. That's what lets it press Force stop for you.

Installing with `adb` matters: on Android 13 and later, apps installed from a browser or file manager
can't turn on accessibility until you allow "restricted settings" in their App info.

## How it works (and why it needs what it needs)

| Permission | Why |
|---|---|
| **Usage access** | To know which apps you opened and when. That's the list. |
| **Accessibility service** | To notice when you hold Home, draw the panel over other apps, and press **Force stop** → **OK** in an app's App info when you ask to close it. |

Since Android 14, normal apps can't close other apps or see what's running. So:

- **Closing** opens the app's App info page and presses Force stop, then OK, behind a dark
  "Closing apps" screen. It only touches Settings screens it opened itself, only when you ask, and it
  gives up on an app after 4 seconds. Then it returns you to the app you were in.
- **The list** shows apps you opened since the TV started, minus the ones you closed here.

**Close all never closes** the app you're in, Live TV, Settings, home screens, the keyboard, apps with
an accessibility service on, or apps from TV and chip makers (Haier, TCL, Sony, Xiaomi, MediaTek and
others), since some of those keep the TV itself working.

## Tested on

- Haier MatrixTV CE (Google TV, Android 14, 1.4 GB RAM)

It should work on other Android TV 8+ and Google TV devices. If it doesn't on yours, please
[open an issue](../../issues) with your TV model and Android version.

## Known limits

- **English only, for now.** Closing apps finds the "Force stop" and "OK" buttons by their English
  text. On a TV set to another language, switching and the list work, but closing doesn't.
- **TV makers' Settings apps differ.** It's built for the standard Android TV Settings app
  (`com.android.tv.settings`). TVs with their own Settings app may not close apps yet.
- **Hold Home** depends on the remote sending a normal Home key. If a TV maker's software grabs Home
  first, the shortcut won't fire; you can still open Recents from its tile.

## Turn it off or remove it

- Turn off the shortcut: Settings → Accessibility → Recents → Off.
- Remove it: `adb uninstall io.github.nilava.tvrecents`, or uninstall it from Settings → Apps.

## Build it yourself

Needs Java 17 and the Android SDK (platform 34, build-tools 34.0.0). No Gradle.

```sh
sh build.sh          # makes out/tv-recents.apk
adb install -r out/tv-recents.apk
```

The first build creates a signing key, `key.jks`. Keep it and back it up: updates must be signed with
the same key. It's in `.gitignore`, so it's never committed.

## Credits

Built while cleaning up a slow Android TV with [tv-detox](https://github.com/Ansub/tv-detox), whose
plain-Java, no-Gradle style this follows.

MIT License.
