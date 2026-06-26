package com.zeerqi27.etoilebridge.core

import com.charleskorn.kaml.decodeFromStream
import com.zeerqi27.etoilebridge.core.etoile.EtoileYaml
import com.zeerqi27.etoilebridge.core.etoile.ProjectInformation
import java.io.File

object ArcCreateProjectReader {
    fun read(projectFile: File): ProjectInformation =
        projectFile.inputStream().use { input ->
            EtoileYaml.decodeFromStream(ProjectInformation.serializer(), input)
        }
}
