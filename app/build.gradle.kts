import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

val tor by configurations.creating

fun versionCodeEpoch(): Int = (Date().time / 1000).toInt()

fun gitCommit(): String {
    val process = ProcessBuilder("git", "rev-parse", "--short=8", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.waitFor()
    return process.inputStream.use { it.readBytes().decodeToString().trim() }
}

android {
    namespace = "org.onionshare.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.onionshare.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 26
        versionName = "0.3.0-beta"

        vectorDrawables {
            useSupportLibrary = true
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions += "releaseType"

    productFlavors {
        create("stable") {
            dimension = "releaseType"
        }
        create("fdroid") {
            dimension = "releaseType"
            applicationIdSuffix = ".fdroid"
            // version codes get multiplied by 10 and an ABI suffix gets added to the code
            // if 'splitApk' property is set
        }
        create("nightly") {
            dimension = "releaseType"
            applicationIdSuffix = ".nightly"
            versionCode = versionCodeEpoch()
            versionNameSuffix = " (${gitCommit()})"
        }
    }

    splits {
        abi {
            // can not be defined per flavor, so we use a property to turn this on for F-Droid
            isEnable = project.hasProperty("splitApk")
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/*",
                // Due to https://github.com/Kotlin/kotlinx.coroutines/issues/2023
                "META-INF/licenses/*",
                "**/attach_hotspot_windows.dll",
            )
        }
    }

    lint {
        disable += setOf("MissingTranslation", "ExtraTranslation", "MissingQuantity")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(libs.kotlin.reflect)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.material)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.security.crypto)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Tor
    implementation(libs.briar.onionwrapper.android)
    add(tor.name, libs.briar.tor.android)
    add(tor.name, libs.briar.lyrebird.android)
    implementation(libs.briar.moat.api)
    implementation(libs.okhttp)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.pebble)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.jul.to.slf4j)
    implementation(libs.logback.android)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.slf4j.jdk14)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.screengrab)
}

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            val baseAbiVersionCode = abiCodes[abi]
            if (baseAbiVersionCode != null) {
                output.versionCode.set(output.versionCode.map { 10 * it + baseAbiVersionCode })
            }
        }
    }
}

val torLibsDir = "src/main/jniLibs"

val cleanTorBinaries = tasks.register("cleanTorBinaries") {
    outputs.dir(torLibsDir)
    doLast {
        project.delete(project.fileTree(torLibsDir))
    }
}

tasks.named("clean") {
    dependsOn(cleanTorBinaries)
}

val unpackTorBinaries = tasks.register("unpackTorBinaries") {
    outputs.dir(torLibsDir)
    dependsOn(cleanTorBinaries)
    doLast {
        project.copy {
            from(tor.resolve().map { project.zipTree(it) })
            into(torLibsDir)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(unpackTorBinaries)
}

apply(from = "${rootProject.rootDir}/gradle/ktlint.gradle")
