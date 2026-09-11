package app.xpod.ui

enum class StatusSeverity {
  Info,
  Error,
}

data class UiStatus(
    val message: String,
    val severity: StatusSeverity = StatusSeverity.Info,
)
