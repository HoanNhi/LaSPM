package LaSPM.search;

import LaSPM.structures.*;
import LaSPM.utils.Pair;
import LaSPM.utils.Settings;
import LaSPM.utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

import static LaSPM.utils.Utils.computeKeyMNI;

public class Miner {

    private int incrId; // simplets generated
    private Map<String, HashMap<String, List<LSimplet>>> examined;
    private Set<LSimplet> examinedAblation;
    private List<Pair<String, Integer>> mapImage;
    public int generated = 0;
    public int totalGen = 0;
    public int totalExamined = 0;
    public Miner() {
        this.incrId = 1;
        if (!Settings.disable_isomorphism)
            this.examined = new HashMap<>();
        else
            this.examinedAblation = new HashSet<>();
        this.mapImage = new ArrayList<>();
    }

    public List mine(Complex complex, int minFreq, int minSize, int maxSize) throws InterruptedException {
        Map<String, SupMap> supportMap = new LinkedHashMap<>(complex.getSupportMaps());
        this.totalGen += supportMap.size();
        this.totalExamined += supportMap.size();
        Iterator<Map.Entry<String, SupMap>> it = supportMap.entrySet().iterator();
        List results = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<String, SupMap> entry = it.next();

            String[] labels = entry.getKey().split(" - ")[0].split(" ");
            if (maxSize != -1 && labels.length > maxSize)
                continue;
            SupMap supMaps = entry.getValue();
            Set<Vertex> sVertices = new HashSet<>();
            for (int j = 0; j < labels.length; j++) {
                sVertices.add(new Vertex(j, Integer.parseInt(labels[j])));
            }
            int sLabel = Integer.parseInt(entry.getKey().split(" - ")[1]);
            Simplex s = new Simplex(incrId++, sVertices, sLabel, true);
            LSimplet pattern = new LSimplet(supMaps, s, complex.getMaxDim(), incrId++);
            pattern.computeFreq();
            results.add(pattern);
            results.addAll(gluing(supportMap, complex, pattern, minFreq, minSize, maxSize));
            if (Settings.writeImageSet) {
                Set<Integer> vp = pattern.getImages().values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
                for (int v : vp) {
                    mapImage.add(new Pair<>(pattern.toString(), v));
                }
            }
            it.remove();
            totalExamined += generated;
            generated = 0;
        }
        return results;
    }

    private void gluingMultiple(LSimplet pattern, int maxSize,
                                Map<String, SupMap> supportMap, List<LSimplet> extensions, int minFreq){
        Iterator<Map.Entry<String, SupMap>> it = supportMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SupMap> entry = it.next();

            String[] labels = entry.getKey().split(" - ")[0].split(" ");
            if (maxSize != -1 && labels.length > maxSize)
                continue;
            SupMap supMaps = entry.getValue();

            if (supMaps == null)
                continue;
            Set<Vertex> sVertices = new HashSet<>();
            for (int j = 0; j < labels.length; j++) {
                sVertices.add(new Vertex(j, Integer.parseInt(labels[j])));
            }

            int sLabel = Integer.parseInt(entry.getKey().split(" - ")[1]);
            Simplex s = new Simplex(pattern.getIncrId(), sVertices, sLabel, true);
            int minimumDim = Integer.min(pattern.getDimension(), s.getDimension());
            Map<Integer, Set<List<Integer>>> combinationsOfExtVertices = Utils.getCombinationsBySize(new ArrayList<>(pattern.getVertexIndices()), minimumDim);

            for (int dim = 1; dim <= minimumDim; dim++) {

                if (maxSize != -1 &&
                        (pattern.getVertices().size() + s.getVertices().size() - dim > maxSize))
                    continue;

                Set<List<Integer>> combinationOfExtVertices = combinationsOfExtVertices.get(dim);

                Map<String, List<Integer>> combinationsOfSimplexVertices = Utils.generateMultisetMap(s.getVertices(), dim);
                for (List<Integer> ExtVertices: combinationOfExtVertices) {

                    Set<Vertex> extVertices = new HashSet<>();
                    for (int extV : ExtVertices){
                        extVertices.add(pattern.getVertex(extV));
                    }
                    String mniKey = computeKeyMNI(extVertices);
                    if (!combinationsOfSimplexVertices.containsKey(mniKey)) {
                        continue;
                    }

                    List<Integer> temporaryCheckVertexExt = new ArrayList<>(ExtVertices);
                    Collections.sort(temporaryCheckVertexExt);
                    int maxVIDs = pattern.getVertices().size();
                    int toBeIncreaseID = s.getVertices().size() - dim;
                    for (int k = maxVIDs; k < maxVIDs + toBeIncreaseID; k++)
                        temporaryCheckVertexExt.add(k);
                    if (pattern.checkIfSimplexExists(temporaryCheckVertexExt.toString()))
                        continue;

                    List<Integer> SimplexVertices = combinationsOfSimplexVertices.get(mniKey);

                    // Result: (patternVertexIndex, simplexVertexIndex)
                    Set<Pair<Integer, Integer>> matching = new HashSet<>();

                    // Group simplex vertices by label (multiset behavior)
                    Map<Integer, Deque<Integer>> simplexByLabel = new HashMap<>();
                    String[] simplexLabels = mniKey.trim().split("\\s+");
                    for (int i=0; i<simplexLabels.length; i++){
                        int tempLabel = Integer.parseInt(simplexLabels[i]);
                        simplexByLabel.computeIfAbsent(tempLabel, k -> new ArrayDeque<>()).add(SimplexVertices.get(i));
                    }

                    // Iterate over pattern vertices and match greedily
                    for (Vertex pv : extVertices) {
                        Deque<Integer> candidates = simplexByLabel.get(pv.getLabel());
                        if (candidates == null || candidates.isEmpty()) {
                            // no possible match for this label → inconsistent
                            matching.clear();
                            break;
                        }

                        int sv = candidates.pollFirst();
                        matching.add(new Pair(pv.getIndex(), sv));
                    }
                    LSimplet ext = new LSimplet(pattern, incrId);
                    Simplex sCopy = (Simplex) s.copy();
                    sCopy.setId(ext.getIncrId());
                    sCopy.setMaximal(true);
                    ext.addEdge(matching, sCopy);
                    totalGen++;
                    if (!hasBeenExamined(ext)){
                        if (!Settings.disable_decomposition){
                            ext.computeImageIfAbsent(pattern.getImages(), supMaps, sCopy, minFreq);
                            if (ext.getFreq() == -1)
                                continue;
//                            //Do simplet decomposition: Iteratively try to remove an individual simplex from the simplet
//                            // and check if there exists any smaller simplet isomorphic to the new simplet. If there is,
//                            // then we can directly use the image of the smaller simplet to compute the image of the new simplet,
//                            // or to decide that the new simplet is not frequent without computing the image.
                            List<LSimplet> parents = ext.getParentPatterns();
                            boolean notFrequent = false;
                            for (LSimplet parent : parents){
                                LSimplet isomorphic = findIsomorphicParent(parent, ext.getNumVertices());
                                if (isomorphic == null)
                                    continue;
                                else{
                                    if (isomorphic.getImages().isEmpty()){
                                        ext.emptyMap(true);
                                        notFrequent = true;
                                        break;
                                    }
                                    else{
                                        Map<Integer, Integer> vertexCorrespondences =
                                                parent.computeCanonicalForm()
                                                        .getCorrespondingVertex(isomorphic.computeCanonicalForm());
                                        ext.computeIntersectImage(isomorphic.getImages(), vertexCorrespondences);
                                        for (int v : ext.getImages().keySet()){
                                            if (ext.getImageOf(v).size() < minFreq){
                                                ext.emptyMap(true);
                                                notFrequent = true;
                                                break;
                                            }
                                        }
                                        if (notFrequent)
                                            break;
                                    }
                                }
                            }
                            if (notFrequent)
                                continue;
                            else
                                extensions.add(ext);
                        }
                        else{
                            extensions.add(ext);
                        }
                        incrId++;
                    }
                }
            }
        }
    }

    private void gluingOne(LSimplet pattern, int maxSize,
                           Map<String, SupMap> supportMap, List<LSimplet> extensions, int minFreq){

        Iterator<Map.Entry<String, SupMap>> it = supportMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, SupMap> entry = it.next();

            String[] labels = entry.getKey().split(" - ")[0].split(" ");
            if (maxSize != -1 && labels.length > maxSize)
                continue;
            SupMap supMaps = entry.getValue();

            if (supMaps == null)
                continue;
            Set<Vertex> sVertices = new HashSet<>();
            for (int j = 0; j < labels.length; j++) {
                sVertices.add(new Vertex(j, Integer.parseInt(labels[j])));
            }

            int sLabel = Integer.parseInt(entry.getKey().split(" - ")[1]);
            Simplex s = new Simplex(pattern.getIncrId(), sVertices, sLabel, true);
            int minimumDim = Integer.min(pattern.getDimension(), s.getDimension());
            for (int dim = 1; dim <= minimumDim; dim++) {

                if (maxSize != -1 &&
                        (pattern.getVertices().size() + s.getVertices().size() - dim > maxSize))
                    continue;

                Map<String, List<Integer>> combinationsOfExtVertices = Utils.generateMultisetMap(pattern.getVertices(), dim);
                Map<String, List<Integer>> combinationsOfSimplexVertices = Utils.generateMultisetMap(s.getVertices(), dim);

                for (Map.Entry<String, List<Integer>> combination: combinationsOfExtVertices.entrySet()) {
                    if (!combinationsOfSimplexVertices.containsKey(combination.getKey()))
                        continue;
                    else{
                        List<Integer> ExtVertices = combination.getValue();
                        List<Integer> SimplexVertices = combinationsOfSimplexVertices.get(combination.getKey());
                        List<Integer> temporaryCheckVertexExt = new ArrayList<>(ExtVertices);
                        Collections.sort(temporaryCheckVertexExt);
                        int maxVIDs = pattern.getVertices().size();
                        int toBeIncreaseID = s.getVertices().size() - dim;
                        for (int k = maxVIDs; k < maxVIDs + toBeIncreaseID; k++)
                            temporaryCheckVertexExt.add(k);
                        if (pattern.checkIfSimplexExists(temporaryCheckVertexExt.toString()))
                            continue;

                        // Result: (patternVertexIndex, simplexVertexIndex)
                        Set<Pair<Integer, Integer>> matching = new HashSet<>();

                        for (int k = 0; k< ExtVertices.size(); k++)
                            matching.add(new Pair<>(ExtVertices.get(k), SimplexVertices.get(k)));

                        LSimplet ext = new LSimplet(pattern, incrId);
                        Simplex sCopy = (Simplex) s.copy();
                        sCopy.setId(ext.getIncrId());
                        sCopy.setMaximal(true);
                        ext.addEdge(matching, sCopy);
                        totalGen++;
                        if (!hasBeenExamined(ext)){
                            try{
                                ext.computeImageIfAbsent(pattern.getImages(), supMaps, sCopy, minFreq);
                                if (ext.getFreq() == -1)
                                    continue;
                                extensions.add(ext);
                            } catch (Exception e) {
                                System.out.println(ext);
                                throw e;
                            }
                            incrId++;
                        }
                    }
                }
            }
        }

    }

    public List gluing (Map<String, SupMap> supportMap, Complex complex, LSimplet pattern, int minFreq, int minSize, int maxSize)  {

        List<LSimplet> extensions = new ArrayList<>();
        List frequent = new ArrayList<>();

        if (pattern.getAllSimplices().size() > 1){
            gluingMultiple(pattern, maxSize, supportMap, extensions, minFreq);
        }
        else if (pattern.getAllSimplices().size() == 1){
            gluingOne(pattern, maxSize, supportMap, extensions, minFreq);
        }

        extensions.parallelStream().forEach(ext -> {
            MatchFinder match =  new MatchFinder(complex, ext, minFreq);
            match.mine();
            ext.computeFreq();
        });

        generated += extensions.size();
        extensions.forEach(ext -> {
            if (ext.getFreq() >= minFreq) {
                if (Settings.writeImageSet) {
                    Set<Integer> vp = ext.getImages().values().stream().flatMap(v -> v.stream()).collect(Collectors.toSet());
                    for (int v : vp) {
                        mapImage.add(new Pair<String, Integer>(ext.toString(), v));
                    }
                }
                // the simplet is added to the output only if the dimension > min dimension threshold
                if (ext.getDimension() >= minSize) {
                    if (Settings.limited)
                        frequent.add(ext.toString());
                    else
                        frequent.add(ext);
                }
                // the simplet is frequent, and so we extend it
                frequent.addAll(gluing(supportMap, complex, ext, minFreq, minSize, maxSize));
            }
        });
        return frequent;
    }

    private boolean hasBeenExamined(LSimplet s) {
        if (Settings.disable_isomorphism){
            if (examinedAblation.parallelStream().anyMatch(
                    other -> other.computeCanonicalForm().equals(s.computeCanonicalForm()))){
                return true;
            }
            else{
                examinedAblation.add(s);
                return false;
            }
        }
        else{
            Pair<Pair<String, String>, String> p = s.computeFingerPrint();
            String hashCodeA = p.getA().toString();
            String hashCodeB = p.getB();

            // Get or create the inner map for hashCodeA
            HashMap<String, List<LSimplet>> innerMap =
                    this.examined.computeIfAbsent(hashCodeA, k -> new HashMap<>());

            // Get or create the set for hashCodeB
            List<LSimplet> simpletSet = innerMap.computeIfAbsent(hashCodeB, k -> new ArrayList<>());

            // Now, check if the simplet already exists
            boolean alreadyExists = simpletSet.stream()
                    .anyMatch(other -> (s.getDimension() == other.getDimension()
                            && s.getNumSimplices() == other.getNumSimplices()
                            && s.getLabel().equals(other.getLabel())
                            && s.getMaxSimplexKey().equals(other.getMaxSimplexKey())
                            && s.getGraphProj().equals(other.getGraphProj())
                            && s.computeCanonicalForm().equals(other.computeCanonicalForm())
                    ));

            if (alreadyExists) {
                return true;
            }

            // Otherwise, add it
            simpletSet.add(s);
            innerMap.put(hashCodeB, simpletSet);
            examined.put(hashCodeA, innerMap);
            return false;
        }
    }

    private LSimplet findIsomorphicParent(LSimplet parent, int extNumVer) {
        if (Settings.disable_isomorphism){
            return examinedAblation.parallelStream()
                    .filter(other -> other.computeCanonicalForm().equals(parent.computeCanonicalForm()))
                    .findFirst().orElse(null);
        }
        else{
            Pair<Pair<String, String>, String> p = parent.computeFingerPrint(extNumVer);
            String hashCodeA = p.getA().toString();
            String hashCodeB = p.getB();

            // Get or create the inner map for hashCodeA
            HashMap<String, List<LSimplet>> innerMap =
                    this.examined.get(hashCodeA);
            if (innerMap == null)
                return null;

            // Get or create the set for hashCodeB
            List<LSimplet> simpletSet = innerMap.get(hashCodeB);
            if (simpletSet == null)
                return null;

            Optional<LSimplet> alreadyExists = simpletSet.stream()
                    .filter(other ->
                            parent.getDimension() == other.getDimension()
                                    && parent.getNumSimplices() == other.getNumSimplices()
                                    && parent.getLabel().equals(other.getLabel())
                                    && parent.getMaxSimplexKey().equals(other.getMaxSimplexKey())
                                    && parent.computeCanonicalForm().equals(other.computeCanonicalForm())
                    )
                    .findFirst();

            return alreadyExists.orElse(null);
        }
    }

    public List<Pair<String, Integer>> getMapImage() {
        return mapImage;
    }
}
