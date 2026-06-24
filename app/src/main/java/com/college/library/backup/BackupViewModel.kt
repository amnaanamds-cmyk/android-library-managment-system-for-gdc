package com.college.library.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BackupUiState(
    val backups: List<BackupInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isCreatingBackup: Boolean = false,
    val isExportingJson: Boolean = false,
    val isRestoring: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val message: String? = null,
    val restoreSuccess: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        loadBackups()
        _state.update { it.copy(autoBackupEnabled = backupManager.isAutoBackupEnabled()) }
    }

    fun loadBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val backups = backupManager.listBackups()
            _state.update { it.copy(backups = backups, isLoading = false) }
        }
    }

    fun createBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isCreatingBackup = true) }
            val file = backupManager.createBackup()
            val msg = if (file != null) "Backup created: ${file.name}" else "Backup failed"
            val backups = backupManager.listBackups()
            _state.update {
                it.copy(
                    isCreatingBackup = false,
                    backups = backups,
                    message = msg
                )
            }
        }
    }

    fun exportToJson() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isExportingJson = true) }
            val file = backupManager.exportToJson()
            val msg = if (file != null) "JSON exported: ${file.name}" else "JSON export failed"
            _state.update { it.copy(isExportingJson = false, message = msg) }
        }
    }

    fun restoreFromBackup(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isRestoring = true) }
            val success = backupManager.restoreFromBackup(file)
            val msg = if (success) "Restore successful. Please restart the app." else "Restore failed"
            _state.update {
                it.copy(
                    isRestoring = false,
                    message = msg,
                    restoreSuccess = success
                )
            }
        }
    }

    fun restoreFromExternalFile(file: File) {
        restoreFromBackup(file)
    }

    fun deleteBackup(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            backupManager.deleteBackup(file)
            val backups = backupManager.listBackups()
            _state.update { it.copy(backups = backups, message = "Backup deleted") }
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        backupManager.scheduleAutoBackup(enabled)
        _state.update { it.copy(autoBackupEnabled = enabled) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun clearRestoreSuccess() {
        _state.update { it.copy(restoreSuccess = false) }
    }
}
