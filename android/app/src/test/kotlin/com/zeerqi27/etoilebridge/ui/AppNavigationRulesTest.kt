package com.zeerqi27.etoilebridge.ui

import com.zeerqi27.etoilebridge.model.UiAppPage
import com.zeerqi27.etoilebridge.model.UiLanguage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationRulesTest {
    @Test
    fun pageMenuOrderIsFixed() {
        assertEquals(
            listOf(UiAppPage.SingleSong, UiAppPage.PackBundle, UiAppPage.Character),
            EtoileAppPages,
        )
        assertEquals(listOf("Single Song", "Pack Editor", "Character Editor"), EtoileAppPages.map { appPageLabel(it, UiLanguage.English) })
        assertEquals(listOf("单曲转换", "曲包编辑", "搭档编辑"), EtoileAppPages.map { appPageLabel(it, UiLanguage.ZhHans) })
        assertEquals(listOf(AppSymbols.Music, AppSymbols.Archive, AppSymbols.Person), EtoileAppPages.map(::appPageIcon))
    }

    @Test
    fun pageSwitchDirectionFollowsFixedOrder() {
        assertTrue(pageSwitchDirection(UiAppPage.SingleSong, UiAppPage.PackBundle) > 0)
        assertTrue(pageSwitchDirection(UiAppPage.PackBundle, UiAppPage.Character) > 0)
        assertTrue(pageSwitchDirection(UiAppPage.Character, UiAppPage.PackBundle) < 0)
        assertTrue(pageSwitchDirection(UiAppPage.PackBundle, UiAppPage.SingleSong) < 0)
        assertEquals(0, pageSwitchDirection(UiAppPage.Character, UiAppPage.Character))
    }

    @Test
    fun allPagesUseTheSharedTopBar() {
        val home = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/ui/HomeScreen.kt").readText()
        val pack = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/ui/PackHomeScreen.kt").readText()
        val character = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/ui/CharacterHomeScreen.kt").readText()

        assertTrue(home.contains("EtoileTopBar("))
        assertTrue(pack.contains("EtoileTopBar("))
        assertTrue(character.contains("EtoileTopBar("))
        assertFalse(home.substringBefore("EtoileTopBar(").contains("TopBar("), "HomeScreen must not call its old page-specific TopBar.")
        assertFalse(pack.substringBefore("EtoileTopBar(").contains("PackTopBar("), "PackHomeScreen must not call its old page-specific TopBar.")
        assertFalse(character.substringBefore("EtoileTopBar(").contains("CharacterTopBar("), "CharacterHomeScreen must not call its old page-specific TopBar.")
    }

    @Test
    fun mainActivityUsesDirectionalAnimatedContentAndEdgeToEdge() {
        val main = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/MainActivity.kt").readText()

        assertTrue(main.contains("enableEdgeToEdge()"))
        assertTrue(main.contains("AnimatedContent("))
        assertTrue(main.contains("pageSwitchDirection(initialState, targetState)"))
        assertTrue(main.contains("slideInHorizontally"))
        assertTrue(main.contains("slideOutHorizontally"))
        assertFalse(main.contains("Crossfade("))
    }

    @Test
    fun pagesDoNotApplyBottomSafeDrawingToRootSurface() {
        listOf("HomeScreen.kt", "PackHomeScreen.kt", "CharacterHomeScreen.kt").forEach { fileName ->
            val source = projectFile("src/main/kotlin/com/zeerqi27/etoilebridge/ui/$fileName").readText()
            assertFalse(source.contains(".windowInsetsPadding(WindowInsets.safeDrawing),"), "$fileName should not pad the root Surface away from the navigation bar.")
            assertTrue(source.contains("WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)"), "$fileName should keep only top/horizontal safe padding on the content column.")
            assertTrue(source.contains("bottomInset + 32.dp"), "$fileName should keep bottom scroll breathing space for gesture navigation.")
        }
    }

    private fun projectFile(pathInApp: String): File {
        val fromAppModule = File(pathInApp)
        if (fromAppModule.exists()) return fromAppModule
        return File("app/$pathInApp")
    }
}
