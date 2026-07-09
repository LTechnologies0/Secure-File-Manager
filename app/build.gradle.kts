import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.dependency.check)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.browserstack)
    alias(libs.plugins.gradle.versions)
}

fun getSecret(name: String): String {
    val props = Properties()
    return try {
        props.load(FileInputStream(File("secrets.properties")))
        props.getProperty(name) ?: ""
    } catch (_: Exception) {
        ""
    }
}

android {
    namespace = "ltechnologies.onionphone.securefilemanager"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ltechnologies.onionphone.securefilemanager"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 12
        versionName = "0.1.9-beta"
    }

    base {
        archivesName.set("secure-file-manager")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isJniDebuggable = true
            val debugEndpoint = project.findProperty("debugAgentEndpoint")?.toString().orEmpty()
            val debugSession = project.findProperty("debugAgentSession")?.toString().orEmpty()
            val sessionEndpoint = project.findProperty("debugSessionEndpoint")?.toString().orEmpty()
            val sessionId = project.findProperty("debugSessionId")?.toString().orEmpty()
            buildConfigField("String", "DEBUG_AGENT_ENDPOINT", "\"$debugEndpoint\"")
            buildConfigField("String", "DEBUG_AGENT_SESSION", "\"$debugSession\"")
            buildConfigField("String", "DEBUG_SESSION_ENDPOINT", "\"$sessionEndpoint\"")
            buildConfigField("String", "DEBUG_SESSION_ID", "\"$sessionId\"")
        }
        release {
            isMinifyEnabled = true
            isDebuggable = false
            buildConfigField("String", "DEBUG_AGENT_ENDPOINT", "\"\"")
            buildConfigField("String", "DEBUG_AGENT_SESSION", "\"\"")
            buildConfigField("String", "DEBUG_SESSION_ENDPOINT", "\"\"")
            buildConfigField("String", "DEBUG_SESSION_ID", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                rootProject.file("gradle/privacy-logging.pro"),
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}
apply(from = rootProject.file("gradle/abi-release.gradle"))

dependencyCheck {
    suppressionFile = file("dependency-suppression.xml").toString()
    scanConfigurations = configurations
        .filter { config ->
            !listOf("androidTest", "test", "debug").any { config.name.startsWith(it) } &&
                config.name.contains("DependenciesMetadata") &&
                (
                    listOf("api", "implementation", "runtimeOnly").any { config.name.startsWith(it) } ||
                        listOf("Api", "Implementation", "RuntimeOnly").any { config.name.contains(it) }
                    )
        }
        .map { it.name }
}

browserStackConfig {
    username = getSecret("BROWSERSTACK_USERNAME")
    accessKey = getSecret("BROWSERSTACK_ACCESSKEY")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.swiperefreshlayout)

    implementation(libs.material)
    implementation(libs.appintro)
    implementation(libs.argon2kt)
    implementation(libs.taptargetview)
    implementation(libs.zip4j)
    implementation(libs.sshj)
    implementation(libs.commons.net)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries)

    implementation(libs.glide)
    ksp(libs.glide.ksp)

    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    testImplementation("junit:junit:4.13.2")
}

apply(from = rootProject.file("gradle/release-signing.gradle"))
