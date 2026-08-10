import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidLibrary {
        // Debe diferir del namespace de :androidApp (AGP 9 exige que sean únicos).
        namespace = "com.kitchenai.ui"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }

        // Sin esto los recursos de Compose no se empaquetan y la app crashea (CMP-9547).
        androidResources { enable = true }

        withHostTest { }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Xcode enlaza un solo framework: éste debe exportar también :shared.
            export(projects.shared)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api (no implementation): requisito para poder exportarlo al framework
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
// Tests de Kotlin/Native desactivados.
//
// GitLive declara linker options hacia los frameworks del SDK nativo de Firebase
// (FirebaseCore y compañía). Cuando Xcode compila la app, SPM los aporta; pero el
// binario de test de Kotlin/Native lo enlaza Gradle por su cuenta, sin acceso a
// ellos, y falla con "ld: framework 'FirebaseCore' not found".
//
// Los tests de commonTest siguen ejecutándose en JVM/Android, que es donde vive
// toda la lógica de dominio. El arreglo de fondo es separar :shared en dos módulos
// (domain puro sin Firebase + data), y entonces domain sí podrá testearse en iOS.
// Ver docs/infra.md.
// ---------------------------------------------------------------------------
tasks.matching { task ->
    task.name.startsWith("linkDebugTestIos") ||
        task.name.startsWith("linkReleaseTestIos") ||
        Regex("^ios[A-Za-z0-9]*Test$").matches(task.name)
}.configureEach {
    enabled = false
}
