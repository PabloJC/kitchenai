import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "admin.js"
                // 8080 is the Firestore emulator's port (firebase.json).
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(port = 5173)
            }
            // Tests run on Node: they cover logic, not the DOM, and CI has no headless browser.
            testTask { enabled = false }
        }
        nodejs {
            // Same as :shared: js-joda's zone rules load on the first test that names a zone.
            testTask { useMocha { timeout = "10s" } }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(projects.shared)
            implementation(compose.runtime)
            implementation(libs.compose.html.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.gitlive.firebase.app)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.functions)
            implementation(libs.gitlive.firebase.storage)
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
