package LaSPM.LFreSCo.search;

import LaSPM.structures.*;
import LaSPM.utils.MemoryLogger;
import LaSPM.utils.Pair;
import LaSPM.utils.Settings;
import LaSPM.utils.Utils;
import LaSPM.LFreSCo.search.nolabel.UMiner;
import com.google.common.collect.HashBasedTable;

import java.util.*;
import java.util.stream.Collectors;

public class Miner {
    int incrId ; // simplets generated
    private HashBasedTable<String, String, Set<Simplet>> examined;
    private List<Pair<String, Integer>> occMap;
    public int totalGen = 0;
    public int totalExamined = 0;

    public Miner() {
        this.incrId = 0;
        this.examined = HashBasedTable.create();
        this.occMap = Collections.synchronizedList(new ArrayList<>());
    }

    public List mine(Complex complex, int minFreq, int minSize, int maxSize, boolean limited, long timeout) throws InterruptedException {
        MemoryLogger memoryLogger = MemoryLogger.getInstance();
        memoryLogger.startMonitoring();
        try {
            UMiner unlabeledMiner = new UMiner();
            // The labelled phase needs the structural Simplet objects and their
            // decision images, even when the final output is limited to strings.
            List<Simplet> bases = unlabeledMiner.mine(complex, minFreq, minSize, maxSize, false, timeout);
            totalExamined += unlabeledMiner.totalExamine;
            totalGen += unlabeledMiner.totalGen;
            Set<Simplet> results = new LinkedHashSet<>();
            Set<Simplet> maximalFrequent = new LinkedHashSet<>();
            System.out.println(bases.size());
            for (Simplet base : bases) {
                List<Simplet> candidates = generateCandidates(complex, base);
                totalExamined += candidates.size();
                candidates.parallelStream().forEach(candidate -> {
                    MatchFinder matcher = new MatchFinder(complex, candidate, minFreq);
                    if (Settings.allMatches) {
                        matcher.examine();
                    } else {
                        matcher.examineSingle(base.getImages(), candidate.getNonCands(), candidate.getImages(), timeout);
                    }
                    candidate.computeFrequency("mni");
                });
                candidates.forEach(candidate -> {
                    if (candidate.getFreq() >= minFreq) {
                        maximalFrequent.add(candidate);
                    }
                });
            }
            results.addAll(maximalFrequent);
            Set<Simplet> frontier = maximalFrequent;
            while (!frontier.isEmpty()) {
                Set<Simplet> nextFrequent = new LinkedHashSet<>();
                for (Simplet parent : frontier) {
                    List<Simplet> faceCandidates = labelFaces(complex, parent);
                    totalExamined += faceCandidates.size();
                    faceCandidates.parallelStream().forEach(candidate -> {
                        MatchFinder matcher = new MatchFinder(complex, candidate, minFreq);
                        if (Settings.allMatches) {
                            matcher.examine();
                        } else {
                            matcher.examineSingle(parent.getImages(), candidate.getNonCands(), candidate.getImages(), timeout);
                        }
                        candidate.computeFrequency("mni");
                    });
                    faceCandidates.forEach(candidate -> {
                        if (candidate.getFreq() >= minFreq) {
                            nextFrequent.add(candidate);
                        }
                    });
                }
                if (nextFrequent.isEmpty()) {
                    break;
                }
                results.addAll(nextFrequent);
                frontier = nextFrequent;
            }
            List output = new ArrayList();
            results.forEach(simplet -> output.add(limited ? simplet.toString() : simplet));
            return output;
        } finally {
            memoryLogger.stopMonitoring();
        }
    }


    List<Simplet> generateCandidates(Complex complex, Simplet simplet) {
        List<Simplet> extensions = new ArrayList<>();
        List<Simplex> maximalSimplices = new ArrayList<>(simplet.getAllHDSimplices());
        if (maximalSimplices.isEmpty()) {
            return extensions;
        }

        // Compute the requested static priority without introducing a helper
        // type: smallest most shared vertices, then
        // largest simplex.  Simplex id is the deterministic final tie-breaker.
        Map<Integer, Integer> sharedVertexCount = new HashMap<>();
        for (Simplex maximal : maximalSimplices) {
            Set<Integer> shared = new HashSet<>();
            for (Simplex other : maximalSimplices) {
                if (other.getIndex() == maximal.getIndex()) {
                    continue;
                }
                Set<Integer> intersection = new HashSet<>(maximal.getVertexIndices());
                intersection.retainAll(other.getVertexIndices());
                shared.addAll(intersection);
            }
            sharedVertexCount.put(maximal.getIndex(), shared.size());
        }
        maximalSimplices.sort((left, right) -> {
            int compare = Integer.compare(
                    sharedVertexCount.get(right.getIndex()),
                    sharedVertexCount.get(left.getIndex()));
            if (compare != 0) {
                return compare;
            }
            compare = Integer.compare(
                    right.getNumVertices(), left.getNumVertices());
            return compare != 0
                    ? compare
                    : Integer.compare(left.getIndex(), right.getIndex());
        });

        // Parallel lists hold the iterative partial assignments.  No recursive
        // calls and no partially labelled Simplet objects are created.
        List<Map<Integer, Integer>> vertexAssignments = new ArrayList<>();
        List<Map<Integer, Integer>> simplexAssignments = new ArrayList<>();
        List<Map<Integer, Set<Integer>>> candidateAssignments = new ArrayList<>();
        vertexAssignments.add(new HashMap<>());
        simplexAssignments.add(new HashMap<>());
        candidateAssignments.add(new HashMap<>());

        for (Simplex maximal : maximalSimplices) {
            //No maximal simplex of the queried dimension exists in the complex, hence return empty list!
            if (complex.getSimplexKeysAtDim(maximal.getDimension()) == null)
                return new ArrayList<>();

            List<Map<Integer, Integer>> nextVertexAssignments = new ArrayList<>();
            List<Map<Integer, Integer>> nextSimplexAssignments = new ArrayList<>();
            List<Map<Integer, Set<Integer>>> nextCandidateAssignments = new ArrayList<>();
            List<Integer> patternVertices = maximal.getVertexIndices().stream().sorted().toList();

            for (int stateIndex = 0; stateIndex < vertexAssignments.size(); stateIndex++) {
                Map<Integer, Integer> currentVertexLabels = vertexAssignments.get(stateIndex);
                Map<Integer, Integer> currentSimplexLabels = simplexAssignments.get(stateIndex);
                Map<Integer, Set<Integer>> currentCandidates = candidateAssignments.get(stateIndex);
                for (String key : complex.getSimplexKeysAtDim(maximal.getDimension())) {
                    String[] vertex_simplex_key = key.trim().split(" - ");
                    String[] tokens = vertex_simplex_key[0].split("\\s+");
                    int simplexLabel = Integer.parseInt(vertex_simplex_key[1]);
                    int[] sortedLabels = Arrays.stream(tokens).mapToInt(Integer::parseInt).toArray();
                    SupMap supportMap = complex.getSupportMap(key);
                    if (supportMap == null) {
                        continue;
                    }
                    int[] permutation = Arrays.copyOf(sortedLabels, sortedLabels.length);
                    boolean more = true;
                    while (more) {
                        boolean compatible = true;
                        Map<Integer, Integer> nextVertexLabels = new HashMap<>(currentVertexLabels);
                        Map<Integer, Set<Integer>> nextCandidates = new HashMap<>();

                        currentCandidates.forEach((vertexId, images) ->
                                                    nextCandidates.put(vertexId, new HashSet<>(images)));

                        for (int position = 0; position < patternVertices.size(); position++) {
                            int vertexId = patternVertices.get(position);
                            int vertexLabel = permutation[position];
                            Integer previousLabel = nextVertexLabels.get(vertexId);
                            if (previousLabel != null && previousLabel != vertexLabel) {
                                compatible = false;
                                break;
                            }
                            Set<Integer> supportedImages = supportMap.getImgWithLabel(vertexLabel);
                            if (supportedImages == null) {
                                compatible = false;
                                break;
                            }
                            Set<Integer> boundedImages;
                            if (nextCandidates.containsKey(vertexId)) {
                                // Reuse the result of all previous intersections.
                                boundedImages = nextCandidates.get(vertexId);
                                boundedImages.retainAll(supportedImages);
                            } else {
                                // No previous candidate bound: initialize it from supportedImages.
                                boundedImages = new HashSet<>(supportedImages);
                                nextCandidates.put(vertexId, boundedImages);
                            }
                            if (boundedImages.size() < Settings.minFreq) {
                                compatible = false;
                                break;
                            }
                            nextVertexLabels.put(vertexId, vertexLabel);
                        }

                        if (compatible) {
                            Map<Integer, Integer> nextSimplexLabels = new HashMap<>(currentSimplexLabels);
                            nextSimplexLabels.put(maximal.getIndex(), simplexLabel);
                            nextVertexAssignments.add(nextVertexLabels);
                            nextSimplexAssignments.add(nextSimplexLabels);
                            nextCandidateAssignments.add(nextCandidates);
                        }

                        int pivot = permutation.length - 2;
                        while (pivot >= 0 && permutation[pivot] >= permutation[pivot + 1]) {
                            pivot--;
                        }
                        if (pivot < 0) {
                            more = false;
                        } else {
                            int successor = permutation.length - 1;
                            while (permutation[successor] <= permutation[pivot]) {
                                successor--;
                            }
                            int tmp = permutation[pivot];
                            permutation[pivot] = permutation[successor];
                            permutation[successor] = tmp;
                            for (int left = pivot + 1, right = permutation.length - 1; left < right; left++, right--) {
                                tmp = permutation[left];
                                permutation[left] = permutation[right];
                                permutation[right] = tmp;
                            }
                        }
                    }
                }
            }
            vertexAssignments = nextVertexAssignments;
            simplexAssignments = nextSimplexAssignments;
            candidateAssignments = nextCandidateAssignments;
            if (vertexAssignments.isEmpty()) {
                return extensions;
            }
        }

        // Only complete assignments are materialized.  Fresh Vertex instances
        // avoid changing labels after insertion into hash-based collections.
        for (int stateIndex = 0; stateIndex < vertexAssignments.size(); stateIndex++) {
            Map<Integer, Integer> assignedVertexLabels = vertexAssignments.get(stateIndex);
            if (assignedVertexLabels.size() != simplet.getNumVertices()) {
                continue;
            }
            Map<Integer, Integer> assignedSimplexLabels = simplexAssignments.get(stateIndex);
            Map<Integer, Vertex> rebuiltVertices = new HashMap<>();

            for (Vertex vertex : simplet.getVertices()) {
                int vertexId = vertex.getIndex();

                Integer assignedLabel = assignedVertexLabels.get(vertexId);
                if (assignedLabel == null) {
                    throw new IllegalStateException(
                            "No assigned label for pattern vertex " + vertexId);
                }

                rebuiltVertices.put(vertexId, new Vertex(vertexId, assignedLabel));
            }

            // This creates a complete copy of the simplet. Its simplices are deep-copied,
            // but its vertices initially still refer to the original vertices.
            Simplet labelled = new Simplet(incrId++, simplet, Settings.allMatches);

            // Replace the vertex set with the newly created labelled vertices.
            labelled.setVertices(new HashSet<>(rebuiltVertices.values()));

            // Replace the vertices inside every copied simplex, including faces.
            for (Simplex copiedSimplex : labelled.getAllSimplices()) {
                Set<Vertex> newSimplexVertices = copiedSimplex.getVertexIndices()
                        .stream()
                        .map(vertexId -> {
                            Vertex rebuiltVertex = rebuiltVertices.get(vertexId);
                            if (rebuiltVertex == null) {
                                throw new IllegalStateException("Missing rebuilt vertex " + vertexId + " for simplex "
                                                                                            + copiedSimplex.getIndex());
                            }
                            return rebuiltVertex;
                        })
                        .collect(Collectors.toSet());

                copiedSimplex.setVertices(newSimplexVertices);

                // Only maximal simplices receive labels at this stage.
                if (copiedSimplex.isMaximal()) {
                    int assignedLabel = assignedSimplexLabels.getOrDefault(copiedSimplex.getIndex(), copiedSimplex.getLabel());
                    copiedSimplex.setLabel(assignedLabel);
                }
            }

            labelled.updateLabelMap();
            labelled.setLabelMode(labelled.getAllSimplices().stream().noneMatch(simplex -> simplex.getLabel() == -1) ? 2 : 1);

            //Check if missing image for a vertex.
            Map<Integer, Set<Integer>> finalCandidates = candidateAssignments.get(stateIndex);
            Set<Integer> missingVertices = new HashSet<>(simplet.getVertexIndices());
            missingVertices.removeAll(finalCandidates.keySet());

            if (!missingVertices.isEmpty()) {
                throw new IllegalStateException("Missing candidate bounds for pattern vertices " + missingVertices);
            }

            labelled.setImages(candidateAssignments.get(stateIndex));
            totalGen++;
            if (!hasBeenExamined(labelled)) {
                extensions.add(labelled);
            }
        }
        return extensions;
    }


    List<Simplet> labelFaces(Complex complex, Simplet simplet) {
        List<Simplet> extensions = new ArrayList<>();
        Set<Simplex> maximalSimplices = simplet.getAllHDSimplices();
        if (maximalSimplices.isEmpty() || maximalSimplices.stream().anyMatch(simplex -> simplex.getLabel() == -1)) {
            return extensions;
        }

        Map<Integer, Set<Integer>> parentBounds = new HashMap<>();
        for (Vertex vertex : simplet.getVertices()) {
            Set<Integer> images = new HashSet<>(complex.getVertexWithLabel(vertex.getLabel()));
            if (images.size() < Settings.minFreq) {
                return extensions;
            }
            parentBounds.put(vertex.getIndex(), images);
        }

        for (Simplex labelledSimplex : simplet.getAllLabeledSimplices()) {
            SupMap supportMap = complex.getSupportMap(Utils.computeKeyMNI(labelledSimplex));
            if (supportMap == null) {
                return extensions;
            }
            for (Vertex vertex : labelledSimplex.getVertices()) {
                Set<Integer> supportedImages = supportMap.getImgWithLabel(vertex.getLabel());
                if (supportedImages == null) {
                    return extensions;
                }
                Set<Integer> boundedImages = parentBounds.get(vertex.getIndex());
                boundedImages.retainAll(supportedImages);
                if (boundedImages.size() < Settings.minFreq) {
                    return extensions;
                }
            }
        }

        List<Simplex> faces = simplet.getAllSimplices().stream().filter(face -> !face.isMaximal() && face.getLabel() == -1).collect(Collectors.toList());
        faces.sort((left, right) -> {
            int comparison = Integer.compare(right.getNumVertices(), left.getNumVertices());
            return comparison != 0 ? comparison : Integer.compare(left.getIndex(), right.getIndex());
        });

        for (Simplex face : faces) {
            String vertexKey = Utils.computeKeyMNI(face.getVertices());
            List<Integer> faceLabels = complex.getLabelsAtDim(face.getDimension()).stream().filter(label -> label >= 0).sorted().collect(Collectors.toList());
            for (int faceLabel : faceLabels) {
                SupMap supportMap = complex.getSupportMap(vertexKey + " - " + faceLabel);
                if (supportMap == null) {
                    continue;
                }

                Map<Integer, Set<Integer>> nextCandidates = new HashMap<>(parentBounds);
                boolean compatible = true;
                for (Vertex vertex : face.getVertices()) {
                    Set<Integer> supportedImages = supportMap.getImgWithLabel(vertex.getLabel());
                    if (supportedImages == null) {
                        compatible = false;
                        break;
                    }
                    Set<Integer> boundedImages = new HashSet<>(parentBounds.get(vertex.getIndex()));
                    boundedImages.retainAll(supportedImages);
                    if (boundedImages.size() < Settings.minFreq) {
                        compatible = false;
                        break;
                    }
                    nextCandidates.put(vertex.getIndex(), boundedImages);
                }
                if (!compatible) {
                    continue;
                }

                Simplet child = new Simplet(incrId++, simplet, Settings.allMatches);
                Simplex copiedFace = child.getAllSimplices().stream().filter(candidate -> candidate.getIndex() == face.getIndex()).findFirst().orElseThrow(() -> new IllegalStateException("Cannot find copied face " + face.getIndex()));
                copiedFace.setLabel(faceLabel);
                child.updateLabelMap();
                child.setLabelMode(child.getAllSimplices().stream().noneMatch(simplex -> simplex.getLabel() == -1) ? 2 : 1);
                child.setImages(nextCandidates);
                totalGen++;
                if (!hasBeenExamined(child)) {
                    extensions.add(child);
                }
            }
        }
        return extensions;
    }


    boolean hasBeenExamined(Simplet s) {
        Pair<Pair<String, String>, String> p = s.computeFingerPrint();
        String hashCodeA = p.getA().toString();
        String hashCodeB = p.getB();
        if (this.examined.contains(hashCodeB, hashCodeA)) {
            if (this.examined.get(hashCodeB, hashCodeA).stream().anyMatch((other) ->
                    s.getDimension() == other.getDimension()
                            && s.getNumSimplices() == other.getNumSimplices()
                            && s.getCanonicalForm(true).equals(other.getCanonicalForm(true)))) {
                return true;
            }
        } else {
            this.examined.put(hashCodeB, hashCodeA, new HashSet());
        }

        Set<Simplet> tmp = this.examined.get(hashCodeB, hashCodeA);
        tmp.add(s);
        this.examined.put(hashCodeB, hashCodeA, tmp);
        return false;
    }

    public List<Pair<String, Integer>> getOccMap() {
        return occMap;
    }
}
