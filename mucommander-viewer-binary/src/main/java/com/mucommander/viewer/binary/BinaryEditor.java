/*
 * This file is part of muCommander, http://www.mucommander.com
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mucommander.viewer.binary;

import com.mucommander.commons.file.AbstractFile;
import com.mucommander.commons.file.FileFactory;
import com.mucommander.commons.file.FileOperation;
import com.mucommander.commons.file.protocol.local.LocalFile;
import com.mucommander.commons.runtime.OsFamily;
import com.mucommander.commons.util.ui.dialog.DialogOwner;
import com.mucommander.commons.util.ui.helper.MenuToolkit;
import com.mucommander.commons.util.ui.helper.MnemonicHelper;
import com.mucommander.core.desktop.DesktopManager;
import com.mucommander.desktop.ActionType;
import com.mucommander.job.FileCollisionChecker;
import com.mucommander.text.Translator;
import com.mucommander.ui.dialog.DialogAction;
import com.mucommander.ui.dialog.InformationDialog;
import com.mucommander.ui.dialog.QuestionDialog;
import com.mucommander.ui.dialog.file.FileCollisionDialog;
import com.mucommander.ui.encoding.EncodingMenu;
import com.mucommander.viewer.CloseCancelledException;
import com.mucommander.viewer.EditorPresenter;
import com.mucommander.viewer.FileEditor;
import org.exbin.auxiliary.binary_data.BinaryData;
import org.exbin.auxiliary.binary_data.array.paged.ByteArrayPagedData;
import org.exbin.bined.operation.BinaryDataCommand;
import org.exbin.bined.operation.swing.CodeAreaOperationCommandHandler;
import org.exbin.bined.operation.swing.CodeAreaUndoRedo;
import org.exbin.bined.operation.undo.BinaryDataUndoRedo;
import org.exbin.bined.operation.undo.BinaryDataUndoRedoChangeListener;
import org.exbin.bined.swing.basic.CodeArea;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * General editor for binary files.
 *
 * @author Miroslav Hajda
 */
@ParametersAreNonnullByDefault
class BinaryEditor extends BinaryBase implements FileEditor {

    private static final Logger LOGGER = Logger.getLogger(BinaryEditor.class.getName());
    private EditorPresenter presenter;
    private AbstractFile currentFile;
    private BinaryDataUndoRedo undoRedo;

    private JMenu fileMenu;

    private JMenuItem saveMenuItem;
    private JMenuItem saveAsMenuItem;
    private JMenuItem undoMenuItem;
    private JMenuItem redoMenuItem;
    private JMenuItem cutMenuItem;
    private JMenuItem pasteMenuItem;
    private JMenuItem deleteMenuItem;
    private JMenuItem undoPopupMenuItem;
    private JMenuItem redoPopupMenuItem;
    private JMenuItem cutPopupMenuItem;
    private JMenuItem copyPopupMenuItem;
    private JMenuItem pastePopupMenuItem;
    private JMenuItem deletePopupMenuItem;

    public BinaryEditor() {
        super();

        initMenuBars();
        init();
    }

    private void initMenuBars() {
        MnemonicHelper menuItemMnemonicHelper = new MnemonicHelper();
        fileMenu = new JMenu(Translator.get("binary_editor.file"));

        int metaMask = getMetaMask();
        saveMenuItem = MenuToolkit.addMenuItem(fileMenu,
                Translator.get("binary_editor.save"),
                menuItemMnemonicHelper,
                KeyStroke.getKeyStroke(KeyEvent.VK_S, metaMask),
                e -> saveFile());
        saveAsMenuItem = MenuToolkit.addMenuItem(fileMenu,
                Translator.get("binary_editor.save_as"),
                menuItemMnemonicHelper,
                KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.SHIFT_DOWN_MASK | metaMask),
                e -> saveAsFile());
        cutMenuItem = MenuToolkit.createMenuItem(Translator.get("binary_editor.cut"),
                menuItemMnemonicHelper,
                KeyStroke.getKeyStroke(KeyEvent.VK_X, metaMask),
                e -> binaryComponent.getCodeArea().cut());
        editMenu.add(cutMenuItem, copyMenuItemPosition);
        pasteMenuItem = MenuToolkit.createMenuItem(Translator.get("binary_editor.paste"),
                menuItemMnemonicHelper,
                KeyStroke.getKeyStroke(KeyEvent.VK_V, metaMask),
                e -> binaryComponent.getCodeArea().paste());
        deleteMenuItem = MenuToolkit.createMenuItem(Translator.get("binary_editor.delete"),
                menuItemMnemonicHelper,
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
                e -> binaryComponent.getCodeArea().delete());

        editMenu.add(pasteMenuItem, copyMenuItemPosition + 2);
        editMenu.add(deleteMenuItem, copyMenuItemPosition + 3);

        undoMenuItem = MenuToolkit.createMenuItem(
                Translator.get("binary_editor.undo"),
                menuItemMnemonicHelper,
                DesktopManager.getActionShortcuts().getDefaultKeystroke(ActionType.Undo),
                e -> performUndo());
        redoMenuItem = MenuToolkit.createMenuItem(
                Translator.get("binary_editor.redo"),
                menuItemMnemonicHelper,
                DesktopManager.getActionShortcuts().getDefaultKeystroke(ActionType.Redo),
                e -> performRedo());
        editMenu.add(undoMenuItem, 0);
        editMenu.add(redoMenuItem, 1);
        editMenu.add(new JSeparator(), 2);

        JPopupMenu popupMenu = new JPopupMenu();

        undoPopupMenuItem = MenuToolkit.createMenuItem(
                Translator.get("binary_editor.undo"),
                menuItemMnemonicHelper,
                DesktopManager.getActionShortcuts().getDefaultKeystroke(ActionType.Undo),
                e -> performUndo());
        popupMenu.add(undoPopupMenuItem);
        redoPopupMenuItem = MenuToolkit.createMenuItem(
                Translator.get("binary_editor.redo"),
                menuItemMnemonicHelper,
                DesktopManager.getActionShortcuts().getDefaultKeystroke(ActionType.Redo),
                e -> performRedo());
        popupMenu.add(redoPopupMenuItem);
        popupMenu.addSeparator();
        cutPopupMenuItem = MenuToolkit.createMenuItem(Translator.get("binary_editor.cut"),
                menuItemMnemonicHelper,
                KeyStroke.getKeyStroke(KeyEvent.VK_X, metaMask),
                e -> binaryComponent.getCodeArea().cut());
        popupMenu.add(cutPopupMenuItem);
        copyPopupMenuItem = MenuToolkit.createMenuItem(Translator.get("binary_viewer.copy"),
                menuItemMnemonicHelper,
                KeyStroke.getKeyStroke(KeyEvent.VK_C, getMetaMask()),
                e -> binaryComponent.getCodeArea().copy());
        popupMenu.add(copyPopupMenuItem);
        pastePopupMenuItem = MenuToolkit.createMenuItem(Translator.get("binary_editor.paste"),
                menuItemMnemonicHelper,
                KeyStroke.getKeyStroke(KeyEvent.VK_V, metaMask),
                e -> binaryComponent.getCodeArea().paste());
        popupMenu.add(pastePopupMenuItem);
        deletePopupMenuItem = MenuToolkit.createMenuItem(Translator.get("binary_editor.delete"),
                menuItemMnemonicHelper,
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
                e -> binaryComponent.getCodeArea().delete());
        popupMenu.add(deletePopupMenuItem);
        popupMenu.add(MenuToolkit.createMenuItem(Translator.get("binary_viewer.select_all"),
                menuItemMnemonicHelper,
                KeyStroke.getKeyStroke(KeyEvent.VK_A, getMetaMask()),
                e -> binaryComponent.getCodeArea().selectAll()));

        binaryComponent.getCodeArea().setComponentPopupMenu(popupMenu);
    }

    private void init() {
        CodeArea codeArea = binaryComponent.getCodeArea();
        codeArea.addSelectionChangedListener(this::updateClipboardActionsStatus);

        undoRedo = new CodeAreaUndoRedo(codeArea);
        codeArea.setCommandHandler(new CodeAreaOperationCommandHandler(codeArea, undoRedo));
        undoRedo.addChangeListener(new BinaryDataUndoRedoChangeListener() {
            @Override
            public void undoChanged() {
                updateUndoStatus();
            }
        });
        updateUndoStatus();

        updateClipboardActionsStatus();
    }

    private void performUndo() {
        undoRedo.performUndo();
    }

    private void performRedo() {
        undoRedo.performRedo();
    }

    private void updateUndoStatus() {
        undoMenuItem.setEnabled(undoRedo.canUndo());
        redoMenuItem.setEnabled(undoRedo.canRedo());
        undoPopupMenuItem.setEnabled(undoRedo.canUndo());
        redoPopupMenuItem.setEnabled(undoRedo.canRedo());

        // Marks/unmarks the window as dirty under Mac OS X (symbolized by a dot in the window closing icon)
        if (OsFamily.MAC_OS.isCurrent()) {
            if (windowFrame != null) {
                JRootPane rootPane = windowFrame.getRootPane();
                if (rootPane != null) {
                    rootPane.putClientProperty("windowModified", isSaveNeeded());
                }
            }
        }
    }

    private void updateClipboardActionsStatus() {
        CodeArea codeArea = binaryComponent.getCodeArea();
        boolean hasSelection = codeArea.hasSelection();
        copyMenuItem.setEnabled(hasSelection);
        cutMenuItem.setEnabled(hasSelection);
        deleteMenuItem.setEnabled(hasSelection);
        copyPopupMenuItem.setEnabled(hasSelection);
        cutPopupMenuItem.setEnabled(hasSelection);
        deletePopupMenuItem.setEnabled(hasSelection);
    }

    @Override
    public void extendMenu(JMenuBar menuBar) {
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(viewMenu);

        EncodingMenu encodingMenu = new EncodingMenu(new DialogOwner(presenter.getWindowFrame()),
                binaryComponent.getCodeArea().getCharset().name());
        encodingMenu.addEncodingListener((source, oldEncoding, newEncoding) -> changeEncoding(newEncoding));
        menuBar.add(encodingMenu);
    }

    private synchronized void loadFile(AbstractFile file) throws IOException {
        final IOException[] operationException = new IOException[1];
        presenter.longOperation(() -> {
            ByteArrayPagedData data = new ByteArrayPagedData();
            try (InputStream in = file.getInputStream()) {
                data.loadFromStream(in);

                currentFile = file;
                binaryComponent.getCodeArea().setContentData(data);
                undoRedo.setSyncPosition();
                notifyOrigFileChanged();
                binaryComponent.updateCurrentMemoryMode();
            } catch (IOException ex) {
                operationException[0] = ex;
            }
        });

        if (operationException[0] != null)
            throw operationException[0];
    }

    private synchronized void saveFile(AbstractFile file) throws IOException {
        final IOException[] operationException = new IOException[1];
        presenter.longOperation(() -> {
            try (OutputStream out = file.getOutputStream()) {
                BinaryData data = Objects.requireNonNull(binaryComponent.getCodeArea().getContentData());
                data.saveToStream(out);
                currentFile = file;
                undoRedo.setSyncPosition();
                notifyOrigFileChanged();

                // Change the parent folder's date to now, so that changes are picked up by folder auto-refresh
                if (file.isFileOperationSupported(FileOperation.CHANGE_DATE)) {
                    try {
                        file.getParent().changeDate(System.currentTimeMillis());
                    } catch (IOException e) {
                        // Fail silently
                        LOGGER.log(Level.FINE, "failed to change the date of " + file, e);
                    }
                }
            } catch (IOException ex) {
                operationException[0] = ex;
            }
        });

        if (operationException[0] != null)
            throw operationException[0];
    }

    public void saveFile() {
        trySave(currentFile);
    }

    public void saveAsFile() {
        JFileChooser fileChooser = new JFileChooser();
        // Sets selected file in JFileChooser to current file
        if (currentFile.getURL().getScheme().equals(LocalFile.SCHEMA)) {
            fileChooser.setSelectedFile(new java.io.File(currentFile.getAbsolutePath()));
        }
        fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
        int ret = fileChooser.showSaveDialog(windowFrame);

        if (ret == JFileChooser.APPROVE_OPTION) {
            AbstractFile destFile;
            try {
                destFile = FileFactory.getFile(fileChooser.getSelectedFile().getAbsolutePath(), true);
            } catch (IOException e) {
                InformationDialog.showErrorDialog(windowFrame,
                        Translator.get("write_error"),
                        Translator.get("file_editor.cannot_write"));
                return;
            }

            // Check for file collisions, i.e. if the file already exists in the destination
            int collision = FileCollisionChecker.checkForCollision(null, destFile);
            if (collision != FileCollisionChecker.NO_COLLISION) {
                // File already exists in destination, ask the user what to do (cancel, overwrite,...) but
                // do not offer the multiple file mode options such as 'skip' and 'apply to all'.
                DialogAction action = new FileCollisionDialog(windowFrame,
                        windowFrame/* mainFrame */,
                        collision,
                        null,
                        destFile,
                        false,
                        false).getActionValue();

                if (action != FileCollisionDialog.FileCollisionAction.OVERWRITE) {
                    return;
                }
            }

            trySave(destFile);
        }
    }

    @Override
    public void open(AbstractFile file) throws IOException {
        loadFile(file);
    }

    protected boolean isSaveNeeded() {
        return undoRedo.getSyncPosition() != undoRedo.getCommandPosition();
    }

    /**
     * Checks whether file can be closed and asks for confirmation if necessary.
     *
     * @return true if the file does not have any unsaved change or if the user opted to save the changes, false if the
     *         user canceled the dialog or the save failed.
     */
    public boolean canClose() {
        if (!isSaveNeeded()) {
            return true;
        }

        QuestionDialog dialog =
                new QuestionDialog(windowFrame,
                        null,
                        Translator.get("file_editor.save_warning"),
                        binaryComponent,
                        Arrays.asList(BinaryEditorAction.YES, BinaryEditorAction.NO, BinaryEditorAction.CANCEL),
                        0);
        DialogAction ret = dialog.getActionValue();

        if (ret == BinaryEditorAction.YES && trySave(currentFile) || ret == BinaryEditorAction.NO) {
            return true;
        }

        return false; // User canceled or the file couldn't be properly saved
    }

    /**
     * Tries to save file.
     *
     * @return false if an error occurred while saving the file.
     */
    private boolean trySave(AbstractFile destFile) {
        try {
            saveFile(destFile);
            return true;
        } catch (IOException e) {
            InformationDialog.showErrorDialog(windowFrame,
                    Translator.get("write_error"),
                    Translator.get("file_editor.cannot_write"));
            return false;
        }
    }

    @Override
    public void close() throws CloseCancelledException {
        if (!canClose()) {
            throw new CloseCancelledException();
        }

        ByteArrayPagedData data = Objects.requireNonNull((ByteArrayPagedData) binaryComponent.getCodeArea().getContentData());
        data.dispose();
    }

    @Nonnull
    @Override
    public JComponent getUI() {
        return binaryComponent;
    }

    @Override
    public void setPresenter(EditorPresenter presenter) {
        setWindowFrame(presenter.getWindowFrame());
        this.presenter = presenter;
    }

    public enum BinaryEditorAction implements DialogAction {
        YES("save"),
        NO("dont_save"),
        CANCEL("cancel");

        private final String actionName;

        BinaryEditorAction(@Nonnull String actionKey) {
            this.actionName = Translator.get(actionKey);
        }

        @Nonnull
        @Override
        public String getActionName() {
            return actionName;
        }
    }
}
