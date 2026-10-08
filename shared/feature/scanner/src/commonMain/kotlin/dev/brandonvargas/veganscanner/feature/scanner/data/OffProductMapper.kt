package dev.brandonvargas.veganscanner.feature.scanner.data

import dev.brandonvargas.veganscanner.core.model.Barcode
import dev.brandonvargas.veganscanner.core.model.Ingredient
import dev.brandonvargas.veganscanner.core.model.IngredientVeganStatus
import dev.brandonvargas.veganscanner.core.model.Product
import dev.brandonvargas.veganscanner.core.model.VeganStatus
import dev.brandonvargas.veganscanner.core.network.off.OffIngredientDto
import dev.brandonvargas.veganscanner.core.network.off.OffProductDto

internal fun OffProductDto.toDomain(barcode: Barcode) =
    Product(
        barcode = barcode,
        name = productName?.takeIf { it.isNotBlank() },
        brands = brands?.takeIf { it.isNotBlank() },
        imageUrl = imageFrontUrl?.takeIf { it.isNotBlank() },
        ingredientsText = ingredientsText?.takeIf { it.isNotBlank() },
        ingredients = ingredients.mapNotNull { it.toDomain() },
        sourceAnalysis = veganStatusFromTags(ingredientsAnalysisTags),
    )

private fun OffIngredientDto.toDomain(): Ingredient? {
    val label = text?.takeIf { it.isNotBlank() } ?: id?.substringAfter(':')?.replace('-', ' ') ?: return null
    return Ingredient(
        id = id,
        text = label,
        vegan =
            when (vegan?.lowercase()) {
                "yes" -> IngredientVeganStatus.YES
                "no" -> IngredientVeganStatus.NO
                "maybe" -> IngredientVeganStatus.MAYBE
                else -> IngredientVeganStatus.UNKNOWN
            },
        subIngredients = ingredients.mapNotNull { it.toDomain() },
    )
}

/** Maps `ingredients_analysis_tags` (e.g. `en:non-vegan`) to a product-level status. */
internal fun veganStatusFromTags(tags: List<String>): VeganStatus =
    when {
        "en:non-vegan" in tags -> VeganStatus.NON_VEGAN
        "en:vegan" in tags -> VeganStatus.VEGAN
        "en:maybe-vegan" in tags -> VeganStatus.MAYBE_VEGAN
        else -> VeganStatus.UNKNOWN
    }
