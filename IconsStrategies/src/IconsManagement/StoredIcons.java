package IconsManagement;

import API.IconsManager;

import javax.swing.*;
import java.net.URL;

public class StoredIcons implements IconsManager {

    @Override
    public ImageIcon getIcon(String name) {
        // Preparing a path for specified icon:
        URL url = getClass().getResource("/icons/" + name);
        // Checking if icon exists:
        if (url == null) { throw new RuntimeException("Icon: " + name + " not found!"); }
        // Returning new ImageIcon object of url:
        return new ImageIcon(url);
    }
}
