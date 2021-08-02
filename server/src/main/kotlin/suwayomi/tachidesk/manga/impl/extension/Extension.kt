package suwayomi.tachidesk.manga.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource
import mu.KotlinLogging
import suwayomi.tachidesk.manga.impl.util.PackageTools.EXTENSION_FEATURE
import suwayomi.tachidesk.manga.impl.util.PackageTools.LIB_VERSION_MAX
import suwayomi.tachidesk.manga.impl.util.PackageTools.LIB_VERSION_MIN
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_SOURCE_CLASS
import suwayomi.tachidesk.manga.impl.util.PackageTools.dex2jar
import suwayomi.tachidesk.manga.impl.util.PackageTools.getPackageInfo
import suwayomi.tachidesk.manga.impl.util.PackageTools.loadExtensionSources
import java.io.File

object Extension {
    private val logger = KotlinLogging.logger {}

    fun isNsfw(sourceInstance: Any): Boolean {
        // Annotations are proxied, hence this janky way of checking for them
        return sourceInstance.javaClass.annotations
            .flatMap { it.javaClass.interfaces.map { it.simpleName } }
            .firstOrNull { it == Nsfw::class.java.simpleName } != null
    }

    data class LoadedSource(
        val source: HttpSource,
        val isNsfw: Boolean
    ) {
        constructor(source: HttpSource) :
            this(source, isNsfw(source))
    }

    suspend fun installAPK(tmpDir: File, fetcher: suspend () -> File): Pair<String, List<LoadedSource>> {
        val apkFile = fetcher()

        val jarFile = File(tmpDir, "${apkFile.nameWithoutExtension}.jar")

        val packageInfo = getPackageInfo(apkFile.absolutePath)

        if (!packageInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }) {
            throw Exception("This apk is not a Tachiyomi extension")
        }

        // Validate lib version
        val libVersion = packageInfo.versionName.substringBeforeLast('.').toDouble()
        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            throw Exception(
                "Lib version is $libVersion, while only versions " +
                    "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed"
            )
        }

        /*val signatureHash = getSignatureHash(packageInfo)

        if (signatureHash == null) {
            throw Exception("Package $pkgName isn't signed")
        } else if (signatureHash !in trustedSignatures) {
            // TODO: allow trusting keys
            throw Exception("This apk is not a signed with the official tachiyomi signature")
        }*/

        val className = packageInfo.packageName + packageInfo.applicationInfo.metaData.getString(METADATA_SOURCE_CLASS)

        logger.trace("Main class for extension is $className")

        dex2jar(apkFile, jarFile)

        // collect sources from the extension
        return packageInfo.packageName to when (val instance = loadExtensionSources(jarFile.absolutePath, className)) {
            is Source -> listOf(instance).filterIsInstance<HttpSource>()
                .map { LoadedSource(it) }
            is SourceFactory -> {
                val isNsfw = isNsfw(instance)
                instance.createSources().filterIsInstance<HttpSource>()
                    .map {
                        if (isNsfw) {
                            LoadedSource(it, true)
                        } else {
                            LoadedSource(it)
                        }
                    }
            }
            else -> throw RuntimeException("Unknown source class type! ${instance.javaClass}")
        }
    }
}
