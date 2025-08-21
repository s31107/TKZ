package Mirror;

public enum StatisticsEnum {
    COPIED_FILES("copiedFiles", false), NOT_COPIED_FILE("notCopiedFiles", true),
    CREATED_DIRS("createdDirs", false),
    NOT_CREATED_DIRECTORIES("notCreatedDirectories", true),
    REMOVED_FILES("removedFiles", false),
    NOT_VISITED_FILES("notVisitedFiles", true),
    NOT_VISITED_DIRS("notVisitedDirs", true),
    NOT_REMOVED_FILES("notRemovedFiles", true),
    REMOVED_DIRECTORIES("removedDirectories", false),
    NOT_REMOVED_DIRECTORIES("notRemovedDirectories", true);

    private final String bundleKey;
    private final boolean errorStatistic;

    StatisticsEnum(String bundleKeyArg, boolean isError) {
        // Key from Resource Bundle:
        bundleKey = bundleKeyArg;
        // Classifies enum records if it's an error statistic or not:
        errorStatistic = isError;
    }
    // Getters:
    public String getMessageName() { return bundleKey; }
    public boolean isErrorStatistic() { return errorStatistic; }
}
