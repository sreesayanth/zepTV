import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}

tasks.test {
    // Lets RealPlaylistSmokeTest read -Dzaptv.playlist=... from the command line.
    System.getProperty("zaptv.playlist")?.let { systemProperty("zaptv.playlist", it) }
    testLogging { showStandardStreams = System.getProperty("zaptv.playlist") != null }
}
