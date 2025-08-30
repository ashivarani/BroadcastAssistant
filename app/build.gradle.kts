import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.android.broadcastassistant"
    //noinspection GradleDependency
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.broadcastassistant"
        minSdk = 26
        //noinspection OldTargetApi
        targetSdk = 34

        //Versioning (split into parts for easy tracking)
        val versionMajor = 2
        val versionMinor = 0
        val versionPatch = 0

        versionCode = (versionMajor * 10000) + (versionMinor * 100) + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
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

    // Extra libs
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    //noinspection UseTomlInstead
    implementation("com.google.code.gson:gson:2.13.1")
    //noinspection UseTomlInstead
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    //noinspection UseTomlInstead
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    //noinspection UseTomlInstead
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
}
val vNameProvider = provider { android.defaultConfig.versionName ?: "0.0.0" }
tasks.register<Copy>("exportDebugApk") {
    dependsOn("assembleDebug")

    val vName = vNameProvider.get()
    val apkDir = layout.buildDirectory.dir("outputs/apk/debug")

    from(apkDir.map { it.file("app-debug.apk") })

    into(layout.projectDirectory.dir("releases").dir(vName)) // ✅ per-version folder

  

    rename { "app-debug-v${vName}.apk" }
}

tasks.register<Copy>("exportReleaseApk") {
    dependsOn("assembleRelease")

    val vName = vNameProvider.get()
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")

    from(apkDir.map { it.file("app-release.apk") })

    into(layout.projectDirectory.dir("releases").dir(vName)) // ✅ per-version folder

    rename { "app-release-v${vName}.apk" }
}