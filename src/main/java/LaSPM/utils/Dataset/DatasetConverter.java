package LaSPM.utils.Dataset;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts labeled simplex datasets into graph and unlabeled simplex variants.
 *
 * Input format:
 * # Vertex
 * v 0 label
 * v 1 label
 *
 * # Simplex
 * 0 1 2 - label
 * 2 3 - label
 */
public class DatasetConverter {

    private enum Mode {
        GRAPH("_graph"),
        UNLABELED_SIMPLICES("_unlabeled_simplices"),
        UNLABELED_SIMPLICES_PLMSC("_unlabeld_simplices_PLMSC"),
        GRAPH_PLMSC("_graph_PLMSC"),
        TWO_DIMENSION_GRAPH("_2_dimension_graph"),
        TWO_DIMENSION_GRAPH_PLMSC("_2_dimension_graph_PLMSC"),
        ALL("");

        private final String suffix;

        Mode(String suffix) {
            this.suffix = suffix;
        }
    }

    private static class Dataset {
        private final Map<Integer, String> vertexLabels = new LinkedHashMap<>();
        private final List<Simplex> simplices = new ArrayList<>();
    }

    private static class Simplex {
        private final List<Integer> vertices;
        private final String label;

        Simplex(List<Integer> vertices, String label) {
            this.vertices = vertices;
            this.label = label;
        }
    }

    private static class Edge {
        private final int source;
        private final int target;
        private final String label;

        Edge(int source, int target, String label) {
            this.source = source;
            this.target = target;
            this.label = label;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || isHelp(args[0])) {
            printUsage();
            return;
        }

        Path inputPath = Paths.get(args[0]);
        Mode mode = parseMode(args[1]);
        Dataset dataset = readDataset(inputPath);

        if (mode == Mode.ALL) {
            for (Mode outputMode : Mode.values()) {
                if (outputMode != Mode.ALL) {
                    write(dataset, outputMode, defaultOutputPath(inputPath, outputMode));
                }
            }
            return;
        }

        Path outputPath = args.length >= 3
                ? Paths.get(args[2])
                : defaultOutputPath(inputPath, mode);
        write(dataset, mode, outputPath);
    }

    private static boolean isHelp(String arg) {
        return "-h".equals(arg) || "--help".equals(arg);
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java LaSPM.utils.Dataset.DatasetConverter <input-file> <mode> [output-file]");
        System.err.println();
        System.err.println("Modes:");
        System.err.println("  graph                         -> t/v/e graph projection");
        System.err.println("  unlabeled-simplices           -> simplex vertices only");
        System.err.println("  unlabeled-simplices-plmsc     -> PLMSC format with all labels set to 0");
        System.err.println("  graph-plmsc                   -> PLMSC graph projection");
        System.err.println("  2-dimension-graph             -> t/v/e graph from only 2-vertex simplices");
        System.err.println("  2-dimension-graph-plmsc       -> PLMSC graph from only 2-vertex simplices");
        System.err.println("  all                           -> write every output next to the input file");
    }

    private static Mode parseMode(String rawMode) {
        String mode = rawMode.trim().toLowerCase(Locale.ROOT);
        switch (mode) {
            case "graph":
                return Mode.GRAPH;
            case "unlabeled-simplices":
            case "unlabelled-simplices":
                return Mode.UNLABELED_SIMPLICES;
            case "unlabeled-simplices-plmsc":
            case "unlabelled-simplices-plmsc":
                return Mode.UNLABELED_SIMPLICES_PLMSC;
            case "graph-plmsc":
                return Mode.GRAPH_PLMSC;
            case "2-dimension-graph":
            case "two-dimension-graph":
            case "2d-graph":
                return Mode.TWO_DIMENSION_GRAPH;
            case "2-dimension-graph-plmsc":
            case "two-dimension-graph-plmsc":
            case "2d-graph-plmsc":
                return Mode.TWO_DIMENSION_GRAPH_PLMSC;
            case "all":
                return Mode.ALL;
            default:
                throw new IllegalArgumentException("Unknown mode: " + rawMode);
        }
    }

    private static Dataset readDataset(Path inputPath) throws IOException {
        Dataset dataset = new Dataset();
        boolean inVertexSection = false;
        boolean inSimplexSection = false;

        try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("#")) {
                    inVertexSection = line.equalsIgnoreCase("# Vertex");
                    inSimplexSection = line.equalsIgnoreCase("# Simplex");
                    continue;
                }

                if (inVertexSection) {
                    readVertexLine(dataset, line, lineNumber);
                } else if (inSimplexSection) {
                    dataset.simplices.add(readSimplexLine(line, lineNumber));
                }
            }
        }

        return dataset;
    }

    private static void readVertexLine(Dataset dataset, String line, int lineNumber) {
        String[] parts = line.split("\\s+", 3);
        if (parts.length != 3 || !"v".equals(parts[0])) {
            throw new IllegalArgumentException("Invalid vertex at line " + lineNumber + ": " + line);
        }

        int vertexId = parseInt(parts[1], "vertex id", lineNumber, line);
        dataset.vertexLabels.put(vertexId, parts[2].trim());
    }

    private static Simplex readSimplexLine(String line, int lineNumber) {
        String[] tokens = line.split("\\s+");
        int separatorIndex = -1;

        for (int i = 0; i < tokens.length; i++) {
            if ("-".equals(tokens[i])) {
                separatorIndex = i;
                break;
            }
        }

        if (separatorIndex <= 0 || separatorIndex >= tokens.length - 1) {
            throw new IllegalArgumentException("Invalid simplex at line " + lineNumber + ": " + line);
        }

        List<Integer> vertices = new ArrayList<>();
        for (int i = 0; i < separatorIndex; i++) {
            vertices.add(parseInt(tokens[i], "simplex vertex", lineNumber, line));
        }

        StringBuilder label = new StringBuilder(tokens[separatorIndex + 1]);
        for (int i = separatorIndex + 2; i < tokens.length; i++) {
            label.append(' ').append(tokens[i]);
        }

        return new Simplex(vertices, label.toString());
    }

    private static int parseInt(String value, String fieldName, int lineNumber, String line) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid " + fieldName + " at line " + lineNumber + ": " + line,
                    e
            );
        }
    }

    private static void write(Dataset dataset, Mode mode, Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        switch (mode) {
            case GRAPH:
                writeTveGraph(dataset, outputPath, true);
                break;
            case UNLABELED_SIMPLICES:
                writeUnlabeledSimplices(dataset, outputPath);
                break;
            case UNLABELED_SIMPLICES_PLMSC:
                writeUnlabeledSimplicesPlmsc(dataset, outputPath);
                break;
            case GRAPH_PLMSC:
                writePlmscGraph(dataset, outputPath, true);
                break;
            case TWO_DIMENSION_GRAPH:
                writeTveGraph(dataset, outputPath, false);
                break;
            case TWO_DIMENSION_GRAPH_PLMSC:
                writePlmscGraph(dataset, outputPath, false);
                break;
            case ALL:
                throw new IllegalArgumentException("ALL mode must be expanded before writing");
            default:
                throw new IllegalArgumentException("Unsupported mode: " + mode);
        }

        System.out.println("Wrote " + outputPath.toAbsolutePath());
    }

    private static void writeTveGraph(Dataset dataset, Path outputPath, boolean projectAllSimplices)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("t # 1");
            writer.newLine();
            writeVerticesWithoutHeader(writer, dataset.vertexLabels);

            for (Edge edge : buildEdges(dataset.simplices, projectAllSimplices)) {
                writer.write("e " + edge.source + " " + edge.target + " " + edge.label);
                writer.newLine();
            }
        }
    }

    private static void writePlmscGraph(Dataset dataset, Path outputPath, boolean projectAllSimplices)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("# Vertex");
            writer.newLine();
            writeVerticesWithoutHeader(writer, dataset.vertexLabels);
            writer.write("# Simplex");
            writer.newLine();

            for (Edge edge : buildEdges(dataset.simplices, projectAllSimplices)) {
                writer.write(edge.source + " " + edge.target + " - " + edge.label);
                writer.newLine();
            }
        }
    }

    private static void writeUnlabeledSimplices(Dataset dataset, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            for (Simplex simplex : dataset.simplices) {
                writer.write(joinVertices(simplex.vertices));
                writer.newLine();
            }
        }
    }

    private static void writeUnlabeledSimplicesPlmsc(Dataset dataset, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("# Vertex");
            writer.newLine();
            for (Integer vertexId : sortedVertexIds(dataset.vertexLabels)) {
                writer.write("v " + vertexId + " 0");
                writer.newLine();
            }

            writer.write("# Simplex");
            writer.newLine();
            for (Simplex simplex : dataset.simplices) {
                writer.write(joinVertices(simplex.vertices) + " - 0");
                writer.newLine();
            }
        }
    }

    private static void writeVerticesWithoutHeader(BufferedWriter writer, Map<Integer, String> vertexLabels)
            throws IOException {
        for (Integer vertexId : sortedVertexIds(vertexLabels)) {
            writer.write("v " + vertexId + " " + vertexLabels.get(vertexId));
            writer.newLine();
        }
    }

    private static List<Integer> sortedVertexIds(Map<Integer, String> vertexLabels) {
        List<Integer> vertexIds = new ArrayList<>(vertexLabels.keySet());
        Collections.sort(vertexIds);
        return vertexIds;
    }

    private static List<Edge> buildEdges(List<Simplex> simplices, boolean projectAllSimplices) {
        List<Edge> edges = new ArrayList<>();

        for (Simplex simplex : simplices) {
            if (!projectAllSimplices && simplex.vertices.size() != 2) {
                continue;
            }

            List<Integer> vertices = new ArrayList<>(simplex.vertices);
            vertices.sort(Comparator.naturalOrder());

            for (int i = 0; i < vertices.size(); i++) {
                for (int j = i + 1; j < vertices.size(); j++) {
                    edges.add(new Edge(vertices.get(i), vertices.get(j), simplex.label));
                }
            }
        }

        return edges;
    }

    private static String joinVertices(List<Integer> vertices) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < vertices.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(vertices.get(i));
        }
        return builder.toString();
    }

    private static Path defaultOutputPath(Path inputPath, Mode mode) {
        Path parent = inputPath.toAbsolutePath().getParent();
        Path fileName = inputPath.getFileName();
        String baseName = fileName == null ? "dataset" : stripExtension(fileName.toString());
        Path outputFile = Paths.get(baseName + mode.suffix);
        return parent == null ? outputFile : parent.resolve(outputFile);
    }

    private static String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0) {
            return fileName;
        }
        return fileName.substring(0, lastDot);
    }
}
