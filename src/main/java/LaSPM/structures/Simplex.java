package LaSPM.structures;

import java.util.*;
import java.util.stream.Collectors;


public class Simplex extends Simpl {
    
    private boolean isMaximal;
    private int label;
    private int dimension;
    private String key;


    //dummy simplex
    public Simplex(Set<Vertex> vertices, int label) {
        super(-1, vertices);
        this.label = label;
        this.dimension = vertices.size();
        this.key = computeKeyMNI(label);
    }

    //Add k-simplex
    public Simplex(int id, Set<Vertex> vertices, int label, boolean isMaximal) {
        super(id, vertices);
        this.isMaximal = isMaximal;
        this.label = label;
        this.dimension = vertices.size();
        this.key = computeKeyMNI(label);
    }
    //Add 0-simplex, using label of vertex as label of simplex.
    public Simplex(int id, Vertex v) {
        super(id);
        addVertex(v);
        this.label = v.getLabel();
        this.isMaximal = true;
        this.dimension = 1;
        this.key = computeKeyMNI(v.getLabel());
    }

    public String simplexVertexKey() {
        return this.getVertexIndices()
                .stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(" "));
    }

    public void setMaximal(boolean isMaximal) {
        this.isMaximal = isMaximal;
    }
    
    public boolean isMaximal() {
        return this.isMaximal;
    }

    public int getLabel() {
        return this.label;
    }

    public void setLabel(int label) {
        this.label = label;
    }

    public int getDimension() {
        return this.dimension;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Simplex that = (Simplex) o;
        return getVertexIndices().equals(that.getVertexIndices()) && this.label == that.label;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getVertexIndices(), getLabel());
    }

    @Override
    public Simpl copy() {
        Set<Vertex> newV = new HashSet<>();
        getVertices().forEach(v -> {
            Vertex vNew = new Vertex(v.getIndex(), v.getLabel());
            newV.add(vNew);
        });
        return new Simplex(getIndex(), newV, this.label, this.isMaximal);
    }
    
    @Override
    public String toString() {
        List<Vertex> vertices = getVertices().stream().sorted().collect(Collectors.toList());
        String simplex = vertices.toString();
        simplex += "-(" + getLabel() + ")";
        return simplex;
    }

    public String toStringIndex(){
        String simplex = getVertexIndices().toString();
        simplex += "-(" + getLabel() + ")";
        return simplex;
    }

    public String getKey(){
        return this.key;
    }

    @Override
    public boolean containsFace(Simplex ot) {
        return getVertices().containsAll(ot.getVertices());
    }
    
    public boolean containsFace(Set<Vertex> ot) {
        return getVertices().containsAll(ot);
    }

}
