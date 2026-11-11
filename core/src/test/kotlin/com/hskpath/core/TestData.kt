package com.hskpath.core

import java.io.File

/** Loads the real lesson data that ships in the app. */
object TestData {
    val content: Content by lazy {
        val dir = File(System.getProperty("assetsDir") ?: "../app/src/main/assets")
        Content.load { File(dir, it).readText() }
    }
}
