package LaSPM.structures;

import com.google.common.collect.Sets;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public abstract class Simpl extends Object {
    
    private int id;
    private Set<Vertex> vertices;
    private Set<Integer> vertexIndices;
    
    public Simpl() {
        this.vertices = Sets.newHashSet();
    }
    
    public Simpl(int id) {
        this.id = id;
        this.vertices = Sets.newHashSet();
        this.vertexIndices = Sets.newHashSet();
    }
    
    public Simpl(int id, Set<Vertex> vertices) {
        this.id = id;
        this.vertices = vertices;
        this.vertexIndices = this.vertices.stream().map(Vertex::getIndex).collect(Collectors.toSet());
    }
    
    public abstract Simpl copy();
    
    public Set<Vertex> getVertices() {
        return vertices;
    }

    public Set<Integer> getVertexIndices(){
        return vertexIndices;
    }
    
    public void setVertices(Set<Vertex> vertices) {
        this.vertices = vertices;
    }
    
    public void addVertex(Vertex v) {
        vertices.add(v);
        vertexIndices.add(v.getIndex());
    }

//    public void changeVertexID(int oldIndex, int newIndex) {
//        vertexIndices.remove(oldIndex);
//        vertexIndices.add(newIndex);
//    }

    public void updateVertexIndices(){
        this.vertexIndices = this.vertices.stream().map(Vertex::getIndex).collect(Collectors.toSet());
    }
    public void changeVertex(int oldIndex, Vertex newV) {
        Vertex v = this.getVertex(oldIndex);
        this.vertices.remove(v);
        this.vertices.add(newV);
    }
    
    public void addVertices(Set<Vertex> vertices) {
        this.vertices.addAll(vertices);
        this.vertexIndices.addAll(vertices.stream().map(Vertex::getIndex).collect(Collectors.toSet()));
    }
    
    public boolean contains(Vertex v) {
        return vertices.contains(v);
    }

    public boolean contains(int v) {
        return vertexIndices.contains(v);
    }

    public Vertex getVertex(int index) {
        return vertices.stream().filter(e -> e.getIndex() == index).findFirst().orElse(null);
    }
    
    public boolean containsAll(Set<Vertex> vertices) {
        return this.vertices.containsAll(vertices);
    }

    public int getNumVertices() {
        return this.vertices.size();
    }


    public String computeKeyMNI(int slabel){
        // Sort vertices by labels
        List<Vertex> sortedVertices = this.vertices.stream()
                .sorted(Comparator.comparingInt(Vertex::getLabel))
                .toList();

        // Build key: vertex labels
        StringBuilder key = new StringBuilder();
        for (Vertex v : sortedVertices) {
            int label = v.getLabel();
            key.append(label).append(" ");
        }
        return key.toString().trim() + " - " + slabel;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIndex() {
        return id;
    }

    public void removeVertex(Vertex v){
        this.vertices.remove(v);
        this.vertexIndices.remove(v.getIndex());
    }

    public abstract boolean containsFace(Simplex ot);
    
    public abstract boolean containsFace(Set<Vertex> ot);

    
}
