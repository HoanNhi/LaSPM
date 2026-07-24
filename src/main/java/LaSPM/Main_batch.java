package LaSPM;

import LaSPM.search.Miner;
import LaSPM.structures.Complex;
import LaSPM.structures.LSimplet;
import LaSPM.utils.MemoryLogger;
import LaSPM.utils.MemorySampler;
import LaSPM.utils.Pair;
import LaSPM.utils.Settings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Main_batch {
    private static final long GC_PAUSE_MILLIS = 1000L;
    private static final long MEMORY_SAMPLE_INTERVAL_MILLIS =
            Long.getLong("LaSPM.memorySampleMillis", 1000L);
    private static final String MEMORY_SUMMARY_FILE = "batch_memory_summary.csv";

    public static void main(String[] args) throws IOException, InterruptedException {
        Settings.loadOverrides(System.getProperty("LaSPM.config"));
        configureAblationMode();

        Map<String, int[]> batchSettings = new LinkedHashMap<>();
        batchSettings.put("DBLP", new int[]{180000, 190000, 200000, 210000, 220000});
        batchSettings.put("OpenAlex", new int[]{800, 1000, 1200, 1400, 1600});
        batchSettings.put("AMiner", new int[]{200, 400, 600, 800, 1000});
        batchSettings.put("Walmart", new int[]{5, 10, 15, 20, 25});
        Settings.getBatchFrequencyOverrides().forEach(batchSettings::put);

        String baseOutputFolder = Settings.outputFolder;
        File memorySummaryFile = memorySummaryFile();
        System.out.println("Writing per-run memory summary to " + memorySummaryFile.getPath());

        boolean firstMiningRun = true;
        boolean appendMemorySummary = false;
        int maxFrequencyCount = maxFrequencyCount(batchSettings);
        for (int frequencyIndex = 0; frequencyIndex < maxFrequencyCount; frequencyIndex++) {
            for (Map.Entry<String, int[]> dataset : batchSettings.entrySet()) {
                int[] frequencies = dataset.getValue();
                if (frequencyIndex >= frequencies.length) {
                    continue;
                }
                Settings.dataFile = dataset.getKey();
                Settings.minFreq = frequencies[frequencyIndex];
                List<RunMetrics> datasetMetrics = new ArrayList<>();
                System.out.println("Starting dataset " + Settings.dataFile + " with minFreq " + Settings.minFreq);

                for (int rerun = 1; rerun <= Settings.numReruns; rerun++) {
                    System.out.println("Starting batch rerun " + rerun + " of " + Settings.numReruns);
                    Settings.outputFolder = outputFolderFor(baseOutputFolder, rerun);
                    if (!firstMiningRun) {
                        stabilizeBetweenRuns();
                    }
                    RunMetrics metrics = runMining(rerun);
                    datasetMetrics.add(metrics);
                    printMemorySummary(metrics);
                    firstMiningRun = false;
                }

                writeMemorySummary(memorySummaryFile, datasetMetrics, appendMemorySummary);
                appendMemorySummary = true;
                System.out.println("Saved per-run memory summary to " + memorySummaryFile.getPath());
            }
        }
    }

    private static int maxFrequencyCount(Map<String, int[]> batchSettings) {
        int maxFrequencyCount = 0;
        for (int[] frequencies : batchSettings.values()) {
            maxFrequencyCount = Math.max(maxFrequencyCount, frequencies.length);
        }
        return maxFrequencyCount;
    }

    private static String outputFolderFor(String baseOutputFolder, int rerun) {
        if (Settings.ablation) {
            return "output/ablation/" + Settings.ablationMode + "/" + Settings.dataFile + "/" + "rerun" + rerun + "/";
        }
        return withTrailingSeparator(baseOutputFolder) + "rerun" + rerun + "/";
    }

    private static void configureAblationMode() {
        if (!Settings.ablation) {
            return;
        }
        if (Settings.disable_sorting) {
            Settings.ablationMode = "Sorting";
            System.out.println("Disable sorting");
        } else if (Settings.disable_decomposition) {
            Settings.ablationMode = "Decomposition";
            System.out.println("Disable decomposition");
        } else if (Settings.disable_dimensionAware) {
            Settings.ablationMode = "DimensionAware";
            System.out.println("Disable dimension aware pruning!");
        } else if (Settings.disable_isomorphism) {
            Settings.ablationMode = "Isomorphism";
            System.out.println("Disable quick isomorphism testing!");
        } else if (Settings.disable_localNeighborhood) {
            Settings.ablationMode = "LocalNeighborhood";
            System.out.println("Disable local neighborhood");
        } else {
            Settings.ablationMode = "Normal";
            System.out.println("Normal mode, all heuristics are enabled!");
        }
    }

    private static String withTrailingSeparator(String folder) {
        if (folder.endsWith("/") || folder.endsWith("\\")) {
            return folder;
        }
        return folder + "/";
    }

    private static void stabilizeBetweenRuns() throws InterruptedException {
        System.gc();
        Thread.sleep(GC_PAUSE_MILLIS);
    }

    private static RunMetrics runMining(int rerun) throws IOException, InterruptedException {
        System.out.println("Mining dataset " + Settings.dataFile + " with minFreq " + Settings.minFreq);

        long totalStart = System.currentTimeMillis();
        long loadMillis;
        long miningMillis;
        List result;
        Miner testMine;

        MemoryLogger.getInstance().reset();
        MemorySampler sampler = MemorySampler.start(MEMORY_SAMPLE_INTERVAL_MILLIS);
        try {
            long loadStart = System.currentTimeMillis();
            Complex complex = new Complex(Settings.dataFolder + Settings.dataFile, Settings.maxSize, false);
            loadMillis = System.currentTimeMillis() - loadStart;

            testMine = new Miner();
            long miningStart = System.currentTimeMillis();
            result = testMine.mine(complex, Settings.minFreq, Settings.minDim, Settings.maxSize);
            miningMillis = System.currentTimeMillis() - miningStart;
            sampler.sampleNow();
        } finally {
            sampler.close();
        }

        RunMetrics metrics = new RunMetrics(
                ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                rerun,
                Settings.dataFile,
                Settings.minFreq,
                Settings.minDim,
                Settings.maxSize,
                Settings.outputFolder,
                loadMillis,
                miningMillis,
                System.currentTimeMillis() - totalStart,
                result.size(),
                testMine.totalExamined,
                testMine.totalGen,
                sampler.getSamples(),
                sampler.getPeakHeapMb(),
                sampler.getPeakNonHeapMb(),
                sampler.getPeakBufferPoolMb(),
                sampler.getPeakJvmUsedMb(),
                sampler.getPeakProcessResidentMemoryMb(),
                sampler.getPeakCommittedVirtualMemoryMb());

        System.out.println("Time taken to load in seconds: " + loadMillis / 1000);
        System.out.println("Time taken to mine in seconds: " + miningMillis / 1000);
        System.out.println("The peak heap memory being used: " + metrics.peakHeapMb);

        File folder = new File(Settings.outputFolder);
        folder.mkdirs();

        writeResult(result, miningMillis, metrics.peakHeapMb);
        writeStats(testMine.totalExamined, testMine.totalGen);
        if (Settings.writeImageSet) {
            writeOccMap(testMine.getMapImage());
        }
        return metrics;
    }

    private static void writeResult(List result, long end, double memory) throws IOException {
        String fileName = Settings.dataFile + "_freq_" + Settings.minFreq + "_minDim_" + Settings.minDim
                + "_maxSize_" + Settings.maxSize;
        FileWriter fwP = new FileWriter(Settings.outputFolder + fileName);
        fwP.write("Time taken to mine is: " + end / 1000);
        fwP.write("\n");
        fwP.write("Memory taken is: " + memory);
        fwP.write("\n");
        fwP.write("------------------------------------------------------------------------------------------------\n");
        for (Object s : result) {
            if (s instanceof LSimplet)
                fwP.write(s.toString() + "\n");
            else
                fwP.write(s + "\n");
        }
        fwP.close();
    }

    private static void writeOccMap(List<Pair<String, Integer>> results) throws IOException {
        String fName = Settings.dataFile + "_freq_" + Settings.minFreq + "_minDim_" + Settings.minDim
                + "_maxSize_" + Settings.maxSize + "occMap";
        FileWriter fwP = new FileWriter(Settings.outputFolder + fName);
        for (Pair<String, Integer> s : results) {
            fwP.write(s.getA() + "\t" + s.getB() + "\n");
        }
        fwP.close();
    }

    private static void writeStats(int totalExamined, int totalGen) throws IOException {
        String fName = Settings.dataFile + "_freq_" + Settings.minFreq + "_minDim_" + Settings.minDim
                + "_maxSize_" + Settings.maxSize + "stats";
        FileWriter fwP = new FileWriter(Settings.outputFolder + fName);
        fwP.write("totalExamined: " + totalExamined);
        fwP.write("\n");
        fwP.write("totalGen: " + totalGen);
        fwP.write("\n");
        fwP.close();
    }

    private static File memorySummaryFile() {
        if (Settings.ablation) {
            return new File("output/ablation/" + Settings.ablationMode, MEMORY_SUMMARY_FILE);
        }
        return new File(Settings.outputFolder, MEMORY_SUMMARY_FILE);
    }

    private static void writeMemorySummary(File file, List<RunMetrics> datasetMetrics, boolean append) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        boolean appendToExistingFile = append && file.exists() && file.length() > 0;
        try (FileWriter writer = new FileWriter(file, appendToExistingFile)) {
            if (!appendToExistingFile) {
                writeMemorySummaryHeader(writer);
            }
            for (RunMetrics metrics : datasetMetrics) {
                writeMemorySummaryRow(writer, metrics);
            }
        }
    }

    private static void writeMemorySummaryHeader(FileWriter writer) throws IOException {
        writer.write(String.join(",",
                "started_at",
                "rerun",
                "dataset",
                "minFreq",
                "minDim",
                "maxSize",
                "load_seconds",
                "mining_seconds",
                "total_seconds",
                "patterns",
                "totalExamined",
                "totalGen",
                "memory_samples",
                "peak_heap_mb",
                "peak_non_heap_mb",
                "peak_buffer_pool_mb",
                "peak_jvm_used_mb",
                "peak_process_resident_mb",
                "peak_committed_virtual_mb",
                "output_folder"));
        writer.write("\n");
    }

    private static void writeMemorySummaryRow(FileWriter writer, RunMetrics metrics) throws IOException {
        List<String> row = new ArrayList<>();
        row.add(csvValue(metrics.startedAt));
        row.add(String.valueOf(metrics.rerun));
        row.add(csvValue(metrics.dataset));
        row.add(String.valueOf(metrics.minFreq));
        row.add(String.valueOf(metrics.minDim));
        row.add(String.valueOf(metrics.maxSize));
        row.add(seconds(metrics.loadMillis));
        row.add(seconds(metrics.miningMillis));
        row.add(seconds(metrics.totalMillis));
        row.add(String.valueOf(metrics.patterns));
        row.add(String.valueOf(metrics.totalExamined));
        row.add(String.valueOf(metrics.totalGen));
        row.add(String.valueOf(metrics.memorySamples));
        row.add(megabytes(metrics.peakHeapMb));
        row.add(megabytes(metrics.peakNonHeapMb));
        row.add(megabytes(metrics.peakBufferPoolMb));
        row.add(megabytes(metrics.peakJvmUsedMb));
        row.add(megabytes(metrics.peakProcessResidentMb));
        row.add(megabytes(metrics.peakCommittedVirtualMemoryMb));
        row.add(csvValue(metrics.outputFolder));

        writer.write(String.join(",", row));
        writer.write("\n");
    }

    private static void printMemorySummary(RunMetrics metrics) {
        System.out.println(String.format(Locale.US,
                "Memory peak for %s minFreq %d: process=%s, JVM-used=%s, heap=%s",
                metrics.dataset,
                metrics.minFreq,
                displayMegabytes(metrics.peakProcessResidentMb),
                displayMegabytes(metrics.peakJvmUsedMb),
                displayMegabytes(metrics.peakHeapMb)));
    }

    private static String seconds(long millis) {
        return String.format(Locale.US, "%.3f", millis / 1000d);
    }

    private static String megabytes(double value) {
        if (Double.isNaN(value)) {
            return "";
        }
        return String.format(Locale.US, "%.3f", value);
    }

    private static String displayMegabytes(double value) {
        if (Double.isNaN(value)) {
            return "n/a";
        }
        return String.format(Locale.US, "%.3f MB", value);
    }

    private static String csvValue(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static final class RunMetrics {
        private final String startedAt;
        private final int rerun;
        private final String dataset;
        private final int minFreq;
        private final int minDim;
        private final int maxSize;
        private final String outputFolder;
        private final long loadMillis;
        private final long miningMillis;
        private final long totalMillis;
        private final int patterns;
        private final int totalExamined;
        private final int totalGen;
        private final int memorySamples;
        private final double peakHeapMb;
        private final double peakNonHeapMb;
        private final double peakBufferPoolMb;
        private final double peakJvmUsedMb;
        private final double peakProcessResidentMb;
        private final double peakCommittedVirtualMemoryMb;

        private RunMetrics(String startedAt,
                           int rerun,
                           String dataset,
                           int minFreq,
                           int minDim,
                           int maxSize,
                           String outputFolder,
                           long loadMillis,
                           long miningMillis,
                           long totalMillis,
                           int patterns,
                           int totalExamined,
                           int totalGen,
                           int memorySamples,
                           double peakHeapMb,
                           double peakNonHeapMb,
                           double peakBufferPoolMb,
                           double peakJvmUsedMb,
                           double peakProcessResidentMb,
                           double peakCommittedVirtualMemoryMb) {
            this.startedAt = startedAt;
            this.rerun = rerun;
            this.dataset = dataset;
            this.minFreq = minFreq;
            this.minDim = minDim;
            this.maxSize = maxSize;
            this.outputFolder = outputFolder;
            this.loadMillis = loadMillis;
            this.miningMillis = miningMillis;
            this.totalMillis = totalMillis;
            this.patterns = patterns;
            this.totalExamined = totalExamined;
            this.totalGen = totalGen;
            this.memorySamples = memorySamples;
            this.peakHeapMb = peakHeapMb;
            this.peakNonHeapMb = peakNonHeapMb;
            this.peakBufferPoolMb = peakBufferPoolMb;
            this.peakJvmUsedMb = peakJvmUsedMb;
            this.peakProcessResidentMb = peakProcessResidentMb;
            this.peakCommittedVirtualMemoryMb = peakCommittedVirtualMemoryMb;
        }
    }
}
