package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Proves final source still reconstructs every recipe measured by the native campaign. */
public final class GrowthDefinedStormCaptureReplay {
    public static void main(String[] args) throws Exception {
        var log=Path.of(args.length>0?args[0]:"specs/001-native-storm-rendering/validation/t256-native-v3-full.log");
        var pattern=Pattern.compile("T256_POSE shot=T256_(cumulonimbus_calvus|cumulonimbus_capillatus|supercell)__(\\d+)__(\\S+) version=(\\d+) signature=(\\S+)");
        int checked=0;
        for(var line:Files.readAllLines(log)) {
            var m=pattern.matcher(line);if(!m.find())continue;
            var type=m.group(1);var view=m.group(3);long seed=Long.parseLong(m.group(2));
            var shape=CloudShapeProfile.defaultFor(type,CloudFamily.CUMULONIMBUS,null);
            if(view.startsWith("control_")) {
                var arm=view.substring(8);
                shape=new CloudShapeProfile("prototype/control",shape.getBaseRadius(),shape.getBaseOffset(),shape.getTopOffset(),shape.getLobeCountMin(),shape.getLobeCountMax(),shape.getLobeStrength(),
                        arm.equals("tilt")?.8f:0,arm.equals("shear")?.8f:0,arm.equals("split")?1:0,arm.equals("narrowing")?1:0,
                        shape.getAnvilSpread(),shape.getBaseFlattening(),shape.getEdgeRaggedness(),arm.equals("wall")?1:0);
            }
            StormGeometryProgram p;
            if(view.startsWith("baseline_T255_"))p=StormGeometryProgram.legacy(StormMorphologyPrototype.compile(type,shape,seed));
            else {
                var candidate=GrowthDefinedStormPrototype.compile(type,shape,seed).withEnvelope(view.startsWith("envelope_A_"));
                candidate=candidate.withInspectionMask(switch(view) {case "cost_no_canopy" -> 1;case "cost_no_ice" -> 2;case "cost_hard_merge" -> 4;case "cost_counts" -> 8;default -> 0;});
                p=candidate;
            }
            if(p.version()!=Integer.parseInt(m.group(4))||!Long.toUnsignedString(p.signature(),16).equals(m.group(5))) {
                throw new AssertionError("Final source recipe differs from native capture: "+type+" / "+seed+" / "+view);
            }
            checked++;
        }
        if(checked!=193)throw new AssertionError("Expected complete193-arm log; replayed "+checked);
        System.out.println("T256 final-source native recipe replay: "+checked+"/193 signatures/version match, including T255/envelope/cost/control arms.");
    }
}
