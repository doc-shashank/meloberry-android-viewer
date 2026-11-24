import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

abstract class GenerateMeloberryVersionInfoTask : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val targetPackageVersion: Property<String>

    @get:Input
    abstract val legacyPackageVersion: Property<String>

    @get:Input
    abstract val supportedPackageVersionsCsv: Property<String>

    @get:Input
    abstract val analysisSchemaVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outFile = outputDir.file("com/meloberry/android/core/model/MeloberryVersionInfo.kt").get().asFile
        outFile.parentFile.mkdirs()

        val supportedLiterals = supportedPackageVersionsCsv.get()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ") { "\"$it\"" }

        outFile.writeText(
            """
            package com.meloberry.android.core.model

            /**
             * Single source of truth for Meloberry Android app, package, and analysis schema versions.
             * Generated at build time from `gradle/libs.versions.toml` — do not edit manually.
             */
            object MeloberryVersionInfo {
                const val VERSION_NAME: String = "${versionName.get()}"
                const val VERSION_CODE: Int = ${versionCode.get()}
                const val TARGET_PACKAGE_VERSION: String = "${targetPackageVersion.get()}"
                const val LEGACY_PACKAGE_VERSION: String = "${legacyPackageVersion.get()}"
                const val ANALYSIS_SCHEMA_VERSION: String = "${analysisSchemaVersion.get()}"
                val SUPPORTED_PACKAGE_VERSIONS: Set<String> = setOf($supportedLiterals)
            }
            """.trimIndent() + "\n"
        )
    }
}

val generateMeloberryVersionInfo = tasks.register<GenerateMeloberryVersionInfoTask>("generateMeloberryVersionInfo") {
    versionName.set(rootProject.extra["meloberryVersionName"] as String)
    versionCode.set(rootProject.extra["meloberryVersionCode"] as Int)
    targetPackageVersion.set(rootProject.extra["meloberryTargetPackageVersion"] as String)
    legacyPackageVersion.set(rootProject.extra["meloberryLegacyPackageVersion"] as String)
    supportedPackageVersionsCsv.set(rootProject.extra["meloberrySupportedPackageVersions"] as String)
    analysisSchemaVersion.set(rootProject.extra["meloberryAnalysisSchemaVersion"] as String)
    outputDir.set(layout.buildDirectory.dir("generated/source/meloberryVersion/kotlin"))
}

kotlin.sourceSets["main"].kotlin.srcDir(generateMeloberryVersionInfo.flatMap { it.outputDir })

dependencies {
    api(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
}
