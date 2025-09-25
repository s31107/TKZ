package CustomComponents;

import API.IconsManager;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.util.ResourceBundle;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static javax.swing.text.Highlighter.Highlight;

public class ConsoleLog extends JPanel {
    private static final int maxLength = 1000;
    protected final JTextArea jTextArea;
    protected final JPanel searchPanel;
    protected final JTextField searchField;
    private int lastIndexOfHighlighted;

    public ConsoleLog(IconsManager iconsManager, ResourceBundle resourceBundle,
                      Supplier<Boolean> isNotOpenSearchBoxStrategy) {
        // Setting global layout:
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        // Console log area:
        jTextArea = new JTextArea();
        jTextArea.setEditable(false);
        jTextArea.setLineWrap(true);
        jTextArea.setWrapStyleWord(true);
        JScrollPane jScrollPane = new JScrollPane(jTextArea);
        jScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        add(jScrollPane);
        // Search panel and it's components:
        searchPanel = new JPanel();
        searchPanel.setLayout(new BoxLayout(searchPanel, BoxLayout.X_AXIS));
        searchField = new JTextField();
        JButton upButton = new JButton(iconsManager.getIcon("up"));
        JButton downButton = new JButton(iconsManager.getIcon("down"));
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, upButton.getPreferredSize().height));
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(upButton, BorderLayout.EAST);
        searchPanel.add(downButton, BorderLayout.EAST);
        add(searchPanel);
        // Hiding search panel:
        searchPanel.setVisible(false);
        // Adding key triggers to a show/close search panel:
        // Console log area triggers:
        jTextArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                // CTRL + F:
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_F) {
                    // Hide:
                    if (searchPanel.isVisible()) { closeSearchPanel(); }
                    // Show:
                    else { openSearchPanel(isNotOpenSearchBoxStrategy); }
                }
                // ESCAPE (hide):
                else if (searchPanel.isVisible() && e.getKeyCode() == KeyEvent.VK_ESCAPE) { closeSearchPanel(); }
            }
        });
        // Search field area triggers:
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                // ENTER (find):
                if (e.getKeyCode() == KeyEvent.VK_ENTER) { search(searchField.getText()); }
                // CTRL+F, ESCAPE (close):
                else if (searchPanel.isVisible() && (e.getKeyCode() == KeyEvent.VK_ESCAPE
                        || (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_F))) { closeSearchPanel(); }
            }
        });
        // Console popup menu:
        JPopupMenu consolePopupMenu = new JPopupMenu();
        JMenuItem consoleCopyItem = new JMenuItem(
                resourceBundle.getString("copy"), iconsManager.getIcon("copy"));
        consolePopupMenu.add(consoleCopyItem);
        jTextArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { showPopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { showPopup(e); }

            private void showPopup(MouseEvent e) {
                // Skipping if not an opening popup menu:
                if (!e.isPopupTrigger()) { return; }
                // Enabling/Disabling copy menu item if no text is selected:
                consoleCopyItem.setEnabled(
                        jTextArea.getSelectedText() != null && !jTextArea.getSelectedText().isEmpty());
                // Showing popup menu:
                consolePopupMenu.show(jTextArea, e.getX(), e.getY());
            }
        });
        consoleCopyItem.addActionListener(_ -> jTextArea.copy());
        // Search field area popup menu:
        JPopupMenu searchFieldPopupMenu = new JPopupMenu();
        JMenuItem searchFieldCopyItem = new JMenuItem(
                resourceBundle.getString("copy"), iconsManager.getIcon("copy"));
        JMenuItem searchFieldCutItem = new JMenuItem(
                resourceBundle.getString("cut"), iconsManager.getIcon("cut"));
        JMenuItem searchFieldPasteItem = new JMenuItem(
                resourceBundle.getString("paste"), iconsManager.getIcon("paste"));
        searchFieldPopupMenu.add(searchFieldCopyItem);
        searchFieldPopupMenu.add(searchFieldCutItem);
        searchFieldPopupMenu.add(searchFieldPasteItem);
        searchField.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { showPopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { showPopup(e); }

            private void showPopup(MouseEvent e) {
                // Skipping if not an opening popup menu:
                if (!e.isPopupTrigger()) { return; }
                // Enabling/Disabling copy and cut menu items if no text is selected:
                if (searchField.getSelectedText() != null && !searchField.getSelectedText().isEmpty()) {
                    searchFieldCopyItem.setEnabled(true);
                    searchFieldCutItem.setEnabled(true);
                } else {
                    searchFieldCopyItem.setEnabled(false);
                    searchFieldCutItem.setEnabled(false);
                }
                // Enabling/Disabling paste menu item by clipboard state:
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                Transferable contents = clipboard.getContents(null);
                searchFieldPasteItem.setEnabled(
                        contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor));
                // Showing popup menu:
                searchFieldPopupMenu.show(searchField, e.getX(), e.getY());
            }
        });
        searchFieldCopyItem.addActionListener(_ -> searchField.copy());
        searchFieldCutItem.addActionListener(_ -> searchField.cut());
        searchFieldPasteItem.addActionListener(_ -> searchField.paste());
        // Buttons connections:
        upButton.addActionListener(_ -> navigate(-1));
        downButton.addActionListener(_ -> navigate(1));
    }

    private void openSearchPanel(Supplier<Boolean> isNotOpen) {
        if (isNotOpen.get()) { return; }
        // Resetting the last saved index:
        lastIndexOfHighlighted = 0;
        // Showing the panel:
        searchPanel.setVisible(true);
        // Searching current phrase:
        search(searchField.getText());
        // Requesting focus:
        searchField.requestFocus();
    }

    private void closeSearchPanel() {
        // Hiding panel:
        searchPanel.setVisible(false);
        // Clearing all occurrences:
        jTextArea.getHighlighter().removeAllHighlights();
    }

    private void navigate(int direction) {
        // Declaring all pattern highlights:
        Highlight[] highlights = jTextArea.getHighlighter().getHighlights();
        if (highlights.length == 0) { return; }
        // Moving index to next occurrence:
        lastIndexOfHighlighted += direction;
        // Looping occurrence index:
        if (lastIndexOfHighlighted < 0) { lastIndexOfHighlighted = highlights.length - 1; }
        else if (lastIndexOfHighlighted == highlights.length) { lastIndexOfHighlighted = 0; }
        // Moving cursor to next occurrence:
        jTextArea.setCaretPosition(highlights[lastIndexOfHighlighted].getStartOffset());
        // Requesting focus:
        jTextArea.requestFocus();
    }

    private void search(String searchText) {
        if (searchText.isEmpty()) { return; }
        Highlighter highlighter = jTextArea.getHighlighter();
        // Clearing all occurrences:
        highlighter.removeAllHighlights();
        // Compiling and matching pattern:
        Pattern pattern = Pattern.compile(Pattern.quote(searchText), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(jTextArea.getText());
        // Highlighting all occurrences:
        while (matcher.find()) {
            try {
                highlighter.addHighlight(matcher.start(), matcher.end(),
                        new DefaultHighlighter.DefaultHighlightPainter(Color.LIGHT_GRAY));
            } catch (BadLocationException exc) { throw new RuntimeException(exc); }
        }
    }

    public void clear() {
        // Clearing console:
        jTextArea.setText("");
        // Clearing search field:
        searchField.setText("");
        // Closing the search panel if visible:
        if (searchPanel.isVisible()) { closeSearchPanel(); }
    }

    public void addLines(String line) {
        // Adding specified lines:
        jTextArea.append(line);
        // Checking if console log hasn't exceeded maximum lines number:
        try {
            // Declaring document:
            Document doc = jTextArea.getDocument();
            // Getting line count:
            int lineCount = jTextArea.getLineCount();
            // Checking lines number:
            if (lineCount > maxLength) {
                // Removing the oldest lines:
                doc.remove(0, jTextArea.getLineStartOffset(lineCount - maxLength - 1));
            }
        } catch (BadLocationException exc) { throw new RuntimeException(exc); }
    }
}
