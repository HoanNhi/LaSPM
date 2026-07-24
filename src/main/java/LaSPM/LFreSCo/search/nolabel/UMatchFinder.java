package LaSPM.LFreSCo.search.nolabel;

import LaSPM.structures.Complex;
import LaSPM.structures.Simplet;
import LaSPM.structures.Simplex;
import LaSPM.utils.Utils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UMatchFinder {
    Complex complex;
    Simplet simplet;
    int minFreq;

    public UMatchFinder(Complex complex, Simplet simplet, int minFreq) {
        this.complex = complex;
        this.simplet = simplet;
        this.minFreq = minFreq;
    }

    //EXACT
    public void examine() {
        Map<Integer, Set<Integer>> images = Maps.newHashMap();
        if (this.simplet.getImages().isEmpty()) {
            this.simplet.emptyImageMap();
        } else {
            List<Integer> ordered_vertices = Lists.newArrayList(this.simplet.getVertexIndices());
            Collections.sort(ordered_vertices, (e1, e2) -> Integer.compare(this.simplet.getImageOf(e1).size(), this.simplet.getImageOf(e2).size()));
            Set<Integer> initial = Sets.newHashSet();

            for(Simplex s : this.complex.getSimplices()) {
                if (s.getNumVertices() >= this.simplet.getNumVertices()) {
                    initial.addAll(s.getVertexIndices());
                }
            }

            for(int v : ordered_vertices) {
                if (this.simplet.getImageOf(v).size() < this.minFreq) {
                    this.simplet.emptyImageMap();
                    return;
                }

                Set<Integer> partialImageSet = Sets.newHashSet(initial);
                partialImageSet.addAll(images.getOrDefault(v, Sets.newHashSet()));
                Set<Integer> candidates = Sets.newHashSet(this.simplet.getImageOf(v));
                candidates.removeAll(partialImageSet);
                int c = 0;
                List<Integer> vertex_ordering = Lists.newArrayList();
                vertex_ordering.add(v);
                this.simplet.dfs(vertex_ordering, v);

                for(Integer n : candidates) {
                    ++c;
                    Map<Integer, Integer> M = Maps.newHashMap();
                    M.put(v, n);
                    Map<Integer, Integer> match = this.findMatch(M, 0, vertex_ordering);
                    if (match.size() == this.simplet.getNumVertices()) {
                        this.updateAndPropagateImageSets(images, match);
                        partialImageSet.add(n);
                    }

                    if (candidates.size() - c + partialImageSet.size() < this.minFreq) {
                        this.simplet.emptyImageMap();
                        return;
                    }
                }

                Set<Integer> imageV = images.getOrDefault(v, Sets.newHashSet());
                imageV.addAll(partialImageSet);
                images.put(v, imageV);
            }

            this.simplet.setImages(images);
        }
    }


    // Find the minimum number of occurrences needed to determine if the pattern is frequent
    public void examineSingle(
            Map<Integer, Set<Integer>> parent,
            Map<Integer, Set<Integer>> pNonCands,
            long timeout) {
        Map<Integer, Set<Integer>> images = Maps.newHashMap();
        // order the vertices according to size of image sets
        // find enough matches for each vertex
        // initialization of images
        Set<Integer> initial = Sets.newHashSet();
        for (Simplex s : complex.getSimplices()) {
            if (s.getNumVertices() >= simplet.getNumVertices()) {
                initial.addAll(s.getVertexIndices());
            }
        }
        for (int v : simplet.getVertexIndices()) {
            Set<Integer> partialImageSet = Sets.newHashSet(initial);
            partialImageSet.addAll(images.getOrDefault(v, Sets.newHashSet()));
            // if we have enough matches for this vertex, we don't need to examine it
            if (partialImageSet.size() < minFreq) {
                List<Integer> ordered_vertices = Lists.newArrayList();
                ordered_vertices.add(v);
                simplet.dfs(ordered_vertices, v);
                List<Integer> candidates = Lists.newArrayList(complex.getVertexIDs());
                if (!parent.isEmpty()) {
                    Utils.customSort(candidates, parent.getOrDefault(v, Collections.EMPTY_SET));
                }
                int c = partialImageSet.size();
                List<Integer> toResume = Lists.newArrayList();
                boolean resume = true;
                if (candidates.size() < minFreq) {
                    simplet.emptyImageMap();
                    return;
                }
                for (Integer n : candidates) {
                    if (partialImageSet.contains(n)) {
                        continue;
                    }
                    c += 1;
                    if (pNonCands.getOrDefault(v, Sets.newHashSet()).contains(n)) {
                        continue;
                    }
                    if (complex.getNeighborsOf(n).size() < simplet.getNeighborsOf(v).size()) {
                        continue;
                    }
                    Map<Integer, Integer> M = Maps.newHashMap();
                    M.put(v, n);
                    Map<Integer, Integer> match = findMatch(M, 0, ordered_vertices, System.currentTimeMillis(), timeout);
                    if (match.size() == simplet.getNumVertices()) {
                        // update image sets with the valid matches
                        updateAndPropagateImageSets(images, match);
                        partialImageSet.add(n);
                    } else if (match.isEmpty()) {
                        toResume.add(n);
                        c -= 1;
                    } else {
                        Set<Integer> tmp = pNonCands.getOrDefault(v, Sets.newHashSet());
                        tmp.add(n);
                        pNonCands.put(v, tmp);
                    }
                    // early stop if the simplet cannot be frequent
                    if (candidates.size() - c + partialImageSet.size() < minFreq) {
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
                        if (pNonCands.getOrDefault(v, Sets.newHashSet()).contains(n)) {
                            continue;
                        }
                        Map<Integer, Integer> M = Maps.newHashMap();
                        M.put(v, n);
                        // call recursive function to get a match
                        Map<Integer, Integer> match = findMatch(M, 0, ordered_vertices, System.currentTimeMillis(), -1);
                        if (match.size() == simplet.getNumVertices()) {
                            // update image sets with the valid matches
                            updateAndPropagateImageSets(images, match);
                            partialImageSet.add(n);
                        } else {
                            Set<Integer> tmp = pNonCands.getOrDefault(v, Sets.newHashSet());
                            tmp.add(n);
                            pNonCands.put(v, tmp);
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
            Set<Integer> imageV = images.getOrDefault(v, Sets.newHashSet());
            for (int m : partialImageSet) {
                if (imageV.size() >= minFreq) {
                    break;
                }
                imageV.add(m);
            }
            images.put(v, imageV);
        }
        simplet.setImages(images);
        simplet.setNonCands(pNonCands);
    }

    // MIN-BASED
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
                Set<Integer> ngbs_c = complex.getNeighborsOf(M.get(ngb));
                if (start) {
                    start = false;
                    candidates.addAll(ngbs_c);
                } else {
                    candidates.retainAll(ngbs_c);
                }
            }
        }
        if (start) {
            candidates.addAll(complex.getVertexIDs());
        }
        for (int n : candidates) {
            if (satisfiesConstraints(M, w, n)) {
                Map<Integer, Integer> newM = Maps.newHashMap(M);
                newM.put(w, n);
                Map<Integer, Integer> updM = findMatch(newM, vertexID + 1, vertexOrder, startTime, timeout);
                if (updM.size() ==  vertexOrder.size() || updM.isEmpty()) {
                    return updM;
                }
            } else if (timeout > -1 && (System.currentTimeMillis() - startTime > timeout)) {
                return Collections.EMPTY_MAP;
            }
        }
        return M;
    }

    private Map<Integer, Integer> findMatch(Map<Integer, Integer> M, int vertexID, List<Integer> vertexOrder) {
        if (M.size() == vertexOrder.size()) {
            return M;
        } else {
            while(M.containsKey(vertexOrder.get(vertexID))) {
                ++vertexID;
            }

            if (vertexID > vertexOrder.size()) {
                return M;
            } else {
                int w = vertexOrder.get(vertexID);
                Set<Integer> ngbs = this.simplet.getNeighborsOf(w);
                Set<Integer> candidates = Sets.newHashSet(this.simplet.getImageOf(w));

                for(int ngb : ngbs) {
                    if (M.containsKey(ngb)) {
                        Set<Integer> ngbs_c = this.complex.getNeighborsOf(M.get(ngb));
                        candidates.retainAll(ngbs_c);
                    }
                }

                for(int n : candidates) {
                    if (this.satisfiesConstraints(M, w, n)) {
                        Map<Integer, Integer> newM = Maps.newHashMap(M);
                        newM.put(w, n);
                        Map<Integer, Integer> updM = this.findMatch(newM, vertexID + 1, vertexOrder);
                        if (updM.size() == vertexOrder.size()) {
                            return updM;
                        }
                    }
                }

                return M;
            }
        }
    }

    private boolean satisfiesConstraints(Map<Integer, Integer> M, int w, int n) {
        if (this.complex.getNeighborsOf(n).size() >= this.simplet.getNeighborsOf(w).size()
                && !M.entrySet().stream().anyMatch((e) -> e.getValue() == n || this.simplet.areNeighbors(e.getKey(), w)
                && !this.complex.getNeighborsOf(e.getValue()).contains(n))) {
            for(Simplex s : this.simplet.getAllHDSimplices()) {
                if (s.contains(w) && s.getNumVertices() > 2) {
                    List<Integer> simplex = Lists.newArrayList();
                    simplex.add(n);
                    M.keySet().stream().filter(s::contains).forEach((v) -> simplex.add(M.get(v)));
                    if (!this.complex.contains(simplex)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private void updateAndPropagateImageSets(Map<Integer, Set<Integer>> images, Map<Integer, Integer> match) {
        match.forEach((key, value) -> {
            for(int ot : this.simplet.getOrbitOf(key, false)) {
                Set<Integer> tmp = images.getOrDefault(ot, Sets.newHashSet());
                tmp.add(value);
                images.put(ot, tmp);
            }

        });
    }
}
