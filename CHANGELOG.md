# Changelog

## 0.0.3

- Allow selecting multiple albums: the album picker is now multi-select with a "Save selection" action, the album source searches all selected albums, and an existing single-album choice is migrated automatically.

## 0.0.2

- Allow unencrypted HTTP connections (`android:usesCleartextTraffic="true"`) so the plugin can talk to a local Immich server over `http://` (thanks to @WildFireFlum, PR #1).
- Warn before saving an unencrypted `http://` server URL: the Immich API key travels in the wallpaper URLs' query string, so over HTTP it would be readable by anyone on the network. The user can accept the risk or cancel.

## 0.0.1

- Initial versioned release: direct Immich URLs with the `apiKey` query parameter, landscape-only photo filter, versioned APK naming.
