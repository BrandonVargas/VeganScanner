package dev.brandonvargas.veganscanner.core.network.off

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `GET /api/v2/product/{barcode}`. `status` is 1 when found, 0 otherwise. */
@Serializable
data class OffProductResponseDto(
    val code: String? = null,
    val status: Int = 0,
    @SerialName("status_verbose") val statusVerbose: String? = null,
    val product: OffProductDto? = null,
)

@Serializable
data class OffProductDto(
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @SerialName("image_front_url") val imageFrontUrl: String? = null,
    @SerialName("ingredients_text") val ingredientsText: String? = null,
    val ingredients: List<OffIngredientDto> = emptyList(),
    @SerialName("ingredients_analysis_tags") val ingredientsAnalysisTags: List<String> = emptyList(),
    val lang: String? = null,
)

/** `vegan` is one of `yes`, `no`, `maybe` or absent. Ingredients can nest (e.g. "lecithin (soy)"). */
@Serializable
data class OffIngredientDto(
    val id: String? = null,
    val text: String? = null,
    val vegan: String? = null,
    val ingredients: List<OffIngredientDto> = emptyList(),
)
