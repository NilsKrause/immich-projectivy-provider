# Immich Wallpaper Provider per Projectivy Launcher

Plugin **wallpaper provider** per [Projectivy Launcher](https://play.google.com/store/apps/details?id=com.spocky.projengmenu)
che mostra le foto del tuo server [Immich](https://immich.app) self-hosted come sfondo del launcher
su Android TV / Fire TV (FireOS 7.x, Android 9, userspace a 32 bit).

## Perché serve un plugin dedicato

Projectivy scarica da sé l'URL del wallpaper, ma **non può inviare header personalizzati**.
Gli URL degli asset di Immich richiedono normalmente l'header `x-api-key`, quindi né Aerial Views né
Immich-Android-TV riescono a usare Immich come *wallpaper* (lo fanno solo come *screensaver*).

Questo plugin risolve il problema sfruttando il fatto che Immich accetta la **stessa API key anche
come query parameter `apikey`** (in alternativa all'header `x-api-key`). Il plugin restituisce quindi a
Projectivy degli **URL diretti Immich** già autenticati, e Projectivy scarica le foto da sé — esattamente
il meccanismo che il template ufficiale di Projectivy usa per i wallpaper di tipo `IMAGE` (un URL `http(s)`).

## Come funziona

1. Projectivy si collega al servizio del plugin e chiama `getWallpapers()`.
2. Il plugin interroga Immich (header `x-api-key`) per ottenere un elenco di asset
   (album scelto / preferiti / recenti / casuali).
3. Costruisce un URL di anteprima per ogni foto:
   `https://<server>/api/assets/<id>/thumbnail?size=preview&apikey=<chiave>`.
4. Restituisce a Projectivy la lista di questi URL; Projectivy li scarica, li mette nella sua
   cache e li ruota col **suo** intervallo, finché la lista scade dopo `itemsCacheDurationMillis`.

### Perché un URL diretto con `apikey` e non `content://`

| Opzione | Problema |
|---|---|
| URL Immich diretto senza chiave | richiede `x-api-key`, che Projectivy non può mandare |
| URL Immich con `?apikey=<chiave>` | ✅ funziona: l'API key viaggia nella query string, niente header |
| `file://` nella cache dell'app | la cache è privata dell'app; un'altra app (Projectivy) non può leggerla |
| `content://` da un `ContentProvider` esportato | non è garantito che il loader `IMAGE` di Projectivy lo supporti (l'esempio ufficiale usa solo URL `http(s)` e `android.resource://`) |

La query string `apikey` è supportata dal middleware di autenticazione di Immich
(`AuthService.validate()` legge `x-api-key` header **oppure** il query parameter `apikey`).

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
- `GET  /api/assets/{id}/thumbnail?size=preview&apikey=<chiave>` — anteprima (JPEG) per lo sfondo

Autenticazione: header **`x-api-key`** per le chiamate del plugin; query parameter **`apikey`**
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

- **`itemsCacheDurationMillis` = 30 min**: statico (dal manifest), non modificabile a runtime.
  Con questo valore Projectivy riusa la stessa lista di foto per 30 minuti prima di chiederla
  di nuovo al plugin.
- La **API key è esportata in chiaro** da `getPreferences()` (per il backup/ripristino delle
  impostazioni di Projectivy), nelle `SharedPreferences` del device e nella query string degli
  URL dei wallpaper: stesso livello di esposizione di qualsiasi app che salva una chiave.
- Il device è debole: la ricerca asset è un'unica chiamata veloce; il download delle anteprime
  lo fa Projectivy col proprio loader e la propria cache.
