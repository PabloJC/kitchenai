package com.kitchenai.admin.requests

/** The values `firebase/firestore.rules` accepts for `status`, as stored. */
enum class RequestStatus(
    val wire: String,
    val label: String,
) {
    OPEN("open", "Open"),
    PROMOTED("promoted", "Promoted"),
    ALIASED("aliased", "Aliased"),
    DISMISSED("dismissed", "Dismissed"),
    ;

    /** Promoted and aliased requests point at a catalogue ingredient; the rules require the id. */
    val needsIngredient: Boolean get() = this == PROMOTED || this == ALIASED

    companion object {
        fun fromWire(value: String): RequestStatus? = entries.firstOrNull { status -> status.wire == value }
    }
}
