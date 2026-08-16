import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidxRoom3) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

allprojects {
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    // extensions.configure rather than `detekt { }` / `ktlint { }`: inside allprojects
    // those accessors resolve against the ROOT project's extension, so the configuration
    // never reached :shared, :composeApp or :androidApp.
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        parallel = true
    }

    extensions.configure<KtlintExtension> {
        // The Compose resource generator writes Res.kt under build/generated/ with
        // lowercase names ktlint rejects and cannot autocorrect. Regenerated on every
        // build, so there is nothing to gain from analysing it.
        filter {
            exclude { element -> element.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    tasks.withType<Detekt>().configureEach {
        // A Spec, not a glob: detekt 2 registers the Compose resource generator's output as a
        // source root of its own, so the paths it walks are relative to build/ and never contain it.
        exclude { element -> element.file.absolutePath.contains("${File.separator}build${File.separator}") }
    }

    // The plain `detekt` task still reads src/main, which no module here uses. The analysis lives
    // in the per-source-set and per-compilation tasks the plugin registers, and only the latter
    // carry a classpath, so anything typed is silent unless both are run.
    val detektAll = tasks.register("detektAll") { dependsOn(tasks.withType<Detekt>()) }
    tasks.matching { task -> task.name == "check" }.configureEach { dependsOn(detektAll) }
}
