package com.meloberry.android.data.workspace.source

import com.meloberry.android.core.model.MeloberryVersionInfo
import kotlinx.serialization.Serializable

@Serializable
internal data class PackageManifestDto(
    val packageVersion: String = MeloberryVersionInfo.LEGACY_PACKAGE_VERSION,
    val kind: String = "",
    val id: String = "",
    val exportedAtUtc: String = "",
    val assets: List<String> = emptyList(),
    val targetPlatform: String = "android"
)

@Serializable
internal data class SessionPackageDto(
    val id: String = "",
    val activeComponentId: String? = null,
    val components: List<ComponentPackageDto> = emptyList(),
    val name: String? = null,
    val description: String? = null
)

@Serializable
internal data class ComponentPackageDto(
    val id: String = "",
    val index: Int = 1,
    val kind: String = "",
    val quiz: QuizPackageDto? = null,
    val articleBlocks: List<ArticleBlockPackageDto> = emptyList(),
    val layoutOverrides: Map<String, String>? = null,
    val title: String? = null,
    val refKey: String? = null
)

@Serializable
internal data class ArticleBlockPackageDto(
    val id: String = "",
    val index: Int = 1,
    val kind: String = "",
    val textContent: String? = null,
    val imagePath: String? = null,
    val caption: String? = null,
    val alt: String? = null,
    val calloutTone: String? = null,
    val calloutTitle: String? = null,
    val refKey: String? = null,
    val payloadJson: String? = null
)

@Serializable
internal data class QuizPackageDto(
    val id: String = "",
    val quizType: String = "Mixed",
    val questions: List<QuestionPackageDto> = emptyList(),
    val name: String? = null,
    val description: String? = null
)

@Serializable
internal data class QuestionPackageDto(
    val id: String = "",
    val index: Int = 1,
    val questionType: String = "",
    val promptText: String? = null,
    val maxOptions: Int = 4,
    val isMultiCorrect: Boolean = false,
    val options: List<OptionPackageDto> = emptyList(),
    val front: SidePackageDto? = null,
    val back: SidePackageDto? = null,
    val layoutOverrides: Map<String, String>? = null,
    val promptImagePath: String? = null,
    val explanation: String? = null,
    val refKey: String? = null,
    val payloadJson: String? = null
)

@Serializable
internal data class OptionPackageDto(
    val index: Int = 1,
    val text: String? = null,
    val imagePath: String? = null,
    val isCorrect: Boolean = false
)

@Serializable
internal data class SidePackageDto(
    val text: String? = null,
    val imagePath: String? = null,
    val position: String = "TextBelowImage",
    val isPositionExplicit: Boolean = false
)
