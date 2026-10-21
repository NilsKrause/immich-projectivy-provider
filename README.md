> 📖 This README is available in **English** (below) and in **Italiano** (scroll to "Versione italiana").
> 📖 Questo README è disponibile in **inglese** (qui sotto) e in **italiano** (in fondo, sezione "Versione italiana").

# Immich Wallpaper Provider for Projectivy Launcher

**Wallpaper provider** plugin for [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu)
that shows photos from your self-hosted [Immich](https://immich.app) server as the launcher wallpaper
on Android TV / Fire TV (FireOS 7.x, Android 9, 32-bit userspace).

## Why a dedicated plugin is needed

Projectivy downloads the wallpaper URL itself, but it **cannot send custom headers**.
Immich asset URLs normally require the `x-api-key` header, so neither Aerial Views nor
Immich-Android-TV can use Immich as a *wallpaper* (they only work as a *screensaver*).

This plugin solves the problem by exploiting the fact that Immich also accepts the **same API key
as the `apiKey` query parameter** (as an alternative to the `x-api-key` header). The plugin therefore
returns **authenticated direct Immich URLs** to Projectivy, and Projectivy downloads the photos
itself — exactly the mechanism the official Projectivy template uses for `IMAGE` wallpapers (a plain
`http(s)` URL).

## How it works

1. Projectivy binds to the plugin service and calls `getWallpapers()`.
2. The plugin queries Immich (`x-api-key` header) to obtain a list of assets
   (chosen album / favorites / recent / random).
3. It builds a preview URL for each photo:
   `https://<server>/api/assets/<id>/thumbnail?size=preview&apiKey=<key>`.
4. It returns this list of URLs to Projectivy; Projectivy downloads them, stores them in its
   own cache, and rotates them on its own interval, until the list expires after
   `itemsCacheDurationMillis`.

### Why a direct URL with `apiKey` and not `content://`

| Option | Problem |
|---|---|
| Direct Immich URL without a key | requires `x-api-key`, which Projectivy cannot send |
| Immich URL with `?apiKey=<key>` | ✅ works: the API key travels in the query string, no header needed |
| `file://` in the app cache | the app cache is private; another app (Projectivy) cannot read it |
| `content://` from an exported `ContentProvider` | not guaranteed that Projectivy's `IMAGE` loader supports it (the official example only uses `http(s)` and `android.resource://`) |

The `apiKey` query string is supported by Immich's authentication middleware
(`AuthService.validate()` reads the `x-api-key` header **or** the `apiKey` query parameter).

**Documented trade-off**: the API key ends up in the query string of the URLs returned to
Projectivy, so it is stored together with the URLs in Projectivy's cache/settings. This is the
same exposure level the plugin already has by design: the key is exported in plain text by
`getPreferences()` (for Projectivy's settings backup/restore) and lives in the app's
`SharedPreferences`. The repository is public: **no server URL or API key is in the code**.

## Requirements

- **Projectivy Launcher Premium** (required for plugins).
- An **Immich** server (v3) reachable from the Fire TV on the local network.
- An **Immich API key** with these permissions: **`album.read`**, **`asset.read`**,
  **`asset.view`**, **`asset.download`**.

### Creating the API key

1. Immich → User settings → **API Keys** → *New API Key*.
2. Grant the permissions: `album.read`, `asset.read`, `asset.view`, `asset.download`.
3. Copy the key; you'll need it in the plugin's settings screen.

## Installation

Build with GitHub Actions (see below) or download the APK from the release:

```
https://github.com/<YOUR-USER>/<YOUR-REPO>/releases/latest/download/immich-provider-debug.apk
```

Install on the Fire TV:

```
adb install -r immich-provider-debug.apk
```

Then, in Projectivy: **Settings → Appearance → Wallpaper → Plugin → Immich Wallpaper Provider → Settings**.

## Settings

| Field | Meaning |
|---|---|
| **Server URL** | e.g. `https://immich.example.com` or `http://192.168.1.10:2283` (without `/api`) |
| **API key** | masked on screen (`••••abcd`); editable |
| **Test connection** | verifies URL + key; lists the album count and loads the album list |
| **Source** | Recent / Favorites / Random / Album |
| **Choose album** | opens the album list (requires *Test connection* or a valid connection) |
| **Images to cache** | how many photos to return to Projectivy on each refresh (default 20) |
| **Seconds per image** | see below — **advisory only** |
| **Trust self-signed certificates** | enable only if your server uses a self-signed certificate |
| **Landscape photos only** | skip portrait photos (they can break Projectivy's UI on some TVs); default: on |

> **Self-signed certificate note**: *Trust self-signed certificates* affects the **plugin's**
> HTTP client (listing albums, test connection, asset search). The actual wallpaper download is
> done by **Projectivy** using the direct URLs, so with a self-signed HTTPS server the wallpaper
> may not load even if "Test connection" works. On a local network, the most reliable approach is
> to use an `http://` URL (e.g. `http://192.168.1.10:2283`).

## The "seconds per image" question (honest take)

The rotation interval is **governed by Projectivy's timer**, not the plugin. The plugin only
controls:

- **which/how many** photos to return;
- `itemsCacheDurationMillis` (static manifest metadata) = how long Projectivy keeps the
  list before requesting it again.

This plugin's **"Seconds per image"** setting is therefore **advisory**: it is saved and shown, but
it cannot force a rotation faster than the launcher's minimum (**30 seconds** since v4.70). To align
them, set the same value in **Projectivy → Appearance → Wallpaper → change interval** (minimum 30 s).
Below 30 seconds the launcher's minimum still applies. *Nothing is simulated*: the value only takes
effect if aligned with Projectivy's setting.

## Immich APIs used (verified against the OpenAPI v3.x spec)

- `GET  /api/albums` — album list
- `POST /api/search/metadata` — asset search (recent, favorites, album; `filter` + `orderBy` + `size`)
- `POST /api/search/random` — random assets
- `GET  /api/assets/{id}/thumbnail?size=preview&apiKey=<key>` — preview (JPEG) for the wallpaper

Authentication: **`x-api-key`** header for the plugin's calls; **`apiKey`** query parameter for the
wallpaper URLs that Projectivy downloads. Parsing with `org.json` (no extra dependency). HTTP with
**OkHttp** (pure Java/Kotlin, no native `.so` library → compatible with the Fire TV's 32-bit userspace).

## Common errors

| Error | Likely cause |
|---|---|
| `Invalid API key (401)` | wrong or revoked key |
| `API key lacks permission (403)` | missing one of the `album.read` / `asset.read` / `asset.view` permissions |
| `Not found (404)` | wrong server URL |
| `Cannot reach server` | server off / network / wrong port |
| SSL handshake failed | self-signed certificate → use `http://` on the local network, or enable *Trust self-signed* (but see note above) |

## Building

### GitHub Actions (recommended — no local SDK required)

The `.github/workflows/build.yml` workflow:

1. sets up **JDK 17** and **Gradle 8.9** (`gradle/actions/setup-gradle`);
2. builds a **debug**-signed APK (`:app:assembleDebug` — installs without a keystore);
3. publishes it as a workflow **artifact** and as a **GitHub Release** asset.

On every push to the main branch it creates/updates a **`latest`** release; on a `v*` tag it creates
a release named after the tag. The direct link is therefore:

```
https://github.com/<user>/<repo>/releases/latest/download/immich-provider-debug.apk
```

### Locally

You need JDK 17 + Android SDK (platform 35). Then:

```
./gradlew :app:assembleDebug
```

## Known notes and limitations

- **Portrait wallpapers**: on some TVs a non-landscape wallpaper makes Projectivy mis-render its
  whole UI (garbled clock, icon trails). The plugin therefore returns landscape/square photos only
  by default ("Landscape photos only"); you can disable it in the settings.
- **`itemsCacheDurationMillis` = 30 min**: static (from the manifest), not changeable at runtime.
  With this value Projectivy reuses the same photo list for 30 minutes before requesting it again
  from the plugin.
- The **API key is exported in plain text** by `getPreferences()` (for Projectivy's settings
  backup/restore), in the device's `SharedPreferences` and in the wallpaper URLs' query string:
  the same exposure level as any app that stores a key.
- The device is weak: the asset search is a single fast call; the preview download is done by
  Projectivy with its own loader and cache.

---

# Immich Wallpaper Provider per Projectivy Launcher

Plugin **wallpaper provider** per [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu)
che mostra le foto del tuo server [Immich](https://immich.app) self-hosted come sfondo del launcher
su Android TV / Fire TV (FireOS 7.x, Android 9, userspace a 32 bit).

## Perché serve un plugin dedicato

Projectivy scarica da sé l'URL del wallpaper, ma **non può inviare header personalizzati**.
Gli URL degli asset di Immich richiedono normalmente l'header `x-api-key`, quindi né Aerial Views né
Immich-Android-TV riescono a usare Immich come *wallpaper* (lo fanno solo come *screensaver*).

Questo plugin risolve il problema sfruttando il fatto che Immich accetta la **stessa API key anche
come query parameter `apiKey`** (in alternativa all'header `x-api-key`). Il plugin restituisce quindi a
Projectivy degli **URL diretti Immich** già autenticati, e Projectivy scarica le foto da sé — esattamente
il meccanismo che il template ufficiale di Projectivy usa per i wallpaper di tipo `IMAGE` (un URL `http(s)`).

## Come funziona

1. Projectivy si collega al servizio del plugin e chiama `getWallpapers()`.
2. Il plugin interroga Immich (header `x-api-key`) per ottenere un elenco di asset
   (album scelto / preferiti / recenti / casuali).
3. Costruisce un URL di anteprima per ogni foto:
   `https://<server>/api/assets/<id>/thumbnail?size=preview&apiKey=<chiave>`.
4. Restituisce a Projectivy la lista di questi URL; Projectivy li scarica, li mette nella sua
   cache e li ruota col **suo** intervallo, finché la lista scade dopo `itemsCacheDurationMillis`.

### Perché un URL diretto con `apiKey` e non `content://`

| Opzione | Problema |
|---|---|
| URL Immich diretto senza chiave | richiede `x-api-key`, che Projectivy non può mandare |
| URL Immich con `?apiKey=<chiave>` | ✅ funziona: l'API key viaggia nella query string, niente header |
| `file://` nella cache dell'app | la cache è privata dell'app; un'altra app (Projectivy) non può leggerla |
| `content://` da un `ContentProvider` esportato | non è garantito che il loader `IMAGE` di Projectivy lo supporti (l'esempio ufficiale usa solo URL `http(s)` e `android.resource://`) |

La query string `apiKey` è supportata dal middleware di autenticazione di Immich
(`AuthService.validate()` legge `x-api-key` header **oppure** il query parameter `apiKey`).

**Trade-off documentato**: l'API key finisce nella query string degli URL restituiti a
Projectivy, quindi è salvata insieme agli URL nella cache/impostazioni di Projectivy. È lo
stesso livello di esposizione che il plugin ha già per design: la chiave è esportata in chiaro
da `getPreferences()` (per il backup/ripristino delle impostazioni di Projectivy) e risiede nelle
`SharedPreferences` dell'app. Il repo è pubblico: **nessun URL del server né API key nel codice**.

## Requisiti

- **Projectivy Launcher Premium** (necessario per i plugin).
- Un server **Immich** (v3) raggiungibile dal Fire TV sulla rete locale.
- Una **API key Immich** con questi permessi: **`album.read`**, **`asset.read`**,
  **`asset.view`**, **`asset.download`**.

### Creare la API key

1. Immich → Impostazioni utente → **API Keys** → *New API Key*.
2. Concedi i permessi: `album.read`, `asset.read`, `asset.view`, `asset.download`.
3. Copia la chiave; servirà nella schermata di configurazione del plugin.

## Installazione

Compila con GitHub Actions (vedi sotto) oppure scarica l'APK dalla release:

```
https://github.com/<TUO-UTENTE>/<TUO-REPO>/releases/latest/download/immich-provider-debug.apk
```

Installa sul Fire TV:

```
adb install -r immich-provider-debug.apk
```

Poi, in Projectivy: **Impostazioni → Aspetto → Wallpaper → Plugin → Immich Wallpaper Provider → Impostazioni**.

## Impostazioni

| Campo | Significato |
|---|---|
| **Server URL** | es. `https://immich.example.com` oppure `http://192.168.1.10:2283` (senza `/api`) |
| **API key** | mascherata a schermo (`••••abcd`); modificabile |
| **Test connection** | verifica URL + chiave; elenca il numero di album e carica l'elenco album |
| **Sorgente** | Recenti / Preferiti / Casuali / Album |
| **Choose album** | apre l'elenco album (richiede *Test connection* o una connessione valida) |
| **Images to cache** | quante foto restituire a Projectivy a ogni refresh (default 20) |
| **Seconds per image** | vedi sotto — **solo indicativo** |
| **Trust self-signed certificates** | abilita solo se il tuo server usa un certificato autofirmato |
| **Landscape photos only** | salta le foto verticali (possono rompere la UI di Projectivy su alcune TV); default: attivo |

> **Nota sul certificato autofirmato**: la voce *Trust self-signed certificates* agisce sul
> client HTTP **del plugin** (elencare album, test connessione, ricerca asset). Il download
> effettivo dei wallpaper lo fa **Projectivy** con i suoi URL diretti, quindi con un server
> HTTPS autofirmato il wallpaper potrebbe non caricarsi anche se "Test connection" funziona.
> Su una rete locale, l'approccio più affidabile è usare l'URL `http://` (es. `http://192.168.1.10:2283`).

## La questione dei "secondi per immagine" (lettura onesta)

L'intervallo di rotazione **lo governa il timer di Projectivy**, non il plugin.
Il plugin controlla solo:

- **quali/quante** foto restituire;
- `itemsCacheDurationMillis` (metadato statico nel manifest) = quanto Projectivy tiene la
  lista prima di richiederla di nuovo.

L'impostazione **"Seconds per image"** di questo plugin è quindi **advisory**: viene salvata
e mostrata, ma non può forzare una rotazione più veloce del minimo del launcher
(**30 secondi** dalla v4.70). Per farla combaciare, imposta lo stesso valore in
**Projectivy → Aspetto → Wallpaper → intervallo di cambio** (minimo 30 s). Sotto i 30 secondi
vale comunque il minimo del launcher. *Non viene simulato nulla*: il valore ha effetto solo
se allineato all'impostazione di Projectivy.

## API Immich usate (verificate sullo spec OpenAPI v3.x)

- `GET  /api/albums` — elenco album
- `POST /api/search/metadata` — ricerca asset (recenti, preferiti, album; filtro `filter` + `orderBy` + `size`)
- `POST /api/search/random` — asset casuali
- `GET  /api/assets/{id}/thumbnail?size=preview&apiKey=<chiave>` — anteprima (JPEG) per lo sfondo

Autenticazione: header **`x-api-key`** per le chiamate del plugin; query parameter **`apiKey`**
per gli URL dei wallpaper che scarica Projectivy. Parsing con `org.json` (nessuna dipendenza
extra). HTTP con **OkHttp** (puro Java/Kotlin, nessuna libreria nativa `.so` → compatibile con
l'userspace a 32 bit del Fire TV).

## Errori comuni

| Errore | Causa probabile |
|---|---|
| `Invalid API key (401)` | chiave errata o revocata |
| `API key lacks permission (403)` | manca uno dei permessi `album.read` / `asset.read` / `asset.view` |
| `Not found (404)` | URL del server errato |
| `Cannot reach server` | server spento / rete / porta sbagliata |
| handshake SSL fallito | certificato autofirmato → usa `http://` in rete locale, o attiva *Trust self-signed* (ma vedi nota sopra) |

## Compilazione

### GitHub Actions (consigliato — nessun SDK richiesto in locale)

Il workflow `.github/workflows/build.yml`:

1. imposta **JDK 17** e **Gradle 8.9** (`gradle/actions/setup-gradle`);
2. compila un APK firmato in **debug** (`:app:assembleDebug` — si installa senza keystore);
3. lo pubblica come **artifact del workflow** e come **asset di una GitHub Release**.

Su ogni push al ramo principale crea/aggiorna una release **`latest`**; su un tag `v*` crea
una release col nome del tag. Il link diretto è quindi:

```
https://github.com/<utente>/<repo>/releases/latest/download/immich-provider-debug.apk
```

### In locale

Serve JDK 17 + Android SDK (platform 35). Poi:

```
./gradlew :app:assembleDebug
```

## Note e limiti noti

- **Wallpaper verticali**: su alcune TV un wallpaper non orizzontale fa disegnare male l'intera UI
  di Projectivy (orologio sfasato, scie sulle icone). Per questo il plugin restituisce di default
  solo foto orizzontali/quadrate ("Landscape photos only"); puoi disattivarlo nelle impostazioni.
- **`itemsCacheDurationMillis` = 30 min**: statico (dal manifest), non modificabile a runtime.
  Con questo valore Projectivy riusa la stessa lista di foto per 30 minuti prima di chiederla
  di nuovo al plugin.
- La **API key è esportata in chiaro** da `getPreferences()` (per il backup/ripristino delle
  impostazioni di Projectivy), nelle `SharedPreferences` del device e nella query string degli
  URL dei wallpaper: stesso livello di esposizione di qualsiasi app che salva una chiave.
- Il device è debole: la ricerca asset è un'unica chiamata veloce; il download delle anteprime
  lo fa Projectivy col proprio loader e la propria cache.
