plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    // No kotlin-android: AGP 9 ships built-in Kotlin support.
}

android {
    namespace = "com.kitchenai.app"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kitchenai.app"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // No applicationIdSuffix: it would change the package to com.kitchenai.app.debug,
            // and google-services.json only has com.kitchenai.app registered. Separate
            // Firebase projects (dev/prod) are a product-flavour problem, not a suffix one.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)

    // App Check. `firebase-appcheck` is declared explicitly because KitchenAiApplication
    // uses it: it would arrive transitively from the provider, but compiling against an
    // undeclared dependency is how an upstream repackaging breaks the build.
    //
    // Play Integrity ships in every variant; the debug provider stays in the debug
    // variant, where it cannot be used to bypass attestation with a registered token.
    // `appCheckProviderFactory()` picks the factory, one implementation per build type.
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // Analytics and Crashlytics: out for now.
    //
    // Crashlytics also needs its Gradle plugin, which injects the build ID into the APK.
    // Without it Crashlytics throws on init and takes FirebaseInitProvider down with it:
    // the app crashes before drawing anything.
    //
    // No code uses either yet. They come back in their own issue, with the plugin and the
    // R8 mapping upload configured, once there are testers. The BOM pinning their versions
    // arrives through `api` from :shared.
}
