package com.college.library.profile

/**
 * One institution's own identity, shown on cards, receipts and reports.
 *
 * The defaults are deliberately generic. This system serves every Government
 * Degree College in Khyber Pakhtunkhwa, so a default naming one particular
 * college would put the wrong name on another college's ID cards and receipts
 * until someone noticed. A college sets its real name during onboarding.
 */
data class CollegeProfile(
    val collegeName: String = "College Library",
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
