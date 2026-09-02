package com.college.library.ui.screens.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.model.OperationsCollections
import com.college.library.data.repository.OperationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One view model per operations collection.
 *
 * They all do the same three things — stream the collection, append a record,
 * patch a record — so the behaviour lives in [BaseOperationsViewModel] and each
 * subclass only names its collection. Hilt cannot inject a generic type
 * parameter, which is why these are separate classes rather than one
 * parameterised view model.
 */
abstract class BaseOperationsViewModel(
    protected val repo: OperationsRepository,
    private val collectionName: String,
) : ViewModel() {

    /** Live records for the active institution, soft-deletes already removed. */
    val records: StateFlow<List<Map<String, Any?>>> =
        repo.observe(collectionName)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val hasInstitution: Boolean get() = repo.institutionId().isNotEmpty()

    fun clearMessage() {
        _message.value = null
    }

    fun add(fields: Map<String, Any?>, successMessage: String = "Saved.") {
        viewModelScope.launch {
            _busy.value = true
            repo.save(collectionName, fields)
                .onSuccess { _message.value = successMessage }
                .onFailure { _message.value = it.message ?: "Could not save." }
            _busy.value = false
        }
    }

    fun patch(syncId: String, fields: Map<String, Any?>, successMessage: String? = null) {
        viewModelScope.launch {
            _busy.value = true
            repo.update(collectionName, syncId, fields)
                .onSuccess { if (successMessage != null) _message.value = successMessage }
                .onFailure { _message.value = it.message ?: "Could not update." }
            _busy.value = false
        }
    }

    fun remove(syncId: String) {
        viewModelScope.launch {
            _busy.value = true
            repo.softDelete(collectionName, syncId)
                .onSuccess { _message.value = "Removed." }
                .onFailure { _message.value = it.message ?: "Could not remove." }
            _busy.value = false
        }
    }
}

@HiltViewModel
class GateLogViewModel @Inject constructor(repo: OperationsRepository) :
    BaseOperationsViewModel(repo, OperationsCollections.VISITOR_LOG)

@HiltViewModel
class AcquisitionsViewModel @Inject constructor(repo: OperationsRepository) :
    BaseOperationsViewModel(repo, OperationsCollections.PURCHASE_ORDERS)

@HiltViewModel
class BookTransfersViewModel @Inject constructor(repo: OperationsRepository) :
    BaseOperationsViewModel(repo, OperationsCollections.BOOK_TRANSFERS) {
    /** Transfers are addressed between colleges, so the screen needs our id. */
    val myCollegeId: String get() = repo.institutionId()
}

@HiltViewModel
class SerialsViewModel @Inject constructor(repo: OperationsRepository) :
    BaseOperationsViewModel(repo, OperationsCollections.SERIALS)

@HiltViewModel
class IllViewModel @Inject constructor(repo: OperationsRepository) :
    BaseOperationsViewModel(repo, OperationsCollections.ILL_REQUESTS)

@HiltViewModel
class OpsWishlistViewModel @Inject constructor(repo: OperationsRepository) :
    BaseOperationsViewModel(repo, OperationsCollections.WISHLIST)

@HiltViewModel
class ReadingRoomViewModel @Inject constructor(repo: OperationsRepository) :
    BaseOperationsViewModel(repo, OperationsCollections.READING_ROOM)

@HiltViewModel
class LostFoundViewModel @Inject constructor(repo: OperationsRepository) :
    BaseOperationsViewModel(repo, OperationsCollections.LOST_FOUND)

@HiltViewModel
class LibraryEventsViewModel @Inject constructor(repo: OperationsRepository) :
    BaseOperationsViewModel(repo, OperationsCollections.EVENTS)
