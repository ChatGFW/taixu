package top.wkbin.taixu.ui.bot

/** List order is seat allocation priority. Animation phases stay stable when instances are added. */
internal fun defaultOfficeDesks() = listOf(
    OfficeDeskSpec("desk", 18, 214, 0xFF7EC8E3.toInt(), 0),
    OfficeDeskSpec("desk-b", 62, 214, 0xFF8FD18A.toInt(), 1),
    OfficeDeskSpec("desk-c", 18, 262, 0xFFC9A6E8.toInt(), 4),
    OfficeDeskSpec("desk-d", 62, 262, 0xFF7EC8E3.toInt(), 5),
    // The existing right-hand row has a shared shadow. Two local segments preserve its pixels.
    OfficeDeskSpec("desk-e", 116, 214, 0xFFC9A6E8.toInt(), 2, false, OfficeDeskShadow(2, 21, 38)),
    OfficeDeskSpec("desk-f", 156, 214, 0xFF7EC8E3.toInt(), 3, false, OfficeDeskShadow(0, 21, 26)),
    OfficeDeskSpec("desk-g", 116, 262, 0xFF8FD18A.toInt(), 6, false, null),
    OfficeDeskSpec("desk-h", 156, 262, 0xFFC9A6E8.toInt(), 7, false, null),
)

internal fun defaultOfficeSeats(): List<OfficeSeat> {
    val meetingPoses = setOf(OfficePose.SIT, OfficePose.TYPE)
    return listOf(
        OfficeSeat("meet-a", 114, 138, OfficePose.SIT, OfficeSeatGroup.MEET, meetingPoses, roamOffset = OfficeAnchor(-18, 12)),
        OfficeSeat("meet-b", 136, 138, OfficePose.SIT, OfficeSeatGroup.MEET, meetingPoses, roamOffset = OfficeAnchor(-18, 14)),
        OfficeSeat("meet-c", 158, 138, OfficePose.SIT, OfficeSeatGroup.MEET, meetingPoses, roamOffset = OfficeAnchor(18, 12)),
        OfficeSeat("meet-d", 96, 138, OfficePose.SIT, OfficeSeatGroup.MEET, meetingPoses, roamOffset = OfficeAnchor(80, 12)),
        OfficeSeat("board", 92, 164, OfficePose.THINK, OfficeSeatGroup.THINK),
        OfficeSeat("sofa-l", 40, 376, OfficePose.SIT, OfficeSeatGroup.REST, roamOffset = OfficeAnchor(-16, 10)),
        OfficeSeat("sofa-r", 116, 378, OfficePose.SIT, OfficeSeatGroup.REST, roamOffset = OfficeAnchor(-16, 10)),
        OfficeSeat("rug", 84, 400, OfficePose.SIT, OfficeSeatGroup.REST, roamOffset = OfficeAnchor(-16, -8)),
        OfficeSeat("coffee-a", 156, 380, OfficePose.SIT, OfficeSeatGroup.COFFEE,
            idleAction = OfficeIdleAction.SIP, roamOffset = OfficeAnchor(-18, 10)),
        OfficeSeat("coffee-b", 178, 380, OfficePose.SIT, OfficeSeatGroup.COFFEE,
            idleAction = OfficeIdleAction.SIP, roamOffset = OfficeAnchor(-12, 10)),
        OfficeSeat("window", 174, 144, OfficePose.STAND),
        OfficeSeat("poster", 164, 380, OfficePose.STAND),
        OfficeSeat("hall", 70, 410, OfficePose.STAND),
    )
}

internal fun defaultOfficeCats() = listOf(
    OfficeCatSpec("office-cat-lobby", OfficeCatStyle.SLEEPING, 73, 54),
    OfficeCatSpec("office-cat-sofa", OfficeCatStyle.PERCHED, 48, 339),
)

internal fun defaultOfficeZones() = listOf(
    OfficeZoneSpec("think", OfficeZoneKind.THINK, OfficeAnchor(8, 69)),
    OfficeZoneSpec("meet", OfficeZoneKind.MEET, OfficeAnchor(112, 69)),
    OfficeZoneSpec("work", OfficeZoneKind.WORK, OfficeAnchor(104, 183)),
    OfficeZoneSpec("coffee", OfficeZoneKind.COFFEE, OfficeAnchor(146, 318)),
    OfficeZoneSpec("rest", OfficeZoneKind.REST, OfficeAnchor(6, 318)),
)
