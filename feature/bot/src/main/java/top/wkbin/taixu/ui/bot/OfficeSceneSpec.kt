package top.wkbin.taixu.ui.bot

/** Original room units, before walkway compaction. Renderers consume furniture-local anchors. */
internal data class OfficeSceneSpec(
    val desks: List<OfficeDeskSpec> = defaultOfficeDesks(),
    val seats: List<OfficeSeat> = defaultOfficeSeats(),
    val cats: List<OfficeCatSpec> = defaultOfficeCats(),
    val coffeeMachines: List<OfficeCoffeeMachineSpec> = listOf(OfficeCoffeeMachineSpec(150, 330)),
    val zones: List<OfficeZoneSpec> = defaultOfficeZones(),
    val overflow: OfficeOverflowSpec = OfficeOverflowSpec(),
) {
    val allSeats get() = desks.map { it.seat } + seats
    val interactions get() = cats.map { it.interaction } + coffeeMachines.map { it.interaction }

    init {
        requireValidOfficeScene(this)
    }

    companion object {
        val Default = OfficeSceneSpec()
    }
}

internal data class OfficeAnchor(val x: Int, val y: Int)
internal data class OfficeBounds(val x: Int, val y: Int, val width: Int, val height: Int)

internal data class OfficeDeskShadow(val dx: Int = -1, val dy: Int = 18, val width: Int = 38)

internal data class OfficeDeskSpec(
    val id: String,
    val x: Int,
    val y: Int,
    val screenColor: Int = 0xFF7EC8E3.toInt(),
    val animationPhase: Int = 0,
    val drawers: Boolean = true,
    val shadow: OfficeDeskShadow? = OfficeDeskShadow(),
) {
    val seat get() = OfficeSeat(id, x + 10, y + 10, OfficePose.TYPE, OfficeSeatGroup.WORK)
    val screen get() = OfficeAnchor(x + 19, y - 18)
}

internal enum class OfficeInteractionAction { PET, BREW }
internal data class OfficeInteractionSpec(val id: String, val bounds: OfficeBounds, val action: OfficeInteractionAction)
internal enum class OfficeCatStyle { SLEEPING, PERCHED }

internal data class OfficeCatSpec(val id: String, val style: OfficeCatStyle, val x: Int, val y: Int) {
    val hitBounds get() = when (style) {
        OfficeCatStyle.SLEEPING -> OfficeBounds(x + 1, y - 3, 16, 10)
        OfficeCatStyle.PERCHED -> OfficeBounds(x, y, 21, 19)
    }
    val heart get() = OfficeAnchor(x + 9, y - 1)
    val interaction get() = OfficeInteractionSpec(id, hitBounds, OfficeInteractionAction.PET)
}

internal data class OfficeCoffeeMachineSpec(val x: Int, val y: Int, val id: String = "office-coffee-machine") {
    val hitBounds get() = OfficeBounds(x - 2, y - 2, 24, 24)
    val steam get() = OfficeAnchor(x + 8, y + 13)
    val wand get() = OfficeAnchor(x + 16, y + 16)
    val indicator get() = OfficeAnchor(x + 17, y + 2)
    val interaction get() = OfficeInteractionSpec(id, hitBounds, OfficeInteractionAction.BREW)
}

internal enum class OfficeZoneKind { THINK, MEET, WORK, COFFEE, REST }
internal data class OfficeZoneSpec(val id: String, val kind: OfficeZoneKind, val anchor: OfficeAnchor)

/** Legacy overflow placement remains deterministic, with its geometry owned by the scene. */
internal data class OfficeOverflowSpec(
    val rows: List<Int> = listOf(148, 288, 408),
    val startX: Int = 20,
    val columns: Int = 6,
    val spacingX: Int = 29,
    val cycleOffsetY: Int = 26,
) {
    fun anchor(index: Int) = OfficeAnchor(
        startX + index % columns * spacingX,
        rows[(index / columns) % rows.size] + index / (columns * rows.size) * cycleOffsetY,
    )
}
