package Mirror;

import API.BackupStrategy;
import ExecutesStrategies.AutomateAsyncExecutor;
import ExecutesStrategies.BackupExecutor;
import Utils.BadPathsException;
import Utils.ListenersTypes;
import Utils.SimplePair;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MirrorBackup implements BackupStrategy {
    private final PropertyChangeSupport propertyChange;
    private List<SimplePair<Path>> backupPaths;
    private final BackupExecutor executor;
    private final LongAdder pathSizeSum;
    private long fileSizes;
    private final AtomicBoolean isInterrupted;
    private FileHandler fileHandler;
    protected final Logger logger;
    protected static final String loggerFileName = "TKZMirrorLog";
    protected final ResourceBundle resourceBundle;

    public MirrorBackup() {
        // Stop backup flag:
        isInterrupted = new AtomicBoolean();
        // Properties manager:
        propertyChange = new PropertyChangeSupport(this);
        // Language bundle:
        resourceBundle = ResourceBundle.getBundle("MirrorBundles.BackupMessages");
        // Atomic sum of all paths:
        pathSizeSum = new LongAdder();
        // Errors logger:
        logger = Logger.getLogger("BackupStrategies.Mirror.MirrorBackup");
        logger.setLevel(Level.ALL);
        // Backup execution strategy:
        executor = new AutomateAsyncExecutor(logger);
    }

    @Override
    public void execute() {
        // Specifying comparison strategy:
        execute((filePath1, filePath2) -> {
            try {
                if (Files.isSymbolicLink(filePath1)) {
                    return Files.readSymbolicLink(filePath1).equals(Files.readSymbolicLink(filePath2));
                }
                // Analyzing file content:
                return Files.mismatch(filePath1, filePath2) == -1;
            } catch (IOException exc) {
                // Decision of a copy file if errors occurred:
                logger.log(Level.WARNING, "Comparing two files: %s, %s".formatted(filePath1, filePath2), exc);
                return false;
            }
        });
    }

    public void execute(BiFunction<Path, Path, Boolean> comparisonStrategy) {
        // Resetting last backup flags and counters:
        isInterrupted.set(false);
        pathSizeSum.reset();
        // Resetting progress:
        setProgress(0);
        // Logging new file handler:
        try {
            fileHandler = new FileHandler(loggerFileName, false);
        } catch (IOException exc) { throw new RuntimeException(exc); }
        logger.addHandler(fileHandler);
        // Executing backup using specified executor with logging error strategy:
        executor.execute(backupPaths, (srcPath, _) -> getPathSize(srcPath), Long::sum, sumSizes -> {
            fileSizes = sumSizes;
            executor.execute(backupPaths, (srcPath, dstPath) -> backup(
                    srcPath, dstPath, comparisonStrategy), Statistics::merge, this::finishStrategy,
                    this::pathExceptionStrategy);
        }, (exc, paths) -> logger.log(Level.SEVERE,
                "Getting device id, while getting path size: %s, %s".formatted(paths.key(), paths.val()), exc));
    }

    private long getPathSize(Path srcPath) {
        // File visitor:
        class Visitor extends SimpleFileVisitor<Path> {
            private long filesSize = 0;

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Getting files size:
                try {
                    filesSize += Files.readAttributes(
                            file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).size();
                    return FileVisitResult.CONTINUE;
                } catch (IOException exc) { return visitFileFailed(file, exc); }
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Cannot get the size of file strategy:
                setConsole(resourceBundle.getString("errorFileSize").formatted(file));
                logger.log(Level.WARNING, "Getting file size: %s".formatted(file), exc);
                return FileVisitResult.CONTINUE;
            }

            public long getFilesSize() { return filesSize; }
        }

        Visitor visitor = new Visitor();
        try {
            // Walking through specified directory and summing sizes of contained files:
            Files.walkFileTree(srcPath, visitor);
        } catch (Throwable exc) {
            // Cannot walk through the specified path:
            setConsole(resourceBundle.getString("errorPathSize").formatted(srcPath));
            logger.log(Level.SEVERE, "Walking through path to get path size: %s".formatted(srcPath), exc);
            return 0;
        } return visitor.getFilesSize();
    }

    private void pathExceptionStrategy(IOException exception, SimplePair<Path> paths) {
        // Logging exception occurred while failed tried of executing a specified path set:
        setConsole(resourceBundle.getString("badPaths").formatted(paths.key(), paths.val()));
        logger.log(Level.SEVERE, "Getting device id, while running backup instance: %s, %s".formatted(
                paths.key(), paths.val()), exception);
    }

    private void finishStrategy(Statistics stats) {
        // Replacing null with default empty statistics:
        if (stats == null) { stats = new Statistics(); }
        // Printing statistics:
        setConsole(resourceBundle.getString("statisticsPrint"));
        for (StatisticsEnum type : StatisticsEnum.values()) { setConsole(stats.getMessage(type)); }
        // Changing state of running backup property:
        setEndBackup(stats.isExceptionsNotRaised());
        // Releasing logger resources:
        releaseResources();
    }

    private boolean isSameFileType(Path srcFile, Path dstFile) {
        // Method requires that srcFile exists, and it is not a directory:
        if (Files.notExists(dstFile, LinkOption.NOFOLLOW_LINKS)) { return false; }
        BasicFileAttributes srcAttribs, dstAttribs;
        try {
            srcAttribs = Files.readAttributes(srcFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            dstAttribs = Files.readAttributes(srcFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exc) {
            logger.log(Level.WARNING, "Comparing types of two files: %s, %s".formatted(srcFile, dstFile), exc);
            return false;
        }
        return srcAttribs.isRegularFile() == dstAttribs.isRegularFile() || srcAttribs.isSymbolicLink()
                == dstAttribs.isSymbolicLink() || srcAttribs.isOther() == dstAttribs.isOther();
    }

    private Statistics backup(Path sourcePath, Path destinationPath,
                              BiFunction<Path, Path, Boolean> comparisonStrategy) {
        // Current backup instance statistics:
        Statistics statistics = new Statistics();
        // Destination path with added source path directory name:
        Path resolvedDestinationPath = destinationPath.resolve(sourcePath.getFileName());
        BiFunction<Path, IOException, FileVisitResult> failedVisitFile = (file, exc) -> {
            // Interrupt backup check:
            if (isInterrupted.get()) { return FileVisitResult.TERMINATE; }
            // Sending proper communicate:
            logger.log(Level.WARNING, "Failed visiting file: %s".formatted(file), exc);
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                statistics.increment(StatisticsEnum.NOT_VISITED_DIRS);
                setConsole(resourceBundle.getString("errorVisitDir").formatted(file));
            } else {
                statistics.increment(StatisticsEnum.NOT_VISITED_FILES);
                setConsole(resourceBundle.getString("errorVisitFile").formatted(file));
            } return FileVisitResult.CONTINUE;
        };

        // Copy visitor:
        FileVisitor<? super Path> fileCopyVisitor = new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Mechanism of creating missing directories:
                // Interrupt backup check:
                if (isInterrupted.get()) { return FileVisitResult.TERMINATE; }
                // Defining a path of current directory in destination backup location:
                Path dstDir = resolvedDestinationPath.resolve(sourcePath.relativize(dir));
                // Creating if not exists:
                if (!Files.isDirectory(dstDir, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        Files.createDirectory(dstDir);
                        statistics.increment(StatisticsEnum.CREATED_DIRS);
                        setConsole(resourceBundle.getString("createDir").formatted(dstDir));
                    } catch (IOException exc) {
                        // Skipping subtree with information:
                        logger.log(Level.SEVERE, "Creating directory: %s".formatted(dstDir), exc);
                        statistics.increment(StatisticsEnum.NOT_CREATED_DIRECTORIES);
                        setConsole(resourceBundle.getString("errorCreateDir").formatted(dstDir));
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                } return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Mechanism of copying missing or different files:
                // Interrupt backup check:
                if (isInterrupted.get()) { return FileVisitResult.TERMINATE; }
                // Defining a path of the current file in destination backup location:
                Path dstFile = resolvedDestinationPath.resolve(sourcePath.relativize(file));
                // Decision of copying file:
                if (!(isSameFileType(file, dstFile) && comparisonStrategy.apply(file, dstFile))) {
                    try {
                        // Copying file:
                        Files.copy(file, dstFile, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                        statistics.increment(StatisticsEnum.COPIED_FILES);
                        setConsole(resourceBundle.getString("fileCopy").formatted(file, dstFile));
                    } catch (IOException exc) {
                        // Sending proper communicate if error:
                        logger.log(Level.SEVERE, "Copying file %s to %s".formatted(file, dstFile), exc);
                        statistics.increment(StatisticsEnum.NOT_COPIED_FILE);
                        setConsole(resourceBundle.getString("errorFileCopy").formatted(file, dstFile));
                    }
                }
                try {
                    // Adding path size to a global files size sum:
                    pathSizeSum.add(Files.readAttributes(file, BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS).size());
                } catch (IOException exc) {
                    // Only logging error:
                    logger.log(Level.WARNING, "Getting file size: %s".formatted(file), exc);
                }
                // Sending new progress of copied files:
                if (fileSizes == 0) { setProgress(100); }
                else { setProgress((int) (pathSizeSum.sum() * 100. / fileSizes)); }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Files that cannot be visited:
                return failedVisitFile.apply(file, exc);
            }
        };

        // Removing visitor:
        FileVisitor<? super Path> fileRemoveVisitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Interrupt backup check:
                if (isInterrupted.get()) { return FileVisitResult.TERMINATE; }
                // Checking if a path of the current file exists in source backup location:
                else if (Files.notExists(sourcePath.resolve(resolvedDestinationPath.relativize(file)),
                        LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        // Removing an additional file:
                        Files.delete(file);
                        statistics.increment(StatisticsEnum.REMOVED_FILES);
                        setConsole(resourceBundle.getString("removeFile").formatted(file));
                    } catch (IOException exc) {
                        // Sending proper communicate of exception:
                        logger.log(Level.SEVERE, "Removing file: %s".formatted(file), exc);
                        statistics.increment(StatisticsEnum.NOT_REMOVED_FILES);
                        setConsole(resourceBundle.getString("errorRemoveFile").formatted(file));
                    }
                } return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                // Interrupt backup check:
                if (isInterrupted.get()) { return FileVisitResult.TERMINATE; }
                // Throwing any exception if throws from other methods and finishing work:
                else if (exc != null) { throw exc; }
                // Checking if a path of the current directory exists in source backup location:
                if (!Files.isDirectory(sourcePath.resolve(resolvedDestinationPath.relativize(dir)),
                        LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        // Removing additional directory:
                        Files.delete(dir);
                        statistics.increment(StatisticsEnum.REMOVED_DIRECTORIES);
                        setConsole(resourceBundle.getString("removeDir").formatted(dir));
                    } catch (IOException exception) {
                        // Sending proper communicate of exception:
                        logger.log(Level.SEVERE, "Removing directory: %s".formatted(dir), exception);
                        statistics.increment(StatisticsEnum.NOT_REMOVED_DIRECTORIES);
                        setConsole(resourceBundle.getString("errorRemoveDir").formatted(dir));
                    }
                } return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return failedVisitFile.apply(file, exc);
            }
        };
        // Variable which stores information if there is a need to check for additional files in a destination path:
        boolean isPureBackup = false;
        try {
            // Creating directory of a source path last directory name:
            if (!Files.isDirectory(resolvedDestinationPath, LinkOption.NOFOLLOW_LINKS)) {
                isPureBackup = true;
                try {
                    Files.createDirectory(resolvedDestinationPath);
                } catch (FileAlreadyExistsException exc) {
                    setConsole(resourceBundle.getString("cannotCreateBackupDirectory").formatted(
                            resolvedDestinationPath));
                    // Going to logging:
                    throw new IOException(exc);
                }
            }
            // Copying files:
            Files.walkFileTree(sourcePath, fileCopyVisitor);
            // Removing files:
            if (!isPureBackup) { Files.walkFileTree(resolvedDestinationPath, fileRemoveVisitor); }
        } catch (IOException exc) {
            // Any backup error catch:
            statistics.increment(StatisticsEnum.NOT_CREATED_DIRECTORIES);
            setConsole(resourceBundle.getString("cannotFinishBackup").formatted(sourcePath, destinationPath));
            logger.log(Level.SEVERE, "Creating backup of: %s, %s".formatted(sourcePath, destinationPath), exc);
        } return statistics;
    }

    @Override
    public void stop() {
        // Changing flag of stopping backup:
        isInterrupted.set(true);
    }

    @Override
    public void joinAndDispose() throws InterruptedException {
        // Joining executor:
        executor.joinAndShutdown();
        // Releasing resources in case finishStrategy() hasn't been invoked:
        releaseResources();
    }

    protected void releaseResources() {
        // Closing file handler:
        logger.removeHandler(fileHandler);
        fileHandler.close();
    }

    @Override
    public void setPaths(List<SimplePair<String>> paths) throws BadPathsException {
        // Checking if specified paths are not empty:
        if (paths.isEmpty()) { throw new BadPathsException(resourceBundle.getString("noPathsSpecified")); }
        // Setting and validating new backup paths:
        List<SimplePair<Path>> acceptedPaths = new ArrayList<>();
        Path srcPath, dstPath;
        for (SimplePair<String> pathSet : paths) {
            // Converting iterating paths to absolute paths:
            srcPath = Path.of(pathSet.key()).toAbsolutePath();
            dstPath = Path.of(pathSet.val()).toAbsolutePath();
            // If paths don't point to existing directories:
            if (!Files.isDirectory(srcPath, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isDirectory(dstPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new BadPathsException(
                        resourceBundle.getString("pathsNotExist").formatted(pathSet.key(), pathSet.val()));
            }
            // If paths include themselves:
            else if (srcPath.startsWith(dstPath) || dstPath.startsWith(srcPath)) {
                throw new BadPathsException(
                        resourceBundle.getString("pathsLooped").formatted(pathSet.key(), pathSet.val()));
            }
            acceptedPaths.add(new SimplePair<>(srcPath, dstPath));
        } backupPaths = acceptedPaths;
    }

    // Strategies change the state of properties:

    private synchronized void setConsole(String line) {
        propertyChange.firePropertyChange(ListenersTypes.CONSOLE.toString(), null, line + "\n");
    }

    private synchronized void setProgress(int progress) {
        propertyChange.firePropertyChange(ListenersTypes.PROGRESS.toString(), null, progress);
    }

    private synchronized void setEndBackup(boolean isNoErrors) {
        propertyChange.firePropertyChange(ListenersTypes.FINISH.toString(), null, isNoErrors);
    }

    // Managing properties:

    @Override
    public synchronized void addPropertyListener(ListenersTypes type, PropertyChangeListener listener) {
        propertyChange.addPropertyChangeListener(type.toString(), listener);
    }

    @Override
    public synchronized void removePropertyListener(ListenersTypes type, PropertyChangeListener listener) {
        propertyChange.removePropertyChangeListener(type.toString(), listener);
    }

    // Backup name:

    @Override
    public String getBackupType() { return resourceBundle.getString("mirrorBackup"); }
}
