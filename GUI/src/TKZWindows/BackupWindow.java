package TKZWindows;

import API.BackupStrategy;
import API.IconsManager;
import CustomComponents.ConsoleLog;
import CustomComponents.ShutdownDialog;
import Utils.ListenersTypes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class BackupWindow {
    private final static int clockRefreshTime = 1000;
    private final static DateTimeFormatter clockFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final static double xWindowPattern = 1680.;
    private final static double yWindowPattern = 1050.;
    private final static double xWindowProp = 800.;
    private final static double yWindowProp = 600.;
    private final static int xWindowBorderProp = 12;
    private final static int yWindowBorderProp = 10;
    private LocalTime clock;
    private JLabel labelClock;
    private JButton returnButton;
    private JButton stopButton;
    private JButton startButton;
    private Timer clockTimer;
    private boolean windowStatus;
    private boolean isStopped;
    private final ShutdownDialog shutdownDialog;
    private volatile boolean isClosingWindow;
    protected boolean isShutdown;
    protected final MainWindow rWindow;
    protected final JFrame jFrame;
    protected final ResourceBundle rBundle;
    protected final IconsManager iManager;
    protected BackupStrategy backupStrategy;
    protected PropertyChangeListener percentageListener;
    protected PropertyChangeListener consoleLogListener;
    protected PropertyChangeListener finishBackupListener;

    public BackupWindow(MainWindow returnWindow, ResourceBundle resourceBundle, IconsManager iconsManager) {
        // Global variables:
        iManager = iconsManager;
        rBundle = resourceBundle;
        rWindow = returnWindow;
        jFrame = new JFrame();
        shutdownDialog = new ShutdownDialog(jFrame, resourceBundle, this::shutdownStrategy);
        windowStatus = false;
        isStopped = false;
        // Flag informing is window closing (For avoiding deadlock with InvokeAndWait() method in PropertyChange):
        isClosingWindow = false;
        // Building gui:
        execGui();
    }

    protected void execGui() {
        // Computing window borders:
        int xWindowBorder = (int) (
                Toolkit.getDefaultToolkit().getScreenSize().width * xWindowBorderProp / xWindowPattern);
        int yWindowBorder = (int) (
                Toolkit.getDefaultToolkit().getScreenSize().height * yWindowBorderProp / yWindowPattern);
        // Main layouts:
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        // JTextArea:
        ConsoleLog consoleLog = new ConsoleLog(iManager, rBundle, () -> windowStatus);
        mainPanel.add(consoleLog);
        // Clock:
        JPanel clockPanel = new JPanel();
        clockPanel.setLayout(new BoxLayout(clockPanel, BoxLayout.X_AXIS));
        labelClock = new JLabel();
        clockPanel.add(Box.createHorizontalGlue());
        clockPanel.add(labelClock);
        mainPanel.add(Box.createVerticalStrut(yWindowBorder));
        mainPanel.add(clockPanel);
        // Progress Bar:
        JProgressBar jProgressBar = new JProgressBar(0, 100);
        jProgressBar.setStringPainted(true);
        mainPanel.add(Box.createVerticalStrut(yWindowBorder));
        mainPanel.add(jProgressBar);
        // Buttons;
        returnButton = new JButton(rBundle.getString("return"), iManager.getIcon("return"));
        stopButton = new JButton(rBundle.getString("stop"), iManager.getIcon("stop"));
        startButton = new JButton(rBundle.getString("start"), iManager.getIcon("run"));
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
        buttonsPanel.add(returnButton);
        buttonsPanel.add(Box.createHorizontalGlue());
        buttonsPanel.add(stopButton);
        buttonsPanel.add(Box.createHorizontalStrut(xWindowBorder));
        buttonsPanel.add(startButton);
        mainPanel.add(Box.createVerticalStrut(yWindowBorder));
        mainPanel.add(buttonsPanel);
        // Window configuration:
        jFrame.add(mainPanel);
        // Closing window strategy:
        jFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainPanel.setBorder(
                BorderFactory.createEmptyBorder(yWindowBorder, xWindowBorder, yWindowBorder, xWindowBorder));
        jFrame.setMinimumSize(new Dimension((int) (Toolkit.getDefaultToolkit().getScreenSize().width * xWindowProp
                / xWindowPattern), (int) (Toolkit.getDefaultToolkit().getScreenSize().height * yWindowProp
                / yWindowPattern)));
        jFrame.setTitle(rBundle.getString("backupWindowTitle"));
        // Buttons:
        returnButton.addActionListener(_ -> {
            // Removing listeners:
            backupStrategy.removePropertyListener(ListenersTypes.CONSOLE, consoleLogListener);
            backupStrategy.removePropertyListener(ListenersTypes.PROGRESS, percentageListener);
            backupStrategy.removePropertyListener(ListenersTypes.FINISH, finishBackupListener);
            // Disposing backup:
            try {
                backupStrategy.joinAndDispose();
            } catch (InterruptedException exc) { throw new RuntimeException(exc); }
            // Hiding window:
            jFrame.setVisible(false);
            // Clearing console:
            consoleLog.clear();
            // Showing a parent window:
            rWindow.show();
        });
        stopButton.addActionListener(_ -> {
            // Setting stop flag:
            isStopped = true;
            // Disabling button to prevent multiple stop signals:
            stopButton.setEnabled(false);
            // Stopping backup:
            backupStrategy.stop();
        });
        startButton.addActionListener(_ -> {
            // Resetting stop flag:
            isStopped = false;
            // Switching a window to running state:
            switchWindowStatus();
            // Executing backup:
            backupStrategy.execute();
        });
        // Listeners:
        // Updating progress bar:
        percentageListener = updateGUIListenerFactory(
                evt -> jProgressBar.setValue((int) evt.getNewValue()));
        // Adding new logs to console:
        consoleLogListener = updateGUIListenerFactory(
                evt -> consoleLog.addLine((String) evt.getNewValue()));
        // Declaring clock timer:
        clockTimer = new Timer(clockRefreshTime, _ -> updateClock());
        // Backup finish strategy:
        finishBackupListener = evt -> SwingUtilities.invokeLater(() -> {
            // Rejecting updating gui if window is closing:
            if (isClosingWindow) { return; }
            // Storing value if some errors occurred in backup:
            boolean isSuccessful = (boolean) evt.getNewValue();
            // Switching window to not running state:
            switchWindowStatus();
            // Displaying a message about backup finish state:
            if (isStopped) {
                JOptionPane.showMessageDialog(jFrame, rBundle.getString("interruptedBackup"),
                        rBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
            } else if (isShutdown) {
                // Switching off clause:
                shutdownDialog.showDialog(!isSuccessful);
            }
            else if (isSuccessful) {
                JOptionPane.showMessageDialog(jFrame, rBundle.getString("backupSuccessful"),
                        rBundle.getString("success"), JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(jFrame, rBundle.getString("backupUnsuccessful"),
                        rBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
            }
        });
        jFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Runnable closeStrategy = () -> {
                    // Closing application strategy:
                    try {
                        // Waiting for finish backup and disposing backup:
                        backupStrategy.joinAndDispose();
                    } catch (InterruptedException exc) { throw new RuntimeException(exc); }
                    System.exit(0);
                };
                // If a window is not working close:
                if (!windowStatus) { closeStrategy.run(); }
                // Asking for interrupting backup:
                if (JOptionPane.showConfirmDialog(jFrame, rBundle.getString("closeConfirmation"),
                        rBundle.getString("question"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    // Setting closing window flag:
                    isClosingWindow = true;
                    // Stopping clockTimer:
                    clockTimer.stop();
                    // Stopping executor:
                    stopButton.doClick();
                    // Changing cursor:
                    jFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    // Closing the window:
                    closeStrategy.run();
                }
            }
        });
    }

    private PropertyChangeListener updateGUIListenerFactory(Consumer<PropertyChangeEvent> updateGUIStrategy) {
        return evt -> {
            try {
                // Rejecting updating gui if window is closing:
                if (isClosingWindow) { return; }
                // Checking if caller is EDT, and scheduling task:
                if (SwingUtilities.isEventDispatchThread()) {
                    updateGUIStrategy.accept(evt);
                } else {
                    SwingUtilities.invokeAndWait(() -> updateGUIStrategy.accept(evt));
                }
            } catch (InterruptedException | InvocationTargetException exc) { throw new RuntimeException(exc); }
        };
    }

    private void shutdownStrategy() {
        try {
            // Starting a new shutdown process:
            ProcessBuilder processBuilder = new ProcessBuilder("systemctl", "poweroff");
            processBuilder.start();
        } catch (IOException exc) { throw new RuntimeException(exc); }
    }

    protected void show(BackupStrategy bStrategy, boolean isShutdownBefore) {
        // Initializing shutdown variable:
        isShutdown = isShutdownBefore;
        // Declaring backup strategy:
        backupStrategy = bStrategy;
        // Showing window:
        jFrame.setVisible(true);
        // Adding listeners:
        backupStrategy.addPropertyListener(ListenersTypes.CONSOLE, consoleLogListener);
        backupStrategy.addPropertyListener(ListenersTypes.PROGRESS, percentageListener);
        backupStrategy.addPropertyListener(ListenersTypes.FINISH, finishBackupListener);
        // Executing backup:
        startButton.doClick();
    }

    private void switchWindowStatus() {
        // Starting/stopping clock:
        if (windowStatus) {
            clockTimer.stop();
        } else {
            resetClock();
            clockTimer.start();
        }
        // Switching a window to running/not running:
        startButton.setEnabled(windowStatus);
        returnButton.setEnabled(windowStatus);
        stopButton.setEnabled(!windowStatus);
        windowStatus = !windowStatus;
    }

    private void updateClock() {
        // Incrementing clock of specified milliseconds:
        clock = clock.plus(clockRefreshTime, ChronoUnit.MILLIS);
        // Updating gui:
        labelClock.setText(clock.format(clockFormatter));
    }

    private void resetClock() {
        // Resetting the clock:
        clock = LocalTime.of(0, 0, 0);
        // Updating GUI:
        labelClock.setText(clock.format(clockFormatter));
    }
}
