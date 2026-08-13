import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // AGP 9: replaces androidTarget {} plus the top-level android {} block.
    androidLibrary {
        namespace = "com.kitchenai.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }

        withHostTest { }
    }

    // Compose Multiplatform 1.11 dropped the Apple x86_64 targets.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)

            // api: the iOS framework needs to see these types
            api(libs.gitlive.firebase.app)
            api(libs.gitlive.firebase.common)
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.firestore)
            api(libs.gitlive.firebase.functions)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            // GitLive declares com.google.firebase:* WITHOUT a version and expects the
            // consumer to supply the BOM. It belongs in this module, where the dependency
            // lives, and as `api` so the version constraint reaches :composeApp and
            // :androidApp.
            api(project.dependencies.platform(libs.firebase.bom))

            implementation(libs.kotlinx.coroutines.android)
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
