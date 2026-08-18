// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jewelShellSample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun ShellToolbar(
  projectName: String,
  selectedFile: State<VirtualFile?>,
  onOpen: () -> Unit,
  onSaveAll: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth()
      .background(JewelTheme.globalColors.panelBackground)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("Jewel Shell", fontWeight = FontWeight.Bold)
    Text(projectName, color = JewelTheme.globalColors.text.disabled)
    Spacer(Modifier.weight(1f))
    Text(
      text = selectedFile.value?.presentableUrl ?: "No file selected",
      color = JewelTheme.globalColors.text.disabled,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(2f, fill = false),
    )
    OutlinedButton(onClick = onOpen) { Text("Open File…") }
    OutlinedButton(onClick = onSaveAll) { Text("Save All") }
  }
}

@Composable
internal fun ShellSidebar(
  files: List<VirtualFile>,
  selectedFile: State<VirtualFile?>,
  onSelect: (VirtualFile) -> Unit,
) {
  Column(
    Modifier.fillMaxSize()
      .background(JewelTheme.globalColors.panelBackground)
      .padding(8.dp)
  ) {
    GroupHeader("Open Files")
    if (files.isEmpty()) {
      Text(
        "Nothing open yet",
        color = JewelTheme.globalColors.text.disabled,
        modifier = Modifier.padding(8.dp),
      )
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
      for (file in files) {
        val isSelected = file == selectedFile.value
        val background =
          if (isSelected) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.25f) else Color.Transparent
        Text(
          text = file.name,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clickable { onSelect(file) }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        )
      }
    }
  }
}

@Composable
internal fun ShellEmptyState() {
  Box(
    Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text("No file open", fontWeight = FontWeight.Bold)
      Text(
        "Use “Open File…” above — files open in a real platform editor inside this Jewel shell.",
        color = JewelTheme.globalColors.text.disabled,
      )
    }
  }
}
