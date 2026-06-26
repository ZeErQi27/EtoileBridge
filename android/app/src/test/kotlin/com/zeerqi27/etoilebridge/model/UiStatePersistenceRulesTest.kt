package com.zeerqi27.etoilebridge.model

import com.zeerqi27.etoilebridge.ui.ImageDetailLayoutMode
import com.zeerqi27.etoilebridge.ui.ImageDetailLayoutRules
import com.zeerqi27.etoilebridge.ui.imagePreviewCacheKey
import com.zeerqi27.etoilebridge.ui.localizeKnownMessage
import com.zeerqi27.etoilebridge.ui.packText
import com.zeerqi27.etoilebridge.ui.textFor
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UiStatePersistenceRulesTest {
    @Test
    fun singleImagePreviewPathsAreStoredInUiState() {
        val state = UiConvertState(
            resourceStatus = UiResourceStatus(
                jacketFilePath = "E:/cache/song/1080_base.jpg",
                backgroundFilePath = "E:/cache/song/bg.jpg",
            ),
        )

        assertEquals("E:/cache/song/1080_base.jpg", state.resourceStatus.jacketFilePath)
        assertEquals("E:/cache/song/bg.jpg", state.resourceStatus.backgroundFilePath)
    }

    @Test
    fun packImagePreviewPathIsStoredInPackUiState() {
        val state = UiPackConvertState(packImageFilePath = "E:/cache/pack/pack.png")

        assertEquals("E:/cache/pack/pack.png", state.packImageFilePath)
    }

    @Test
    fun imagePreviewKeyUsesPathLastModifiedAndFileSize() {
        val file = File.createTempFile("etoilebridge-preview", ".png")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            file.setLastModified(1000L)
            val first = imagePreviewCacheKey(file.absolutePath, 320, 240)

            file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            file.setLastModified(2000L)
            val second = imagePreviewCacheKey(file.absolutePath, 320, 240)

            assertEquals(file.absolutePath, first.path)
            assertNotEquals(first, second)
            assertEquals(5L, second.fileSizeBytes)
            assertEquals(2000L, second.lastModified)
        } finally {
            file.delete()
        }
    }

    @Test
    fun advancedInfoTextUsesDetailsInsteadOfErrorDetailsForNormalDebugPanel() {
        val zh = textFor(UiLanguage.ZhHans)
        val en = textFor(UiLanguage.English)

        assertEquals("显示详细信息", zh.showDetails)
        assertEquals("收起详细信息", zh.hideDetails)
        assertEquals("Show details", en.showDetails)
        assertEquals("Hide details", en.hideDetails)
        assertTrue(zh.showErrorDetails.contains("错误"))
    }

    @Test
    fun topBarContentRulesUseRoundedViewportInsteadOfFadeScrim() {
        assertTrue(ResponsiveLayoutRules.TopContentPaddingDp >= 12)
        assertTrue(ResponsiveLayoutRules.ContentViewportCornerDp >= 28)
        assertEquals(32, ResponsiveLayoutRules.ContentViewportCornerDp)
    }

    @Test
    fun imageDetailDialogUsesResponsiveLayoutAndFitPreview() {
        assertEquals(ImageDetailLayoutMode.Compact, ImageDetailLayoutRules.modeForWidth(412))
        assertEquals(ImageDetailLayoutMode.Medium, ImageDetailLayoutRules.modeForWidth(700))
        assertEquals(ImageDetailLayoutMode.Expanded, ImageDetailLayoutRules.modeForWidth(900))
        assertFalse(ImageDetailLayoutRules.useSideBySide(700))
        assertTrue(ImageDetailLayoutRules.useSideBySide(900))
        assertEquals("Fit", ImageDetailLayoutRules.DetailContentScale)
        assertFalse(ImageDetailLayoutRules.PathInitiallyExpanded)
    }

    @Test
    fun packPageEnglishTextUsesEnglishForPrimaryLabels() {
        val en = packText(UiLanguage.English)
        val joined = listOf(
            en.pageTitle,
            en.officialMode,
            en.arcpkgMode,
            en.selectOfficialZip,
            en.selectOfficialFolder,
            en.selectArcpkgs,
            en.packSettings,
            en.packCover,
            en.levelList,
            en.startBundling,
            en.noBundleableItems,
            en.bundleValidationPassed,
            en.bundleValidationFailed,
            en.copyLogsAndScan,
        ).joinToString("\n")

        assertFalse(Regex("[\\u4e00-\\u9fff]").containsMatchIn(joined))
    }

    @Test
    fun singleSongEnglishTextUsesEnglishForPrimaryLabels() {
        val en = textFor(UiLanguage.English)
        val joined = listOf(
            en.pageTitle,
            en.noInputTitle,
            en.noInputDetail,
            en.metadata,
            en.publisherId,
            en.levelId,
            en.title,
            en.artist,
            en.difficulty,
            en.jacket,
            en.background,
            en.resources,
            en.appearance,
            en.preprocessOptions,
            en.convertAndSave,
            en.advancedInfo,
            en.showDetails,
            en.hideDetails,
            en.imageDetails,
            en.changeImage,
            en.selectAudio,
            en.selectJacket,
            en.selectBackground,
            en.identified,
            en.notDetected,
            en.imagePreviewFailed,
        ).joinToString("\n")

        assertFalse(Regex("[\\u4e00-\\u9fff]").containsMatchIn(joined))
    }

    @Test
    fun singleSongChineseTextLocalizesCommonLabels() {
        val zh = textFor(UiLanguage.ZhHans)

        assertEquals("警告", zh.warnings)
        assertEquals("日志", zh.logs)
        assertEquals("错误", zh.error)
        assertEquals("显示详细信息", zh.showDetails)
        assertEquals("收起详细信息", zh.hideDetails)
        assertEquals("显示详细路径", zh.showDetailedPath)
        assertEquals("隐藏详细路径", zh.hideDetailedPath)
    }

    @Test
    fun chineseWarningLabelsAndKnownWarningPrefixesAreLocalized() {
        val zh = packText(UiLanguage.ZhHans)

        assertEquals("警告", zh.warning)
        assertEquals(
            "已忽略非标准 AFF 文件：2-extra.aff",
            localizeKnownMessage("Ignored non-standard AFF file: 2-extra.aff", UiLanguage.ZhHans),
        )
    }
}
