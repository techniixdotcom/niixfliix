# How niixfliix works

The full documentation: what the apps do and how, the Linux app, building, signing keys and
releases. The short version is in the [README](../README.md).

A native Android and Linux app for the public Stremio add-on protocol. Browse
movies and series, open one, pick a stream, watch it. Any Stremio add-on works;
Cinemeta, Torrentio, ThePirateBay+ and OpenSubtitles come installed.

Android 8.0 (API 26) and newer, and 64-bit Linux (x86_64) with Flatpak. See
[The Linux app](#the-linux-app) for what's different there.

The app is a player and add-on client. It contains no media and hosts no
streams. Torrentio and other add-ons are run by third parties, and you are
responsible for what you watch. niixfliix is not affiliated with Stremio.

## Playing torrents

Torrentio and ThePirateBay+ return torrent results. The streams list shows one
per quality (2K, 1080p, 720p, 480p), the one with the most seeds, and plays it
when tapped:

- **Built-in torrent engine.** Plays torrent results straight away. It
  downloads through BitTorrent, so your IP address is visible to other peers.
  There's no limit on its cache: a torrent may use whatever room the device
  has, always leaving 256 MB free. A torrent's files go once it stops playing
  and the cache is emptied when the app closes. Where you stopped each film or
  episode is kept, so it carries on from there next time.
- **Debrid.** A Torrentio link set up with a debrid service (from your Stremio
  account, or an install link) returns plain https links, which start at once.

The add-ons come set up: there's no list of them to manage in the app. A
stremio:// install link from the browser still adds one, after asking.

Streams that only look like the film are left out of the list: trailers,
samples, "premium only" or "upgrade" notices from paid add-ons, and links that
only open a website. What's listed plays the real thing.

Only full-quality releases are listed. Cams, telesyncs, telecines, screeners,
workprints and upscaled copies are left out, and so is any stream that doesn't
say its resolution or whose file is too small for it (for example a 1080p
film under 900 MB or a 1080p episode under 250 MB). A resolution with no
full-quality stream doesn't show at all: fewer choices, but every one is
good. Auto-play plays your quality, or the nearest one there is.

## Home and Discover

Home opens on a big banner that reaches up under the see-through top bar (the
bar turns solid once you scroll). It shows the first five titles of your first
catalog in turn, a new one every 4 seconds with a soft fade; the small bars
under it show which one is on and jump to one when tapped or clicked. It
pauses while you scroll down, touch the list or (on Linux) rest the mouse on
it. Each title shows its artwork, logo, genres and description, **Play**
(opens it and starts the best stream in your quality), **Save** (adds it to
or removes it from your library) and, on Linux, **More info**. Tap the banner
on a phone for its page. Below come Continue watching and a row for every
catalog.

On Linux, resting the mouse on a film or series poster for a moment opens a
preview beside it: bigger artwork, year, length, genres and the start of the
description, with Play, Save and More info buttons.

A title's page shows grey placeholder lines while its details load, and on
the phone the trailer plays inside the app (on Linux it opens in your
browser).

A title's page has, below the play button and description:
- **Cast**: tap a name to search for it.
- **Trailer**: a large preview picture; tap it to watch the trailer (inside the
  app on the phone, on YouTube in your browser on Linux).
  On Linux the trailer and About sit side by side.
- **About**: director, writers, release date, country and awards (whatever the
  source gives), and a link to the title on IMDb.
- **Related movies** (films only): the rest of the film's series (Jurassic
  Park, The Lost World, Jurassic World …), the films it follows or is followed
  by, and its other versions (remakes, other adaptations of the same book),
  oldest first, from Wikidata. Titles in this row are left out of More like
  this.
- **More like this**: titles really related to this one, from Wikidata (the
  open database behind Wikipedia; free, no account or key). It looks at the
  same franchise or series, what it's based on, detailed genres (like
  "psychological thriller" or "heist film" rather than just Thriller; each one
  shared counts more than the same director), the director, writers and cast,
  and ranks by how much is shared. Cartoons, documentaries
  and family titles only show for titles of that style, and titles released
  closer in time come a little higher. Each title is asked once and kept for
  two weeks, in your language. Your sources' genre catalogs are asked at the
  same time, so when Wikidata knows too little (new or rare titles) or doesn't
  answer, they fill the row up without extra waiting.

Search is at the top of Discover, under its title. Type and the results
take the place of the browsing below, a row per type; tap the empty field to
see your recent searches. Back (Esc on Linux) goes back
to browsing.

Discover has no catalog picker: pick Movies or Series, then a genre (or All
genres) from the row of chips. The genres of all your sources are put
together (years are left out), and each genre is browsed from the first
catalog that has it; All genres is the first catalog that needs no genre,
usually Popular.

Titles are sorted by release date, newest first: Home's rows, Discover,
Search and the Library (a series there by its latest episode out). Episodes
stay in season and episode order, and Continue watching and History by when
you watched.

## Stremio account (optional)

Everything works without an account. Sign in with an existing Stremio account
(Settings > Stremio account) and your Stremio add-ons,
including a debrid-configured Torrentio, and your library come into the app.
Saving or removing a title here updates your Stremio library too. Signing out
leaves your add-ons and library on the phone. Accounts can't be created in the
app.

## Watching

- Playback keeps going when you leave the player, with the usual media
  notification and lock-screen controls.
- Swipe down on the video (in portrait), press back, or tap the arrow at the
  top: the app comes back with a mini bar at the bottom and the sound keeps
  playing, so you can browse and search. Tap the bar to get the picture back,
  its X stops playback. Picture in picture is a button in the player.
- In full screen, a swipe down from the top edge is the phone's own: it shows
  the status bar, and a second swipe pulls down the notifications, without
  leaving the video. Brightness is a slider on the left and volume one on the
  right; they show with the controls, and sliding a finger up or down on that
  half of the video changes them too. The brightness only holds in the player's
  full screen (the phone's own setting doesn't change); the volume is the
  phone's media volume.
- Long-press any movie, show or episode (right-click on Linux) to add it to the
  queue, play it next, save it to or remove it from your library, mark it as
  watched, or download it. A series' menu has three downloads: this season (the
  one with the next episode to watch), the whole series, or pick episodes,
  which opens the series so you can download single episodes from their own
  menu.
- On a series' page the watched button marks the season on show. To mark the
  whole series, long-press its picture at the top (on Linux, click the poster).
  Marking as watched only marks episodes that are out, so a new one still
  shows as new. In Continue watching the same menu can also remove it from
  that row; its history and resume point stay, and it comes back once you play
  it again.
- Watched films are greyed out wherever their poster shows, like watched
  episodes in a series. Marking one watched or unwatched changes it right away. The queue is under Library and plays after the current
  video.
- Saved, under Library, can show All, Movies or Series. Titles
  are in A to Z order (in your language); films you've watched to the end move
  to the bottom under a small "Watched" heading. Series stay in A to Z order,
  since new episodes keep coming. When you've watched every episode of a series
  (saved or watched before) and a new one comes out, its poster gets a "NEW"
  ribbon across the top right corner, in Library and in Discover, until you
  watch it. Episode lists are checked in the background every
  few hours. History has the same filter.
- Downloads: right-click (long-press on a phone) a stream with a direct link,
  such as from a debrid service, to download it; torrents can only be played.
  A series' page has "Download episodes" next to the season: the season on
  show or the whole series, every episode that's out, each with the stream
  auto-play would pick among those that can be downloaded. Episodes are named
  like `Series name.s1e2.mkv`; ones already downloaded or queued are skipped.
  They go to Downloads/niixfliix unless you pick another folder in Settings >
  Storage and history > Downloads folder.
- A series plays its next episode on its own, and a film the next thing in the
  queue. Whatever comes next starts downloading in the background, torrents
  included, and "Up next" shows: 5 minutes before the end for the next episode
  of a series, 10 minutes before the end when it's the next title in the queue. Once its start
  is in, it's lined up behind the current video, so it begins the moment this
  one ends. The next button in the player, the mini bar and the notification
  skips to it right away. In full screen the "Up next" card is small and
  see-through, and only shows in the last 30 seconds or while the controls are
  up, so it covers as little of the video as possible.
- The quality you pick for a series (in the streams list or the player) is
  remembered for that series: the next episode, and any episode you open later,
  plays in the same quality, or the next lower one if it isn't there.
- The full screen button in the player's bottom row switches between full screen
  (landscape) and portrait, with the video on top and the title, details and
  "Up next" under it. Turning the phone does the same, if auto-rotate is on and
  the player isn't locked. The player opens the way you left it.
- The lock button in the player's top bar ignores every touch and key until
  the lock is held for 5 seconds. Good for pockets and kids.
- The app starts straight away, with no questions. Your language (Settings >
  Playback, the device's language until you pick one) decides the audio if the
  video has it, otherwise subtitles in it, otherwise English subtitles with the
  original audio. Auto-play quality starts at 1080p on Wi-Fi and 720p on mobile
  data.
- Content marked adult shows unless you turn on Settings > App > Hide content
  marked adult.
- Whatever language the app is in, titles, descriptions and episode names
  follow it. Cinemeta only has English, so titles come from Wikidata and
  descriptions and episode names from the TMDB add-on; everything else (ids,
  episodes, streams) stays Cinemeta's. If one of them is slow or down, the
  other still fills in what it can, and the rest stays in English.
- Double-tap the sides to skip, pinch to zoom.
- Audio the phone can't decode itself (AC3, E-AC3, DTS, TrueHD, common in MKV
  releases) goes through a bundled FFmpeg decoder.
- Search finds titles by their name in the app's language too ("El padrino"
  finds The Godfather), through Wikidata.
- Auto-play and the streams list prefer what your phone's video chip can
  decode itself. A release the phone could only decode in software (often
  10-bit HEVC or AV1, or Dolby Vision on a phone without a Dolby Vision
  decoder) stutters and heats the phone up, so it's only picked when there's
  nothing else in that quality or near it; in the list it says "May stutter
  here". Playback always tries the hardware decoders first.
- The torrent engine is gentler on a phone than on a computer: fewer
  connections at once and uploads capped at 1 MB/s, which saves battery and
  heat without slowing the video down.

## Speed

- Opening a film or an episode's page already asks the add-ons for its
  streams, and gets the torrent engine going, so Play usually finds the list
  full and the engine connected. On any network (Wi-Fi, mobile data and
  roaming) the torrent auto-play would pick gets its start in too: its file
  list, its first megabytes and the video's index, then it waits, paused, so
  Play starts the video at once. On mobile data it's the one auto-play picks
  for mobile data. It's dropped after 3 minutes if nothing played.
- Once the add-ons are in, Home does the same for the first title in Continue
  watching, so tapping it starts straight away.
- Two torrents race to start: the stream being played and the next best one of
  the same quality are fetched side by side, and whichever has its start in
  first plays; the other is dropped and its files deleted. If the first one
  fails while the other is still going, the other takes over without an error. The answers are kept for 5 minutes (1 for
  live TV, 30 seconds if an add-on didn't answer), so going back and forth
  asks nobody again. The engine stops again after 3 minutes if nothing plays,
  and remembers the torrent network between runs so it connects faster.
- Auto-play doesn't wait for the slowest add-on: it starts as soon as a stream
  in your quality that plays smoothly is in, or 2 seconds after the first
  results otherwise.
- At start the app opens its connections to the add-ons straight away. Their
  manifests are stored again once a day; in between it only checks that they
  answer.
- Catalogs and titles' details are kept on disk: Home and a title's page show
  at once, also when an add-on is down, while fresh copies come in behind.
- Android: pictures load through Glide, which decodes them at the size
  they're shown, keeps them on disk and pauses while a list flings; Cinemeta's
  and TMDB's posters are fetched at about the size of the card.
- Linux: the app comes with a Java trimmed to what it uses, and the first run
  of each version saves a start-up cache, so every later start is quicker.

## Live TV

The Live tab looks like Discover, on Android and Linux alike: a row of chips
picks the source (Sport TV, Sports channels and every live TV or live events
catalog of your add-ons, Stremio's "tv" and "events" types), a second row
picks the genre when the catalog has more than one, and the channels and
events show as posters below, a channel's logo shown whole. Before a channel or event shows, the
app asks its add-on for its streams, leaves out trailers and "premium only"
notices, and opens the first few to see that video comes back; only what
answers is listed, and a stream that didn't answer is left out of its list
too. So whatever is in the tab can be watched. A row with nothing on says so.
Pull down (Android) or press the refresh button (Linux) to check again.

Live TV has no resume point and no "watched" mark, and never shows under
Continue watching. Its catalogs don't show on Home, Discover or Search.

The first chip is **Sport TV**: blue Sport and SRF Sport, played by their own
web players with your own account. blue Sport has every Swiss Super League
match plus the Champions League, Europa League and Conference League; SRF
shows a selection free of charge in Switzerland. On Android the player opens
inside the app: log in once and the app keeps you logged in (sign out on
their site, or clear the app's data). Their video is protected (Widevine), so
the app allows it only on their own sites (`blue.ch`, `swisscom.ch`,
`srf.ch`, `srgssr.ch` and their sub-domains); links to anywhere else open in
your browser. Fullscreen video turns to landscape, Back leaves fullscreen,
then goes back a page, then closes. The menu has **Reload** and **Desktop
site** (on by default for blue Sport, whose web TV is made for computers;
turn it off if a page looks wrong). Both services only play inside
Switzerland. On Linux the tiles open their sites in your browser, because the
web engine Flatpak apps can use can't play protected video.

The next chip is built in too: **Sports channels**, free channels open to
everyone (FIFA+, Red Bull TV, DAZN and Pluto TV sports channels, NHL Network,
Tennis Channel and the like). The list comes from iptv-org's public sports
playlist (`iptv-org.github.io`), fetched at most every 6 hours, and only
channels sent by licensed free-TV distributors (Pluto TV, Samsung TV Plus,
Amagi, Wurl and similar) are kept; restreams of pay channels are left out.
Channels from your country come first, and as everywhere in Live only the
ones that play right now show. Paid matches (league football, big fights)
aren't on free channels, so they aren't here.

## Tablets and TVs

On tablets the player turns freely in portrait mode. The app also installs on
Android TV and other devices without a touch screen: it shows on the TV home
screen, posters get a white outline when the remote selects them, the player is
always full screen, and left/right on the remote skip back and forward while
the controls are hidden.

## The Linux app

The same app for the desktop, built from the same code: add-ons, catalogs,
search, streams with auto-play, the built-in torrent engine and debrid, the
Stremio account, library, history, queue, downloads, "Up next", translations
and the in-app updater all work the same way. What only makes sense on a phone
(rotation, picture in picture, the lock, gestures, a separate quality for
mobile data) isn't there. It's a Flatpak, so it runs on any distribution and
keeps to its own sandbox.

- Settings is the gear at the top right of every tab on the phone (the bottom
  bar has room for five tabs); on Linux it stays at the bottom of the side rail.
- The side bar shows the tabs with their names on a wide window and turns
  into a slim rail of icons when the window gets narrower.
- Hover over a row of posters for arrows at its ends that page through it.
  A title's page shows its poster beside the facts, and each episode with a
  bigger picture and its summary.
- Right-click a poster, an episode or a stream for the menu a long-press opens
  on the phone (queue, play next, watched, download, copy the link).
- On a touchscreen, a long press does what a right-click does. In the player a
  tap shows or hides the controls, a double tap on the left or right third
  skips back or forward 10 seconds and in the middle plays or pauses. Rows and
  pages scroll with a swipe.
- The player: Space or K plays and pauses, Left/Right or J/L skip 10 seconds,
  Up/Down and the mouse wheel change the volume, M mutes, F or F11 or a double
  click toggles full screen, N plays what's next, Esc leaves full screen and
  then goes back to the app with the video carrying on in the mini bar. The
  mouse's back button goes back a page. Ctrl+F opens the search at the top
  of Discover.
- Subtitles can also be loaded from a file in your Downloads or Videos folder.
- Downloads go to `~/Downloads/niixfliix`, or the folder picked in Settings >
  Downloads folder (through your desktop's folder picker, which gives the app
  that folder only). The folder button next to a download opens its folder.
- stremio:// install links and magnet links from the browser open in the app.
- Add-on links that hold a key (a debrid-configured Torrentio) are encrypted
  with a key that your desktop's keyring holds (GNOME Keyring or KWallet,
  through the Secret portal), or that sits in a file only you can read if
  there isn't one.
- It's a native GTK 4 and libadwaita app: it runs on Wayland (or X11 where
  there's no Wayland), follows your desktop's fonts, scaling and accessibility
  settings, and the whole window is drawn by the graphics card.
- Video goes from GStreamer straight to the graphics card (GTK's own video
  sink), so the picture is scaled and shown there instead of being copied
  through the processor frame by frame. On Wayland, video the graphics card
  decoded can be handed to the display as it is (GTK's graphics offload). The
  screen stays on while a video plays.
- Hardware video decoding (Settings > Playback, on by default) lets the
  graphics card decode too, through VA-API (Intel, AMD) or NVDEC (NVIDIA), for
  smooth 4K playback that hardly uses the processor. A video the card
  can't decode, or that fails there, plays in software instead. If the app ever
  crashes while the card decodes, the next start turns hardware decoding off
  and says so; you can turn it back on in Settings.
- The torrent cache works as on the phone (no limit but the disk's room, with
  256 MB kept free; emptied when the app closes, where you stopped is kept).
  Posters (up to 256 MB) and the add-ons' catalogs (up to 16 MB) are kept on
  disk between runs, so the next start shows Home at once while fresh copies
  come in behind, and a catalog still shows while its add-on is down.
- Scrolling is GTK's own: smooth with a touchpad (with kinetic scrolling),
  and the mouse wheel over a row of posters scrolls the page, Shift+wheel
  scrolls the row.

### Installing on Linux

Flatpak has to be installed with the Flathub repository; most distributions
have it, otherwise see https://flathub.org/setup. Download
`niixfliix<version>.flatpak` from the releases and run:

```
flatpak install --user niixfliix1.17.1.flatpak
flatpak run app.niixfliix.Niixfliix
```

The first install also downloads the GNOME runtime from Flathub (a few hundred
MB, shared with other Flatpak apps). After that the app is in the app menu.
Uninstall with `flatpak uninstall --user app.niixfliix.Niixfliix`; add
`--delete-data` to remove its settings and library too.

### Building the Linux app

`./BUILD.sh --linux` (or option 2 in its menu, see [Build](#build)). If
flatpak, curl, tar or python3 is missing, it says which and offers to install
them with your package manager (pacman, apt, dnf, zypper or xbps; it asks
first and uses sudo). Without a flatpak-builder on the system it uses Flathub's
(`org.flatpak.Builder`, installed for your user, no root needed). From the
menu it also asks whether to install the app once it's built; with options,
add `--install`. Installing replaces the old version, offers to remove an
older copy installed for all users, and offers to close the app if it's still
running the old version (starting it again would only bring the old window to
the front).

It installs JDK 25 into `~/.niixfliix` (checksum verified), runs the unit tests,
builds the app with Gradle, adds Flathub for your user, builds the Flatpak on
the GNOME 51 runtime and writes `dist/niixfliix<version>.flatpak` plus a
`.sha256` file and a `.sig` file (the bundle's signature, made with the Linux
signing key in `~/.keys/linux`, see [Signing keys](#signing-keys)). Older
bundles in `dist/` are removed once the build succeeds.
The log is in `build-linux.log`. To try a build without packaging it:
`./gradlew -PlinuxOnly=true :desktop:run` (needs a JDK 25, GTK 4.14 or later,
libadwaita 1.5 or later and GStreamer with its good, bad and libav plugins and
the GTK 4 video sink, `gst-plugin-gtk4`, on the machine).

## Crash log

There are no crash reports. If the app crashes, the crash is written to a log
on the phone (or computer) only, with keys, links and e-mail addresses removed. Settings >
About > Crash log shows it, copies it (paste it into a GitHub issue) or
deletes it. On Linux this includes crashes in native code (GStreamer,
libtorrent) and the app being killed (for example when memory runs out): the
cause and the last lines the app printed are added to the log at the next
start. Everything the Linux app prints also goes to
`~/.var/app/app.niixfliix.Niixfliix/data/logs` (the last five runs), readable
only by you. Release builds are shrunk by R8: `BUILD.sh` keeps
`dist/niixfliix<version>-mapping.txt`, and
`retrace dist/niixfliix<version>-mapping.txt crash.txt` (in the SDK's
`cmdline-tools/latest/bin`) turns a pasted log back into real class and method
names.

## Build

One script, `BUILD.sh`, builds, tests and publishes both apps. Run it without
options for its menu; it is the same menu as in the maintainer's other apps
(TestTube, NiiX), with the options that fit this app:

```
./BUILD.sh
What would you like to do?
  1) Build a signed release APK
  2) Build the Linux app (Flatpak)
  3) Build the signed release APK and the Linux app
  4) Build both and publish them to GitHub Releases
  5) Publish the already-built APK and Linux app to GitHub Releases
  6) Build a debug APK (no signing key needed)
  7) Install the built APK to a connected phone (shows the real error if it fails)
  8) Run the test suite
  9) Package a source archive for a reviewer (no keys)
 10) Record dependency hashes (supply-chain verification)
 11) Push source to GitHub (no release)
 12) Set the version for the next release
 13) Create signing keys (once, before your first signed build)
 14) Show the signing keys
 15) Switch to the shared Android key (one key for all apps)
 16) Give the Linux app a new signing key (installed copies accept it)
 17) Back up all signing keys (~/.keys)
 18) Delete all build output
```

Or say it up front, e.g. in scripts (`./BUILD.sh --help` lists every option):

```
./BUILD.sh --release              # signed release APK (--android works too)
./BUILD.sh --debug                # debug APK
./BUILD.sh --linux                # Flatpak bundle
./BUILD.sh --all                  # both, Android first
./BUILD.sh --all --clean --install --verbose
./BUILD.sh --publish              # both, published to GitHub Releases
./BUILD.sh --test                 # the test suite
```

`--clean` wipes the build output first (what's in `dist/` stays; on its own it
deletes all build output), `--install` installs what was built (the APK on the
connected device with adb, the Flatpak for your user; on its own it installs the
APK already in `dist/`), `--verbose` shows everything instead of only writing
the logs (`build.log`, `build-linux.log`). Everything ends up in `dist/`. With
`--all` it stops at the first build that fails. The Android app builds on Linux
and macOS, the Linux app on Linux (x86_64); on macOS the Linux options say so.
Building both, publishing and recording dependency hashes run on Linux. The two
builds themselves are in `scripts/`; `BUILD.sh` is the one to run.

For the Android app it installs JDK 17 and the Android SDK into `~/.niixfliix`
(checksums verified), creates a release signing key in `~/.keys/niixfliix` on
the first run, runs the unit tests and writes `dist/niixfliix<version>.apk` plus
a `.sha256` file. Older APKs in `dist/` are removed once the build succeeds; the
mapping files are kept. Back up `~/.keys` (`./BUILD.sh --backup-keys`): updates
must be signed with the same keys.

### Signing keys

Every signing key, for all your apps, is kept in one folder, `~/.keys`, that
only you can open (its `README.txt` says what each file is):

- `~/.keys/android`: the Android key all your apps share.
- `~/.keys/niixfliix`: this app's own original Android key. Up to 1.17.0 it was
  kept in `~/.niixfliix/keys`; the first build moves it.
- `~/.keys/linux`: the Linux signing key, earlier ones in `old/`, and
  `handover.txt`.

`./BUILD.sh --use-shared-key` (menu option 15) moves the Android app to the
shared key, for good. The app's own key signs, once, its approval of the shared
key (`lineage.bin`, Android's key rotation), and every APK built after that
carries it: phones with Android 9 or newer take it as a normal update and from
then on only accept the shared key; Android 8 keeps checking the original key,
which is still signed in for it. The in-app updater accepts such a switch from
1.17.1 on, so publish 1.17.1 (or newer) with the old key first and switch after
that; the script checks GitHub and warns otherwise. The shared key is made the
first time any of your apps switches; keep both keys and `lineage.bin` for good.

The Linux app only installs updates signed with the Linux key it was built to
trust (`linux/update-key.pub`, written by the build). `./BUILD.sh --new-linux-key`
(menu option 16) makes a new Linux key: the old key signs its approval of the new
one into `handover.txt`, which every release from then on carries as
`niixfliix-linux-key-handover.txt`, so installed copies accept the new key by
themselves. Versions up to 1.17.0 only checked the `.sha256`, and update to a
signed release normally.

`./BUILD.sh --backup-keys` (menu option 17) copies all of `~/.keys` to
another folder (it asks which) and checks every file. Keep two copies offline: anyone with these
files can sign updates for your apps, and without them no new version can update
what people have installed.

Android Studio works too: open the folder and run the `app` configuration.

### Checks on GitHub

`.github/workflows/build.yml` runs the unit tests, a debug build, an R8-shrunk
release build and lint on every push and pull request, on a real Android SDK,
and builds the Linux app's Flatpak with `BUILD.sh --linux`. The debug APK, the
Flatpak bundle and the test and lint reports are attached to each run.
`.github/dependabot.yml` opens a pull request once a month when Media3, OkHttp,
libtorrent4j, the other libraries, Gradle or the GitHub Actions have a
new version. Turn on Dependabot security updates in the repository's settings
(Security > Code security) to also get pull requests for security fixes as
soon as they're published.

## Renaming

The name lives in `gradle.properties` (`appName`, `appId`). Everything else,
the app label, the APK and Flatpak names and the downloads folder included,
comes from there. The Flatpak's id (`app.niixfliix.Niixfliix`) is set in
`scripts/linux.sh` and `Paths.java`; changing it makes it a different app with
its own settings.

## Releases and updates

Source code and releases: https://github.com/techniixdotcom/niixfliix

The in-app updater reads the releases of the repository set in
`gradle.properties` (`githubRepo`); the repository has to be public. It takes
the release with the highest version (from the tag, e.g. `v1.1.3`, or the file's
name), pre-releases included, drafts not. The Android app looks for the `.apk`
in it, the Linux app for the `.flatpak`.

To publish an update:

1. Building, publishing and pushing the source all ask for the version first,
   suggesting the next one (Enter takes it; typing the current one keeps it).
   `./BUILD.sh --set-version` (option 12) only sets it. It raises `versionName`
   and `versionCode` in `gradle.properties` and adds the version's section to
   `CHANGELOG.md`.
2. Write what changed under it in `CHANGELOG.md`: it becomes the release notes
   and the text of the update dialog.
3. Run `./BUILD.sh --publish` (option 4) on Linux, on the same computer as
   before or one with a copy of `~/.keys`: updates must be signed with the same
   keys. It builds both apps, checks them the way the apps' updaters will, and
   publishes the APK and its `.apk.sha256`, the `.flatpak` with its
   `.flatpak.sha256` and `.flatpak.sig`, and `niixfliix-linux-key-handover.txt`
   if there is one. Then it checks GitHub's size and SHA-256 of every file.
   Publishing needs the GitHub CLI (`gh`); it is installed and logged in the
   first time.

An update is only installed when the checksum matches, the package name is the
same, the version code is higher and the signing certificate is the same as
the installed app's (or the installed app's key approved it, see
[Signing keys](#signing-keys)). If something is off, Settings > Check for updates says
exactly what (missing file, wrong repository, different key, version code not
raised). Debug builds (`./BUILD.sh --android --debug`) are a separate app and can't
update themselves: install the release APK once.

On Linux the update is downloaded to the downloads folder (`~/Downloads/niixfliix`
unless you picked another one), checked against
its `.flatpak.sha256` and its `.flatpak.sig` (signed with a Linux key the app
trusts) and handed to your software centre (GNOME Software, KDE
Discover) to install. If none opens, the app shows the `flatpak install` command
to run. Restart the app afterwards.

## More

- License: the app's own code is MIT (see [LICENSE](../LICENSE)). The APK also
  bundles Jellyfin's prebuilt FFmpeg audio decoder
  (`org.jellyfin.media3:media3-ffmpeg-decoder`), published under GPL-3.0, so an
  APK you distribute falls under GPL-3.0 terms and its source has to be
  available (Settings > About > Source code links to it). libtorrent4j is MIT, libtorrent is BSD, the rest is Apache 2.0 or BSD.
  The Linux app bundles the OpenJDK runtime (GPL 2.0 with the Classpath
  Exception) and TwelveMonkeys ImageIO (BSD), and uses the GTK, libadwaita,
  GStreamer, GLib and libsecret libraries of the GNOME runtime (LGPL 2.1 or
  later) and its GTK 4 video sink from gst-plugins-rs (MPL 2.0); it doesn't
  include the FFmpeg decoder.
