import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.caipan.music"
    compileSdk = 36

    signingConfigs {
        create("release") {
            // 正式签名配置从本地 keystore/keystore.properties 读取（该文件已被 .gitignore 排除，不入库）
            val propsFile = rootProject.file("keystore/keystore.properties")
            if (propsFile.exists()) {
                val props = Properties()
                propsFile.inputStream().use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.caipan.music"
        minSdk = 31
        targetSdk = 36
        versionCode = 2804
        versionName = "2.804"
        // GitHub OAuth 配置：从 local.properties 读取，开发者自行注册 OAuth App 后填入
        // 格式: github.client.id=YOUR_CLIENT_ID
        //       github.token.proxy.url=https://your-server.com/github/access_token  (Web Flow 后端，可选)
        val localProps = rootProject.file("local.properties").takeIf { it.exists() }
            ?.let { Properties().apply { it.inputStream().use { s -> load(s) } } }
        val githubClientId = localProps?.getProperty("github.client.id") ?: ""
        val githubTokenProxyUrl = localProps?.getProperty("github.token.proxy.url") ?: ""
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$githubClientId\"")
        buildConfigField("String", "GITHUB_TOKEN_PROXY_URL", "\"$githubTokenProxyUrl\"")
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            // 插件作为外部 ZIP 分发，不写入 APK；Release 使用 R8 和资源压缩。
            isMinifyEnabled = true
            isShrinkResources = true
            // 正式签名（keystore/keystore.properties 读取，已被 .gitignore 排除）；
            // 新环境缺失该文件时回退 debug 签名，保证本地开发构建不中断
            signingConfig = if (rootProject.file("keystore/keystore.properties").exists())
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", layout.buildDirectory.dir("generated/musePluginAssets"))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val bundleGlassLab by tasks.registering(Zip::class) {
    from(rootProject.file("examples/glass-lab"))
    destinationDirectory.set(layout.buildDirectory.dir("generated/musePluginAssets/plugins"))
    archiveFileName.set("glass-lab.museplugin")
}

val bundleLanRemotePlugin by tasks.registering(Copy::class) {
    from(rootProject.file("examples/lan-remote-webui.museplugin"))
    into(layout.buildDirectory.dir("generated/musePluginAssets/plugins"))
}

// 第三方许可证全文随 APK 分发，供「关于 → 开源许可」页面在线阅读。
// AGPL-3.0 §5d 要求交互界面展示 Appropriate Legal Notices，
// GPL/AGPL 也要求分发时附带许可证副本，这里保证 APK 自带一份。
val bundleOpenSourceLicenses by tasks.registering(Copy::class) {
    from(rootProject.file("licenses")) {
        include("*.txt")
    }
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    from(rootProject.file("COPYRIGHT.md"))
    into(layout.buildDirectory.dir("generated/musePluginAssets/licenses"))
}

// res/font/sf_pro.ttf 是 Apple 专有字体，不入库（见 .gitignore 与 THIRD_PARTY_NOTICES.md 第 2 节）。
// 缺失时给出可操作的提示，而不是让 aapt 抛出难以理解的资源错误。
val checkMeloXFont by tasks.registering {
    val font = project.file("src/main/res/font/sf_pro.ttf")
    doFirst {
        if (!font.exists()) {
            throw GradleException(
                """
                缺少 app/src/main/res/font/sf_pro.ttf

                该字体为 Apple SF Pro，属专有授权，禁止随源码或软件产品再分发，
                因此未纳入版本库（见 THIRD_PARTY_NOTICES.md 第 2 节）。

                请任选其一：
                  1. 从 https://developer.apple.com/fonts/ 获取 SF Pro，
                     重命名为 sf_pro.ttf 放入 app/src/main/res/font/（仅限本地开发）
                  2. 放入任意可自由分发的字体并命名为 sf_pro.ttf
                     （推荐 Inter 或 Roboto Flex，公开发布构建应采用此方案）

                Missing app/src/main/res/font/sf_pro.ttf - Apple's SF Pro is proprietary and
                cannot be redistributed, so it is not tracked in git. Drop in your own copy,
                or any freely redistributable font renamed to sf_pro.ttf.
                """.trimIndent(),
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(bundleGlassLab, bundleLanRemotePlugin, bundleOpenSourceLicenses, checkMeloXFont)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    implementation("androidx.media:media:1.7.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.palette:palette-ktx:1.0.0")

    // Compose Liquid Glass / Backdrop effect
    implementation("io.github.kyant0:backdrop:2.0.0-alpha03")
    implementation("io.github.kyant0:shapes:1.2.0")
    implementation("io.github.kyant0:capsule:2.1.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.mozilla:rhino:1.9.1")
    implementation("com.caoccao.javet:javet-v8-android:5.0.10")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.security:security-crypto:1.0.0")

    testImplementation("junit:junit:4.13.2")
    // Android supplies org.json at runtime; JVM unit tests need a concrete implementation.
    testImplementation("org.json:json:20240303")
}

