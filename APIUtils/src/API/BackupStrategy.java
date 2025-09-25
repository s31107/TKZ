package API;

import Utils.BadPathsException;
import Utils.ListenersTypes;
import Utils.SimplePair;

import java.beans.PropertyChangeListener;
import java.util.List;

public interface BackupStrategy {
    // Running backup, even with joinAndDispose() call:
    void execute();
    // Stopping backup (interrupting work):
    void stop();
    // Setting backup paths:
    void setPaths(List<SimplePair<String>> paths) throws BadPathsException;
    // Setting is proceed with hidden elements:
    void setIsCopyHiddenElements(boolean copyHiddenElements);
    // Property strategies for JTextArea, JProgressBar, Finish Backup strategy(buttons):
    void addPropertyListener(ListenersTypes type, PropertyChangeListener listener);
    void removePropertyListener(ListenersTypes type, PropertyChangeListener listener);
    // Waiting for backup to finish (not interrupting work) and releasing resources:
    void joinAndDispose() throws InterruptedException;
    // Getting backup name:
    String getBackupType();
}
