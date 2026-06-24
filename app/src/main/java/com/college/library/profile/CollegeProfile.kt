package com.college.library.profile

data class CollegeProfile(
    val collegeName: String = "GDC Library",
    val collegeFullName: String = "Government Degree College",
    val tagline: String = "Knowledge is Power",
    val libraryName: String = "College Library",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val logoUri: String? = null,
    val principalName: String = "",
    val librarianName: String = "",
    val establishedYear: String = "",
    val currency: String = "Rs.",
    val fineUnit: String = "per day",
    val memberIdPrefix: String = "STU",
    val isSetupComplete: Boolean = false
)
