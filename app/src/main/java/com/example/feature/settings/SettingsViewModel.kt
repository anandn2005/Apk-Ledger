package com.example.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.example.data.BusinessSummary
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class SettingsScreenUiState(
    val businessName: String = "",
    val businessPhone: String = "",
    val businessAddress: String = "",
    val currencySymbol: String = "₹",
    val upiId: String = "",
    val gstin: String = "",
    val isDarkMode: Boolean = false,
    val selectedLanguage: AppLanguage = AppLanguage.ENGLISH,
    val summary: BusinessSummary = BusinessSummary()
)

/**
 * Unidirectional state for the Settings & Reports feature (#8, #17, #19).
 */
class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsScreenUiState())
    val uiState: StateFlow<SettingsScreenUiState> = _uiState.asStateFlow()
}
