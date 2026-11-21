import com.android.build.api.dsl.CommonExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

subprojects {
    val configureAndroid: CommonExtension<*, *, *, *, *, *>.() -> Unit = {
        compileSdk = 35
        defaultConfig {
            minSdk = 26
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    plugins.withId("com.android.application") {
        extensions.configure(CommonExtension::class.java, configureAndroid)
    }
    plugins.withId("com.android.library") {
        extensions.configure(CommonExtension::class.java, configureAndroid)
    }
    plugins.withId("org.jetbrains.kotlin.android") {
        extensions.configure(KotlinAndroidProjectExtension::class.java) {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure(JavaPluginExtension::class.java) {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        extensions.configure(KotlinJvmProjectExtension::class.java) {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
