package CustomComponents;

import API.IconsManager;
import Utils.SimplePair;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class PathJTable extends JTable {
    // Const variables:
    public final static int cursorRowDifference = 5;
    public final static int headerTextSize = 14;
    public final static int contentTextSize = 12;
    public final static int minimalRowSize = 15;
    public final static int startRowSize = 25;
    public final static int numberFrameSize = 20;

    // Resize variables:
    private int draggingRow = -1;
    private int startY = -1;
    private int startHeight = -1;

    // Managers:
    protected final IconsManager iconsManager;
    protected final DefaultTableModel tableModel;
    // Popup menu:
    protected final JPopupMenu popupMenu;
    protected final JMenuItem addMenuItem;
    protected final JMenuItem removeMenuItem;

    private class resizeableMotionListener extends MouseAdapter {
        @Override
        public void mouseMoved(MouseEvent e) {
            // Computing pointing row:
            int row = PathJTable.this.rowAtPoint(e.getPoint());
            if (row == -1) { return; }
            // Computing bottom frame of row:
            int rowBottom = PathJTable.this.getCellRect(row, 0, true).y
                    + PathJTable.this.getRowHeight(row);
            // Changing type of cursor if cursor points to bottom frame of row:
            PathJTable.this.setCursor(Math.abs(e.getY() - rowBottom) < cursorRowDifference ?
                    Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : Cursor.getDefaultCursor());
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            // If dragging row:
            if (draggingRow != -1) {
                // Computing next bottom row position:
                int newHeight = startHeight + (e.getY() - startY);
                // Moving if not exceeded minimal row size:
                if (newHeight > minimalRowSize) { PathJTable.this.setRowHeight(draggingRow, newHeight); }
            }
        }
    }

    private class resizeableMouseListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            // Displaying popup menu:
            if (e.isPopupTrigger()) {
                popupMenuManagement(e);
                return;
            }
            // Computing pointing row:
            int row = PathJTable.this.rowAtPoint(e.getPoint());
            if (row == -1) { return; }
            // Computing bottom frame of row:
            int rowBottom = PathJTable.this.getCellRect(
                    row, 0, true).y + PathJTable.this.getRowHeight(row);
           // Initializing dragging row:
            if (Math.abs(e.getY() - rowBottom) < cursorRowDifference) {
                draggingRow = row;
                startY = e.getY();
                startHeight = PathJTable.this.getRowHeight(row);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            // Leaving resized row:
            draggingRow = -1;
        }
    }


    private class fileChooserEditor extends DefaultCellEditor {
        private final JTextField textField;
        private final JPanel panel;
        private final JFileChooser fileChooser;

        public fileChooserEditor() {
            super(new JTextField());
            // Declaring cell content:
            textField = (JTextField) getComponent();
            JButton button = new JButton(iconsManager.getIcon("browse"));
            fileChooser = new JFileChooser(System.getProperty("user.home"));
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            // Adding cell content to panel:
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            panel.add(textField);
            panel.add(Box.createHorizontalGlue());
            panel.add(button);
            // Button connection:
            button.addActionListener(e -> getPath());
        }

        private void getPath() {
            // Placing path from file chooser to text field:
            if (fileChooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                textField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            } fireEditingStopped();
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int column) {
            // Placing text from renderer to text field:
            if (value != null) { textField.setText(value.toString()); }
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            // Clearing text field before exit:
            String path = textField.getText();
            textField.setText("");
            return path;
        }
    }

    public PathJTable(ResourceBundle bundle, IconsManager iconsManagement) {
        // Icons manager initialization:
        iconsManager = iconsManagement;
        // Popup Menu:
        popupMenu = new JPopupMenu();
        addMenuItem = new JMenuItem(bundle.getString("add"), iconsManager.getIcon("add"));
        removeMenuItem = new JMenuItem(bundle.getString("remove"), iconsManager.getIcon("remove"));
        popupMenu.add(addMenuItem);
        popupMenu.add(removeMenuItem);
        addMenuItem.addActionListener(e -> addRow());
        removeMenuItem.addActionListener(e -> {
            int row;
            while ((row = getSelectedRow()) >= 0) { removeRow(row); }
        });
        // Setting table model:
        // Disabling number column edition:
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) { return column != 0; }
        };
        setModel(tableModel);
        // Adding headers:
        for (String iter : new String[] {
                bundle.getString("number"),
                bundle.getString("sourcePath"),
                bundle.getString("destinationPath")
        }) { tableModel.addColumn(iter); }
        // Customization:
        getTableHeader().setFont(getTableHeader().getFont().deriveFont(Font.BOLD | Font.ITALIC, headerTextSize));
        setFont(getFont().deriveFont(Font.PLAIN, contentTextSize));
        setRowHeight(startRowSize);
        // Setting file chooser editor to columns:
        TableCellEditor cellEditor = new fileChooserEditor();
        for (int col = 1; col < 3; ++col) {
            getColumnModel().getColumn(col).setCellEditor(cellEditor);
        }
        // Aligning content of number column to center:
        DefaultTableCellRenderer centerCellRenderer = new DefaultTableCellRenderer();
        centerCellRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        getColumnModel().getColumn(0).setCellRenderer(centerCellRenderer);
        // Adding resizable rows:
        addMouseListener(new resizeableMouseListener());
        addMouseMotionListener(new resizeableMotionListener());
        // Setting minimal size of number column:
        TableCellRenderer headerRenderer = getTableHeader().getDefaultRenderer();
        Component comp = headerRenderer.getTableCellRendererComponent(
                this, getColumnModel().getColumn(0).getHeaderValue(),
                false, false, 0, 0);
        int headerWidth = comp.getPreferredSize().width + numberFrameSize;
        getColumnModel().getColumn(0).setMaxWidth(headerWidth);
    }

    public void popupMenuManagement(MouseEvent event) {
        popupMenu.show(event.getComponent(), event.getX(), event.getY());
        // Disabling remove button if no row is selected:
        removeMenuItem.setEnabled(getSelectedRow() != -1);
    }

    public void addRow(Object... row) {
        Vector<Object> vRow = new Vector<>(List.of(row));
        vRow.addFirst(null);
        tableModel.addRow(vRow);
        // Adding number to row:
        tableModel.setValueAt(getRowCount(), getRowCount() - 1, 0);
    }

    public void removeRow(int row) {
        tableModel.removeRow(row);
        // Decrementing number for each row > deleted row:
        for (int rowIter = row; rowIter < getRowCount(); ++rowIter) {
            tableModel.setValueAt(((int) tableModel.getValueAt(rowIter, 0)) - 1, rowIter, 0);
        }
    }

    public Optional<List<SimplePair<String>>> getPaths() {
        List<SimplePair<String>> values = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); ++row) {
            // Creating a pair of source path - destination path;
            SimplePair<String> pair = new SimplePair<>(Objects.requireNonNullElse(
                    (String) tableModel.getValueAt(row, 1), ""), Objects.requireNonNullElse((String)
                    tableModel.getValueAt(row, 2), ""));
            // If row is empty:
            if (pair.key().isEmpty() && pair.val().isEmpty()) { continue; }
            // If paths are incomplete:
            else if (pair.key().isEmpty() || pair.val().isEmpty()) { return Optional.empty(); }
            values.add(pair);
        } return Optional.of(values);
    }

    public void setPaths(List<SimplePair<String>> paths) {
        // Clearing table:
        tableModel.setRowCount(0);
        // Adding every row to table:
        paths.forEach(row -> addRow(row.key(), row.val()));
    }
}
