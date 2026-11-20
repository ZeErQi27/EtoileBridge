package com.zeerqi27.etoilebridge.core

import java.io.File

data class PreprocessResult(
    val outputFile: File,
    val stats: PreprocessStats,
    val warnings: List<String>,
)

data class PreprocessStats(
    val deletedDesignantLines: Int = 0,
    val fixedZeroDurationArcTaps: Int = 0,
    val fixedReversedArcTimes: Int = 0,
)

class Preprocessor {
    fun preprocessAffFiles(
        affFiles: Map<Int, File>,
        processedDir: File,
        options: ConvertOptions,
        logger: LogCollector,
    ): Map<Int, PreprocessResult> {
        processedDir.mkdirs()
        return affFiles.mapValues { (ratingClass, input) ->
            val output = processedDir.resolve("${ratingClass}.aff")
            val result = preprocess(input.readText(Charsets.UTF_8), options, input.name)
            output.writeText(result.content, Charsets.UTF_8)
            logger.log(
                "Preprocessed ${input.name}: designant=${result.stats.deletedDesignantLines}, " +
                    "zeroArcTap=${result.stats.fixedZeroDurationArcTaps}, " +
                    "reversedArc=${result.stats.fixedReversedArcTimes}"
            )
            PreprocessResult(output, result.stats, result.warnings)
        }
    }

    fun preprocess(content: String, options: ConvertOptions, sourceName: String = "<memory>"): PreprocessedContent {
        val newline = detectNewline(content)
        val endedWithNewline = content.endsWith("\n") || content.endsWith("\r")
        val lines = content.split(Regex("\\r\\n|\\n|\\r"))
            .let { if (endedWithNewline) it.dropLast(1) else it }

        var stats = PreprocessStats()
        val warnings = mutableListOf<String>()

        val out = mutableListOf<String>()
        for ((lineIndex, originalLine) in lines.withIndex()) {
            if (options.enableDeleteDesignantLine && originalLine.contains("designant", ignoreCase = true)) {
                stats = stats.copy(deletedDesignantLines = stats.deletedDesignantLines + 1)
                continue
            }

            if (!options.enableFixZeroDurationArcTap && !options.enableFixReversedArcTime) {
                out += originalLine
                continue
            }

            val mutation = rewriteArcs(
                line = originalLine,
                fixZeroDurationArcTap = options.enableFixZeroDurationArcTap,
                fixReversedArcTime = options.enableFixReversedArcTime,
                sourceName = sourceName,
                lineNumber = lineIndex + 1,
            )
            stats = stats.copy(
                fixedZeroDurationArcTaps = stats.fixedZeroDurationArcTaps + mutation.fixedZeroDurationArcTaps,
                fixedReversedArcTimes = stats.fixedReversedArcTimes + mutation.fixedReversedArcTimes,
            )
            warnings += mutation.warnings
            out += mutation.line
        }

        val rebuilt = out.joinToString(newline) + if (endedWithNewline) newline else ""
        return PreprocessedContent(rebuilt, stats, warnings)
    }

    private fun detectNewline(content: String): String = when {
        "\r\n" in content -> "\r\n"
        "\r" in content -> "\r"
        else -> "\n"
    }

    private fun rewriteArcs(
        line: String,
        fixZeroDurationArcTap: Boolean,
        fixReversedArcTime: Boolean,
        sourceName: String,
        lineNumber: Int,
    ): ArcLineMutation {
        val warnings = mutableListOf<String>()
        val builder = StringBuilder()
        var cursor = 0
        var fixedZero = 0
        var fixedReversed = 0

        while (true) {
            val arcStart = line.indexOf("arc(", cursor)
            if (arcStart < 0) {
                builder.append(line.substring(cursor))
                break
            }
            val open = arcStart + 3
            val close = findMatchingParen(line, open)
            if (close == null) {
                warnings += "$sourceName:$lineNumber unable to safely parse arc; kept original line."
                builder.append(line.substring(cursor))
                break
            }

            builder.append(line.substring(cursor, open + 1))
            val params = splitCommaSeparated(line.substring(open + 1, close)).toMutableList()
            if (params.size < 10) {
                warnings += "$sourceName:$lineNumber arc has ${params.size} params; kept arc params unchanged."
                builder.append(line.substring(open + 1, close))
                cursor = close
                continue
            }

            val start = params[0].trim().toLongOrNull()
            val end = params[1].trim().toLongOrNull()
            val tail = line.substring(close + 1).takeWhile { it != ';' } +
                line.substring(close + 1).takeIf { it.contains(';') }?.substringBefore(';').orEmpty()
            val hasArcTap = tail.contains("arctap(")

            if (start != null && end != null) {
                if (fixZeroDurationArcTap && start == end && hasArcTap) {
                    params[1] = (end + 1).toString()
                    fixedZero += 1
                } else if (fixReversedArcTime && start > end) {
                    params[0] = end.toString()
                    params[1] = start.toString()
                    val xStart = params[2]
                    params[2] = params[3]
                    params[3] = xStart
                    val yStart = params[5]
                    params[5] = params[6]
                    params[6] = yStart
                    fixedReversed += 1
                }
            } else {
                warnings += "$sourceName:$lineNumber arc timing is not numeric; kept timing unchanged."
            }

            builder.append(params.joinToString(","))
            cursor = close
        }

        return ArcLineMutation(builder.toString(), fixedZero, fixedReversed, warnings)
    }

    private fun splitCommaSeparated(value: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in value) {
            when (ch) {
                '(' -> {
                    depth += 1
                    current.append(ch)
                }
                ')' -> {
                    depth -= 1
                    current.append(ch)
                }
                ',' -> {
                    if (depth == 0) {
                        result += current.toString()
                        current.clear()
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        result += current.toString()
        return result
    }

    private fun findMatchingParen(line: String, openIndex: Int): Int? {
        var depth = 0
        for (i in openIndex until line.length) {
            when (line[i]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return i
                }
            }
        }
        return null
    }
}

data class PreprocessedContent(
    val content: String,
    val stats: PreprocessStats,
    val warnings: List<String>,
)

private data class ArcLineMutation(
    val line: String,
    val fixedZeroDurationArcTaps: Int,
    val fixedReversedArcTimes: Int,
    val warnings: List<String>,
)
