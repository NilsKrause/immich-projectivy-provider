# Keep Wallpaper class and its Parcelable implementation for AIDL/IPC
-keep class tv.projectivy.plugin.wallpaperprovider.api.Wallpaper { *; }

# Keep Event class hierarchy (uses reflection for CREATOR field lookup)
-keep class tv.projectivy.plugin.wallpaperprovider.api.Event { *; }
-keep class tv.projectivy.plugin.wallpaperprovider.api.Event$* { *; }

# Keep the AIDL service interface and its generated Stub (used by Projectivy over Binder)
-keep class tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService { *; }
-keep class tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService$Stub { *; }
