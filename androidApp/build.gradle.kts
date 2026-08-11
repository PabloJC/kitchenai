plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    // Sin kotlin-android: AGP 9 trae soporte de Kotlin integrado.
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
            // Sin applicationIdSuffix: cambiaría el package a com.kitchenai.app.debug
            // y google-services.json sólo tiene registrado com.kitchenai.app.
            // Cuando existan proyectos Firebase separados (dev/prod) esto se resuelve
            // con product flavors y un google-services.json por flavor, no con un sufijo.
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

    // App Check. `firebase-appcheck` se declara explícito porque lo usa
    // KitchenAiApplication: llegaría transitivo desde el proveedor, pero
    // compilar contra una dependencia que no se declara es cómo un cambio de
    // empaquetado río arriba rompe el build sin tocar nada aquí.
    //
    // Play Integrity va en todas las variantes; el proveedor de debug se queda
    // en la variante debug y no entra en el APK de release, donde permitiría
    // saltarse la atestación con un token registrado. La factoría concreta la
    // elige `appCheckProviderFactory()`, con una implementación por build type
    // en src/debug y src/release.
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    // Analytics y Crashlytics: fuera por ahora.
    //
    // Crashlytics necesita además su plugin de Gradle (com.google.firebase.crashlytics),
    // que inyecta el build ID en el APK. Sin él, Crashlytics lanza al inicializarse y
    // tumba FirebaseInitProvider entero: la app crashea antes de pintar nada.
    //
    // Ninguna de las dos la usa ningún código todavía. Se añadirán en su propia issue,
    // con el plugin y la subida del mapping de R8 configurados, cuando haya testers.
    // El BOM que fija sus versiones llega vía `api` desde :shared.
}
