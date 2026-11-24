plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.meloberry.android"

    defaultConfig {
        applicationId = "com.meloberry.android"
        targetSdk = 35
        versionCode = rootProject.extra["meloberryVersionCode"] as Int
        versionName = rootProject.extra["meloberryVersionName"] as String
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = true
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))
    implementation(project(":data:workspace"))
    implementation(project(":data:analysis"))
    implementation(project(":domain"))
    implementation(project(":feature:library"))
    implementation(project(":feature:player"))
    implementation(project(":feature:history"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
