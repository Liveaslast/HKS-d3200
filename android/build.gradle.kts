// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.chaquopy) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${libs.versions.kotlin.get()}")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}")
            force("com.squareup.okhttp3:okhttp:${libs.versions.okhttp.get()}")
        }
    }

    configurations.matching { name.contains("android", ignoreCase = true) || name.contains("jvm", ignoreCase = true) }.all {
        val coroutinesAndroidVer = libs.versions.coroutines.android.get()
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesAndroidVer")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesAndroidVer")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesAndroidVer")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:$coroutinesAndroidVer")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesAndroidVer")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$coroutinesAndroidVer")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
