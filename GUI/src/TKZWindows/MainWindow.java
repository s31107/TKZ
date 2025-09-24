package TKZWindows;

import API.BackupStrategy;
import API.IconsManager;
import API.ProfileManager;
import CustomComponents.PathJTable;
import Utils.BadPathsException;
import Utils.ExtendedPair;
import Utils.FileFormatException;
import Utils.SimplePair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;

public class MainWindow {
    private final static double xWindowProp = 800.;
    private final static double yWindowProp = 600.;
    private final static double xWindowPattern = 1680.;
    private final static double yWindowPattern = 1050.;
    private final static int xWindowBorderProp = 12;
    private final static int yWindowBorderProp = 10;
    private final static String shutdownAttributeName = "ShutDown";
    private final static String backupStrategyAttributeName = "BackupStrategy";
    protected final ResourceBundle contentsResourceBundle;
    protected final ProfileManager profileManager;
    protected final IconsManager iconsManager;
    protected final BackupWindow backupWindow;
    protected final JFrame jFrame;
    protected final BackupStrategy[] availableBackups;

    public MainWindow(IconsManager iconsManagerStrategy, ProfileManager profileManagerStrategy,
                      BackupStrategy[] availableBackupsStrategies) {
        // Global variables:
        jFrame = new JFrame();
        iconsManager = iconsManagerStrategy;
        profileManager = profileManagerStrategy;
        availableBackups = availableBackupsStrategies;
        // Loading language resource bundle:
        try {
            contentsResourceBundle = ResourceBundle.getBundle("WindowContents");
        } catch (Exception exc) {
            JOptionPane.showMessageDialog(null,
                    "Unexpected error with language packages:" + exc, "Error", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(exc);
        }
        // Initializing backup window:
        backupWindow = new BackupWindow(this, contentsResourceBundle, iconsManager);
        // Executing gui:
        execGui(); show();
    }

    protected void execGui() {
        // Main layouts:
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        // Menu Bar:
        JMenuBar menuBar = new JMenuBar();
        // Menus:
        JMenu fileMenu = new JMenu(contentsResourceBundle.getString("file"));
        JMenu helpMenu = new JMenu(contentsResourceBundle.getString("help"));
        // Menu items:
        JMenuItem openMenuItem = new JMenuItem(contentsResourceBundle.getString("open"),
                iconsManager.getIcon("open"));
        JMenuItem saveMenuItem = new JMenuItem(contentsResourceBundle.getString("save"),
                iconsManager.getIcon("save"));
        JMenuItem saveAsMenuItem = new JMenuItem(contentsResourceBundle.getString("saveAs"),
                iconsManager.getIcon("save-as"));
        JMenuItem aboutMenuItem = new JMenuItem(contentsResourceBundle.getString("about"),
                iconsManager.getIcon("about"));
        // Adding elements to Menu Bars:
        helpMenu.add(aboutMenuItem);
        fileMenu.add(openMenuItem);
        fileMenu.add(saveMenuItem);
        fileMenu.add(saveAsMenuItem);
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        // Label:
        JLabel backUpType = new JLabel(contentsResourceBundle.getString("backUpType"));
        // ComboBox:
        DefaultComboBoxModel<String> backUpTypesModel = new DefaultComboBoxModel<>();
        JComboBox<String> backUpTypes = new JComboBox<>(backUpTypesModel);
        for (BackupStrategy backupStrategy : availableBackups) {
            backUpTypes.addItem(backupStrategy.getBackupType());
        }
        // Adding Label and ComboBox:
        JPanel backUpTypesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        backUpTypesPanel.add(backUpType);
        backUpTypesPanel.add(backUpTypes);
        mainPanel.add(backUpTypesPanel);
        // JTable:
        PathJTable jTable = new PathJTable(contentsResourceBundle, iconsManager);
        JScrollPane jScrollPane = new JScrollPane(jTable);
        jScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        mainPanel.add(jScrollPane);
        // CheckBox:
        JCheckBox turnOffCheckBox = new JCheckBox(contentsResourceBundle.getString("turnOff"));
        JPanel checkBoxPanel = new JPanel();
        checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.X_AXIS));
        checkBoxPanel.add(turnOffCheckBox);
        checkBoxPanel.add(Box.createHorizontalGlue());
        mainPanel.add(checkBoxPanel);
        // JButton:
        JButton execBackupButton = new JButton(contentsResourceBundle.getString("execute"),
                iconsManager.getIcon("execute"));
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(execBackupButton);
        mainPanel.add(buttonPanel);
        // Window configuration:
        int xWindowBorder = (int) (
                Toolkit.getDefaultToolkit().getScreenSize().width * xWindowBorderProp / xWindowPattern);
        int yWindowBorder = (int) (
                Toolkit.getDefaultToolkit().getScreenSize().height * yWindowBorderProp / yWindowPattern);
        mainPanel.setBorder(
                BorderFactory.createEmptyBorder(yWindowBorder, xWindowBorder, yWindowBorder, xWindowBorder));
        jFrame.add(mainPanel);
        jFrame.setJMenuBar(menuBar);
        jFrame.setMinimumSize(new Dimension((int) (Toolkit.getDefaultToolkit().getScreenSize().width * xWindowProp
                / xWindowPattern), (int) (Toolkit.getDefaultToolkit().getScreenSize().height * yWindowProp
                / yWindowPattern)));
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.setTitle(contentsResourceBundle.getString("mainWindowTitle"));
        // Opening last used profile if exists:
        profileManager.openLastUsedFile().ifPresent(fileContent ->
                setContentFromProfileManager(fileContent, jTable, backUpTypesModel, turnOffCheckBox));
        // Connections:
        // Popup Menu:
        jScrollPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                if (e.isPopupTrigger()) { jTable.popupMenuManagement(e); }
            }
        });
        // Menu items:
        saveMenuItem.addActionListener(_ -> saveStrategy(false, jTable,
                (String) Objects.requireNonNull(backUpTypes.getSelectedItem()), turnOffCheckBox.isSelected(), jFrame));
        saveAsMenuItem.addActionListener(_ -> saveStrategy(true, jTable,
                (String) Objects.requireNonNull(backUpTypes.getSelectedItem()), turnOffCheckBox.isSelected(), jFrame));
        openMenuItem.addActionListener(_ -> openProfile(jTable, turnOffCheckBox, backUpTypesModel, jFrame));
        aboutMenuItem.addActionListener(_ -> openDocumentation());
        // Button:
        execBackupButton.addActionListener(_ -> {
            // Backup Window preparing strategy:
            if (availableBackups.length == 0) {
                throw new RuntimeException("There's no available backups to run! " +
                        "Provide any BackupStrategy service to execute this part of program.");
            }
            // Getting chosen backup:
            BackupStrategy chosenBackup = availableBackups[backUpTypes.getSelectedIndex()];
            jTable.getPaths().ifPresentOrElse(paths -> {
                try {
                    // Setting paths from JTable:
                    chosenBackup.setPaths(paths);
                    // Switching prepared backup instance to BackupWindow:
                    backupWindow.show(chosenBackup, turnOffCheckBox.isSelected());
                    // Hiding window:
                    jFrame.setVisible(false);
                } catch (BadPathsException exc) {
                    // Exceptions to bad paths:
                    JOptionPane.showMessageDialog(jFrame, exc.getMessage(),
                            contentsResourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
                }
                // Exceptions to incomplete paths (during reading paths from JTable):
            }, () -> JOptionPane.showMessageDialog(jFrame, contentsResourceBundle.getString("incompleteError"),
                    contentsResourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE));
        });
    }

    private void openDocumentation() {
        // Unpacking documentation to temp directory:
        Path tempManualFile;
        try (InputStream inputStream = getClass().getResourceAsStream("/TKZ_Manual.pdf")) {
            if (inputStream == null) { throw new RuntimeException("TKZ_Manual.pdf not found"); }
            tempManualFile = Files.createTempFile("TKZ_Manual", ".pdf");
            Files.copy(inputStream, tempManualFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exc) {
            JOptionPane.showMessageDialog(jFrame, contentsResourceBundle.getString("openNotSupported"),
                    contentsResourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Checking if Desktop is supported:
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            // Checking if opening action is supported:
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                try {
                    // Opening documentation:
                    desktop.open(tempManualFile.toFile());
                } catch (IOException exc) {
                    JOptionPane.showMessageDialog(jFrame, contentsResourceBundle.getString("cannotOpen"),
                            contentsResourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(jFrame, contentsResourceBundle.getString("openNotSupported"),
                        contentsResourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(jFrame, contentsResourceBundle.getString("desktopNotSupported"),
                    contentsResourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveStrategy(boolean isSaveAs, PathJTable jTable, String backupType,
                              Boolean isShutdown, Component parent) {
        jTable.getPaths().ifPresentOrElse(paths -> {
            try {
                // Preparing attributes:
                Map<String, String> attr = Map.of(shutdownAttributeName, isShutdown.toString(),
                        backupStrategyAttributeName, backupType);
                // Invoking proper method from profileManager:
                Optional<File> file = isSaveAs ? profileManager.saveAs(paths, attr)
                        : profileManager.save(paths, attr);
                // Displaying a message about success:
                file.ifPresent(path -> JOptionPane.showMessageDialog(parent,
                        contentsResourceBundle.getString("saveSuccess").formatted(path.getName()),
                        contentsResourceBundle.getString("success"), JOptionPane.INFORMATION_MESSAGE));
            } catch (IOException exc) {
                // Exceptions during save:
                JOptionPane.showMessageDialog(parent,
                        contentsResourceBundle.getString("fileSaveError"),
                        contentsResourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
                throw new RuntimeException(exc);
            }
            // Exceptions to incomplete paths (during reading paths from JTable):
        }, () -> JOptionPane.showMessageDialog(parent, contentsResourceBundle.getString("incompleteError"),
                contentsResourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE));
    }

    private void openProfile(PathJTable jTable, JCheckBox checkBox,
                             DefaultComboBoxModel<String> comboBoxModel, Component parent) {
        // Getting content from a window:
        Optional<List<SimplePair<String>>> content = jTable.getPaths();
        String backupType = (String) Objects.requireNonNull(comboBoxModel.getSelectedItem());
        Map<String, String> attr = Map.of(shutdownAttributeName, Boolean.toString(checkBox.isSelected()),
                backupStrategyAttributeName, backupType);
        // Checking if content is equals to save in file:
        if (content.isEmpty() || !profileManager.isContentIdentical(content.get(), attr)) {
            // Asking user for saving modified content:
            int choice = JOptionPane.showConfirmDialog(parent,
                    contentsResourceBundle.getString("fileSaveQuestion"),
                    contentsResourceBundle.getString("question"), JOptionPane.YES_NO_CANCEL_OPTION);
            // Saving content if necessary:
            if (choice == JOptionPane.YES_OPTION) { saveStrategy(false, jTable, backupType,
                    checkBox.isSelected(), parent); }
            else if (choice == JOptionPane.CANCEL_OPTION) { return; }
        }
        try {
            // Opening a selected file and prints it's content to window:
            profileManager.open().ifPresent(pair -> setContentFromProfileManager(
                    pair, jTable, comboBoxModel, checkBox));
        } catch (FileFormatException exc) {
            // Exceptions to selecting a wrong file:
            JOptionPane.showMessageDialog(parent, contentsResourceBundle.getString("wrongFileSelection"),
                    contentsResourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
        } catch (IOException exc) {
            // Exceptions during opening:
            JOptionPane.showMessageDialog(parent, contentsResourceBundle.getString("fileOpenError"),
                    contentsResourceBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(exc);
        }
    }

    private static void setContentFromProfileManager(
            ExtendedPair<List<SimplePair<String>>, Map<String, String>> pair, PathJTable jTable,
            DefaultComboBoxModel<String> comboBoxModel, JCheckBox checkBox) {
        // Setting paths to JTable:
        jTable.setPaths(pair.key());
        // Setting backup strategy combobox:
        String item = pair.val().get(backupStrategyAttributeName);
        // Checking if combobox contain backup strategy from a file:
        if (comboBoxModel.getIndexOf(item) != -1) { comboBoxModel.setSelectedItem(item); }
        // Setting shutdown checkbox:
        checkBox.setSelected(Boolean.parseBoolean(pair.val().get(shutdownAttributeName)));
    }

    protected void show() { jFrame.setVisible(true); }
}
