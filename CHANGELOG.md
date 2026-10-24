# Changelog

## 0.0.4

- Album and Favorites sources now draw a random sample from the server on every refresh (`/api/search/random`) instead of always listing the newest files — multi-album selections rotate across all of them.
- Fix boolean settings ("Trust self-signed certificates", "Landscape photos only", "Full-resolution photos") not unchecking: a single radio-group action cannot be unchecked, so they now use real checkboxes.
- Test connection probes a full-resolution download and warns when the API key lacks the `asset.download` permission (required for `/api/assets/{id}/original`; without it Projectivy silently keeps old cached wallpapers).
- New "Full-resolution photos" toggle (off by default): serves the original asset file (`/api/assets/{id}/original`) instead of Immich's ~1440px preview thumbnail. Quality is paid for in device storage — Projectivy caches every wallpaper locally, so enable it plus a small "Images to cache" value only if storage allows (see README).
- Skip camera RAW files (Sony `.arw` and other RAW formats): a TV cannot decode a RAW original and their previews are lower quality.

## 0.0.3

- Allow selecting multiple albums: the album picker is now multi-select with a "Save selection" action, the album source searches all selected albums, and an existing single-album choice is migrated automatically.

## 0.0.2

- Allow unencrypted HTTP connections (`android:usesCleartextTraffic="true"`) so the plugin can talk to a local Immich server over `http://` (thanks to @WildFireFlum, PR #1).
- Warn before saving an unencrypted `http://` server URL: the Immich API key travels in the wallpaper URLs' query string, so over HTTP it would be readable by anyone on the network. The user can accept the risk or cancel.

## 0.0.1

- Initial versioned release: direct Immich URLs with the `apiKey` query parameter, landscape-only photo filter, versioned APK naming.
