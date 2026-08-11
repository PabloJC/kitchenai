import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
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
