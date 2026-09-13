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
    languageToggle = "Switch to Urdu / اردو میں تبدیل کریں",
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

// NOTE: machine-drafted Urdu — please have a native/fluent Urdu speaker
// review this wording before it reaches real colleges. The app renders this
// left-to-right (no RTL layout mirroring yet), which is acceptable for short
// labels like these but worth revisiting for a fuller Urdu rollout later.
val UrduStrings = AppStrings(
    appName = "GDC لائبریری",
    ok = "ٹھیک ہے",
    cancel = "منسوخ کریں",
    back = "واپس",
    done = "مکمل",
    save = "محفوظ کریں",
    search = "تلاش کریں",
    share = "شیئر کریں",
    print = "پرنٹ کریں",
    download = "ڈاؤن لوڈ کریں",
    loading = "لوڈ ہو رہا ہے…",
    error = "خرابی",
    success = "کامیابی",

    navHome = "ہوم",
    navBooks = "کتابیں",
    navMembers = "ممبران",
    navTransact = "لین دین",
    navReports = "رپورٹس",
    navRanks = "درجہ بندی",

    dashboard = "ڈیش بورڈ",
    totalBooks = "کل کتابیں",
    availableBooks = "دستیاب",
    issuedBooks = "جاری شدہ",
    totalMembers = "ممبران",
    overdueBooks = "میعاد گزشتہ",
    fineCollected = "وصول شدہ جرمانہ",
    quickActions = "فوری اقدامات",

    books = "کتابیں",
    addBook = "کتاب شامل کریں",
    editBook = "کتاب میں ترمیم کریں",
    bookTitle = "کتاب کا عنوان",
    author = "مصنف",
    isbn = "آئی ایس بی این",
    publisher = "ناشر",
    edition = "ایڈیشن",
    price = "قیمت",
    status = "حیثیت",
    available = "دستیاب",
    issued = "جاری شدہ",

    members = "ممبران",
    addMember = "ممبر شامل کریں",
    editMember = "ممبر میں ترمیم کریں",
    memberName = "پورا نام",
    memberId = "ممبر آئی ڈی",
    department = "شعبہ",
    email = "ای میل",
    phone = "فون",
    memberType = "ممبر کی قسم",

    issueBook = "کتاب جاری کریں",
    returnBook = "کتاب واپس کریں",
    bulkIssue = "یکمشت اجراء",
    selectBook = "کتاب منتخب کریں",
    selectMember = "ممبر منتخب کریں",
    confirmIssue = "اجراء کی تصدیق کریں",
    issueDate = "تاریخ اجراء",
    dueDate = "تاریخ واپسی",
    returnDate = "واپسی کی تاریخ",
    bookIssuedSuccess = "کتاب کامیابی سے جاری ہو گئی!",
    bookReturnedSuccess = "کتاب کامیابی سے واپس ہو گئی!",
    fine = "جرمانہ",
    noFine = "کوئی جرمانہ نہیں",
    fineAmount = "جرمانے کی رقم",
    issuedTo = "کو جاری کی گئی",
    returnedBy = "کی جانب سے واپس کی گئی",
    step = "مرحلہ",
    of = "از",

    issueSlip = "اجراء کی رسید",
    returnSlip = "واپسی کی رسید",
    receiptNumber = "رسید نمبر",
    libraryName = "GDC لائبریری",
    collegeHeader = "گورنمنٹ ڈگری کالج",
    thankYou = "شکریہ! براہ کرم کتاب وقت پر واپس کریں۔",
    printReceipt = "رسید پرنٹ کریں",
    shareReceipt = "رسید شیئر کریں",

    reports = "رپورٹس",
    overview = "جائزہ",
    totalCollectionValue = "کل مجموعہ کی مالیت",
    issuedThisMonth = "اس ماہ جاری شدہ",
    issuedLastMonth = "پچھلے ماہ جاری شدہ",
    fineThisMonth = "اس ماہ جرمانہ",
    topPublishers = "سرفہرست 10 ناشرین",
    availabilityRatio = "دستیابی کا تناسب",
    mostIssuedBooks = "سب سے زیادہ جاری کی گئی کتابیں",
    mostActiveBorrowers = "سب سے زیادہ فعال قارئین",
    overdueList = "میعاد گزشتہ کتابیں",
    exportPdf = "PDF ایکسپورٹ کریں",

    leaderboard = "لیڈر بورڈ",
    noDataLeaderboard = "ابھی کوئی ڈیٹا نہیں۔ لیڈر بورڈ شروع کرنے کے لیے کتابیں جاری کریں!",

    settings = "ترتیبات",
    language = "زبان",
    languageToggle = "Switch to English / انگریزی میں تبدیل کریں",
    darkMode = "ڈارک موڈ",
    finePerDay = "یومیہ جرمانہ (روپے)",
    borrowDuration = "مستعار مدت (دن)",
    maxBooks = "فی ممبر زیادہ سے زیادہ کتابیں",
    importBooks = "کتابیں درآمد کریں",
    resetDatabase = "ڈیٹا بیس ری سیٹ کریں",
    seedSampleData = "نمونہ ڈیٹا شامل کریں",
    days = "دن",
    rupees = "روپے",

    aboutApp = "ایپ کے بارے میں",
    appVersion = "ورژن 1.0",
    developerInfo = "GDC لائبریری مینجمنٹ کے لیے تیار کردہ۔",
    techStack = "ٹیک اسٹیک: Kotlin, Jetpack Compose, Room, Hilt, ML Kit",
    exportDataCsv = "ڈیٹا ایکسپورٹ کریں (CSV)"
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
    return if (lang == AppLanguage.URDU) UrduStrings else EnglishStrings
}
