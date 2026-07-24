package LaSPM;
import LaSPM.search.Miner;
import LaSPM.structures.Complex;
import LaSPM.utils.Pair;
import LaSPM.utils.Settings;
import LaSPM.utils.MemoryLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        Settings.loadMainOverrides(System.getProperty("LaSPM.mainConfig"));

        Complex complex = new Complex(Settings.dataFolder + Settings.dataFile, Settings.maxSize, false);
        Miner testMine = new Miner();

        long start = System.currentTimeMillis();
//        MemoryLogger.getInstance().reset();

        //Starting to mine
        List result = testMine.mine(complex, Settings.minFreq, Settings.minDim, Settings.maxSize);
        long end = System.currentTimeMillis() - start;
//        MemoryLogger.getInstance().checkMemory();
        System.out.println("Time taken to mine in seconds: " + end/1000);
        System.out.println("The memory being used: " + MemoryLogger.getInstance().getMaxMemory());

        File folder = new File(Settings.outputFolder);
        folder.mkdirs();

        writeResult(result, end, MemoryLogger.getInstance().getMaxMemory());
        writeStats(testMine.totalExamined);
        if (Settings.writeImageSet){
            writeOccMap(testMine.getMapImage());
        }

    }

    private static void writeResult(List result, long end, double memory) throws IOException {
        String fileName = Settings.dataFile + "_freq_" + Settings.minFreq + "_minDim_" + Settings.minDim
                + "_maxSize_" + Settings.maxSize;
        FileWriter fwP = new FileWriter(Settings.outputFolder + fileName);
        fwP.write("Time taken to mine is: " + end/1000);
        fwP.write("\n");
        fwP.write("Memory taken is: " + memory);
        fwP.write("\n");
        fwP.write("------------------------------------------------------------------------------------------------\n");
        for (Object s : result) {
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

    private static void writeStats(int totalGen) throws IOException {
        String fName = Settings.dataFile + "_freq_" + Settings.minFreq + "_minDim_" + Settings.minDim
                + "_maxSize_" + Settings.maxSize + "stats";
        FileWriter fwP = new FileWriter(Settings.outputFolder + fName);
        fwP.write("totalGen: " + totalGen);
        fwP.write("\n");
        fwP.close();
    }
}
