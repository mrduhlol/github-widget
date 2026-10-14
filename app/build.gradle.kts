plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.githubwidget"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.githubwidget"
        minSdk = 26
        targetSdk = 34
        versionCode = 22
        versionName = "3.1.3"
    }

    // Repo keystore (PKCS12): every release APK gets the same signature,
    // so updates install over the old version instead of conflicting.
    // Anyone with repo access can sign as this app — accepted tradeoff
    // for a sideloaded app; back up keystore/release.p12 regardless.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/release.p12")
            storeType = "PKCS12"
            storePassword = "gh-widgets-release"
            keyAlias = "ghwidgets"
            keyPassword = "gh-widgets-release"
        }
    }

    buildTypes {
        release {
            // Persistent repo keystore so every release carries the same
            // signature and installs cleanly over the previous version.
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")

    // No Retrofit/image libraries on purpose: HttpURLConnection + Canvas keep
    // the APK small and everything works offline from the local cache.
}
