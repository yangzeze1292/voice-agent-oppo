import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secretProperty(vararg names: String): String {
    val value = names.firstNotNullOfOrNull { name ->
        localProperties.getProperty(name) ?: System.getenv(name)
    }.orEmpty()
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.example.voiceagent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.voiceagent"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-oppo-c16"

        buildConfigField(
            "String",
            "LLM_API_KEY",
            "\"${secretProperty("VOICEAGENT_LLM_API_KEY", "llmApiKey")}\""
        )
        buildConfigField(
            "String",
            "LLM_VISION_API_KEY",
            "\"${secretProperty("VOICEAGENT_LLM_VISION_API_KEY", "llmVisionApiKey")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json.org)
}
