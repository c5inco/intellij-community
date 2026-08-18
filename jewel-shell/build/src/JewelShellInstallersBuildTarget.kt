// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jewelShell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.createBuildContext

/**
 * Builds standalone JewelShell distributions (with bundled JBR, which Jewel requires) from community
 * sources. Modeled on [OpenSourceCommunityInstallersBuildTarget].
 */
object JewelShellInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      val options = BuildOptions().apply {
        incrementalCompilation = true
        useCompiledClassesFromProjectOutput = false
        buildStepsToSkip += BuildOptions.MAC_SIGN_STEP
        buildStepsToSkip += BuildOptions.WIN_SIGN_STEP
        buildStepsToSkip += BuildOptions.SOURCES_ARCHIVE_STEP
      }
      val context = createBuildContext(
        projectHome = COMMUNITY_ROOT.communityRoot,
        productProperties = JewelShellProperties(),
        options = options,
      )
      buildDistributions(context)
    }
  }
}
