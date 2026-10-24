package com.absensiapp.school.data.model

import java.io.Serializable

data class SchoolClass(
    val id: String,
    val name: String, // e.g. "XI IPA 2", "XI TKJ 1"
    val room: String = "R. 12", // e.g. "R. 12", "Lab Komputer 1"
    val latitude: Double = -6.89972,
    val longitude: Double = 112.04944,
    val radiusMeters: Float = 50f // Radius toleransi presensi kelas (meter)
) : Serializable {

    companion object {
        val DEFAULT_CLASSES = listOf(
            SchoolClass(
                id = "cls_xi_ipa_2",
                name = "XI IPA 2",
                room = "R. 12",
                latitude = -6.899720,
                longitude = 112.049440,
                radiusMeters = 50f
            ),
            SchoolClass(
                id = "cls_xi_tkj_1",
                name = "XI TKJ 1",
                room = "Lab Komputer 1",
                latitude = -6.899650,
                longitude = 112.049520,
                radiusMeters = 50f
            ),
            SchoolClass(
                id = "cls_xi_tkj_2",
                name = "XI TKJ 2",
                room = "Lab Komputer 2",
                latitude = -6.899580,
                longitude = 112.049600,
                radiusMeters = 50f
            ),
            SchoolClass(
                id = "cls_xii_rpl_1",
                name = "XII RPL 1",
                room = "Lab Software 3",
                latitude = -6.899500,
                longitude = 112.049700,
                radiusMeters = 50f
            )
        )
    }
}
