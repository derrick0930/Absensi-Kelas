package com.absensiapp.school.data.model

import java.io.Serializable

data class StudentCardData(
    val nisn: String,
    val name: String,
    val birthPlaceDate: String,
    val address: String,
    val religion: String,
    val bloodType: String,
    val schoolName: String = "SMKS TARUNA JAYA PRAWIRA TUBAN",
    val foundationName: String = "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA",
    val headmasterName: String = "Bambang Kusdiyanto, ST.M.Pd",
    val schoolAddress: String = "JL. DR. WAHIDIN SUDIROHUSODO TUBAN - JAWA TIMUR",
    val className: String = "XI TKJ 1"
) : Serializable {

    companion object {
        /**
         * Authentic sample based directly on the provided Student ID card photo.
         */
        val DERRICK_VITO = StudentCardData(
            nisn = "0095687758",
            name = "DERRICK VITO RENGGADITA",
            birthPlaceDate = "Tuban, 23 Februari 2009",
            address = "DS. Jarorejo RT.03/02 Kerek - Tuban",
            religion = "Islam",
            bloodType = "A",
            schoolName = "SMKS TARUNA JAYA PRAWIRA TUBAN",
            foundationName = "YAYASAN PENDIDIKAN TARUNA JAYA PRAWIRA",
            headmasterName = "Bambang Kusdiyanto, ST.M.Pd",
            schoolAddress = "JL. DR. WAHIDIN SUDIROHUSODO TUBAN - JAWA TIMUR",
            className = "XI TKJ 1"
        )
    }
}
