package LaSPM.structures;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import fi.tkk.ics.jbliss.pattern.JBlissPattern;
import LaSPM.utils.Pair;
import LaSPM.utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

import static LaSPM.utils.Utils.generate;

public class Simplet extends Simpl{

    private Map<Integer, List<Simplex>> simplices; // simplices in the simplet: dimension -> list of simplices with that dimension
    private Map<Integer, Set<Integer>> neighbours; // neighbours of each vertex: VID -> vertices belonging to a common simplex;
    private Map<String, List<Integer>> cofaceSimplexMap; // for each coface, it gives the positions in the simplex list of the simplices with that coface
    private HashBasedTable<Integer, Integer, Set<Integer>> simplexNeighbours; //(dimension, simplex pos) -> positions in the simplex list of simplices sharing a coface
    private Map<Integer, Set<Integer>> images; // image sets associated to the vertices: VID -> valid complex vertices mapped to VID
    private Map<Integer, Set<Integer>> nonCands; // mappings not valid found during the single match search
    private Map<Integer, Set<Integer>> labels;
    private JBlissPattern canForm; // canonical form of the simplet
    private JBlissPattern graphProj; // canonical form of the underlying graph
    private Map<Integer, Set<Integer>> orbitRepresentatives; // orbit representatives of the simplet
    private Map<Integer, Integer> orbitMemberships; // VID -> orbit representative
    private Set<String> maxSimplexKey;
    private double freq;
    private int dimension;
    private int incrId;
    private int numSimplex;
    private int labelMode; // 0 = all unlabeled, 1 = partially labeled, 2 = fully labeled.


    public Simplet(int id) {
        super(id);
        this.simplices = Maps.newHashMap();
        this.neighbours = Maps.newHashMap();
        this.cofaceSimplexMap = Maps.newHashMap();
        this.simplexNeighbours = HashBasedTable.create();
        this.images = Maps.newHashMap();
        this.nonCands = Maps.newHashMap();
        this.dimension = 0;
        this.labels = Maps.newHashMap();
        this.incrId = 1;
        initializeStructures();
        this.numSimplex = 0;
    }

    public Simplet(int id, Simplet s, boolean allMatches) {
        super(id, Sets.newHashSet(s.getVertices()));
        if (allMatches) {
            this.images = Utils.copyMap(s.getImages());
        }
        else {
            this.images = new HashMap<>();
        }
        this.nonCands = Utils.copyMap(s.getNonCands());
        initializeStructures();
        initializeFromSimplet(s);
        this.numSimplex = this.simplices.values().stream().mapToInt(List::size).sum();
    }

    private void initializeStructures() {
        this.canForm = null;
        this.graphProj = null;
        this.orbitRepresentatives = Maps.newHashMap();
        this.orbitMemberships = Maps.newHashMap();
    }

    private void initializeFromSimplet(Simplet s) {
        this.simplices = Maps.newHashMap();
        s.getSimplexMap().forEach((key, value) -> this.simplices.put(key, (List<Simplex>) Utils.deepCopy(value)));
        this.neighbours = Utils.copyMap(s.getNeighbors());
        this.cofaceSimplexMap = Maps.newHashMap();
        s.getCofaceIndex().forEach((key, value) -> this.cofaceSimplexMap.put(key, Lists.newArrayList(value)));
        this.simplexNeighbours = HashBasedTable.create();
        s.getSimplexNeighbours().cellSet().forEach(cell
                -> this.simplexNeighbours.put(cell.getRowKey(), cell.getColumnKey(), Sets.newHashSet(cell.getValue())));
        this.labels = s.getLabel().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new HashSet<>(entry.getValue())
                ));

        this.dimension = s.getDimension();
        this.incrId = s.incrId;
        this.labelMode = s.labelMode;
    }

    public void add0Simplex(Vertex v, Simplex s, int uID){
        ensureUniqueSimplexId(s);
        addVertex(v);
        setLabel(1, s.getLabel());
        incrId++;
        //Add vertex into 0-simplex
        List<Simplex> tmp = simplices.getOrDefault(1, Lists.newArrayList());
        tmp.add(s);
        simplices.put(1, tmp);
        if (s.getNumVertices() > dimension) {
            dimension = s.getNumVertices();
        }
        // add vertex in coface index and update neighbours
        List<Integer> tmpSimpList = cofaceSimplexMap.getOrDefault("-", Lists.newArrayList()); //Get all 0-simplex
        Set<Integer> tmpSimpNBS = Sets.newHashSet(tmpSimpList);
        tmpSimpNBS.remove(uID);
        //Update the neighbors of the new vertex
        simplexNeighbours.put(1, simplices.get(1).size() - 1, Sets.newHashSet(tmpSimpList));
        //Update the neighbor of the existing vertices with the new one.
        for (int ot : tmpSimpNBS) {
            Set<Integer> tmpOTSimpNBS = simplexNeighbours.get(1, ot);
            tmpOTSimpNBS.add(simplices.get(1).size() - 1);
            simplexNeighbours.put(1, ot, tmpOTSimpNBS);
        }
        tmpSimpList.add(simplices.get(1).size() - 1);
        this.cofaceSimplexMap.put("-", tmpSimpList);
        this.numSimplex++;
    }

    // adds 1-simplex and 0-simplex of new vertex
    public void add1Simplex(Vertex newV, Simplex vSimp, Simplex edge, int oldV) {
        add0Simplex(newV, vSimp, oldV);
        addkSimplex(edge);
        incrId++;
    }

    // adds k-simplex
    public void addkSimplex(Simplex s) {
        ensureUniqueSimplexId(s);
        int simplexDim = s.getNumVertices();
        // update simplex map
        addVertices(s.getVertices());
        setLabel(s.getNumVertices(), s.getLabel());
        List<Simplex> tmp = simplices.getOrDefault(simplexDim, Lists.newArrayList());
        tmp.add(s);
        simplices.put(simplexDim, tmp);
        // update neighbour map
        for (int v : s.getVertexIndices()) {
            Set<Integer> ngb = Sets.newHashSet(s.getVertexIndices());
            ngb.remove(v);
            Set<Integer> tmp2 = neighbours.getOrDefault(v, Sets.newHashSet());
            tmp2.addAll(ngb);
            neighbours.put(v, tmp2);
        }
        // update dimension
        if (simplexDim > dimension) {
            dimension = simplexDim;
        }
        // set sub-simplices to maximal=false
        simplices.getOrDefault(simplexDim - 1, Lists.newArrayList())
                .stream()
                .filter(sub -> sub.isMaximal() && s.containsFace(sub))
                .forEach(sub -> sub.setMaximal(false));
        this.numSimplex++;
    }

    private void ensureUniqueSimplexId(Simplex simplex) {
        Set<Integer> usedIds = simplices.values().stream()
                .flatMap(Collection::stream)
                .map(Simpl::getIndex)
                .collect(Collectors.toSet());
        if (simplex.getIndex() <= 0 || usedIds.contains(simplex.getIndex())) {
            int nextId = usedIds.stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0) + 1;
            simplex.setId(nextId);
        }
        incrId = Math.max(incrId, simplex.getIndex() + 1);
    }


    public void updateCofaceMap(Simplex s) {
        // update coface map
        int simplexDim = s.getNumVertices();
        List<String> cofaces = generateCofaces(s);
        int idx = simplices.get(simplexDim).size()-1;
        for (String code : cofaces) {
            if (!simplexNeighbours.contains(simplexDim, idx)) {
                simplexNeighbours.put(simplexDim, idx, Sets.newHashSet());
            }
            Set<Integer> newNeigh = Sets.newHashSet(cofaceSimplexMap.getOrDefault(code, Lists.newArrayList()));
            if (!newNeigh.isEmpty()) {
                Set<Integer> tmpNeigh = simplexNeighbours.get(simplexDim, idx);
                tmpNeigh.addAll(newNeigh);
                simplexNeighbours.put(simplexDim, idx, tmpNeigh);
            }
            for (int ot : newNeigh) {
                Set<Integer> tmpNeighOt = simplexNeighbours.get(simplexDim, ot);
                tmpNeighOt.add(idx);
                simplexNeighbours.put(simplexDim, ot, tmpNeighOt);
            }
            List<Integer> tmp2 = cofaceSimplexMap.getOrDefault(code, Lists.newArrayList());
            tmp2.add(idx);
            cofaceSimplexMap.put(code, tmp2);
        }
    }

    public int getLabelMode(){
        return this.labelMode;
    }

    public void setLabelMode(int labelMode) {
        this.labelMode = labelMode;
    }

    public Set<Integer> getNeighborsOf(int v) {
        return neighbours.getOrDefault(v, Collections.emptySet());
    }

    public Map<Integer, Set<Integer>> getNeighbors() {
        return neighbours;
    }

    public HashBasedTable<Integer, Integer, Set<Integer>> getSimplexNeighbours() {
        return simplexNeighbours;
    }

    public boolean areNeighbors(int u, int v) {
        return neighbours.get(u).contains(v) || neighbours.get(v).contains(u);
    }

    public void setNeighbours(Map<Integer, Set<Integer>> neighbours) {
        this.neighbours = neighbours;
    }

    public Set<Simplex> getAllHDSimplices() {
        return simplices.values().stream()
                .flatMap(s -> s.stream().filter(Simplex::isMaximal)).collect(Collectors.toSet());
    }

    public Set<Simplex> getAllLabeledSimplices(){
        return simplices.values().stream()
                .flatMap(s -> s.stream().filter(simp -> simp.getLabel() != -1)).collect(Collectors.toSet());
    }

    public Set<Simplex> getAllSimplices() {
        return simplices.values().stream().flatMap(s -> s.stream()).collect(Collectors.toSet());
    }



    public Map<Integer, List<Simplex>> getSimplexMap() {
        return simplices;
    }

    public void insertSimplices(int dim, List<Simplex> simplices) {
        this.simplices.put(dim, simplices);
    }

    public int getNumSimplices() {
        return this.numSimplex;
    }

    public HashBasedTable<Integer, Integer, Integer> getLabelMap(){
        HashBasedTable<Integer, Integer, Integer> result = HashBasedTable.create();
        simplices.forEach((key, value) -> {
            for (Simplex s : value) {
                if (!result.contains(key, s.getLabel()))
                    result.put(key, s.getLabel(), 1);
                else
                    result.put(key, s.getLabel(), result.get(key, s.getLabel()) + 1);
            }
        });
        return result;
    }

    public Map<Integer, Set<Integer>> getLabel(){
        return this.labels;
    }

    public void setLabel(int dim, int label){
        Set<Integer> labelAtDim = this.labels.getOrDefault(dim, Sets.newHashSet());
        labelAtDim.add(label);
        labels.put(dim, labelAtDim);
    }

    public void updateLabelMap(){
        Map<Integer, Set<Integer>> tmpLabels = new HashMap<>();
        this.simplices.forEach((key, value) -> {
            Set<Integer> labelAtDim = tmpLabels.getOrDefault(key, Sets.newHashSet());
            for (Simplex s : value) {
                labelAtDim.add(s.getLabel());
            }
            tmpLabels.put(key, labelAtDim);
        });
        this.labels = tmpLabels;
    }

    @Override
    public boolean containsFace(Simplex ot) {
        return simplices.getOrDefault(ot.getNumVertices(), Collections.emptyList()).stream().anyMatch(s -> s.containsFace(ot));
    }

    @Override
    public boolean containsFace(Set<Vertex> ot) {
        return simplices.getOrDefault(ot.size(), Collections.emptyList()).stream().anyMatch(s -> s.containsFace(ot));
    }

    public int getIncrId() {
        return ++incrId;
    }

    public void setIncrId(int incrId) {
        this.incrId = incrId;
    }

    public void setImages(Map<Integer, Set<Integer>> ubs) {
        this.images = new HashMap<>();
        if (ubs.isEmpty()) {
            return;
        }
        ubs.forEach((key, value) -> this.images.put(key, Sets.newHashSet(value)));
    }

    // used when the simplet cannot frequent
    public void emptyImageMap() {
        this.images.clear();
    }

    public void addUBImage(int v, Set<Integer> image) {
        images.put(v, Sets.newHashSet(image));
    }

    public Map<Integer, Set<Integer>> getImages() {
        return images;
    }

    public Set<Integer> getImageOf(int v) {
        return images.getOrDefault(v, Collections.emptySet());
    }

    public Map<Integer, Set<Integer>> getNonCands() {
        return nonCands;
    }

    public void setNonCands(int v, int img){
        this.nonCands.computeIfAbsent(v, key -> new HashSet<>()).add(img);
    }

    public void setNonCands(Map<Integer, Set<Integer>> nonCands) {
        this.nonCands = nonCands;
    }

    public void computeFrequency(String supportMeasure) {
        if (images.isEmpty()) {
            return;
        }
        if (supportMeasure.equalsIgnoreCase("mni")) {
            freq = images.values().stream().mapToInt(s -> s.size()).min().orElse(0);
        }
    }

    public double getFreq() {
        return freq;
    }

    public void setFreq(double freq) {
        this.freq = freq;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    private List<String> generateCofaces(Simplex s) {
        List<Integer> V = Lists.newArrayList(s.getVertexIndices());
        List<int[]> combinations = generate(s.getNumVertices(), s.getNumVertices() - 1);
        return combinations.stream()
                .map(combination -> {
                    int[] subset = new int[combination.length];
                    for (int i = 0; i < combination.length; i++) {
                        subset[i] = V.get(combination[i]);
                    }
                    Arrays.sort(subset);
                    return Arrays.toString(subset);
                })
                .collect(Collectors.toList());
    }

    public Map<String, List<Integer>> getCofaceIndex() {
        return cofaceSimplexMap;
    }

    public Simplex findSimplex(Set<Integer> vertices){
        List<Simplex> simplexAtDim = simplices.getOrDefault(vertices.size(), Collections.emptyList());
        if (simplexAtDim.isEmpty()){
            System.out.println("Cannot find the simplex with vertices " + vertices);
            return null;
        }
        return simplexAtDim.stream().filter(s -> s.getVertexIndices().equals(vertices)).findFirst().orElse(null);
    }

    // Canonical identity and automorphism orbits must include every labeled face.
    public JBlissPattern computeCanonicalForm(boolean includeAllSimplices) {
        JBlissPattern p;
        if (includeAllSimplices)
            p = new JBlissPattern(getAllSimplices(), true);
        else
            p = new JBlissPattern(getAllHDSimplices(), true);
        p.turnCanonical();
        orbitRepresentatives = p.getOrbitRepresentatives();
        orbitRepresentatives.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .forEach(e -> e.getValue().stream().forEach(v -> orbitMemberships.put(v, e.getKey())));
        return p;
    }


    // compute canonical form of underlying graph
    public JBlissPattern computeGraphCanonicalForm() {
        JBlissPattern p = new JBlissPattern(getAllHDSimplices(), false);
        p.turnCanonical();
        return p;
    }


    public Set<Integer> getOrbitOf(int v, boolean includeAllSimplices) {
        if (orbitRepresentatives.isEmpty()) {
            computeCanonicalForm(includeAllSimplices);
        }
        return orbitRepresentatives.getOrDefault(
                orbitMemberships.get(v), Collections.singleton(v));
    }

    public List<Pair<Set<Vertex>, Set<Integer>>> validateJoists(List<Simplex> C, int level, int maxDim) {
        if (level >= maxDim) {
            return Collections.emptyList();
        }
        Map<Integer, Set<Integer>> inv_idx = Maps.newHashMap();
        //Get a map of vertex v -> simplex s containing v
        for (int idx = 0; idx < C.size(); idx++) {
            for (int v : C.get(idx).getVertexIndices()) {
                Set<Integer> tmp = inv_idx.getOrDefault(v, Sets.newHashSet());
                tmp.add(idx);
                inv_idx.put(v, tmp);
            }
        }
        List<Pair<Set<Vertex>, Set<Integer>>> joists = Lists.newArrayList();
        /*
         * In particular, for each q-simplex s in the simplet, we search for a simplex subset S of other simplices
         * associated to the same face that share a vertex w not in s.
         * */
        for (int s : simplexNeighbours.row(level).keySet()) {
            // vertices of all the nbs of simplex s
            Set<Integer> v_set = Sets.newHashSet();
            //Get all the vertex neighbor of simplex s, including the vertices of s themselves
            simplexNeighbours.get(level, s).forEach(x -> v_set.addAll(C.get(x).getVertexIndices()));
            C.get(s).getVertexIndices().forEach(v_set::remove); //Remove all vertices of s, keep the vertex w only.
            int maxVID = C.get(s).getVertexIndices().stream().mapToInt(x -> x).max().getAsInt();
            v_set.stream()
                    .filter(v -> v > maxVID)
                    .forEach(v -> {
                        Set<Integer> cand_joist = Sets.newHashSet(inv_idx.get(v)); //Get the simplex containing v
                        cand_joist.retainAll(simplexNeighbours.get(level, s)); // Remove non-neighbor simplices
                        if (cand_joist.size() == C.get(s).getNumVertices()) {
                            Set<Integer> joist = Sets.newHashSet(C.get(s).getVertexIndices());
                            Set<Vertex> joistVertices = Sets.newHashSet();
                            joist.add(v);
                            joist.forEach(joistVertex -> {
                                joistVertices.add(getVertex(joistVertex));
                            });
                            cand_joist.add(s);
                            joists.add(new Pair<>(joistVertices, cand_joist));
                        }
                    });
        }
        return joists;
    }

    // update simplex neighbours map used to find the joists
    public void updateSimplexNeighbours(Set<Integer> simplexIDs, int level) {
        Map<Integer, Set<Integer>> tmpMap = simplexNeighbours.row(level);
        for (int sID : simplexIDs) {
            Set<Integer> tmpNeigh = tmpMap.get(sID);
            tmpNeigh.removeAll(simplexIDs);
            simplexNeighbours.put(level, sID, tmpNeigh);
        }
    }

    public void removeSimplexNeighbours(Set<Integer> simplexIDs, int level) {
        Map<Integer, Set<Integer>> tmpMap = simplexNeighbours.row(level);
        for (int sID : simplexIDs) {
            Set<Integer> tmpNeigh = tmpMap.get(sID);
            tmpNeigh.removeAll(simplexIDs);
            simplexNeighbours.put(level, sID, tmpNeigh);
        }
    }

    public JBlissPattern getCanonicalForm(boolean includeAllSimplices) {
        if (canForm == null){
            try {
                canForm = computeCanonicalForm(includeAllSimplices);
            }
            catch (RuntimeException e) {
                throw new IllegalStateException(
                        "Cannot canonicalize simplet " + toStringAllSimplices(),
                        e);
            }
        }

        return canForm;
    }

    public Set<String> getSimplexKeys(){
        if (this.maxSimplexKey == null){
            Set<Simplex> maxSimplices = new HashSet<>();
            for (Simplex s : getAllSimplices()){
                if (s.getLabel() == -1)
                    continue;
                maxSimplices.add(s);
            }
            this.maxSimplexKey = maxSimplices.stream().map(Utils::computeKeyMNI).collect(Collectors.toSet());
            return this.maxSimplexKey;
        }
        return this.maxSimplexKey;
    }

    public JBlissPattern getGraphProj() {
        if (graphProj == null) {
            graphProj = computeGraphCanonicalForm();
        }
        return graphProj;
    }

    public boolean equals(Simplet o) {
        return this.getCanonicalForm(true).equals(o.getCanonicalForm(true));
    }

    // creates simplex-count and vertex-count sequences;
    // used to store the simplices in the examined map
    public Pair<Pair<String, String>, String> computeFingerPrint() {
        int[] simplexCounts = new int[dimension];
        int[] vertexCounts = new int[getNumVertices()];
        int[] listLabels = this.labels.values().stream()
                .flatMapToInt(set -> set.stream().mapToInt(Integer::intValue)) // Directly create an IntStream
                .sorted()
                .toArray();
        simplices.entrySet().stream().forEach(e -> {
            List<Simplex> maximal = e.getValue().stream()
                    .filter(s -> s.isMaximal())
                    .collect(Collectors.toList());
            simplexCounts[e.getKey() - 1] += maximal.size();
            maximal.forEach(simpl -> simpl.getVertexIndices().forEach(v -> vertexCounts[v]++));
        });
        Arrays.sort(vertexCounts);
        String vertexCountString = Arrays.toString(vertexCounts);
        String simplexCountString = Arrays.toString(simplexCounts);
        String listLabelString = Arrays.toString(listLabels);
        return new Pair<>(new Pair<>(simplexCountString, vertexCountString), listLabelString);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(String.valueOf(freq));

        List<Simplex> simplices = new ArrayList<>(getAllSimplices().stream().filter(s -> s.getLabel() != -1).toList());
        simplices.sort((a, b) -> {
            // smaller simplex first
            if (a.getVertices().size() != b.getVertices().size()) {
                return Integer.compare(a.getVertices().size(), b.getVertices().size());
            }

            // lexicographic comparison
            List<Integer> va = a.getVertexIndices().stream().toList();
            List<Integer> vb = b.getVertexIndices().stream().toList();

            for (int i = 0; i < va.size(); i++) {
                int cmp = Integer.compare(va.get(i), vb.get(i));
                if (cmp != 0) {
                    return cmp;
                }
            }

            return 0;
        });

        for (Simplex s : simplices) {
            out.append("-").append(s);
        }

        return out.toString();
    }

    public String toStringAllSimplices() {
        String out = String.valueOf(freq);
        for (int dim : simplices.keySet()){
            for (Simplex s: simplices.get(dim)){
                out += "-";
                out += s.toStringIndex();
            }
        }
        return out;
    }

    public List<Integer> dfs(List<Integer> visited, int v) {
        for (int w : getNeighborsOf(v)) {
            if (!visited.contains(w)) {
                visited.add(w);
                dfs(visited, w);
            }
        }
        return visited;
    }


    public void printSimplet() {
        for (Simplex s : getAllSimplices()) {
            System.out.print(s.toString() + "-");
        }
        System.out.println("D" + dimension);
    }

    @Override
    public Simpl copy() {
        Simplet s = new Simplet(getIndex());
        s.setVertices(new HashSet<>(getVertices()));
        s.setImages(Utils.copyMap(getImages()));
        getSimplexMap().entrySet()
                .forEach(e -> s.insertSimplices(e.getKey(), (List<Simplex>) Utils.deepCopy(e.getValue())));
        s.setIncrId(getIncrId());
        s.setDimension(getDimension());
        s.setNeighbours(Utils.copyMap(getNeighbors()));
        return s;
    }


    public void emptyNonCands() {
        this.nonCands.clear();
    }
}
