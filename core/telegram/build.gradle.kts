import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

// Telegram API credentials come from local.properties (never committed). Without them the
// module still builds and the library reports "not set up in this build".
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.zaptv.core.telegram"
    compileSdk = 37
    defaultConfig {
        minSdk = 24
        buildConfigField("int", "TG_API_ID", localProps.getProperty("TG_API_ID") ?: "0")
        buildConfigField("String", "TG_API_HASH", "\"${localProps.getProperty("TG_API_HASH") ?: ""}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:parser"))
    api(libs.tdl.coroutines)
    implementation(libs.media3.exoplayer)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
