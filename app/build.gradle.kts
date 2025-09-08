import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Main Android configuration block.
 * Sets namespace, SDK versions, build types, Kotlin options, Compose features,
 * and packaging options for native libraries.
 */
//noinspection OldTargetApi
android {
    /** Application namespace (used for R class generation and code structure) */
    namespace = "com.android.broadcastassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.broadcastassistant"
        minSdk = 26
        targetSdk = 34

        /**
         * Versioning configuration.
         * versionCode is calculated as: major * 10000 + minor * 100 + patch
         * versionName is "major.minor.patch"
         */
        val versionMajor = 2
        val versionMinor = 2
        val versionPatch = 0

        versionCode = (versionMajor * 10000) + (versionMinor * 100) + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        /** Test instrumentation runner */
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            /** Keep full debug symbols in debug builds for native libraries */
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        release {
            /** Disable minification and apply ProGuard rules */
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            /** Strip debug symbols minimally to reduce APK size in release */
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        /** Enable Jetpack Compose */
        compose = true
    }

    /**
     * Packaging options for native libraries.
     * Keeps debug symbols for problematic prebuilt libraries to avoid stripping warnings.
     */
    packaging {
        jniLibs {
            // Tell Gradle to keep these libraries as-is
            keepDebugSymbols += listOf(
                "libandroidx.graphics.path.so",
                "libbarhopper_v3.so"
            )
        }
    }
}

/**
 * Dependencies for the project.
 * Includes core, lifecycle, Compose, navigation, testing, and third-party libraries.
 */
//noinspection UseTomlInstead
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.runtime.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
}

/**
 * Provider for the app version name.
 * Fallbacks to "0.0.0" if versionName is not set.
 */
val vNameProvider = provider { android.defaultConfig.versionName ?: "0.0.0" }

/**
 * Helper function to generate human-readable date for folder naming.
 * Format: Sep_04_2025
 */
fun getDateFolderName(): String {
    val sdf = SimpleDateFormat("MMM_dd_yyyy") // Month (short), day, year
    return sdf.format(Date())
}

/**
 * Task to export the Debug APK.
 * - Depends on `assembleDebug`.
 * - Copies the APK into a folder named by the current date.
 * - Renames the APK to include the version only.
 */
tasks.register<Copy>("exportDebugApk") {
    dependsOn("assembleDebug")

    val vName = vNameProvider.get()
    val dateFolder = getDateFolderName()
    val apkDir = layout.buildDirectory.dir("outputs/apk/debug")

    from(apkDir.map { it.file("app-debug.apk") })
    into(layout.projectDirectory.dir("releases").dir(dateFolder)) // Folder by date
    rename { "app-debug-v${vName}.apk" } // APK filename includes version only
}

/**
 * Task to export the Release APK.
 * - Depends on `assembleRelease`.
 * - Copies the APK into a folder named by the current date.
 * - Renames the APK to include the version only.
 */
tasks.register<Copy>("exportReleaseApk") {
    dependsOn("assembleRelease")

    val vName = vNameProvider.get()
    val dateFolder = getDateFolderName()
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")

    from(apkDir.map { it.file("app-release.apk") })
    into(layout.projectDirectory.dir("releases").dir(dateFolder)) // Folder by date
    rename { "app-release-v${vName}.apk" } // APK filename includes version only
}
