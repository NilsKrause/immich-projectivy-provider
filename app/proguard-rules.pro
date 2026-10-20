# Keep the plugin service referenced from the AndroidManifest (defensive for release builds).
-keep class tv.projectivy.plugin.wallpaperprovider.immich.WallpaperProviderService { *; }

# The api module ships its own consumer rules (keeps Wallpaper + Event + AIDL Stub).
