plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.dependency.check) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.browserstack) apply false
    alias(libs.plugins.gradle.versions) apply false
    id("org.jetbrains.dokka") version "2.2.0"
}

subprojects {
    pluginManager.withPlugin("com.android.library") {
        apply(plugin = "org.jetbrains.dokka")
    }
    pluginManager.withPlugin("com.android.application") {
        apply(plugin = "org.jetbrains.dokka")
    }
}

dependencies {
    dokka(project(":app"))
}

dokka {
    dokkaPublications.html {
        moduleName.set("Secure File Manager")
        moduleVersion.set(
            providers.gradleProperty("VERSION_NAME").orElse("0.1.9-beta"),
        )
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
}

// The org.jetbrains.dokka root plugin applies the "base" plugin, which already
// registers a "clean" task that deletes rootProject.layout.buildDirectory.
