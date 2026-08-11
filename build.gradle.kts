import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
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
        exclude("**/build/**", "**/generated/**")
    }

    // KMP keeps its code under src/<sourceSet>/kotlin; the default detekt task only reads
    // src/main/{java,kotlin}, so without this both modules analyse nothing and stay green.
    plugins.withId(rootProject.libs.plugins.kotlinMultiplatform.get().pluginId) {
        // Lazy on purpose: resolved any earlier, the hierarchy is still half-built and only
        // the leaf sets exist, so iosMain is silently dropped by the exists() filter.
        val kmpSourceDirs =
            provider {
                // The Compose resource generator registers its output under build/ as a
                // srcDir. The exclude() above drops those files from the analysis but not
                // from the task inputs, which makes Gradle fail on an undeclared dependency
                // once the directory exists — so they are dropped here instead.
                val generated = layout.buildDirectory.get().asFile
                extensions.getByType<KotlinMultiplatformExtension>()
                    .sourceSets
                    .flatMap { sourceSet -> sourceSet.kotlin.srcDirs }
                    .filter { dir -> dir.exists() && !dir.startsWith(generated) }
            }
        tasks.withType<Detekt>().configureEach { setSource(files(kmpSourceDirs)) }
    }
}
