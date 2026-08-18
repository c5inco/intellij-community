// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jewelShell

import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.JetBrainsProductProperties
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import java.nio.file.Path

/**
 * Product definition for JewelShell: a standalone tool built on the IntelliJ Platform core whose entire
 * UI shell is the Jewel-rendered window from the `intellij.jewelShellSample` plugin. Installs and runs
 * side by side with other IntelliJ-based IDEs — its own platform prefix, system selector (config/cache
 * directories), and launcher, the same way Android Studio coexists with IntelliJ IDEA.
 *
 * Like MPS, this product opts out of the Product DSL generator ([getProductContentDescriptor] returns
 * null); the core plugin descriptor `META-INF/JewelShellPlugin.xml` is hand-written from the committed
 * generated module-set XMLs (essential.minimal + compose).
 */
class JewelShellProperties : JetBrainsProductProperties() {
  init {
    platformPrefix = "JewelShell"
    applicationInfoModule = "intellij.jewelShell.customization"
    // Boot straight into the jewelShell ApplicationStarter on a plain double-click launch
    mainClassName = "com.intellij.jewelShell.JewelShellMain"
    scrambleMainJar = false
    useSplash = false
    embeddedFrontendRootModule = null

    productLayout.productImplementationModules = listOf(
      "intellij.platform.starter",
      "intellij.jewelShell.customization",
    )

    productLayout.bundledPluginModules += sequenceOf(
      "intellij.jewelShellSample",
      "intellij.textmate",
    )

    // Plugins from intellij-community may refer to modules that only exist in the ultimate project
    productLayout.skipUnresolvedContentModules = true
    productLayout.buildAllCompatiblePlugins = false
    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
  }

  override val baseFileName: String
    get() = "jewelshell"

  override val customProductCode: String
    get() = "JS"

  override fun getProductContentDescriptor(): ProductModulesContentSpec? = null

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "JewelShell${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "jewelshell-$buildNumber"

  // Per-OS installers need real branding assets (ico/icns/installer images); start with the plain
  // archive distributions and add customizers modeled on communityWindowsCustomizer & co. when needed.
  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer? = null

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer = LinuxDistributionCustomizer()

  override fun createMacCustomizer(projectHome: Path): MacDistributionCustomizer? = null
}
