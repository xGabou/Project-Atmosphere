import json, sys, statistics as st
for log in sys.argv[1:]:
    print('==', log.split('/')[-1])
    for line in open(log, encoding='utf-8', errors='replace'):
        if not line.startswith('T260_GATES '): continue
        g = json.loads(line[11:]); A = g['gateA_cell']; B = g['gateB_system']
        sp = B['profiles']
        fails = [k for k, v in B['checks'].items() if v is False or (isinstance(v, list) and v[-1] is False)]
        failsA = [k for k, v in A['checks'].items() if v is False or (isinstance(v, list) and v[-1] is False)]
        print(f"{g['seed']} A={'P' if A['pass'] else 'F'+str(failsA)} B={'P' if B['pass'] else 'F'+str(fails)} "
              f"sysAsp={st.mean(p['aspect'] for p in sp):.2f}[{min(p['aspect'] for p in sp):.2f}-{max(p['aspect'] for p in sp):.2f}] "
              f"cd={st.mean(p['convexityDeficit'] for p in sp):.3f} notch={st.mean(p['notches'] for p in sp):.1f} deep={st.mean(p['deepestNotch'] for p in sp):.3f} "
              f"towers={st.mean(p['towers'] for p in sp):.1f} mass={B['lowerMass']} gap={B['connectivity']['minGapVoxels']} c3d={B['connectivity']['components3d']} "
              f"MB={g['textureBytes']/1048576:.1f} bricks={g['bricks']} bubbles={g['bubbles']}")
