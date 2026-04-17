import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Load signing properties from local.properties (kept out of git)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}
fun localProp(key: String): String = localProperties.getProperty(key, "")

android {
    namespace = "fr.yvz.stopandgo"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.yvz.stopandgo"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val keyAliasProperty = localProp("signingConfigs.config.keyAlias")
        val storeFileProperty = localProp("signingConfigs.config.storeFile")
        if (keyAliasProperty.isNotEmpty() && storeFileProperty.isNotEmpty()) {
            create("release") {
                keyAlias = keyAliasProperty
                keyPassword = localProp("signingConfigs.config.keyPassword")
                storeFile = file(storeFileProperty)
                storePassword = localProp("signingConfigs.config.storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    kotlin {
        jvmToolchain(11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Media3 / ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    // Serialization
    implementation(libs.gson)

    // Image loading
    implementation(libs.coil.compose)

    // Google Play Billing
    implementation(libs.billing.ktx)

    // Reorderable LazyColumn
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
