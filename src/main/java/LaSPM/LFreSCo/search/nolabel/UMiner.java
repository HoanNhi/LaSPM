package LaSPM.LFreSCo.search.nolabel;

import LaSPM.structures.Complex;
import LaSPM.structures.Simplet;
import LaSPM.structures.Simplex;
import LaSPM.structures.Vertex;
import LaSPM.utils.Pair;
import LaSPM.utils.Settings;
import LaSPM.utils.StopWatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.stream.Collectors;

public class UMiner {
    private int incrId = 0;
    private HashBasedTable<String, String, Set<Simplet>> examined = HashBasedTable.create();
    private List<Pair<String, Integer>> occMap = Lists.newArrayList();
    public int totalGen = 0;
    public int totalExamine = 0;

    public UMiner() {
    }

    public List mine(Complex complex, int minFreq, int minSize, int maxSize, boolean limited, long timeout) {
        Simplet simplet = new Simplet(this.incrId);
        Vertex dummy = new Vertex(0, -1);
        this.incrId++;
        simplet.add0Simplex(dummy, new Simplex(this.incrId, Sets.newHashSet(dummy), -1, false), -1);
        Map<Integer, Set<Integer>> images = Maps.newHashMap();
        images.put(0, complex.getVertexIDs());
        simplet.setImages(images);
        List FS = this.extend(complex, simplet, minFreq, minSize, maxSize, limited, timeout);
        return FS;
    }

    private List<Simplet> extend(Complex complex, Simplet simplet, int minFreq, int minSize, int maxSize, boolean limited, long timeout) {
        List frequents = Lists.newArrayList();
        List<Simplet> simpletStack = Lists.newArrayList();
        List<List<Simplet>> extensionsStack = Lists.newArrayList();
        List<Integer> nextExtensionStack = Lists.newArrayList();
        simpletStack.add(simplet);

        while (!simpletStack.isEmpty()) {
            int depth = simpletStack.size() - 1;
            Simplet current = simpletStack.get(depth);


            if (extensionsStack.size() == depth) {
                StopWatch watch = new StopWatch();
                watch.start();
                List<Simplet> extensions = Lists.newArrayList();
                int u = current.getNumVertices();
                if (u < maxSize) {
                    Vertex vertexU = new Vertex(u, -1);
                    for(int v = 0; v < u; ++v) {
                        Vertex vertexV = new Vertex(v, -1);
                        Simplet ext = new Simplet(this.incrId, current, Settings.allMatches);
                        Simplex simplex = new Simplex(ext.getIncrId(), Sets.newHashSet(vertexU, vertexV), -1, true);
                        ext.add1Simplex(vertexU, new Simplex(ext.getIncrId(), Sets.newHashSet(vertexU), -1, false), simplex, v);
                        totalGen++;
                        if (!this.hasBeenExamined(ext)) {
                            ext.addUBImage(u, complex.getVertexIDs());
                            ext.updateCofaceMap(simplex);
                            extensions.add(ext);
                            ++this.incrId;
                        }
                    }
                }

                current.getSimplexMap().entrySet().forEach((entry) -> {
                    for(Pair<Set<Vertex>, Set<Integer>> joist : current.validateJoists(entry.getValue(), entry.getKey(), complex.getMaxDim())) {
                        Simplet ext = new Simplet(this.incrId, current, Settings.allMatches);
                        Simplex simplex = new Simplex(ext.getIncrId(), joist.getA(), -1, true);
                        ext.addkSimplex(simplex);
                        totalGen++;
                        if (!this.hasBeenExamined(ext)) {
                            ext.updateCofaceMap(simplex);
                            ext.updateSimplexNeighbours(joist.getB(), entry.getKey());
                            extensions.add(ext);
                            ++this.incrId;
                        }
                    }

                });
                totalExamine += extensions.size();
                extensions.parallelStream().forEach((ext) -> {
                    UMatchFinder matcher = new UMatchFinder(complex, ext, minFreq);
                    if (Settings.allMatches)
                        matcher.examine();
                    else
                        matcher.examineSingle(current.getImages(), ext.getNonCands(), timeout);
                    ext.computeFrequency("mni");
                });
                extensionsStack.add(extensions);
                nextExtensionStack.add(0);
            }

            List<Simplet> extensions = extensionsStack.get(depth);
            int nextExtension = nextExtensionStack.get(depth);
            if (nextExtension < extensions.size()) {
                Simplet ext = extensions.get(nextExtension);
                nextExtensionStack.set(depth, nextExtension + 1);
                if (ext.getFreq() >= minFreq) {
                    if (Settings.writeImageSet) {
                        for(int v : ext.getImages().values().stream().flatMap((v) -> v.stream()).collect(Collectors.toSet())) {
                            this.occMap.add(new Pair(ext.toString(), v));
                        }
                    }

                    simpletStack.add(ext);
                }
                continue;
            }

            if (limited) {
                current.emptyImageMap();
                current.setNonCands(Collections.EMPTY_MAP);
            }
            simpletStack.remove(depth);
            extensionsStack.remove(depth);
            nextExtensionStack.remove(depth);


            if (!simpletStack.isEmpty() && current.getDimension() >= minSize) {
                if (limited) {
                    frequents.add(current.toString());
                } else {
                    current.emptyImageMap();
                    current.emptyNonCands();
                    frequents.add(current);
                }
            }
        }

        return frequents;
    }

    private boolean hasBeenExamined(Simplet s) {
        Pair<Pair<String, String>, String> p = s.computeFingerPrint();
        String hashCodeA = p.getA().toString();
        String hashCodeB = p.getB();
        if (this.examined.contains(hashCodeB, hashCodeA)) {
            if (this.examined.get(hashCodeB, hashCodeA).stream().anyMatch((other) ->
                    s.getDimension() == other.getDimension()
                    && s.getNumSimplices() == other.getNumSimplices()
                    && s.getLabel().equals(other.getLabel())
                    && s.getSimplexKeys().equals(other.getSimplexKeys())
                    && s.getGraphProj().equals(other.getGraphProj())
                    && s.getCanonicalForm(false).equals(other.getCanonicalForm(false)))) {
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
        return this.occMap;
    }
}
