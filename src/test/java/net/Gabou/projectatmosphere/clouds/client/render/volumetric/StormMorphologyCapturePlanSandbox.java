package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveGrowthFieldA;
import net.Gabou.projectatmosphere.clouds.morphology.ConvectiveGrowthFieldPrototype;
import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import java.util.List;

/** Capture-only tests. Does not alter either candidate or initialize Minecraft. */
public final class StormMorphologyCapturePlanSandbox {
    private static int assertions;
    private static void require(boolean v,String text) {assertions++;if(!v)throw new AssertionError(text);}
    public static void main(String[] args) throws Exception {
        var names=List.of("T257_x__1__side","T257_x__1__a_side","T257_x__1__front","T257_x__1__a_front",
                "T257_x__1__attribution_intervals","T257_x__1__attribution_exact");
        require(StormMorphologyCapturePlan.select(names,List.of(names.get(0),names.get(1))).size()==2,"only requested pair");
        for(var bad:List.of(List.of(names.get(0)),List.of("unknown"),List.of(names.get(0),names.get(0)),List.of(names.get(4)))) {
            boolean rejected=false;try{StormMorphologyCapturePlan.select(names,bad);}catch(IllegalArgumentException expected){rejected=true;}
            require(rejected,"reject incomplete/unknown/duplicate selection");
        }
        for(String type:List.of("cumulonimbus_calvus","cumulonimbus_capillatus","supercell"))for(long seed:new long[]{20261008L,20268927L,20276846L}) {
            var shape=CloudShapeProfile.defaultFor(type,CloudFamily.CUMULONIMBUS,null);
            var a=ConvectiveGrowthFieldA.compile(type,shape,seed);var b=ConvectiveGrowthFieldPrototype.compile(type,shape,seed);
            var signatures=new long[]{a.signature(),b.signature()};
            for(double[] pose:List.of(new double[]{650,390,1210,650,390,0},new double[]{-560,390,0,650,390,0},
                    new double[]{-1550,390,0,650,390,0},new double[]{650,2590,440,650,390,0},
                    new double[]{650,-750,880,650,390,0},new double[]{-905,-750,1555,650,390,0},
                    new double[]{650,390,-2200,650,390,0},new double[]{5050,390,0,650,390,0})) {
                pose=pose.clone();pose[1]+=1400;pose[4]+=1400;
                var fitted=StormMorphologyCapturePlan.fit(pose,a.bounds(),b.bounds(),0,1400,0,110,1600.0/900,650);
                var reversed=StormMorphologyCapturePlan.fit(pose,b.bounds(),a.bounds(),0,1400,0,110,1600.0/900,650);
                for(int j=0;j<6;j++)require(fitted[j]==reversed[j],"A/B order cannot change pose");
                require(fitted[1]>=650,"clear terrain");
                require(StormMorphologyCapturePlan.fits(fitted,a.bounds(),0,1400,0,110,1600.0/900,.91),"A bounds visible");
                require(StormMorphologyCapturePlan.fits(fitted,b.bounds(),0,1400,0,110,1600.0/900,.91),"B bounds visible");
                require(a.signature()==signatures[0]&&b.signature()==signatures[1],"camera does not modify geometry");
            }
        }
        int replayed=0;
        var posePattern=java.util.regex.Pattern.compile("T257_POSE shot=T257_(cumulonimbus_calvus|cumulonimbus_capillatus|supercell)__(\\d+)__(\\S+) version=(\\d+) signature=(\\S+)");
        var capturePattern=java.util.regex.Pattern.compile("T098_CAPTURE shot=(T257_\\S+)");
        for(String log:args) {
            var lines=java.nio.file.Files.readAllLines(java.nio.file.Path.of(log));var captured=new java.util.HashSet<String>();
            for(String line:lines){var m=capturePattern.matcher(line);if(m.find())captured.add(m.group(1));}
            for(String line:lines) {
                var m=posePattern.matcher(line);if(!m.find())continue;
                String type=m.group(1),view=m.group(3);long seed=Long.parseLong(m.group(2));
                if(!captured.contains("T257_"+type+"__"+seed+"__"+view))continue;
                var shape=CloudShapeProfile.defaultFor(type,CloudFamily.CUMULONIMBUS,null);
                if(view.startsWith("control_")) {
                    String arm=view.substring(8);
                    shape=new CloudShapeProfile("prototype/control",shape.getBaseRadius(),shape.getBaseOffset(),shape.getTopOffset(),
                            shape.getLobeCountMin(),shape.getLobeCountMax(),shape.getLobeStrength(),arm.equals("tilt")?.8f:0,
                            arm.equals("shear")?.8f:0,arm.equals("split")?1:0,arm.equals("narrowing")?1:0,
                            shape.getAnvilSpread(),shape.getBaseFlattening(),shape.getEdgeRaggedness(),arm.equals("wall")?1:0);
                }
                net.Gabou.projectatmosphere.clouds.morphology.StormGeometryProgram program=view.startsWith("a_")
                        ?ConvectiveGrowthFieldA.compile(type,shape,seed):ConvectiveGrowthFieldPrototype.compile(type,shape,seed);
                require(program.version()==Integer.parseInt(m.group(4)),"captured recipe version unchanged");
                require(Long.toUnsignedString(program.signature(),16).equals(m.group(5)),"captured recipe signature unchanged: "+view);
                replayed++;
            }
        }
        System.out.println("T257_CAPTURE_PLAN assertions="+assertions+" replayed="+replayed+" pass=true; geometry frozen");
    }
}
