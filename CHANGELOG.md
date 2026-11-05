# Changelog

What changed in each version. A version's section becomes its release notes on GitHub and in the
apps' update dialog: `./BUILD.sh` publishes it, and refuses to publish a version without one.

## 1.17.1

- Linux: an update is only installed when it's signed with the app's own Linux key (each release has a .flatpak.sig next to the bundle). Versions up to 1.17.0 update to it as before
- Android: an update signed with a new key is accepted when this app's key approved the new one, the way Android itself allows a key change
