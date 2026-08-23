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

# Javet - Keep all classes, methods, and fields required for JNI/reflection
-keep class com.caoccao.javet.** { *; }
-keep interface com.caoccao.javet.** { *; }
-keepclassmembers class com.caoccao.javet.** { *; }
-keep @interface com.caoccao.javet.annotations.**
-keepclassmembers class * {
    @com.caoccao.javet.annotations.** <methods>;
    @com.caoccao.javet.annotations.** <fields>;
}

# Keep LxSourceHost host callbacks (invoked by Javet via reflection)
-keep class com.caipan.music.online.LxSourceHost$* { *; }

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

# Tink（EncryptedSharedPreferences 依赖）引用的 errorprone 编译期注解，R8 全量模式下缺失，运行不需要
-dontwarn com.google.errorprone.annotations.**
