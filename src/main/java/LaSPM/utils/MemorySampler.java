package LaSPM.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MemorySampler implements AutoCloseable {
    private static final double BYTES_PER_MEGABYTE = 1024d * 1024d;
    private static final double KILOBYTES_PER_MEGABYTE = 1024d;
    private static final long COMMAND_TIMEOUT_MILLIS = 2000L;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final long sampleIntervalMillis;
    private final long pid;
    private final Thread worker;

    private volatile int samples;
    private volatile double peakHeapMb;
    private volatile double peakNonHeapMb;
    private volatile double peakBufferPoolMb;
    private volatile double peakJvmUsedMb;
    private volatile double peakCommittedVirtualMemoryMb = Double.NaN;
    private volatile double peakProcessResidentMemoryMb = Double.NaN;

    private MemorySampler(long sampleIntervalMillis) {
        this.sampleIntervalMillis = sampleIntervalMillis;
        this.pid = ProcessHandle.current().pid();
        this.worker = new Thread(this::run, "LaSPM-memory-sampler");
        this.worker.setDaemon(true);
    }

    public static MemorySampler start(long sampleIntervalMillis) {
        MemorySampler sampler = new MemorySampler(sampleIntervalMillis);
        sampler.sampleNow();
        sampler.worker.start();
        return sampler;
    }

    public synchronized void sampleNow() {
        double heapMb = MemoryLogger.getInstance().checkMemory();
        double nonHeapMb = currentNonHeapMemoryMb();
        double bufferPoolMb = currentBufferPoolMemoryMb();
        double jvmUsedMb = heapMb + nonHeapMb + bufferPoolMb;
        double committedVirtualMemoryMb = currentCommittedVirtualMemoryMb();
        double processResidentMemoryMb = currentProcessResidentMemoryMb();

        peakHeapMb = Math.max(peakHeapMb, heapMb);
        peakNonHeapMb = Math.max(peakNonHeapMb, nonHeapMb);
        peakBufferPoolMb = Math.max(peakBufferPoolMb, bufferPoolMb);
        peakJvmUsedMb = Math.max(peakJvmUsedMb, jvmUsedMb);
        peakCommittedVirtualMemoryMb = maxNullable(peakCommittedVirtualMemoryMb, committedVirtualMemoryMb);
        peakProcessResidentMemoryMb = maxNullable(peakProcessResidentMemoryMb, processResidentMemoryMb);
        samples++;
    }

    public int getSamples() {
        return samples;
    }

    public double getPeakHeapMb() {
        return peakHeapMb;
    }

    public double getPeakNonHeapMb() {
        return peakNonHeapMb;
    }

    public double getPeakBufferPoolMb() {
        return peakBufferPoolMb;
    }

    public double getPeakJvmUsedMb() {
        return peakJvmUsedMb;
    }

    public double getPeakCommittedVirtualMemoryMb() {
        return peakCommittedVirtualMemoryMb;
    }

    public double getPeakProcessResidentMemoryMb() {
        return peakProcessResidentMemoryMb;
    }

    @Override
    public void close() {
        running.set(false);
        worker.interrupt();
        try {
            worker.join(sampleIntervalMillis + COMMAND_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        sampleNow();
    }

    private void run() {
        while (running.get()) {
            try {
                Thread.sleep(sampleIntervalMillis);
                sampleNow();
            } catch (InterruptedException e) {
                if (running.get()) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static double currentNonHeapMemoryMb() {
        MemoryUsage usage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        return bytesToMegabytes(usage.getUsed());
    }

    private static double currentBufferPoolMemoryMb() {
        long bytes = 0L;
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            long used = pool.getMemoryUsed();
            if (used > 0L) {
                bytes += used;
            }
        }
        return bytesToMegabytes(bytes);
    }

    private static double currentCommittedVirtualMemoryMb() {
        java.lang.management.OperatingSystemMXBean bean =
                ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean) {
            long bytes = ((com.sun.management.OperatingSystemMXBean) bean).getCommittedVirtualMemorySize();
            return bytesToMegabytes(bytes);
        }
        return Double.NaN;
    }

    private double currentProcessResidentMemoryMb() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (osName.contains("win")) {
                return currentWindowsWorkingSetMb();
            }
            return currentUnixResidentSetMb();
        } catch (IOException e) {
            return Double.NaN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Double.NaN;
        }
    }

    private double currentWindowsWorkingSetMb() throws IOException, InterruptedException {
        String line = firstCommandLine(
                "tasklist",
                "/FI",
                "PID eq " + pid,
                "/FO",
                "CSV",
                "/NH");
        if (line == null || line.startsWith("INFO:")) {
            return Double.NaN;
        }
        List<String> values = parseCsvLine(line);
        if (values.isEmpty()) {
            return Double.NaN;
        }
        return kilobytesToMegabytes(parseKilobytes(values.get(values.size() - 1)));
    }

    private double currentUnixResidentSetMb() throws IOException, InterruptedException {
        String line = firstCommandLine("ps", "-o", "rss=", "-p", String.valueOf(pid));
        if (line == null || line.isBlank()) {
            return Double.NaN;
        }
        return kilobytesToMegabytes(parseKilobytes(line));
    }

    private static String firstCommandLine(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return null;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    return line.trim();
                }
            }
        }
        return null;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    private static long parseKilobytes(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(digits);
    }

    private static double bytesToMegabytes(long bytes) {
        if (bytes < 0L) {
            return Double.NaN;
        }
        return bytes / BYTES_PER_MEGABYTE;
    }

    private static double kilobytesToMegabytes(long kilobytes) {
        return kilobytes / KILOBYTES_PER_MEGABYTE;
    }

    private static double maxNullable(double currentMax, double value) {
        if (Double.isNaN(value)) {
            return currentMax;
        }
        if (Double.isNaN(currentMax)) {
            return value;
        }
        return Math.max(currentMax, value);
    }
}
