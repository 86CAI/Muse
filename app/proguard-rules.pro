# Rhino also ships an optional desktop JavaBeans converter. Android does not
# provide java.beans, and Muse does not call that converter.
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**

# Keep ALL Rhino runtime classes — the interpreted engine loads dozens of
# internal types (NativeString, NativeNumber, Wrapper, etc.) at runtime.
# Selective keep rules miss internal dependencies and cause silent failures.
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.optimizer.**

# WebView bridge methods are invoked by JavaScript through reflection.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# androidx.webkit (WebViewAssetLoader, WebMessageListener)
-keep class androidx.webkit.** { *; }
-dontwarn androidx.webkit.**

# Keep plugin host API dispatch (accessed via ViewModel → PluginManager)
-keep class com.caipan.music.viewmodel.MusicViewModel { *; }
-keep class com.caipan.music.ui.components.PluginWebUiScreen { *; }
-keep class com.caipan.music.plugin.PluginManager { *; }
-keep class com.caipan.music.plugin.JsMusePlugin { *; }
-keep class com.caipan.music.plugin.PluginPackageInstaller { *; }

# Keep muse glass config (accessed via ViewModel)
-keep class com.caipan.music.ui.components.MuseGlassConfig { *; }
-keep class com.caipan.music.ui.components.MuseGlassConfigStore { *; }
