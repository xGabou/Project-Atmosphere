package net.Gabou.projectatmosphere.clouds.morphology;

import net.Gabou.projectatmosphere.clouds.type.CloudShapeProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import static net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.*;

/** T256 growth-owned support. Pure experimental compiler, no production authority. */
public final class GrowthDefinedStormPrototype {
    public static final int VERSION = 3;
    private GrowthDefinedStormPrototype() {}

    /** Five texels: curved axis, width bands, maturity/attachment, local expansion. */
    public record Growth(float rootX, float rootY, float rootZ, float endY,
                         float displacementX, float displacementZ, float bendX, float bendZ,
                         float rootWidth, float midWidth, float crownWidth, float aspect,
                         float maturity, float merge, int parent, float canopyContribution,
                         float expansionHeight,float expansionSpan,float expansion) {
        public float xAt(float u) { return rootX + displacementX*u + bendX*u*u; }
        public float zAt(float u) { return rootZ + displacementZ*u + bendZ*u*u; }
        public float widthAt(float u) {
            float width = u < .52f ? mix(rootWidth, midWidth, smooth(0,.52f,u))
                    : mix(midWidth,crownWidth,smooth(.52f,.83f,u));
            width += expansion*maturity*compact((u-expansionHeight)/expansionSpan);
            return width * roundCaps(u,.10f,.72f);
        }
        public float distance(float x,float y,float z) {
            float u = clamp((y-rootY)/(endY-rootY),0,1);
            return Math.max(hypot(x-xAt(u),(z-zAt(u))/aspect)-widthAt(u),Math.max(rootY-y,y-endY));
        }
    }
    /** Independent horizontal loft: own direction, fan, thickness, shear, age. Four texels. */
    public record Canopy(float rootX,float rootY,float rootZ,float length,
                         float fan,float lift,float bend,float maturity,
                         float rootWidth,float endWidth,float rootDepth,float endDepth,
                         float angleX,float angleZ,float merge,float contribution) {
        public float centreY(float u) { return rootY+lift*u+bend*u*(1-u); }
        public float widthAt(float u) { return mix(rootWidth,endWidth,smooth(.05f,.70f,u))*cap(u); }
        public float depthAt(float u) { return mix(rootDepth,endDepth,smooth(0,.85f,u))*cap(u); }
        private float cap(float u) { return roundCaps(u,.10f,.68f); }
        public float xAt(float u) { return rootX+angleX*length*u-angleZ*fan*u*u; }
        public float zAt(float u) { return rootZ+angleZ*length*u+angleX*fan*u*u; }
        public float distance(float x,float y,float z) {
            float dx=x-rootX,dz=z-rootZ;
            float along=dx*angleX+dz*angleZ;
            float u=clamp(along/length,0,1);
            float across=-dx*angleZ+dz*angleX-fan*u*u;
            float w=Math.max(1,widthAt(u)),depth=Math.max(1,depthAt(u));
            // Ellipse metric, not a body primitive squashed onto its side.
            float v=y-centreY(u);
            return Math.max(ellipse(across,v,w,depth),Math.max(-along,along-length));
        }
    }
    /** Branching ice stream. Different evaluator/axis from vertical growth. Three texels. */
    public record Ice(float rootX,float rootY,float rootZ,float length,
                      float fan,float lift,float width,float depth,
                      float angleX,float angleZ,float bend,float maturity) {
        public float distance(float x,float y,float z) {
            float dx=x-rootX,dz=z-rootZ,along=dx*angleX+dz*angleZ;
            float u=clamp(along/length,0,1),cap=roundCaps(u,.04f,.80f);
            float across=-dx*angleZ+dz*angleX-fan*(.55f*u+.45f*u*u);
            float cy=rootY+lift*u+bend*u*(1-u);
            float taper=(1-.65f*u)*cap;
            return Math.max(ellipse(across,y-cy,Math.max(1,width*taper),Math.max(1,depth*taper)),Math.max(-along,along-length));
        }
    }

    public static Program compile(String type,CloudShapeProfile shape,long seed) {
        Archetype kind=switch(type) {
            case "cumulonimbus_calvus" -> Archetype.CALVUS;
            case "cumulonimbus_capillatus" -> Archetype.CAPILLATUS;
            case "supercell" -> Archetype.SUPERCELL;
            default -> throw new IllegalArgumentException("Not a storm: "+type);
        };
        for(float value:new float[]{shape.getBaseRadius(),shape.getTopOffset(),shape.getBaseOffset(),shape.getVerticalTilt(),
                shape.getWindShearStrength(),shape.getTowerNarrowing(),shape.getAnvilSpread(),shape.getLobeStrength(),
                shape.getCellSplitStrength(),shape.getStormWallStrength(),shape.getBaseFlattening(),shape.getEdgeRaggedness()}) {
            if(!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite growth descriptor");
        }
        float r=shape.getBaseRadius()*2.5f,h=shape.getTopOffset()*4,base=shape.getBaseOffset()*2;
        float tilt=shape.getVerticalTilt(),shear=shape.getWindShearStrength(),narrow=shape.getTowerNarrowing();
        float spread=shape.getAnvilSpread(),strength=shape.getLobeStrength();
        var rng=new SplittableRandom(seed);
        List<Growth> growth=new ArrayList<>();
        float lean=h*(.9f*tilt+.12f*shear),bend=h*.28f*shear;
        float age=kind==Archetype.CALVUS?.45f:kind==Archetype.CAPILLATUS?.85f:1;
        // Rooted principal growth, with expansion in lower body and crown. No enclosing shell.
        float rootDepth=r*(1-shape.getBaseFlattening());
        growth.add(new Growth(-r*.12f,-base-rootDepth*.16f,0,h*(kind==Archetype.CALVUS?1.03f:1.00f),
                lean,0,bend,r*(float)rng.nextDouble(-.08,.08),r*.74f,r*(.61f-.13f*narrow),r*.68f,1.02f,age,r*.16f,-1,1,
                (float)rng.nextDouble(.34,.48),.23f,r*.24f));
        // Two unequal rooted inflow paths share the mass and grow into distinct shoulders.
        for(int i=0;i<2;i++) {
            float sign=i==0?-1:1,end=h*(float)rng.nextDouble(.72,.93);
            growth.add(new Growth(-r*(i==0?.50f:.12f),-base-rootDepth*(.10f+.08f*i),sign*r*.42f,end,
                    lean*.75f+r*(i==0?-.08f:.18f),sign*r*.14f,bend*.75f,sign*r*.05f,
                    r*(i==0?.66f:.57f),r*(.49f-.09f*narrow),r*(i==0?.55f:.46f),1,age*.9f,r*.18f,-1,.5f,
                    (float)rng.nextDouble(.32,.60),.26f,r*.20f));
        }
        // Shoulder paths are primary macro anatomy, not bumps added after a global fill.
        int shoulders=kind==Archetype.SUPERCELL?3:4;
        for(int i=0;i<shoulders;i++) {
            float angle=(float)(i*2.39996323+rng.nextDouble(-.35,.35));
            float ax=(float)Math.cos(angle),az=(float)Math.sin(angle);
            float birth=(float)rng.nextDouble(.18,.47),end=(float)rng.nextDouble(.85,1.25);
            float rootY=h*birth,u=(rootY-growth.get(0).rootY())/(growth.get(0).endY()-growth.get(0).rootY());
            Growth parent=growth.get(0);
            float size=r*(float)rng.nextDouble(.40,.57)*(.7f+.45f*strength);
            float reach=r*(float)rng.nextDouble(.65,1.10)*(.8f+.3f*strength);
            growth.add(new Growth(parent.xAt(u)+ax*r*.18f,rootY,parent.zAt(u)+az*r*.18f,h*end,
                    lean*(end-birth)+ax*reach,az*reach,bend*(end-birth),az*r*.05f,
                    size*.65f,size*.90f,size*(float)rng.nextDouble(1,1.30),1,age,r*.16f,0,.7f,
                    (float)rng.nextDouble(.34,.68),.24f,size*.40f));
        }
        if(shape.getCellSplitStrength()>0) {
            float split=shape.getCellSplitStrength();
            growth.add(new Growth(-r*.35f,-base, r*.38f,h*(.62f+.16f*split),
                    lean*.55f-r*.25f*split,r*.85f*split,bend*.45f,r*.12f*split,
                    r*.48f,r*.39f,r*.42f,1,age*.6f,r*.24f,0,.25f,.48f,.30f,r*.12f));
        }
        if(kind==Archetype.SUPERCELL && shape.getStormWallStrength()>0) {
            float wall=shape.getStormWallStrength();
            growth.add(new Growth(-r*.35f,-base-r*(.16f+.55f*wall),-r*.20f,h*.33f,
                    lean*.20f+r*.24f,r*.12f,bend*.10f,0,
                    r*.36f,r*.49f,r*.50f,1.15f,age,r*.27f,0,0,.38f,.28f,r*.09f));
        }
        List<Canopy> canopy=new ArrayList<>();
        List<Ice> ice=new ArrayList<>();
        int patches=kind==Archetype.CALVUS?1:3;
        float canopyLength=r*(kind==Archetype.CALVUS?.75f+.55f*spread:kind==Archetype.CAPILLATUS?2.0f+2.0f*spread:2.6f+2.2f*spread);
        for(int i=0;i<patches;i++) {
            float angle=kind==Archetype.CALVUS?.05f:(i-1)*.19f+(float)rng.nextDouble(-.05,.05);
            float dx=(float)Math.cos(angle),dz=(float)Math.sin(angle);
            float u=.82f,rootX=growth.get(0).xAt(u)-r*.32f,rootZ=(i-1)*r*.24f;
            float rootY=h*(kind==Archetype.CALVUS?.88f:.85f)+r*(i==1?.08f:0);
            float length=canopyLength*(float)rng.nextDouble(.82,1.12);
            float thickness=r*(kind==Archetype.CALVUS?.24f:.27f);
            canopy.add(new Canopy(rootX,rootY,rootZ,length, r*shear*(i-1)*.35f,
                    h*(.06f+.035f*age),r*(i==1?.08f:-.05f),age,
                    r*.72f,r*(kind==Archetype.CALVUS?.55f:.85f+.35f*spread),thickness,thickness*.44f,
                    dx,dz,r*.22f,age));
        }
        if(kind!=Archetype.CALVUS) {
            for(int i=0;i<6;i++) {
                var c=canopy.get(i/2);
                float u=(float)rng.nextDouble(.32,.53),fan=(i%2==0?-1:1)*r*(float)rng.nextDouble(.22,.65);
                ice.add(new Ice(c.xAt(u),c.centreY(u),c.zAt(u),c.length()*(float)rng.nextDouble(.58,.80),
                        fan+r*shear*.18f,h*(float)rng.nextDouble(.02,.07),r*.085f,r*.032f,
                        c.angleX(),c.angleZ(),r*(float)rng.nextDouble(-.06,.09),age));
            }
        }
        return new Program(kind,r,h,base,tilt,shear,narrow,spread,shape.getBaseFlattening(),shape.getEdgeRaggedness(),
                growth,canopy,ice,false,0);
    }

    public static final class Program implements StormGeometryProgram {
        private final Archetype kind;
        private final float r,h,base,tilt,shear,narrow,spread,flat,ragged;
        private final List<Growth> growth;
        private final List<Canopy> canopy;
        private final List<Ice> ice;
        private final boolean envelope;
        private final int inspectionMask;
        private final List<Bounds> groups;
        private final Bounds bounds;
        private final long signature,growthSignature;
        private Program(Archetype kind,float r,float h,float base,float tilt,float shear,float narrow,float spread,float flat,float ragged,
                        List<Growth> growth,List<Canopy> canopy,List<Ice> ice,boolean envelope,int inspectionMask) {
            this.kind=kind;this.r=r;this.h=h;this.base=base;this.tilt=tilt;this.shear=shear;this.narrow=narrow;
            this.spread=spread;this.flat=flat;this.ragged=ragged;this.growth=List.copyOf(growth);this.canopy=List.copyOf(canopy);
            this.ice=List.copyOf(ice);this.envelope=envelope;this.inspectionMask=inspectionMask;
            // Conservative polynomial-axis hulls, largest width/thickness, smooth-min certificate.
            float margin=r*.30f+feather()*.5f+r*.022f*ragged+2;
            Bounds body=null,upper=null,fibres=null;
            for(var g:growth) {
                float width=Math.max(g.rootWidth(),Math.max(g.midWidth(),g.crownWidth()))+g.expansion()*g.maturity();
                float minX=g.rootX()+Math.min(0,g.displacementX())+Math.min(0,g.bendX());
                float maxX=g.rootX()+Math.max(0,g.displacementX())+Math.max(0,g.bendX());
                float minZ=g.rootZ()+Math.min(0,g.displacementZ())+Math.min(0,g.bendZ());
                float maxZ=g.rootZ()+Math.max(0,g.displacementZ())+Math.max(0,g.bendZ());
                body=join(body,new Bounds(minX-width-margin,g.rootY()-margin,minZ-width*g.aspect()-margin,
                        maxX+width+margin,g.endY()+margin,maxZ+width*g.aspect()+margin));
            }
            if(envelope) {
                float min=Math.min(0,h*.9f*tilt)+Math.min(0,h*.38f*shear),max=Math.max(0,h*.9f*tilt)+Math.max(0,h*.38f*shear);
                body=join(body,new Bounds(min-r*1.65f-margin,-base-margin,-r*1.65f-margin,max+r*1.65f+margin,h+margin,r*1.65f+margin));
            }
            for(var c:canopy) {
                float width=Math.max(c.rootWidth(),c.endWidth())+Math.abs(c.fan());
                float ex=c.rootX()+c.angleX()*c.length(),ez=c.rootZ()+c.angleZ()*c.length();
                upper=join(upper,new Bounds(Math.min(c.rootX(),ex)-width-margin,c.rootY()+Math.min(0,c.lift())-Math.abs(c.bend())-c.rootDepth()-margin,
                        Math.min(c.rootZ(),ez)-width-margin,Math.max(c.rootX(),ex)+width+margin,
                        c.rootY()+Math.max(0,c.lift())+Math.abs(c.bend())+c.rootDepth()+margin,Math.max(c.rootZ(),ez)+width+margin));
            }
            for(var f:ice) {
                float ex=f.rootX()+f.angleX()*f.length(),ez=f.rootZ()+f.angleZ()*f.length();
                float width=f.width()+Math.abs(f.fan());
                fibres=join(fibres,new Bounds(Math.min(f.rootX(),ex)-width-margin,f.rootY()+Math.min(0,f.lift())-Math.abs(f.bend())-f.depth()-margin,
                        Math.min(f.rootZ(),ez)-width-margin,Math.max(f.rootX(),ex)+width+margin,
                        f.rootY()+Math.max(0,f.lift())+Math.abs(f.bend())+f.depth()+margin,Math.max(f.rootZ(),ez)+width+margin));
            }
            var list=new ArrayList<Bounds>();list.add(body);if(upper!=null)list.add(upper);if(fibres!=null)list.add(fibres);
            groups=List.copyOf(list);Bounds whole=null;for(var b:groups)whole=join(whole,b);bounds=whole;
            signature=hash(packed());
            float[] recipe=packed();recipe[12]=0;
            // Bounds differ for A/B; compare authored geometry, not rejection boxes.
            for(int i=24;i<48;i++)recipe[i]=0;
            growthSignature=hash(recipe);
        }
        public Program withEnvelope(boolean value) {
            return new Program(kind,r,h,base,tilt,shear,narrow,spread,flat,ragged,growth,canopy,ice,value,inspectionMask);
        }
        /** Image-invalid cost arms (1 no canopy, 2 no ice, 4 hard merge); 8 counts keeps geometry. */
        public Program withInspectionMask(int mask) {return new Program(kind,r,h,base,tilt,shear,narrow,spread,flat,ragged,growth,canopy,ice,envelope,mask);}
        public int inspectionMask() {return inspectionMask;}
        private float merge(float a,float b,float k) {return (inspectionMask&4)!=0?Math.min(a,b):union(a,b,k);}
        public List<Growth> growth() { return growth; }
        public List<Canopy> canopies() { return canopy; }
        public List<Ice> ice() { return ice; }
        public boolean envelopeContributes() { return envelope; }
        public long growthSignature() { return growthSignature; }
        public float feather() { return Math.max(2,r*.055f); }
        public float radius() { return r; }
        public float height() { return h; }
        public int version() { return VERSION; }
        public boolean growthDefined() { return true; }
        public Bounds bounds() { return bounds; }
        public List<Bounds> componentBounds() { return groups; }
        public long signature() { return signature; }
        public float axisX(float t) { return growth.get(0).xAt(clamp(t,0,1)); }
        public float growthUnionDistance(float x,float y,float z) {
            float d=1e6f;
            for(int i=0;i<growth.size();i++) {var g=growth.get(i);d=merge(d,g.distance(x,y,z),g.merge());}
            return d;
        }
        /** Exact lower-bound rejection: discarded operands cannot enter smooth-min's support. */
        public float acceleratedDistance(float x,float y,float z,Scale scale) {
            float d=1e6f;
            for(int i=0;i<growth.size();i++) {
                var g=growth.get(i);
                if(Math.max(g.rootY()-y,y-g.endY())>d+g.merge())continue;
                d=merge(d,g.distance(x,y,z),g.merge());
            }
            if(envelope)d=merge(d,legacyEnvelope(x,y,z),r*.24f);
            if((inspectionMask&1)==0)for(int i=0;i<canopy.size();i++) {
                var c=canopy.get(i);
                float vertical=Math.max(c.rootY()+Math.min(0,c.lift())-Math.abs(c.bend())-Math.max(c.rootDepth(),c.endDepth())-y,
                        y-c.rootY()-Math.max(0,c.lift())-Math.abs(c.bend())-Math.max(c.rootDepth(),c.endDepth()));
                float along=(x-c.rootX())*c.angleX()+(z-c.rootZ())*c.angleZ();
                // Radial ellipse approximation is not a signed Euclidean distance inside.
                // Positive outside-box bounds are valid; negative vertical bounds are not.
                if(Math.max(vertical>0?vertical:-1e6f,Math.max(-along,along-c.length()))>d+c.merge())continue;
                d=merge(d,c.distance(x,y,z),c.merge());
            }
            if(scale!=Scale.MACRO && (inspectionMask&2)==0)for(int i=0;i<ice.size();i++) {
                var f=ice.get(i);
                float vertical=Math.max(f.rootY()+Math.min(0,f.lift())-Math.abs(f.bend())-f.depth()-y,
                        y-f.rootY()-Math.max(0,f.lift())-Math.abs(f.bend())-f.depth());
                float along=(x-f.rootX())*f.angleX()+(z-f.rootZ())*f.angleZ();
                if(Math.max(vertical>0?vertical:-1e6f,Math.max(-along,along-f.length()))>d+r*.04f)continue;
                d=merge(d,f.distance(x,y,z),r*.04f);
            }
            if(scale==Scale.MICRO)d+=(float)(Math.sin(x/17+Math.sin(z/31))*Math.sin(y/23+Math.sin(x/41))*Math.cos(z/19))*r*.022f*ragged;
            return d;
        }
        public float bodyDistance(float x,float y,float z) {
            float d=growthUnionDistance(x,y,z);
            return envelope?merge(d,legacyEnvelope(x,y,z),r*.24f):d;
        }
        private float legacyEnvelope(float x,float y,float z) {
            float t=clamp(y/h,0,1),axis=h*(.9f*tilt*t+.38f*shear*t*t),dx=x-axis;
            float foot=kind==Archetype.SUPERCELL?compact(t/.38f):0;
            float q=hypot(dx/(1+.55f*foot),z/(1+.30f*foot));
            float width=r*(1-.16f*t-.26f*narrow*smooth(.10f,.70f,t)+(kind==Archetype.CALVUS?.20f:.08f)*smooth(.62f,.88f,t));
            float floor=-base+(1-flat)*r*.12f*(q/r)*(q/r);
            return Math.max(q-width,Math.max(floor-y,y-h+r*.43f*(q/width)*(q/width)));
        }
        public float distanceWithoutGrowth(float x,float y,float z,boolean includeCanopy) {
            float d=1e6f;
            if(includeCanopy)for(int i=0;i<canopy.size();i++) {var c=canopy.get(i);d=union(d,c.distance(x,y,z),c.merge());}
            return d;
        }
        public float unboundedDistance(float x,float y,float z,Scale scale) {
            float d=bodyDistance(x,y,z);
            if((inspectionMask&1)==0)for(int i=0;i<canopy.size();i++) {var c=canopy.get(i);d=merge(d,c.distance(x,y,z),c.merge());}
            if(scale!=Scale.MACRO && (inspectionMask&2)==0)for(int i=0;i<ice.size();i++) {var f=ice.get(i);d=merge(d,f.distance(x,y,z),r*.04f);}
            if(scale==Scale.MICRO)d+=(float)(Math.sin(x/17+Math.sin(z/31))*Math.sin(y/23+Math.sin(x/41))*Math.cos(z/19))*r*.022f*ragged;
            return d;
        }
        public float density(float x,float y,float z,Scale scale) {
            if(!bounds.contains(x,y,z))return 0;
            return clamp(.5f-acceleratedDistance(x,y,z,scale)/feather(),0,1);
        }
        /** 12 common hierarchy/header texels, 5/path, 4/canopy, 3/ice. */
        public float[] packed() {
            float[] p=new float[48+growth.size()*20+canopy.size()*16+ice.size()*12];
            float[] header={r,h,base,kind.ordinal(),tilt,shear,narrow,spread,flat,ragged,growth.size(),canopy.size(),
                    envelope?1:0,ice.size(),VERSION,feather(),inspectionMask,0,0,0,0,0,r*.30f,groups.size()};
            System.arraycopy(header,0,p,0,24);
            for(int i=0;i<groups.size();i++) {
                var b=groups.get(i);int o=24+i*8;
                p[o]=b.minX();p[o+1]=b.minY();p[o+2]=b.minZ();p[o+4]=b.maxX();p[o+5]=b.maxY();p[o+6]=b.maxZ();
            }
            int o=48;
            for(var g:growth) {float[] v={g.rootX(),g.rootY(),g.rootZ(),g.endY(),g.displacementX(),g.displacementZ(),g.bendX(),g.bendZ(),
                    g.rootWidth(),g.midWidth(),g.crownWidth(),g.aspect(),g.maturity(),g.merge(),g.parent(),g.canopyContribution(),
                    g.expansionHeight(),g.expansionSpan(),g.expansion(),0};System.arraycopy(v,0,p,o,20);o+=20;}
            for(var c:canopy) {float[] v={c.rootX(),c.rootY(),c.rootZ(),c.length(),c.fan(),c.lift(),c.bend(),c.maturity(),
                    c.rootWidth(),c.endWidth(),c.rootDepth(),c.endDepth(),c.angleX(),c.angleZ(),c.merge(),c.contribution()};System.arraycopy(v,0,p,o,16);o+=16;}
            for(var f:ice) {float[] v={f.rootX(),f.rootY(),f.rootZ(),f.length(),f.fan(),f.lift(),f.width(),f.depth(),f.angleX(),f.angleZ(),f.bend(),f.maturity()};System.arraycopy(v,0,p,o,12);o+=12;}
            return p;
        }
    }
    private static Bounds join(Bounds a,Bounds b) {return a==null?b:new Bounds(Math.min(a.minX(),b.minX()),Math.min(a.minY(),b.minY()),Math.min(a.minZ(),b.minZ()),Math.max(a.maxX(),b.maxX()),Math.max(a.maxY(),b.maxY()),Math.max(a.maxZ(),b.maxZ()));}
    private static long hash(float[] p) {long hash=0xcbf29ce484222325L;for(float v:p)hash=(hash^Float.floatToIntBits(v))*0x100000001b3L;return hash;}
    private static float compact(float t) {float v=Math.max(0,1-t*t);return v*v;}
    private static float ellipse(float a,float b,float ra,float rb) {float norm=hypot(a/ra,b/rb);return norm==0?-Math.min(ra,rb):(norm-1)*hypot(a,b)/norm;}
    private static float union(float a,float b,float k) {float t=Math.max(k-Math.abs(a-b),0)/k;return Math.min(a,b)-t*t*k*.25f;}
    private static float hypot(float a,float b) {return (float)Math.sqrt(a*a+b*b);}
    private static float smooth(float a,float b,float v) {float t=clamp((v-a)/(b-a),0,1);return t*t*(3-2*t);}
    private static float clamp(float v,float a,float b) {return Math.max(a,Math.min(b,v));}
    private static float mix(float a,float b,float t) {return a+(b-a)*t;}
    private static float roundCaps(float u,float rootEnd,float crownStart) {
        float a=clamp((rootEnd-u)/rootEnd,0,1),b=clamp((u-crownStart)/(1-crownStart),0,1);
        return (float)Math.sqrt(Math.max(0,(1-a*a)*(1-b*b)));
    }
}
