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
    appName = "College Library",
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
    libraryName = "College Library",
    collegeHeader = "Higher Education Department, Khyber Pakhtunkhwa",
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
    languageToggle = "اردو میں بدلیں / Switch to Urdu",
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
    appVersion = "Version 1.0",
    developerInfo = "Built for the Government Degree College libraries of Khyber Pakhtunkhwa.",
    techStack = "Tech Stack: Kotlin, Jetpack Compose, Room, Hilt, ML Kit",
    exportDataCsv = "Export Data (CSV)"
)

val UrduStrings = AppStrings(
    appName = "لائبریری سسٹم",
    ok = "ٹھیک ہے",
    cancel = "منسوخ",
    back = "واپس",
    done = "مکمل",
    save = "محفوظ کریں",
    search = "تلاش",
    share = "شیئر کریں",
    print = "پرنٹ",
    download = "ڈاؤن لوڈ",
    loading = "لوڈ ہو رہا ہے…",
    error = "خرابی",
    success = "کامیاب",

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
    overdueBooks = "زائد المیعاد",
    fineCollected = "جمع شدہ جرمانہ",
    quickActions = "فوری اقدامات",

    books = "کتابیں",
    addBook = "کتاب شامل کریں",
    editBook = "کتاب میں ترمیم",
    bookTitle = "کتاب کا نام",
    author = "مصنف",
    isbn = "آئی ایس بی این",
    publisher = "ناشر",
    edition = "ایڈیشن",
    price = "قیمت",
    status = "حالت",
    available = "دستیاب",
    issued = "جاری شدہ",

    members = "ممبران",
    addMember = "ممبر شامل کریں",
    editMember = "ممبر میں ترمیم",
    memberName = "پورا نام",
    memberId = "ممبر شناختی نمبر",
    department = "شعبہ",
    email = "ای میل",
    phone = "فون",
    memberType = "ممبر کی قسم",

    issueBook = "کتاب جاری کریں",
    returnBook = "کتاب واپس کریں",
    bulkIssue = "اجتماعی اجرا",
    selectBook = "کتاب منتخب کریں",
    selectMember = "ممبر منتخب کریں",
    confirmIssue = "اجرا کی تصدیق کریں",
    issueDate = "تاریخ اجرا",
    dueDate = "واپسی کی تاریخ",
    returnDate = "تاریخ واپسی",
    bookIssuedSuccess = "کتاب کامیابی سے جاری کر دی گئی!",
    bookReturnedSuccess = "کتاب کامیابی سے واپس ہو گئی!",
    fine = "جرمانہ",
    noFine = "کوئی جرمانہ نہیں",
    fineAmount = "جرمانے کی رقم",
    issuedTo = "کو جاری کی گئی",
    returnedBy = "کی طرف سے واپس",
    step = "مرحلہ",
    of = "از",

    issueSlip = "اجرا کی پرچی",
    returnSlip = "واپسی کی پرچی",
    receiptNumber = "رسید نمبر",
    libraryName = "کالج لائبریری",
    collegeHeader = "محکمہ اعلیٰ تعلیم، خیبر پختونخوا",
    thankYou = "شکریہ! براہِ کرم کتاب وقت پر واپس کریں۔",
    printReceipt = "رسید پرنٹ کریں",
    shareReceipt = "رسید شیئر کریں",

    reports = "رپورٹس",
    overview = "جائزہ",
    totalCollectionValue = "کل ذخیرے کی مالیت",
    issuedThisMonth = "اس ماہ جاری شدہ",
    issuedLastMonth = "گزشتہ ماہ جاری شدہ",
    fineThisMonth = "جرمانہ (اس ماہ)",
    topPublishers = "سرِفہرست 10 ناشرین",
    availabilityRatio = "دستیابی کا تناسب",
    mostIssuedBooks = "سب سے زیادہ جاری ہونے والی کتابیں",
    mostActiveBorrowers = "سب سے فعال قارئین",
    overdueList = "زائد المیعاد کتابیں",
    exportPdf = "پی ڈی ایف برآمد کریں",

    leaderboard = "درجہ بندی",
    noDataLeaderboard = "ابھی کوئی ڈیٹا نہیں۔ درجہ بندی شروع کرنے کے لیے کتابیں جاری کریں!",

    settings = "ترتیبات",
    language = "زبان",
    languageToggle = "Switch to English",
    darkMode = "ڈارک موڈ",
    finePerDay = "یومیہ جرمانہ (روپے)",
    borrowDuration = "مدتِ ادھار (دن)",
    maxBooks = "فی ممبر زیادہ سے زیادہ کتابیں",
    importBooks = "کتابیں درآمد کریں",
    resetDatabase = "ڈیٹابیس ری سیٹ کریں",
    seedSampleData = "نمونہ ڈیٹا شامل کریں",
    days = "دن",
    rupees = "روپے",

    aboutApp = "ایپ کے بارے میں",
    appVersion = "ورژن 1.0",
    developerInfo = "خیبر پختونخوا کے سرکاری کالجوں کی لائبریریوں کے لیے تیار کردہ۔",
    techStack = "ٹیکنالوجی: Kotlin، Jetpack Compose، Room، Hilt، ML Kit",
    exportDataCsv = "ڈیٹا برآمد کریں (CSV)"
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
