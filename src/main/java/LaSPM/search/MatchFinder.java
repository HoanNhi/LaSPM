package LaSPM.search;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import LaSPM.structures.*;
import LaSPM.utils.Pair;
import LaSPM.utils.Settings;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MatchFinder {

    Complex complex;
    LSimplet simplet;
    int minFreq;
    Map<Integer, Map<Integer, Set<Integer>>> localNeighborhood;
    Map<Integer, Set<SupMap>> incidentSupportMaps;

    public MatchFinder(Complex complex, LSimplet simplet, int minFreq) {
        this.complex = complex;
        this.simplet = simplet;
        this.minFreq = minFreq;
        this.localNeighborhood = new ConcurrentHashMap<>();
        this.incidentSupportMaps = new HashMap<>();
    }

    // Find all the occurrences of a pattern

    public void mine(){
        Map<Integer, Set<Integer>> images = new HashMap<>();
        Map<Integer, Set<Integer>> candidateImages = new HashMap<>();
        simplet.getImages().forEach((vertex, vertexImages) ->
                candidateImages.put(vertex, new HashSet<>(vertexImages)));
        if (!Settings.disable_dimensionAware){
            for (int i=0; i < candidateImages.size(); i++){
                int[] degSeqOfPattern = this.simplet.getDegreeSequenceOf(i);
                int maxDim = simplet.getMaxDim();
                Set<Integer> newImage = candidateImages.get(i).parallelStream().filter(v -> {
                    int[] deqSeqOfComplex = this.complex.getDegreeSequenceOf(v);
                    for (int j = 0; j <= maxDim; j++){
                        if (degSeqOfPattern[j] > deqSeqOfComplex[j])
                            return false;
                    }
                    return true;
                }).collect(Collectors.toSet());
                if (newImage.size() < minFreq){
                    simplet.emptyMap(true);
                    return;
                }
                candidateImages.put(i, newImage);
            }
        }
        if (!Settings.disable_localNeighborhood)
            buildIncidentSupportMaps();
        for (int v : simplet.getVertexIndices()) {
            int patternDegree = simplet.getNeighborsOf(v).size();
            Set<Integer> imgOfV = candidateImages
                    .getOrDefault(v, Collections.emptySet())
                    .parallelStream()
                    .filter(n -> neighborsOf(v, n).size() >= patternDegree)
                    .collect(Collectors.toSet());

            if (imgOfV.size() < minFreq) {
                simplet.getImages().clear();
                return;
            }
            candidateImages.put(v, imgOfV);
        }
        intersectOrbitCandidateImages(candidateImages);
        for (int v : simplet.getVertexIndices()){
            if (candidateImages.get(v).size() < minFreq){
                simplet.getImages().clear();
                return;
            }
        }
        simplet.setImages(candidateImages);

        // order the vertices according to size of image sets
        // Mining the smallest-sized vertex, if frequency is less than tau, return!
        List<Integer> ordered_vertices = Lists.newArrayList(simplet.getVertexIndices());
        Collections.sort(ordered_vertices, (Integer e1, Integer e2)
                -> Integer.compare(simplet.getImageOf(e1).size(), simplet.getImageOf(e2).size()));
        // initial set of valid matches
        for (int v : ordered_vertices) {
            if (simplet.getImageOf(v).size() < minFreq) {
                simplet.getImages().clear();
                return;
            }
            Set<Integer> partialImageSet = new HashSet<>(images.getOrDefault(v, Sets.newHashSet()));
            Set<Integer> candidates = Sets.newHashSet(simplet.getImageOf(v));
            candidates.removeAll(partialImageSet);
            int c = 0;
            // sort vertices using a dfs
            List<Integer> vertex_ordering = Lists.newArrayList();
//            vertex_ordering.add(v);
            simplet.dfs(vertex_ordering, v);
            for (Integer n : candidates) {
                c += 1;
                if (partialImageSet.contains(n))
                    continue;

                Map<Integer, Integer> M = Maps.newHashMap();
                if (!satisfiesConstraints(M, v, n)) {
                    continue;
                }
                M.put(v, n);
                Map<Integer, Integer> match = findMatch(M, vertex_ordering, 0);


                if (match.size() == simplet.getNumVertices()){
                    updateAndPropagateImageSets(images, match);
                    partialImageSet.add(n);
                }
                // early stop if the simplet cannot be frequent
                if (candidates.size() - c + partialImageSet.size() < minFreq) {
                    simplet.getImages().clear();
                    return;
                }

            }
            Set<Integer> imageV = images.getOrDefault(v, Sets.newHashSet());
            imageV.addAll(partialImageSet);
            images.put(v, imageV);
        }
        simplet.setImages(images);
    }

    private Map<Integer, Integer> findMatch(
            Map<Integer, Integer> M,
            List<Integer> vertexOrder,
            int vertexID) {

        if (M.size() == vertexOrder.size()) {
            return M;
        }

        while (M.containsKey(vertexOrder.get(vertexID))) {
            vertexID++;
        }
        if (vertexID >= vertexOrder.size()) {
            return M;
        }

        int w = vertexOrder.get(vertexID);
        Set<Integer> ngbs = simplet.getNeighborsOf(w);
        List<Set<Integer>> candidateSets = new ArrayList<>();

        for (int ngb : ngbs) {
            if (M.containsKey(ngb)) {
                Set<Integer> localCandidates = neighborsOf(ngb, M.get(ngb));
                if (localCandidates.isEmpty()) {
                    return M;
                }
                candidateSets.add(localCandidates);
            }
        }

        candidateSets.add(simplet.getImageOf(w));
        candidateSets.sort(Comparator.comparingInt(Set::size));

        Set<Integer> candidates = new HashSet<>(candidateSets.get(0));
        for (int i = 1; i < candidateSets.size(); i++) {
            candidates.retainAll(candidateSets.get(i));
            if (candidates.isEmpty()) {
                return M;
            }
        }

        for (int n : candidates) {
            if (!satisfiesConstraints(M, w, n))
                continue;

            Map<Integer, Integer> newM = Maps.newHashMap(M);
            newM.put(w, n);
            Map<Integer, Integer> updM = findMatch(newM, vertexOrder, vertexID + 1);
            if (updM.size() == vertexOrder.size()) {
                return updM;
            }
        }
        return M;
    }

    private boolean satisfiesConstraints(Map<Integer, Integer> M, int w, int n) {
        Set<Integer> patternNeighbors = simplet.getNeighborsOf(w);
        Set<Integer> complexNeighbors = neighborsOf(w, n);
        if (complexNeighbors.size() < patternNeighbors.size()) {
            return false;
        }
        if (complex.getVertex(n).getLabel() == simplet.getVertex(w).getLabel() &&
                localNeighborConstraintsHold(M, w, n)){
            for (int simplexID : simplet.getIncidentSimplex(w)) {
                Simplex simplex = simplet.getSimplex(simplexID);
                List<Integer> image = new ArrayList<>();
                for (int vertex : simplex.getVertexIndices()) {
                    if (vertex == w) {
                        image.add(n);
                    } else if (M.containsKey(vertex)) {
                        image.add(M.get(vertex));
                    }
                }
                boolean simplexExists;
                // Partial assignments use co-containment; completed simplices require an exact labeled match.
                if (image.size() == simplex.getNumVertices()) {
                    String key = Complex.computeKeyVertex(image) + " - " + simplex.getLabel();
                    simplexExists = complex.contains(key);
                } else {
                    simplexExists = complex.contains(image, simplex.getLabel());
                }
                if (!simplexExists) {
                    return false;
                }
            }
            return true;
        }
        else{
            return false;
        }
    }

    private boolean localNeighborConstraintsHold(Map<Integer, Integer> M, int w, int n) {
        for (Map.Entry<Integer, Integer> e : M.entrySet()) {
            if (e.getValue() == n) {
                return false;
            }
            if (simplet.areNeighbors(e.getKey(), w)
                    && !neighborsOf(e.getKey(), e.getValue()).contains(n)) {
                return false;
            }
        }
        return true;
    }
    private Set<Integer> neighborsOf(int patternVertex, int complexVertex) {
        if (Settings.disable_localNeighborhood) {
            return new HashSet<>(complex.getNeighborsOf(complexVertex));
        }
        return localNeighborsOf(patternVertex, complexVertex);
    }


    /**
     * If a vertex v has an image w in complex, then set all vertices of the same orbit (i.e., vertices of the same degree)
     * to have that image w
     * */
    private void updateAndPropagateImageSets(Map<Integer, Set<Integer>> images, Map<Integer, Integer> match) {
        match.entrySet().stream().forEach(e -> {
            for (int ot : simplet.getOrbitOf(e.getKey())) {
                Set<Integer> tmp = images.getOrDefault(ot, Sets.newHashSet());
                tmp.add(e.getValue());
                images.put(ot, tmp);
            }
        });
    }

    private void intersectOrbitCandidateImages(Map<Integer, Set<Integer>> images) {
        // Orbit vertices have equal true image sets, so intersect their current upper bounds.
        Set<Integer> processed = new HashSet<>();
        for (int v : simplet.getVertexIndices()){
            if (processed.contains(v)) {
                continue;
            }

            Set<Integer> orbit = simplet.getOrbitOf(v);
            Set<Integer> commonImages = null;
            for (int orbitVertex : orbit) {
                Set<Integer> orbitImages = images.getOrDefault(orbitVertex, Collections.emptySet());
                if (commonImages == null) {
                    commonImages = new HashSet<>(orbitImages);
                } else {
                    commonImages.retainAll(orbitImages);
                }
            }

            Set<Integer> sharedImages = commonImages == null ? Collections.emptySet() : commonImages;
            for (int orbitVertex : orbit) {
                images.put(orbitVertex, new HashSet<>(sharedImages));
            }
            processed.addAll(orbit);
        }
    }

    private void buildIncidentSupportMaps() {
        localNeighborhood.clear();
        incidentSupportMaps.clear();

        for (Simplex simplex : simplet.getAllSimplices()) {
            SupMap supportMap = simplet.getSimp2SupMap(simplex.getIndex());
            if (supportMap == null) {
                continue;
            }
            for (int patternVertex : simplex.getVertexIndices()) {
                incidentSupportMaps
                        .computeIfAbsent(patternVertex, k -> new LinkedHashSet<>())
                        .add(supportMap);
            }
        }
    }

    private Set<Integer> localNeighborsOf(int patternVertex, int complexVertex) {
        Map<Integer, Set<Integer>> byImage =
                localNeighborhood.computeIfAbsent(patternVertex, k -> new ConcurrentHashMap<>());
        return byImage.computeIfAbsent(complexVertex, n -> computeLocalNeighbors(patternVertex, n));
    }

    private Set<Integer> computeLocalNeighbors(int patternVertex, int complexVertex) {
        Set<SupMap> supportMaps = incidentSupportMaps.get(patternVertex);
        if (supportMaps == null || supportMaps.isEmpty()) {
            return Collections.emptySet();
        }
        if (supportMaps.size() == 1) {
            return supportMaps.iterator().next().getVertexToNeighbours(complexVertex);
        }

        Set<Integer> neighbors = new HashSet<>();
        for (SupMap supportMap : supportMaps) {
            neighbors.addAll(supportMap.getVertexToNeighbours(complexVertex));
        }
        return neighbors.isEmpty() ? Collections.emptySet() : neighbors;
    }

}
