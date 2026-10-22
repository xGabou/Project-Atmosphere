package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

/** Actual analytic evaluator rendered orthographically; diagnostics, NOT native acceptance. */
public final class GrowthDefinedStormPreview {
    public static void main(String[] args) throws Exception {
        var root=Path.of("specs/001-native-storm-rendering/validation/t256-storm-prototype/cpu-v"+GrowthDefinedStormPrototype.VERSION);
        for(var type:new String[]{"cumulonimbus_calvus","cumulonimbus_capillatus","supercell"}) {
            for(long seed:args.length>0?new long[]{20261008}:new long[]{20261008,20268927,20276846}) {
                var shape=CloudShapeProfile.defaultFor(type,CloudFamily.CUMULONIMBUS,null);
                var p=GrowthDefinedStormPrototype.compile(type,shape,seed);
                var folder=root.resolve(type).resolve(Long.toString(seed));Files.createDirectories(folder);
                for(String view:args.length>0?new String[]{"side","above","close","updraft_oblique"}:new String[]{"side","opposite_side","front","back","above","below","low_angle","far","close","updraft_oblique"}) {
                    ImageIO.write(StormMorphologyPreview.render(p,view),"png",folder.resolve(view+".png").toFile());
                }
                if(seed==20261008)for(String view:new String[]{"side","above","close"}) {
                    ImageIO.write(StormMorphologyPreview.render(p.withEnvelope(true),view),"png",folder.resolve("envelope_A_"+view+".png").toFile());
                    ImageIO.write(StormMorphologyPreview.render(StormGeometryProgram.legacy(StormMorphologyPrototype.compile(type,shape,seed)),view),"png",folder.resolve("T255_"+view+".png").toFile());
                }
                System.out.println(type+" seed="+seed+" signature="+Long.toUnsignedString(p.signature(),16)+" growth="+p.growth().size()+" canopy="+p.canopies().size()+" ice="+p.ice().size());
            }
        }
        System.out.println((args.length>0?30:108)+" CPU diagnostics; NOT native/performance/visual approval.");
    }
}
