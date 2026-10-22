# What belongs in Git

Keep source code, mod assets, Gradle build configuration and wrapper, required
dependency jars in `libs/`, tests in `src/test/`, reusable validation scripts,
feature specifications, and maintained documentation in version control.

| Location | Purpose | Git policy |
| --- | --- | --- |
| `src/main/` | Production Java, shaders, assets, and data | Track |
| `src/test/` | Test code, preview generators, and reusable fixtures | Track |
| `specs/<feature>/` | Specification, plan, tasks, contracts, and checklists | Track |
| `specs/<feature>/validation/` | Run results, screenshots, galleries, preview HTML, reports, metrics, and archives | Local only |
| Scripts under `specs/<feature>/validation/` | Python, PowerShell, shell, Java, and shader tools | Track; frozen source copies stay local |
| `docs/` | Maintained architecture, API, installation, and contributor documentation | Track |
| `docs/**/preview/`, `previews/`, `results/`, `captures/` | Generated documentation and visual output | Local only |
| `docs/phase2a/evidence/` | Build evidence and summaries | Local only |
| `docs/decompiled-mods/` | Extracted third-party reference material | Local only |
| `build/`, `target/`, `out/`, `.gradle/`, `.gradle-tools/` | Build output, caches, and downloaded tooling | Local only |
| `run/`, `run-data/`, `run-native-jar-visual/` | Game runtime, machine settings, saves, and logs | Local only |
| `ai-trainning/datasets/`, `ai-trainning/uselesslogs/` | Generated datasets and archived investigation output | Local only |

Logs, Python caches, task-specific `launch-t*.cmd` scripts, root gallery launchers,
and root task profiler exports are also ignored. Required dependency jars and
production textures remain tracked.

Write durable findings and instructions in `docs/` or the feature's main design
documents. Put individual run reports and preview documents in its `validation/`
directory. Put reusable test input in `src/test/resources/`; JSON, images, and
other output beneath `validation/` are ignored by default. References from older
design documents to validation evidence resolve only when that evidence exists
locally.

The cleanup removes generated files from Git's index and leaves the local copies
in place. Git shows those removals as staged deletions until they are committed.
Ignore rules prevent subsequent ordinary `git add` commands from adding them
again. They do not remove files from older commits or reduce existing Git history.

Inspect a rule with `git check-ignore -v --no-index <path>`. Review pending
tracking removals with `git diff --cached --name-status`.
