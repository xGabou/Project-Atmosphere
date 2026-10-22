#!/bin/bash
S="C:/Users/matga/AppData/Local/Temp/claude/C--Users-matga-Downloads-Project-Atmosphere/ff1ed3c1-134c-4958-9973-76566779d319/scratchpad"
cd C:/Users/matga/Downloads/Project-Atmosphere
CP="$(cat $S/cp.txt)"; M=net.Gabou.projectatmosphere.clouds.morphology.T2607Preview; O=C:/Users/matga/Downloads/Project-Atmosphere/run/t2607
java -Xmx14g -cp "$CP" $M render $O/g_surface 20261015,20265193,20268011 B,V1,V2,V3 side,three_quarter,close .5 > $O/g_surface.log 2>&1
PA_T2607_COMMON=1 java -Xmx14g -cp "$CP" $M render $O/g_system 20261015 V2,V2S=COMPACT,V2S=DOMINANT,V2S=MULTICELL,V2S=WIDE,V2S=ASYMMETRIC side,three_quarter .5 > $O/g_system.log 2>&1
for s in 20265193 20268011; do PA_T2607_COMMON=1 java -Xmx14g -cp "$CP" $M render $O/g_system $s V2,V2S side,three_quarter .5 >> $O/g_system.log 2>&1; done
java -Xmx14g -cp "$CP" $M grow $O/g_grow 20261015 B,BP,V3,V3P 5 4 side,close .5 > $O/g_grow.log 2>&1
echo FINAL_DONE >> $O/g_grow.log
