// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jewelShellSample

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.ui.OnePixelSplitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
internal class JewelShellWindow(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
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
      // Platform components hosted outside the standard IDE shell still expect an IdeGlassPane on the
      // root pane (OnePixelDivider, editor drag handling, and others look it up via IdeGlassPaneUtil).
      // IdeFrameImpl installs one; this window has to do it itself.
      rootPane.glassPane = IdeGlassPaneImpl(rootPane).also { it.isVisible = true }
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

      // The Compose panels measure themselves when they are first realized, which can happen before
      // the frame has its final bounds — the content then gets laid out against the panel's
      // effectively unbounded preferred width (16383) instead of the real one, and everything the
      // toolbar places after its Spacer(weight(1f)) (the file path and the buttons) ends up far off
      // screen. Invalidating once the frame is showing forces one more layout pass at the real width.
      contentPane.invalidate()
      validate()
      repaint()
    }
  }

  /**
   * Opens [file] in a real platform editor, following the sequence `PsiAwareTextEditorProvider` uses:
   * everything expensive — reading the document text and building the syntax highlighter — happens off
   * the EDT under a read lock, and the EDT is entered only to construct the editor, which `EditorImpl`
   * requires (`EditorImpl.assertIsDispatchThread`).
   *
   * Doing this work on the EDT instead trips `SlowOperations.assertSlowOperationsAreAllowed`:
   * `getDocument` reads file contents through the VFS, and `createEditorHighlighter` resolves the file
   * type, which can block on things like TextMate bundle initialization.
   */
  suspend fun openFile(file: VirtualFile) {
    if (file.isDirectory) return
    if (file !in editors) {
      val fileDocumentManager = serviceAsync<FileDocumentManager>()
      val document = readAction { fileDocumentManager.getDocument(file) } ?: return

      val colorScheme = serviceAsync<EditorColorsManager>().globalScheme
      val highlighterFactory = serviceAsync<EditorHighlighterFactory>()
      val highlighter = readActionBlocking {
        highlighterFactory.createEditorHighlighter(file, colorScheme, project)
      }
      // Priming the highlighter here keeps the lexing off the EDT; setHighlighter would
      // otherwise do it during editor construction.
      highlighter.setText(document.immutableCharSequence)

      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          // createMainEditor is the only entry point that accepts a pre-built highlighter. The public
          // createEditor(document, project, file, isViewer) overload builds one itself, on the calling
          // thread, which would put the work we just moved off the EDT straight back onto it.
          // @ApiStatus.Internal, like the LightEditServiceImpl cast in JewelShellStarter.
          val editor = (EditorFactory.getInstance() as EditorFactoryImpl)
            .createMainEditor(document, project, file, highlighter, null)
          editor.settings.apply {
            isLineNumbersShown = true
            isFoldingOutlineShown = true
          }
          editors[file] = editor
          editorHost.add(editor.component, file.url)
          openFiles.add(file)
        }
      }
    }
    withContext(Dispatchers.EDT) { selectFile(file) }
  }

  private fun selectFile(file: VirtualFile) {
    selectedFile.value = file
    editorCards.show(editorHost, file.url)
    editors[file]?.contentComponent?.requestFocusInWindow()
  }

  private fun chooseAndOpenFile() {
    // The chooser is modal and must run on the EDT (this is a Compose click handler, so it already is);
    // the open itself is suspending, so it is launched rather than awaited here.
    val file = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileDescriptor(), project, null)
               ?: return
    coroutineScope.launch { openFile(file) }
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
