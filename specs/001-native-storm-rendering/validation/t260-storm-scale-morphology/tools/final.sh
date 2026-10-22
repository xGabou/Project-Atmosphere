#!/bin/bash
S="C:/Users/matga/AppData/Local/Temp/claude/C--Users-matga-Downloads-Project-Atmosphere/ff1ed3c1-134c-4958-9973-76566779d319/scratchpad"
cd C:/Users/matga/Downloads/Project-Atmosphere
CP=$(cat $S/cp.txt); O=run/t2609
rm -f $O/smoke.log $O/world.log
java -Xmx14g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T2609Preview plan 2609101,2609102,2609103,2609104 > $O/plan.log 2>&1
java -Xmx14g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T2609Preview world $O/world 2609101,2609102,2609103,2609104 22,44 c4,t9 side,three_quarter,gameplay .6 > $O/world.log 2>&1
for sd in 2609101 2609102 2609103 2609104; do PA_T2608_VARIANT=4 java -Xmx14g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T2608Preview smoke system $sd 40 >> $O/smoke.log 2>&1; done
PA_T2608_VARIANT=4 java -Xmx14g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T2608Preview smoke three_free 2608402 32 >> $O/smoke.log 2>&1
PA_T2608_VARIANT=4 java -Xmx14g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T2608Preview smoke system 2609104 30 >> $O/smoke.log 2>&1
echo FINAL_DONE >> $O/smoke.log
