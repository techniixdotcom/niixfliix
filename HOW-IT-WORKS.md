# How it works

niixfliix speaks the public Stremio add-on protocol. An add-on is an HTTPS
server; the app asks it for JSON with plain GET requests:

| Request | Answer |
| --- | --- |
| `{base}/manifest.json` | What the add-on can do |
| `{base}/catalog/{type}/{id}[/{extra}].json` | Posters for a row, a page or a search |
| `{base}/meta/{type}/{id}.json` | Details, cast, episodes |
| `{base}/stream/{type}/{videoId}.json` | Streams: https links or torrents |
| `{base}/subtitles/{type}/{videoId}/{extra}.json` | Subtitle files |

An add-on is only asked for things its manifest says it supports.

## Every server the app contacts

There are no ads, analytics, tracking or crash reports. This is the complete list:

| Server | When | Why |
| --- | --- | --- |
| `v3-cinemeta.strem.io` | Browsing, searching, opening a title | Catalogs and details (Cinemeta add-on) |
| `torrentio.strem.fun` | Opening the streams list | Stream results (Torrentio add-on). With a debrid key, the key is part of this URL, because that is how Torrentio is configured. |
| `thepiratebay-plus.strem.fun` | Opening the streams list | Stream results (ThePirateBay+ add-on). Never gets your debrid key. |
| `api.strem.io` | Only if you sign in to Stremio: signing in, on app start, and when you save or remove a title | Your email and password once (the password is never stored), then an encrypted-at-rest auth key to read your add-ons and library and to update the library. Requests never follow redirects. |
| `query.wikidata.org` | Only when the app isn't in English: showing posters and opening a title | Official titles in your language, a row of posters per request. Only IMDb ids and your language are sent, with a User-Agent naming the app. |
| `94c8cb9f702d-tmdb-addon.baby-beamup.club` | Only when the app isn't in English: opening a title | The same title's description and episode names in your language (TMDB add-on). Only the IMDb id and your language are sent. If it's slow or down, the title still comes from Wikidata and the rest stays in English. |
| `opensubtitles-v3.strem.io` | Playing a video | Subtitle lists (OpenSubtitles add-on) |
| Add-ons you install yourself | Same as above | Whatever their manifest offers |
| Image servers named by add-ons, e.g. `images.metahub.space` | Showing posters and backgrounds | Images. Only https images are loaded. |
| The stream servers in a link you play, e.g. your debrid service's download servers | Playing or downloading | The video itself |
| `api.real-debrid.com`, `api.alldebrid.com`, `www.premiumize.me`, `debrid-link.com`, `api.torbox.app`, `offcloud.com` | Only when you press **Test** | Checks that your key works. Only your own service is contacted. |
| `torrentio.strem.fun/configure` | Only when you open Torrentio's configure page | Torrentio's own settings page, in a locked-down WebView |
| `api.github.com`, `github.com`, GitHub's download servers | Only when you check for updates (or turn on the check at start) | New releases of this app |
| BitTorrent peers, trackers and the DHT | Only when you play a torrent with the built-in engine | Downloading the torrent. Your IP address is visible to other peers. |

## Where your data stays

- Everything stays on the device: history, library, settings. Backups and
  device transfer are switched off (`allowBackup=false`, empty extraction rules).
- Debrid keys, configured add-on URLs and download links are encrypted with
  AES-GCM using a key kept in the Android Keystore. They are never logged,
  never shown in full and only sent to the add-on or debrid service they
  belong to. If a request is redirected to another host, credentials and
  cookies are dropped.
- Responses are size-capped (manifest 1 MB, catalog 5 MB, meta, streams and
  subtitles 2 MB each) and add-on JSON is parsed forgivingly, so a broken
  add-on cannot crash the app.
- Only https is allowed. The single exception is the built-in torrent engine,
  which serves the video to the player on `127.0.0.1`, on a random port, behind
  a random token in the URL.
