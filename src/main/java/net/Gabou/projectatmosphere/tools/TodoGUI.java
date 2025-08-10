package net.Gabou.projectatmosphere.tools;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

import com.google.gson.*;
import com.google.gson.reflect.*;

public class TodoGUI extends JFrame {

    static class Task {
        String description;
        boolean done;

        Task(String description) {
            this.description = description;
            this.done = false;
        }
    }

    static class Section {
        String title;
        List<Task> tasks = new ArrayList<>();

        Section(String title) {
            this.title = title;
        }

        void add(String description) {
            tasks.add(new Task(description));
        }
    }

    private final List<Section> sections = new ArrayList<>();
    private static final String SAVE_FILE = "todo_progress.json";

    public TodoGUI() {
        setTitle("Project Atmosphere – Interactive TODO");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        add(scrollPane, BorderLayout.CENTER);

        if (!loadFromJson()) {
            loadTasks();
        }

        for (Section section : sections) {
            JLabel sectionLabel = new JLabel("📌 " + section.title);
            sectionLabel.setFont(new Font("Arial", Font.BOLD, 16));
            contentPanel.add(sectionLabel);

            for (Task task : section.tasks) {
                JCheckBox box = new JCheckBox(task.description, task.done);
                box.addActionListener(e -> task.done = box.isSelected());
                contentPanel.add(box);
            }

            contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        }

        JButton saveButton = new JButton("Save Markdown + Progress");
        saveButton.addActionListener(e -> {
            saveToMarkdown();
            saveToJson();
        });

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(saveButton);
        add(bottomPanel, BorderLayout.SOUTH);

        setSize(600, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void loadTasks() {
        Section clouds = new Section("Cloud System");
        clouds.add("Implement GeckoLib CloudEntity");
        clouds.add("Add cloud variants: cumulus, cirrus, fog");
        clouds.add("Add wind drifting logic");
        clouds.add("Detect and merge cloud entities");
        clouds.add("Save merged state in NBT with original identity");
        clouds.add("Restore original clouds after split");
        sections.add(clouds);

        Section fog = new Section("Fog System");
        fog.add("Add morning fog when temperature < 10°C");
        fog.add("Customize fog by biome: jungle, swamp, plains");
        fog.add("Fade fog after sunrise or player descent");
        fog.add("Use safe Forge hook to override vanilla fog");
        sections.add(fog);

        Section shader = new Section("Shader Compatibility");
        shader.add("Do not override sky renderer (retain shader support)");
        shader.add("Add config option: cloudMode FULL | HYBRID | VANILLA");
        shader.add("Ensure visual blend for vanilla + custom clouds");
        shader.add("Test with Complementary, BSL, SEUS, Iris+Sodium");
        sections.add(shader);

        Section weather = new Section("Weather Manager");
        weather.add("Create server-authoritative WeatherManager");
        weather.add("Override vanilla rain/thunder states");
        weather.add("Sync weather state to clients");
        weather.add("Simulate 7-day forecast based on season + temperature");
        weather.add("Forecast includes random ±10°C scaling");
        weather.add("Final displayed temp varies ±3°C daily");
        weather.add("Generate storms using season + wind + temperature");
        sections.add(weather);

        Section extreme = new Section("Extreme Weather Events");
        extreme.add("Implement TornadoEvent (wind + suction)");
        extreme.add("Implement HurricaneEvent (wide-area wind + rain)");
        extreme.add("Implement SandstormEvent (reduced visibility + particles)");
        extreme.add("Implement SnowstormEvent with snow accumulation");
        extreme.add("Handle spring melt logic for snow_pile blocks");
        sections.add(extreme);

        Section temp = new Section("Temperature System");
        temp.add("Integrate Serene Seasons API");
        temp.add("Add fallback temperature simulation if SS missing");
        temp.add("Mixin to override temperature source if needed");
        temp.add("Expose API to other mods for temp reading");
        temp.add("Allow forecast-based temperature overrides");
        sections.add(temp);

        Section rain = new Section("Rain & Crops");
        rain.add("Integrate with Farmer’s Delight: rain helps growth");
        rain.add("Track rainfall per chunk");
        rain.add("Convert dirt to corpse dirt if too much rain");
        rain.add("Only affect uncovered farmland");
        sections.add(rain);

        Section config = new Section("Config & Performance");
        config.add("Create projectatmosphere.toml config");
        config.add("Options: fog, cloud mode, forecast deviation, event toggle");
        config.add("Use patterns: Observer, Strategy, Factory");
        config.add("Avoid BlockEntities — use chunk managers");
        config.add("Memoize biome → weather lookup");
        config.add("Separate ClientWeatherHandler and ServerWeatherHandler");
        sections.add(config);

        Section compat = new Section("Mod Compatibility");
        compat.add("Farmer’s Delight");
        compat.add("Naturalist");
        compat.add("Immersive Weathering");
        compat.add("Tough As Nails");
        compat.add("Epic Fight");
        sections.add(compat);
    }

    private boolean loadFromJson() {
        try (Reader reader = new FileReader(SAVE_FILE)) {
            Gson gson = new Gson();
            List<Section> loaded = gson.fromJson(reader, new TypeToken<List<Section>>() {}.getType());
            if (loaded != null) {
                sections.clear();
                sections.addAll(loaded);
                return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private void saveToJson() {
        try (Writer writer = new FileWriter(SAVE_FILE)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(sections, writer);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save progress: " + e.getMessage());
        }
    }

    private void saveToMarkdown() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save TODO as Markdown");
        chooser.setSelectedFile(new File("ProjectAtmosphereTODO.md"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter writer = new FileWriter(chooser.getSelectedFile())) {
                writer.write("# 🌩️ Project Atmosphere – TODO Checklist\\n\\n");
                for (Section section : sections) {
                    writer.write("## " + section.title + "\\n\\n");
                    for (Task task : section.tasks) {
                        writer.write(String.format("- [%s] %s\\n", task.done ? "x" : " ", task.description));
                    }
                    writer.write("\\n");
                }
                JOptionPane.showMessageDialog(this, "✅ Markdown and progress saved!");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "❌ Error: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TodoGUI::new);
    }
}
