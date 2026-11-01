plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    // Injects the build id Crashlytics checks on start; without it the SDK crashes the app at launch.
    alias(libs.plugins.firebaseCrashlytics)
    // No kotlin-android: AGP 9 ships built-in Kotlin support.
}

android {
    namespace = "com.kitchenai.app"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kitchenai.app"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        // From the build's caller, never edited by hand: CI passes its run number, so every
        // uploaded build is newer than the last (Play and App Distribution both refuse a repeat).
        // Present but not a number is a typo, not "use 1": a repeat versionCode is refused on upload.
        versionCode =
            providers.gradleProperty("kitchenai.versionCode").orNull?.let { raw ->
                raw.toIntOrNull()?.takeIf { it > 0 }
                    ?: error("kitchenai.versionCode must be a positive integer, got '$raw'.")
            } ?: 1
        versionName = providers.gradleProperty("kitchenai.versionName").orNull?.takeIf { it.isNotBlank() } ?: "0.1.0"

        // The deployment coordinate reaches the app from here rather than from a constant in
        // :shared. Absent rather than defaulted: a wrong region is a call that reaches nothing,
        // so the build should stop instead of picking one.
        // Blank, not just absent: an empty override reaches the SDK as no region at all, which
        // is the silent failure this whole change exists to remove.
        val functionsRegion =
            providers.gradleProperty("kitchenai.functionsRegion").orNull
                ?.takeIf { it.isNotBlank() }
                ?: error("kitchenai.functionsRegion is missing or blank. See gradle.properties.")
        buildConfigField("String", "FUNCTIONS_REGION", "\"$functionsRegion\"")

        // Blank-tolerant, unlike functionsRegion above: nothing calls Google Sign-In yet (#188),
        // so a missing value here must not fail every build the way a missing region does.
        val googleWebClientId = providers.gradleProperty("kitchenai.googleWebClientId").orNull.orEmpty()
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")

        // Blank-tolerant too: About hides its Privacy policy row until a policy is published (#355).
        val privacyPolicyUrl = providers.gradleProperty("kitchenai.privacyPolicyUrl").orNull.orEmpty()
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The upload key lives outside the repository (docs/release.md › Android signing). With
    // the four properties absent the release build stays unsigned, which is what CI and every
    // contributor without the key get: it still proves the minified build works.
    val uploadKey =
        listOf("uploadStoreFile", "uploadStorePassword", "uploadKeyAlias", "uploadKeyPassword")
            .associateWith { name -> providers.gradleProperty("kitchenai.$name").orNull?.takeIf { it.isNotBlank() } }
    val canSign = uploadKey.values.all { it != null }
    // Some but not all is a misspelt name: an unsigned build would only be noticed at upload.
    if (!canSign && uploadKey.values.any { it != null }) {
        val missing = uploadKey.filterValues { it == null }.keys.joinToString { "kitchenai.$it" }
        error("Release signing is half configured; missing $missing. See docs/release.md.")
    }
    signingConfigs {
        if (canSign) {
            create("upload") {
                storeFile = file(uploadKey.getValue("uploadStoreFile")!!)
                storePassword = uploadKey.getValue("uploadStorePassword")
                keyAlias = uploadKey.getValue("uploadKeyAlias")
                keyPassword = uploadKey.getValue("uploadKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSign) signingConfig = signingConfigs.getByName("upload")
            manifestPlaceholders["crashlyticsCollectionEnabled"] = true
        }
        debug {
            // Off from process start, not from onCreate: a debug crash never reaches the console.
            manifestPlaceholders["crashlyticsCollectionEnabled"] = false
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
    implementation(libs.androidx.core.splashscreen)
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

    // Declared although :shared's GitLive wrapper brings it: the plugin above configures it here.
    implementation(libs.firebase.crashlytics)
}

// Off unless asked for: CI builds against a template google-services.json, whose upload would fail.
// The release workflow sets it, so every tester build has a mapping to deobfuscate its crashes.
val uploadMapping = providers.gradleProperty("kitchenai.crashlyticsUploadMapping").orNull == "true"
tasks.matching { it.name.startsWith("uploadCrashlyticsMappingFile") }.configureEach { enabled = uploadMapping }
