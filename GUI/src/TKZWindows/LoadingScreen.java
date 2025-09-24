package TKZWindows;

import API.BackupStrategy;
import API.IconsManager;
import API.ProfileManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;

public class LoadingScreen {
    private final JFrame frame;
    private final static double xWindowPattern = 1680.;
    private final static double yWindowPattern = 1050.;
    private final static double xWindowProp = 800.;
    private final static double yWindowProp = 600.;
    private final static double progressBarProportion = 0.04;

    public LoadingScreen(
            Class<? extends IconsManager> iconsManagerClass, Class<? extends ProfileManager> profileManagerClass) {
        // Global variables:
        frame = new JFrame();
        // Building gui:
        constructGui(iconsManagerClass, profileManagerClass);
    }

    private void constructGui(
            Class<? extends IconsManager> iconsManagerClass, Class<? extends ProfileManager> profileManagerClass) {
        // Removing frame:
        frame.setUndecorated(true);
        // Declaring window picture:
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/splashScreen")));
        // Applying a new draw strategy, which scales the picture to the window size:
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(icon.getImage(), 0, 0, getWidth(), getHeight(), this);
            }
        };
        // Setting custom layout:
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        // Declaring progress bar and it's configuration:
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setOpaque(false);
        progressBar.setBorderPainted(false);
        // Adding progress bar to panel:
        panel.add(Box.createVerticalGlue());
        panel.add(progressBar);
        // Adding panel to the layout:
        frame.add(panel);
        // Saving screen size:
        int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
        int screenHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        // Calculating window size:
        Dimension windowSize = new Dimension((int) (screenWidth * xWindowProp
                / xWindowPattern), (int) (screenHeight * yWindowProp
                / yWindowPattern));
        // Setting preferred progress bar size:
        progressBar.setPreferredSize(new Dimension(Integer.MAX_VALUE,
                (int) (windowSize.getHeight() * progressBarProportion)));
        // Setting window size:
        frame.setSize(windowSize);
        // Centering window:
        frame.setLocation((int) (screenWidth / 2. - windowSize.getWidth() / 2),
                (int) (screenHeight / 2. - windowSize.getHeight() / 2));
        // Window settings:
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);
        // Loading components and monitoring progress:
        new SwingWorker<Object[], Integer>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                // Creating instances of necessary components by reflection and publishing progresses:
                IconsManager iconsManager = iconsManagerClass.getDeclaredConstructor().newInstance();
                publish(33);
                ProfileManager profileManager = profileManagerClass.getDeclaredConstructor().newInstance();
                publish(66);
                // Creating instances of Backups Strategies:
                ServiceLoader<BackupStrategy> serviceLoader = ServiceLoader.load(BackupStrategy.class);
                BackupStrategy[] backupStrategies = serviceLoader.stream().map(ServiceLoader.Provider::get).toArray(
                        BackupStrategy[]::new);
                publish(100);
                // Creating visual effect by freezing window:
                Thread.sleep(3000);
                // Returning created object:
                return new Object[] {iconsManager, profileManager, backupStrategies};
            }

            @Override
            protected void process(List<Integer> chunks) {
                super.process(chunks);
                // Updating progress bar:
                chunks.forEach(progressBar::setValue);
            }

            @Override
            protected void done() {
                try {
                    // Closing loading window:
                    frame.setVisible(false);
                    frame.dispose();
                    // Passing created objects to MainWindow:
                    Object[] objects = get();
                    new MainWindow(
                            (IconsManager) objects[0], (ProfileManager) objects[1], (BackupStrategy[]) objects[2]);
                } catch (InterruptedException | ExecutionException e) { throw new RuntimeException(e); }
            }
        }.execute();
    }
}
