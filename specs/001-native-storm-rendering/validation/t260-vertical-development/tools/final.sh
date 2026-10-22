#!/bin/bash
cd "$REPO_ROOT"
CP="$(cat cp.txt)"; O=run/t26010
rm -f $O/smoke.log $O/world.log
java -Xmx8g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T26010Preview plan 2609101,2609102,2609103,2609104 12,40 > $O/plan.log 2>&1
java -Xmx20g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T26010Preview world $O/world 2609101,2609102,2609103,2609104 12,40 t9,t10 side,three_quarter,overview,gameplay .6 > $O/world.log 2>&1
echo WORLD_DONE >> $O/world.log
PA_T2608_VARIANT=4 java -Xmx20g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T2608Preview smoke three_free 2608402 32 >> $O/smoke.log 2>&1
PA_T2608_VARIANT=4 java -Xmx20g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T2608Preview smoke system 2609104 30 >> $O/smoke.log 2>&1
PA_T2608_VARIANT=4 java -Xmx20g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T2608Preview smoke deep 2609104 12 >> $O/smoke.log 2>&1
PA_T2608_VARIANT=4 java -Xmx20g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T2608Preview smoke deep 2609102 12 >> $O/smoke.log 2>&1
echo FINAL_DONE >> $O/smoke.log
