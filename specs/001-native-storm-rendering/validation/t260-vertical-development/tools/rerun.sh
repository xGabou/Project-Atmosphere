#!/bin/bash
cd "$REPO_ROOT"
CP="$TEST_CLASSPATH"; O=run/t26010
PA_T2608_VARIANT=4 java -Xmx20g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T2608Preview smoke deep 2609102 40 > $O/smoke2.log 2>&1
java -Xmx20g -cp "$CP" net.Gabou.projectatmosphere.clouds.morphology.T26010Preview world $O/world40 2609102,2609103,2609104,2609101 40 t10 side,three_quarter,overview,gameplay .6 > $O/world40.log 2>&1
echo RERUN_DONE >> $O/world40.log
