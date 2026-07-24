package LaSPM.structures;


import java.util.*;

public class SupMap implements Comparable<SupMap> {
    private int dimension;
    private int frequency;
    private int numSimplices = -1;
    private Map<Integer, Set<Integer>> labelToVertexIndex; // Label -> complex vertex with that label
    private List<Integer> simplexOfKey; // Ids of simplex in complex taking this form
    private Map<Integer, Set<Integer>> vertexToVertexNeighbours; //map vertex -> set of neighbours


    public SupMap(int dimension) {
        this.dimension = dimension;
        this.frequency = 0;
        this.labelToVertexIndex = new HashMap<>();
        this.simplexOfKey = new ArrayList<>();
        this.vertexToVertexNeighbours = new HashMap<>();

    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public void incrFrequency() {
        this.frequency++;
    }

    public Map<Integer, Set<Integer>> getLabelToVertexIndex() {
        return labelToVertexIndex;
    }

    public Set<Integer> getImgWithLabel(int label) {
        return labelToVertexIndex.get(label);
    }

    public void setLabelToVertexIndex(Map<Integer, Set<Integer>> labelToVertexIndex) {
        this.labelToVertexIndex = labelToVertexIndex;
    }

    public List<Integer> getSimplexOfKey() {
        return simplexOfKey;
    }

    public void setSimplexOfKey(List<Integer> simplexOfKey) {
        this.simplexOfKey = simplexOfKey;
    }

    public int getDim(){
        return this.dimension;
    }

    public void setDim(int dimension){
        this.dimension = dimension;
    }

    public void updateVertexToNeighbours(int vertex, Set<Integer> neighbour){
        if(vertexToVertexNeighbours.containsKey(vertex)){
            vertexToVertexNeighbours.get(vertex).addAll(neighbour);
            vertexToVertexNeighbours.get(vertex).remove(vertex);
        }else{
            Set<Integer> neighbourSet = new HashSet<>(neighbour);
            vertexToVertexNeighbours.put(vertex, neighbourSet);
            vertexToVertexNeighbours.get(vertex).remove(vertex);
        }
    }

    public Set<Integer> getVertexToNeighbours(int vertex){
        return vertexToVertexNeighbours.getOrDefault(vertex, Collections.emptySet());
    }

    public int getNumSimplices() {
        if (numSimplices == -1)
            numSimplices = this.simplexOfKey.size();
        return this.numSimplices;
    }
    @Override
    public int compareTo(SupMap other){
        if (other.getFrequency() > this.getFrequency())
            return -1;
        else if (other.getFrequency() < this.getFrequency())
            return 1;
        else if (other.getFrequency() == this.getFrequency())
            return 0;

        return 0;
    }
}

