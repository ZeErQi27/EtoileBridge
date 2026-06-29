package com.zeerqi27.etoilebridge.core

import com.charleskorn.kaml.encodeToStream
import com.tairitsu.compose.parser.ArcCreateChartSerializer
import com.zeerqi27.etoilebridge.core.etoile.A2CConverter
import com.zeerqi27.etoilebridge.core.etoile.ArcpkgEntryType
import com.zeerqi27.etoilebridge.core.etoile.ChartEntry
import com.zeerqi27.etoilebridge.core.etoile.EtoilePackUtil
import com.zeerqi27.etoilebridge.core.etoile.EtoileYaml
import com.zeerqi27.etoilebridge.core.etoile.ImportInformationEntry
import com.zeerqi27.etoilebridge.core.etoile.ProjectInformation
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.ScenecontrolService
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.extractScenecontrols
import com.zeerqi27.etoilebridge.core.etoile.scenecontrol.loadChart
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackEngine {
    fun pack(
        resolvedSong: ResolvedSong,
        outputFile: File,
        warnings: MutableList<String>,
        logger: LogCollector,
        packageOptions: PackageOptions = PackageOptions(),
        appearanceOptions: AppearanceOptions = AppearanceOptions(),
    ): File {
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fileOut ->
            ZipOutputStream(fileOut).use { zip ->
                val songId = resolvedSong.metadata.songId
                val directory = songId
                val identifier = packageOptions.resolvedIdentifier(songId)
                val indexEntry = listOf(
                    ImportInformationEntry(
                        directory = directory,
                        identifier = identifier,
                        settingsFile = "project.arcproj",
                        version = 0,
                        type = ArcpkgEntryType.LEVEL,
                    )
                )
                val convertedCharts = convertCharts(resolvedSong, warnings, logger)
                val project = ProjectInformation(
                    lastOpenedChartPath = lastOpenedChartPath(resolvedSong),
                    charts = buildChartEntries(resolvedSong, warnings, appearanceOptions),
                )

                zip.putNextEntry(ZipEntry("index.yml"))
                EtoileYaml.encodeToStream(indexEntry, zip)
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("$directory/project.arcproj"))
                EtoileYaml.encodeToStream(project, zip)
                zip.closeEntry()

                convertedCharts.forEach { (ratingClass, content) ->
                    zip.putNextEntry(ZipEntry("$directory/$ratingClass.aff"))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }

                val writtenEntries = mutableSetOf<String>()
                collectResourceFiles(resolvedSong).forEach { file ->
                    val entryName = "$directory/${file.name}"
                    if (writtenEntries.add(entryName)) {
                        zip.putNextEntry(ZipEntry(entryName))
                        zip.write(file.readBytes())
                        zip.closeEntry()
                    }
                }

                exportScenecontrols(resolvedSong, warnings, logger).forEach { (ratingClass, content) ->
                    zip.putNextEntry(ZipEntry("$directory/$ratingClass.sc.json"))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
        logger.log("Packed successfully to: ${outputFile.absolutePath}")
        return outputFile
    }

    private fun convertCharts(
        resolvedSong: ResolvedSong,
        warnings: MutableList<String>,
        logger: LogCollector,
    ): List<Pair<Int, String>> = resolvedSong.difficulties.map { diff ->
        val content = diff.affFile.readText(Charsets.UTF_8)
        val converted = try {
            val chart = A2CConverter.parse(content)
            ArcCreateChartSerializer.serialize(chart).joinToString(System.lineSeparator())
        } catch (e: Exception) {
            throw RuntimeException("Error converting ${diff.affFile.name}: ${e.message}", e)
        }
        logger.log("Converted ${diff.affFile.name}")
        diff.metadata.ratingClass to converted
    }.also {
        if (it.isEmpty()) warnings += "No charts were converted."
    }

    private fun buildChartEntries(
        resolvedSong: ResolvedSong,
        warnings: MutableList<String>,
        appearanceOptions: AppearanceOptions,
    ): List<ChartEntry> {
        val song = resolvedSong.metadata
        val charts = resolvedSong.difficulties.map { diff ->
            val meta = diff.metadata
            val bgName = meta.bg ?: song.bg ?: meta.bgInverse ?: song.bgInverse
            ChartEntry(
                chartPath = "${meta.ratingClass}.aff",
                audioPath = diff.audioFile.name,
                jacketPath = diff.jacketFile?.name.orEmpty(),
                backgroundPath = diff.backgroundFile?.name,
                baseBpm = meta.bpmBase ?: song.bpmBase,
                bpmText = meta.bpmText ?: song.bpmText,
                syncBaseBpm = false,
                title = meta.title ?: song.title,
                composer = meta.artist ?: song.artist,
                charter = meta.chartDesigner,
                illustrator = meta.jacketDesigner,
                difficulty = meta.difficulty ?: DifficultyMapper.displayName(meta.ratingClass, meta.rating, meta.ratingPlus),
                chartConstant = meta.chartConstant ?: DifficultyMapper.chartConstant(meta.rating, meta.ratingPlus),
                difficultyColor = EtoilePackUtil.getDifficultyColor(meta.ratingClass),
                skin = EtoilePackUtil.getSkin(song.side, song.songId, song.set, bgName, appearanceOptions),
                previewStart = song.audioPreview,
                previewEnd = song.audioPreviewEnd,
                searchTags = song.searchTags.ifBlank { null },
            )
        }
        if (resolvedSong.metadata.side == 3 && appearanceOptions.side == null) {
            val warning = "Lephon 不受 ArcCreate 支持，已按 Light 处理"
            if (warning !in warnings) warnings += warning
        }
        return charts
    }

    private fun lastOpenedChartPath(resolvedSong: ResolvedSong): String {
        val ratingClasses = resolvedSong.difficulties.map { it.metadata.ratingClass }
        return if (2 in ratingClasses) "2.aff" else "${ratingClasses.last()}.aff"
    }

    private fun collectResourceFiles(resolvedSong: ResolvedSong): List<File> {
        val explicit = resolvedSong.difficulties.flatMap {
            listOfNotNull(it.audioFile, it.jacketFile, it.backgroundFile)
        } + resolvedSong.additionalFiles
        val media = resolvedSong.songDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in mediaExtensions && it.name !in affNames }
            .orEmpty()
        return (explicit + media).distinctBy { it.canonicalFile }
    }

    private fun exportScenecontrols(
        resolvedSong: ResolvedSong,
        warnings: MutableList<String>,
        logger: LogCollector,
    ): List<Pair<Int, String>> = resolvedSong.difficulties.mapNotNull { diff ->
        try {
            val chartContent = diff.affFile.readText(Charsets.UTF_8)
            val timingGroups = loadChart(chartContent)
            val scenecontrols = extractScenecontrols(timingGroups)
            if (scenecontrols.isEmpty()) return@mapNotNull null
            val exported = ScenecontrolService(scenecontrols, timingGroups, diff.metadata.ratingClass).export()
            if (exported == null) {
                null
            } else {
                logger.log("Exported scenecontrol for ${diff.metadata.ratingClass}.aff")
                diff.metadata.ratingClass to exported
            }
        } catch (e: Exception) {
            warnings += "Scenecontrol export failed for ${diff.metadata.ratingClass}.aff: ${e.message}"
            null
        }
    }

    companion object {
        private val mediaExtensions = setOf("ogg", "wav", "jpg", "jpeg", "png")
        private val affNames = (0..4).map { "$it.aff" }.toSet()
    }
}
