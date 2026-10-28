# TV Recents: button mapper and recent-apps switcher for Android TV

Remap your TV remote's buttons (the colour buttons, and on many remotes the app buttons such as
Netflix or Prime Video) to open any app, show your recent apps, or close apps that are stuck.
For Android TV 8+ and Google TV. Tested on a Sony BRAVIA; other TVs and boxes (TCL, Philips, Hisense,
Xiaomi, Chromecast, Nvidia Shield) should work. No ads, no permissions, nothing leaves your TV.

Most Android TVs don't have a recent-apps screen at all, can't remap their remote, and don't let you
close apps easily. Recents adds all three.

- **Recent apps:** hold **Home** or press **blue**, from inside any app, and a panel slides up over
  what you're watching. The app you used before this one is already selected: press OK to switch.
  A short press on Home still goes Home.
- **Buttons:** in the panel, choose **Buttons**, then **Add button** and press any spare button on the
  remote. Pick what it does: open an app, Recent apps, Home, Back or Close all. Changes work at once.
  OK, the arrows, Back, Home, Menu, power and volume stay as they are, so a mapping can't lock you out.
- **Close an app:** press Menu on it in the panel. **Close all** closes everything except the app
  you're in.
- **Memory:** the panel shows how much is free, and how much each close gave back.
- **Small and private:** plain Java, no libraries, no internet, no permissions.

## Install

You need a computer with [adb](https://developer.android.com/tools/releases/platform-tools)
(on a Mac: `brew install android-platform-tools`) on the same network as the TV.

1. **Turn on developer mode on the TV:** Settings → System (or Device Preferences) → About → press
   **Build** 7 times. Then in **Developer options**, turn on **USB debugging** (and **Network
   debugging** if you see it).
2. **Download** `tv-recents.apk` from [Releases](../../releases/latest).
3. **Install it:**
   ```sh
   adb connect <TV_IP>:5555      # the TV asks "Allow debugging?": tick Always allow, press Allow
   adb install tv-recents.apk
   ```
4. **On the TV, open Recents** from your apps. Setup is one switch: in **Accessibility**, turn on
   **Recents: hold Home and Force stop**. Android warns that it can see and control the screen; that's
   what lets it catch your buttons, keep the list and press Force stop for you. Setup comes back by
   itself once it's on.
5. **Turn USB debugging off again** if you like. Recents keeps working.

Installing with `adb` matters: on Android 13 and later, apps installed from a browser or file manager
can't turn on accessibility until you allow "restricted settings" in their App info.

## How it works (and why it needs what it needs)

Its only permission is the **accessibility service**, which you switch on yourself. It uses it to:

- notice when you hold Home or press a button you set, and draw the panel over other apps,
- catch the button you press on the Buttons screen. Some buttons, like Sony's app buttons, are taken
  by the TV before any screen sees them, so the service hands them to the Buttons screen itself,
- note which app is on screen, to keep the list,
- press **Force stop** → **OK** in an app's App info when you ask to close it.

**Closing** opens the app's App info page and presses Force stop, then OK, behind a dark "Closing
apps" screen. It finds both buttons by the Settings app's own words in the TV's language, so it works
whatever language the TV is set to. It only touches Settings screens it opened itself, only when you
ask, and it gives up on an app after 4 seconds. Then it returns you to the app you were in.

Why not Android's `killBackgroundProcesses()`? Since Android 14 it only works on an app's own
processes, and some TVs refuse it for apps altogether: on a Sony BRAVIA (Android 10) every call
was rejected with "Invalid packageName".

**The list** shows apps you opened since the TV started, minus the ones you closed here. Recents keeps
it itself, timed by the TV's uptime rather than its clock, so it still works on TVs whose clock is
wrong at startup.

**Close all never closes** the app you're in, Live TV, Settings, home screens, the keyboard, apps with
an accessibility service on, or apps from TV and chip makers (Haier, TCL, Sony, Xiaomi, MediaTek and
others), since some of those keep the TV itself working.

## Tested on

- Sony BRAVIA KE-55XH8096 (Android TV 10, 2 GB RAM): recent apps, Buttons (colour and Sony app
  buttons), closing one app and Close all.

It should work on other Android TV 8+ and Google TV devices. If it doesn't on yours, please
[open an issue](../../issues) with your TV model and Android version.

## Known limits

- **TV makers' Settings apps differ.** Closing is built for the standard Android TV Settings app
  (`com.android.tv.settings`), which Sony also uses. TVs with their own Settings app may not close
  apps yet.
- **Hold Home** depends on the remote sending a normal Home key. If a TV maker's software grabs Home
  first, the shortcut won't fire; you can still open Recents from its tile or the blue button.
- **A remapped button does its new job everywhere.** Apps that use that button themselves (some IPTV
  and teletext apps use the colour buttons) won't get it while it's mapped.
- **Closing apps rarely makes a TV faster.** Android reuses freed memory for its cache right away, and
  Settings stays loaded after closing. It's most useful for an app that has stopped responding.

## Turn it off or remove it

- Turn off the shortcut and buttons: Settings → Accessibility → Recents → Off.
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

## License

MIT. See [LICENSE](LICENSE).
