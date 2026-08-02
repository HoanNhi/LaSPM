package LaSPM.utils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import LaSPM.structures.Simpl;
import LaSPM.structures.Simplex;
import LaSPM.structures.Vertex;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Utils {
    
    public static Collection<? extends Simpl> deepCopy(Collection<? extends Simpl> o) {
        if (o instanceof Set) {
            Set<Simpl> newSet = Sets.newHashSet();
            o.stream().forEach(e -> newSet.add(e.copy()));
            return newSet;
        } else if (o instanceof List) {
            List<Simpl> newList = Lists.newArrayList();
            o.stream().forEach(e -> newList.add(e.copy()));
            return newList;
        } else {
            throw new UnsupportedOperationException();
        }
    }
    
    public static Map<Integer, Set<Integer>> copyMap(Map<Integer, Set<Integer>> map) {
        Map<Integer, Set<Integer>> newMap = Maps.newHashMap();
        map.entrySet().stream().forEach(e -> newMap.put(e.getKey(), Sets.newHashSet(e.getValue())));
        return newMap;
    }

    public static Map<Integer, Set<List<Integer>>> getCombinationsBySize(List<Integer> vertices, int dim) {
        Map<Integer, Set<List<Integer>>> result = new HashMap<>();

        for (int r = 1; r <= dim; r++) {
            Set<List<Integer>> subsets = new HashSet<>();
            generateCombinations(vertices, new ArrayList<>(), subsets, 0, r);
            result.put(r, subsets);
        }
        return result;
    }


    private static void generateCombinations(
            List<Integer> numbers,
            List<Integer> current,
            Set<List<Integer>> collector,
            int start,
            int r) {

        if (current.size() == r) {
            collector.add(new ArrayList<>(current));
            return;
        }

        for (int i = start; i < numbers.size(); i++) {
            current.add(numbers.get(i));
            generateCombinations(numbers, current, collector, i + 1, r);
            current.remove(current.size() - 1); // backtrack
        }
    }


    private static void helper(List<int[]> combinations, int data[], int start, int end, int index) {
        if (index == data.length) {
            int[] combination = data.clone();
            combinations.add(combination);
        } else if (start <= end) {
            data[index] = start;
            helper(combinations, data, start + 1, end, index + 1);
            helper(combinations, data, start + 1, end, index);
        }
    }

    public static List<int[]> generate(int n, int r) {
        List<int[]> combinations = Lists.newArrayList();
        helper(combinations, new int[r], 0, n - 1, 0);
        return combinations;
    }
    /**
     * @param cands
     * @param image
     * Sort vertices in cands, o1 and o2, in order s.t: if image contains o1 then o2, return 0
     * If it only contains o1, returns -1
     * If it contains o2 only, returns 1
     * If it does not contain both, return the numerical order of o1 and o2
     * */
    public static void customSort(List<Integer> cands, Set<Integer> image) {
        if (image.isEmpty()) {
            return;
        }
        Collections.sort(cands, (Integer o1, Integer o2) -> {
            if (image.contains(o1)) {
                if (image.contains(o2)) {
                    return 0;
                }
                return -1;
            } else {
                if (image.contains(o2)) {
                    return 1;
                }
                return Integer.compare(o1, o2);
            }
        });
    }

    public static String computeKeyMNI(Set<Vertex> vertices){
        // Sort vertices by ID
        List<Vertex> sortedVertices = vertices.stream()
                .sorted(Comparator.comparingInt(Vertex::getLabel))
                .collect(Collectors.toList());

        // Build key: vertex labels
        StringBuilder key = new StringBuilder();
        for (Vertex v : sortedVertices) {
            int label = v.getLabel();
            key.append(label).append(" ");
        }
        return key.toString().trim();
    }

    public static String computeKeyMNI(Simplex s){
        List<Vertex> sortedVertices = s.getVertices().stream()
                .sorted(Comparator.comparingInt(Vertex::getLabel))
                .toList();

        StringJoiner labels = new StringJoiner(" ");

        for (Vertex v : sortedVertices) {
            int label = v.getLabel();
            labels.add(String.valueOf(label));
        }

        return labels + " - " + s.getLabel();
    }

    public static Map<String, List<Integer>> generateMultisetMap(
            Set<Vertex> vertices,
            int k) {

        // Group vertices by label
        Map<Integer, List<Integer>> labelBuckets = new HashMap<>();
        for (Vertex v : vertices) {
            labelBuckets
                    .computeIfAbsent(v.getLabel(), x -> new ArrayList<>())
                    .add(v.getIndex());
        }

        // Sort labels for canonical ordering
        List<Integer> labels = new ArrayList<>(labelBuckets.keySet());
        Collections.sort(labels);

        Map<String, List<Integer>> result = new HashMap<>();

        backtrack(labels, labelBuckets, 0, k,
                new ArrayList<>(),   // chosen vertex ids
                new ArrayList<>(),   // chosen labels (sorted automatically)
                result);

        return result;
    }

    /**
     * Generates every size-{@code k} subset of the supplied vertex multiset.
     * Vertices with the same label are still distinct when their indices differ.
     *
     * <p>A {@link Map} cannot contain the same key more than once. Therefore the
     * first subset having a particular label key uses the ordinary canonical key
     * (for example, {@code "1 2"}), and subsequent subsets use {@code "#2"},
     * {@code "#3"}, etc. (for example, {@code "1 2#2"}). The values contain the
     * actual vertex indices, so no vertex-level subset is discarded.</p>
     *
     * @param vertices vertices from which to choose
     * @param k number of vertices in each subset
     * @return all vertex-level subsets in deterministic order
     * @throws IllegalArgumentException if {@code k} is negative
     */
    public static Map<String, List<Integer>> generateAllSubsetMap(
            Set<Vertex> vertices,
            int k) {

        if (k < 0)
            throw new IllegalArgumentException("Subset size cannot be negative");

        List<Vertex> sortedVertices = vertices.stream()
                .sorted(Comparator.comparingInt(Vertex::getLabel)
                        .thenComparingInt(Vertex::getIndex))
                .collect(Collectors.toList());

        Map<String, List<Integer>> result = new LinkedHashMap<>();
        if (k > sortedVertices.size())
            return result;

        generateAllSubsets(sortedVertices, k, 0,
                new ArrayList<>(), new HashMap<>(), result);
        return result;
    }

    private static void generateAllSubsets(
            List<Vertex> vertices,
            int k,
            int start,
            List<Vertex> chosen,
            Map<String, Integer> keyOccurrences,
            Map<String, List<Integer>> result) {

        if (chosen.size() == k) {
            String labelKey = chosen.stream()
                    .map(vertex -> Integer.toString(vertex.getLabel()))
                    .collect(Collectors.joining(" "));
            int occurrence = keyOccurrences.merge(labelKey, 1, Integer::sum);
            String mapKey = occurrence == 1
                    ? labelKey
                    : labelKey + "#" + occurrence;
            List<Integer> vertexIndices = chosen.stream()
                    .map(Vertex::getIndex)
                    .collect(Collectors.toList());
            result.put(mapKey, vertexIndices);
            return;
        }

        int stillNeeded = k - chosen.size();
        for (int i = start; i <= vertices.size() - stillNeeded; i++) {
            chosen.add(vertices.get(i));
            generateAllSubsets(vertices, k, i + 1, chosen,
                    keyOccurrences, result);
            chosen.remove(chosen.size() - 1);
        }
    }

    private static void backtrack(
            List<Integer> labels,
            Map<Integer, List<Integer>> labelBuckets,
            int index,
            int remaining,
            List<Integer> chosenVertexIds,
            List<Integer> chosenLabels,
            Map<String, List<Integer>> result) {

        if (remaining == 0) {

            // Build canonical key: sorted labels
            String key = "";
            for (int label : chosenLabels)
                key += " " + label;
            key = key.strip();

            // Only keep one representative vertex set per label multiset
            result.putIfAbsent(key, new ArrayList<>(chosenVertexIds));
            return;
        }

        if (index == labels.size())
            return;

        int label = labels.get(index);
        List<Integer> bucket = labelBuckets.get(label);
        int maxAvailable = bucket.size();

        for (int use = 0; use <= Math.min(maxAvailable, remaining); use++) {

            // add vertices + labels
            for (int i = 0; i < use; i++) {
                chosenVertexIds.add(bucket.get(i));
                chosenLabels.add(label);
            }

            backtrack(labels, labelBuckets,
                    index + 1,
                    remaining - use,
                    chosenVertexIds,
                    chosenLabels,
                    result);

            // remove
            for (int i = 0; i < use; i++) {
                chosenVertexIds.remove(chosenVertexIds.size() - 1);
                chosenLabels.remove(chosenLabels.size() - 1);
            }
        }
    }

    
}
