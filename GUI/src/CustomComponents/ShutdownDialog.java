package CustomComponents;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

public class ShutdownDialog {
    private final static int clockRefreshTime = 1000;
    private final static short errorTimeLeft = 15;
    private final static short successTimeLeft = 5;
    private final Timer timer;
    private final Component parentComponent;
    private final JLabel messageLabel;
    private short timeElapsed;
    protected final Runnable sStrategy;
    protected final ResourceBundle rBundle;

    public ShutdownDialog(Component parent, ResourceBundle resourceBundle, Runnable shutdownStrategy) {
        // Global variables:
        parentComponent = parent;
        rBundle = resourceBundle;
        sStrategy = shutdownStrategy;
        // Initializing variables using default values:
        messageLabel = new JLabel();
        timer = new Timer(clockRefreshTime, null);
    }

    private void updateMessage(boolean isError, short time) {
        // Constructing message:
        String message = "<html>" + rBundle.getString(
                isError ? "shutdownDialogErrorMsg" : "shutdownDialogMsg") + "<br>" + rBundle.getString(
                "timeLeftMsg").formatted(time) + "</br>" + "</html>";
        // Updating message box:
        messageLabel.setText(message);
    }

    public void showDialog(boolean isError) {
        // Initializing current session variables:
        timeElapsed = isError ? errorTimeLeft : successTimeLeft;
        int dialogType = isError ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE;
        Object[] options = { rBundle.getString("cancel") };
        updateMessage(isError, timeElapsed);
        // Constructing timer variable:
        ActionListener actionListener = _ -> {
            if (timeElapsed == 0) {
                timer.stop();
                sStrategy.run();
            }
            updateMessage(isError, --timeElapsed);
        };
        timer.addActionListener(actionListener);
        timer.start();
        // Creating main dialog:
        JOptionPane.showOptionDialog(parentComponent, messageLabel, rBundle.getString("shutdownDialogTitle"),
                JOptionPane.DEFAULT_OPTION, dialogType, null, options, options[0]);
        // Resetting timer when wait interrupted:
        timer.stop();
        timer.restart();
        timer.removeActionListener(actionListener);
    }
}