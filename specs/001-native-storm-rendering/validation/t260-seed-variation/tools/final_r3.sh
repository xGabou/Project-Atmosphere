#!/bin/bash
S="C:/Users/matga/AppData/Local/Temp/claude/C--Users-matga-Downloads-Project-Atmosphere/ff1ed3c1-134c-4958-9973-76566779d319/scratchpad"
cd C:/Users/matga/Downloads/Project-Atmosphere
CP="$(cat $S/cp.txt)"; M=net.Gabou.projectatmosphere.clouds.morphology.T2607Preview; O=C:/Users/matga/Downloads/Project-Atmosphere/run/t2607
java -Xmx14g -cp "$CP" $M render $O/r_surface 20261015,20265193,20268011 R3 side,three_quarter,close,gameplay .5 > $O/r3_surface.log 2>&1
java -Xmx14g -cp "$CP" $M grow $O/r_grow 20261015 R2P,R3P 5 4 side,close .5 > $O/r3_grow.log 2>&1
echo FINAL_DONE >> $O/r3_grow.log
