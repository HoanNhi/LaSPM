package LaSPM.structures;

import java.util.Arrays;

/**
 * Java equivalent of the C++ CellSignature class.
 *
 * This class maintains bit-based vertex signatures for a data hypergraph
 * and a query (pattern) hypergraph, used to quickly check hyperedge
 * compatibility during sub-hypergraph matching.
 */
public class SimplexKey {

    /** Pointer to the data hypergraph (C++: DataHyperGraph*) */
    private Complex data;

    /** Pointer to the query hypergraph (C++: PatternHyperGraph*) */
    private LSimplet query;

    /**
     * Per-vertex profiles:
     * - dataProfiles[v]: bit-signature of data vertex v
     * - queryProfiles[v]: bit-signature of query vertex v
     *
     * C++ type: unsigned long long*
     */
    private long[] dataProfiles;
    private long[] queryProfiles;

    /**
     * Temporary buffers used for multiset comparison of vertex signatures
     * inside hyperedges.
     *
     * C++ type: unsigned long long*
     */
    private long[] qbit;
    private long[] dbit;

    /**
     * Constructor.
     *
     * @param data  data hypergraph
     * @param query query (pattern) hypergraph
     */
    public SimplexKey(Complex data, LSimplet query) {
        this.data = data;
        this.query = query;
        initialize();
    }

    /**
     * Allocate arrays and initialize vertex profiles.
     *
     * C++ calloc(...) → Java new long[] (zero-initialized by default).
     */
    private void initialize() {
        qbit = new long[query.getNumVertices()];
        dbit = new long[data.getNumVertices()];

        dataProfiles = new long[data.getNumVertices()];
        queryProfiles = new long[query.getNumVertices()];

        // Equivalent to: (1ull << query->GetNumHyperedges())
        long offset = 1L << query.getNumSimplices();

        // Initialize data vertex profiles with label-based offset
        for (int i = 0; i < data.getNumVertices(); i++) {
            dataProfiles[i] = offset * data.getVertex(i).getLabel();
        }

        // Initialize query vertex profiles with label-based offset
        for (int i = 0; i < query.getNumVertices(); i++) {
            queryProfiles[i] = offset * query.getVertex(i).getLabel();
        }
    }

    /**
     * Prepare the signature multiset for a query hyperedge.
     *
     * @param qe query hyperedge index
     */
    public void addQueryMapping(int qe) {
        int i = 0;
        for (int v : query.getSimplex(qe).getVertexIndices()) {
            qbit[i++] = queryProfiles[v];
        }

        // Sort only the relevant prefix (arity of the hyperedge)
        Arrays.sort(qbit, 0, query.getSimplex(qe).getNumVertices());
    }

    /**
     * Check whether a data hyperedge matches the prepared query hyperedge
     * signature.
     *
     * @param qe query hyperedge index
     * @param he data hyperedge index
     * @return true if signatures match, false otherwise
     */
    public boolean checkMapping(int qe, int he) {
        int i = 0;
        for (int v : data.getSimplex(he).getVertexIndices()) {
            dbit[i++] = dataProfiles[v];
        }

        Arrays.sort(dbit, 0, query.getSimplex(qe).getNumVertices());

        for (int j = 0; j < query.getSimplex(qe).getNumVertices(); j++) {
            if (qbit[j] != dbit[j]) {
                return false;
            }
        }
        return true;
    }

    /**
     * No-op, preserved for interface symmetry with the C++ version.
     *
     * @param qe query hyperedge index
     */
    public void removeQueryMapping(int qe) {
        // intentionally empty
    }

    /**
     * Add a mapping between a query hyperedge and a data hyperedge.
     * This updates the bit-signatures of all incident vertices.
     *
     * @param qe query hyperedge index
     * @param he data hyperedge index
     */
    public void addMapping(int qe, int he) {
        long mask = 1L << qe;

        for (int v : data.getSimplex(he).getVertexIndices()) {
            dataProfiles[v] |= mask;
        }

        for (int v : query.getSimplex(qe).getVertexIndices()) {
            queryProfiles[v] |= mask;
        }
    }

    /**
     * Remove a previously added mapping.
     *
     * Note: this mirrors the C++ logic exactly (subtraction, not bit-clear).
     *
     * @param qe query hyperedge index
     * @param he data hyperedge index
     */
    public void removeMapping(int qe, int he) {
        long mask = 1L << qe;

        for (int v : data.getSimplex(he).getVertexIndices()) {
            dataProfiles[v] -= mask;
        }

        for (int v : query.getSimplex(qe).getVertexIndices()) {
            queryProfiles[v] -= mask;
        }
    }

    public long getDataProfile(int v) {
        return this.dataProfiles[v];
    }

    public long getQueryProfile(int v) {
        return this.queryProfiles[v];
    }
}
