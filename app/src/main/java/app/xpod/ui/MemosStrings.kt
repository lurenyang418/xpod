package app.xpod.ui

/** Resolves UI strings so ViewModels stay free of Android resources (unit-test friendly). */
fun interface MemosStrings {
  fun get(resId: Int, vararg formatArgs: Any): String
}
