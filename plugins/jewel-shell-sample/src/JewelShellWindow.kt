// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jewelShellSample

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import org.jetbrains.jewel.bridge.compose
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel

/**
 * The custom shell: a plain [JFrame] whose chrome (header toolbar, file sidebar) is rendered by
 * Jewel/Compose via the IDE LaF bridge, while each opened file is displayed in a real platform editor
 * (`EditorImpl`) hosted as a Swing island. The standard IDE shell classes are never instantiated.
 *
 * Compose and Swing share the AWT EDT, so the Compose snapshot state below may be mutated directly
 * from Swing/IDE callbacks, and Compose click handlers may call Swing code directly.
 */
internal class JewelShellWindow(private val project: Project) {
  private val openFiles = mutableStateListOf<VirtualFile>()
  private val selectedFile = mutableStateOf<VirtualFile?>(null)
  private val editors = LinkedHashMap<VirtualFile, Editor>()

  private val frame = JFrame("Jewel Shell")
  private val editorCards = CardLayout()
  private val editorHost = JPanel(editorCards)

  fun show() {
    editorHost.add(compose { ShellEmptyState() }, EMPTY_CARD)

    val toolbar = compose {
      ShellToolbar(
        projectName = project.name,
        selectedFile = selectedFile,
        onOpen = ::chooseAndOpenFile,
        onSaveAll = ::saveAll,
      )
    }
    val sidebar = compose {
      ShellSidebar(files = openFiles, selectedFile = selectedFile, onSelect = ::selectFile)
    }

    val splitter = OnePixelSplitter(false, 0.22f).apply {
      firstComponent = sidebar
      secondComponent = editorHost
    }

    with(frame) {
      contentPane.layout = BorderLayout()
      contentPane.add(toolbar, BorderLayout.NORTH)
      contentPane.add(splitter, BorderLayout.CENTER)
      minimumSize = Dimension(640, 400)
      setSize(1100, 750)
      setLocationRelativeTo(null)
      defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
      addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent) = exit()
      })
      isVisible = true
    }
  }

  fun openFile(file: VirtualFile) {
    if (file.isDirectory) return
    if (file !in editors) {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return
      val editor = EditorFactory.getInstance().createEditor(document, project, file, false) as EditorEx
      editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
      editor.settings.apply {
        isLineNumbersShown = true
        isFoldingOutlineShown = true
      }
      editors[file] = editor
      editorHost.add(editor.component, file.url)
      openFiles.add(file)
    }
    selectFile(file)
  }

  private fun selectFile(file: VirtualFile) {
    selectedFile.value = file
    editorCards.show(editorHost, file.url)
    editors[file]?.contentComponent?.requestFocusInWindow()
  }

  private fun chooseAndOpenFile() {
    FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileDescriptor(), project, null)
      ?.let(::openFile)
  }

  private fun saveAll() {
    FileDocumentManager.getInstance().saveAllDocuments()
  }

  private fun exit() {
    saveAll()
    val editorFactory = EditorFactory.getInstance()
    editors.values.forEach(editorFactory::releaseEditor)
    editors.clear()
    frame.dispose()
    ApplicationManager.getApplication().exit()
  }
}

private const val EMPTY_CARD = "<empty>"
