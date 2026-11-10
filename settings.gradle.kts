rootProject.name = "KitchenAI"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":shared") // domain + data, Kotlin puro
include(":composeApp") // UI Compose Multiplatform (biblioteca KMP)
include(":androidApp") // entry point Android; AGP 9 lo exige separado
include(":admin") // the catalogue admin web, Kotlin/JS (#399)
