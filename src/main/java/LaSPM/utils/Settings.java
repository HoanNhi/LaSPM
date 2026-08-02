package LaSPM.utils;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class Settings {
    public static String dataFolder = "dataExperiment/plmsc/";
    public static String dataFile = "testSmallData2";
    public static String outputFolder = "output/";
    public static int numReruns = 4;
    public static boolean ablation = true;
    public static String ablationMode;
    public static boolean allMatches = false;
    public static int minDim = 0;
    public static int maxSize = -1;
    public static int minFreq = 1;
    public static long timeout;
    public static boolean disable_localNeighborhood = false;
    public static boolean disable_dimensionAware = false;
    public static boolean disable_decomposition = false;
    public static boolean disable_isomorphism = false;
    public static boolean disable_multiset = false;
    public static boolean disable_sorting = false;
    public static boolean limited = true;
    // whether you want to write on disk the image sets
    public static boolean writeImageSet = true;
    private static final Map<String, int[]> batchFrequencyOverrides =
            new LinkedHashMap<>();

    /**
     * Overrides batch and ablation settings from a Java properties file.
     * Omitted keys retain their current values, so command-line runs without a
     * config file continue to use the defaults above.
     */
    public static void loadOverrides(String configFile) throws IOException {
        Properties properties = loadProperties(configFile);
        if (properties == null) {
            return;
        }

        dataFolder = readFolder(properties, "dataFolder", dataFolder);
        outputFolder = readFolder(properties, "outputFolder", outputFolder);
        numReruns = readInteger(properties, "numReruns", numReruns);
        minDim = readInteger(properties, "minDim", minDim);
        maxSize = readInteger(properties, "maxSize", maxSize);
        limited = readBoolean(properties, "limited", limited);
        writeImageSet = readBoolean(
                properties,
                "writeImageSets",
                readBoolean(properties, "writeImageSet", writeImageSet));
        ablation = readBoolean(properties, "ablation", ablation);
        disable_localNeighborhood = readBoolean(
                properties,
                "disable_localNeighborhood",
                disable_localNeighborhood);
        disable_dimensionAware = readBoolean(
                properties,
                "disable_dimensionAware",
                disable_dimensionAware);
        disable_decomposition = readBoolean(
                properties,
                "disable_decomposition",
                disable_decomposition);
        disable_isomorphism = readBoolean(
                properties,
                "disable_isomorphism",
                disable_isomorphism);
        disable_multiset = readBoolean(
                properties,
                "disable_multiset",
                disable_multiset);
        disable_sorting = readBoolean(
                properties,
                "disable_sorting",
                disable_sorting);
        loadBatchFrequencyOverrides(properties);

        validateBatchSettings();
        validateAblationSettings();
    }

    /**
     * Returns configured batch frequency arrays, keyed by dataset name.
     * Arrays are copied so callers cannot mutate the stored configuration.
     */
    public static Map<String, int[]> getBatchFrequencyOverrides() {
        Map<String, int[]> copy = new LinkedHashMap<>();
        batchFrequencyOverrides.forEach((dataset, thresholds) ->
                copy.put(dataset, thresholds.clone()));
        return copy;
    }

    /**
     * Overrides the single-run settings used by {@code LaSPM.Main}.
     * The plural {@code writeImageSets} key is preferred; the singular field
     * name remains accepted for compatibility.
     */
    public static void loadMainOverrides(String configFile) throws IOException {
        Properties properties = loadProperties(configFile);
        if (properties == null) {
            return;
        }

        dataFolder = readString(properties, "dataFolder", dataFolder);
        dataFile = readString(properties, "dataFile", dataFile);
        outputFolder = readString(properties, "outputFolder", outputFolder);
        minFreq = readInteger(properties, "minFreq", minFreq);
        maxSize = readInteger(properties, "maxSize", maxSize);
        limited = readBoolean(properties, "limited", limited);
        writeImageSet = readBoolean(
                properties,
                "writeImageSets",
                readBoolean(properties, "writeImageSet", writeImageSet));
    }

    private static Properties loadProperties(String configFile) throws IOException {
        if (configFile == null || configFile.isBlank()) {
            return null;
        }

        Path path = Path.of(configFile);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Configuration file does not exist: " + path);
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return properties;
    }

    private static void loadBatchFrequencyOverrides(Properties properties) {
        batchFrequencyOverrides.clear();
        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("batch."))
                .sorted()
                .forEach(key -> {
                    String dataset = key.substring("batch.".length()).trim();
                    if (dataset.isEmpty()) {
                        throw new IllegalArgumentException(
                                "A dataset name is required after 'batch.'.");
                    }
                    batchFrequencyOverrides.put(
                            dataset,
                            parseFrequencyThresholds(
                                    key, properties.getProperty(key)));
                });
    }

    private static int[] parseFrequencyThresholds(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "At least one threshold is required for '" + key + "'.");
        }

        String[] entries = value.split(",", -1);
        int[] thresholds = new int[entries.length];
        for (int index = 0; index < entries.length; index++) {
            try {
                thresholds[index] = Integer.parseInt(entries[index].trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Expected comma-separated integers for configuration key '"
                                + key + "'.",
                        exception);
            }
            if (thresholds[index] <= 0) {
                throw new IllegalArgumentException(
                        "Thresholds for '" + key + "' must be positive.");
            }
        }
        return thresholds;
    }

    private static String readString(
            Properties properties,
            String key,
            String fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : value.trim();
    }

    private static String readFolder(
            Properties properties,
            String key,
            String fallback) {
        String folder = readString(properties, key, fallback);
        if (folder == null || folder.isBlank()) {
            throw new IllegalArgumentException(
                    "Configuration key '" + key + "' cannot be blank.");
        }
        if (folder.endsWith("/") || folder.endsWith("\\")) {
            return folder;
        }
        return folder + "/";
    }

    private static int readInteger(
            Properties properties,
            String key,
            int fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Expected an integer for configuration key '" + key + "'.",
                    exception);
        }
    }

    private static boolean readBoolean(
            Properties properties,
            String key,
            boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        throw new IllegalArgumentException(
                "Expected true or false for configuration key '" + key + "'.");
    }

    private static void validateAblationSettings() {
        if (!ablation) {
            return;
        }

        int disabledHeuristics = 0;
        disabledHeuristics += disable_localNeighborhood ? 1 : 0;
        disabledHeuristics += disable_dimensionAware ? 1 : 0;
        disabledHeuristics += disable_decomposition ? 1 : 0;
        disabledHeuristics += disable_isomorphism ? 1 : 0;
        disabledHeuristics += disable_multiset ? 1 : 0;
        disabledHeuristics += disable_sorting ? 1 : 0;
        if (disabledHeuristics > 1) {
            throw new IllegalArgumentException(
                    "An ablation run may disable at most one heuristic.");
        }
    }

    private static void validateBatchSettings() {
        if (dataFolder == null || dataFolder.isBlank()) {
            throw new IllegalArgumentException(
                    "Configuration key 'dataFolder' cannot be blank.");
        }
        if (outputFolder == null || outputFolder.isBlank()) {
            throw new IllegalArgumentException(
                    "Configuration key 'outputFolder' cannot be blank.");
        }
        if (numReruns <= 0) {
            throw new IllegalArgumentException(
                    "Configuration key 'numReruns' must be positive.");
        }
    }
}
