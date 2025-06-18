package ProfileContentManager;

import API.ProfileManager;
import Utils.ExtendedPair;
import Utils.FileFormatException;
import Utils.SimplePair;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SeparatorStyleFiles implements ProfileManager {
    protected final static String pathsSeparator = "///";
    protected final static String attributesSeparator = ": ";
    protected final static File lastUsedFileLog = Path.of("TKZLastUsedProfile").toFile();
    protected final static Pattern fileContentPattern = Pattern.compile("(.+%s.+)|(.+%s.+)".formatted(
            pathsSeparator, attributesSeparator));
    protected File usingFile;
    protected final JFileChooser fileChooser;

    public SeparatorStyleFiles() {
        // Global variables default values:
        usingFile = null;
        fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    }

    private static boolean isCorrectFormat(List<String> lines) {
        // Determines if file content is suitable for usage:
        for (String line : lines) {
            // Checking if file content matches to specified regex pattern:
            Matcher matcher = fileContentPattern.matcher(line);
            if (!matcher.matches()) { return false; }
        } return true;
    }

    private void writeStrategy(File file, List<SimplePair<String>> paths,
                               Map<String, String> attributes) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, false))) {
            // Adding backup paths to file:
            paths.forEach(pair -> writer.println(String.join(pathsSeparator, pair.key(), pair.val())));
            // Adding attributes to file (such as isShutdown ..., backupType):
            attributes.forEach((key, val) -> writer.println(String.join(attributesSeparator, key, val)));
        }
        // Writing to log the new default file:
        setDefaultFileLog(file.toPath());
        // Setting default file:
        usingFile = file;
    }

    private static void setDefaultFileLog(Path pathToSet) throws IOException {
        // Writing to default file log, new path for default profile:
        Files.write(lastUsedFileLog.toPath(), pathToSet.toString().getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public Optional<ExtendedPair<List<SimplePair<String>>, Map<String, String>>> open() throws IOException,
            FileFormatException {
        // Showing open dialog:
        int rValue = fileChooser.showOpenDialog(null);
        if (rValue == JFileChooser.APPROVE_OPTION) {
            // Converting selected path to Path:
            Path selectedPath = fileChooser.getSelectedFile().toPath();
            // Trying to extract file content:
            ExtendedPair<List<SimplePair<String>>, Map<String, String>> fileContent = getFileContent(selectedPath);
            // Writing to log the new default file:
            setDefaultFileLog(selectedPath);
            // Setting default file:
            usingFile = selectedPath.toFile();
            // Returning result:
            return Optional.of(fileContent);
        } return Optional.empty();
    }

    private static ExtendedPair<List<SimplePair<String>>,
            Map<String, String>> getFileContent(Path file) throws IOException, FileFormatException {
        // Extracting all lines from specified file:
        List<String> fileContent = Files.readAllLines(file);
        // Checking if lines are adequate for usage:
        if (!SeparatorStyleFiles.isCorrectFormat(fileContent)) { throw new FileFormatException(); }
        // Grouping lines to paths data and attribute data:
        Map<String, String> attr = new TreeMap<>();
        List<SimplePair<String>> paths = fileContent.stream().filter(line -> {
            if (!line.contains(pathsSeparator)) {
                String[] splLine = line.split(attributesSeparator);
                attr.put(splLine[0], splLine[1]);
                return false;
            } return true;
        }).map(line -> {
            String[] splLine = line.split(pathsSeparator);
            return new SimplePair<>(splLine[0], splLine[1]);
        }).toList();
        // Returning grouped content:
        return new ExtendedPair<>(paths, attr);
    }

    @Override
    public Optional<File> save(List<SimplePair<String>> paths, Map<String, String> attributes) throws IOException {
        // Checking if there is a need to enter file for save:
        if (usingFile != null) {
            // Writing content to file:
            writeStrategy(usingFile, paths, attributes);
            // Returning file where the content has been written:
            return Optional.of(usingFile);
        } else { return saveAs(paths, attributes); }
    }

    @Override
    public Optional<File> saveAs(List<SimplePair<String>> paths, Map<String, String> attributes) throws IOException {
        // Opening File Chooser:
        int rValue = fileChooser.showOpenDialog(null);
        // If user select any file:
        if (rValue == JFileChooser.APPROVE_OPTION) {
            // Writing content to the file:
            writeStrategy(fileChooser.getSelectedFile(), paths, attributes);
            return Optional.of(usingFile);
        } return Optional.empty();
    }

    @Override
    public Optional<ExtendedPair<List<SimplePair<String>>, Map<String, String>>> openLastUsedFile() {
        // Checking if file log exists:
        if (lastUsedFileLog.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(lastUsedFileLog))) {
                // Converting file content to Path:
                Path defaultPath = Path.of(Objects.requireNonNullElse(reader.readLine(), ""));
                // Checking if log is up to date:
                if (Files.isRegularFile(defaultPath)) {
                    // Extracting content from file:
                    ExtendedPair<List<SimplePair<String>>, Map<String, String>> fileContent = getFileContent(
                            defaultPath);
                    // Setting default file:
                    usingFile = defaultPath.toFile();
                    // Returning file content:
                    return Optional.of(fileContent);
                }
            }
            // Ignoring any errors:
            catch (IOException | FileFormatException exc) { return Optional.empty(); }
        } return Optional.empty();
    }

    @Override
    public boolean isContentIdentical(List<SimplePair<String>> content, Map<String, String> attributes) {
        if (content.isEmpty()) { return true; }
        else if (usingFile == null) { return false; }
        try {
            // Checking if specified content is the same as content from file:
            return new ExtendedPair<>(content, attributes).equals(getFileContent(usingFile.toPath()));
        } catch (IOException | FileFormatException exc) { return false; }
    }
}
