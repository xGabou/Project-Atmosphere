#!/bin/bash
S="C:/Users/matga/AppData/Local/Temp/claude/C--Users-matga-Downloads-Project-Atmosphere/ff1ed3c1-134c-4958-9973-76566779d319/scratchpad"
cd C:/Users/matga/Downloads/Project-Atmosphere
J="java -Xmx14g -cp $(cat $S/cp.txt) net.Gabou.projectatmosphere.clouds.morphology.T2608Preview"; O=run/t2608r3
rm -f $O/smoke.log
for sd in 2608401 2608402 2608403; do $J compare $O/final three_free $sd 22,44 1,3,4 three_quarter,side,close,transition .6 > $O/cmp_$sd.log 2>&1; done
for sc in "three_free 2608401 44" "three_free 2608402 32" "three_free 2608403 44" "pair 2608201 34"; do PA_T2608_VARIANT=4 $J smoke $sc >> $O/smoke.log 2>&1; done
for v in 0 1 3; do PA_T2608_VARIANT=$v $J smoke single 2608101 30 >> $O/smoke.log 2>&1; done
echo FINAL_DONE >> $O/smoke.log
