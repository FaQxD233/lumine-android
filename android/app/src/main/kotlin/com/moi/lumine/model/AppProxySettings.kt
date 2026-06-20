package com.moi.lumine.model

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

enum class AppProxyMode {
    All,
    BypassSelected,
    OnlySelected
}
