import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

allprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    // extensions.configure y no `detekt { }` / `ktlint { }`: dentro de allprojects
    // esos accesores resuelven contra la extensión del proyecto RAÍZ, así que la
    // configuración nunca llegaba a :shared, :composeApp ni :androidApp.
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        parallel = true
    }

    extensions.configure<KtlintExtension> {
        // El generador de recursos de Compose escribe Res.kt bajo build/generated/
        // con nombres en minúscula que ktlint rechaza y no puede autocorregir.
        // Es código regenerado en cada build: analizarlo no aporta nada.
        filter {
            exclude { element -> element.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    tasks.withType<Detekt>().configureEach {
        exclude("**/build/**", "**/generated/**")
    }
}
