plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciTestKeystore = rootProject.file(".github/ci/dioonplus-test.jks")
val releaseKeystore = rootProject.file("release-keystore.jks")
val releaseStorePassword = System.getenv("DIOON_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("DIOON_KEY_ALIAS")
val releaseKeyPassword = System.getenv("DIOON_KEY_PASSWORD")
val hasReleaseSigning = releaseKeystore.exists() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.dioonplus.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dioonplus.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "0.5.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (ciTestKeystore.exists()) {
            create("ciTest") {
                storeFile = ciTestKeystore
                storePassword = "dioonplus-test-2026"
                keyAlias = "dioonplus-test"
                keyPassword = "dioonplus-test-2026"
            }
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (ciTestKeystore.exists()) {
                signingConfig = signingConfigs.getByName("ciTest")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.gms:play-services-ads:25.4.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Google Play closed testing release: 0.5.5
