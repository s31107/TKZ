package API;

import Utils.ExtendedPair;
import Utils.FileFormatException;
import Utils.SimplePair;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProfileManager {
    Optional<ExtendedPair<List<SimplePair<String>>, Map<String, String>>> open()
            throws IOException, FileFormatException;
    Optional<File> save(List<SimplePair<String>> paths, Map<String, String> attributes) throws IOException;
    Optional<File> saveAs(List<SimplePair<String>> paths, Map<String, String> attributes) throws IOException;
    Optional<ExtendedPair<List<SimplePair<String>>, Map<String, String>>> openLastUsedFile();
    boolean isContentIdentical(List<SimplePair<String>> content, Map<String, String> attributes);
}