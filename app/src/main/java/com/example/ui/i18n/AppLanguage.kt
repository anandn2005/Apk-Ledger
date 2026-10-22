package com.example.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "Hindi", "हिंदी"),
    SPANISH("es", "Spanish", "Español"),
    CHINESE("zh", "Chinese", "中文"),
    RUSSIAN("ru", "Russian", "Русский");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.ENGLISH }

@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLanguage provides language) {
        content()
    }
}

val currentAppLanguage: AppLanguage
    @Composable
    @ReadOnlyComposable
    get() = LocalAppLanguage.current
