package dev.brandonvargas.veganscanner.feature.scanner.domain.rules

/** Rough language guess for ingredient text (only used to give the web research context). Defaults to Spanish. */
internal object LabelLanguage {
    private val SPANISH = setOf("de", "y", "con", "agua", "sal", "azucar", "aceite", "harina", "leche", "ingredientes")
    private val ENGLISH = setOf("of", "and", "with", "water", "salt", "sugar", "oil", "flour", "milk", "ingredients")

    fun guess(text: String?): String {
        val words = TextFolding.fold(text.orEmpty()).split(' ')
        return if (words.count { it in ENGLISH } > words.count { it in SPANISH }) "en" else "es"
    }
}
