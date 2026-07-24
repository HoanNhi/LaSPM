package LaSPM.LFreSCo;

import LaSPM.LFreSCo.search.Miner;
import LaSPM.structures.Complex;
import LaSPM.utils.MemoryLogger;
import LaSPM.utils.Pair;
import LaSPM.utils.Settings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class LFreSCo {
    public static void main(String[] args) throws IOException, InterruptedException {
        Settings.loadMainOverrides(System.getProperty("LaSPM.LFreSCo.mainConfig"));

        long start = System.currentTimeMillis();
        Complex testing = new Complex(Settings.dataFolder + Settings.dataFile, Settings.maxSize, true);
        Miner testMine = new Miner();

        //Starting to mine
        List result = testMine.mine(testing, Settings.minFreq, Settings.minDim, Settings.maxSize, Settings.limited, Settings.timeout);
        long end = System.currentTimeMillis() - start;
        System.out.println("Time taken to mine in seconds: " + end/1000);
        System.out.println("Peak JVM heap used (MiB): " + MemoryLogger.getInstance().getMaxMemory());
        System.out.println("Number of frequent patterns: " + result.size());

        File folder = new File(Settings.outputFolder);
        folder.mkdirs();

        writeResult(result, end, MemoryLogger.getInstance().getMaxMemory());
        if (Settings.writeImageSet){
            writeOccMap(testMine.getOccMap());
        }

    }

    private static void writeResult(List result, long end, double memory) throws IOException {
        String fileName = Settings.dataFile + "_freq_" + Settings.minFreq + "_minDim_" + Settings.minDim
                + "_maxSize_" + Settings.maxSize + "_allMatch_" + Settings.allMatches;
        FileWriter fwP = new FileWriter(Settings.outputFolder + fileName);
        fwP.write("Time taken to mine is: " + end/1000);
        fwP.write("\n");
        fwP.write("Peak JVM heap used (MiB): " + memory);
        fwP.write("\n");
        fwP.write("------------------------------------------------------------------------------------------------\n");
        for (Object s : result) {
            fwP.write(s.toString() + "\n");
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
}
