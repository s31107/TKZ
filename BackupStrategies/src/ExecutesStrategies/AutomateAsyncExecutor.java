package ExecutesStrategies;

import Utils.SimplePair;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AutomateAsyncExecutor implements BackupExecutor {
    private ExecutorService executor;
    protected final Logger logger;

    // Simple pair of paths set (source path, destination path) and devices where paths are stored:
    private static class copyDevices {
        public final SimplePair<Path> pathSet;
        public final FileStore srcFileStore, dstFileStore;

        public copyDevices(SimplePair<Path> pathPair) throws IOException {
            // Global variables:
            pathSet = pathPair;
            // Declaring devices:
            srcFileStore = Files.getFileStore(pathPair.key());
            dstFileStore = Files.getFileStore(pathPair.val());
        }

        // Objects are only equals when two devices are the same, paths set are only information data:

        @Override
        public int hashCode() { return Objects.hash(srcFileStore, dstFileStore); }

        @Override
        public boolean equals(Object o) {
            if (o instanceof copyDevices cd) {
                return Objects.equals(srcFileStore, cd.srcFileStore) && Objects.equals(dstFileStore, cd.dstFileStore);
            } return false;
        }
    }

    public AutomateAsyncExecutor(Logger log) {
        // Global variables:
        logger = log;
    }

    public void join() throws InterruptedException {
        // Waiting forever for backup finish:
        while (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
            logger.log(Level.INFO, "Joining executor...");
        }
    }

    @Override
    public <R> void execute(List<SimplePair<Path>> backupPaths, BiFunction<Path, Path, R> backupStrategy,
                            BiFunction<R, R, R> mergeStrategy, Consumer<R> finishStrategy,
                            BiConsumer<IOException, SimplePair<Path>> pathsErrorStrategy) {
        // Declaring new ExecutorService:
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        // Declaring tasks dictionary:
        Map<copyDevices, CompletableFuture<R>> executorList = new HashMap<>();

        // Exception strategy when backupStrategy throw any exceptions:
        Function<Throwable, R> backupExceptionStrategy = exc -> {
            logger.log(Level.SEVERE, "Exception thrown from backup instance!", exc);
            return null;
        };
        // Merge strategy which manages any thrown exceptions from mergeStrategy:
        BiFunction<R, R, R> mergeApply = (backupReturn1, backupReturn2) -> {
            try {
                return mergeStrategy.apply(backupReturn1, backupReturn2);
            } catch (Throwable exc) {
                logger.log(Level.SEVERE, "Exception thrown from merge function!", exc);
                return null;
            }
        };
        // Finish strategy which manages any thrown exception from finishStrategy:
        Consumer<R> finishAccept = result -> {
            try {
                finishStrategy.accept(result);
            } catch (Throwable exc) {
                logger.log(Level.SEVERE, "Exception thrown from finish function!", exc);
            }
        };

        copyDevices copyDevices;
        for (SimplePair<Path> pathSet : backupPaths) {
            try {
                // Declaring devices of current path set:
                copyDevices = new copyDevices(pathSet);
                executorList.compute(copyDevices, (key, val) -> {
                    if (val == null) {
                        // Creating new subtree and new backup as separated task:
                        // Also catching any errors thrown from backup instance:
                        return CompletableFuture.supplyAsync(() -> backupStrategy.apply(
                                key.pathSet.key(), key.pathSet.val()), executor).exceptionally(backupExceptionStrategy);

                    } else {
                        // Queueing new task in existing tree:
                        return val.thenApply(statistics -> {
                            R backupReturn;
                            // Invoking backup task and catching any exceptions:
                            try {
                                backupReturn = backupStrategy.apply(key.pathSet.key(), key.pathSet.val());
                            } catch (Throwable exc) { backupReturn = backupExceptionStrategy.apply(exc); }
                            // Merging two results using merge strategy:
                            return mergeApply.apply(statistics, backupReturn);
                        });
                    }
                });
            } catch (IOException exc) {
                // Catching exceptions to getting device id from paths set:
                pathsErrorStrategy.accept(exc, pathSet);
            }
        }
        // Merging all CompletableFutures from executorList into one CompletableFuture using mergeFunction:
        Optional<CompletableFuture<R>> rFuture = executorList.values().stream().reduce(
                (future1, future2) -> future1.thenCombine(future2, mergeApply));
        // Disposing ExecutorService and cleaning after backup:
        rFuture.ifPresentOrElse(future -> future.thenAccept(finishAccept).thenRun(
                executor::shutdown), () -> {
            finishAccept.accept(null);
            executor.shutdown();
        });
    }
}
