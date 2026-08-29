package com.nuvio.app.features.settings

import com.nuvio.app.features.player.desktop.DesktopHostOs
import java.awt.Taskbar
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import kotlin.streams.toList

internal object MacAppIconUpdater {
    fun updateAsync(icon: AppIconOption, onComplete: () -> Unit) {
        Thread({
            try {
                update(icon)
            } finally {
                onComplete()
            }
        }, "Nuvio macOS app icon updater").apply { isDaemon = false }.start()
    }

    private fun update(icon: AppIconOption) {
        if (DesktopHostOs.current != DesktopHostOs.MACOS) return
        runCatching {
            // 1. Instantly update live macOS Dock icon
            val pngPath = "icons/app-icon-${icon.key}-transparent.png"
            loadResourceStream(pngPath)?.use { stream ->
                val img = ImageIO.read(stream)
                if (img != null && Taskbar.isTaskbarSupported()) {
                    val taskbar = Taskbar.getTaskbar()
                    if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                        taskbar.iconImage = img
                    }
                }
            }

            // 2. Persist to .app bundle Resources if installed
            val appBundle = applicationBundle() ?: return@runCatching
            val resourcesDir = appBundle.resolve("Contents/Resources")
            if (Files.isDirectory(resourcesDir)) {
                val icnsPath = "icons/app-icon-${icon.key}-transparent.icns"
                val icnsBytes = loadResourceStream(icnsPath)?.use { it.readAllBytes() }
                if (icnsBytes != null && icnsBytes.isNotEmpty()) {
                    val existingIcns = Files.list(resourcesDir).use { stream ->
                        stream.filter { it.fileName.toString().endsWith(".icns") }.toList()
                    }
                    if (existingIcns.isNotEmpty()) {
                        existingIcns.forEach { file ->
                            Files.write(file, icnsBytes)
                        }
                    } else {
                        Files.write(resourcesDir.resolve("KhaYin Admin.icns"), icnsBytes)
                        Files.write(resourcesDir.resolve("KhaYin.icns"), icnsBytes)
                        Files.write(resourcesDir.resolve("nuvio-app-icon.icns"), icnsBytes)
                    }
                }
            }

            runCatching {
                ProcessBuilder("touch", appBundle.toString()).start().waitFor()
            }
        }
    }

    private fun loadResourceStream(resourcePath: String): InputStream? {
        return MacAppIconUpdater::class.java.classLoader?.getResourceAsStream(resourcePath)
            ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(resourcePath)
            ?: MacAppIconUpdater::class.java.getResourceAsStream("/$resourcePath")
    }

    fun restartAsync() {
        Thread({
            runCatching {
                Thread.sleep(800)
                val appBundle = applicationBundle()
                if (appBundle != null) {
                    ProcessBuilder("open", "-n", appBundle.toString()).start()
                }
                kotlin.system.exitProcess(0)
            }
        }, "Nuvio macOS app restart").apply {
            isDaemon = false
            start()
        }
    }

    private fun applicationBundle(): Path? {
        var path = System.getProperty("compose.application.home")?.let(Path::of) ?: return null
        while (true) {
            if (path.fileName?.toString()?.endsWith(".app") == true) return path
            path = path.parent ?: return null
        }
    }
}