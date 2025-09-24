package ApplicationExecution;

import IconsManagement.StoredIcons;
import ProfileContentManager.SeparatorStyleFiles;
import TKZWindows.LoadingScreen;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoadingScreen(StoredIcons.class, SeparatorStyleFiles.class));
    }
}