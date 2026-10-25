package com.example.feature.customers

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.example.data.CustomerWithBalance
import com.example.ui.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

enum class CustomerSortOption(val label: String) {
    RECENT("Recent"),
    HIGHEST_DUE("Highest Due"),
    OLDEST_DUE("Oldest Due"),
    AREA("Area / Village"),
    NAME("Name A-Z")
}

enum class AgeingBucket(val label: String) {
    ALL("All Ages"),
    DAYS_0_30("0–30d"),
    DAYS_30_60("30–60d"),
    DAYS_60_PLUS("60+d Overdue")
}

@Immutable
data class CustomersScreenUiState(
    val customers: List<CustomerWithBalance> = emptyList(),
    val sortOption: CustomerSortOption = CustomerSortOption.RECENT,
    val selectedArea: String = "ALL",
    val selectedAgeingBucket: AgeingBucket = AgeingBucket.ALL,
    val availableAreas: List<String> = emptyList()
)

/**
 * Unidirectional state & helper logic for the Customers feature (#5, #8, #17, #25, #26).
 */
class CustomersViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CustomersScreenUiState())
    val uiState: StateFlow<CustomersScreenUiState> = _uiState.asStateFlow()

    fun setSortOption(option: CustomerSortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option)
    }

    fun setAreaFilter(area: String) {
        _uiState.value = _uiState.value.copy(selectedArea = area)
    }

    fun setAgeingBucket(bucket: AgeingBucket) {
        _uiState.value = _uiState.value.copy(selectedAgeingBucket = bucket)
    }

    companion object {
        private val avatarPalettesLight = listOf(
            Color(0xFFEAF6ED) to Color(0xFF15803D), // Emerald
            Color(0xFFEFF6FF) to Color(0xFF1D4ED8), // Royal Blue
            Color(0xFFFEF3C7) to Color(0xFFB45309), // Amber
            Color(0xFFF3E8FF) to Color(0xFF7E22CE), // Purple
            Color(0xFFFFE4E6) to Color(0xFFBE123C), // Rose
            Color(0xFFE0F2FE) to Color(0xFF0369A1), // Sky
            Color(0xFFCCFBF1) to Color(0xFF0F766E)  // Teal
        )

        private val avatarPalettesDark = listOf(
            Color(0xFF143321) to Color(0xFF4ADE80),
            Color(0xFF172554) to Color(0xFF60A5FA),
            Color(0xFF3F270B) to Color(0xFFFBBF24),
            Color(0xFF2E1065) to Color(0xFFC084FC),
            Color(0xFF4C0519) to Color(0xFFFB7185),
            Color(0xFF082F49) to Color(0xFF38BDF8),
            Color(0xFF134E4A) to Color(0xFF2DD4BF)
        )

        fun avatarColorsForName(name: String, isDark: Boolean): Pair<Color, Color> {
            val palette = if (isDark) avatarPalettesDark else avatarPalettesLight
            val index = abs(name.trim().lowercase().hashCode()) % palette.size
            return palette[index]
        }

        fun computeAgeingDays(item: CustomerWithBalance, nowMs: Long = System.currentTimeMillis()): Int {
            if (item.netBalance <= 0.005) return 0
            val refTs = item.oldestDueDate ?: item.lastTransactionDate ?: item.customer.createdAt
            return Formatters.daysSince(refTs, nowMs)
        }
    }
}
