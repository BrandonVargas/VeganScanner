package dev.brandonvargas.veganscanner.core.testing

/**
 * Open Food Facts API v2 responses used as test fixtures.
 *
 * Product data comes from real responses recorded in 2026-10 and trimmed to the requested fields.
 * Open Food Facts data is © Open Food Facts contributors, licensed under ODbL.
 * The ones marked "synthetic" are crafted to cover cases that are hard to find live.
 */
object OffFixtures {
    const val NON_VEGAN_BARCODE = "3017620422003"

    /** Nutella: OFF says `en:non-vegan`; milk and whey are flagged `vegan: no`. */
    val nonVegan =
        """
        {
          "code": "3017620422003",
          "product": {
            "product_name": "Nutella",
            "brands": "Nutella, Ferrero",
            "image_front_url": "https://images.openfoodfacts.org/images/products/301/762/042/2003/front_en.879.400.jpg",
            "ingredients_text": "Sucre, huile de palme, NOISETTES 13%, cacao maigre 7,4%, LAIT écrémé en poudre 6,6%, LACTOSERUM en poudre, émulsifiants: lécithines [SOJA), vanilline. Sans gluten.",
            "ingredients_analysis_tags": ["en:palm-oil", "en:non-vegan", "en:vegetarian"],
            "lang": "fr",
            "ingredients": [
              { "id": "en:sugar", "text": "Sucre", "vegan": "maybe" },
              { "id": "en:palm-oil", "text": "huile de palme", "vegan": "yes" },
              { "id": "en:hazelnut", "text": "NOISETTES", "vegan": "yes" },
              { "id": "en:fat-reduced-cocoa", "text": "cacao maigre", "vegan": "yes" },
              { "id": "en:skimmed-milk-powder", "text": "LAIT écrémé en poudre", "vegan": "no" },
              { "id": "en:whey-powder", "text": "LACTOSERUM en poudre", "vegan": "no" },
              {
                "id": "en:e322", "text": "lécithines", "vegan": "maybe",
                "ingredients": [{ "id": "en:soya-lecithin", "text": "lécithines de SOJA", "vegan": "yes" }]
              },
              { "id": "en:vanillin", "text": "vanilline", "vegan": "yes" }
            ]
          },
          "status": 1,
          "status_verbose": "product found"
        }
        """.trimIndent()

    const val VEGAN_BARCODE = "7500327047878"

    /** Rice cakes: OFF says `en:vegan`. */
    val vegan =
        """
        {
          "code": "7500327047878",
          "product": {
            "product_name": "Rice cakes con quinoa",
            "brands": "OKKO SUPERFOODS",
            "image_front_url": "https://images.openfoodfacts.org/images/products/750/032/704/7878/front_en.4.400.jpg",
            "ingredients_text": "Arroz integral y quinoa (14%)",
            "ingredients_analysis_tags": ["en:palm-oil-free", "en:vegan", "en:vegetarian"],
            "lang": "es",
            "ingredients": [
              { "id": "en:brown-rice", "text": "Arroz integral", "vegan": "yes" },
              { "id": "en:quinoa", "text": "quinoa", "vegan": "yes" }
            ]
          },
          "status": 1,
          "status_verbose": "product found"
        }
        """.trimIndent()

    const val VEGAN_NESTED_BARCODE = "0097339000054"

    /** Hot sauce: nested "Preservative" has no vegan flag, but the parent ingredient is `yes`. */
    val veganWithNestedUnknown =
        """
        {
          "code": "0097339000054",
          "product": {
            "product_name": "Mexican hot sauce",
            "brands": "Salsa Tamazula S.A. De C.V., Valentina",
            "ingredients_text": "Water, Chili Pepper, Vinegar, Salt, Spice, Sodium Benzoate (Preservative).",
            "ingredients_analysis_tags": ["en:palm-oil-free", "en:vegan", "en:vegetarian"],
            "lang": "en",
            "ingredients": [
              { "id": "en:water", "text": "Water", "vegan": "yes" },
              { "id": "en:chili-pepper", "text": "Chili Pepper", "vegan": "yes" },
              { "id": "en:vinegar", "text": "Vinegar", "vegan": "yes" },
              { "id": "en:salt", "text": "Salt", "vegan": "yes" },
              { "id": "en:spice", "text": "Spice", "vegan": "yes" },
              {
                "id": "en:e211", "text": "Sodium Benzoate", "vegan": "yes",
                "ingredients": [{ "id": "en:preservative", "text": "Preservative" }]
              }
            ]
          },
          "status": 1,
          "status_verbose": "product found"
        }
        """.trimIndent()

    const val MAYBE_VEGAN_BARCODE = "7501234567893"

    /** Synthetic: OFF says `en:maybe-vegan` because of sugar and natural flavours. */
    val maybeVegan =
        """
        {
          "code": "7501234567893",
          "product": {
            "product_name": "Galletas de avena",
            "brands": "Marca Ejemplo",
            "ingredients_text": "Harina de avena, azúcar, aceite de girasol, saborizantes naturales.",
            "ingredients_analysis_tags": ["en:palm-oil-free", "en:maybe-vegan", "en:maybe-vegetarian"],
            "lang": "es",
            "ingredients": [
              { "id": "en:oat-flour", "text": "Harina de avena", "vegan": "yes" },
              { "id": "en:sugar", "text": "azúcar", "vegan": "maybe" },
              { "id": "en:sunflower-oil", "text": "aceite de girasol", "vegan": "yes" },
              { "id": "en:natural-flavouring", "text": "saborizantes naturales", "vegan": "maybe" }
            ]
          },
          "status": 1,
          "status_verbose": "product found"
        }
        """.trimIndent()

    const val UNKNOWN_STATUS_BARCODE = "7509876543213"

    /** Synthetic: OFF has the product but its ingredients were never entered. */
    val unknownStatusNoIngredients =
        """
        {
          "code": "7509876543213",
          "product": {
            "product_name": "Producto sin ingredientes",
            "ingredients_analysis_tags": ["en:palm-oil-content-unknown", "en:vegan-status-unknown", "en:vegetarian-status-unknown"],
            "lang": "es"
          },
          "status": 1,
          "status_verbose": "product found"
        }
        """.trimIndent()

    const val NO_ANALYSIS_BARCODE = "7501111111119"

    /** Synthetic: product-level analysis is missing, but per-ingredient flags are all `yes`. */
    val noAnalysisAllIngredientsVegan =
        """
        {
          "code": "7501111111119",
          "product": {
            "product_name": "Frijoles",
            "ingredients_text": "Frijoles, agua, sal",
            "ingredients": [
              { "id": "en:bean", "text": "Frijoles", "vegan": "yes" },
              { "id": "en:water", "text": "agua", "vegan": "yes" },
              { "id": "en:salt", "text": "sal", "vegan": "yes" }
            ]
          },
          "status": 1,
          "status_verbose": "product found"
        }
        """.trimIndent()

    const val NOT_FOUND_BARCODE = "7501000000012"

    /** Real 404 body for an unknown barcode. */
    val notFound =
        """
        {"code":"7501000000012","status":0,"status_verbose":"product not found"}
        """.trimIndent()

    /** Real 200 body for a code OFF cannot parse. */
    val invalidCode =
        """
        {"code":"00000000","status":0,"status_verbose":"no code or invalid code"}
        """.trimIndent()

    /** Shape of the HTML page OFF serves while overloaded (sometimes with HTTP 200). */
    val maintenanceHtml =
        """
        <!DOCTYPE html><html lang="en"><head><title>Page temporarily unavailable - Open Food Facts</title></head>
        <body><h1>Page temporarily unavailable</h1></body></html>
        """.trimIndent()
}
