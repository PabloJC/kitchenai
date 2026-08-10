import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // AGP 9: sustituye a androidTarget {} + al bloque android {} de nivel superior.
    androidLibrary {
        namespace = "com.kitchenai.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()

        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }

        withHostTest { }
    }

    // Compose Multiplatform 1.11 eliminó los targets Apple x86_64.
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

            // api: el framework de iOS necesita ver estos tipos
            api(libs.gitlive.firebase.app)
            api(libs.gitlive.firebase.common)
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.firestore)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            // GitLive declara com.google.firebase:* SIN versión y espera que el
            // consumidor aporte el BOM. Tiene que estar en este módulo, que es
            // donde vive la dependencia, y como `api` para que la restricción de
            // versiones llegue al classpath de :composeApp y :androidApp.
            api(project.dependencies.platform(libs.firebase.bom))

            implementation(libs.kotlinx.coroutines.android)
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
