package com.college.library.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// ── String catalog ────────────────────────────────────────────────────────────
// Each property returns the correct string for the active language.
// Usage in Composable:  val s = LocalStrings.current
// Usage anywhere else:  strings.issueBook

data class AppStrings(
    // General
    val appName: String,
    val ok: String,
    val cancel: String,
    val back: String,
    val done: String,
    val save: String,
    val search: String,
    val share: String,
    val print: String,
    val download: String,
    val loading: String,
    val error: String,
    val success: String,

    // Nav / Tabs
    val navHome: String,
    val navBooks: String,
    val navMembers: String,
    val navTransact: String,
    val navReports: String,
    val navRanks: String,

    // Dashboard
    val dashboard: String,
    val totalBooks: String,
    val availableBooks: String,
    val issuedBooks: String,
    val totalMembers: String,
    val overdueBooks: String,
    val fineCollected: String,
    val quickActions: String,

    // Books
    val books: String,
    val addBook: String,
    val editBook: String,
    val bookTitle: String,
    val author: String,
    val isbn: String,
    val publisher: String,
    val edition: String,
    val price: String,
    val status: String,
    val available: String,
    val issued: String,

    // Members
    val members: String,
    val addMember: String,
    val editMember: String,
    val memberName: String,
    val memberId: String,
    val department: String,
    val email: String,
    val phone: String,
    val memberType: String,

    // Issue / Return
    val issueBook: String,
    val returnBook: String,
    val bulkIssue: String,
    val selectBook: String,
    val selectMember: String,
    val confirmIssue: String,
    val issueDate: String,
    val dueDate: String,
    val returnDate: String,
    val bookIssuedSuccess: String,
    val bookReturnedSuccess: String,
    val fine: String,
    val noFine: String,
    val fineAmount: String,
    val issuedTo: String,
    val returnedBy: String,
    val step: String,
    val of: String,

    // Receipt
    val issueSlip: String,
    val returnSlip: String,
    val receiptNumber: String,
    val libraryName: String,
    val collegeHeader: String,
    val thankYou: String,
    val printReceipt: String,
    val shareReceipt: String,

    // Reports
    val reports: String,
    val overview: String,
    val totalCollectionValue: String,
    val issuedThisMonth: String,
    val issuedLastMonth: String,
    val fineThisMonth: String,
    val topPublishers: String,
    val availabilityRatio: String,
    val mostIssuedBooks: String,
    val mostActiveBorrowers: String,
    val overdueList: String,
    val exportPdf: String,

    // Leaderboard
    val leaderboard: String,
    val noDataLeaderboard: String,

    // Settings
    val settings: String,
    val language: String,
    val languageToggle: String,
    val darkMode: String,
    val finePerDay: String,
    val borrowDuration: String,
    val maxBooks: String,
    val importBooks: String,
    val resetDatabase: String,
    val seedSampleData: String,
    val days: String,
    val rupees: String,

    // About & Competition Features
    val aboutApp: String,
    val appVersion: String,
    val developerInfo: String,
    val techStack: String,
    val exportDataCsv: String
)

val EnglishStrings = AppStrings(
    appName = "GDC Library",
    ok = "OK",
    cancel = "Cancel",
    back = "Back",
    done = "Done",
    save = "Save",
    search = "Search",
    share = "Share",
    print = "Print",
    download = "Download",
    loading = "Loading…",
    error = "Error",
    success = "Success",

    navHome = "Home",
    navBooks = "Books",
    navMembers = "Members",
    navTransact = "Transact",
    navReports = "Reports",
    navRanks = "Ranks",

    dashboard = "Dashboard",
    totalBooks = "Total Books",
    availableBooks = "Available",
    issuedBooks = "Issued",
    totalMembers = "Members",
    overdueBooks = "Overdue",
    fineCollected = "Fine Collected",
    quickActions = "Quick Actions",

    books = "Books",
    addBook = "Add Book",
    editBook = "Edit Book",
    bookTitle = "Book Title",
    author = "Author",
    isbn = "ISBN",
    publisher = "Publisher",
    edition = "Edition",
    price = "Price",
    status = "Status",
    available = "Available",
    issued = "Issued",

    members = "Members",
    addMember = "Add Member",
    editMember = "Edit Member",
    memberName = "Full Name",
    memberId = "Member ID",
    department = "Department",
    email = "Email",
    phone = "Phone",
    memberType = "Member Type",

    issueBook = "Issue Book",
    returnBook = "Return Book",
    bulkIssue = "Bulk Issue",
    selectBook = "Select a Book",
    selectMember = "Select a Member",
    confirmIssue = "Confirm Issue",
    issueDate = "Issue Date",
    dueDate = "Due Date",
    returnDate = "Return Date",
    bookIssuedSuccess = "Book Issued Successfully!",
    bookReturnedSuccess = "Book Returned Successfully!",
    fine = "Fine",
    noFine = "No Fine",
    fineAmount = "Fine Amount",
    issuedTo = "issued to",
    returnedBy = "returned by",
    step = "Step",
    of = "of",

    issueSlip = "ISSUE SLIP",
    returnSlip = "RETURN SLIP",
    receiptNumber = "Receipt No",
    libraryName = "GDC Library",
    collegeHeader = "Government Degree College",
    thankYou = "Thank you! Please return the book on time.",
    printReceipt = "Print Receipt",
    shareReceipt = "Share Receipt",

    reports = "Reports",
    overview = "Overview",
    totalCollectionValue = "Total Collection Value",
    issuedThisMonth = "Issued This Month",
    issuedLastMonth = "Issued Last Month",
    fineThisMonth = "Fine Collected (This Month)",
    topPublishers = "Top 10 Publishers",
    availabilityRatio = "Availability Ratio",
    mostIssuedBooks = "Most Issued Books (All Time)",
    mostActiveBorrowers = "Most Active Borrowers",
    overdueList = "Overdue Books",
    exportPdf = "Export PDF",

    leaderboard = "Leaderboard",
    noDataLeaderboard = "No data yet. Issue some books to start the leaderboard!",

    settings = "Settings",
    language = "Language",
    languageToggle = "Switch to Hindi / हिन्दी में बदलें",
    darkMode = "Dark Mode",
    finePerDay = "Fine Per Day (Rs.)",
    borrowDuration = "Borrow Duration (Days)",
    maxBooks = "Max Books Per Member",
    importBooks = "Import Books",
    resetDatabase = "Reset Database",
    seedSampleData = "Seed Sample Data",
    days = "days",
    rupees = "Rs.",

    aboutApp = "About App",
    appVersion = "Version 1.0 (Competition Build)",
    developerInfo = "Developed for GDC Library Management.",
    techStack = "Tech Stack: Kotlin, Jetpack Compose, Room, Hilt, ML Kit",
    exportDataCsv = "Export Data (CSV)"
)

val HindiStrings = AppStrings(
    appName = "GDC पुस्तकालय",
    ok = "ठीक है",
    cancel = "रद्द करें",
    back = "वापस",
    done = "हो गया",
    save = "सहेजें",
    search = "खोजें",
    share = "साझा करें",
    print = "प्रिंट",
    download = "डाउनलोड",
    loading = "लोड हो रहा है…",
    error = "त्रुटि",
    success = "सफलता",

    navHome = "होम",
    navBooks = "किताबें",
    navMembers = "सदस्य",
    navTransact = "लेन-देन",
    navReports = "रिपोर्ट",
    navRanks = "रैंक",

    dashboard = "डैशबोर्ड",
    totalBooks = "कुल किताबें",
    availableBooks = "उपलब्ध",
    issuedBooks = "जारी",
    totalMembers = "सदस्य",
    overdueBooks = "अतिदेय",
    fineCollected = "जुर्माना वसूला",
    quickActions = "त्वरित क्रियाएं",

    books = "किताबें",
    addBook = "किताब जोड़ें",
    editBook = "किताब संपादित करें",
    bookTitle = "पुस्तक शीर्षक",
    author = "लेखक",
    isbn = "आईएसबीएन",
    publisher = "प्रकाशक",
    edition = "संस्करण",
    price = "मूल्य",
    status = "स्थिति",
    available = "उपलब्ध",
    issued = "जारी",

    members = "सदस्य",
    addMember = "सदस्य जोड़ें",
    editMember = "सदस्य संपादित करें",
    memberName = "पूरा नाम",
    memberId = "सदस्य आईडी",
    department = "विभाग",
    email = "ईमेल",
    phone = "फ़ोन",
    memberType = "सदस्य प्रकार",

    issueBook = "पुस्तक जारी करें",
    returnBook = "पुस्तक वापस करें",
    bulkIssue = "बल्क जारी",
    selectBook = "किताब चुनें",
    selectMember = "सदस्य चुनें",
    confirmIssue = "जारी की पुष्टि करें",
    issueDate = "जारी तिथि",
    dueDate = "देय तिथि",
    returnDate = "वापसी तिथि",
    bookIssuedSuccess = "पुस्तक सफलतापूर्वक जारी हुई!",
    bookReturnedSuccess = "पुस्तक सफलतापूर्वक वापस हुई!",
    fine = "जुर्माना",
    noFine = "कोई जुर्माना नहीं",
    fineAmount = "जुर्माना राशि",
    issuedTo = "को जारी किया गया",
    returnedBy = "द्वारा वापस किया गया",
    step = "चरण",
    of = "का",

    issueSlip = "जारी पर्ची",
    returnSlip = "वापसी पर्ची",
    receiptNumber = "रसीद नं.",
    libraryName = "GDC पुस्तकालय",
    collegeHeader = "राजकीय डिग्री कॉलेज",
    thankYou = "धन्यवाद! कृपया पुस्तक समय पर लौटाएं।",
    printReceipt = "रसीद प्रिंट करें",
    shareReceipt = "रसीद साझा करें",

    reports = "रिपोर्ट",
    overview = "अवलोकन",
    totalCollectionValue = "कुल संग्रह मूल्य",
    issuedThisMonth = "इस माह जारी",
    issuedLastMonth = "पिछले माह जारी",
    fineThisMonth = "इस माह जुर्माना",
    topPublishers = "शीर्ष 10 प्रकाशक",
    availabilityRatio = "उपलब्धता अनुपात",
    mostIssuedBooks = "सबसे अधिक जारी पुस्तकें",
    mostActiveBorrowers = "सबसे सक्रिय पाठक",
    overdueList = "अतिदेय पुस्तकें",
    exportPdf = "PDF निर्यात करें",

    leaderboard = "लीडरबोर्ड",
    noDataLeaderboard = "अभी कोई डेटा नहीं। लीडरबोर्ड शुरू करने के लिए किताबें जारी करें!",

    settings = "सेटिंग्स",
    language = "भाषा",
    languageToggle = "Switch to English / अंग्रेज़ी में बदलें",
    darkMode = "डार्क मोड",
    finePerDay = "प्रति दिन जुर्माना (रु.)",
    borrowDuration = "उधार अवधि (दिन)",
    maxBooks = "प्रति सदस्य अधिकतम पुस्तकें",
    importBooks = "पुस्तकें आयात करें",
    resetDatabase = "डेटाबेस रीसेट करें",
    seedSampleData = "नमूना डेटा भरें",
    days = "दिन",
    rupees = "रु.",

    aboutApp = "ऐप के बारे में",
    appVersion = "संस्करण 1.0 (प्रतियोगिता निर्माण)",
    developerInfo = "GDC पुस्तकालय प्रबंधन के लिए विकसित।",
    techStack = "तकनीक: Kotlin, Jetpack Compose, Room, Hilt, ML Kit",
    exportDataCsv = "डेटा निर्यात करें (CSV)"
)

// ── ViewModel to bridge language state into Compose ──────────────────────────
@HiltViewModel
class StringsViewModel @Inject constructor(
    val languageManager: LanguageManager
) : ViewModel()

// ── Composable accessor ───────────────────────────────────────────────────────
@Composable
fun rememberStrings(stringsViewModel: StringsViewModel = hiltViewModel()): AppStrings {
    val lang by stringsViewModel.languageManager.currentLanguage.collectAsState()
    return if (lang == AppLanguage.HINDI) HindiStrings else EnglishStrings
}
