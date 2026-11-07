package nl.contrastlezer.image

/** Een hoog-contrast kleurenpaar: tekstkleur op achtergrondkleur. */
data class Palette(
    val name: String,
    val foreground: Int,
    val background: Int,
)

/**
 * Vaste set kleurcombinaties die maximale leesbaarheid geven.
 * De volgorde is ook de volgorde van de knoppen in de app.
 */
val HighContrastPalettes: List<Palette> = listOf(
    Palette("Zwart op wit", 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
    Palette("Wit op zwart", 0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
    Palette("Geel op zwart", 0xFFFFD500.toInt(), 0xFF000000.toInt()),
    Palette("Zwart op geel", 0xFF000000.toInt(), 0xFFFFD500.toInt()),
    Palette("Blauw op wit", 0xFF0033CC.toInt(), 0xFFFFFFFF.toInt()),
    Palette("Cyaan op zwart", 0xFF00E5FF.toInt(), 0xFF000000.toInt()),
)
