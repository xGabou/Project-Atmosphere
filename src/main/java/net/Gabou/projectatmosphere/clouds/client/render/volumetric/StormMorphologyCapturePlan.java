package net.Gabou.projectatmosphere.clouds.client.render.volumetric;

import net.Gabou.projectatmosphere.clouds.morphology.StormMorphologyPrototype.Bounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Pure capture planning only: never changes candidate geometry, renderer settings or weather. */
final class StormMorphologyCapturePlan {
    private StormMorphologyCapturePlan() {}
    static List<String> select(List<String> known,List<String> requested) {
        var selected=new HashSet<String>();
        for(String name:requested) {
            if(name.isBlank()||name.startsWith("#"))continue;
            if(!known.contains(name)||!selected.add(name))throw new IllegalArgumentException("Unknown/duplicate capture: "+name);
        }
        if(selected.isEmpty())throw new IllegalArgumentException("Empty resume selection");
        for(String name:selected) {
            int at=name.lastIndexOf("__");String view=name.substring(at+2),prefix=name.substring(0,at+2);
            String mate=prefix+(view.startsWith("a_")?view.substring(2):"a_"+view);
            if(view.equals("attribution_intervals"))mate=prefix+"attribution_exact";
            if(view.equals("attribution_exact"))mate=prefix+"attribution_intervals";
            if(known.contains(mate)&&!selected.contains(mate))throw new IllegalArgumentException("Resume must keep A/B or traversal pairs together: "+name);
        }
        var ordered=new ArrayList<String>();for(String name:known)if(selected.contains(name))ordered.add(name);
        return List.copyOf(ordered);
    }

    static double[] fit(double[] pose,Bounds a,Bounds b,double ox,double oy,double oz,double fov,double aspect,double floor) {
        double[] out=pose.clone();out[1]=Math.max(floor,out[1]);
        for(int pass=0;pass<32;pass++) {
            double dx=out[3]-out[0],dy=out[4]-out[1],dz=out[5]-out[2];double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
            double[] f={dx/distance,dy/distance,dz/distance};
            double required=Math.max(required(out,a,ox,oy,oz,fov,aspect,.88),required(out,b,ox,oy,oz,fov,aspect,.88));
            if(required<=distance+.001)return out;
            double next=Math.ceil((required+4)/10)*10;
            out[0]=out[3]-f[0]*next;out[1]=Math.max(floor,out[4]-f[1]*next);out[2]=out[5]-f[2]*next;
        }
        throw new IllegalArgumentException("Cannot frame comparison above camera floor");
    }
    static boolean fits(double[] pose,Bounds b,double ox,double oy,double oz,double fov,double aspect,double margin) {
        double dx=pose[3]-pose[0],dy=pose[4]-pose[1],dz=pose[5]-pose[2];
        return required(pose,b,ox,oy,oz,fov,aspect,margin)<=Math.sqrt(dx*dx+dy*dy+dz*dz)+.001;
    }
    private static double required(double[] pose,Bounds b,double ox,double oy,double oz,double fov,double aspect,double margin) {
        double dx=pose[3]-pose[0],dy=pose[4]-pose[1],dz=pose[5]-pose[2];double n=Math.sqrt(dx*dx+dy*dy+dz*dz);
        double fx=dx/n,fy=dy/n,fz=dz/n,h=Math.hypot(fx,fz),rx=-fz/h,rz=fx/h;
        double ux=-rz*fy,uy=rz*fx-rx*fz,uz=rx*fy;
        double ty=Math.tan(Math.toRadians(fov)*.5)*margin,tx=ty*aspect,need=0;
        for(double x:new double[]{b.minX()+ox,b.maxX()+ox})for(double y:new double[]{b.minY()+oy,b.maxY()+oy})for(double z:new double[]{b.minZ()+oz,b.maxZ()+oz}) {
            double vx=x-pose[3],vy=y-pose[4],vz=z-pose[5],along=vx*fx+vy*fy+vz*fz;
            need=Math.max(need,Math.max(64-along,Math.max(Math.abs(vx*rx+vz*rz)/tx-along,Math.abs(vx*ux+vy*uy+vz*uz)/ty-along)));
        }
        return need;
    }
}
