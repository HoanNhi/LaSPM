package LaSPM.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String originalDataFolder;
    private String originalOutputFolder;
    private int originalNumReruns;
    private int originalMinDim;
    private int originalMaxSize;
    private boolean originalLimited;
    private boolean originalWriteImageSet;
    private boolean originalAblation;
    private boolean originalDisableLocalNeighborhood;
    private boolean originalDisableDimensionAware;
    private boolean originalDisableDecomposition;
    private boolean originalDisableIsomorphism;
    private boolean originalDisableMultiset;
    private boolean originalDisableSorting;
    private Map<String, int[]> originalBatchOverrides;

    @Before
    public void saveSettings() throws ReflectiveOperationException {
        originalDataFolder = Settings.dataFolder;
        originalOutputFolder = Settings.outputFolder;
        originalNumReruns = Settings.numReruns;
        originalMinDim = Settings.minDim;
        originalMaxSize = Settings.maxSize;
        originalLimited = Settings.limited;
        originalWriteImageSet = Settings.writeImageSet;
        originalAblation = Settings.ablation;
        originalDisableLocalNeighborhood = Settings.disable_localNeighborhood;
        originalDisableDimensionAware = Settings.disable_dimensionAware;
        originalDisableDecomposition = Settings.disable_decomposition;
        originalDisableIsomorphism = Settings.disable_isomorphism;
        originalDisableMultiset = Settings.disable_multiset;
        originalDisableSorting = Settings.disable_sorting;
        originalBatchOverrides = copyBatchOverrides();
    }

    @After
    public void restoreSettings() throws ReflectiveOperationException {
        Settings.dataFolder = originalDataFolder;
        Settings.outputFolder = originalOutputFolder;
        Settings.numReruns = originalNumReruns;
        Settings.minDim = originalMinDim;
        Settings.maxSize = originalMaxSize;
        Settings.limited = originalLimited;
        Settings.writeImageSet = originalWriteImageSet;
        Settings.ablation = originalAblation;
        Settings.disable_localNeighborhood = originalDisableLocalNeighborhood;
        Settings.disable_dimensionAware = originalDisableDimensionAware;
        Settings.disable_decomposition = originalDisableDecomposition;
        Settings.disable_isomorphism = originalDisableIsomorphism;
        Settings.disable_multiset = originalDisableMultiset;
        Settings.disable_sorting = originalDisableSorting;

        Map<String, int[]> overrides = batchOverrides();
        overrides.clear();
        originalBatchOverrides.forEach((dataset, thresholds) ->
                overrides.put(dataset, thresholds.clone()));
    }

    @Test
    public void loadOverridesReadsBatchRuntimeAndMultisetSettings()
            throws IOException {
        Path config = writeConfig(
                "dataFolder=input\n"
                        + "outputFolder=results\n"
                        + "numReruns=2\n"
                        + "minDim=1\n"
                        + "maxSize=4\n"
                        + "limited=false\n"
                        + "writeImageSets=false\n"
                        + "ablation=true\n"
                        + "disable_localNeighborhood=false\n"
                        + "disable_dimensionAware=false\n"
                        + "disable_decomposition=false\n"
                        + "disable_isomorphism=false\n"
                        + "disable_multiset=true\n"
                        + "disable_sorting=false\n"
                        + "batch.custom_dataset=10,20\n");

        Settings.loadOverrides(config.toString());

        assertEquals("input/", Settings.dataFolder);
        assertEquals("results/", Settings.outputFolder);
        assertEquals(2, Settings.numReruns);
        assertEquals(1, Settings.minDim);
        assertEquals(4, Settings.maxSize);
        assertFalse(Settings.limited);
        assertFalse(Settings.writeImageSet);
        assertTrue(Settings.disable_multiset);
        assertArrayEquals(
                new int[]{10, 20},
                Settings.getBatchFrequencyOverrides().get("custom_dataset"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadOverridesRejectsZeroReruns() throws IOException {
        Settings.loadOverrides(writeConfig(
                "ablation=false\nnumReruns=0\n").toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadOverridesRejectsMultipleDisabledHeuristics()
            throws IOException {
        Settings.loadOverrides(writeConfig(
                "ablation=true\n"
                        + "numReruns=1\n"
                        + "disable_localNeighborhood=false\n"
                        + "disable_dimensionAware=false\n"
                        + "disable_decomposition=false\n"
                        + "disable_isomorphism=false\n"
                        + "disable_multiset=true\n"
                        + "disable_sorting=true\n").toString());
    }

    private Path writeConfig(String contents) throws IOException {
        Path config = temporaryFolder.newFile("laspm.properties").toPath();
        Files.writeString(config, contents, StandardCharsets.UTF_8);
        return config;
    }

    private Map<String, int[]> copyBatchOverrides()
            throws ReflectiveOperationException {
        Map<String, int[]> copy = new LinkedHashMap<>();
        batchOverrides().forEach((dataset, thresholds) ->
                copy.put(dataset, thresholds.clone()));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Map<String, int[]> batchOverrides()
            throws ReflectiveOperationException {
        Field field = Settings.class.getDeclaredField("batchFrequencyOverrides");
        field.setAccessible(true);
        return (Map<String, int[]>) field.get(null);
    }
}
