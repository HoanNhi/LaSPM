package LaSPM.structures;

import java.io.Serializable;
import java.util.Objects;

public class Vertex implements Serializable, Comparable<Vertex> {
    private int id;
    private int label;
    private int hash;

    public Vertex(Object id, Object label){
        if (id instanceof Integer && label instanceof Integer) {
            this.id = (Integer) id;
            this.label = (Integer) label;
        }
        else if (id instanceof String && label instanceof String) {
            this.id = Integer.parseInt((String) id);
            this.label = Integer.parseInt((String) label);
        }

        hash = Objects.hash(id, label);

    }

    @Override
    public boolean equals(Object o){
        if (this == o)
            return true;
        if (o==null || this.getClass() != o.getClass())
            return false;
        Vertex vertex = (Vertex) o;
        return ((vertex.label == this.label) && (vertex.id == this.id));
    }

    public int getIndex() {
        return this.id;
    }

    public void setIndex(int id) {
        this.id = id;
        this.hash = Objects.hash(this.id, label);
    }

    public int getLabel() {
        return label;
    }

    public void setLabel(int label) {
        this.label = label;
        this.hash = Objects.hash(this.id, this.label);
    }

    @Override
    public String toString() {
        return "(" + this.id + "," + this.label + ")";
    }

    @Override
    public int compareTo(Vertex o) {
        if (this.id > o.id)
            return 1;
        else if (this.id < o.id)
            return -1;
        else
            return 0;
    }

    @Override
    public int hashCode(){
        return this.hash;
    }


    public Vertex clone() {
        return new Vertex(this.id, this.label);
    }
}
