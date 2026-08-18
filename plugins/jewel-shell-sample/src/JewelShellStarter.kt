// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jewelShellSample

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.ide.lightEdit.LightEditServiceImpl
import com.intellij.idea.IdeStarter
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Boots the full IntelliJ Platform (services, VFS, PSI, indexing) and presents a Jewel-rendered window
 * instead of the standard IDE shell. Modeled on [IdeStarter.StandaloneLightEditStarter]: by overriding
 * [openProjectIfNeeded], the standard path (welcome frame, `IdeProjectFrameAllocator`, `IdeFrameImpl`,
 * `IdeRootPane`, tool window panes, `EditorsSplitters`) is never executed.
 *
 * Activate by starting the IDE with the `jewelShell` command-line argument, optionally followed by file paths.
 */
internal class JewelShellStarter : IdeStarter() {
  override suspend fun openProjectIfNeeded(
    args: List<String>,
    app: Application,
    coroutineScope: CoroutineScope,
    publisher: AppLifecycleListener,
  ) {
    // Reuse the LightEdit project container: a lightweight Project created directly, bypassing
    // ProjectManager.openProject and therefore the standard frame allocation.
    val project = (serviceAsync<LightEditService>() as LightEditServiceImpl).getOrCreateProject()

    // args[0] is the "jewelShell" command itself; the rest are files to open
    val filePaths = args.drop(1).mapNotNull { runCatching { Path.of(it).toAbsolutePath() }.getOrNull() }

    withContext(Dispatchers.EDT) {
      val window = JewelShellWindow(project)
      window.show()
      for (path in filePaths) {
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.let(window::openFile)
      }
    }
  }
}
