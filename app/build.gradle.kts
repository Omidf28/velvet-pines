plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseSigningVariables = listOf(
    "VELVET_PINES_KEYSTORE",
    "VELVET_PINES_STORE_PASSWORD",
    "VELVET_PINES_KEY_ALIAS",
    "VELVET_PINES_KEY_PASSWORD",
)
val releaseSigningValues = releaseSigningVariables.associateWith {
    providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank)
}
val configureReleaseSigning = releaseSigningValues.values.any { it != null }
if (configureReleaseSigning) {
    val missingVariables = releaseSigningValues.filterValues { it == null }.keys
    require(missingVariables.isEmpty()) {
        "Set all Velvet Pines release signing variables. Missing: ${missingVariables.joinToString()}"
    }
}

android {
    namespace = "com.subtitleplayer.prototype"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.subtitleplayer.prototype"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "0.7.0-beta.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (configureReleaseSigning) {
        val releaseKeystore = rootProject.file(releaseSigningValues.getValue("VELVET_PINES_KEYSTORE")!!)
        require(releaseKeystore.isFile) { "VELVET_PINES_KEYSTORE must point to a keystore file." }
        val releaseSigning = signingConfigs.create("release") {
            storeFile = releaseKeystore
            storePassword = releaseSigningValues.getValue("VELVET_PINES_STORE_PASSWORD")
            keyAlias = releaseSigningValues.getValue("VELVET_PINES_KEY_ALIAS")
            keyPassword = releaseSigningValues.getValue("VELVET_PINES_KEY_PASSWORD")
        }
        buildTypes.named("release") {
            signingConfig = releaseSigning
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation:1.9.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("com.hierynomus:smbj:0.14.0")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.4")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
