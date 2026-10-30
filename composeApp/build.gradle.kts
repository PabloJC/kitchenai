import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Type-safe navigation routes are @Serializable: the library reads them through their serializer.
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.aboutLibraries)
}

kotlin {
    androidLibrary {
        // Must differ from :androidApp's namespace (AGP 9 requires them to be unique).
        namespace = "com.kitchenai.ui"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }

        // Without this, Compose resources are not packaged and the app crashes (CMP-9547).
        androidResources { enable = true }

        withHostTest { }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Xcode links a single framework: this one must export :shared too.
            export(projects.shared)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: required to export it to the framework
            api(projects.shared)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        androidMain.dependencies {
            // The @Preview annotation only, not the tooling runtime: Compose Multiplatform
            // renders previews on Android alone, so they live in androidMain.
            implementation(libs.compose.ui.tooling.preview)

            // Google Sign-In via Credential Manager: GoogleSignInLauncher.android.kt.
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.google.id)

            // The notification permission prompt: NotificationPermission.android.kt.
            implementation(libs.androidx.activity.compose)

            // The home-screen shopping widget: widget/ShoppingListWidget.kt.
            implementation(libs.androidx.glance.appwidget)

            // The daily expiry reminder: platform/ExpiryReminderWorker.kt.
            implementation(libs.androidx.work.runtime)

            // The privacy policy and photo sources: platform/OpenUrl.android.kt.
            implementation(libs.androidx.browser)

            // Coil's transport for recipe photographs: designsystem/component/RecipeImage.kt.
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            // Google Sign-In via Google's own iOS SDK, wrapped for Kotlin/Native rather than
            // hand-cinteroped: see the `kmpauth` version comment in the catalog.
            implementation(libs.kmpauth.google)

            // Coil's transport for recipe photographs: designsystem/component/RecipeImage.kt.
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

// ---------------------------------------------------------------------------
// Kotlin/Native tests disabled.
//
// GitLive declares linker options against the native Firebase frameworks. Xcode gets them
// from SPM, but Gradle links the Kotlin/Native test binary on its own, without access, and
// fails with "ld: framework 'FirebaseCore' not found".
//
// commonTest still runs on JVM/Android, where the domain logic lives. The real fix is
// splitting :shared into two modules (pure domain + data). See docs/infra.md.
// ---------------------------------------------------------------------------
tasks.matching { task ->
    task.name.startsWith("linkDebugTestIos") ||
        task.name.startsWith("linkReleaseTestIos") ||
        Regex("^ios[A-Za-z0-9]*Test$").matches(task.name)
}.configureEach {
    enabled = false
}

// ---------------------------------------------------------------------------
// Open-source licences (#347).
//
// Collected from this build's own dependencies on every build, never committed, so the list
// cannot fall behind the catalog. Offline: the build must not depend on GitHub's API. The JSON
// joins the hand-written resources in one generated folder, which Compose then packages.
// ---------------------------------------------------------------------------
aboutLibraries {
    offlineMode = true
    collect { fetchRemoteLicense = false }
    export { excludeFields.addAll("developers", "funding") }
}

val commonResourcesDir = layout.buildDirectory.dir("generated/commonComposeResources")

val commonComposeResources =
    tasks.register<Sync>("assembleCommonComposeResources") {
        from("src/commonMain/composeResources")
        from(tasks.named("exportLibraryDefinitions")) { into("files") }
        into(commonResourcesDir)
    }

// The generated class defaults to a package derived from the module name, which is neither this
// module's package nor anything a reader would guess.
compose.resources {
    packageOfResClass = "com.kitchenai.ui.resources"
    // Through the task, so Compose waits for it; a plain directory would skip the dependency.
    customDirectory("commonMain", commonComposeResources.map { commonResourcesDir.get() })
}
