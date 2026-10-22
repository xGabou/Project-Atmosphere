#!/bin/bash
S="C:/Users/matga/AppData/Local/Temp/claude/C--Users-matga-Downloads-Project-Atmosphere/ff1ed3c1-134c-4958-9973-76566779d319/scratchpad"
cd C:/Users/matga/Downloads/Project-Atmosphere
J="java -Xmx14g -cp $(cat $S/cp.txt) net.Gabou.projectatmosphere.clouds.morphology.T2608Preview"; O=run/t2608
$J stages $O/stages single 2608101 0,10,20,30,40 three_quarter,side .6 > $O/s1.log 2>&1
$J stages $O/stages pair 2608201 0,14,26,34,46 three_quarter,side .6 > $O/s2.log 2>&1
for sd in 2608301 2608302 2608303; do $J stages $O/stages three $sd 0,12,22,32,44 three_quarter,side .6 > $O/s3_$sd.log 2>&1; done
$J anim $O/anim pair 2608201 0 46 1 three_quarter .4 > $O/a2.log 2>&1
$J anim $O/anim single 2608101 0 40 1 three_quarter .4 > $O/a1.log 2>&1
for sc in "single 2608101 30" "pair 2608201 40" "three 2608301 32"; do $J smoke $sc >> $O/smoke.log 2>&1; done
echo FINAL_DONE >> $O/smoke.log
