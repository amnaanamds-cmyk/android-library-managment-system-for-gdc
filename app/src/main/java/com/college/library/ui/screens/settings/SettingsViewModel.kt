package com.college.library.ui.screens.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.college.library.data.db.DataSeeder
import com.college.library.data.db.LibraryDatabase
import com.college.library.data.model.Book
import com.college.library.utils.AppLanguage
import com.college.library.utils.LanguageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DateUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

data class SettingsState(
    val finePerDay: Float = 1.0f,
    val borrowDuration: Int = 14,
    val maxBooks: Int = 3,
    val isImporting: Boolean = false,
    val importSuccessCount: Int = -1,
    val importError: String? = null,
    val isResetting: Boolean = false,
    val resetSuccess: Boolean = false,
    val isSeeding: Boolean = false,
    val seedSuccess: Boolean = false,
    // UI preferences
    val darkModeEnabled: Boolean = false,
    val fontScale: Float = 1.0f,
    val onboardingCompleted: Boolean = false,
    val currentLanguage: AppLanguage = AppLanguage.ENGLISH
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val database: LibraryDatabase,
    private val application: Application,
    val languageManager: LanguageManager
) : ViewModel() {

    private val prefs = application.getSharedPreferences("library_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val fine = prefs.getFloat("fine_per_day", 1.0f)
        val duration = prefs.getInt("borrow_duration", 14)
        val max = prefs.getInt("max_books", 3)
        val darkModeEnabled = prefs.getBoolean("dark_mode_enabled", false)
        val fontScale = prefs.getFloat("font_scale", 1.0f)
        val onboardingDone = prefs.getBoolean("onboarding_completed", false)
        _state.value = _state.value.copy(
            finePerDay = fine,
            borrowDuration = duration,
            maxBooks = max,
            darkModeEnabled = darkModeEnabled,
            fontScale = fontScale,
            onboardingCompleted = onboardingDone,
            currentLanguage = languageManager.currentLanguage.value
        )
    }

    fun setLanguage(language: AppLanguage) {
        languageManager.setLanguage(language)
        _state.value = _state.value.copy(currentLanguage = language)
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode_enabled", enabled).apply()
        _state.value = _state.value.copy(darkModeEnabled = enabled)
    }

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat("font_scale", scale).apply()
        _state.value = _state.value.copy(fontScale = scale)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
        _state.value = _state.value.copy(onboardingCompleted = completed)
    }

    fun saveSettings(fine: Float, duration: Int, max: Int) {
        // Preserve existing UI settings when saving core settings
        val darkMode = _state.value.darkModeEnabled
        val scale = _state.value.fontScale
        prefs.edit()
            .putFloat("fine_per_day", fine)
            .putInt("borrow_duration", duration)
            .putInt("max_books", max)
            .apply()
        _state.value = _state.value.copy(
            finePerDay = fine,
            borrowDuration = duration,
            maxBooks = max,
            darkModeEnabled = darkMode,
            fontScale = scale
        )
    }

    fun importBooksFromFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isImporting = true, importSuccessCount = -1, importError = null)
            try {
                // Detect file type by display name / extension
                val contentResolver = application.contentResolver
                var fileName = ""
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = it.getString(nameIndex)
                        }
                    }
                }

                val isExcel = fileName.endsWith(".xlsx", ignoreCase = true) || 
                              fileName.endsWith(".xls", ignoreCase = true)

                val successCount = if (isExcel) {
                    importBooksFromExcel(uri)
                } else {
                    parseAndImportCsv(uri)
                }

                _state.value = _state.value.copy(
                    isImporting = false,
                    importSuccessCount = successCount,
                    importError = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isImporting = false,
                    importError = e.message ?: "Failed to import books"
                )
            }
        }
    }

    private suspend fun parseAndImportCsv(uri: Uri): Int {
        val contentResolver = application.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Failed to open the CSV file")
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines()

        if (lines.isEmpty()) throw Exception("The selected CSV file is empty")

        // Automatically detect header row and skip it if present
        var startIndex = 0
        val firstLine = lines.first()
        if (firstLine.contains("title", ignoreCase = true) || 
            firstLine.contains("isbn", ignoreCase = true) || 
            firstLine.contains("acc", ignoreCase = true)) {
            startIndex = 1
        }

        val booksToInsert = mutableListOf<Book>()
        var successCount = 0

        for (i in startIndex until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue

            try {
                val tokens = parseCsvLine(line)
                if (tokens.size < 3) continue // Needs at least title, author, isbn/accNo

                val isbn = tokens.getOrNull(0) ?: ""
                val accNo = tokens.getOrNull(1) ?: ""
                val title = tokens.getOrNull(2) ?: ""
                val author = tokens.getOrNull(3) ?: ""
                val publisher = tokens.getOrNull(4) ?: ""
                val publisherPlace = tokens.getOrNull(5) ?: ""
                val publishDate = tokens.getOrNull(6) ?: ""
                val edition = tokens.getOrNull(7) ?: ""
                val pages = tokens.getOrNull(8)?.toIntOrNull() ?: 0
                val procurement = tokens.getOrNull(9) ?: ""
                val volume = tokens.getOrNull(10) ?: ""
                val price = tokens.getOrNull(11)?.toDoubleOrNull() ?: 0.0
                val statusVal = tokens.getOrNull(12)?.trim()
                val status = if (statusVal.isNullOrBlank()) "Available" else statusVal
                val digitalUrlVal = tokens.getOrNull(13)?.trim()
                val isDigital = !digitalUrlVal.isNullOrBlank()

                if (title.isBlank()) continue

                val book = Book(
                    isbn = isbn,
                    accNo = accNo,
                    title = title,
                    author = author,
                    publisher = publisher,
                    publisherPlace = publisherPlace,
                    publishDate = publishDate,
                    edition = edition,
                    pages = pages,
                    procurement = procurement,
                    volume = volume,
                    price = price,
                    status = status,
                    isDigital = isDigital,
                    digitalUrl = if (digitalUrlVal.isNullOrBlank()) null else digitalUrlVal
                )
                booksToInsert.add(book)
                successCount++
            } catch (e: Exception) {
                // Keep parsing other lines
            }
        }

        if (booksToInsert.isNotEmpty()) {
            database.withTransaction {
                booksToInsert.forEach { database.bookDao().insertBook(it) }
            }
        }

        return successCount
    }

    private suspend fun importBooksFromExcel(uri: Uri): Int {
        val contentResolver = application.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Failed to open Excel file")
        
        // Use WorkbookFactory to support both .xls and .xlsx
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0) ?: throw Exception("Excel sheet is empty")

        val booksToInsert = mutableListOf<Book>()
        var successCount = 0

        // Automatically detect header row and skip it if present
        var skipHeader = false
        val firstRow = sheet.getRow(0)
        if (firstRow != null) {
            val cellVal1 = getCellValueAsString(firstRow.getCell(0))
            val cellVal2 = getCellValueAsString(firstRow.getCell(1))
            val cellVal3 = getCellValueAsString(firstRow.getCell(2))
            if (cellVal1.contains("title", ignoreCase = true) || cellVal1.contains("isbn", ignoreCase = true) ||
                cellVal2.contains("title", ignoreCase = true) || cellVal2.contains("isbn", ignoreCase = true) ||
                cellVal3.contains("title", ignoreCase = true) || cellVal3.contains("isbn", ignoreCase = true)) {
                skipHeader = true
            }
        }

        for (row in sheet) {
            if (row.rowNum == 0 && skipHeader) continue

            try {
                // Expected columns:
                // 0: isbn, 1: accNo, 2: title, 3: author, 4: publisher, 5: publisherPlace, 
                // 6: publishDate, 7: edition, 8: pages, 9: procurement, 10: volume, 11: price, 12: status
                val isbn = getCellValueAsString(row.getCell(0))
                val accNo = getCellValueAsString(row.getCell(1))
                val title = getCellValueAsString(row.getCell(2))
                val author = getCellValueAsString(row.getCell(3))
                val publisher = getCellValueAsString(row.getCell(4))
                val publisherPlace = getCellValueAsString(row.getCell(5))
                val publishDate = getCellValueAsString(row.getCell(6))
                val edition = getCellValueAsString(row.getCell(7))

                val pagesVal = getCellValueAsString(row.getCell(8))
                val pages = pagesVal.toDoubleOrNull()?.toInt() ?: pagesVal.toIntOrNull() ?: 0

                val procurement = getCellValueAsString(row.getCell(9))
                val volume = getCellValueAsString(row.getCell(10))

                val priceVal = getCellValueAsString(row.getCell(11))
                val price = priceVal.toDoubleOrNull() ?: 0.0
                val statusVal = getCellValueAsString(row.getCell(12)).trim()
                val status = if (statusVal.isBlank()) "Available" else statusVal
                val digitalUrlVal = getCellValueAsString(row.getCell(13)).trim()
                val isDigital = digitalUrlVal.isNotBlank()

                if (title.isBlank()) continue

                val book = Book(
                    isbn = isbn,
                    accNo = accNo,
                    title = title,
                    author = author,
                    publisher = publisher,
                    publisherPlace = publisherPlace,
                    publishDate = publishDate,
                    edition = edition,
                    pages = pages,
                    procurement = procurement,
                    volume = volume,
                    price = price,
                    status = status,
                    isDigital = isDigital,
                    digitalUrl = if (digitalUrlVal.isBlank()) null else digitalUrlVal
                )
                booksToInsert.add(book)
                successCount++
            } catch (e: Exception) {
                // Keep parsing other rows
            }
        }

        workbook.close()
        inputStream.close()

        if (booksToInsert.isNotEmpty()) {
            database.withTransaction {
                booksToInsert.forEach { database.bookDao().insertBook(it) }
            }
        }

        return successCount
    }

    private fun getCellValueAsString(cell: Cell?): String {
        if (cell == null) return ""
        // Use integer constants for POI 3.17 compatibility (before CellType enum was added)
        return when (cell.cellType) {
            Cell.CELL_TYPE_STRING -> cell.stringCellValue.trim()
            Cell.CELL_TYPE_NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    val date = cell.dateCellValue
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    sdf.format(date)
                } else {
                    val value = cell.numericCellValue
                    if (value == value.toLong().toDouble()) {
                        value.toLong().toString()
                    } else {
                        value.toString()
                    }
                }
            }
            Cell.CELL_TYPE_BOOLEAN -> cell.booleanCellValue.toString()
            Cell.CELL_TYPE_FORMULA -> {
                try {
                    cell.stringCellValue.trim()
                } catch (e: Exception) {
                    try {
                        val value = cell.numericCellValue
                        if (value == value.toLong().toDouble()) {
                            value.toLong().toString()
                        } else {
                            value.toString()
                        }
                    } catch (e2: Exception) { "" }
                }
            }
            else -> ""
        }
    }

    fun seedSampleData() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isSeeding = true, seedSuccess = false)
            try {
                DataSeeder.seedBooks(database)
                _state.value = _state.value.copy(isSeeding = false, seedSuccess = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSeeding = false)
            }
        }
    }

    fun resetDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isResetting = true, resetSuccess = false)
            try {
                database.withTransaction {
                    // Delete in FK-safe order: child tables first
                    database.openHelper.writableDatabase.execSQL("DELETE FROM issued_books")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM books")
                    database.openHelper.writableDatabase.execSQL("DELETE FROM members")
                }
                _state.value = _state.value.copy(isResetting = false, resetSuccess = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isResetting = false,
                    importError = "Reset failed: ${e.message}"
                )
            }
        }
    }

    fun clearStatus() {
        _state.value = _state.value.copy(
            importSuccessCount = -1,
            importError = null,
            resetSuccess = false,
            seedSuccess = false
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        val currentToken = StringBuilder()

        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                tokens.add(currentToken.toString().trim())
                currentToken.setLength(0)
            } else {
                currentToken.append(c)
            }
            i++
        }
        tokens.add(currentToken.toString().trim())
        return tokens
    }
}
