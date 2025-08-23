package Mirror;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;

public class MirrorBackupModificationTime extends MirrorBackup {

    // Overriding comparison strategy:
    @Override
    public void execute() {
        // Specifying comparison strategy:
        execute((filePath1, filePath2) -> {
            try {
                BasicFileAttributes fileAttributes1 = Files.readAttributes(filePath1, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                BasicFileAttributes fileAttributes2 = Files.readAttributes(filePath2, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                // Comparing using modification time and file size:
                return fileAttributes1.lastModifiedTime().equals(fileAttributes2.lastModifiedTime())
                        && fileAttributes1.size() == fileAttributes2.size();
            } catch (IOException exc) {
                // Decision od copy file if errors occurred:
                logger.log(Level.WARNING, "Comparing two files: %s, %s".formatted(filePath1, filePath2), exc);
                return false;
            }
        });
    }

    // Backup name:
    @Override
    public String getBackupType() { return resourceBundle.getString("mirrorBackupModificationTime"); }
}
