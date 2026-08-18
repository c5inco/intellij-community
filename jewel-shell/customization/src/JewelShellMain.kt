// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JewelShellMain")
package com.intellij.jewelShell

import com.intellij.idea.main as platformMain

private const val JEWEL_SHELL_COMMAND = "jewelShell"

/**
 * Product entry point (see `JewelShellProperties.mainClassName`): always boots through the `jewelShell`
 * [com.intellij.openapi.application.ApplicationStarter], so a double-click launch of the installed
 * distribution opens the Jewel shell directly, with no command-line argument needed.
 *
 * This replaces LightEdit's approach — a hardcoded platform-prefix check in
 * `ApplicationLoader.createDefaultAppStarter` — with a mechanism a product can own.
 */
fun main(rawArgs: Array<String>) {
  val args = if (rawArgs.firstOrNull() == JEWEL_SHELL_COMMAND) rawArgs else arrayOf(JEWEL_SHELL_COMMAND, *rawArgs)
  platformMain(args)
}
