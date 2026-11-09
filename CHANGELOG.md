# Changelog

What changed in each version. A version's section becomes its release notes on GitHub and in the
apps' update dialog: `./BUILD.sh` publishes it, and refuses to publish a version without one.

## 1.17.2

- "More like this" shows up straight away: the row is there with placeholders as soon as a title opens, the titles from the same genres fill it within moments, and the closest matches from Wikidata move to the front when they're in. The Wikidata lookup starts the moment the title opens, and for the titles on Home's banner and the first ones to continue watching even before that; once found, a title's matches show at once the next time
- The Wikidata lookup is given as long as Wikidata needs, instead of being cut off after 15 seconds and started again on every visit
- Download from the long-press menu (right-click on Linux): a movie, or for a series this season, the whole series, or pick episodes. Episodes have Download in their own menu too
- On a series' page the watched button marks the season on show; long-press the picture at the top (click the poster on Linux) to mark the whole series. Marking as watched leaves episodes that aren't out yet alone, so they show as new when they come
- A "NEW" ribbon across the poster's corner when every episode of a series was watched and a new one is out, in Library and Discover, for saved series and series you've watched
- Related movies: a film's page has a row with the rest of its series and its other versions (remakes, other adaptations), oldest first
- Linux: the Live tab looks like the Android one, with chips for the source and genre above a grid

## 1.17.1

- Linux: an update is only installed when it's signed with the app's own Linux key (each release has a .flatpak.sig next to the bundle). Versions up to 1.17.0 update to it as before
- Android: an update signed with a new key is accepted when this app's key approved the new one, the way Android itself allows a key change
