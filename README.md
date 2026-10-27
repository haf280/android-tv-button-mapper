# TV Recents: button mapper and recent-apps switcher for Android TV

Remap your TV remote's buttons (the colour buttons, and on many remotes the app buttons such as
Netflix or Prime Video) to open any app, show your recent apps, or close apps to free memory.
For Android TV 8+ and Google TV. Tested on Sony BRAVIA and Haier; other TVs and boxes (TCL, Philips,
Hisense, Xiaomi, Chromecast, Nvidia Shield) should work. No ads, no permissions, no internet.

> **This fork adds a Buttons screen** to [nilava/tv-recents](https://github.com/nilava/tv-recents).
> Open Recents and choose **Buttons** to give your remote's spare buttons a job: press the button,
> then pick an app, Recent apps, Home, Back or Close all. The blue button opens Recents out of the
> box. Its app id is `io.github.haf280.tvrecents`, so it installs next to the original. Tested on a
> Sony BRAVIA KE-55XH8096 (Android TV 10, 2 GB RAM).

**A recent-apps switcher for Android TV and Google TV.** Hold **Home** on your remote and a clean panel
slides up over whatever you're watching. Jump back to the last app in one press, or close the apps you're
done with to free memory.

Most Android TVs don't have a recent-apps screen at all, and none of them let you close apps easily.
On a TV with 1–2 GB of RAM, that matters.

- **Hold Home** or press **blue** to open it, from inside any app. A short press on Home still goes Home.
- **Buttons:** set what your remote's spare buttons do (colour buttons, app buttons and so on). OK, the
  arrows, Back, Home, Menu, power and volume stay as they are, so a mapping can't lock you out.
- **Switch:** the app you used before this one is already selected. Press OK.
- **Close an app:** press Menu on it. **Close all** closes everything except the app you're in.
- **Memory:** shows how much is free, and how much each close gave back.
- **Tiny:** about 5 MB of RAM when idle, one screen of plain Java, no libraries.
- **Private:** no internet permission, no other permissions. Nothing leaves your TV.

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
4. **On the TV, open Recents** from your apps. Setup is one switch, one press away: in
   **Accessibility**, turn on **Recents: hold Home and Force stop**. Android warns that it can see and
   control the screen; that's what lets it keep the list and press Force stop for you. Setup comes
   back by itself once it's on.

Installing with `adb` matters: on Android 13 and later, apps installed from a browser or file manager
can't turn on accessibility until you allow "restricted settings" in their App info.

## How it works (and why it needs what it needs)

Its only permission is the **accessibility service**, which you switch on yourself. It uses it to:

- notice when you hold Home, and draw the panel over other apps,
- catch the buttons you set on the Buttons screen and do what you picked,
- note which app is on screen, to keep the list,
- press **Force stop** → **OK** in an app's App info when you ask to close it.

Since Android 14, normal apps can't close other apps or see what's running. So:

- **Closing** opens the app's App info page and presses Force stop, then OK, behind a dark
  "Closing apps" screen. It only touches Settings screens it opened itself, only when you ask, and it
  gives up on an app after 4 seconds. Then it returns you to the app you were in.
- **The list** shows apps you opened since the TV started, minus the ones you closed here. Recents
  keeps it itself, timed by the TV's uptime rather than its clock, so it still works on TVs whose
  clock is wrong at startup. (Android's own usage log doesn't: it files everything under the wrong date.)

**Close all never closes** the app you're in, Live TV, Settings, home screens, the keyboard, apps with
an accessibility service on, or apps from TV and chip makers (Haier, TCL, Sony, Xiaomi, MediaTek and
others), since some of those keep the TV itself working.

## Tested on

- Haier MatrixTV CE (Google TV, Android 14, 1.4 GB RAM)
- Sony BRAVIA KE-55XH8096 (Android TV 10, 2 GB RAM): switching, closing, Close all and Buttons

It should work on other Android TV 8+ and Google TV devices. If it doesn't on yours, please
[open an issue](../../issues) with your TV model and Android version.

## Known limits

- **English only, for now.** Closing apps finds the "Force stop" and "OK" buttons by their English
  text. On a TV set to another language, switching and the list work, but closing doesn't.
- **TV makers' Settings apps differ.** It's built for the standard Android TV Settings app
  (`com.android.tv.settings`). TVs with their own Settings app may not close apps yet.
- **Hold Home** depends on the remote sending a normal Home key. If a TV maker's software grabs Home
  first, the shortcut won't fire; you can still open Recents from its tile or the blue button.
- **A remapped button does its new job everywhere.** Apps that use that button themselves (some IPTV
  and teletext apps use the colour buttons) won't get it while it's mapped.
- **Close all doesn't always free memory.** It works through Settings, which stays loaded afterwards.
  It's most useful for an app that has stopped responding.

## Turn it off or remove it

- Turn off the shortcut: Settings → Accessibility → Recents → Off.
- Remove it: `adb uninstall io.github.haf280.tvrecents`, or uninstall it from Settings → Apps.

## Build it yourself

Needs Java 17 and the Android SDK (platform 34, build-tools 34.0.0). No Gradle.

```sh
sh build.sh          # makes out/tv-recents.apk
adb install -r out/tv-recents.apk
```

No Java or SDK on your computer? Build it in Docker instead (about 0.8 GB, downloaded once):

```sh
sh docker/build.sh   # same output, out/tv-recents.apk
```

The first build creates a signing key, `key.jks`. Keep it and back it up: updates must be signed with
the same key. It's in `.gitignore`, so it's never committed.

## Credits

Built while cleaning up a slow Android TV with [tv-detox](https://github.com/Ansub/tv-detox), whose
plain-Java, no-Gradle style this follows.

The Buttons screen in this fork is by [haf280](https://github.com/haf280). The app itself is by
[Nilava Chowdhury](https://github.com/nilava).

MIT License.
