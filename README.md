# LaSPM
<u>La</u>beled <u>S</u>implicial <u>P</u>attern <u>M</u>ining in A Large Simplicial Complexes.

## Dataset Format

Each dataset is a plain-text file with a vertex section followed by a simplex
section. Blank lines are allowed. Section headers must contain `Vertex` and
`Simplex`, respectively; use `# Vertex` and `# Simplex` for clarity.

- A vertex row is `v <id> <label>`. Vertex IDs must be integer, zero-based,
  and contiguous (`0`, `1`, `2`, ...). Labels are integer values.
- A simplex row is `<vertex-id> <vertex-id> ... - <label>`. The delimiter is
  a space, hyphen, space (` - `). Vertex IDs within a simplex must be strictly
  ascending and cannot be repeated. Simplex labels are integer values and are
  independent of vertex labels.

Example:

```text
# Vertex
v 0 1
v 1 0
v 2 1
v 3 1
v 4 0
v 5 1
# Simplex
0 1 2 - 0
0 2 - 0
0 1 - 0
0 5 - 2
```
    

## Requirements

	Java JRE v17.0.18

## Usage

First, download the [dataset from Google Drive](https://drive.google.com/open?id=1aqsBOCUBf5Hkln8UeVt3mKpjDeGc3jxr&usp=drive_fs).
Then, place the downloaded files in the folder selected by `dataFolder` in
`config/laspm.properties`. Each `batch.<name>` suffix must match a dataset
filename in that folder.

### LaSPM
To recreate the main result, runs:
```bash
./scripts/run_main_batch_with_memory.sh
```
The batch runner loads `config/laspm.properties` by default. It controls the
dataset folder, output folder, rerun count, mining bounds, batch frequency
thresholds, and ablation switches. To run a single-heuristic ablation, set
exactly one `disable_*` option to `true` and the other five to `false`; set
all six options to `false` for the normal configuration. Then run:

```bash
./scripts/run_main_batch_with_memory.sh
```

Set `CONFIG_FILE` to use another properties file. The batch runner loads the
selected file before creating the complex or miner and writes ablation results
under `<outputFolder>/ablation/<mode>/`.

To run a single dataset without editing Java settings,
set `dataFolder`, `dataFile`, `outputFolder`, `minFreq`, `maxSize`, `limited`,
and `writeImageSets` at `config/laspm-main.properties`. Then run:

```bash
MAIN_CONFIG_FILE=config/laspm-main.properties TIME_LIMIT=72h ./scripts/run_main_with_timeout.sh
```

This runner invokes `LaSPM.Main`.

### LFreSCo

Use the same config file for LaSPM with:

```bash
MAIN_CONFIG_FILE=config/laspm-main.properties ./scripts/run_lfresco_main_with_timeout.sh
```
