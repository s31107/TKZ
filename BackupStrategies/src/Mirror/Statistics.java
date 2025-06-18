package Mirror;

import java.util.ResourceBundle;

public class Statistics {
    protected final ResourceBundle resourceBundle;
    private final long[] stats;

    public Statistics() {
        // List of stats:
        stats = new long[StatisticsEnum.values().length];
        // Resource Bundle of all statistics communicates:
        resourceBundle = ResourceBundle.getBundle("MirrorBundles.Statistics");
    }

    public void increment(StatisticsEnum type) {
        // Incrementing specified statistic:
        ++stats[type.ordinal()];
    }

    public static Statistics merge(Statistics statistics1, Statistics statistics2) {
        // If statistics are not present, creating empty statistics:
        if (statistics1 == null) { statistics1 = new Statistics(); }
        if (statistics2 == null) { statistics2 = new Statistics(); }
        // Summing all statistics into one:
        for (int iter = 0; iter < statistics1.stats.length; ++iter) {
            statistics1.stats[iter] += statistics2.stats[iter];
        }
        // Returning summed statistics:
        return statistics1;
    }
    public String getMessage(StatisticsEnum type) {
        // Getting message from resource bundle from specified type and substituting statistic:
        return resourceBundle.getString(type.getMessageName()).formatted(stats[type.ordinal()]);
    }

    public boolean isExceptionsNotRaised() {
        // Checking if any error statistics is present in stats:
        for (StatisticsEnum statisticsEnum : StatisticsEnum.values()) {
            if (statisticsEnum.isErrorStatistic() && stats[statisticsEnum.ordinal()] != 0) {
                return false;
            }
        } return true;
    }
}
