package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudFamily;
import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import java.util.Arrays;
import java.util.SplittableRandom;

/** Structural contracts, not screenshot/pixel acceptance. */
public final class GrowthDefinedStormSandbox {
    private static int assertions;
    private static void require(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (String type : new String[]{"cumulonimbus_calvus", "cumulonimbus_capillatus", "supercell"}) {
            for (long seed : new long[]{20261008, 20268927, 20276846}) {
                var shape = CloudShapeProfile.defaultFor(type, CloudFamily.CUMULONIMBUS, null);
                var p = GrowthDefinedStormPrototype.compile(type, shape, seed);
                var a = p.withEnvelope(true);
                require(!p.envelopeContributes(), "Candidate must not contain a filled global envelope");
                require(p.growth().size() >= 5 && p.growth().size() <= 10, "Limited meaningful growth paths");
                require(a.growthSignature() == p.growthSignature(), "A/B changes only envelope contribution");
                require(a.signature() != p.signature(), "A/B transport flag is not silently ignored");
                require(Arrays.equals(p.packed(), GrowthDefinedStormPrototype.compile(type, shape, seed).packed()), "Deterministic recipe");
                require(p.packed().length % 4 == 0 && p.packed().length * 4 < 4096, "Compact aligned complete recipe");
                for (var g : p.growth()) {
                    require(g.endY() > g.rootY() && g.rootWidth() > 0 && g.midWidth() > 0 && g.crownWidth() > 0, "Coherent rising width profile");
                    if (g.parent() >= 0) {
                        // Lowered growth attaches upward into the parent; shoulders attach at birth.
                        var parent=p.growth().get(g.parent());
                        float y = g.rootY() + (g.endY() - g.rootY()) * (g.rootY()<parent.rootY()?.80f:.15f);
                        float u = (y - g.rootY()) / (g.endY() - g.rootY());
                        require(parent.distance(g.xAt(u), y, g.zAt(u)) < 0, "Growth attaches to its parent, not just itself");
                    }
                }
                var random = new SplittableRandom(seed);
                int envelopeAdded = 0, bodySamples = 0;
                int principalSamples=0,rootedSamples=0,shoulderAdded=0;
                var b = a.bounds();
                for (int i = 0; i < 12000; i++) {
                    float x = (float) random.nextDouble(b.minX() - 50, b.maxX() + 50);
                    float y = (float) random.nextDouble(b.minY() - 50, b.maxY() + 50);
                    float z = (float) random.nextDouble(b.minZ() - 50, b.maxZ() + 50);
                    float body = p.bodyDistance(x, y, z);
                    require(Float.isFinite(body), "Finite analytic body");
                    require(Math.abs(body - p.growthUnionDistance(x, y, z)) < .0001f, "Growth union owns the entire candidate body");
                    require(p.distanceWithoutGrowth(x, y, z, false) > 0, "No hidden macro body after growth ablation");
                    float d = p.unboundedDistance(x, y, z, StormMorphologyPrototype.Scale.MESO);
                    require(Math.abs(d-p.acceleratedDistance(x,y,z,StormMorphologyPrototype.Scale.MESO)) < .002f, "Bounds pruning preserves the distance field, not just connectivity");
                    if (d < p.feather() * .5f) require(p.bounds().contains(x, y, z), "Unclipped density enclosed by rejection bounds");
                    if (body < 0) {
                        bodySamples++;
                        if(p.growth().get(0).distance(x,y,z)<0)principalSamples++;
                        float rootDistance=1e6f;
                        for(int root=0;root<3;root++) {
                            var g=p.growth().get(root);float gd=g.distance(x,y,z),k=g.merge();
                            float t=Math.max(k-Math.abs(rootDistance-gd),0)/k;
                            rootDistance=Math.min(rootDistance,gd)-t*t*k*.25f;
                        }
                        if(rootDistance<0)rootedSamples++;else if(y>p.height()*.3f)shoulderAdded++;
                    }
                    if (a.bodyDistance(x, y, z) < 0 && body >= 0) envelopeAdded++;
                }
                require(bodySamples > 100, "Growth paths actually supply primary occupied volume");
                require(shoulderAdded>bodySamples*.08,"Attached rising structures change a material part of the body, not tiny decoration");
                require(envelopeAdded > 0, "Envelope dominance A/B has a measurable support witness");
                require(type.equals("cumulonimbus_calvus") ? p.ice().isEmpty() : !p.canopies().isEmpty() && !p.ice().isEmpty(), "Maturity-specific independent ice anatomy");
                for (var c : p.canopies()) {
                    float u=.10f;
                    require(p.bodyDistance(c.xAt(u),c.centreY(u),c.zAt(u)) < 0, "Independent canopy root intersects growth support");
                    require(c.length() > c.rootDepth()*3, "Canopy has independent spreading/thickness anatomy");
                }
                System.out.println(type + " seed=" + seed + " growth=" + p.growth().size() + " canopy=" + p.canopies().size()
                        + " bodySamples=" + bodySamples + " principalSamples="+principalSamples+" rootedSamples="+rootedSamples
                        + " shoulderAdded="+shoulderAdded+" envelopeAdded=" + envelopeAdded + " bytes=" + p.packed().length * 4);
            }
        }
        var profile=CloudShapeProfile.defaultFor("supercell",CloudFamily.CUMULONIMBUS,null);
        var neutral=GrowthDefinedStormPrototype.compile("supercell",controls(profile,-1),20261008);
        for(int arm=0;arm<5;arm++) {
            var changed=GrowthDefinedStormPrototype.compile("supercell",controls(profile,arm),20261008);
            var b=changed.bounds();var rng=new SplittableRandom(774L);
            double delta=0;
            for(int i=0;i<10000;i++) {
                float x=(float)rng.nextDouble(-1000,1900),y=(float)rng.nextDouble(-450,1050),z=(float)rng.nextDouble(-800,800);
                delta+=Math.abs(neutral.density(x,y,z,StormMorphologyPrototype.Scale.MACRO)-changed.density(x,y,z,StormMorphologyPrototype.Scale.MACRO));
            }
            require(delta>10,"Control "+arm+" changes actual macro density support");
            System.out.println("control="+arm+" fixedDomainDensityL1="+delta);
        }
        var sameProfile=new GrowthDefinedStormPrototype.Program[]{
                GrowthDefinedStormPrototype.compile("cumulonimbus_calvus",profile,177L),
                GrowthDefinedStormPrototype.compile("cumulonimbus_capillatus",profile,177L),
                GrowthDefinedStormPrototype.compile("supercell",profile,177L)};
        for(int pair=0;pair<2;pair++) {
            var rng=new SplittableRandom(7282L);double delta=0;
            for(int i=0;i<16000;i++) {
                float x=(float)rng.nextDouble(-1000,3000),y=(float)rng.nextDouble(-500,1100),z=(float)rng.nextDouble(-1200,1200);
                delta+=Math.abs(sameProfile[pair].density(x,y,z,StormMorphologyPrototype.Scale.MESO)-sameProfile[pair+1].density(x,y,z,StormMorphologyPrototype.Scale.MESO));
            }
            require(delta>30,"Archetypes differ with identical profile/scale/seed, not just material or native default size");
            System.out.println("sameProfilePair="+pair+" geometryDensityL1="+delta);
        }
        int extremeOccupied=0;
        for(float tilt:new float[]{-1,1})for(float shear:new float[]{-1,1})for(String type:new String[]{"cumulonimbus_calvus","cumulonimbus_capillatus","supercell"}) {
            var extreme=new CloudShapeProfile("growth/extreme",148,38,196,32,32,1,tilt,shear,1,1,1,0,1,1);
            var p=GrowthDefinedStormPrototype.compile(type,extreme,999L);
            var b=p.bounds();var rng=new SplittableRandom(666L);
            for(int i=0;i<2000;i++) {
                float x=(float)rng.nextDouble(b.minX()-150,b.maxX()+150),y=(float)rng.nextDouble(b.minY()-150,b.maxY()+150),z=(float)rng.nextDouble(b.minZ()-150,b.maxZ()+150);
                float d=p.unboundedDistance(x,y,z,StormMorphologyPrototype.Scale.MICRO);
                require(Float.isFinite(d),"Signed extreme finite support");
                require(Math.abs(d-p.acceleratedDistance(x,y,z,StormMorphologyPrototype.Scale.MICRO))<.002f,"Exact acceleration at signed extremes");
                if(d<p.feather()*.5f) {extremeOccupied++;require(b.contains(x,y,z),"Signed extremes cannot escape containment");}
            }
            require(p.growth().size()<32,"Legacy requested lobe count does not constrain authored role hierarchy");
        }
        require(extremeOccupied>100,"Extreme enclosure tests exercise actual occupied samples");
        System.out.println("T256 structural assertions=" + assertions + "; NOT visual acceptance.");
    }
    private static CloudShapeProfile controls(CloudShapeProfile s,int arm) {
        return new CloudShapeProfile("growth/control",s.getBaseRadius(),s.getBaseOffset(),s.getTopOffset(),s.getLobeCountMin(),s.getLobeCountMax(),s.getLobeStrength(),
                arm==0?.8f:0,arm==1?.8f:0,arm==2?1:0,arm==3?1:0,s.getAnvilSpread(),s.getBaseFlattening(),s.getEdgeRaggedness(),arm==4?1:0);
    }
}
