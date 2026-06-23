package net.Gabou.projectatmosphere.clouds.field;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Writes the standalone Cloud Formation Lab.
 *
 * <p>The generated browser app intentionally avoids Minecraft startup. It
 * previews forecast-driven, persistent CloudField evolution using placeholder
 * Canvas debug visuals.</p>
 */
public final class CloudFieldSandbox {
    private CloudFieldSandbox() {
    }

    public static void main(String[] args) throws IOException {
        Path output = Path.of("build", "cloud-field-sandbox", "cloud-formation-lab.html");
        Files.createDirectories(output.getParent());
        Files.writeString(output, html(), StandardCharsets.UTF_8);
        copyShaderFiles(output.getParent());
        System.out.println("Cloud Formation Lab wrote " + output.toAbsolutePath().normalize());
        System.out.println("Open the HTML file directly in a browser. Minecraft is not launched.");
    }

    private static void copyShaderFiles(Path outputDirectory) throws IOException {
        Path sourceDirectory = Path.of("src", "test", "resources", "cloud-formation-lab", "shaders");
        if (!Files.isDirectory(sourceDirectory)) {
            return;
        }
        Path targetDirectory = outputDirectory.resolve("shaders");
        Files.createDirectories(targetDirectory);
        try (Stream<Path> files = Files.walk(sourceDirectory)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path target = targetDirectory.resolve(sourceDirectory.relativize(source));
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String html() {
        return String.join("", """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Project Atmosphere Cloud Formation Lab</title>
                  <style>
                    :root {
                      color-scheme: dark;
                      --bg: #101317;
                      --panel: #171d23;
                      --panel-2: #202832;
                      --line: #34404d;
                      --text: #e7edf3;
                      --muted: #9aa8b6;
                      --cyan: #5eead4;
                      --blue: #93c5fd;
                      --amber: #fbbf24;
                      --red: #fb7185;
                      --green: #86efac;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      background: var(--bg);
                      color: var(--text);
                      font: 13px/1.35 system-ui, Segoe UI, sans-serif;
                    }
                    main {
                      min-height: 100vh;
                      display: grid;
                      grid-template-columns: minmax(440px, 1fr) 430px;
                    }
                    .stage {
                      min-width: 0;
                      display: grid;
                      grid-template-rows: minmax(340px, 1.35fr) minmax(230px, 1fr) minmax(170px, 0.72fr);
                      border-right: 1px solid var(--line);
                    }
                    .viewport {
                      position: relative;
                      min-width: 0;
                      min-height: 0;
                      background: #0f1419;
                    }
                    .gl-viewport {
                      min-height: 230px;
                      border-top: 1px solid var(--line);
                      border-bottom: 1px solid var(--line);
                    }
                    .hidden { display: none; }
                    canvas {
                      width: 100%;
                      height: 100%;
                      display: block;
                    }
                    .hud {
                      position: absolute;
                      left: 14px;
                      top: 12px;
                      display: flex;
                      gap: 8px;
                      flex-wrap: wrap;
                      pointer-events: none;
                    }
                    .pill {
                      background: rgba(16, 19, 23, 0.84);
                      border: 1px solid rgba(154, 168, 182, 0.28);
                      border-radius: 6px;
                      padding: 5px 8px;
                      color: var(--muted);
                    }
                    aside {
                      min-width: 0;
                      background: var(--panel);
                      overflow-y: auto;
                      max-height: 100vh;
                    }
                    .controls {
                      padding: 14px;
                      display: grid;
                      gap: 12px;
                    }
                    h1, h2 {
                      margin: 0;
                      font-weight: 650;
                      letter-spacing: 0;
                    }
                    h1 { font-size: 18px; }
                    h2 { font-size: 13px; color: var(--muted); }
                    .section {
                      background: var(--panel-2);
                      border: 1px solid var(--line);
                      border-radius: 7px;
                      padding: 11px;
                      display: grid;
                      gap: 9px;
                    }
                    .row {
                      display: grid;
                      grid-template-columns: 126px minmax(92px, 1fr) 70px;
                      gap: 8px;
                      align-items: center;
                    }
                    .row.wide {
                      grid-template-columns: 142px minmax(0, 1fr);
                    }
                    .toggle-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 7px;
                    }
                    .toggle-grid label {
                      display: flex;
                      gap: 7px;
                      align-items: center;
                      background: #11171d;
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 7px;
                    }
                    label { color: var(--muted); }
                    input[type="range"] { width: 100%; }
                    input[type="number"], select {
                      width: 100%;
                      background: #11171d;
                      color: var(--text);
                      border: 1px solid var(--line);
                      border-radius: 5px;
                      padding: 5px 6px;
                    }
                    button {
                      background: #26313c;
                      color: var(--text);
                      border: 1px solid #405063;
                      border-radius: 6px;
                      padding: 7px 9px;
                      cursor: pointer;
                    }
                    button:hover { background: #304052; }
                    .button-grid {
                      display: grid;
                      grid-template-columns: repeat(3, minmax(0, 1fr));
                      gap: 8px;
                    }
                    .metrics {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 8px;
                    }
                    .metric, .detail, .breakdown {
                      background: #11171d;
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 8px;
                    }
                    .metric strong {
                      display: block;
                      font-size: 17px;
                    }
                    .metric span, .detail span, .breakdown span { color: var(--muted); }
                    .kv {
                      display: grid;
                      grid-template-columns: minmax(120px, 1fr) minmax(0, 1.1fr);
                      gap: 5px 10px;
                      margin-top: 4px;
                    }
                    .bar {
                      height: 7px;
                      background: #0d1217;
                      border: 1px solid #2d3945;
                      border-radius: 999px;
                      overflow: hidden;
                    }
                    .bar i {
                      display: block;
                      height: 100%;
                      background: var(--cyan);
                    }
                    .help {
                      color: var(--muted);
                      background: #11171d;
                      border: 1px solid var(--line);
                      border-radius: 6px;
                      padding: 8px;
                    }
                    table {
                      width: 100%;
                      border-collapse: collapse;
                      color: var(--muted);
                    }
                    th, td {
                      border-bottom: 1px solid var(--line);
                      padding: 5px 4px;
                      text-align: left;
                      white-space: nowrap;
                    }
                    th { color: var(--text); font-weight: 600; }
                    tr.selected { color: var(--text); background: rgba(94, 234, 212, 0.08); }
                    .legend {
                      display: flex;
                      gap: 10px;
                      flex-wrap: wrap;
                      color: var(--muted);
                    }
                    .dot {
                      display: inline-block;
                      width: 9px;
                      height: 9px;
                      border-radius: 50%;
                      margin-right: 5px;
                    }
                    @media (max-width: 960px) {
                      main { grid-template-columns: 1fr; }
                      aside { max-height: none; }
                      .stage { border-right: 0; grid-template-rows: 420px 280px 240px; }
                    }
                  </style>
                </head>
                <body>
                  <main>
                    <section class="stage">
                      <div class="viewport">
                        <canvas id="mapCanvas"></canvas>
                        <div class="hud" id="mapHud"></div>
                      </div>
                      <div class="viewport gl-viewport">
                        <canvas id="glPreviewCanvas"></canvas>
                        <div class="hud" id="glHud">
                          <span class="pill">WebGL preview: selected persistent CloudField uniforms</span>
                        </div>
                      </div>
                      <div class="viewport" id="profilePane">
                        <canvas id="profileCanvas"></canvas>
                        <div class="hud"><span class="pill">Side profile: base/top Y, maturity, current -> target state</span></div>
                      </div>
                    </section>
                    <aside>
                      <div class="controls">
                        <div>
                          <h1>Cloud Formation Lab</h1>
                          <h2>Forecast targets -> persistent CloudField state -> evolution controller -> preview snapshots</h2>
                        </div>

                        <section class="section">
                          <h2>Preset</h2>
                          <select id="preset"></select>
                          <div class="button-grid">
                            <button id="applyPreset">Apply Preset</button>
                            <button id="reset">Reset</button>
                            <button id="regenSeed">Regenerate Seed</button>
                          </div>
                        </section>

                        """, """
                        <section class="section" id="parameterControls">
                          <h2>Forecast Parameters</h2>
                        </section>

                        <section class="section">
                          <h2>Progression</h2>
                          <div class="row">
                            <label for="timeScale">Time scale</label>
                            <select id="timeScale">
                              <option value="1">realtime</option>
                              <option value="10">10x</option>
                              <option value="100">100x</option>
                              <option value="1000">1000x</option>
                            </select>
                            <span></span>
                          </div>
                          <div class="button-grid">
                            <button id="playPause">Play</button>
                            <button id="stepHour">Step 1 Hour</button>
                            <button id="rebuildTargets">Refresh Targets</button>
                          </div>
                        </section>

                        <section class="section">
                          <h2>Display</h2>
                          <div class="toggle-grid">
                            <label><input type="checkbox" id="showRings" checked> Show field rings</label>
                            <label><input type="checkbox" id="showCloudlets"> Show cloudlet dots</label>
                            <label><input type="checkbox" id="showWind" checked> Show wind arrows</label>
                            <label><input type="checkbox" id="showLabels" checked> Show labels</label>
                            <label><input type="checkbox" id="showProfile" checked> Show side profile</label>
                          </div>
                        </section>

                        """, """
                        <section class="section">
                          <h2>GLSL Preview</h2>
                          <div class="row wide">
                            <label for="previewFieldMode">Preview mode</label>
                            <select id="previewFieldMode">
                              <option value="selected">Selected CloudField only</option>
                              <option value="all">All visible CloudFields</option>
                              <option value="synthetic">Single synthetic field from forecast</option>
                            </select>
                          </div>
                          <div class="row wide">
                            <label for="previewRenderMode">Render mode</label>
                            <select id="previewRenderMode">
                              <option value="shaded">Final shaded preview</option>
                              <option value="density">Density only</option>
                            </select>
                          </div>
                          <div class="row">
                            <label for="previewDensity">Density multiplier</label>
                            <input id="previewDensity" type="range" min="0" max="3" step="0.05" value="1">
                            <input id="previewDensityNumber" type="number" min="0" max="3" step="0.05" value="1">
                          </div>
                          <div class="row">
                            <label for="previewCoverage">Coverage multiplier</label>
                            <input id="previewCoverage" type="range" min="0" max="3" step="0.05" value="1">
                            <input id="previewCoverageNumber" type="number" min="0" max="3" step="0.05" value="1">
                          </div>
                          <div class="row">
                            <label for="previewHydration">Hydration multiplier</label>
                            <input id="previewHydration" type="range" min="0" max="3" step="0.05" value="1">
                            <input id="previewHydrationNumber" type="number" min="0" max="3" step="0.05" value="1">
                          </div>
                          <div class="row">
                            <label for="previewLighting">Lighting strength</label>
                            <input id="previewLighting" type="range" min="0" max="2" step="0.05" value="1">
                            <input id="previewLightingNumber" type="number" min="0" max="2" step="0.05" value="1">
                          </div>
                          <div class="row">
                            <label for="previewSpeed">Preview speed</label>
                            <input id="previewSpeed" type="range" min="0" max="4" step="0.05" value="1">
                            <input id="previewSpeedNumber" type="number" min="0" max="4" step="0.05" value="1">
                          </div>
                          <div class="toggle-grid">
                            <label><input type="checkbox" id="previewRenderEnabled" checked> Render WebGL</label>
                            <label><input type="checkbox" id="previewShowBounds" checked> Show bounds</label>
                            <label><input type="checkbox" id="previewPlaying" checked> Play preview</label>
                          </div>
                          <input id="previewShaderFiles" type="file" multiple accept=".vert,.frag,.glsl,.txt">
                          <div class="button-grid">
                            <button id="previewResetTime">Reset Time</button>
                            <button id="previewResetCamera">Reset Camera</button>
                            <button id="previewRecompile">Recompile Shader</button>
                          </div>
                        </section>

                        <section class="section">
                          <h2>Spawn / Region Simulation</h2>
                          <div class="toggle-grid">
                            <label><input type="checkbox" id="regionAllowsClouds" checked> Region allows clouds</label>
                          </div>
                          <div class="row">
                            <label for="regionMoisture">Biome moisture</label>
                            <input id="regionMoisture" type="range" min="0" max="1" step="0.01" value="0.55">
                            <input id="regionMoistureNumber" type="number" min="0" max="1" step="0.01" value="0.55">
                          </div>
                          <div class="row">
                            <label for="terrainLift">Terrain lift</label>
                            <input id="terrainLift" type="range" min="0" max="1" step="0.01" value="0.20">
                            <input id="terrainLiftNumber" type="number" min="0" max="1" step="0.01" value="0.20">
                          </div>
                          <div class="row">
                            <label for="frontConvergence">Front convergence</label>
                            <input id="frontConvergence" type="range" min="0" max="1" step="0.01" value="0.15">
                            <input id="frontConvergenceNumber" type="number" min="0" max="1" step="0.01" value="0.15">
                          </div>
                          <div class="row">
                            <label for="spawnSuppression">Spawn suppression</label>
                            <input id="spawnSuppression" type="range" min="0" max="1" step="0.01" value="0">
                            <input id="spawnSuppressionNumber" type="number" min="0" max="1" step="0.01" value="0">
                          </div>
                        </section>

                        <section class="section">
                          <h2>Result</h2>
                          <div class="metrics" id="metrics"></div>
                          <div class="legend">
                            <span><i class="dot" style="background: var(--cyan)"></i>dynamic</span>
                            <span><i class="dot" style="background: var(--blue)"></i>transition</span>
                            <span><i class="dot" style="background: var(--amber)"></i>far procedural</span>
                            <span><i class="dot" style="background: var(--red)"></i>storm/decay risk</span>
                          </div>
                        </section>

                        <section class="section">
                          <h2>Selected CloudField</h2>
                          <div id="selectedDetails" class="detail"></div>
                        </section>

                        <section class="section">
                          <h2>Forecast Influence</h2>
                          <div id="influenceBreakdown" class="breakdown"></div>
                        </section>

                        <section class="section">
                          <h2>CloudFields</h2>
                          <table>
                            <thead><tr><th>#</th><th>kind</th><th>r</th><th>dens</th><th>cov</th><th>cloudlets</th><th>lod</th></tr></thead>
                            <tbody id="fieldTable"></tbody>
                          </table>
                        </section>

                        <section class="section">
                          <h2>Help</h2>
                          <div class="help">
                            2D map = formation/debug view. Dots = deterministic cloudlet markers, not final rendered clouds.
                            WebGL preview = miniature live shader viewport driven by the selected persistent CloudField.
                            Shader files live beside this HTML under shaders/ and can also be imported with the file picker.
                            Forecast sliders and region spawning controls change CloudField targets; the preview follows current evolved state. This is not Minecraft rendering yet.
                          </div>
                        </section>
                      </div>
                    </aside>
                  </main>

                  """, """
                  <script>
                    class ForecastParameterState {
                      constructor(values = {}) {
                        Object.assign(this, {
                          temperature: 18,
                          humidity: 68,
                          pressure: 1008,
                          windSpeed: 16,
                          windDirection: 240,
                          cloudCover: 48,
                          rainIntensity: 0.18,
                          stormChance: 0.22,
                          instability: 0.38,
                          verticalDevelopment: 0.42,
                          baseY: 118,
                          topY: 205,
                          forecastHour: 13,
                          regionAllowsClouds: true,
                          regionMoisture: 0.55,
                          terrainLift: 0.20,
                          frontConvergence: 0.15,
                          spawnSuppression: 0.0,
                          seed: 47291
                        }, values);
                      }
                      clone() { return new ForecastParameterState({ ...this }); }
                    }

                    class CloudFormationPreset {
                      constructor(id, label, values) {
                        this.id = id;
                        this.label = label;
                        this.values = values;
                      }
                    }

                    class CloudFieldFormationTarget {
                      constructor(values) { Object.assign(this, values); }
                    }

                    class PersistentCloudFieldState {
                      constructor(target, createdAtHour) {
                        this.fieldId = target.fieldId;
                        this.seed = target.seed;
                        this.center = { ...target.center };
                        this.previousCenter = { ...target.center };
                        this.velocity = { ...target.windVector };
                        this.radius = Math.max(8, target.radius * 0.35);
                        this.density = target.density * 0.35;
                        this.coverage = target.coverage * 0.35;
                        this.baseY = target.baseY;
                        this.topY = target.baseY + Math.max(8, (target.topY - target.baseY) * 0.35);
                        this.growth = target.growth;
                        this.decay = target.decay;
                        this.stormPotential = target.stormPotential * 0.22;
                        this.verticalDevelopment = target.verticalDevelopment * 0.35;
                        this.maturity = 0;
                        this.ageHours = 0;
                        this.lifetimeHours = target.lifetimeHours;
                        this.kind = target.kind;
                        this.targetKind = target.kind;
                        this.target = target;
                        this.retiring = false;
                        this.createdAtHour = createdAtHour;
                      }
                    }

                    class CloudFormationResult {
                      constructor(targets, fields, snapshots, summary) {
                        this.targets = targets;
                        this.fields = fields;
                        this.snapshots = snapshots;
                        this.summary = summary;
                      }
                    }

                    """, """
                    class CloudFormationTargetModel {
                      evaluate(params) {
                        const rng = mulberry32((params.seed | 0) + 913);
                        const influence = computeInfluence(params);
                        const stableLayer = influence.stabilityLayeringScore > 0.58;
                        const formationStrength = clamp01(
                          influence.humidityContribution * 0.30 +
                          influence.cloudCoverContribution * 0.25 +
                          influence.instabilityContribution * 0.18 +
                          influence.lowPressureContribution * 0.14 +
                          influence.rainStormContribution * 0.16 +
                          influence.regionMoistureContribution * 0.12 +
                          influence.terrainLiftContribution * 0.08 +
                          influence.frontConvergenceContribution * 0.10 -
                          influence.drynessPenalty * 0.38 -
                          influence.spawnSuppressionPenalty * 0.55
                        );
                        const count = !params.regionAllowsClouds ? 0 : influence.drynessPenalty > 0.72
                          ? Math.max(0, Math.round(formationStrength * 3))
                          : clamp(Math.round((stableLayer ? 2 : 1) + formationStrength * (stableLayer ? 4 : 7)), 0, 9);
                        const targets = [];
                        const wind = windVector(params.windSpeed, params.windDirection);

                        for (let i = 0; i < count; i++) {
                          const slot = i / Math.max(1, count);
                          const slotSeed = params.seed + i * 1009;
                          const srng = mulberry32(slotSeed);
                          const angle = slot * Math.PI * 2 + srng() * 0.55 + params.windDirection * Math.PI / 720;
                          const ring = stableLayer ? lerp(120, 330, srng()) : lerp(80, 390, srng());
                          const center = {
                            x: Math.cos(angle) * ring + (srng() - 0.5) * 70,
                            y: lerp(params.baseY, params.topY, 0.42),
                            z: Math.sin(angle) * ring + (srng() - 0.5) * 70
                          };
                          const kind = classifyKind(params, influence, stableLayer);
                          const vertical = clamp01(params.verticalDevelopment * 0.45 + influence.instabilityContribution * 0.34 + influence.lowPressureContribution * 0.12 + influence.rainStormContribution * 0.09);
                          const storm = clamp01(influence.rainStormContribution * 0.55 + influence.lowPressureContribution * 0.25 + influence.instabilityContribution * 0.20);
                          const density = clamp01(influence.humidityContribution * 0.42 + influence.cloudCoverContribution * 0.22 + storm * 0.22 + params.rainIntensity * 0.12 - influence.drynessPenalty * 0.32);
                          const coverage = clamp01(influence.cloudCoverContribution * 0.55 + influence.humidityContribution * 0.25 + (stableLayer ? 0.18 : 0.0) - influence.drynessPenalty * 0.22);
                          const growth = clamp01(influence.humidityContribution * 0.22 + influence.instabilityContribution * 0.28 + params.verticalDevelopment * 0.24 + influence.lowPressureContribution * 0.16 - influence.drynessPenalty * 0.22);
                          const decay = clamp01(influence.drynessPenalty * 0.62 + Math.max(0, params.pressure - 1018) / 60 + (1 - influence.cloudCoverContribution) * 0.08);
                          const baseRadius = stableLayer ? lerp(190, 355, coverage) : lerp(78, 220, formationStrength);
                          const radius = baseRadius * lerp(0.82, 1.18, srng());
                          const topY = Math.max(params.baseY + 10, params.topY + vertical * 80 + storm * 45 - influence.drynessPenalty * 35);
                          const cloudletCount = Math.round(clamp(coverage * density * radius * (stableLayer ? 0.36 : 0.68), 0, 190));
                          targets.push(new CloudFieldFormationTarget({
                            fieldId: stableId(params.seed, i),
                            seed: slotSeed,
                            targetIndex: i,
                            sourceId: 'forecast-target-' + i,
                            sourceType: stableLayer ? 'FORECAST_LAYER_TARGET' : 'FORECAST_CELL_TARGET',
                            kind,
                            center,
                            radius,
                            density,
                            coverage,
                            baseY: params.baseY,
                            topY,
                            growth,
                            decay,
                            stormPotential: storm,
                            verticalDevelopment: vertical,
                            cloudletCount,
                            windVector: wind,
                            lifetimeHours: lerp(5, 18, coverage) + storm * 10,
                            influence: { ...influence }
                          }));
                        }
                        return targets;
                      }
                    }

                    class CloudFieldEvolutionController {
                      constructor() {
                        this.fields = [];
                      }

                      reset(targets, forecastHour) {
                        this.fields = targets.map(target => new PersistentCloudFieldState(target, forecastHour));
                      }

                      applyTargets(targets, forecastHour) {
                        const byId = new Map(this.fields.map(field => [field.fieldId, field]));
                        const targetIds = new Set();
                        for (const target of targets) {
                          targetIds.add(target.fieldId);
                          const existing = byId.get(target.fieldId);
                          if (existing) {
                            existing.target = target;
                            existing.targetKind = target.kind;
                            existing.retiring = false;
                          } else {
                            this.fields.push(new PersistentCloudFieldState(target, forecastHour));
                          }
                        }
                        for (const field of this.fields) {
                          if (!targetIds.has(field.fieldId)) {
                            field.retiring = true;
                            field.target = {
                              ...field.target,
                              density: 0,
                              coverage: 0,
                              growth: 0,
                              decay: Math.max(field.decay, 0.75),
                              stormPotential: 0,
                              verticalDevelopment: 0,
                              cloudletCount: 0
                            };
                          }
                        }
                      }

                      tick(deltaHours, params) {
                        const dt = Math.max(0, Math.min(0.35, deltaHours));
                        if (dt <= 0) return;
                        for (const field of this.fields) {
                          const target = field.target;
                          field.ageHours += dt;
                          field.previousCenter = { ...field.center };
                          field.velocity = approachVec(field.velocity, target.windVector, rateFor('wind', target, params), dt);
                          field.center = addVec(field.center, scaleVec(field.velocity, dt * 35));
                          field.radius = approach(field.radius, target.radius, rateFor('radius', target, params), dt);
                          field.density = approach(field.density, target.density, rateFor('density', target, params), dt);
                          field.coverage = approach(field.coverage, target.coverage, rateFor('coverage', target, params), dt);
                          field.baseY = approach(field.baseY, target.baseY, 0.48, dt);
                          field.topY = approach(field.topY, target.topY, rateFor('vertical', target, params), dt);
                          field.growth = approach(field.growth, target.growth, 0.42, dt);
                          field.decay = approach(field.decay, target.decay, target.decay > 0.72 ? 0.48 : 0.20, dt);
                          field.stormPotential = approach(field.stormPotential, target.stormPotential, rateFor('storm', target, params), dt);
                          field.verticalDevelopment = approach(field.verticalDevelopment, target.verticalDevelopment, rateFor('vertical', target, params), dt);
                          const maturityTarget = field.retiring ? 0 : clamp01((field.density + field.coverage + field.verticalDevelopment) / 3);
                          field.maturity = approach(field.maturity, maturityTarget, field.retiring ? 0.42 : rateFor('maturity', target, params), dt);
                          if (kindCanChange(field, target)) {
                            field.kind = target.kind;
                          }
                        }
                        this.fields = this.fields.filter(field => !(field.retiring && field.maturity < 0.02 && field.density < 0.02 && field.coverage < 0.02));
                      }
                    }

                    class CloudFieldPreviewSnapshotFactory {
                      static create(fields) {
                        const camera = { x: 0, y: 130, z: 0 };
                        return fields.map(field => {
                          const target = field.target;
                          const distance = Math.hypot(field.center.x - camera.x, field.center.z - camera.z);
                          const lodBand = distance < 260 ? 'DYNAMIC' : distance < 470 ? 'TRANSITION' : distance < 680 ? 'FAR_PROCEDURAL' : 'HAZE';
                          const hydrationProgress = clamp01(field.target.influence.humidityContribution * 0.42 + field.coverage * 0.28 + field.growth * 0.18 - field.decay * 0.24);
                          const hydrationState = hydrationProgress > 0.82 ? 'HYDRATED' : hydrationProgress > 0.48 ? 'HYDRATING' : field.decay > field.growth ? 'DEHYDRATING' : 'NOT_HYDRATED';
                          const lodFraction = lodBand === 'DYNAMIC' ? 1 : lodBand === 'TRANSITION' ? 0.55 : 0;
                          const targetCloudletCount = Math.round(target.cloudletCount * Math.max(0.05, field.maturity));
                          const activeCloudletCount = Math.round(targetCloudletCount * hydrationProgress * lodFraction);
                          return {
                            ...field,
                            distance,
                            lodBand,
                            hydrationProgress,
                            hydrationState,
                            targetCloudletCount,
                            activeCloudletCount,
                            effectiveDensity: clamp01(field.density * (1 - field.decay * 0.28)),
                            effectiveCoverage: clamp01(field.coverage * (1 - field.decay * 0.22)),
                            target
                          };
                        });
                      }
                    }

                    class CloudFieldPreviewStore {
                      constructor() {
                        this.params = new ForecastParameterState();
                        this.targetModel = new CloudFormationTargetModel();
                        this.evolution = new CloudFieldEvolutionController();
                        this.targets = this.targetModel.evaluate(this.params);
                        this.evolution.reset(this.targets, this.params.forecastHour);
                        this.evolution.tick(0.2, this.params);
                        this.snapshots = CloudFieldPreviewSnapshotFactory.create(this.evolution.fields);
                        this.playing = false;
                        this.lastFrame = 0;
                        this.selectedId = this.snapshots[0]?.fieldId || null;
                        this.timeScale = 1;
                        this.display = {
                          rings: true,
                          cloudlets: false,
                          wind: true,
                          labels: true,
                          profile: true
                        };
                        this.preview = {
                          fieldMode: 'selected',
                          renderMode: 'shaded',
                          densityMultiplier: 1,
                          coverageMultiplier: 1,
                          hydrationMultiplier: 1,
                          lightingStrength: 1,
                          speed: 1,
                          rendering: true,
                          showBounds: true,
                          playing: true,
                          time: 0
                        };
                      }
                      refreshTargets() {
                        this.targets = this.targetModel.evaluate(this.params);
                        this.evolution.applyTargets(this.targets, this.params.forecastHour);
                        this.syncSnapshots();
                      }
                      setParams(patch) {
                        Object.assign(this.params, patch);
                        this.refreshTargets();
                      }
                      applyPreset(preset) {
                        this.params = new ForecastParameterState({ ...preset.values, seed: this.params.seed });
                        this.refreshTargets();
                      }
                      reset() {
                        this.params = new ForecastParameterState();
                        this.targets = this.targetModel.evaluate(this.params);
                        this.evolution.reset(this.targets, this.params.forecastHour);
                        this.evolution.tick(0.2, this.params);
                        this.syncSnapshots();
                        this.selectedId = this.snapshots[0]?.fieldId || null;
                      }
                      step(hours) {
                        this.params.forecastHour = wrap24(this.params.forecastHour + hours);
                        this.refreshTargets();
                        this.evolution.tick(hours, this.params);
                        this.syncSnapshots();
                      }
                      regenerateSeed() {
                        this.params.seed = Math.floor(Math.random() * 999999);
                        this.targets = this.targetModel.evaluate(this.params);
                        this.evolution.reset(this.targets, this.params.forecastHour);
                        this.evolution.tick(0.2, this.params);
                        this.syncSnapshots();
                        this.selectedId = this.snapshots[0]?.fieldId || null;
                      }
                      tick(deltaHours) {
                        this.params.forecastHour = wrap24(this.params.forecastHour + deltaHours);
                        this.refreshTargets();
                        this.evolution.tick(deltaHours, this.params);
                        this.syncSnapshots();
                      }
                      syncSnapshots() {
                        this.snapshots = CloudFieldPreviewSnapshotFactory.create(this.evolution.fields);
                        if (!this.snapshots.some(field => field.fieldId === this.selectedId)) {
                          this.selectedId = this.snapshots[0]?.fieldId || null;
                        }
                      }
                      selectedField() {
                        return this.snapshots.find(field => field.fieldId === this.selectedId) || this.snapshots[0] || null;
                      }
                      summary() {
                        const count = this.snapshots.length || 1;
                        return {
                          fields: this.snapshots.length,
                          cloudlets: this.snapshots.reduce((sum, f) => sum + f.activeCloudletCount, 0),
                          avgDensity: this.snapshots.reduce((sum, f) => sum + f.effectiveDensity, 0) / count,
                          stormPotential: this.snapshots.reduce((sum, f) => sum + f.stormPotential, 0) / count
                        };
                      }
                    }

                    """, """
                    class CloudPreviewShaderSource {
                      static vertexOverride = null;
                      static fragmentOverride = null;

                      static async loadExternal() {
                        const common = await this.tryFetchText('shaders/cloud_preview_common.glsl');
                        const density = await this.tryFetchText('shaders/cloud_preview_density.glsl');
                        const vertex = await this.tryFetchText('shaders/cloud_preview.vert');
                        const fragment = await this.tryFetchText('shaders/cloud_preview.frag');
                        if (!vertex || !fragment) return false;
                        const includeMap = {
                          'cloud_preview_common.glsl': common || '',
                          'cloud_preview_density.glsl': density || ''
                        };
                        this.vertexOverride = vertex;
                        this.fragmentOverride = this.preprocess(fragment, includeMap);
                        return true;
                      }

                      static async tryFetchText(path) {
                        try {
                          const response = await fetch(path, { cache: 'no-store' });
                          return response.ok ? await response.text() : null;
                        } catch (error) {
                          return null;
                        }
                      }

                      static async importFiles(fileList) {
                        const files = Array.from(fileList || []);
                        const byName = new Map();
                        for (const file of files) {
                          byName.set(file.name, await file.text());
                        }
                        const vertex = byName.get('cloud_preview.vert') || byName.get('preview.vert') || byName.get('cloud.vert');
                        const fragment = byName.get('cloud_preview.frag') || byName.get('preview.frag') || byName.get('cloud.frag');
                        if (!vertex || !fragment) {
                          throw new Error('Select at least cloud_preview.vert and cloud_preview.frag');
                        }
                        this.vertexOverride = vertex;
                        this.fragmentOverride = this.preprocess(fragment, Object.fromEntries(byName));
                      }

                      static preprocess(source, includeMap) {
                        return source.replace(/^\\s*#include\\s+[\"<]([^\">]+)[\">]\\s*$/gm, (ignored, name) => includeMap[name] || '');
                      }

                      static vertex() {
                        if (this.vertexOverride) return this.vertexOverride;
                        return `
                          attribute vec2 aPosition;
                          varying vec2 vUv;
                          void main() {
                            vUv = aPosition * 0.5 + 0.5;
                            gl_Position = vec4(aPosition, 0.0, 1.0);
                          }
                        `;
                      }

                      static fragment() {
                        if (this.fragmentOverride) return this.fragmentOverride;
                        return `
                          precision highp float;
                          #define MAX_FIELDS 8
                          varying vec2 vUv;
                          uniform vec2 uResolution;
                          uniform float uTime;
                          uniform vec3 uCameraPos;
                          uniform vec3 uCameraTarget;
                          uniform vec3 uSunDirection;
                          uniform int uFieldCount;
                          uniform vec3 uFieldCenter[MAX_FIELDS];
                          uniform float uFieldRadius[MAX_FIELDS];
                          uniform float uFieldBaseY[MAX_FIELDS];
                          uniform float uFieldTopY[MAX_FIELDS];
                          uniform float uFieldDensity[MAX_FIELDS];
                          uniform float uFieldCoverage[MAX_FIELDS];
                          uniform float uFieldGrowth[MAX_FIELDS];
                          uniform float uFieldDecay[MAX_FIELDS];
                          uniform float uFieldHumidityInfluence[MAX_FIELDS];
                          uniform vec3 uFieldWind[MAX_FIELDS];
                          uniform float uFieldVerticalDevelopment[MAX_FIELDS];
                          uniform float uFieldStormPotential[MAX_FIELDS];
                          uniform float uFieldSeed[MAX_FIELDS];
                          uniform float uFieldHydration[MAX_FIELDS];
                          uniform float uFieldAge[MAX_FIELDS];
                          uniform float uFieldCloudletCount[MAX_FIELDS];
                          uniform float uDensityMultiplier;
                          uniform float uCoverageMultiplier;
                          uniform float uHydrationMultiplier;
                          uniform float uLightingStrength;
                          uniform int uDensityMode;
                          uniform int uShowBounds;
                          uniform float uForecastTemperature;
                          uniform float uForecastHumidity;
                          uniform float uForecastPressure;
                          uniform float uForecastWindSpeed;
                          uniform float uForecastWindDirection;
                          uniform float uForecastCloudCover;
                          uniform float uForecastRainIntensity;
                          uniform float uForecastStormChance;
                          uniform float uForecastInstability;
                          uniform float uForecastVerticalDevelopment;
                          uniform float uForecastBaseY;
                          uniform float uForecastTopY;
                          uniform float uForecastHour;
                          uniform float uForecastSeed;
                          uniform float uRegionAllowsClouds;
                          uniform float uRegionMoisture;
                          uniform float uTerrainLift;
                          uniform float uFrontConvergence;
                          uniform float uSpawnSuppression;
                          uniform int uPreviewFieldMode;
                          uniform float uPreviewRendering;
                          uniform float uPreviewSpeed;

                          float hash(float n) {
                            return fract(sin(n) * 43758.5453123);
                          }

                          float noise3(vec3 p) {
                            vec3 i = floor(p);
                            vec3 f = fract(p);
                            f = f * f * (3.0 - 2.0 * f);
                            float n = dot(i, vec3(1.0, 57.0, 113.0));
                            float a = mix(hash(n + 0.0), hash(n + 1.0), f.x);
                            float b = mix(hash(n + 57.0), hash(n + 58.0), f.x);
                            float c = mix(hash(n + 113.0), hash(n + 114.0), f.x);
                            float d = mix(hash(n + 170.0), hash(n + 171.0), f.x);
                            return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
                          }

                          float fbm(vec3 p) {
                            float v = 0.0;
                            float a = 0.5;
                            for (int i = 0; i < 4; i++) {
                              v += noise3(p) * a;
                              p *= 2.02;
                              a *= 0.5;
                            }
                            return v;
                          }

                          mat3 cameraBasis(vec3 ro, vec3 target) {
                            vec3 f = normalize(target - ro);
                            vec3 r = normalize(cross(f, vec3(0.0, 1.0, 0.0)));
                            vec3 u = cross(r, f);
                            return mat3(r, u, f);
                          }

                          float sceneDensity(vec3 p, out float storm) {
                            float d = 0.0;
                            storm = 0.0;
                            for (int i = 0; i < MAX_FIELDS; i++) {
                              if (i >= uFieldCount) break;
                              vec3 center = uFieldCenter[i];
                              float radius = max(8.0, uFieldRadius[i]);
                              float baseY = uFieldBaseY[i];
                              float topY = max(baseY + 4.0, uFieldTopY[i]);
                              float height01 = clamp((p.y - baseY) / (topY - baseY), 0.0, 1.0);
                              vec2 xz = (p.xz - center.xz) / radius;
                              float horizontal = length(xz);
                              float shape = 1.0 - smoothstep(0.58, 1.04, horizontal);
                              float baseFalloff = smoothstep(0.00, 0.16, height01);
                              float topFalloff = 1.0 - smoothstep(0.78, 1.0, height01);
                              float vertical = baseFalloff * topFalloff;
                              float tower = mix(1.0, 1.0 - smoothstep(0.20, 0.82, horizontal), uFieldVerticalDevelopment[i]);
                              vec3 windOffset = uFieldWind[i] * uTime * 0.045;
                              float n = fbm((p + windOffset + uFieldSeed[i] * 0.017) * mix(0.018, 0.035, uFieldVerticalDevelopment[i]));
                              float detail = smoothstep(0.25, mix(0.72, 0.42, uFieldCoverage[i] * uCoverageMultiplier), n);
                              float hydration = clamp(uFieldHydration[i] * uHydrationMultiplier, 0.0, 1.5);
                              float fieldD = shape * vertical * tower * detail;
                              fieldD *= uFieldDensity[i] * uDensityMultiplier;
                              fieldD *= mix(0.42, 1.2, clamp(uFieldCoverage[i] * uCoverageMultiplier, 0.0, 1.0));
                              fieldD *= mix(0.35, 1.0, hydration);
                              fieldD *= 1.0 - uFieldDecay[i] * 0.55;
                              d += max(0.0, fieldD);
                              storm = max(storm, uFieldStormPotential[i]);
                            }
                            return clamp(d, 0.0, 2.5);
                          }

                          vec3 sky(vec3 rd) {
                            float t = clamp(rd.y * 0.5 + 0.5, 0.0, 1.0);
                            return mix(vec3(0.48, 0.62, 0.78), vec3(0.12, 0.20, 0.30), 1.0 - t);
                          }

                          void main() {
                            vec2 uv = (gl_FragCoord.xy * 2.0 - uResolution.xy) / max(uResolution.x, uResolution.y);
                            vec3 ro = uCameraPos;
                            mat3 basis = cameraBasis(ro, uCameraTarget);
                            vec3 rd = normalize(basis * normalize(vec3(uv, 1.35)));
                            vec3 col = sky(rd);
                            float alpha = 0.0;
                            float totalDensity = 0.0;
                            float stormSeen = 0.0;
                            float t = 0.0;
                            for (int step = 0; step < 72; step++) {
                              vec3 p = ro + rd * t;
                              float storm = 0.0;
                              float d = sceneDensity(p, storm);
                              if (d > 0.002) {
                                float light = clamp(dot(normalize(uSunDirection), vec3(0.2, 0.75, 0.3)) * 0.5 + 0.5, 0.0, 1.0);
                                vec3 cloudColor = mix(vec3(0.92, 0.95, 0.98), vec3(0.42, 0.45, 0.50), storm * 0.65);
                                cloudColor *= mix(0.52, 1.18, light * uLightingStrength);
                                float a = 1.0 - exp(-d * 0.055);
                                col = mix(col, cloudColor, a * (1.0 - alpha));
                                alpha += a * (1.0 - alpha);
                                totalDensity += d;
                                stormSeen = max(stormSeen, storm);
                              }
                              t += 7.5;
                              if (alpha > 0.96 || t > 760.0) break;
                            }
                            if (uDensityMode == 1) {
                              float v = clamp(totalDensity * 0.05, 0.0, 1.0);
                              col = mix(vec3(0.04, 0.08, 0.12), vec3(v, v * 0.92, v * 0.78), v);
                            }
                            if (uShowBounds == 1 && uFieldCount > 0) {
                              for (int i = 0; i < MAX_FIELDS; i++) {
                                if (i >= uFieldCount) break;
                                vec3 c = uFieldCenter[i];
                                float r = uFieldRadius[i];
                                float planeHit = smoothstep(0.018, 0.0, abs(rd.y));
                                float baseHint = smoothstep(5.0, 0.0, abs((ro.y + rd.y * 260.0) - uFieldBaseY[i]));
                                float topHint = smoothstep(5.0, 0.0, abs((ro.y + rd.y * 260.0) - uFieldTopY[i]));
                                float radial = abs(length((ro.xz + rd.xz * 260.0) - c.xz) - r);
                                float ring = smoothstep(5.0, 0.0, radial) * planeHit;
                                col = mix(col, vec3(0.9, 0.78, 0.35), clamp(ring + baseHint * 0.18 + topHint * 0.22, 0.0, 0.55));
                              }
                            }
                            gl_FragColor = vec4(col, 1.0);
                          }
                        `;
                      }
                    }

                    class CloudPreviewShaderProgram {
                      constructor(gl) {
                        this.gl = gl;
                        this.program = this.createProgram();
                        this.locations = new Map();
                      }

                      createProgram() {
                        const gl = this.gl;
                        const vertex = this.compile(gl.VERTEX_SHADER, CloudPreviewShaderSource.vertex());
                        const fragment = this.compile(gl.FRAGMENT_SHADER, CloudPreviewShaderSource.fragment());
                        const program = gl.createProgram();
                        gl.attachShader(program, vertex);
                        gl.attachShader(program, fragment);
                        gl.linkProgram(program);
                        if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
                          throw new Error(gl.getProgramInfoLog(program));
                        }
                        return program;
                      }

                      compile(type, source) {
                        const gl = this.gl;
                        const shader = gl.createShader(type);
                        gl.shaderSource(shader, source);
                        gl.compileShader(shader);
                        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
                          throw new Error(gl.getShaderInfoLog(shader));
                        }
                        return shader;
                      }

                      use() {
                        this.gl.useProgram(this.program);
                      }

                      attrib(name) {
                        return this.gl.getAttribLocation(this.program, name);
                      }

                      uniform(name) {
                        if (!this.locations.has(name)) {
                          this.locations.set(name, this.gl.getUniformLocation(this.program, name));
                        }
                        return this.locations.get(name);
                      }
                    }

                    class CloudPreviewCamera {
                      constructor() {
                        this.azimuth = 0.72;
                        this.elevation = 0.28;
                        this.distance = 410;
                        this.target = { x: 0, y: 160, z: 0 };
                      }
                      reset() {
                        this.azimuth = 0.72;
                        this.elevation = 0.28;
                        this.distance = 410;
                      }
                      position() {
                        const ce = Math.cos(this.elevation);
                        return {
                          x: this.target.x + Math.sin(this.azimuth) * ce * this.distance,
                          y: this.target.y + Math.sin(this.elevation) * this.distance,
                          z: this.target.z + Math.cos(this.azimuth) * ce * this.distance
                        };
                      }
                    }

                    """, """
                    class CloudPreviewUniformUploader {
                      static upload(gl, program, fields, camera, preview, params) {
                        const maxFields = 8;
                        const selected = fields.slice(0, maxFields);
                        const centers = new Float32Array(maxFields * 3);
                        const winds = new Float32Array(maxFields * 3);
                        const radius = new Float32Array(maxFields);
                        const baseY = new Float32Array(maxFields);
                        const topY = new Float32Array(maxFields);
                        const density = new Float32Array(maxFields);
                        const coverage = new Float32Array(maxFields);
                        const growth = new Float32Array(maxFields);
                        const decay = new Float32Array(maxFields);
                        const humidity = new Float32Array(maxFields);
                        const vertical = new Float32Array(maxFields);
                        const storm = new Float32Array(maxFields);
                        const seed = new Float32Array(maxFields);
                        const hydration = new Float32Array(maxFields);
                        const age = new Float32Array(maxFields);
                        const cloudlets = new Float32Array(maxFields);
                        for (let i = 0; i < selected.length; i++) {
                          const f = selected[i];
                          centers[i * 3] = f.center.x;
                          centers[i * 3 + 1] = f.center.y;
                          centers[i * 3 + 2] = f.center.z;
                          winds[i * 3] = f.velocity.x;
                          winds[i * 3 + 1] = f.velocity.y || 0;
                          winds[i * 3 + 2] = f.velocity.z;
                          radius[i] = f.radius;
                          baseY[i] = f.baseY;
                          topY[i] = f.topY;
                          density[i] = f.effectiveDensity;
                          coverage[i] = f.effectiveCoverage;
                          growth[i] = f.growth;
                          decay[i] = f.decay;
                          humidity[i] = f.target.influence.humidityContribution;
                          vertical[i] = f.verticalDevelopment;
                          storm[i] = f.stormPotential;
                          seed[i] = f.seed % 100000;
                          hydration[i] = f.hydrationProgress;
                          age[i] = f.ageHours;
                          cloudlets[i] = f.targetCloudletCount;
                        }
                        const camPos = camera.position();
                        gl.uniform2f(program.uniform('uResolution'), gl.canvas.width, gl.canvas.height);
                        gl.uniform1f(program.uniform('uTime'), preview.time);
                        gl.uniform3f(program.uniform('uCameraPos'), camPos.x, camPos.y, camPos.z);
                        gl.uniform3f(program.uniform('uCameraTarget'), camera.target.x, camera.target.y, camera.target.z);
                        gl.uniform3f(program.uniform('uSunDirection'), 0.35, 0.82, 0.42);
                        gl.uniform1i(program.uniform('uFieldCount'), selected.length);
                        gl.uniform3fv(program.uniform('uFieldCenter[0]'), centers);
                        gl.uniform1fv(program.uniform('uFieldRadius[0]'), radius);
                        gl.uniform1fv(program.uniform('uFieldBaseY[0]'), baseY);
                        gl.uniform1fv(program.uniform('uFieldTopY[0]'), topY);
                        gl.uniform1fv(program.uniform('uFieldDensity[0]'), density);
                        gl.uniform1fv(program.uniform('uFieldCoverage[0]'), coverage);
                        gl.uniform1fv(program.uniform('uFieldGrowth[0]'), growth);
                        gl.uniform1fv(program.uniform('uFieldDecay[0]'), decay);
                        gl.uniform1fv(program.uniform('uFieldHumidityInfluence[0]'), humidity);
                        gl.uniform3fv(program.uniform('uFieldWind[0]'), winds);
                        gl.uniform1fv(program.uniform('uFieldVerticalDevelopment[0]'), vertical);
                        gl.uniform1fv(program.uniform('uFieldStormPotential[0]'), storm);
                        gl.uniform1fv(program.uniform('uFieldSeed[0]'), seed);
                        gl.uniform1fv(program.uniform('uFieldHydration[0]'), hydration);
                        gl.uniform1fv(program.uniform('uFieldAge[0]'), age);
                        gl.uniform1fv(program.uniform('uFieldCloudletCount[0]'), cloudlets);
                        gl.uniform1f(program.uniform('uDensityMultiplier'), preview.densityMultiplier);
                        gl.uniform1f(program.uniform('uCoverageMultiplier'), preview.coverageMultiplier);
                        gl.uniform1f(program.uniform('uHydrationMultiplier'), preview.hydrationMultiplier);
                        gl.uniform1f(program.uniform('uLightingStrength'), preview.lightingStrength);
                        gl.uniform1i(program.uniform('uDensityMode'), preview.renderMode === 'density' ? 1 : 0);
                        gl.uniform1i(program.uniform('uShowBounds'), preview.showBounds ? 1 : 0);
                        gl.uniform1f(program.uniform('uForecastTemperature'), params.temperature);
                        gl.uniform1f(program.uniform('uForecastHumidity'), params.humidity);
                        gl.uniform1f(program.uniform('uForecastPressure'), params.pressure);
                        gl.uniform1f(program.uniform('uForecastWindSpeed'), params.windSpeed);
                        gl.uniform1f(program.uniform('uForecastWindDirection'), params.windDirection);
                        gl.uniform1f(program.uniform('uForecastCloudCover'), params.cloudCover);
                        gl.uniform1f(program.uniform('uForecastRainIntensity'), params.rainIntensity);
                        gl.uniform1f(program.uniform('uForecastStormChance'), params.stormChance);
                        gl.uniform1f(program.uniform('uForecastInstability'), params.instability);
                        gl.uniform1f(program.uniform('uForecastVerticalDevelopment'), params.verticalDevelopment);
                        gl.uniform1f(program.uniform('uForecastBaseY'), params.baseY);
                        gl.uniform1f(program.uniform('uForecastTopY'), params.topY);
                        gl.uniform1f(program.uniform('uForecastHour'), params.forecastHour);
                        gl.uniform1f(program.uniform('uForecastSeed'), params.seed);
                        gl.uniform1f(program.uniform('uRegionAllowsClouds'), params.regionAllowsClouds ? 1 : 0);
                        gl.uniform1f(program.uniform('uRegionMoisture'), params.regionMoisture);
                        gl.uniform1f(program.uniform('uTerrainLift'), params.terrainLift);
                        gl.uniform1f(program.uniform('uFrontConvergence'), params.frontConvergence);
                        gl.uniform1f(program.uniform('uSpawnSuppression'), params.spawnSuppression);
                        gl.uniform1i(program.uniform('uPreviewFieldMode'), preview.fieldMode === 'all' ? 1 : preview.fieldMode === 'synthetic' ? 2 : 0);
                        gl.uniform1f(program.uniform('uPreviewRendering'), preview.rendering ? 1 : 0);
                        gl.uniform1f(program.uniform('uPreviewSpeed'), preview.speed);
                      }
                    }

                    class CloudPreviewWebGLRenderer {
                      constructor(canvas) {
                        this.canvas = canvas;
                        this.gl = canvas.getContext('webgl', { antialias: false, alpha: false });
                        this.camera = new CloudPreviewCamera();
                        this.dragging = false;
                        this.lastMouse = { x: 0, y: 0 };
                        if (!this.gl) {
                          this.error = 'WebGL unavailable';
                          return;
                        }
                        this.program = new CloudPreviewShaderProgram(this.gl);
                        this.buffer = this.gl.createBuffer();
                        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.buffer);
                        this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), this.gl.STATIC_DRAW);
                        this.installControls();
                      }

                      installControls() {
                        this.canvas.addEventListener('mousedown', event => {
                          this.dragging = true;
                          this.lastMouse = { x: event.clientX, y: event.clientY };
                        });
                        window.addEventListener('mouseup', () => this.dragging = false);
                        window.addEventListener('mousemove', event => {
                          if (!this.dragging) return;
                          const dx = event.clientX - this.lastMouse.x;
                          const dy = event.clientY - this.lastMouse.y;
                          this.lastMouse = { x: event.clientX, y: event.clientY };
                          this.camera.azimuth += dx * 0.008;
                          this.camera.elevation = clamp(this.camera.elevation + dy * 0.006, -0.18, 1.12);
                          draw();
                        });
                        this.canvas.addEventListener('wheel', event => {
                          event.preventDefault();
                          this.camera.distance = clamp(this.camera.distance * (event.deltaY > 0 ? 1.08 : 0.92), 120, 980);
                          draw();
                        }, { passive: false });
                      }

                      recompile() {
                        if (!this.gl) return;
                        this.program = new CloudPreviewShaderProgram(this.gl);
                      }

                      resetCamera() {
                        this.camera.reset();
                      }

                      render(fields, preview, params) {
                        const gl = this.gl;
                        if (!gl) return;
                        resizeCanvas(this.canvas);
                        gl.viewport(0, 0, this.canvas.width, this.canvas.height);
                        gl.disable(gl.DEPTH_TEST);
                        gl.clearColor(0.08, 0.12, 0.16, 1);
                        gl.clear(gl.COLOR_BUFFER_BIT);
                        this.program.use();
                        const location = this.program.attrib('aPosition');
                        gl.bindBuffer(gl.ARRAY_BUFFER, this.buffer);
                        gl.enableVertexAttribArray(location);
                        gl.vertexAttribPointer(location, 2, gl.FLOAT, false, 0, 0);
                        CloudPreviewUniformUploader.upload(gl, this.program, fields, this.camera, preview, params);
                        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
                      }
                    }

                    """, """
                    class CloudPreviewControls {
                      static setPreviewValue(preview, key, value) {
                        const parsed = parseNumber(value);
                        if (!Number.isFinite(parsed)) return false;
                        preview[key] = parsed;
                        return true;
                      }

                      static setPreviewMode(preview, key, value) {
                        preview[key] = value;
                      }
                    }

                    const PRESETS = [
                      new CloudFormationPreset('clear', 'Clear sky', { temperature: 22, humidity: 28, pressure: 1022, windSpeed: 8, windDirection: 260, cloudCover: 4, rainIntensity: 0, stormChance: 0.01, instability: 0.08, verticalDevelopment: 0.08, baseY: 160, topY: 190, forecastHour: 12 }),
                      new CloudFormationPreset('fair', 'Fair weather cumulus', { temperature: 23, humidity: 54, pressure: 1014, windSpeed: 12, windDirection: 230, cloudCover: 32, rainIntensity: 0.02, stormChance: 0.06, instability: 0.28, verticalDevelopment: 0.30, baseY: 124, topY: 190, forecastHour: 13 }),
                      new CloudFormationPreset('humid', 'Humid cumulus field', { temperature: 26, humidity: 78, pressure: 1009, windSpeed: 10, windDirection: 210, cloudCover: 58, rainIntensity: 0.10, stormChance: 0.16, instability: 0.46, verticalDevelopment: 0.48, baseY: 104, topY: 230, forecastHour: 14 }),
                      new CloudFormationPreset('strato', 'Stratocumulus / low layer', { temperature: 16, humidity: 82, pressure: 1016, windSpeed: 18, windDirection: 280, cloudCover: 82, rainIntensity: 0.05, stormChance: 0.04, instability: 0.14, verticalDevelopment: 0.18, baseY: 82, topY: 138, forecastHour: 9 }),
                      new CloudFormationPreset('congestus', 'Congestus growth', { temperature: 28, humidity: 72, pressure: 1006, windSpeed: 14, windDirection: 190, cloudCover: 52, rainIntensity: 0.14, stormChance: 0.34, instability: 0.70, verticalDevelopment: 0.76, baseY: 112, topY: 305, forecastHour: 15 }),
                      new CloudFormationPreset('storm', 'Storm buildup', { temperature: 29, humidity: 86, pressure: 996, windSpeed: 22, windDirection: 205, cloudCover: 76, rainIntensity: 0.38, stormChance: 0.74, instability: 0.86, verticalDevelopment: 0.90, baseY: 92, topY: 380, forecastHour: 16 }),
                      new CloudFormationPreset('dry', 'Dry dissipation', { temperature: 31, humidity: 24, pressure: 1019, windSpeed: 11, windDirection: 250, cloudCover: 22, rainIntensity: 0, stormChance: 0.02, instability: 0.22, verticalDevelopment: 0.16, baseY: 155, topY: 210, forecastHour: 17 }),
                      new CloudFormationPreset('shear', 'High wind shear', { temperature: 20, humidity: 64, pressure: 1007, windSpeed: 46, windDirection: 300, cloudCover: 55, rainIntensity: 0.16, stormChance: 0.28, instability: 0.52, verticalDevelopment: 0.50, baseY: 118, topY: 255, forecastHour: 14 })
                    ];

                    const PARAMS = [
                      ['temperature', 'Temperature', -20, 45, 1],
                      ['humidity', 'Humidity', 0, 100, 1],
                      ['pressure', 'Pressure', 960, 1040, 1],
                      ['windSpeed', 'Wind speed', 0, 80, 1],
                      ['windDirection', 'Wind direction', 0, 359, 1],
                      ['cloudCover', 'Cloud cover', 0, 100, 1],
                      ['rainIntensity', 'Rain intensity', 0, 1, 0.01],
                      ['stormChance', 'Storm chance', 0, 1, 0.01],
                      ['instability', 'Instability', 0, 1, 0.01],
                      ['verticalDevelopment', 'Vertical dev', 0, 1, 0.01],
                      ['baseY', 'Base Y', 40, 240, 1],
                      ['topY', 'Top Y', 60, 460, 1],
                      ['forecastHour', 'Forecast hour', 0, 24, 0.25],
                      ['seed', 'Seed', 1, 999999, 1]
                    ];

                    const store = new CloudFieldPreviewStore();
                    const mapCanvas = document.getElementById('mapCanvas');
                    const glPreviewCanvas = document.getElementById('glPreviewCanvas');
                    const profileCanvas = document.getElementById('profileCanvas');
                    const mapCtx = mapCanvas.getContext('2d');
                    const profileCtx = profileCanvas.getContext('2d');
                    let glRenderer = null;

                    """, """
                    function setupUi() {
                      const presetSelect = document.getElementById('preset');
                      for (const preset of PRESETS) {
                        const option = document.createElement('option');
                        option.value = preset.id;
                        option.textContent = preset.label;
                        presetSelect.append(option);
                      }

                      const parameterControls = document.getElementById('parameterControls');
                      for (const [key, label, min, max, step] of PARAMS) {
                        const row = document.createElement('div');
                        row.className = 'row';
                        const labelEl = document.createElement('label');
                        labelEl.textContent = label;
                        const range = document.createElement('input');
                        range.type = 'range';
                        range.min = min;
                        range.max = max;
                        range.step = step;
                        range.dataset.key = key;
                        const number = document.createElement('input');
                        number.type = 'number';
                        number.min = min;
                        number.max = max;
                        number.step = step;
                        number.dataset.key = key;
                        number.dataset.number = 'true';
                        range.addEventListener('input', () => updateParam(key, parseNumber(range.value)));
                        number.addEventListener('input', () => updateParam(key, parseNumber(number.value)));
                        row.append(labelEl, range, number);
                        parameterControls.append(row);
                      }

                      document.getElementById('applyPreset').addEventListener('click', () => {
                        const preset = PRESETS.find(p => p.id === presetSelect.value);
                        store.applyPreset(preset);
                        syncUi();
                        draw();
                      });
                      document.getElementById('reset').addEventListener('click', () => {
                        store.reset();
                        syncUi();
                        draw();
                      });
                      document.getElementById('regenSeed').addEventListener('click', () => {
                        store.regenerateSeed();
                        syncUi();
                        draw();
                      });
                      document.getElementById('playPause').addEventListener('click', () => {
                        store.playing = !store.playing;
                        document.getElementById('playPause').textContent = store.playing ? 'Pause' : 'Play';
                      });
                      document.getElementById('stepHour').addEventListener('click', () => {
                        store.step(1);
                        syncUi();
                        draw();
                      });
                      document.getElementById('rebuildTargets').addEventListener('click', () => {
                        store.refreshTargets();
                        draw();
                      });
                      document.getElementById('timeScale').addEventListener('change', event => {
                        store.timeScale = Number(event.target.value);
                      });
                      for (const [id, key] of [
                        ['showRings', 'rings'],
                        ['showCloudlets', 'cloudlets'],
                        ['showWind', 'wind'],
                        ['showLabels', 'labels'],
                        ['showProfile', 'profile']
                      ]) {
                        document.getElementById(id).addEventListener('change', event => {
                          store.display[key] = event.target.checked;
                          document.getElementById('profilePane').classList.toggle('hidden', !store.display.profile);
                          draw();
                        });
                      }
                      document.getElementById('previewFieldMode').addEventListener('change', event => {
                        CloudPreviewControls.setPreviewMode(store.preview, 'fieldMode', event.target.value);
                        draw();
                      });
                      document.getElementById('previewRenderMode').addEventListener('change', event => {
                        CloudPreviewControls.setPreviewMode(store.preview, 'renderMode', event.target.value);
                        draw();
                      });
                      bindPreviewNumberPair('previewDensity', 'densityMultiplier');
                      bindPreviewNumberPair('previewCoverage', 'coverageMultiplier');
                      bindPreviewNumberPair('previewHydration', 'hydrationMultiplier');
                      bindPreviewNumberPair('previewLighting', 'lightingStrength');
                      bindPreviewNumberPair('previewSpeed', 'speed');
                      document.getElementById('previewShowBounds').addEventListener('change', event => {
                        store.preview.showBounds = event.target.checked;
                        draw();
                      });
                      document.getElementById('previewRenderEnabled').addEventListener('change', event => {
                        store.preview.rendering = event.target.checked;
                        draw();
                      });
                      document.getElementById('previewPlaying').addEventListener('change', event => {
                        store.preview.playing = event.target.checked;
                      });
                      document.getElementById('regionAllowsClouds').addEventListener('change', event => {
                        store.setParams({ regionAllowsClouds: event.target.checked });
                        draw();
                      });
                      bindRegionNumberPair('regionMoisture', 'regionMoisture');
                      bindRegionNumberPair('terrainLift', 'terrainLift');
                      bindRegionNumberPair('frontConvergence', 'frontConvergence');
                      bindRegionNumberPair('spawnSuppression', 'spawnSuppression');
                      document.getElementById('previewShaderFiles').addEventListener('change', async event => {
                        try {
                          await CloudPreviewShaderSource.importFiles(event.target.files);
                          if (glRenderer) glRenderer.recompile();
                          draw();
                        } catch (error) {
                          document.getElementById('glHud').innerHTML = '<span class=\"pill\">Shader import failed: ' + error.message + '</span>';
                        }
                      });
                      document.getElementById('previewResetTime').addEventListener('click', () => {
                        store.preview.time = 0;
                        draw();
                      });
                      document.getElementById('previewResetCamera').addEventListener('click', () => {
                        if (glRenderer) glRenderer.resetCamera();
                        draw();
                      });
                      document.getElementById('previewRecompile').addEventListener('click', async () => {
                        try {
                          await CloudPreviewShaderSource.loadExternal();
                          if (glRenderer) glRenderer.recompile();
                        } catch (error) {
                          document.getElementById('glHud').innerHTML = '<span class=\"pill\">Shader compile failed: ' + error.message + '</span>';
                        }
                        draw();
                      });
                      mapCanvas.addEventListener('click', event => {
                        const rect = mapCanvas.getBoundingClientRect();
                        const x = (event.clientX - rect.left) * (mapCanvas.width / rect.width);
                        const y = (event.clientY - rect.top) * (mapCanvas.height / rect.height);
                        const hit = hitTestField(x, y);
                        if (hit) {
                          store.selectedId = hit.fieldId;
                          draw();
                        }
                      });
                      glRenderer = new CloudPreviewWebGLRenderer(glPreviewCanvas);
                      CloudPreviewShaderSource.loadExternal().then(loaded => {
                        if (loaded && glRenderer) {
                          glRenderer.recompile();
                          draw();
                        }
                      }).catch(() => {});
                      syncUi();
                    }

                    function bindPreviewNumberPair(rangeId, key) {
                      const range = document.getElementById(rangeId);
                      const number = document.getElementById(rangeId + 'Number');
                      const apply = value => {
                        if (!CloudPreviewControls.setPreviewValue(store.preview, key, value)) return;
                        range.value = store.preview[key];
                        number.value = store.preview[key];
                        draw();
                      };
                      range.addEventListener('input', () => apply(range.value));
                      number.addEventListener('input', () => apply(number.value));
                    }

                    function bindRegionNumberPair(rangeId, key) {
                      const range = document.getElementById(rangeId);
                      const number = document.getElementById(rangeId + 'Number');
                      const apply = value => {
                        const parsed = parseNumber(value);
                        if (!Number.isFinite(parsed)) return;
                        store.setParams({ [key]: clamp01(parsed) });
                        range.value = store.params[key];
                        number.value = store.params[key];
                        draw();
                      };
                      range.addEventListener('input', () => apply(range.value));
                      number.addEventListener('input', () => apply(number.value));
                    }

                    function updateParam(key, value) {
                      if (!Number.isFinite(value)) return;
                      if (key === 'topY') value = Math.max(value, store.params.baseY + 5);
                      if (key === 'baseY') store.params.topY = Math.max(store.params.topY, value + 5);
                      store.setParams({ [key]: value });
                      syncUi();
                      draw();
                    }

                    function syncUi() {
                      for (const [key] of PARAMS) {
                        for (const input of document.querySelectorAll('[data-key=\"' + key + '\"]')) {
                          input.value = roundForUi(store.params[key]);
                        }
                      }
                      document.getElementById('regionAllowsClouds').checked = store.params.regionAllowsClouds;
                      for (const key of ['regionMoisture', 'terrainLift', 'frontConvergence', 'spawnSuppression']) {
                        const range = document.getElementById(key);
                        const number = document.getElementById(key + 'Number');
                        if (range) range.value = roundForUi(store.params[key]);
                        if (number) number.value = roundForUi(store.params[key]);
                      }
                    }

                    function tick(timestamp) {
                      if (!store.lastFrame) store.lastFrame = timestamp;
                      const dtSeconds = Math.min(0.08, (timestamp - store.lastFrame) / 1000);
                      store.lastFrame = timestamp;
                      if (store.playing) {
                        store.tick(dtSeconds * store.timeScale / 3600);
                        syncUi();
                      }
                      if (store.preview.playing) {
                        store.preview.time += dtSeconds * store.preview.speed;
                      }
                      draw();
                      requestAnimationFrame(tick);
                    }

                    function draw() {
                      document.getElementById('profilePane').classList.toggle('hidden', !store.display.profile);
                      resizeCanvas(mapCanvas);
                      resizeCanvas(glPreviewCanvas);
                      if (store.display.profile) resizeCanvas(profileCanvas);
                      drawMap();
                      drawGlPreview();
                      if (store.display.profile) drawProfile();
                      drawMetrics();
                      drawSelection();
                    }

                    function drawMap() {
                      const ctx = mapCtx;
                      const w = mapCanvas.width;
                      const h = mapCanvas.height;
                      ctx.clearRect(0, 0, w, h);
                      ctx.fillStyle = '#0f1419';
                      ctx.fillRect(0, 0, w, h);
                      drawGrid(ctx, w, h);

                      for (const field of store.snapshots) {
                        const selected = field.fieldId === store.selectedId;
                        const p = worldToMap(field.center, w, h);
                        const r = field.radius * mapScale(w, h);
                        const color = lodColor(field);
                        if (store.display.rings) {
                          ctx.save();
                          ctx.globalAlpha = selected ? 0.18 + field.effectiveDensity * 0.24 : 0.05 + field.effectiveDensity * 0.12;
                          ctx.fillStyle = color;
                          ctx.beginPath();
                          ctx.ellipse(p.x, p.y, r * (1 + windShear(field) * 0.45), r * (1 - windShear(field) * 0.18), directionRad(store.params.windDirection), 0, Math.PI * 2);
                          ctx.fill();
                          ctx.globalAlpha = selected ? 1 : 0.42;
                          ctx.strokeStyle = selected ? '#ffffff' : color;
                          ctx.lineWidth = selected ? 4 : 2;
                          ctx.stroke();
                          ctx.restore();
                        }
                        if (store.display.cloudlets && selected) drawCloudlets(ctx, field, w, h, color);
                        if (store.display.wind) drawWindArrow(ctx, field, w, h);
                        if (store.display.labels && (selected || field.maturity > 0.42)) drawLabel(ctx, field, p, selected);
                        ctx.fillStyle = selected ? '#ffffff' : color;
                        ctx.globalAlpha = selected ? 1 : 0.55;
                        ctx.beginPath();
                        ctx.arc(p.x, p.y, selected ? 6 : 4, 0, Math.PI * 2);
                        ctx.fill();
                        ctx.globalAlpha = 1;
                      }

                      document.getElementById('mapHud').innerHTML =
                        '<span class=\"pill\">hour ' + store.params.forecastHour.toFixed(2) + '</span>' +
                        '<span class=\"pill\">time scale ' + store.timeScale + 'x</span>' +
                        '<span class=\"pill\">persistent fields: ' + store.snapshots.length + '</span>';
                    }

                    """, """
                    function drawProfile() {
                      const ctx = profileCtx;
                      const w = profileCanvas.width;
                      const h = profileCanvas.height;
                      ctx.clearRect(0, 0, w, h);
                      ctx.fillStyle = '#11171d';
                      ctx.fillRect(0, 0, w, h);
                      ctx.strokeStyle = '#34404d';
                      ctx.lineWidth = 1;
                      for (let y = 60; y <= 460; y += 40) {
                        const py = profileY(y, h);
                        ctx.beginPath();
                        ctx.moveTo(0, py);
                        ctx.lineTo(w, py);
                        ctx.stroke();
                      }
                      const fields = store.snapshots;
                      fields.forEach((field, i) => {
                        const selected = field.fieldId === store.selectedId;
                        const x = fields.length <= 1 ? w * 0.5 : 70 + i * ((w - 140) / Math.max(1, fields.length - 1));
                        const yTop = profileY(field.topY, h);
                        const yBase = profileY(field.baseY, h);
                        const targetTop = profileY(field.target.topY, h);
                        const width = Math.max(24, Math.min(86, field.radius * 0.28));
                        const color = lodColor(field);
                        ctx.fillStyle = colorWithAlpha(color, 0.16 + field.effectiveDensity * 0.34);
                        ctx.strokeStyle = selected ? '#ffffff' : color;
                        ctx.lineWidth = selected ? 4 : 2;
                        roundRect(ctx, x - width / 2, yTop, width, yBase - yTop, 8);
                        ctx.fill();
                        ctx.stroke();
                        ctx.strokeStyle = '#9aa8b6';
                        ctx.setLineDash([4, 4]);
                        ctx.beginPath();
                        ctx.moveTo(x - width / 2, targetTop);
                        ctx.lineTo(x + width / 2, targetTop);
                        ctx.stroke();
                        ctx.setLineDash([]);
                        ctx.fillStyle = field.stormPotential > 0.55 ? '#fb7185' : '#e7edf3';
                        ctx.fillRect(x - width / 2, yTop - 5, width * field.maturity, 3);
                        ctx.fillStyle = '#e7edf3';
                        ctx.fillText(field.kind, x - width / 2, Math.max(14, yTop - 8));
                      });
                    }

                    function drawGlPreview() {
                      if (!glRenderer) return;
                      if (!store.preview.rendering) {
                        if (glRenderer.gl) {
                          glRenderer.gl.viewport(0, 0, glPreviewCanvas.width, glPreviewCanvas.height);
                          glRenderer.gl.clearColor(0.02, 0.025, 0.03, 1);
                          glRenderer.gl.clear(glRenderer.gl.COLOR_BUFFER_BIT);
                        }
                        document.getElementById('glHud').innerHTML = '<span class=\"pill\">WebGL rendering stopped</span>';
                        return;
                      }
                      const fields = previewFields();
                      if (fields.length > 0 && glRenderer.camera) {
                        const center = averageCenter(fields);
                        glRenderer.camera.target = { x: center.x, y: center.y, z: center.z };
                      }
                      try {
                        glRenderer.render(fields, store.preview, store.params);
                        document.getElementById('glHud').innerHTML =
                          '<span class=\"pill\">WebGL preview: ' + store.preview.fieldMode + '</span>' +
                          '<span class=\"pill\">fields uploaded: ' + Math.min(fields.length, 8) + '</span>' +
                          '<span class=\"pill\">shader mode: ' + store.preview.renderMode + '</span>';
                      } catch (error) {
                        document.getElementById('glHud').innerHTML = '<span class=\"pill\">WebGL error: ' + error.message + '</span>';
                      }
                    }

                    function previewFields() {
                      if (store.preview.fieldMode === 'all') {
                        return store.snapshots.filter(field => field.lodBand !== 'HAZE').slice(0, 8);
                      }
                      if (store.preview.fieldMode === 'synthetic') {
                        return [syntheticPreviewField()];
                      }
                      const selected = store.selectedField();
                      if (selected) return [selected];
                      const strongest = [...store.snapshots].sort((a, b) =>
                        (b.effectiveDensity * b.effectiveCoverage * b.radius) - (a.effectiveDensity * a.effectiveCoverage * a.radius)
                      )[0];
                      return strongest ? [strongest] : [syntheticPreviewField()];
                    }

                    function syntheticPreviewField() {
                      const influence = computeInfluence(store.params);
                      const density = clamp01(influence.humidityContribution * 0.48 + influence.cloudCoverContribution * 0.26 + influence.rainStormContribution * 0.22 - influence.drynessPenalty * 0.26);
                      const coverage = clamp01(influence.cloudCoverContribution * 0.58 + influence.humidityContribution * 0.24 - influence.drynessPenalty * 0.20);
                      const storm = clamp01(influence.rainStormContribution * 0.60 + influence.lowPressureContribution * 0.25 + influence.instabilityContribution * 0.15);
                      const wind = windVector(store.params.windSpeed, store.params.windDirection);
                      return {
                        fieldId: 'synthetic-forecast',
                        seed: store.params.seed + 777,
                        center: { x: 0, y: lerp(store.params.baseY, store.params.topY, 0.5), z: 0 },
                        previousCenter: { x: -wind.x, y: lerp(store.params.baseY, store.params.topY, 0.5), z: -wind.z },
                        velocity: wind,
                        radius: lerp(95, 230, clamp01(coverage + density) * 0.5),
                        baseY: store.params.baseY,
                        topY: Math.max(store.params.baseY + 8, store.params.topY + storm * 70),
                        density,
                        coverage,
                        effectiveDensity: density,
                        effectiveCoverage: coverage,
                        growth: clamp01(influence.humidityContribution * 0.25 + influence.instabilityContribution * 0.28 + store.params.verticalDevelopment * 0.28),
                        decay: clamp01(influence.drynessPenalty * 0.65),
                        stormPotential: storm,
                        verticalDevelopment: clamp01(store.params.verticalDevelopment * 0.52 + influence.instabilityContribution * 0.34 + storm * 0.14),
                        hydrationProgress: clamp01(influence.humidityContribution * 0.55 + coverage * 0.25),
                        ageHours: store.params.forecastHour,
                        targetCloudletCount: Math.round(clamp(coverage * density * 170, 0, 190)),
                        activeCloudletCount: Math.round(clamp(coverage * density * 130, 0, 160)),
                        target: { influence, kind: 'synthetic forecast' },
                        kind: 'synthetic forecast',
                        lodBand: 'DYNAMIC',
                        maturity: 1
                      };
                    }

                    function averageCenter(fields) {
                      if (fields.length === 0) return { x: 0, y: 160, z: 0 };
                      let x = 0;
                      let y = 0;
                      let z = 0;
                      for (const field of fields) {
                        x += field.center.x;
                        y += lerp(field.baseY, field.topY, 0.52);
                        z += field.center.z;
                      }
                      return { x: x / fields.length, y: y / fields.length, z: z / fields.length };
                    }

                    function drawCloudlets(ctx, field, w, h, color) {
                      const rng = mulberry32(field.seed);
                      const count = Math.min(140, field.activeCloudletCount);
                      for (let i = 0; i < count; i++) {
                        const angle = rng() * Math.PI * 2;
                        const ring = Math.sqrt(rng()) * field.radius * 0.78;
                        const offset = rotate({ x: Math.cos(angle) * ring, z: Math.sin(angle) * ring }, directionRad(store.params.windDirection), windShear(field));
                        const p = worldToMap({ x: field.center.x + offset.x, z: field.center.z + offset.z }, w, h);
                        const size = Math.max(1.5, Math.min(6.5, field.radius * 0.018 * (0.6 + rng())));
                        ctx.fillStyle = colorWithAlpha(color, 0.18 + field.hydrationProgress * 0.46);
                        ctx.beginPath();
                        ctx.arc(p.x, p.y, size, 0, Math.PI * 2);
                        ctx.fill();
                      }
                    }

                    function drawWindArrow(ctx, field, w, h) {
                      const p = worldToMap(field.center, w, h);
                      const len = Math.max(20, Math.min(80, Math.hypot(field.velocity.x, field.velocity.z) * 16));
                      const angle = Math.atan2(field.velocity.x, -field.velocity.z);
                      const end = { x: p.x + Math.sin(angle) * len, y: p.y - Math.cos(angle) * len };
                      ctx.strokeStyle = '#f3f6f8';
                      ctx.lineWidth = 2;
                      ctx.beginPath();
                      ctx.moveTo(p.x, p.y);
                      ctx.lineTo(end.x, end.y);
                      ctx.stroke();
                      ctx.fillStyle = '#f3f6f8';
                      ctx.beginPath();
                      ctx.arc(end.x, end.y, 3, 0, Math.PI * 2);
                      ctx.fill();
                    }

                    function drawLabel(ctx, field, p, selected) {
                      ctx.fillStyle = '#e7edf3';
                      ctx.font = '12px system-ui, sans-serif';
                      const label = selected
                        ? field.kind + ' cloudlets: ' + field.activeCloudletCount + ' / ' + field.targetCloudletCount
                        : field.kind;
                      ctx.fillText(label, p.x + 8, p.y - 8);
                    }

                    function drawMetrics() {
                      const s = store.summary();
                      document.getElementById('metrics').innerHTML = [
                        metric('Fields', s.fields),
                        metric('Cloudlets', s.cloudlets),
                        metric('Avg density', s.avgDensity.toFixed(2)),
                        metric('Storm potential', s.stormPotential.toFixed(2))
                      ].join('');

                      document.getElementById('fieldTable').innerHTML = store.snapshots.map((f, i) =>
                        '<tr class=\"' + (f.fieldId === store.selectedId ? 'selected' : '') + '\" data-id=\"' + f.fieldId + '\"><td>' + (i + 1) + '</td><td>' + f.kind + '</td><td>' + f.radius.toFixed(0) +
                        '</td><td>' + f.effectiveDensity.toFixed(2) + '</td><td>' + f.effectiveCoverage.toFixed(2) +
                        '</td><td>' + f.activeCloudletCount + ' / ' + f.targetCloudletCount + '</td><td>' + f.lodBand + '</td></tr>'
                      ).join('');
                      for (const row of document.querySelectorAll('#fieldTable tr')) {
                        row.addEventListener('click', () => {
                          store.selectedId = row.dataset.id;
                          draw();
                        });
                      }
                    }

                    function drawSelection() {
                      const field = store.selectedField();
                      if (!field) {
                        document.getElementById('selectedDetails').innerHTML = '<span>No CloudField selected.</span>';
                        document.getElementById('influenceBreakdown').innerHTML = '<span>No forecast influence available.</span>';
                        return;
                      }
                      document.getElementById('selectedDetails').innerHTML =
                        '<div class=\"kv\">' +
                        kv('field id', field.fieldId) +
                        kv('kind', field.kind + ' -> ' + field.target.kind) +
                        kv('density current -> target', field.density.toFixed(2) + ' -> ' + field.target.density.toFixed(2)) +
                        kv('coverage current -> target', field.coverage.toFixed(2) + ' -> ' + field.target.coverage.toFixed(2)) +
                        kv('storm potential current -> target', field.stormPotential.toFixed(2) + ' -> ' + field.target.stormPotential.toFixed(2)) +
                        kv('growth / decay', field.growth.toFixed(2) + ' / ' + field.decay.toFixed(2)) +
                        kv('age / maturity', field.ageHours.toFixed(2) + 'h / ' + field.maturity.toFixed(2)) +
                        kv('velocity', field.velocity.x.toFixed(2) + ', ' + field.velocity.z.toFixed(2)) +
                        kv('cloudlet count', field.activeCloudletCount + ' / ' + field.targetCloudletCount) +
                        '</div>';
                      const influence = field.target.influence;
                      document.getElementById('influenceBreakdown').innerHTML =
                        influenceBar('humidity contribution', influence.humidityContribution) +
                        influenceBar('cloud cover contribution', influence.cloudCoverContribution) +
                        influenceBar('instability contribution', influence.instabilityContribution) +
                        influenceBar('low pressure contribution', influence.lowPressureContribution) +
                        influenceBar('rain/storm contribution', influence.rainStormContribution) +
                        influenceBar('dryness penalty', influence.drynessPenalty) +
                        influenceBar('stability/layering score', influence.stabilityLayeringScore) +
                        influenceBar('region allows clouds', influence.regionAllowance) +
                        influenceBar('biome moisture', influence.regionMoistureContribution) +
                        influenceBar('terrain lift', influence.terrainLiftContribution) +
                        influenceBar('front convergence', influence.frontConvergenceContribution) +
                        influenceBar('spawn suppression', influence.spawnSuppressionPenalty);
                    }

                    function hitTestField(x, y) {
                      const w = mapCanvas.width;
                      const h = mapCanvas.height;
                      let best = null;
                      let bestDistance = Infinity;
                      for (const field of store.snapshots) {
                        const p = worldToMap(field.center, w, h);
                        const r = Math.max(10, field.radius * mapScale(w, h));
                        const d = Math.hypot(x - p.x, y - p.y);
                        if (d <= r && d < bestDistance) {
                          best = field;
                          bestDistance = d;
                        }
                      }
                      return best;
                    }

                    """, """
                    function computeInfluence(params) {
                      const humidity = clamp01(params.humidity / 100);
                      const cloudCover = clamp01(params.cloudCover / 100);
                      const instability = clamp01(params.instability);
                      const lowPressure = clamp01((1018 - params.pressure) / 38);
                      const rainStorm = clamp01(params.rainIntensity * 0.45 + params.stormChance * 0.38 + lowPressure * 0.17);
                      const dryness = clamp01((42 - params.humidity) / 42);
                      const stabilityLayering = clamp01(cloudCover * 0.62 + humidity * 0.25 + (1 - instability) * 0.38 - rainStorm * 0.20);
                      const regionAllowed = params.regionAllowsClouds ? 1.0 : 0.0;
                      return {
                        humidityContribution: humidity,
                        cloudCoverContribution: cloudCover,
                        instabilityContribution: instability,
                        lowPressureContribution: lowPressure,
                        rainStormContribution: rainStorm,
                        drynessPenalty: dryness,
                        stabilityLayeringScore: stabilityLayering,
                        regionAllowance: regionAllowed,
                        regionMoistureContribution: clamp01(params.regionMoisture),
                        terrainLiftContribution: clamp01(params.terrainLift),
                        frontConvergenceContribution: clamp01(params.frontConvergence),
                        spawnSuppressionPenalty: clamp01(params.spawnSuppression)
                      };
                    }

                    function classifyKind(params, influence, stableLayer) {
                      if (stableLayer) return 'stratocumulus';
                      if (influence.rainStormContribution > 0.62 && influence.instabilityContribution > 0.62) return 'storm buildup';
                      if (params.verticalDevelopment > 0.68 || influence.instabilityContribution > 0.66) return 'congestus';
                      if (params.humidity > 62 && params.cloudCover > 38) return 'cumulus field';
                      if (params.humidity < 38) return 'dissipating';
                      return 'fair cumulus';
                    }

                    function kindCanChange(field, target) {
                      if (field.kind === target.kind) return false;
                      if (target.kind === 'storm buildup') return field.stormPotential > 0.58 && field.verticalDevelopment > 0.58 && field.maturity > 0.45;
                      if (target.kind === 'congestus') return field.verticalDevelopment > 0.52 && field.maturity > 0.34;
                      if (target.kind === 'dissipating') return field.decay > 0.45 || field.maturity < 0.18;
                      return field.maturity > 0.25;
                    }

                    function rateFor(kind, target, params) {
                      const stormSlowdown = target.stormPotential > 0.55 || target.kind === 'storm buildup' ? 0.42 : 1;
                      const dryFast = computeInfluence(params).drynessPenalty > 0.82 ? 1.7 : 1;
                      switch (kind) {
                        case 'storm': return 0.045 * stormSlowdown;
                        case 'vertical': return 0.10 * stormSlowdown;
                        case 'radius': return 0.18 * stormSlowdown;
                        case 'density': return 0.30 * dryFast * stormSlowdown;
                        case 'coverage': return 0.24 * dryFast * stormSlowdown;
                        case 'maturity': return 0.18 * stormSlowdown;
                        case 'wind': return 0.55;
                        default: return 0.20;
                      }
                    }

                    function approach(current, target, rate, dt) {
                      const alpha = 1 - Math.exp(-Math.max(0.001, rate) * dt);
                      return current + (target - current) * alpha;
                    }
                    function approachVec(current, target, rate, dt) {
                      return {
                        x: approach(current.x, target.x, rate, dt),
                        y: approach(current.y || 0, target.y || 0, rate, dt),
                        z: approach(current.z, target.z, rate, dt)
                      };
                    }

                    function metric(label, value) {
                      return '<div class=\"metric\"><strong>' + value + '</strong><span>' + label + '</span></div>';
                    }
                    function kv(label, value) {
                      return '<span>' + label + '</span><strong>' + value + '</strong>';
                    }
                    function influenceBar(label, value) {
                      return '<div style=\"margin-bottom:8px\"><span>' + label + '</span><div class=\"bar\"><i style=\"width:' + (clamp01(value) * 100).toFixed(0) + '%\"></i></div></div>';
                    }

                    function drawGrid(ctx, w, h) {
                      ctx.strokeStyle = '#26313c';
                      ctx.lineWidth = 1;
                      for (let x = -700; x <= 700; x += 100) {
                        const p0 = worldToMap({ x, z: -470 }, w, h);
                        const p1 = worldToMap({ x, z: 470 }, w, h);
                        ctx.beginPath(); ctx.moveTo(p0.x, p0.y); ctx.lineTo(p1.x, p1.y); ctx.stroke();
                      }
                      for (let z = -470; z <= 470; z += 100) {
                        const p0 = worldToMap({ x: -720, z }, w, h);
                        const p1 = worldToMap({ x: 720, z }, w, h);
                        ctx.beginPath(); ctx.moveTo(p0.x, p0.y); ctx.lineTo(p1.x, p1.y); ctx.stroke();
                      }
                    }

                    function resizeCanvas(canvas) {
                      const rect = canvas.getBoundingClientRect();
                      const scale = window.devicePixelRatio || 1;
                      const width = Math.max(1, Math.round(rect.width * scale));
                      const height = Math.max(1, Math.round(rect.height * scale));
                      if (canvas.width !== width || canvas.height !== height) {
                        canvas.width = width;
                        canvas.height = height;
                      }
                    }

                    function worldToMap(point, w, h) {
                      const scale = mapScale(w, h);
                      return { x: w * 0.5 + point.x * scale, y: h * 0.5 + point.z * scale };
                    }
                    function mapScale(w, h) { return Math.min(w / 1450, h / 960); }
                    function profileY(y, h) { return h - 20 - (clamp(y, 40, 480) - 40) / 440 * (h - 42); }
                    function directionRad(deg) { return deg * Math.PI / 180; }
                    function windVector(speed, direction) {
                      const a = directionRad(direction);
                      return { x: Math.sin(a) * speed * 0.08, y: 0, z: -Math.cos(a) * speed * 0.08 };
                    }
                    function windShear(field) {
                      return Math.min(1.1, Math.hypot(field.velocity.x, field.velocity.z) / 4.5);
                    }
                    function rotate(v, angle, shear) {
                      const c = Math.cos(angle), s = Math.sin(angle);
                      return { x: (v.x * (1 + shear) * c - v.z * s), z: (v.x * (1 + shear) * s + v.z * c) };
                    }
                    function addVec(a, b) { return { x: a.x + b.x, y: (a.y || 0) + (b.y || 0), z: a.z + b.z }; }
                    function scaleVec(v, s) { return { x: v.x * s, y: (v.y || 0) * s, z: v.z * s }; }
                    function wrap24(value) { return ((value % 24) + 24) % 24; }
                    function clamp(value, min, max) { return Math.max(min, Math.min(max, value)); }
                    function clamp01(value) { return clamp(value, 0, 1); }
                    function lerp(a, b, t) { return a + (b - a) * clamp01(t); }
                    function parseNumber(value) { return Number.parseFloat(value); }
                    function roundForUi(value) { return Number.isInteger(value) ? value : Math.round(value * 100) / 100; }
                    function stableId(seed, index) { return 'preview-' + seed + '-' + index; }
                    function mulberry32(seed) {
                      let t = seed >>> 0;
                      return function() {
                        t += 0x6D2B79F5;
                        let r = Math.imul(t ^ t >>> 15, 1 | t);
                        r ^= r + Math.imul(r ^ r >>> 7, 61 | r);
                        return ((r ^ r >>> 14) >>> 0) / 4294967296;
                      };
                    }
                    function lodColor(field) {
                      if (field.decay > 0.58 && field.maturity < 0.35) return '#fb7185';
                      if (field.lodBand === 'DYNAMIC') return field.stormPotential > 0.55 ? '#fb7185' : '#5eead4';
                      if (field.lodBand === 'TRANSITION') return '#93c5fd';
                      if (field.lodBand === 'FAR_PROCEDURAL') return '#fbbf24';
                      return '#9aa8b6';
                    }
                    function colorWithAlpha(hex, alpha) {
                      const value = hex.replace('#', '');
                      const r = parseInt(value.slice(0, 2), 16);
                      const g = parseInt(value.slice(2, 4), 16);
                      const b = parseInt(value.slice(4, 6), 16);
                      return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
                    }
                    function roundRect(ctx, x, y, w, h, r) {
                      const rr = Math.min(r, Math.abs(w) / 2, Math.abs(h) / 2);
                      ctx.beginPath();
                      ctx.moveTo(x + rr, y);
                      ctx.lineTo(x + w - rr, y);
                      ctx.quadraticCurveTo(x + w, y, x + w, y + rr);
                      ctx.lineTo(x + w, y + h - rr);
                      ctx.quadraticCurveTo(x + w, y + h, x + w - rr, y + h);
                      ctx.lineTo(x + rr, y + h);
                      ctx.quadraticCurveTo(x, y + h, x, y + h - rr);
                      ctx.lineTo(x, y + rr);
                      ctx.quadraticCurveTo(x, y, x + rr, y);
                    }

                    // TODO: real GLSL/raymarched cloud rendering belongs in a future Minecraft renderer path.
                    // This lab intentionally uses placeholder Canvas debug visuals only.
                    setupUi();
                    draw();
                    requestAnimationFrame(tick);
                    window.addEventListener('resize', draw);
                  </script>
                </body>
                </html>
                """);
    }
}
