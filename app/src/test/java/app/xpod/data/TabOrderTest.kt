package app.xpod.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderTest {
  @Test
  fun missingAndUnknownTabsAreNormalized() {
    assertEquals(
        listOf(AppTab.Memos, AppTab.Podcasts, AppTab.Reader, AppTab.Library, AppTab.Settings),
        parseTabOrder("Memos,Unknown,Podcasts,Memos"),
    )
  }

  @Test
  fun tabsCanMoveWithinBounds() {
    val moved = moveTab(defaultTabOrder, AppTab.Memos, -2)

    assertEquals(
        listOf(AppTab.Podcasts, AppTab.Memos, AppTab.Reader, AppTab.Library, AppTab.Settings),
        moved,
    )
    assertEquals(moved, moveTab(moved, AppTab.Podcasts, -1))
    assertEquals(moved, moveTab(moved, AppTab.Settings, 1))
  }

  @Test
  fun disabledTabsIgnoreUnknownValuesAndKeepSettingsAvailable() {
    assertEquals(
        setOf(AppTab.Reader, AppTab.Memos),
        parseDisabledTabs("Reader,Unknown,Settings,Memos,Reader"),
    )
    assertEquals(emptySet<AppTab>(), parseDisabledTabs(null))
  }
}
