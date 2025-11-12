import os, json, random
from tqdm import tqdm

SRC = r"G:\Project-Atmosphere"
OUT = "datasets/PANewDynamicWeatherForge1201WIPmc_auto_generated.jsonl"

def summarize(file, code):
    """Return a short automatic summary seed."""
    name = os.path.basename(file)
    if "Mixin" in name: return f"This class injects behavior into {name.replace('Mixin','')}."
    if "Manager" in name: return f"{name} controls a gameplay or system manager, handling updates and synchronization."
    if "Compat" in name: return f"{name} provides compatibility with other mods or APIs."
    if "Handler" in name: return f"{name} listens for events or server ticks."
    return f"{name} defines part of the gameplay or rendering logic."

samples = []

for root, _, files in os.walk(SRC):
    for file in files:
        if not file.endswith(".java"): continue
        path = os.path.join(root, file)
        with open(path, "r", encoding="utf-8", errors="ignore") as f:
            code = f.read()

        code_excerpt = code[:1200]

        # a few random instruction patterns
        instructions = [
            f"Explain the purpose and behavior of {file}.",
            f"Refactor {file} to improve readability without changing logic.",
            f"Generate Forge or Fabric documentation for {file}.",
            f"Summarize key methods and variables in {file}.",
            f"Describe how {file} interacts with other modules."
        ]

        for inst in random.sample(instructions, 2):
            entry = {
                "instruction": inst,
                "input": code_excerpt,
                "output": summarize(file, code)
            }
            samples.append(entry)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as out:
    for s in samples:
        out.write(json.dumps(s) + "\n")

print(f"✅ Dataset built with {len(samples)} examples -> {OUT}")
