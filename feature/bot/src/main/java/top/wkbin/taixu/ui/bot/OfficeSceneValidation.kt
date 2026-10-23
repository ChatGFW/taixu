package top.wkbin.taixu.ui.bot

/** Validate data errors before rendering or hit testing. Art may deliberately clip at edges. */
internal fun requireValidOfficeScene(scene: OfficeSceneSpec) {
    fun unique(ids: List<String>, kind: String) {
        require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "Duplicate or blank $kind ID" }
    }
    fun inside(x: Int, y: Int) = x in 0 until PixelOfficeRoom.WIDTH && y in 0 until PixelOfficeRoom.HEIGHT
    unique(scene.desks.map { it.id } + scene.cats.map { it.id } + scene.coffeeMachines.map { it.id }, "furniture")
    unique(scene.allSeats.map { it.id }, "seat")
    require(scene.allSeats.map { it.x to it.y }.distinct().size == scene.allSeats.size) { "Seats overlap" }
    unique(scene.interactions.map { it.id }, "interaction")
    unique(scene.zones.map { it.id }, "zone")
    scene.allSeats.forEach { seat ->
        require(inside(seat.x, seat.y)) { "Seat ${seat.id} is outside the room" }
        require(seat.pose in seat.poses) { "Seat ${seat.id} does not support its default pose" }
        seat.roamOffset?.let {
            require(inside(seat.x + it.x, seat.y + it.y)) { "Seat ${seat.id} roams outside the room" }
        }
    }
    scene.desks.forEach {
        require(inside(it.screen.x, it.screen.y)) { "Desk ${it.id} has a screen outside the room" }
    }
    scene.zones.forEach { require(inside(it.anchor.x, it.anchor.y)) { "Zone ${it.id} is outside the room" } }
    val interactions = scene.interactions
    interactions.forEach { interaction ->
        val b = interaction.bounds
        require(b.width > 0 && b.height > 0 && inside(b.x, b.y) && inside(b.x + b.width - 1, b.y + b.height - 1)) {
            "Interaction ${interaction.id} is outside the room"
        }
        require(PixelOfficeScene.y(b.y.toFloat()) + b.height <= PixelOfficeScene.HEIGHT) {
            "Interaction ${interaction.id} is clipped by walkway compaction"
        }
    }
    interactions.forEachIndexed { index, first ->
        interactions.drop(index + 1).forEach { second ->
            val a = first.bounds
            val b = second.bounds
            // Compose projects the origin and retains target size; test the same visible bounds.
            val ay = PixelOfficeScene.y(a.y.toFloat())
            val by = PixelOfficeScene.y(b.y.toFloat())
            require(a.x >= b.x + b.width || b.x >= a.x + a.width || ay >= by + b.height || by >= ay + a.height) {
                "Interactions ${first.id} and ${second.id} overlap"
            }
        }
    }
    with(scene.overflow) {
        require(rows.isNotEmpty() && columns > 0 && spacingX > 0 && cycleOffsetY >= 0) { "Invalid overflow geometry" }
        require(rows.all { inside(startX, it) && inside(startX + (columns - 1) * spacingX, it) }) {
            "Overflow row is outside the room"
        }
    }
}
