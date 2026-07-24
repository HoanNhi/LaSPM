package LaSPM.LFreSCo.search;

import LaSPM.structures.*;
import LaSPM.utils.MemoryLogger;
import LaSPM.utils.StopWatch;
import LaSPM.utils.Utils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.stream.Collectors;

import static LaSPM.utils.Utils.computeKeyMNI;

public class MatchFinder {

    Complex complex;
    Simplet simplet;
    int minFreq;

    public MatchFinder(Complex complex, Simplet simplet, int minFreq) {
        this.complex = complex;
        this.simplet = simplet;
        this.minFreq = minFreq;
    }

    // Find all the occurrences of a pattern
    public void examine() {
        StopWatch timer = new StopWatch();
        timer.start();
        Map<Integer, Set<Integer>> images = new HashMap<>();
        Map<Integer, Set<Integer>> candidateImages = new HashMap<>();
        simplet.getImages().forEach((e, v) -> {
            candidateImages.put(e, new HashSet<>(v));
        });

        for (Vertex vertex : simplet.getVertices()) {
            candidateImages.computeIfAbsent(
                    vertex.getIndex(),
                    ignored -> new HashSet<>(complex.getVertexWithLabel(vertex.getLabel())));
        }

        for (Simplex s : simplet.getAllSimplices()){
            if (s.getLabel() == -1){
                continue;
            }
            String key = computeKeyMNI(s);
            SupMap supportMap = complex.getSupportMap(key);
            if (supportMap == null){
                simplet.emptyImageMap();
                return;
            }
            for (Vertex v : s.getVertices()){
                Set<Integer> candImg = candidateImages.get(v.getIndex());
                Set<Integer> supported = supportMap.getImgWithLabel(v.getLabel());
                if (supported == null) {
                    simplet.emptyImageMap();
                    return;
                }
                candImg.retainAll(supported);
            }
        }

        for (int v : simplet.getVertexIndices()){
            Set<Integer> imgOfV = candidateImages.get(v)
                    .stream()
                    .filter(w -> simplet.getNeighborsOf(v).size() <= complex.getNeighborsOf(w).size())
                    .collect(Collectors.toSet());
            if (imgOfV.size() < minFreq){
                simplet.emptyImageMap();
                return;
            }
            candidateImages.put(v, imgOfV);
        }
        PropagateImageSets(candidateImages);
        if (candidateImages
                .values()
                .stream()
                .anyMatch(set -> set.size() < minFreq)){
            simplet.emptyImageMap();
            return;
        }
        simplet.setImages(candidateImages);

        // order the vertices according to size of image sets
        // Mining the smallest-sized vertex, if frequency is less than tau, return!
        List<Integer> ordered_vertices = Lists.newArrayList(simplet.getVertexIndices());
        ordered_vertices.sort((Integer e1, Integer e2)
                -> Integer.compare(simplet.getImageOf(e1).size(), simplet.getImageOf(e2).size()));
        // initial set of valid matches
        for (int v : ordered_vertices) {

            Set<Integer> partialImageSet = new HashSet<>(images.getOrDefault(v, Collections.emptySet()));
            Set<Integer> candidates = Sets.newHashSet(simplet.getImageOf(v));
            candidates.removeAll(partialImageSet);
            int c = 0;
            // sort vertices using a dfs
            List<Integer> vertex_ordering = Lists.newArrayList();
            vertex_ordering.add(v);
            simplet.dfs(vertex_ordering, v);

            for (Integer n : candidates) {

                c += 1;
                if (partialImageSet.contains(n))
                    continue;

                Map<Integer, Integer> M = Maps.newHashMap();
                M.put(v, n);


                Map<Integer, Integer> match = findMatch(M, vertex_ordering, 0);
                if (match.size() == simplet.getNumVertices()){
                    updateAndPropagateImageSets(images, match);
                    partialImageSet.add(n);
                }

                // early stop if the simplet cannot be frequent
                if (candidates.size() - c + partialImageSet.size() < minFreq) {
                    simplet.emptyImageMap();
                    return;
                }

            }
            Set<Integer> imageV = images.computeIfAbsent(v, key -> new HashSet<>());
            imageV.addAll(partialImageSet);
        }
        simplet.setImages(images);
        MemoryLogger.getInstance().checkMemory();

    }

    // Find the minimum number of occurrences needed to determine if the pattern is frequent
    public void examineSingle(Map<Integer, Set<Integer>> parentImages, Map<Integer, Set<Integer>> parentNonCands, Map<Integer, Set<Integer>> candidateBounds, long timeout) {
        Map<Integer, Set<Integer>> images = new HashMap<>();
        simplet.setImages(candidateBounds);
        Map<Integer, Set<Integer>> candidates = simplet.getImages();
        Map<Integer, Set<Integer>> nonCands = Utils.copyMap(parentNonCands);
        simplet.setNonCands(nonCands);

        for (Vertex vertex : simplet.getVertices()) {
            Set<Integer> labelImages = complex.getVertexWithLabel(vertex.getLabel());
            candidates.computeIfAbsent(vertex.getIndex(), ignored -> new HashSet<>(labelImages));
            candidates.get(vertex.getIndex()).retainAll(labelImages);
        }

        intersectOrbitCandidateImages(candidates);

        for (Vertex v : simplet.getVertices()) {
            Set<Integer> partialImageSet = new HashSet<>(images.getOrDefault(v.getIndex(), new HashSet<>()));
            // if we have enough matches for this vertex, we don't need to examine it
            if (partialImageSet.size() < minFreq) {
                List<Integer> ordered_vertices = Lists.newArrayList();
                ordered_vertices.add(v.getIndex());
                simplet.dfs(ordered_vertices, v.getIndex());
                List<Integer> candidate = new ArrayList<>(candidates.get(v.getIndex()));
                Utils.customSort(candidate, parentImages.getOrDefault(v.getIndex(), Collections.emptySet()));
                int c = partialImageSet.size();
                List<Integer> toResume = Lists.newArrayList();
                boolean resume = true;
                if (candidate.size() < minFreq) {
                    simplet.emptyImageMap();
                    return;
                }
                for (Integer n : candidate) {
                    if (partialImageSet.contains(n)) {
                        continue;
                    }
                    c += 1;
                    if (nonCands.getOrDefault(v.getIndex(), Collections.emptySet()).contains(n)) {
                        continue;
                    }
                    if (complex.getNeighborsOf(n).size() < simplet.getNeighborsOf(v.getIndex()).size()) {
                        continue;
                    }
                    Map<Integer, Integer> M = Maps.newHashMap();
                    M.put(v.getIndex(), n);
                    // call recursive function to get a match
                    Map<Integer, Integer> match = findMatch(M, 0, ordered_vertices, System.currentTimeMillis(), timeout);
                    if (match.size() == simplet.getNumVertices()) {
                        // update image sets with the valid matches
                        updateAndPropagateImageSets(images, match);
                        partialImageSet.add(n);
                    } else if (match.isEmpty()) {
                        toResume.add(n);
                        c -= 1;
                    } else {
                        nonCands.computeIfAbsent(v.getIndex(), key -> new HashSet<>()).add(n);
                    }
                    // early stop if the simplet cannot be frequent
                    if (candidate.size() - c + partialImageSet.size() < minFreq) {
                        simplet.emptyImageMap();
                        return;
                    } else if (partialImageSet.size() >= minFreq) {
                        resume = false;
                        break;
                    }
                }
                if (resume) {
                    c = 0;
                    for (int n : toResume) {
                        if (partialImageSet.contains(n)) {
                            continue;
                        }
                        c += 1;
                        if (nonCands.getOrDefault(v.getIndex(), Collections.emptySet()).contains(n)) {
                            continue;
                        }
                        Map<Integer, Integer> M = Maps.newHashMap();
                        M.put(v.getIndex(), n);
                        // call recursive function to get a match
                        Map<Integer, Integer> match = findMatch(M, 0, ordered_vertices, System.currentTimeMillis(), -1);
                        if (match.size() == simplet.getNumVertices()) {
                            // update image sets with the valid matches
                            updateAndPropagateImageSets(images, match);
                            partialImageSet.add(n);
                        } else {
                            nonCands.computeIfAbsent(v.getIndex(), key -> new HashSet<>()).add(n);
                        }
                        // early stop if the simplet cannot be frequent
                        if (toResume.size() - c + partialImageSet.size() < minFreq) {
                            simplet.emptyImageMap();
                            return;
                        } else if (partialImageSet.size() >= minFreq) {
                            break;
                        }
                    }
                }
            }
            Set<Integer> imageV = images.computeIfAbsent(v.getIndex(), key -> new HashSet<>());
            for (int m : partialImageSet) {
                if (imageV.size() >= minFreq) {
                    break;
                }
                imageV.add(m);
            }
        }
        simplet.setImages(images);
        simplet.setNonCands(nonCands);
    }

    // Decision-version
    private Map<Integer, Integer> findMatch(Map<Integer, Integer> M,
                                            int vertexID,
                                            List<Integer> vertexOrder,
                                            long startTime,
                                            long timeout) {
        if (timeout > -1 && (System.currentTimeMillis() - startTime > timeout)) {
            return Collections.EMPTY_MAP;
        }
        // M is a valid match
        if (M.size() == vertexOrder.size()) {
            return M;
        }
        // find next vertex to examine
        while (M.containsKey(vertexOrder.get(vertexID))) {
            vertexID++;
        }
        if (vertexID > vertexOrder.size()) {
            return M;
        }
        int w = vertexOrder.get(vertexID);
        // the set of candidates is the intersection among:
        // 1. upper-bound to the image set
        // 2. vertices not already assigned to a simplet vertex
        // 3. neighbours of vertices assigned to simplex vertices that are neighbours of w
        // 4. the assignment is valid only if it preserves the simplex memberships
        Set<Integer> ngbs = simplet.getNeighborsOf(w);
        Set<Integer> candidates = Sets.newHashSet();
        boolean start = true;
        for (int ngb : ngbs) {
            if (M.containsKey(ngb)) {
                Set<Integer> ngbs_c = new HashSet<>(complex.getNeighborsOf(M.get(ngb)));
                ngbs_c.retainAll(complex.getVertexWithLabel(simplet.getVertex(w).getLabel()));
                if (start) {
                    start = false;
                    candidates.addAll(ngbs_c);
                } else {
                    candidates.retainAll(ngbs_c);
                }
            }
        }
        if (start) {
            candidates.addAll(simplet.getImageOf(w));
        }
        candidates.retainAll(simplet.getImageOf(w));
        for (int n : candidates) {
            if (satisfiesConstraints(M, w, n)) {
                Map<Integer, Integer> newM = Maps.newHashMap(M);
                newM.put(w, n);
                Map<Integer, Integer> updM = findMatch(newM, vertexID + 1, vertexOrder, startTime, timeout);
                if ((updM.size() ==  vertexOrder.size() || updM.isEmpty())) {
                    return updM;
                }
            } else if (timeout > -1 && (System.currentTimeMillis() - startTime > timeout)) {
                return Collections.EMPTY_MAP;
            }
        }
        return M;
    }

    //Exact
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
        Set<Integer> candidates = new HashSet<>();
        boolean constrainedByMappedNeighbor = false;
        boolean firstMappedNeighbor = true;

        for (int ngb : ngbs) {
            if (M.containsKey(ngb)) {
                constrainedByMappedNeighbor = true;
                Set<Integer> ngbs_c = complex.getNeighborsOf(M.get(ngb));
                if (firstMappedNeighbor) {
                    firstMappedNeighbor = false;
                    candidates.addAll(ngbs_c);
                }
                else {
                    candidates.retainAll(ngbs_c);
                }
            }
        }

        if (!constrainedByMappedNeighbor) {
            candidates.addAll(simplet.getImageOf(w));
        }
        candidates.retainAll(simplet.getImageOf(w));
        for (int n : candidates) {
            if (!satisfiesConstraints(M, w, n))
                continue;
            Map<Integer, Integer> newM = new HashMap<>(M);
            newM.put(w, n);
            Map<Integer, Integer> updM = findMatch(newM, vertexOrder, vertexID + 1);
            if (updM.size() == vertexOrder.size()) {
                return updM;
            }
        }
        return new HashMap<>(M);
    }

    private boolean satisfiesConstraints(Map<Integer, Integer> M, int w, int n) {
        if (complex.getNeighborsOf(n) != null
                && complex.getVertices().get(n).getLabel() == simplet.getVertex(w).getLabel()
                && complex.getNeighborsOf(n).size() >= simplet.getNeighborsOf(w).size()
                && M.entrySet().stream().noneMatch(e -> (e.getValue() == n) ||
                    (simplet.areNeighbors(e.getKey(), w) && !complex.getNeighborsOf(e.getValue()).contains(n)))){
            Map<Integer, Integer> tentativeMapping = new HashMap<>(M);
            tentativeMapping.put(w, n);
            Set<Simplex> allSimplices = simplet.getAllLabeledSimplices();
            for (Simplex simplex : allSimplices) {
                if (!simplex.contains(w)) {
                    continue;
                }
                if (!this.complex.canExtendToSimplex(simplex, tentativeMapping)) {
                    return false;
                }
            }
            return true;
        }
        else{
            return false;
        }
    }



    /**
     * If a vertex v has an image w in complex, then set all vertices of the same orbit (i.e., vertices of the same degree)
     * to have that image w
     * */
    private void updateAndPropagateImageSets(Map<Integer, Set<Integer>> images, Map<Integer, Integer> match) {
        match.entrySet().stream().forEach(e -> {
            for (int ot : simplet.getOrbitOf(e.getKey(), true)) {
                images.computeIfAbsent(ot, key -> new HashSet<>()).add(e.getValue());
            }
        });
    }


    private void PropagateImageSets(Map<Integer, Set<Integer>> images){
        for (int v : simplet.getVertexIndices()){
            for (int ot: simplet.getOrbitOf(v, true)){
                Set<Integer> tmp = images.computeIfAbsent(v, key -> new HashSet<>());
                tmp.addAll(images.getOrDefault(ot, Collections.emptySet()));
            }
        }
    }

    private void intersectOrbitCandidateImages(Map<Integer, Set<Integer>> images) {
        // Orbit vertices have equal true image sets, so intersect their current upper bounds.
        Set<Integer> processed = new HashSet<>();
        for (int v : simplet.getVertexIndices()){
            if (processed.contains(v)) {
                continue;
            }

            Set<Integer> orbit = simplet.getOrbitOf(v, true);
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

}
