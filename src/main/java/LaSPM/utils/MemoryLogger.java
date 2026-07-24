package LaSPM.utils;
/*
 *  Copyright (c) 2008-2012 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * This class is used to record the maximum memory usaged of an algorithm during
 * a given execution. It is implemented by using the "singleton" design pattern.
 * The memory logger has the option of saving the logged values to a file if it
 * is set to be in recording mode, and a file path is provided.
 *
 */
public class MemoryLogger {

    private static final long BYTES_PER_MEBIBYTE = 1024L * 1024L;
    private static final long DEFAULT_SAMPLING_INTERVAL_MILLIS = 100L;

    // the only instance of this class (this is the "singleton" design pattern)
    private static final MemoryLogger instance = new MemoryLogger(MemoryLogger::usedHeapBytes);

    // variable to store the maximum memory usage
    private final AtomicLong maxMemoryBytes = new AtomicLong();
    private final LongSupplier memorySource;
    private ScheduledExecutorService monitorExecutor;

    // A boolean flag to indicate whether the recording mode is on or off
    private boolean recordingMode = false;

    // A file object to store the output file name
    private File outputFile = null;

    // A buffered writer object to write the memory usage values to the file
    private BufferedWriter writer = null;

    MemoryLogger(LongSupplier memorySource) {
        this.memorySource = Objects.requireNonNull(memorySource, "memorySource");
    }

    /**
     * Method to obtain the only instance of this class
     *
     * @return instance of MemoryLogger
     */
    public static MemoryLogger getInstance() {
        return instance;
    }

    /**
     * To get the maximum amount of memory used until now
     *
     * @return a double value indicating memory as megabytes
     */
    public double getMaxMemory() {
        return bytesToMebibytes(maxMemoryBytes.get());
    }

    /**
     * Reset the maximum amount of memory recorded.
     */
    public void reset() {
        maxMemoryBytes.set(0L);
    }

    /**
     * Start sampling used JVM heap every 100 milliseconds.
     */
    public void startMonitoring() {
        startMonitoring(DEFAULT_SAMPLING_INTERVAL_MILLIS);
    }

    /**
     * Start sampling used JVM heap at the requested interval.
     *
     * @param samplingIntervalMillis interval between samples in milliseconds
     */
    public synchronized void startMonitoring(long samplingIntervalMillis) {
        if (samplingIntervalMillis <= 0) {
            throw new IllegalArgumentException("samplingIntervalMillis must be positive");
        }

        if (monitorExecutor != null) {
            stopMonitoring();
        }
        reset();
        checkMemory();

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "laspm-memory-monitor");
            thread.setDaemon(true);
            return thread;
        };
        monitorExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
        monitorExecutor.scheduleAtFixedRate(
                this::checkMemorySafely,
                samplingIntervalMillis,
                samplingIntervalMillis,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Stop periodic sampling and take one final heap sample.
     */
    public synchronized void stopMonitoring() {
        if (monitorExecutor != null) {
            monitorExecutor.shutdownNow();
            monitorExecutor = null;
        }
        checkMemory();
    }

    /**
     * Check the current memory usage and record it if it is higher than the amount
     * of memory previously recorded.
     *
     * @return the memory usage in megabytes
     */
    public double checkMemory() {
        long currentMemoryBytes = Math.max(0L, memorySource.getAsLong());
        maxMemoryBytes.accumulateAndGet(currentMemoryBytes, Math::max);
        double currentMemory = bytesToMebibytes(currentMemoryBytes);
        // If the recording mode is on
        synchronized (this) {
            if (recordingMode && writer != null) {
                // Try to write the current memory value to the file
                try {
                    writer.write(currentMemory + "\n");
                } catch (IOException e) {
                    // Handle the exception
                    e.printStackTrace();
                }
            }
        }
        return currentMemory;
    }

    private void checkMemorySafely() {
        try {
            checkMemory();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static double bytesToMebibytes(long bytes) {
        return bytes / (double) BYTES_PER_MEBIBYTE;
    }

    /**
     * A method to set the recording mode and the output file name
     *
     * @param fileName the path to a file for saving recorded values
     */
    public synchronized void startRecordingMode(String fileName) {
        // Create a new file object with the given file name
        outputFile = new File(fileName);
        // Try to create a new buffered writer object with the file object
        try {
            writer = new BufferedWriter(new FileWriter(outputFile));
            recordingMode = true;
        } catch (IOException e) {
            // Handle the exception
            recordingMode = false;
            writer = null;
            e.printStackTrace();
        }
    }

    /**
     * A method to stop the recording mode and close the file
     */
    public synchronized void stopRecordingMode() {
        // If the recording mode is on
        if (recordingMode) {
            // Try to close the buffered writer object
            try {
                writer.close();
            } catch (IOException e) {
                // Handle the exception
                e.printStackTrace();
            }
            // Set the recording mode flag to false
            recordingMode = false;
            writer = null;
        }
    }

}

