package com.example.feature.invoices

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.example.data.InvoiceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class InvoicesScreenUiState(
    val invoices: List<InvoiceEntity> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: String = "ALL"
)

/**
 * Unidirectional state for the Invoices & Receipts feature (#8, #17, #28).
 */
class InvoicesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(InvoicesScreenUiState())
    val uiState: StateFlow<InvoicesScreenUiState> = _uiState.asStateFlow()

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setStatusFilter(status: String) {
        _uiState.value = _uiState.value.copy(statusFilter = status)
    }
}
