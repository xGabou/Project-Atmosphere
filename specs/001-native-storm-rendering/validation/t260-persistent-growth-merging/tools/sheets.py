"""T260.8 contact sheets and animations from the CPU preview renders."""
import os, sys, glob
sys.argv = [sys.argv[0], sys.argv[1], sys.argv[2]]
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "t2607", "sheets.py"), encoding="utf-8").read().split("VIEWS = [")[0])
from PIL import Image

ST = f"{SRC}/stages"
V = [("three_quarter", "three-quarter"), ("side", "side")]
def stagecols(times, labels):
    return [(str(i), f"t={t}  {l}") for i, (t, l) in enumerate(zip(times, labels))]

sheet("s1_single_growth.png", "T260.8 S1 - one persistent cell: cumulus -> towering -> congestus -> calvus (seed 2608101, fixed camera, fixed domain)",
      stagecols([0, 10, 20, 30, 40], ["small cumulus", "vertical growth", "congestus", "developing calvus", "calvus"]), V,
      lambda st, v: f"{ST}/single_2608101_stage{st}_{v}.png", scale=.75)
sheet("s2_pair_merging.png", "T260.8 S2 - two independent cells merge (seed 2608201): A independent, B approaching, C connecting, D organised, E further development",
      stagecols([0, 14, 26, 34, 46], ["A independent", "B approaching", "C connecting", "D organised", "E development"]), V,
      lambda st, v: f"{ST}/pair_2608201_stage{st}_{v}.png", scale=.75)
sheet("s3_three_cells.png", "T260.8 S3 - three cells (deep D / broad W / weak S), seed 2608301",
      stagecols([0, 12, 22, 32, 44], ["", "", "", "", ""]), V,
      lambda st, v: f"{ST}/three_2608301_stage{st}_{v}.png", scale=.75)
sheet("s3_seeds.png", "T260.8 S3 - same roles, three seeds (placement, timing, anatomy differ) - three-quarter",
      stagecols([0, 12, 22, 32, 44], ["", "", "", "", ""]), [(s, f"seed {s}") for s in ["2608301", "2608302", "2608303"]],
      lambda st, s: f"{ST}/three_{s}_stage{st}_three_quarter.png", scale=.75)

for name in ["pair_2608201", "single_2608101"]:
    fr = sorted(glob.glob(f"{SRC}/anim/{name}_three_quarter_f*.png"))
    if not fr: continue
    ims = [Image.open(f).convert("P", palette=Image.ADAPTIVE) for f in fr]
    ims[0].save(os.path.join(OUT, f"anim_{name}.gif"), save_all=True, append_images=ims[1:], duration=180, loop=0)
    print("wrote anim", name, len(ims))

sheet("s3_free_seeds.png", "T260.8 S3 seed-driven variant - no assigned roles; each cell's potential, size, timing and fate come from its seed - three-quarter",
      stagecols([0, 12, 22, 32, 44], ["", "", "", "", ""]), [(s, f"seed {s}") for s in ["2608401", "2608402", "2608403"]],
      lambda st, s: f"{ST}/three_free_{s}_stage{st}_three_quarter.png", scale=.75)
