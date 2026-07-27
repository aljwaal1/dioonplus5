plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciTestKeystore = rootProject.file(".github/ci/dioonplus-test.jks")

android {
    namespace = "com.dioonplus.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dioonplus.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.3"

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
    }

    buildTypes {
        getByName("debug") {
            if (ciTestKeystore.exists()) {
                signingConfig = signingConfigs.getByName("ciTest")
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

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
