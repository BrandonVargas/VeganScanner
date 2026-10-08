package dev.brandonvargas.veganscanner.core.model

/** A food product as understood by the app, independent of where the data came from. */
data class Product(
    val barcode: Barcode,
    val name: String?,
    val brands: String?,
    val imageUrl: String?,
    val ingredientsText: String?,
    val ingredients: List<Ingredient>,
    /** The data source's own product-level vegan analysis, before the app applies its own rules. */
    val sourceAnalysis: VeganStatus,
)

data class Ingredient(
    val id: String?,
    val text: String,
    val vegan: IngredientVeganStatus,
    val subIngredients: List<Ingredient> = emptyList(),
)

enum class IngredientVeganStatus { YES, NO, MAYBE, UNKNOWN }
