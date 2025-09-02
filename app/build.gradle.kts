import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.android.broadcastassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.broadcastassistant"
        minSdk = 26
        //noinspection OldTargetApi
        targetSdk = 34

        // Versioning
        val versionMajor = 2
        val versionMinor = 1
        val versionPatch = 0

        versionCode = (versionMajor * 10000) + (versionMinor * 100) + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Keep debug symbols for native libraries
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
    }
}
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

    // Extra libraries
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

// Export APK tasks
val vNameProvider = provider { android.defaultConfig.versionName ?: "0.0.0" }

tasks.register<Copy>("exportDebugApk") {
    dependsOn("assembleDebug")

    val vName = vNameProvider.get()
    val apkDir = layout.buildDirectory.dir("outputs/apk/debug")

    from(apkDir.map { it.file("app-debug.apk") })
    into(layout.projectDirectory.dir("releases").dir(vName))
    rename { "app-debug-v${vName}.apk" }
}

tasks.register<Copy>("exportReleaseApk") {
    dependsOn("assembleRelease")

    val vName = vNameProvider.get()
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")

    from(apkDir.map { it.file("app-release.apk") })
    into(layout.projectDirectory.dir("releases").dir(vName))
    rename { "app-release-v${vName}.apk" }
}
