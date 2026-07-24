package LaSPM.structures;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import fi.tkk.ics.jbliss.pattern.JBlissPattern;
import LaSPM.utils.Pair;
import LaSPM.utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class LSimplet extends Simpl {
    private Map<Integer, List<Simplex>> dimToSimplices; // simplices in the simplet: dimension -> list of simplices with that dimension
    private List<Simplex> simplices;
    private Map<Integer, Set<Integer>> vertex2Simps; //vertex -> simplices containing it
    private Map<Integer, Set<Integer>> simplexNeighbours; //(dimension, simplex pos) -> positions in the simplex list of simplices sharing a coface
    private Map<Integer, Set<Integer>> vertexToNgb;
    private Map<Integer, Set<Integer>> images; // image sets associated to the vertices: VID -> valid complex vertices mapped to VID
    private Map<Integer, List<Integer>> labels;
    private Map<Integer, Set<Integer>> orbitRepresentatives; // orbit representatives of the simplet
    private Map<Integer, int[]> vertexToDegreeSequence;
    private Map<Integer, Integer> orbitMemberships; // VID -> orbit representative
    private List<Set<Integer>> simp2Imgs;
    private List<SupMap> simp2SupMap;
    private JBlissPattern canForm; // canonical form of the simplet
    private JBlissPattern graphProj; // canonical form of the underlying graph

    private Set<String> maxSimplexKey;
    private Set<String> simplexVertexKey;

    private int freq;
    private int dimension;
    private int incrId;
    private int numSimplex;

    private int maxDim;

    public LSimplet(SupMap supMap, Simplex s, int maxDim, int incrId) {
        super(incrId, new HashSet<>(s.getVertices()));
        s.setId(0);

        this.dimToSimplices = new HashMap<>();
        this.images = new HashMap<>();
        this.labels = new HashMap<>();
        this.simplexNeighbours = new HashMap<>();
        this.simplices = new ArrayList<>();
        this.orbitRepresentatives = Maps.newHashMap();
        this.orbitMemberships = Maps.newHashMap();
        this.vertexToNgb = new HashMap<>();
        this.incrId = incrId;
        this.vertex2Simps = new HashMap<>();
        this.vertexToDegreeSequence = new HashMap<>();
        this.canForm = null;

        this.simplexVertexKey = new HashSet<>();
        this.maxSimplexKey = new HashSet<>();

        this.simp2SupMap = new ArrayList<>(128);

        this.dimension = s.getDimension();
        this.dimToSimplices.put(s.getDimension(), Lists.newArrayList(s));
        this.simplices.add(s.getIndex(), s);
        this.numSimplex++;
        this.maxDim = maxDim;

        for (Vertex v : this.getVertices()) {
            int vID = v.getIndex();
            if (!vertexToDegreeSequence.containsKey(vID))
                this.vertexToDegreeSequence.put(vID, new int[this.maxDim + 1]);
            Set<Integer> ngbs = new HashSet<>(s.getVertexIndices());
            ngbs.remove(v.getIndex());
            ;
            vertexToNgb.put(v.getIndex(), ngbs);
            this.vertexToDegreeSequence.get(vID)[s.getDimension()]++;
        }

        for (Vertex vID : s.getVertices()) {
            Set<Integer> images = new HashSet<>(supMap.getImgWithLabel(vID.getLabel()));
            this.vertex2Simps.put(vID.getIndex(), Sets.newHashSet(s.getIndex()));
            this.images.put(vID.getIndex(), images);
        }

        this.simplexNeighbours.put(s.getIndex(), new HashSet<>());
        this.simplexVertexKey.add(this.simplexKey(s.getVertexIndices()));
        this.simp2SupMap.add(supMap);
        this.labels.put(s.getNumVertices(), Lists.newArrayList());
        this.labels.get(s.getNumVertices()).add(s.getLabel());
        maxSimplexKey.add(s.computeKeyMNI(s.getLabel()));
    }

    public LSimplet(LSimplet pattern, int incrId) {
        super(incrId, new HashSet<>(pattern.getVertices()));

        this.maxDim = pattern.getMaxDim();

        this.dimToSimplices = new HashMap<>();
        pattern.getDimToSimplices().entrySet().forEach(e -> {
            List<Simplex> simplices = (List<Simplex>) Utils.deepCopy(e.getValue());
            this.dimToSimplices.put(e.getKey(), simplices);
        });
        this.simplices = (List<Simplex>) Utils.deepCopy(pattern.getSimplices());

        this.images = new HashMap<>();

        this.labels = new HashMap<>();
        pattern.getLabel().entrySet().forEach(e -> {
            List<Integer> labelAtDim = new ArrayList<>(e.getValue());
            this.labels.put(e.getKey(), labelAtDim);
        });

        this.simplexNeighbours = new HashMap<>();
        pattern.getsimplexNeighbours().forEach((key, value)
                -> this.simplexNeighbours.put(key, new HashSet<>(value)));

        this.vertexToNgb = new HashMap<>();
        pattern.getVertexToNgb().entrySet().forEach(e -> {
            Set<Integer> ngbs = new HashSet<>(e.getValue());
            this.vertexToNgb.put(e.getKey(), ngbs);
        });

        this.vertex2Simps = new HashMap<>();
        pattern.getVertex2Simps().entrySet().forEach(e -> {
            Set<Integer> simps = new HashSet<>(e.getValue());
            this.vertex2Simps.put(e.getKey(), simps);
        });

        this.vertexToDegreeSequence = new HashMap<>();
        pattern.getVertexToDegreeSequence().forEach((key, value) ->
                this.vertexToDegreeSequence.put(key, Arrays.copyOf(value, value.length)));
        this.simp2SupMap = new ArrayList<>(pattern.getSimp2SupMap());

        this.maxSimplexKey = new HashSet<>(pattern.getMaxSimplexKey());
        this.simplexVertexKey = new HashSet<>(pattern.getSimplexVertexKey());

        this.canForm = null;
        this.orbitRepresentatives = Maps.newHashMap();
        this.orbitMemberships = Maps.newHashMap();
        this.incrId = incrId;
        this.numSimplex = pattern.getNumSimplices();

        this.dimension = pattern.getDimension();
    }


    //Compute parent hypergraphlet, only need vertices, simplices, and labels
    public LSimplet(LSimplet pattern, Simplex toremove) {
        super(-1, new HashSet<>(pattern.getVertices()));

        this.simplices = new ArrayList<>(pattern.getSimplices());

        this.labels = new HashMap<>();
        pattern.getLabel().entrySet().forEach(e -> {
            List<Integer> labelAtDim = new ArrayList<>(e.getValue());
            this.labels.put(e.getKey(), labelAtDim);
        });

        this.canForm = null;
        this.orbitRepresentatives = Maps.newHashMap();
        this.orbitMemberships = Maps.newHashMap();
        this.numSimplex = pattern.getNumSimplices();
        this.maxSimplexKey = new HashSet<>(pattern.getMaxSimplexKey());

        this.simplices.remove(toremove.getIndex());
        this.numSimplex--;
        List<Integer> labelsAtDimension = this.labels.get(toremove.getNumVertices());
        labelsAtDimension.remove(Integer.valueOf(toremove.getLabel()));
        if (labelsAtDimension.isEmpty()) {
            this.labels.remove(toremove.getNumVertices());
        }
        this.dimension = this.simplices.stream()
                .mapToInt(Simplex::getDimension)
                .max()
                .orElse(0);
        this.maxSimplexKey.remove(toremove.computeKeyMNI(toremove.getLabel()));
        for (Simplex s : this.simplices)
            this.maxSimplexKey.add(s.computeKeyMNI(s.getLabel()));
    }

    public int getMaxDim() {
        return this.maxDim;
    }

    public Map<Integer, int[]> getVertexToDegreeSequence() {
        return this.vertexToDegreeSequence;
    }

    public List<SupMap> getSimp2SupMap() {
        return this.simp2SupMap;
    }

    public int getNumSimplices() {
        return this.numSimplex;
    }

    public Simplex getSimplex(int id) {
        return this.simplices.get(id);
    }

    public List<Simplex> getSimplices() {
        return this.simplices;
    }

    public List<Set<Integer>> getSimp2Imgs() {
        return this.simp2Imgs;
    }

    public Map<Integer, Set<Integer>> getVertex2Simps() {
        return this.vertex2Simps;
    }

    public Set<String> getSimplexVertexKey() {
        return this.simplexVertexKey;
    }

    public Set<String> getMaxSimplexKey() {
        return this.maxSimplexKey;
    }

    /**
     * Function to glue two hyperedges together.
     *
     * @param vertices : vertex IDs in THIS hypergraphlet that the new simplex attaches to
     * @param simplex  : simplex template to be attached
     */
    public void addEdge(Set<Pair<Integer, Integer>> vertices, Simplex simplex) {
        // Reindex vertices of simplex s to match hypergraphlet vertex IDs
        Map<Integer, Integer> simplexToPatternVertex = new HashMap<>();
        this.labels.computeIfAbsent(simplex.getNumVertices(), k -> new ArrayList<>()).add(simplex.getLabel());

        if (this.dimension < simplex.getDimension()) {
            this.dimension = simplex.getDimension();
        }

        for (Pair<Integer, Integer> vertex : vertices) {
            simplexToPatternVertex.put(vertex.getB(), vertex.getA());
        }

        int nextVertexID = this.getVertices().size();
        ArrayList<Vertex> sortedVerticesOfS = new ArrayList<>(simplex.getVertices());
        Collections.sort(sortedVerticesOfS);
        Set<Vertex> reindexedVertices = new HashSet<>();
        for (Vertex simplexVertex : sortedVerticesOfS) {
            Integer patternVertexID = simplexToPatternVertex.get(simplexVertex.getIndex());
            Vertex reindexedVertex;
            if (patternVertexID == null) {
                reindexedVertex = new Vertex(nextVertexID++, simplexVertex.getLabel());
                this.addVertex(reindexedVertex);
            } else {
                Vertex patternVertex = this.getVertex(patternVertexID);
                reindexedVertex = new Vertex(patternVertexID, patternVertex.getLabel());
            }
            reindexedVertices.add(reindexedVertex);
        }

        simplex.setVertices(reindexedVertices);
        simplex.updateVertexIndices();
        simplex.setId(this.numSimplex);
        // 2. Add simplex to hypergraphlet
        dimToSimplices.computeIfAbsent(simplex.getDimension(), k -> new ArrayList<>()).add(simplex);
        this.numSimplex++;


        int sID = simplex.getIndex();
        // 3. Update vertex-to-simplex mapping
        for (Vertex v : simplex.getVertices()) {
            int vID = v.getIndex();
            if (!this.vertexToDegreeSequence.containsKey((vID)))
                this.vertexToDegreeSequence.put(vID, new int[this.maxDim + 1]);
            this.vertexToDegreeSequence.get(vID)[simplex.getDimension()]++;
            Set<Integer> ngbs = new HashSet<>(simplex.getVertexIndices());
            ngbs.remove(v.getIndex());
            vertexToNgb.computeIfAbsent(v.getIndex(), k -> new HashSet<>()).addAll(ngbs);
        }

        // 4. Update simplex neighbors (shared vertices)
        simplexNeighbours.put(sID, new HashSet<>());

        for (int vID : simplex.getVertexIndices()) {
            Set<Integer> incident = this.vertex2Simps.computeIfAbsent(vID, k -> new HashSet<>());
            incident.add(sID);

            for (int otherSID : incident) {
                if (otherSID != sID) {
                    simplexNeighbours.get(sID).add(otherSID);
                    Set<Integer> others = simplexNeighbours.get(otherSID);
                    if (others == null)
                        others = new HashSet<>();
                    others.add(sID);
                    simplexNeighbours.put(otherSID, others);
                }
            }
        }
        List<Integer> sSortedVertexIDs = new ArrayList<>(simplex.getVertexIndices());
        Collections.sort(sSortedVertexIDs);
        simplexVertexKey.add(sSortedVertexIDs.toString());
        maxSimplexKey.add(simplex.computeKeyMNI(simplex.getLabel()));
        this.simplices.add(simplex);
        this.labels.values().stream().forEach(e -> Collections.sort(e));
    }

    public List<LSimplet> getParentPatterns() {
        List<LSimplet> parents = new ArrayList<>();
        for (Simplex s : this.simplices) {
            LSimplet parent = new LSimplet(this, s);
            parents.add(parent);
        }
        return parents;
    }

    public void computeIntersectImage(Map<Integer, Set<Integer>> thatImages, Map<Integer, Integer> vertexCorrespondences) {
        for (Map.Entry<Integer, Integer> vertexCorrespondence : vertexCorrespondences.entrySet()) {
                this.images.get(vertexCorrespondence.getKey()).retainAll(thatImages.get(vertexCorrespondence.getValue()));
        }
    }

    public void computeIntersectImage(Map<Integer, Set<Integer>> thatImages) {
        for (int v : this.images.keySet()) {
            if (thatImages.containsKey(v))
                this.images.get(v).retainAll(thatImages.get(v));
        }
    }

    public int[] getDegreeSequenceOf(int vID) {
        return this.vertexToDegreeSequence.get(vID);
    }

    public Set<Integer> getIncidentSimplex(int vID) {
        return this.vertex2Simps.get(vID);
    }

    public int getIncrId() {
        return ++this.incrId;
    }

    private String simplexKey(Set<Integer> vertices) {
        List<Integer> sorted = new ArrayList<>(vertices);
        Collections.sort(sorted);
        return sorted.toString();
    }


    public int getDimension() {
        return this.dimension;
    }

    public Map<Integer, Set<Integer>> getsimplexNeighbours() {
        return this.simplexNeighbours;
    }

    public Set<Integer> getSimplexNeighboursOf(int sID) {
        return this.simplexNeighbours.get(sID);
    }

    public Map<Integer, List<Simplex>> getDimToSimplices() {
        return this.dimToSimplices;
    }

    public List<Simplex> getAllSimplices() {
        return this.simplices;
    }

    public Map<Integer, Set<Integer>> getImages() {
        return this.images;
    }

    public List<Set<Integer>> getSimp2Images() {
        return this.simp2Imgs;
    }

    public Set<Integer> getSimpl2ImagesOf(int s) {
        return this.simp2Imgs.get(s);
    }

    public void setSimp2Images(List<Set<Integer>> simp2Imgs) {
        this.simp2Imgs = simp2Imgs;
    }

    public void computeImageIfAbsent(Map<Integer, Set<Integer>> images, SupMap supMap, Simplex s, int minFreq) {
        images.entrySet().forEach(e -> {
            int vertex = e.getKey();
            Set<Integer> vertex_image = new HashSet<>(e.getValue());
            this.images.put(vertex, vertex_image);
        });
        for (Vertex v : s.getVertices()) {
            Set<Integer> image = new HashSet<>(supMap.getImgWithLabel(v.getLabel()));
            if (this.images.get(v.getIndex()) == null)
                this.images.put(v.getIndex(), image);
            else {
                this.images.get(v.getIndex()).retainAll(image);
            }
            if (this.images.get(v.getIndex()).size() < minFreq) {
                this.images.clear();
                this.freq = -1;
                return;
            }
        }

        if (this.simp2SupMap.size() == s.getIndex())
            this.simp2SupMap.add(supMap);
        else if (this.simp2SupMap.size() > s.getIndex())
            this.simp2SupMap.add(s.getIndex(), supMap);
        else
            System.out.println("Incorrect!!!!!!!");

    }

    public SupMap getSimp2SupMap(int sID) {
        return this.simp2SupMap.get(sID);
    }

    public void setImages(Map<Integer, Set<Integer>> images) {
        this.images = images;
    }

    public Set<Integer> getImageOf(int v) {
        return images.computeIfAbsent(v, k -> new HashSet<>());
    }

    public boolean checkIfSimplexExists(String key) {
        return this.simplexVertexKey.contains(key);
    }

    public JBlissPattern computeGraphCanonicalForm() {
        JBlissPattern p = new JBlissPattern(getAllSimplices(), false);
        p.turnCanonical();
        return p;
    }

    public JBlissPattern getGraphProj() {
        if (graphProj == null) {
            graphProj = computeGraphCanonicalForm();
        }
        return graphProj;
    }
    public JBlissPattern computeCanonicalForm() {
        if (this.canForm != null)
            return this.canForm;
        JBlissPattern p = new JBlissPattern(this.simplices, true);
        p.turnCanonical();
        orbitRepresentatives = p.getOrbitRepresentatives();
        orbitRepresentatives.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .forEach(e -> e.getValue().forEach(v -> orbitMemberships.put(v, e.getKey())));
        this.canForm = p;
        return this.canForm;
    }

    public Set<Integer> getOrbitOf(int v) {
        if (orbitRepresentatives.isEmpty()) {
            computeCanonicalForm();
        }
        return orbitRepresentatives.get(orbitMemberships.get(v));
    }

    public List<Integer> dfs(List<Integer> visited, int v) {
        Stack<Integer> stack = new Stack<>();
        Set<Integer> visitedSet = new HashSet<>();
        Set<Integer> visitedSimp = new HashSet<>();
        stack.add(v);
        visited.add(v);
        visitedSet.add(v);
        while (!stack.isEmpty()) {
            int nextV = stack.pop();
            for (int sID : this.vertex2Simps.get(nextV)) {
                if (visitedSimp.contains(sID))
                    continue;
                visitedSimp.add(sID);
                Simplex s = this.simplices.get(sID);
                for (int vSimp : s.getVertexIndices()) {
                    if (visitedSet.contains(vSimp))
                        continue;
                    visitedSet.add(vSimp);
                    visited.add(vSimp);
                    stack.add(vSimp);
                }
            }
        }
        return visited;
    }

//    public List<Integer> dfs(List<Integer> visited, int v) {
//        for (int w : getNeighborsOf(v)) {
//            if (!visited.contains(w)) {
//                visited.add(w);
//                dfs(visited, w);
//            }
//        }
//        return visited;
//    }

    public boolean areNeighbors(int u, int v) {
        return vertexToNgb.get(u).contains(v) || vertexToNgb.get(v).contains(u);
    }

    public Map<Integer, List<Integer>> getLabel() {
        return this.labels;
    }


    public Set<Integer> getNeighborsOf(int vID) {
        return this.vertexToNgb.get(vID);
    }

    public Map<Integer, Set<Integer>> getVertexToNgb() {
        return this.vertexToNgb;
    }

    public int getFreq() {
        return this.freq;
    }

    public void computeFreq() {
        if (this.images.isEmpty()) {
            this.freq = 0;
            return;
        }
        this.freq = this.images.values().stream().mapToInt(Set::size).min().getAsInt();
    }

    public Pair<Pair<String, String>, String> computeFingerPrint() {
        int[] simplexCounts = new int[dimension];
        int[] vertexCounts = new int[getNumVertices()];
        int[] listLabels = this.labels.values().stream()
                .flatMapToInt(set -> set.stream().mapToInt(Integer::intValue)) // Directly create an IntStream
                .sorted()
                .toArray();
        for (int i = 0; i < this.simplices.size(); i++) {
            simplexCounts[this.simplices.get(i).getNumVertices() - 1]++;
            for (int v : this.simplices.get(i).getVertexIndices())
                vertexCounts[v]++;
        }

        Arrays.sort(vertexCounts);
        String vertexCountString = Arrays.toString(vertexCounts);
        String simplexCountString = Arrays.toString(simplexCounts);
        String listLabelString = Arrays.toString(listLabels);
        return new Pair<>(new Pair<>(simplexCountString, vertexCountString), listLabelString);
    }

    public Pair<Pair<String, String>, String> computeFingerPrint(int numVertex) {
        int[] simplexCounts = new int[dimension];
        int[] vertexCounts = new int[numVertex];
        Arrays.fill(vertexCounts, -1);
        int[] listLabels = this.labels.values().stream()
                .flatMapToInt(set -> set.stream().mapToInt(Integer::intValue)) // Directly create an IntStream
                .sorted()
                .toArray();
        for (int i = 0; i < this.simplices.size(); i++) {
            simplexCounts[this.simplices.get(i).getNumVertices() - 1]++;
            for (int v : this.simplices.get(i).getVertexIndices()) {
                if (vertexCounts[v] == -1)
                    vertexCounts[v] = 0;
                vertexCounts[v]++;
            }
        }

        int[] trueVertexCounts = Arrays.stream(vertexCounts)
                .filter(v -> v != -1)
                .toArray();

        Arrays.sort(trueVertexCounts);
        String vertexCountString = Arrays.toString(trueVertexCounts);
        String simplexCountString = Arrays.toString(simplexCounts);
        String listLabelString = Arrays.toString(listLabels);
        return new Pair<>(new Pair<>(simplexCountString, vertexCountString), listLabelString);
    }

    public void emptyMap(boolean clearKey) {
        if (clearKey)
            this.images.clear();
        else {
            for (int key : this.images.keySet()) {
                this.images.get(key).clear();
            }
        }
    }

    public String toString() {
        String out = String.valueOf(freq);
        for (Simplex s : this.getAllSimplices()) {
            out += "-" + s.toString();
        }
        return out;
    }


    @Override
    public Simpl copy() {
        return null;
    }

    @Override
    public boolean containsFace(Simplex ot) {
        return false;
    }

    @Override
    public boolean containsFace(Set<Vertex> ot) {
        return false;
    }

    public static void main(String args[]) {
        Set<Vertex> testVertices1 = Sets.newHashSet();
        testVertices1.add(new Vertex(0, 0));
        testVertices1.add(new Vertex(1, 1));
        testVertices1.add(new Vertex(2, 0));
        testVertices1.add(new Vertex(3, 1));
        Simplex s = new Simplex(0, testVertices1, 1, true);

        Set<Vertex> testVertices2 = Sets.newHashSet();
        testVertices2.add(new Vertex(4, 0));
        testVertices2.add(new Vertex(5, 1));
        testVertices2.add(new Vertex(6, 0));

        Simplex s1 = new Simplex(0, testVertices2, 1, true);
        SupMap test = new SupMap(4);
        test.setLabelToVertexIndex(new HashMap<>());

        LSimplet pattern = new LSimplet(test, s, 4, 0);
        Set<Pair<Integer, Integer>> pairVertex = new HashSet<>();
        pairVertex.add(new Pair<>(4, 0));
        pairVertex.add(new Pair<>(5, 1));
        pairVertex.add(new Pair<>(6, 2));

        List<Integer> vertexIndices = testVertices1.stream().map(v -> v.getIndex()).collect(Collectors.toList());
        Collections.sort(vertexIndices);
        if (pattern.checkIfSimplexExists(vertexIndices.toString())) {
            System.out.println("Pass!");
        } else {
            System.out.println("Fail!");
        }
    }
}
