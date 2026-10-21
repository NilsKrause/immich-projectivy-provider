package tv.projectivy.plugin.wallpaperprovider.immich

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.util.Log
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

/**
 * Projectivy wallpaper-provider plugin. Projectivy binds to this service and calls
 * [getWallpapers] to obtain the list of wallpapers to rotate.
 *
 * Immich image URLs require the `x-api-key` header, which Projectivy cannot send when it
 * downloads a wallpaper by URL. Immich also accepts the same API key as the `apiKey`
 * query parameter, so this service returns **direct Immich thumbnail URLs** and lets
 * Projectivy download them itself — the exact mechanism the official Projectivy sample
 * uses for `IMAGE` wallpapers (a plain http(s) URL). No local cache or exported
 * ContentProvider is needed.
 */
class WallpaperProviderService : Service() {

    private lateinit var client: ImmichClient

    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
        client = ImmichClient()
    }

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            assertCallerIsLauncher()
            return buildWallpapers()
        }

        override fun getPreferences(): String {
            assertCallerIsLauncher()
            return PreferencesManager.export()
        }

        override fun setPreferences(params: String) {
            assertCallerIsLauncher()
            PreferencesManager.import(params)
        }
    }

    /**
     * Rejects binder calls from anything but Projectivy.
     *
     * This service must be `exported` for Projectivy to bind it, and `android:permission`
     * is not an option because Projectivy holds no permission of ours - so without a check
     * here, every method is reachable by any app on the device. All three are sensitive,
     * not just the obvious one: [getPreferences] returns the Immich API key verbatim,
     * [setPreferences] can repoint `server_url` and switch on `trust_ssl` (enabling the
     * trust-all X509TrustManager), and [getWallpapers] returns URLs that carry the key in
     * their query string. A UID comparison is the whole fix; package visibility for the
     * lookup is already declared via `<queries>` in the manifest.
     *
     * `getApplicationInfo().uid` rather than `getPackageUid()`, which needs API 24 > minSdk.
     */
    private fun assertCallerIsLauncher() {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return
        val allowedUid = try {
            packageManager.getApplicationInfo(PROJECTIVY_PACKAGE, 0).uid
        } catch (e: Exception) {
            Log.w(TAG, "$PROJECTIVY_PACKAGE not installed; rejecting uid $callingUid")
            -1
        }
        if (callingUid != allowedUid) {
            throw SecurityException("uid $callingUid is not $PROJECTIVY_PACKAGE")
        }
    }

    private fun buildWallpapers(): List<Wallpaper> {
        if (!PreferencesManager.isConfigured) {
            Log.i(TAG, "Not configured; returning no wallpapers")
            return emptyList()
        }

        val baseUrl = client.normalizeBaseUrl(PreferencesManager.serverUrl)
        val apiKey = PreferencesManager.apiKey
        val trustSsl = PreferencesManager.trustSsl

        // Only the asset list is fetched here (one quick request). Projectivy downloads the
        // image bytes itself from the returned URLs on its own threads, so this binder call
        // stays fast even on slow networks.
        val assets = try {
            client.fetchAssets(
                baseUrl, apiKey, trustSsl,
                PreferencesManager.source, PreferencesManager.albumId, PreferencesManager.imageCount
            )
        } catch (e: ImmichException) {
            Log.w(TAG, "fetchAssets failed (HTTP ${e.httpCode}): ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "fetchAssets failed: ${e.message}", e)
            emptyList()
        }

        val wallpapers = assets.shuffled().map { asset ->
            Wallpaper(
                uri = client.thumbnailUrl(baseUrl, apiKey, asset.id),
                type = WallpaperType.IMAGE,
                displayMode = WallpaperDisplayMode.CROP,
                title = asset.fileName.takeIf { it.isNotBlank() }
            )
        }

        Log.i(TAG, "Returning ${wallpapers.size} wallpapers")
        return wallpapers
    }

    companion object {
        private const val TAG = "ImmichWallpaper"
        private const val PROJECTIVY_PACKAGE = "com.spocky.projengmenu"
    }
}
