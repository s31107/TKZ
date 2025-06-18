package ExecutesStrategies;

import Utils.SimplePair;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public interface BackupExecutor {
    <R> void execute(List<SimplePair<Path>> backupPaths, BiFunction<Path, Path, R> backupStrategy,
                     BiFunction<R, R, R> mergeStrategy, Consumer<R> finishStrategy,
                     BiConsumer<IOException, SimplePair<Path>> pathsErrorStrategy);
    void join() throws InterruptedException;
}
