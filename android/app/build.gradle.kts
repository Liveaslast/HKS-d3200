plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "demo.d3200"
    compileSdk = 36

    defaultConfig {
        applicationId = "demo.d3200"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
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

    kotlinOptions {
        jvmTarget = "11"
    }
}

allprojects {
    configurations.all {
        resolutionStrategy {
            val coroutinesAndroidVer = libs.versions.coroutines.android.get()
            force("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesAndroidVer")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesAndroidVer")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesAndroidVer")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:$coroutinesAndroidVer")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesAndroidVer")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$coroutinesAndroidVer")
            force("com.squareup.okhttp3:okhttp:${libs.versions.okhttp.get()}")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.jdk8)
    implementation(libs.coroutines.rx2)
    implementation(libs.coroutines.coreJvm)
    implementation(libs.coroutines.reactive)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.buffer)
    implementation(libs.atomicfu)

    implementation(libs.spongycastle.core)
    implementation(libs.spongycastle.prov)
    implementation(libs.gson)
    implementation(libs.xxpermissions)

    // Soundcore SDK (spplink)
    implementation(files("libs/module_spplink-release.aar"))

    implementation(files("libs/opus-lib-0.0.2.aar"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
