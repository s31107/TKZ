package Mirror;

import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

public class MirrorBackupModificationTime extends MirrorBackup {

    // Overriding comparison strategy:
    @Override
    public void execute() {
        // Specifying comparison strategy:
        execute((filePath1, filePath2) -> {
            try {
                // Comparing using modification time:
                return Files.getLastModifiedTime(filePath1).toMillis()
                        == Files.getLastModifiedTime(filePath2).toMillis();
            } catch (IOException exc) {
                // Decision od copy file if errors occurred:
                logger.log(Level.WARNING, "Comparing modification time of two files: %s, %s".formatted(
                        filePath1, filePath2), exc);
                return false;
            }
        });
    }

    // Backup name:
    @Override
    public String getBackupType() { return resourceBundle.getString("mirrorBackupModificationTime"); }
}
