package TKZWindows;

import API.BackupStrategy;
import API.IconsManager;
import CustomComponents.ConsoleLog;
import Utils.ListenersTypes;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ResourceBundle;

public class BackupWindow {
    private final static int clockRefreshTime = 1000;
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
        windowStatus = false;
        isStopped = false;
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
        ConsoleLog consoleLog = new ConsoleLog(iManager, rBundle);
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
        jFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Runnable closeStrategy = () -> {
                    // Closing application strategy:
                    jFrame.dispose();
                    System.exit(0);
                };
                // If window is not working close:
                if (!windowStatus) { closeStrategy.run(); }
                // Asking for interrupting backup:
                if (JOptionPane.showConfirmDialog(jFrame, rBundle.getString("closeConfirmation"),
                        rBundle.getString("question"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    // Stopping executor:
                    stopButton.doClick();
                    try {
                        // Changing cursor:
                        jFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        // Waiting for finish backup:
                        backupStrategy.join();
                    } catch (InterruptedException exc) { throw new RuntimeException(exc); }
                    // Closing window:
                    closeStrategy.run();
                }
            }
        });
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
            // Hiding window:
            jFrame.setVisible(false);
            // Clearing console:
            consoleLog.clear();
            // Showing parent window:
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
            // Switching window to running state:
            switchWindowStatus();
            // Executing backup:
            backupStrategy.execute();
        });
        // Listeners:
        // Updating progress bar:
        percentageListener = evt -> SwingUtilities.invokeLater(
                () -> jProgressBar.setValue((int) evt.getNewValue()));
        // Adding new logs to console:
        consoleLogListener = evt -> SwingUtilities.invokeLater(
                () -> consoleLog.addLine((String) evt.getNewValue()));
        // Declaring clock timer:
        clockTimer = new Timer(clockRefreshTime, _ -> updateClock());
        // Backup finish strategy:
        finishBackupListener = evt -> SwingUtilities.invokeLater(() -> {
            // Shutting down computer if necessary:
            if (isShutdown && !isStopped) {
                shutdownStrategy();
                return;
            }
            // Switching window to not running state:
            switchWindowStatus();
            // Displaying message about backup finish state:
            if (isStopped) {
                JOptionPane.showMessageDialog(jFrame, rBundle.getString("interruptedBackup"),
                        rBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
            } else if ((boolean) evt.getNewValue()) {
                JOptionPane.showMessageDialog(jFrame, rBundle.getString("backupSuccessful"),
                        rBundle.getString("success"), JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(jFrame, rBundle.getString("backupUnsuccessful"),
                        rBundle.getString("error"), JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void shutdownStrategy() {
        try {
            // Starting new shutdown process:
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
        switchWindowStatus();
        backupStrategy.execute();
    }

    private void switchWindowStatus() {
        // Starting/stopping clock:
        if (windowStatus) {
            clockTimer.stop();
        } else {

            resetClock();
            clockTimer.start();
        }
        // Switching window to running/not running:
        startButton.setEnabled(windowStatus);
        returnButton.setEnabled(windowStatus);
        stopButton.setEnabled(!windowStatus);
        windowStatus = !windowStatus;
    }

    private void updateClock() {
        // Incrementing clock of specified milliseconds:
        clock = clock.plus(clockRefreshTime, ChronoUnit.MILLIS);
        // Updating gui:
        labelClock.setText(clock.toString());
    }

    private void resetClock() {
        // Resetting clock:
        clock = LocalTime.of(0, 0, 0);
        // Updating GUI:
        labelClock.setText(clock.toString());
    }
}
