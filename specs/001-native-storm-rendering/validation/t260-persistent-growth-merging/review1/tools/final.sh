#!/bin/bash
S="C:/Users/matga/AppData/Local/Temp/claude/C--Users-matga-Downloads-Project-Atmosphere/ff1ed3c1-134c-4958-9973-76566779d319/scratchpad"
cd C:/Users/matga/Downloads/Project-Atmosphere
J="java -Xmx14g -cp $(cat $S/cp.txt) net.Gabou.projectatmosphere.clouds.morphology.T2608Preview"; O=run/t2608r
rm -f $O/smoke.log
for sd in 2608401 2608402 2608403; do $J compare $O/final three_free $sd 12,26,44 0,1,2 three_quarter,side,close .6 > $O/cmp_$sd.log 2>&1; done
$J compare $O/final pair 2608201 14,26,46 0,1,2 three_quarter,side .6 > $O/cmp_pair.log 2>&1
for v in 1 2; do for sc in "three_free 2608401 44" "three_free 2608402 32" "pair 2608201 34" "single 2608101 30"; do PA_T2608_VARIANT=$v $J smoke $sc >> $O/smoke.log 2>&1; done; done
PA_T2608_VARIANT=0 $J smoke single 2608101 30 >> $O/smoke.log 2>&1
echo FINAL_DONE >> $O/smoke.log
