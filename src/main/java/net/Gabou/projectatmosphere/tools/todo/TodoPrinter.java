package net.Gabou.projectatmosphere.tools.todo;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class TodoPrinter {

    static class Task {
        final String description;
        boolean done;

        Task(String description) {
            this.description = description;
            this.done = false;
        }

        String toMarkdown() {
            return String.format("- [%s] %s", done ? "x" : " ", description);
        }
    }

    static class Section {
        final String title;
        final List<Task> tasks = new ArrayList<>();

        Section(String title) {
            this.title = title;
        }

        void add(String description) {
            tasks.add(new Task(description));
        }

        String toMarkdown() {
            StringBuilder sb = new StringBuilder("## " + title + "\n\n");
            for (Task task : tasks) {
                sb.append(task.toMarkdown()).append("\n");
            }
            return sb.toString();
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        List<Section> sections = new ArrayList<>();

        
        Section clouds = new Section("â˜ï¸ Cloud System");
        clouds.add("Implement GeckoLib CloudEntity");
        clouds.add("Add cloud variants: cumulus, cirrus, fog");
        clouds.add("Add wind drifting logic");
        clouds.add("Detect and merge cloud entities");
        clouds.add("Save merged state in NBT with original identity");
        clouds.add("Restore original clouds after split");
        sections.add(clouds);

        
        Section fog = new Section("ðŸŒ«ï¸ Fog System");
        fog.add("Add morning fog when temperature < 10Â°C");
        fog.add("Customize fog by biome: jungle, swamp, plains");
        fog.add("Fade fog after sunrise or player descent");
        fog.add("Use safe Forge hook to override vanilla fog");
        sections.add(fog);

        
        Section shader = new Section("ðŸ§ª Shader Compatibility");
        shader.add("Do not override sky renderer (retain shader support)");
        shader.add("Add config option: cloudMode FULL | HYBRID | VANILLA");
        shader.add("Ensure visual blend for vanilla + custom clouds");
        shader.add("Test with Complementary, BSL, SEUS, Iris+Sodium");
        sections.add(shader);

        
        Section weather = new Section("ðŸŒ¦ï¸ Weather Manager");
        weather.add("Create server-authoritative WeatherManager");
        weather.add("Override vanilla rain/thunder states");
        weather.add("Sync weather state to clients");
        weather.add("Simulate 7-day forecast based on season + temperature");
        weather.add("Forecast includes random Â±10Â°C scaling");
        weather.add("Final displayed temp varies Â±3Â°C daily");
        weather.add("Generate storms using season + wind + temperature");
        sections.add(weather);

        
        Section extreme = new Section("ðŸŒªï¸ Extreme Weather Events");
        extreme.add("Implement TornadoEvent (wind + suction)");
        extreme.add("Implement HurricaneEvent (wide-area wind + rain)");
        extreme.add("Implement SandstormEvent (reduced visibility + particles)");
        extreme.add("Implement SnowstormEvent with snow accumulation");
        extreme.add("Handle spring melt logic for snow_pile blocks");
        sections.add(extreme);

        
        Section temp = new Section("ðŸŒ¡ï¸ Temperature System");
        temp.add("Integrate Serene Seasons API");
        temp.add("Add fallback temperature simulation if SS missing");
        temp.add("Mixin to override temperature source if needed");
        temp.add("Expose API to other mods for temp reading");
        temp.add("Allow forecast-based temperature overrides");
        sections.add(temp);

        
        Section rain = new Section("ðŸŒ¾ Rain & Crops");
        rain.add("Integrate with Farmerâ€™s Delight: rain helps growth");
        rain.add("Track rainfall per chunk");
        rain.add("Convert dirt to corpse dirt if too much rain");
        rain.add("Only affect uncovered farmland");
        sections.add(rain);

        
        Section config = new Section("âš™ï¸ Config & Performance");
        config.add("Create projectatmosphere.toml config");
        config.add("Options: fog, cloud mode, forecast deviation, event toggle");
        config.add("Use patterns: Observer, Strategy, Factory");
        config.add("Avoid BlockEntities â€” use chunk managers");
        config.add("Memoize biome â†’ weather lookup");
        config.add("Separate ClientWeatherHandler and ServerWeatherHandler");
        sections.add(config);

        
        Section compat = new Section("ðŸ§© Mod Compatibility");
        compat.add("Farmerâ€™s Delight");
        compat.add("Naturalist");
        compat.add("Immersive Weathering");
        compat.add("Tough As Nails");
        compat.add("Epic Fight");
        sections.add(compat);

        
        while (true) {
            System.out.println("\n==== Project Atmosphere TODO ====");
            for (int i = 0; i < sections.size(); i++) {
                System.out.printf("%d. %s\n", i + 1, sections.get(i).title);
            }
            System.out.print("Select section to update (0 to finish): ");
            int sectionIndex = scanner.nextInt() - 1;
            if (sectionIndex < 0 || sectionIndex >= sections.size()) break;

            Section section = sections.get(sectionIndex);
            while (true) {
                System.out.println("\n-- " + section.title + " --");
                for (int j = 0; j < section.tasks.size(); j++) {
                    Task task = section.tasks.get(j);
                    System.out.printf("%d. [%s] %s\n", j + 1, task.done ? "x" : " ", task.description);
                }
                System.out.print("Toggle task number (0 to go back): ");
                int taskIndex = scanner.nextInt() - 1;
                if (taskIndex < 0 || taskIndex >= section.tasks.size()) break;
                section.tasks.get(taskIndex).done = !section.tasks.get(taskIndex).done;
            }
        }

        
        System.out.print("Enter filename to save as (e.g., ProjectAtmosphereTODO.md): ");
        scanner.nextLine();  
        String filename = scanner.nextLine();

        try (FileWriter writer = new FileWriter(filename)) {
            writer.write("# ðŸŒ©ï¸ Project Atmosphere â€“ TODO Checklist\n\n");
            for (Section section : sections) {
                writer.write(section.toMarkdown());
                writer.write("\n");
            }
            System.out.println("âœ… TODO list saved to " + filename);
        } catch (IOException e) {
            System.err.println("âŒ Error saving file: " + e.getMessage());
        }
    }
}
