package LaSPM.utils.Dataset;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Converts datasets stored as parallel hyperedges.txt and hyperedge-labels.txt files.
 *
 * Input:
 *   hyperedges.txt          v1<TAB>v2<TAB>...<TAB>vn
 *   hyperedge-labels.txt    one label per hyperedge line
 *
 * Output:
 *   # Vertex
 *   v <new-vertex-id> 0
 *   ...
 *   # Simplex
 *   new-v1 new-v2 ... new-vn - label
 *
 * Vertex ids are remapped from the sorted original ids to contiguous ids starting at 0.
 */
public class HyperedgeLabelDatasetConverter {

    private static class Hyperedge {
        private final List<Integer> vertices;
        private final String label;

        Hyperedge(List<Integer> vertices, String label) {
            this.vertices = vertices;
            this.label = label;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || isHelp(args[0])) {
            printUsage();
            return;
        }

        Path datasetDirectory = Paths.get(args[0]);
        Path hyperedgesPath = datasetDirectory.resolve("hyperedges.txt");
        Path labelsPath = datasetDirectory.resolve("hyperedge-labels.txt");
        Path outputPath = args.length >= 2
                ? Paths.get(args[1])
                : datasetDirectory.resolve(datasetDirectory.getFileName() + "_converted");

        convert(hyperedgesPath, labelsPath, outputPath);
    }

    public static void convert(Path hyperedgesPath, Path labelsPath, Path outputPath) throws IOException {
        List<String> labels = Files.readAllLines(labelsPath);
        TreeSet<Integer> vertexIds = new TreeSet<>();
        List<Hyperedge> hyperedges = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(hyperedgesPath)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                if (lineNumber > labels.size()) {
                    throw new IllegalArgumentException(
                            "Missing hyperedge label for hyperedge line " + lineNumber
                    );
                }

                List<Integer> vertices = parseVertices(line, lineNumber);
                vertices.sort(Integer::compareTo);
                vertexIds.addAll(vertices);
                hyperedges.add(new Hyperedge(vertices, labels.get(lineNumber - 1).trim()));
            }

            if (lineNumber < labels.size()) {
                throw new IllegalArgumentException(
                        "Found " + labels.size() + " labels but only " + lineNumber + " hyperedges"
                );
            }
        }

        Map<Integer, Integer> oldToNewVertexIds = buildVertexIdMapping(vertexIds);
        writeOutput(outputPath, oldToNewVertexIds, hyperedges);
        System.out.println("Wrote " + outputPath.toAbsolutePath());
    }

    private static List<Integer> parseVertices(String line, int lineNumber) {
        String[] parts = line.split("\\s+");
        TreeSet<Integer> vertices = new TreeSet<>();

        for (String part : parts) {
            try {
                vertices.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Invalid vertex id at hyperedge line " + lineNumber + ": " + line,
                        e
                );
            }
        }

        return new ArrayList<>(vertices);
    }

    private static Map<Integer, Integer> buildVertexIdMapping(TreeSet<Integer> vertexIds) {
        Map<Integer, Integer> oldToNewVertexIds = new LinkedHashMap<>();
        int nextVertexId = 0;

        for (Integer vertexId : vertexIds) {
            oldToNewVertexIds.put(vertexId, nextVertexId);
            nextVertexId++;
        }

        return oldToNewVertexIds;
    }

    private static void writeOutput(
            Path outputPath,
            Map<Integer, Integer> oldToNewVertexIds,
            List<Hyperedge> hyperedges
    )
            throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("# Vertex");
            writer.newLine();

            for (Integer vertexId : oldToNewVertexIds.values()) {
                writer.write("v " + vertexId + " 0");
                writer.newLine();
            }

            writer.write("# Simplex");
            writer.newLine();

            for (Hyperedge hyperedge : hyperedges) {
                List<Integer> remappedVertices = remapVertices(hyperedge.vertices, oldToNewVertexIds);
                remappedVertices.sort(Integer::compareTo);
                writer.write(joinVertices(remappedVertices) + " - " + hyperedge.label);
                writer.newLine();
            }
        }
    }

    private static List<Integer> remapVertices(List<Integer> vertices, Map<Integer, Integer> oldToNewVertexIds) {
        List<Integer> remappedVertices = new ArrayList<>(vertices.size());

        for (Integer vertex : vertices) {
            Integer remappedVertex = oldToNewVertexIds.get(vertex);
            if (remappedVertex == null) {
                throw new IllegalArgumentException("No remapped id for vertex " + vertex);
            }
            remappedVertices.add(remappedVertex);
        }

        return remappedVertices;
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

    private static boolean isHelp(String arg) {
        return "-h".equals(arg) || "--help".equals(arg);
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java LaSPM.utils.Dataset.HyperedgeLabelDatasetConverter <dataset-directory> [output-file]");
        System.err.println();
        System.err.println("The dataset directory must contain hyperedges.txt and hyperedge-labels.txt.");
    }
}
