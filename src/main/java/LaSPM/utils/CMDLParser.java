package LaSPM.utils;

public class CMDLParser {
    
    public static void parse(String[] args) {

        if (args != null && args.length > 0) {
            parseArgs(args);
        }
    }

    private static void parseArgs(String[] args) {
        for (String arg : args) {
            String[] parts = arg.split("=");
            parseArg(parts[0], parts[1]);
        }
    }

    private static void parseArg(String key, String value) {
        if (key.compareTo("dataFolder") == 0) {
            Settings.dataFolder = value;
        } else if (key.compareTo("outputFolder") == 0) {
            Settings.outputFolder = value;
        } else if (key.compareTo("dataFile") == 0) {
            Settings.dataFile = value;
        } else if (key.compareTo("minSize") == 0) {
            Settings.minDim = Integer.parseInt(value);
        } else if (key.compareTo("maxSize") == 0) {
            Settings.maxSize = Integer.parseInt(value);
        } else if (key.compareTo("minFreq") == 0) {
            Settings.minFreq = Integer.parseInt(value);
        }
    }
    
}
