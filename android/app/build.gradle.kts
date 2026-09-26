import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.chaquopy)
}

val localProperties = Properties().apply {
    val source = rootProject.file("local.properties")
    if (source.isFile) source.inputStream().use(::load)
}
val algorithmRepo = file(
    localProperties.getProperty("harmonica.repo")
        ?: rootProject.file("../../harmonica-audio-eval").canonicalPath
).canonicalFile
val python310 = localProperties.getProperty("python310.path")

android {
    namespace = "demo.d3200"
    compileSdk = 36

    defaultConfig {
        applicationId = "demo.d3200"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
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

    sourceSets["main"].jniLibs.srcDir(algorithmRepo.resolve("android/app/src/main/jniLibs"))
}

chaquopy {
    defaultConfig {
        version = "3.10"
        if (!python310.isNullOrBlank()) buildPython(python310)
        pip {
            install("numpy==1.26.2")
            install("soundfile==0.13.1")
            install("chaquopy-openblas")
            install("chaquopy-libgfortran")
            install("scipy")
            install("cffi")
            install("chaquopy-libffi")
            install("chaquopy-libsndfile")
            install("chaquopy-flac")
            install("chaquopy-libogg")
            install("chaquopy-libvorbis")
            options("--no-deps")
            install("harmonica-eval @ ${algorithmRepo.toURI()}")
        }
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
