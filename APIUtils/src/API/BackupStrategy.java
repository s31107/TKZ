package API;

import Utils.BadPathsException;
import Utils.ListenersTypes;
import Utils.SimplePair;

import java.beans.PropertyChangeListener;
import java.util.List;

public interface BackupStrategy {
    // Running backup:
    void execute();
    // Stopping backup:
    void stop();
    // Setting backup paths:
    void setPaths(List<SimplePair<String>> paths) throws BadPathsException;
    // Property strategies for JTextArea, JProgressBar, Finish Backup strategy(buttons):
    void addPropertyListener(ListenersTypes type, PropertyChangeListener listener);
    void removePropertyListener(ListenersTypes type, PropertyChangeListener listener);
    // Waiting for backup to finish (not interrupting work):
    void join() throws InterruptedException;
    // Getting backup name:
    String getBackupType();
}
