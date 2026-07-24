package LaSPM.structures;

import com.google.common.collect.Sets;
import LaSPM.utils.Settings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static LaSPM.utils.Utils.computeKeyMNI;

public class Complex {

    private List<Vertex> vertices;
    private Set<Integer> vertexIDs;
    private List<Simplex> simplices;
    private Map<Integer, Set<Integer>> labels; //Map the dimension to the set of unique labels at that dimension
    private Map<Integer, Set<Integer>> vertexMap; //Vertex degree; Map the vertex to the simplices it belongs to
    private Map<Integer, Set<Integer>> vNeighbours; //Map the neighbor vertices of a vertex
    private Map<Integer, Set<Integer>> sNeighbours; //Map the neighbor simplex of a simplex
    private Map<Integer, Set<Integer>> labelToVertex;
    private Map<Integer, int[]> vertexToDegreeSequence;
    private Map<String, SupMap> supportMap;
    private Set<String> simplexKeys;
    private Map<Integer, Set<String>> dimToSimplexKeys;
    private Set<String> simplexVertices;
    private int frequency;
    private int maxDim = 0;
    private int minDim = Integer.MAX_VALUE;
    private int prevId = -1;
    private int numSimplices;
    private int numVertices;
    private boolean[] nonFreqVertexLabel;
    private boolean LFreSCo;

    public Complex(String path, int maxSize, boolean isLFreSCo) throws IOException {
        init(path, maxSize, isLFreSCo);
        this.LFreSCo = isLFreSCo;
        if (!Settings.disable_sorting){
            this.supportMap = this.supportMap.entrySet().stream()
                            .sorted(
                                    Map.Entry.<String, SupMap>comparingByValue()
                                            .thenComparingInt(e -> e.getValue().getDim())
                            )
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (e1, e2) -> e1,
                                    LinkedHashMap::new
                            ));
        }
        System.out.println("Finished loading dataset");
        System.out.println("There are " + numVertices + " vertices and " + numSimplices + " simplices");
    }

    private void init(String path, int maxSize, boolean isLFreSCo) throws IOException {
        this.vertices = new ArrayList<>();
        this.simplices = new ArrayList<>();
        this.vertexMap = new HashMap<>();
        this.vNeighbours = new HashMap<>();
        this.supportMap = new HashMap<>();
        this.labels = new HashMap<>();
        this.labelToVertex = new HashMap<>();
        this.frequency = Settings.minFreq;
        this.simplexKeys = new HashSet<>();
        this.simplexVertices = new HashSet<>();
        this.sNeighbours = new HashMap<>();
        this.vertexToDegreeSequence = new HashMap<>();
        this.LFreSCo = isLFreSCo;
        if (isLFreSCo) {
            this.dimToSimplexKeys = new HashMap<>();
            this.vertexIDs = new HashSet<>();
        }
        readFromFile(path, maxSize, isLFreSCo);
    }

    /**
     * @param path Path to the dataset
     * <p> The program expect a dataset with following format: </p>
     * <p> + Each line represents a simplex</p>
     * <p> + The vertex must be sorted by ID </p>
     * <p> + The vertex and labels must be seperated by " - "</p>
     * <p> + The labels in each label group assume lexical order, meaning a simplex [0, 1, 2] comes before [0, 1, 3]</p>
     * <p> + The number of labels must match the number of simplices, otherwise the program will return RunTimeError</p>
     * <p> An example of a correct dataset can be found in the folder ./dataset/data</p>
     * */
    private void readFromFile(String path, int maxSize, boolean isLFreSCo) throws IOException  {
        final BufferedReader rows = new BufferedReader(new FileReader(path));
        String line;
        boolean isVertexSection = false;
        boolean isSimplexSection = false;

        while ((line = rows.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#")) { // Section headers
                if (line.contains("Vertex")) {
                    isVertexSection = true;
                    isSimplexSection = false;
                } else if (line.contains("Simplex")) {
                    isVertexSection = false;
                    isSimplexSection = true;
                    pruneVertex();
                }
            } else if (!line.isEmpty()) {
                if (isVertexSection) {
                    parseVertex(line);
                } else if (isSimplexSection) {
                    parseSimplex(line, maxSize);
                }
            }
        }
        rows.close();
        pruneSimplex();
        updateNeighborhood();
        if (isLFreSCo)
            buildDimToSimplexKey();
        else
            buildSimplexKey();
        this.numSimplices = this.simplices.size();
        this.numVertices = this.vertices.size();
    }

    private void buildSimplexKey() {
        this.simplexKeys = this.supportMap.keySet();
    }

    private void buildDimToSimplexKey() {
        for (String key: supportMap.keySet()){
            int dim = key.split(" - ")[0].split("\\s+").length;
            Set<String> simplexKeys = this.dimToSimplexKeys.computeIfAbsent(dim, k -> new HashSet<>());
            simplexKeys.add(key);
        }
    }

    public Set<String> getSimplexKeysAtDim(int dim){
        return dimToSimplexKeys.get(dim);
    }

    private void parseVertex(String line) {
        // Example: v 6 1
        String[] parts = line.split(" ");
        Set<Integer> labels = new HashSet<>();
        if (parts.length == 3 && parts[0].equals("v")) {
            int id = Integer.parseInt(parts[1]);
            if (id == prevId + 1)
                prevId++;
            else{
                System.out.println(line);
                System.out.println(line);
                System.exit(1);
            }
            int label = Integer.parseInt(parts[2]);
            vertices.add(new Vertex(id, label));
            labels.add(label);
            Set<Integer> vertexOfLabel = labelToVertex.getOrDefault(label, new HashSet<>());
            vertexOfLabel.add(id);
            labelToVertex.put(label, vertexOfLabel);
        }
        addLabels(0, labels);
    }

    private void parseSimplex(String line, int maxSize) {
        // Example: 6 7 8 - 0
        String[] parts = line.split(" - ");
        if (parts.length == 2) {
            String[] vertexIds = parts[0].trim().split(" ");
            //Skip those simplices whose size are greater than maxSize
            if (maxSize != -1 && vertexIds.length > maxSize)
                return;
            if (vertexIds.length > this.maxDim){
                this.maxDim = vertexIds.length;
            }
            // Query Vertex objects using their IDs
            Set<Vertex> simplexVertices = new HashSet<>();
            List<Integer> simplexVertexForComputingKey = new ArrayList<>();
            int prevId = -1;
            for (String idStr : vertexIds) {
                int id = Integer.parseInt(idStr.trim());
                if (id <= prevId){
		            System.out.println(line);
                    throw new IllegalArgumentException("Your simplex vertex IDs are not sorted ascendingly or have duplication!!!!!");
                }
                prevId = id;
                // Find the vertex by ID using getIndex()
                if (nonFreqVertexLabel[vertices.get(id).getLabel()]) {
                    return;
                }
                simplexVertices.add(vertices.get(id));
                simplexVertexForComputingKey.add(id);
            }
            // Parse the label
            int label = Integer.parseInt(parts[1].trim());
            String key = computeKeyVertex(simplexVertexForComputingKey) + " - " + label;
            this.simplexVertices.add(key);

            // Create and add the Simplex object
            simplices.add(new Simplex(simplices.size(), simplexVertices, label, true));
            addLabels(simplexVertices.size(), Sets.newHashSet(label));
            if (simplexVertices.size() > this.maxDim){
                this.maxDim = simplexVertices.size();
            }
        }
    }

    private void addLabels(int dim, Set<Integer> new_labels){
            Set<Integer> labels = this.labels.getOrDefault(dim, Sets.newHashSet());
            labels.addAll(new_labels);
            this.labels.put(dim, labels);
    }

    public Set<Integer> getLabelsAtDim(int dim){
        return labels.getOrDefault(dim, Sets.newHashSet());
    }

    public Set<Integer> getVertexWithLabel(int label){
        return labelToVertex.get(label);
    }

    public Map<Integer, Set<Integer>> getSimplexNeighbours(){
        return this.sNeighbours;
    }

    public Set<Integer> getSimplexNeighbour(int sID){
        return this.sNeighbours.get(sID);
    }

    public int[] getDegreeSequenceOf(int vID){
        return this.vertexToDegreeSequence.get(vID);
    }

    private void updateNeighborhood() {
        Map<Integer, Set<Integer>> labels = new HashMap<>();
        int maxDim = this.maxDim;
        this.maxDim = 0;
        labels.put(0, this.labels.get(0));
        for (Simplex s : this.simplices) {
            Set<Integer> labelAtDim = labels.getOrDefault(s.getVertexIndices().size(), new HashSet<>());
            labelAtDim.add(s.getLabel());
            labels.put(s.getVertexIndices().size(), labelAtDim);
            for (int v : s.getVertexIndices()) {
                if (!this.vertexToDegreeSequence.containsKey(v))
                    this.vertexToDegreeSequence.put(v, new int[maxDim+1]);
                Set<Integer> memb = this.vertexMap.getOrDefault(v, new HashSet<>());
                memb.add(s.getIndex());
                this.vertexToDegreeSequence.get(v)[s.getDimension()]++;
                this.vertexMap.put(v, memb);
            }

            if (s.getVertexIndices().size() > 1){
                s.getVertexIndices().forEach(
                        v -> {
                            Set<Integer> tmp2 = this.vNeighbours.getOrDefault(v, new HashSet<>());
                            tmp2.addAll(s.getVertexIndices());
                            tmp2.remove(v);
                            this.vNeighbours.put(v, tmp2);
                            if (this.LFreSCo)
                                this.vertexIDs.add(v);
                        }
                );
            }

            if (s.getVertexIndices().size() > this.maxDim)
                this.maxDim = s.getVertexIndices().size();

            if (s.getVertexIndices().size() < this.minDim)
                this.minDim = s.getVertexIndices().size();

        }
        this.labels = labels;
    }



    public List<Vertex> getVertices() {
        return vertices;
    }

    public void pruneVertex(){
        this.nonFreqVertexLabel = new boolean[labelToVertex.keySet().stream().max(Comparator.naturalOrder()).get() + 1];
        Iterator<Map.Entry<Integer, Set<Integer>>> it = labelToVertex.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Set<Integer>> entry = it.next();
            Set<Integer> vertexWithLabel = entry.getValue();
            int curLabel = entry.getKey();
            if (vertexWithLabel.size() < this.frequency) {
                this.labels.get(0).remove(curLabel);
                nonFreqVertexLabel[curLabel] = true;
                it.remove();
            }
        }
    }


    public void pruneSimplex() {
        computeSupportMap();
        Set<String> keyToDelete = new HashSet<>();
        Map<Integer, Set<Integer>> newLabelMap = new HashMap<>();
        for (Map.Entry<String, SupMap> instances : this.supportMap.entrySet()){
            SupMap supMap = instances.getValue();
            String key = instances.getKey();

            if (supMap!=null){
                int MNIFreq = supMap.getLabelToVertexIndex().values().stream()
                        .min(Comparator.comparingInt(Set::size))
                        .map(Set::size)
                        .orElse(0);
                if (MNIFreq < this.frequency){
                    supMap = null;
                    keyToDelete.add(key);
                    continue;
                }
                supMap.setFrequency(MNIFreq);
            }
            else{
                keyToDelete.add(key);
                continue;
            }
        }
        for (String key : keyToDelete) {
            this.supportMap.remove(key);
        }
        // Change the ids of simplices after pruning
        List<Simplex> newSimplices = new ArrayList<>();
        for (String supMap : this.supportMap.keySet()) {
            SupMap supportMap = this.supportMap.get(supMap);
            if (supportMap == null)
                continue;
            Set<Integer> simplexID = new HashSet<>();
            for (int s : supportMap.getSimplexOfKey()){
                int id = newSimplices.size();
                Simplex simplex = this.simplices.get(s);
                simplex.setId(id);
                newSimplices.add(this.simplices.get(s));
                simplexID.add(id);
                simplex.getVertexIndices().forEach(v -> {
                    supportMap.updateVertexToNeighbours(v, simplex.getVertexIndices());
                });
            }
            supportMap.setSimplexOfKey(new ArrayList(simplexID));
            Set<Integer> freqLabels = newLabelMap.getOrDefault(supportMap.getDim(), new HashSet<>());
            freqLabels.add(Integer.parseInt(supMap.split(" - ")[1]));
            newLabelMap.put(supportMap.getDim(), freqLabels);
        }

        System.out.println("Old maximum dimension is " + this.maxDim);
        this.maxDim = 0;
        for (String key : supportMap.keySet()){
            String keyVertex = key.trim().split(" - ")[0];
            int dimension = keyVertex.trim().split("\\s+").length;
            if (dimension > this.maxDim)
                this.maxDim = dimension;
        }
        System.out.println("After pruning, only dimension " + this.maxDim + " containing the simplices exceeding the " +
                "frequency threshold!");
        System.out.printf("Removing %d non-frequent simplices!\n", this.simplices.size() - newSimplices.size());
        newLabelMap.put(0, labels.get(0));
        this.simplices = newSimplices;
        this.labels = newLabelMap;
    }


    public static String computeKeyVertex(List<Integer> vertices){
        StringBuilder key = new StringBuilder();
        vertices = vertices.stream().sorted().collect(Collectors.toList());
        for (Integer v : vertices) {
            key.append(v).append(" ");
        }
        return key.toString().trim();
    }

    public void computeSupportMap(){

        for (Simplex s : simplices) {
            String typeKey = s.getKey();
            if (typeKey == null)
                continue;
            SupMap supMap = this.supportMap.get(typeKey);
            if (supMap == null) {
                supMap = new SupMap(s.getNumVertices());;
            }
            Map<Integer, Set<Integer>> labelToVertex = supMap.getLabelToVertexIndex();
            List<Integer> simplexOfKey = supMap.getSimplexOfKey();
            simplexOfKey.add(s.getIndex());
            for (Vertex v : s.getVertices()) {
                int label = v.getLabel();
                Set<Integer> img = labelToVertex.getOrDefault(label, new HashSet<>());
                img.add(v.getIndex());
                labelToVertex.put(label, img);
            }
            this.supportMap.put(typeKey, supMap);
        }

    }

    public SupMap getSupportMap(String key) {
        SupMap supportMap = this.supportMap.get(key);
        return supportMap == null ? null : supportMap;
    }

    public Map<String, SupMap> getSupportMaps() {
        return this.supportMap;
    }

    public Set<Integer> getNeighborsOf(int v) {
        return vNeighbours.getOrDefault(v, new HashSet<>());
    }


    public boolean contains(Set<Integer> simplex, int label){
        if (label == -1){
            Set<Integer> test = getSimplices(new ArrayList<>(simplex));
            if (test == null)
                return false;
            else{
                Iterator<Integer> iter = test.iterator();
                while(iter.hasNext()){
                    int id = iter.next();
                    Simplex s = simplices.get(id);
                    if (s.getVertexIndices().equals(simplex))
                        iter.remove();
                }

                return !test.isEmpty();
            }
        }
        Simplex result = this.simplices.parallelStream().filter(s -> s.getVertexIndices().equals(simplex)).findFirst().orElse(null);
        if (result != null){
            return result.getLabel() == label;
        }
        return false;
    }

    public boolean contains(List<Integer> simplex, int simplexLabel) {
        Set<Integer> memb = Sets.newHashSet(vertexMap.getOrDefault(simplex.get(0), Collections.emptySet()));
        for (int i = 0; i < simplex.size(); i++) {
            memb.retainAll(vertexMap.getOrDefault(simplex.get(i), Collections.emptySet()));
            if (memb.isEmpty()) {
                return false;
            }
        }
        if (memb.isEmpty()) {
            return false;
        }

        return memb.stream().anyMatch(s -> this.simplices.get(s).getLabel() == simplexLabel);
    }

    public boolean contains(List<Integer> simplex) {
        Set<Integer> memb = Sets.newHashSet(vertexMap.getOrDefault(simplex.get(0), Collections.emptySet()));
        for (int i = 0; i < simplex.size(); i++) {
            memb.retainAll(vertexMap.getOrDefault(simplex.get(i), Collections.emptySet()));
            if (memb.isEmpty()) {
                return false;
            }
        }
        return !memb.isEmpty();
    }

    public boolean containsExactSimplex(Collection<Integer> simplex, int simplexLabel) {
        if (simplex == null || simplex.isEmpty()) {
            return false;
        }
        Set<Integer> mappedVertices = new HashSet<>(simplex);
        if (mappedVertices.size() != simplex.size()) {
            return false;
        }
        Set<Integer> containing = getSimplices(new ArrayList<>(mappedVertices));
        if (containing == null || containing.isEmpty()) {
            return false;
        }
        if (simplexLabel == -1) {
            return true;
        }
        return containing.stream()
                .map(this.simplices::get)
                .anyMatch(candidate ->
                        candidate.getLabel() == simplexLabel
                                && candidate.getVertexIndices().equals(mappedVertices));
    }


    public boolean canExtendToSimplex(Simplex patternSimplex, Map<Integer, Integer> mapping) {
        for (Vertex patternVertex : patternSimplex.getVertices()) {
            Integer image = mapping.get(patternVertex.getIndex());
            if (image != null && (image < 0 || image >= vertices.size() || vertices.get(image).getLabel() != patternVertex.getLabel())) {
                return false;
            }
        }
        Set<Integer> assignedImages = patternSimplex.getVertexIndices().stream()
                .filter(mapping::containsKey)
                .map(mapping::get)
                .collect(Collectors.toSet());
        long assignedCount = patternSimplex.getVertexIndices().stream().filter(mapping::containsKey).count();

        if (assignedImages.size() != assignedCount || assignedImages.isEmpty()) {
            return false;
        }
        if (assignedImages.size() == patternSimplex.getNumVertices()) {
            return containsExactSimplex(assignedImages, patternSimplex.getLabel());
        }

        Set<Integer> occupiedImages = new HashSet<>(mapping.values());
        if (occupiedImages.size() != mapping.size()) {
            return false;
        }

        if (patternSimplex.getLabel() != -1) {
            SupMap support = getSupportMap(computeKeyMNI(patternSimplex));
            if (support == null) {
                return false;
            }
            return support.getSimplexOfKey().stream()
                    .map(this.simplices::get)
                    .anyMatch(candidate ->
                            candidate.getLabel() == patternSimplex.getLabel()
                                    && candidate.getNumVertices() == patternSimplex.getNumVertices()
                                    && candidate.getVertexIndices().containsAll(assignedImages)
                                    && hasNoOccupiedCompletionVertex(candidate, assignedImages, occupiedImages));
        }

        Set<Integer> containing = getSimplices(new ArrayList<>(assignedImages));
        if (containing == null || containing.isEmpty()) {
            return false;
        }
        Map<Integer, Long> requiredLabels = patternSimplex.getVertices().stream()
                .filter(vertex -> !mapping.containsKey(vertex.getIndex()))
                .collect(Collectors.groupingBy(Vertex::getLabel, Collectors.counting()));
        return containing.stream()
                .map(this.simplices::get)
                .filter(candidate -> candidate.getNumVertices() >= patternSimplex.getNumVertices())
                .anyMatch(candidate -> hasAvailableVertexLabels(candidate, occupiedImages, requiredLabels));
    }
    /**
     * Provide a check if in the future, a complete match will require any
     * image vertex that already present in the mapping.
     * **/
    private boolean hasNoOccupiedCompletionVertex(
            Simplex candidate,
            Set<Integer> assignedImages,
            Set<Integer> occupiedImages) {
        for (int vertex : candidate.getVertexIndices()) {
            if (!assignedImages.contains(vertex) && occupiedImages.contains(vertex)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAvailableVertexLabels(
            Simplex candidate,
            Set<Integer> occupiedImages,
            Map<Integer, Long> requiredLabels) {
        Map<Integer, Long> availableLabels = candidate.getVertices().stream()
                .filter(vertex -> !occupiedImages.contains(vertex.getIndex()))
                .collect(Collectors.groupingBy(
                        Vertex::getLabel,
                        Collectors.counting()));
        return requiredLabels.entrySet().stream()
                .allMatch(entry ->
                        availableLabels.getOrDefault(entry.getKey(), 0L)
                                >= entry.getValue());
    }

    public List<Simplex> getSimplices(){
        return this.simplices;
    }

    public Set<Integer> getSimplices(List<Integer> simplex) {
        if (simplex == null || simplex.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> memb = Sets.newHashSet(vertexMap.getOrDefault(simplex.get(0), Collections.emptySet()));
        for (int i = 1; i < simplex.size(); i++) {
            memb.retainAll(vertexMap.getOrDefault(simplex.get(i), Collections.emptySet()));
            if (memb.isEmpty()) {
                return null;
            }
        }
        return memb;
    }


    public Vertex getVertex(int vID){
        return this.vertices.get(vID);
    }

    public Map<Integer, Set<Integer>> getLabels(){
        return this.labels;
    }

    public int getNumVertices(){
        return this.numVertices;
    }
    public boolean contains(String key){
        return this.simplexVertices.contains(key);
    }

    public List<Simplex> getSimplex() {
        return simplices;
    }

    public Simplex getSimplex(int sID){
        return this.simplices.get(sID);
    }

    public int getMaxDim(){
        return this.maxDim;
    }

    public Set<Integer> getVertexIDs() {
        return this.vertexIDs;
    }

    private DatasetStatistics computeDatasetStatistics(String datasetName) {
        int maximumDimension = simplices.isEmpty() ? 0 : maxDim - 1;
        int minimumDimension = simplices.isEmpty() ? 0 : minDim - 1;
        double averageDimension = simplices.stream()
                .mapToInt(s -> s.getDimension() - 1)
                .average()
                .orElse(0.0);
        int dimension95Cutoff = computeDimension95Cutoff();
        long simplicesWithThreeVertices = simplices.stream()
                .filter(s -> s.getVertexIndices().size() == 3)
                .count();

        IntSummaryStatistics nodeDegreeStats = vertices.stream()
                .mapToInt(v -> vNeighbours.getOrDefault(v.getIndex(), new HashSet<>()).size())
                .summaryStatistics();
        IntSummaryStatistics simplexDegreeStats = simplices.stream()
                .mapToInt(s -> sNeighbours.getOrDefault(s.getIndex(), Collections.emptySet()).size())
                .summaryStatistics();

        Set<Integer> simplexLabels = new HashSet<>();
        for (int k = 1; k <= maxDim; k++) {
            simplexLabels.addAll(getLabelsOrEmpty(k));
        }

        return new DatasetStatistics(
                datasetName,
                simplices.size(),
                vertices.size(),
                maximumDimension,
                minimumDimension,
                averageDimension,
                dimension95Cutoff,
                simplicesWithThreeVertices,
                getLabelsOrEmpty(0).size(),
                simplexLabels.size(),
                nodeDegreeStats.getCount() == 0 ? 0 : nodeDegreeStats.getMax(),
                nodeDegreeStats.getCount() == 0 ? 0 : nodeDegreeStats.getMin(),
                nodeDegreeStats.getAverage(),
                simplexDegreeStats.getCount() == 0 ? 0 : simplexDegreeStats.getMax(),
                simplexDegreeStats.getCount() == 0 ? 0 : simplexDegreeStats.getMin(),
                simplexDegreeStats.getAverage()
        );
    }

    private int computeDimension95Cutoff() {
        if (simplices.isEmpty()) {
            return 0;
        }

        Map<Integer, Long> dimensionCounts = simplices.stream()
                .collect(Collectors.groupingBy(s -> s.getDimension() - 1, TreeMap::new, Collectors.counting()));
        long cutoffCount = (long) Math.ceil(simplices.size() * 0.95);
        long cumulativeCount = 0;
        for (Map.Entry<Integer, Long> entry : dimensionCounts.entrySet()) {
            cumulativeCount += entry.getValue();
            if (cumulativeCount >= cutoffCount) {
                return entry.getKey();
            }
        }
        return maxDim - 1;
    }

    private Set<Integer> getLabelsOrEmpty(int dim) {
        Set<Integer> labelsAtDim = labels.get(dim);
        return labelsAtDim == null ? Collections.emptySet() : labelsAtDim;
    }

    private static void writeStatisticsCsv(List<DatasetStatistics> statistics, String outputPath) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(DatasetStatistics.csvHeader());
            writer.write("\n");
            for (DatasetStatistics statistic : statistics) {
                writer.write(statistic.toCsvRow());
                writer.write("\n");
            }
        }
    }

    private static String csvValue(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static boolean hasDatasetSections(File file) {
        boolean hasVertexSection = false;
        boolean hasSimplexSection = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#")) {
                    hasVertexSection = hasVertexSection || line.contains("Vertex");
                    hasSimplexSection = hasSimplexSection || line.contains("Simplex");
                    if (hasVertexSection && hasSimplexSection) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Skipping " + file.getName() + ": " + e.getMessage());
        }
        return false;
    }

    private static class DatasetStatistics {
        private final String dataset;
        private final int totalMaximalSimplices;
        private final int totalVertices;
        private final int maximumDimension;
        private final int minimumDimension;
        private final double averageDimension;
        private final int dimension95Cutoff;
        private final long simplicesWithThreeVertices;
        private final int nodeLabels;
        private final int simplexLabels;
        private final int maximumNodeDegree;
        private final int minimumNodeDegree;
        private final double averageNodeDegree;
        private final int maximumSimplexDegree;
        private final int minimumSimplexDegree;
        private final double averageSimplexDegree;

        private DatasetStatistics(String dataset,
                                  int totalMaximalSimplices,
                                  int totalVertices,
                                  int maximumDimension,
                                  int minimumDimension,
                                  double averageDimension,
                                  int dimension95Cutoff,
                                  long simplicesWithThreeVertices,
                                  int nodeLabels,
                                  int simplexLabels,
                                  int maximumNodeDegree,
                                  int minimumNodeDegree,
                                  double averageNodeDegree,
                                  int maximumSimplexDegree,
                                  int minimumSimplexDegree,
                                  double averageSimplexDegree) {
            this.dataset = dataset;
            this.totalMaximalSimplices = totalMaximalSimplices;
            this.totalVertices = totalVertices;
            this.maximumDimension = maximumDimension;
            this.minimumDimension = minimumDimension;
            this.averageDimension = averageDimension;
            this.dimension95Cutoff = dimension95Cutoff;
            this.simplicesWithThreeVertices = simplicesWithThreeVertices;
            this.nodeLabels = nodeLabels;
            this.simplexLabels = simplexLabels;
            this.maximumNodeDegree = maximumNodeDegree;
            this.minimumNodeDegree = minimumNodeDegree;
            this.averageNodeDegree = averageNodeDegree;
            this.maximumSimplexDegree = maximumSimplexDegree;
            this.minimumSimplexDegree = minimumSimplexDegree;
            this.averageSimplexDegree = averageSimplexDegree;
        }

        private static String csvHeader() {
            return "Dataset,Total number of maximal simplices,Total number of vertices,Maximum dimension,"
                    + "Minimum dimension,Average dimension,95% simplex dimension cutoff,"
                    + "Number of simplices with 3 vertices,Number of node labels,Number of simplex labels,"
                    + "Maximum node degree,Minimum node degree,Average node degree,Maximum simplex degree,"
                    + "Minimum simplex degree,Average simplex degree";
        }

        private String toCsvRow() {
            return String.join(",",
                    csvValue(dataset),
                    String.valueOf(totalMaximalSimplices),
                    String.valueOf(totalVertices),
                    String.valueOf(maximumDimension),
                    String.valueOf(minimumDimension),
                    String.format(Locale.US, "%.6f", averageDimension),
                    String.valueOf(dimension95Cutoff),
                    String.valueOf(simplicesWithThreeVertices),
                    String.valueOf(nodeLabels),
                    String.valueOf(simplexLabels),
                    String.valueOf(maximumNodeDegree),
                    String.valueOf(minimumNodeDegree),
                    String.format(Locale.US, "%.6f", averageNodeDegree),
                    String.valueOf(maximumSimplexDegree),
                    String.valueOf(minimumSimplexDegree),
                    String.format(Locale.US, "%.6f", averageSimplexDegree)
            );
        }
    }

    public static void main(String[] args) throws IOException {
        String folderName = args.length > 0 ? args[0] : Settings.dataFolder;
        File input = new File(folderName);
        String outputPath = args.length > 1
                ? args[1]
                : (input.isFile() ? input.getParent() : folderName) + File.separator + Settings.dataFile + "_dataset_statistics.csv";
        File folder = new File(folderName);
        File[] files = input.isFile() ? new File[]{input} : folder.listFiles(File::isFile);

        if (files == null) {
            throw new IOException("Cannot list files in folder: " + folderName);
        }

        Arrays.sort(files, Comparator.comparing(File::getName));

        List<DatasetStatistics> statistics = new ArrayList<>();
        for (File file : files) {
            if (file.getPath().equals(outputPath) || !hasDatasetSections(file)) {
                continue;
            }
            try {
                System.out.println("Computing statistics for " + file.getName());
                Complex complex = new Complex(file.getPath(), Settings.maxSize, false);
                statistics.add(complex.computeDatasetStatistics(file.getName()));
            } catch (RuntimeException | IOException e) {
                System.err.println("Skipping " + file.getName() + ": " + e.getMessage());
            } catch (OutOfMemoryError e) {
                System.err.println("Skipping " + file.getName() + ": Java heap space");
                System.gc();
            }
        }

        writeStatisticsCsv(statistics, outputPath);
        System.out.println("Wrote dataset statistics to " + outputPath);
    }
}
