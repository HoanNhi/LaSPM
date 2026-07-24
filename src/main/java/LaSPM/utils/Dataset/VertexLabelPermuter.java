package LaSPM.utils.Dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Creates a copy of a FreSCo dataset with its vertex labels permuted.
 *
 * The permutation changes which vertex receives each label, but preserves the
 * exact number of occurrences of every label. Simplex vertices and simplex
 * labels are copied unchanged.
 */
public final class VertexLabelPermuter {

    private VertexLabelPermuter() {
    }

    private static final class VertexLine {
        private final int lineIndex;
        private final String vertexId;
        private final String label;

        private VertexLine(int lineIndex, String vertexId, String label) {
            this.lineIndex = lineIndex;
            this.vertexId = vertexId;
            this.label = label;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3 || isHelp(args[0])) {
            printUsage();
            return;
        }

        Path inputPath = Paths.get(args[0]);
        long seed = parseSeed(args[1]);
        Path outputPath = args.length == 3
                ? Paths.get(args[2])
                : defaultOutputPath(inputPath, seed);

        permute(inputPath, outputPath, seed);
        System.out.println(
                "Wrote label-permuted dataset to " + outputPath.toAbsolutePath()
                        + " using seed " + seed);
    }

    public static void permute(Path inputPath, Path outputPath, long seed) throws IOException {
        Path normalizedInput = inputPath.toAbsolutePath().normalize();
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        if (normalizedInput.equals(normalizedOutput)) {
            throw new IllegalArgumentException("Input and output paths must be different");
        }

        List<String> lines = Files.readAllLines(inputPath);
        List<VertexLine> vertices = readVertexLines(lines);
        if (vertices.isEmpty()) {
            throw new IllegalArgumentException(
                    "No vertices found in the '# Vertex' section of " + inputPath);
        }

        List<String> shuffledLabels = new ArrayList<>(vertices.size());
        for (VertexLine vertex : vertices) {
            shuffledLabels.add(vertex.label);
        }
        Collections.shuffle(shuffledLabels, new Random(seed));

        List<String> outputLines = new ArrayList<>(lines);
        for (int i = 0; i < vertices.size(); i++) {
            VertexLine vertex = vertices.get(i);
            outputLines.set(
                    vertex.lineIndex,
                    "v " + vertex.vertexId + " " + shuffledLabels.get(i));
        }

        Path parent = normalizedOutput.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputPath, outputLines);
    }

    private static List<VertexLine> readVertexLines(List<String> lines) {
        List<VertexLine> vertices = new ArrayList<>();
        boolean inVertexSection = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.startsWith("#")) {
                inVertexSection = line.equalsIgnoreCase("# Vertex");
                continue;
            }
            if (!inVertexSection || line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+");
            if (parts.length != 3 || !"v".equals(parts[0])) {
                throw new IllegalArgumentException(
                        "Invalid vertex at line " + (i + 1) + ": " + lines.get(i));
            }

            parseInteger(parts[1], "vertex id", i + 1, lines.get(i));
            parseInteger(parts[2], "vertex label", i + 1, lines.get(i));
            vertices.add(new VertexLine(i, parts[1], parts[2]));
        }

        return vertices;
    }

    private static void parseInteger(
            String value,
            String fieldName,
            int lineNumber,
            String line
    ) {
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid " + fieldName + " at line " + lineNumber + ": " + line,
                    e);
        }
    }

    private static long parseSeed(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Seed must be a 64-bit integer: " + value, e);
        }
    }

    private static Path defaultOutputPath(Path inputPath, long seed) {
        Path absoluteInput = inputPath.toAbsolutePath();
        Path parent = absoluteInput.getParent();
        String fileName = absoluteInput.getFileName() == null
                ? "dataset"
                : absoluteInput.getFileName().toString();

        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        String extension = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";
        String outputName = baseName + "_shuffled_seed_" + seed + extension;
        return parent == null ? Paths.get(outputName) : parent.resolve(outputName);
    }

    private static boolean isHelp(String arg) {
        return "-h".equals(arg) || "--help".equals(arg);
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println(
                "  java LaSPM.utils.Dataset.VertexLabelPermuter "
                        + "<input-file> <seed> [output-file]");
        System.err.println();
        System.err.println(
                "Permutes existing vertex labels without changing their distribution.");
    }
}
