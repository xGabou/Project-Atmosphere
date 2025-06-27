import json
import os

# Dossier de sortie
output_dir = "dust_models"
os.makedirs(output_dir, exist_ok=True)

# Texture path
texture_path = "projectatmosphere:block/dust"

# Génération des 8 couches
for layer in range(1, 9):
    height = layer * 2  # Chaque couche fait 2 unités (1/8 bloc)
    model = {
        "parent": "block/block",
        "textures": {
            "particle": texture_path,
            "layer": texture_path
        },
        "elements": [
            {
                "from": [0, 0, 0],
                "to": [16, height, 16],
                "faces": {
                    "up":    { "texture": "#layer" },
                    "down":  { "texture": "#layer" },
                    "north": { "texture": "#layer" },
                    "south": { "texture": "#layer" },
                    "west":  { "texture": "#layer" },
                    "east":  { "texture": "#layer" }
                }
            }
        ]
    }

    filename = f"dust_layer_{layer}.json"
    filepath = os.path.join(output_dir, filename)
    with open(filepath, "w") as f:
        json.dump(model, f, indent=4)

print(f"✅ Fichiers générés dans le dossier '{output_dir}'")
