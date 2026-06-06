import java.util.Properties
import java.io.FileInputStream

val secretsFile = rootProject.file("secrets.properties")
val secretsProperties = Properties().apply {
    if (secretsFile.exists()) {
        load(FileInputStream(secretsFile))
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.hermesbridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hermesbridge"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("String", "API_BASE_URL", "\"${secretsProperties.getProperty("API_BASE_URL", "https://agent.pitythepool.com/")}\"")
        buildConfigField("String", "AGENT_ENDPOINT", "\"${secretsProperties.getProperty("AGENT_ENDPOINT", "ask")}\"")
        buildConfigField("String", "DEVICE_ID", "\"${secretsProperties.getProperty("DEVICE_ID", "pixel10a-dev-001")}\"")
        buildConfigField("String", "API_KEY", "\"${secretsProperties.getProperty("API_KEY", "")}\"")
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"${secretsProperties.getProperty("PICOVOICE_ACCESS_KEY", "")}\"")

        manifestPlaceholders["metaApplicationId"] = secretsProperties.getProperty("META_APPLICATION_ID", "0")
        manifestPlaceholders["metaClientToken"] = secretsProperties.getProperty("META_CLIENT_TOKEN", "0")
        manifestPlaceholders["metaCallbackScheme"] = "hermesbridge"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.meta.wearables.dat.core)
    implementation(libs.porcupine.android)
}
