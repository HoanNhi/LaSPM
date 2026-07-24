package fi.tkk.ics.jbliss.pattern;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import fi.tkk.ics.jbliss.Graph;
import fi.tkk.ics.jbliss.Reporter;
import LaSPM.structures.Simplex;
import LaSPM.structures.Vertex;
import LaSPM.utils.Pair;
import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class JBlissPattern implements Serializable {

    private List<LabeledNode> vertices;
    private List<Edge> edges;
    private Map<Integer, Integer> originalIDs; // pattern VID -> simplet VID
    private VertexPositionEquivalence automorphisms;
    private final Map<Integer, Integer> canonicalLabelling;
    private final Graph jblissGraph;


    public JBlissPattern(Collection<Simplex> simplices, boolean bipartite) {
        this.automorphisms = null;
        this.canonicalLabelling = Maps.newHashMap();
        this.vertices = Lists.newArrayList();
        this.edges = Lists.newArrayList();
        this.originalIDs = Maps.newHashMap();
        if (bipartite) {
            // find the canonical form of the simplex
            initializeBipartiteGraph(simplices);
        } else {
            // find the canonical form of the graph projection
            initializeUnderlyingGraph(simplices);
        }
        this.jblissGraph = new Graph(this);
    }

    private void initializeUnderlyingGraph(Collection<Simplex> simplices) {
        Map<Integer, Integer> inserted = Maps.newHashMap();
        Set<Pair<Integer, Integer>> edgeSet = Sets.newHashSet();
        for (Simplex s : simplices) {
            List<Vertex> vertexList = Lists.newArrayList(s.getVertices());
            // node remapping; needed to use jbliss API
            for (int i = 0; i < vertexList.size(); i++) {
                int nodeVID = getNodeId(vertexList.get(i), inserted);
                for (int j = i + 1; j < vertexList.size(); j++) {
                    int nodeUID = getNodeId(vertexList.get(j), inserted);
                    if (nodeVID < nodeUID) {
                        edgeSet.add(new Pair<>(nodeVID, nodeUID));
                    } else {
                        edgeSet.add(new Pair<>(nodeUID, nodeVID));
                    }
                }
            }
        }
        // save vertex IDs
        inserted.entrySet().stream().forEach(e -> this.originalIDs.put(e.getValue(), e.getKey()));
        edgeSet.stream().forEach(p -> addEdge(p.getA(), p.getB()));
    }

    private void initializeBipartiteGraph(Collection<Simplex> simplices) {
        Map<Integer, Integer> inserted = Maps.newHashMap();
        for (Simplex s : simplices) {
            LabeledNode nodeS = new LabeledNode(vertices.size(), 2*s.getLabel() + 1);
            vertices.add(nodeS);
            // save simplex IDs as negative ints to distinguish them from vertex IDs
            inserted.put(-(s.getIndex()+1), nodeS.getIndex());
            for (Vertex v : s.getVertices()) {
                int nodeVID = getNodeId(v, inserted);
                addEdge(nodeVID, nodeS.getIndex());
            }
        }
        // save vertex IDs
        inserted.entrySet().stream().forEach(e -> this.originalIDs.put(e.getValue(), e.getKey()));
    }

    private int getNodeId(Vertex v, Map<Integer, Integer> inserted) {
        if (!inserted.containsKey(v.getIndex())) {
            LabeledNode nodeV = new LabeledNode(vertices.size(), 2*v.getLabel());
            inserted.put(v.getIndex(), nodeV.getIndex());
            vertices.add(nodeV);
            return nodeV.getIndex();
        }
        return inserted.get(v.getIndex());
    }

    protected class AutomorphismReporter implements Reporter {
        VertexPositionEquivalence equivalences;

        public AutomorphismReporter(VertexPositionEquivalence equivalences) {
            this.equivalences = equivalences;
        }

        @Override
        public void report(Map<Integer, Integer> generator, Object user_param) {
            generator.entrySet().forEach(e -> equivalences.addEquivalence(e.getKey(), e.getValue()));
        }
    }

    protected void fillVertexPositionEquivalences(VertexPositionEquivalence vertexPositionEquivalences) {
        for (int i = 0; i < vertices.size(); ++i) {
            vertexPositionEquivalences.addEquivalence(i, i);
        }
        AutomorphismReporter reporter = new AutomorphismReporter(vertexPositionEquivalences);
        jblissGraph.findAutomorphisms(reporter, null);
        vertexPositionEquivalences.propagateEquivalences();
    }

    public void findAutomorphisms() {
        if (automorphisms == null) {
            automorphisms = new VertexPositionEquivalence();
            fillVertexPositionEquivalences(automorphisms);
        }
    }

    public VertexPositionEquivalence getAutomorphisms() {
        return automorphisms;
    }

    public void addEdge(int src, int dst) {
        Edge edge = new Edge(src, dst);
        edges.add(edge);
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public int getNumberOfEdges() {
        return edges.size();
    }

    public void addVertex(LabeledNode node) {
        vertices.add(node);
    }

    public List<LabeledNode> getVertices() {
        return vertices;
    }

    public int getNumberOfVertices() {
        return vertices.size();
    }

    public void computeCanonicalLabeling() {
        if (canonicalLabelling.isEmpty()) {
            fillCanonicalLabelling(canonicalLabelling);
        }
    }

    protected void fillCanonicalLabelling(Map<Integer, Integer> canonicalLabelling) {
        jblissGraph.fillCanonicalLabeling(canonicalLabelling);
    }

    public void turnCanonical() {
        findAutomorphisms();
        computeCanonicalLabeling();

        boolean allEqual = true;
        for (Entry<Integer, Integer> e : canonicalLabelling.entrySet()) {
            if (!e.getKey().equals(e.getValue())) {
                allEqual = false;
            }
        }
        if (allEqual) {
            edges.sort((Edge o1, Edge o2) -> {
            if (o1.getSrc() != o2.getSrc()) {
                return Integer.compare(o1.getSrc(), o2.getSrc());
            }
            return Integer.compare(o1.getDst(), o2.getDst());
        });
            return;
        }
        List<LabeledNode> oldVertices = Lists.newArrayList(vertices);
        Map<Integer, Integer> oldOriginalIDs = Maps.newHashMap(originalIDs);
        for (int i = 0; i < vertices.size(); ++i) {
            int newPos = canonicalLabelling.get(i);
            // If position didn't change, do nothing
            if (newPos == i) {
                continue;
            }
            vertices.set(newPos, new LabeledNode(newPos, oldVertices.get(i).getLabel()));
            originalIDs.put(newPos, oldOriginalIDs.get(i));
        }
        for (int i = 0; i < edges.size(); ++i) {
            Edge edge = edges.get(i);

            int srcPos = edge.getSrc();
            int dstPos = edge.getDst();

            int convertedSrcPos = canonicalLabelling.get(srcPos);
            int convertedDstPos = canonicalLabelling.get(dstPos);

            if (convertedSrcPos < convertedDstPos) {
                edge.setSrc(convertedSrcPos);
                edge.setDst(convertedDstPos);
            } else {
                // If we changed the position of source and destination due to
                // relabel, we also have to change the labels to match this
                // change.
                edge.setSrc(convertedDstPos);
                edge.setDst(convertedSrcPos);
            }
        }
        edges.sort((Edge o1, Edge o2) -> {
            if (o1.getSrc() != o2.getSrc()) {
                return Integer.compare(o1.getSrc(), o2.getSrc());
            }
            return Integer.compare(o1.getDst(), o2.getDst());
        });
        VertexPositionEquivalence canonicalAutomorphisms = new VertexPositionEquivalence();
        Map<Integer, Set<Integer>> oldAutomorphisms = automorphisms.getEquivalences();
        oldAutomorphisms.entrySet().forEach(e -> {
            Set<Integer> canonicalEquivalences = e.getValue()
                    .stream()
                    .map(eq -> canonicalLabelling.get(eq))
                    .collect(Collectors.toSet());
            canonicalAutomorphisms.addEquivalences(canonicalLabelling.get(e.getKey()), canonicalEquivalences);
        });
        automorphisms = canonicalAutomorphisms;
    }

    // takes one vertex per orbit as representative, returns representative -> other vertices in the orbit
    public Map<Integer, Set<Integer>> getOrbitRepresentatives() {
        Map<Integer, Set<Integer>> orbitRepresentatives = Maps.newHashMap();
        Set<Set<Integer>> groups = automorphisms.getDistinctEquivalences();
        groups.stream().forEach(g -> {
            Iterator<Integer> it = g.iterator();
            while(it.hasNext()) {
                Integer obj = it.next();
                if (originalIDs.get(obj) >= 0) {
                    Set<Integer> others = g.stream()
                            .map(o -> originalIDs.get(o))
                            .collect(Collectors.toSet());
                    orbitRepresentatives.put(originalIDs.get(obj), others);
                    break;
                }
            }
        });
        return orbitRepresentatives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JBlissPattern other = (JBlissPattern) o;
        if (other.getNumberOfVertices() != this.getNumberOfVertices() || other.getNumberOfEdges() != this.getNumberOfEdges()) {
            return false;
        }
        for (int i = 0; i < this.vertices.size(); i++) {
            if (!this.vertices.get(i).equals(other.vertices.get(i))) {
                return false;
            }
        }
        for (int i = 0; i < this.edges.size(); i++) {
            if (!this.edges.get(i).equals(other.edges.get(i))) {
                return false;
            }
        }
        return true;
    }

    public Map<Integer, Integer> getCorrespondingVertex(JBlissPattern other){
        if (!equals(other))
            throw new IllegalStateException("Simplet must be isomorphic in order to get vertex correspondence!!!!");
        Map<Integer, Integer> vertexCorrespondence = new HashMap<>();
        for (int i = 0; i < this.vertices.size(); i++) {
            int thisVertexID = this.originalIDs.get(i);
            if (thisVertexID < 0)
                continue;
            int otherVertexID = other.originalIDs.get(i);
            vertexCorrespondence.put(thisVertexID, otherVertexID);
        }
        return vertexCorrespondence;
    }

    @Override
    public int hashCode() {
        return edges.hashCode();
    }

    public void printPattern() {
        System.out.println("Pattern: " +
                " |N|=" + this.vertices.size() +
                " |E|=" + this.edges.size() +
                this.vertices.toString() + " " +
                this.edges.toString());
    }

    public void printOriginalIDS() {
        originalIDs.entrySet().stream().forEach(e -> System.out.println(e.getKey() + "=>" + e.getValue()));
    }

}
