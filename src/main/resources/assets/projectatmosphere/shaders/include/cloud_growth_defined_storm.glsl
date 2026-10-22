// T256 isolated evaluator. CPU mirror: GrowthDefinedStormPrototype. No production build defines this.
vec4 paGrowthTexel(int i) { return texelFetch(StormDescriptorSampler, ivec2(i,0),0); }
#ifdef PA_GROWTH_COST_COUNTS
vec4 paGrowthCounts=vec4(0.0); // queries, path evaluations, canopy evaluations, ice evaluations
#endif
float paGrowthUnion(float a,float b,float k) {
#ifdef PA_GROWTH_HARD_MERGE
    return min(a,b);
#else
    float t=max(k-abs(a-b),0.0)/k;return min(a,b)-t*t*k*.25;
#endif
}
float paGrowthEllipse(vec2 p,vec2 r) {
    float norm=length(p/r);return norm==0.0?-min(r.x,r.y):(norm-1.0)*length(p)/norm;
}
float paGrowthCaps(float u,float rootEnd,float crownStart) {
    float a=clamp((rootEnd-u)/rootEnd,0.0,1.0),b=clamp((u-crownStart)/(1.0-crownStart),0.0,1.0);
    return sqrt(max(0.0,(1.0-a*a)*(1.0-b*b)));
}
void paMorphRayIntervals(vec3 origin,vec3 direction,float low,float high,out vec2 intervals[3]) {
    vec3 local=origin-PaMorphologyOrigin;int count=int(paGrowthTexel(5).w+.5);
    for(int i=0;i<3;i++) {
        intervals[i]=vec2(1e20,-1.0);if(i>=count)continue;
        vec3 lo=paGrowthTexel(6+i*2).xyz,hi=paGrowthTexel(7+i*2).xyz;
        float a=low,b=high;
        for(int axis=0;axis<3;axis++) {
            if(abs(direction[axis])<1e-8) {if(local[axis]<lo[axis]||local[axis]>hi[axis])b=-1.0;}
            else {vec2 t=vec2(lo[axis]-local[axis],hi[axis]-local[axis])/direction[axis];a=max(a,min(t.x,t.y));b=min(b,max(t.x,t.y));}
        }
        if(b>=a)intervals[i]=vec2(a,b);
    }
    vec2 t;
    if(intervals[0].x>intervals[1].x){t=intervals[0];intervals[0]=intervals[1];intervals[1]=t;}
    if(intervals[1].x>intervals[2].x){t=intervals[1];intervals[1]=intervals[2];intervals[2]=t;}
    if(intervals[0].x>intervals[1].x){t=intervals[0];intervals[0]=intervals[1];intervals[1]=t;}
}
float paMorphDensity(vec3 world) {
#ifdef PA_GROWTH_COST_COUNTS
    paGrowthCounts.x+=1.0;
#endif
    vec3 p=world-PaMorphologyOrigin;
    if(any(lessThan(p,PaMorphologyMin))||any(greaterThan(p,PaMorphologyMax)))return 0.0;
    vec4 dim=paGrowthTexel(0),controls=paGrowthTexel(1),lower=paGrowthTexel(2),mode=paGrowthTexel(3);
    float r=dim.x,h=dim.y,d=1e6;
    int growthCount=int(lower.z+.5),canopyCount=int(lower.w+.5),iceCount=int(mode.y+.5);
    for(int i=0;i<growthCount;i++) {
        int offset=12+i*5;
        vec4 root=paGrowthTexel(offset);
        // Exact: max-height term is a lower bound on this operand's distance.
        if(max(root.y-p.y,p.y-root.w)>d+r*.27)continue;
        vec4 axis=paGrowthTexel(offset+1),width=paGrowthTexel(offset+2),meta=paGrowthTexel(offset+3);
#ifdef PA_GROWTH_COST_COUNTS
        paGrowthCounts.y+=1.0;
#endif
        float u=clamp((p.y-root.y)/(root.w-root.y),0.0,1.0);
        vec2 centre=root.xz+axis.xy*u+axis.zw*u*u;
        float w=u<.52?mix(width.x,width.y,smoothstep(0.0,.52,u)):mix(width.y,width.z,smoothstep(.52,.83,u));
        vec4 expansion=paGrowthTexel(offset+4);
        float pulse=max(0.0,1.0-((u-expansion.x)/expansion.y)*((u-expansion.x)/expansion.y));
        w+=expansion.z*meta.x*pulse*pulse;
        w*=paGrowthCaps(u,.10,.72);
        float g=max(length((p.xz-centre)/vec2(1.0,width.w))-w,max(root.y-p.y,p.y-root.w));
        d=paGrowthUnion(d,g,meta.y);
    }
    if(mode.x>.5) {
        float t=clamp(p.y/h,0.0,1.0),axis=h*(.9*controls.x*t+.38*controls.y*t*t);
        float foot=dim.w>1.5?max(0.0,1.0-t*t/(.38*.38)):0.0;foot*=foot;
        float q=length((p.xz-vec2(axis,0.0))/vec2(1.0+.55*foot,1.0+.30*foot));
        float width=r*(1.0-.16*t-.26*controls.z*smoothstep(.10,.70,t)+(dim.w<.5?.20:.08)*smoothstep(.62,.88,t));
        float floorY=-dim.z+(1.0-lower.x)*r*.12*(q/r)*(q/r);
        float body=max(q-width,max(floorY-p.y,p.y-h+r*.43*(q/width)*(q/width)));
        d=paGrowthUnion(d,body,r*.24);
    }
    int canopyOffset=12+growthCount*5;
#ifndef PA_GROWTH_NO_CANOPY
    for(int i=0;i<canopyCount;i++) {
        int offset=canopyOffset+i*4;
        vec4 root=paGrowthTexel(offset),shape=paGrowthTexel(offset+1),width=paGrowthTexel(offset+2),dir=paGrowthTexel(offset+3);
        vec2 delta=p.xz-root.xz;
        float along=dot(delta,dir.xy),u=clamp(along/root.w,0.0,1.0);
        float vertical=max(root.y+min(0.0,shape.y)-abs(shape.z)-max(width.z,width.w)-p.y,
                p.y-root.y-max(0.0,shape.y)-abs(shape.z)-max(width.z,width.w));
        if(max(vertical>0.0?vertical:-1e6,max(-along,along-root.w))>d+dir.z)continue;
#ifdef PA_GROWTH_COST_COUNTS
        paGrowthCounts.z+=1.0;
#endif
        float across=dot(delta,vec2(-dir.y,dir.x))-shape.x*u*u;
        float cap=paGrowthCaps(u,.10,.68);
        float w=mix(width.x,width.y,smoothstep(.05,.70,u))*cap;
        float depth=mix(width.z,width.w,smoothstep(0.0,.85,u))*cap;
        float cy=root.y+shape.y*u+shape.z*u*(1.0-u);
        float c=max(paGrowthEllipse(vec2(across,p.y-cy),max(vec2(1.0),vec2(w,depth))),max(-along,along-root.w));
        d=paGrowthUnion(d,c,dir.z);
    }
#endif
#ifndef PA_GROWTH_NO_ICE
    if(PaMorphologyScale>0)for(int i=0;i<iceCount;i++) {
        int offset=canopyOffset+canopyCount*4+i*3;
        vec4 root=paGrowthTexel(offset),shape=paGrowthTexel(offset+1),dir=paGrowthTexel(offset+2);
        vec2 delta=p.xz-root.xz;
        float along=dot(delta,dir.xy),u=clamp(along/root.w,0.0,1.0);
        float vertical=max(root.y+min(0.0,shape.y)-abs(dir.z)-shape.w-p.y,
                p.y-root.y-max(0.0,shape.y)-abs(dir.z)-shape.w);
        if(max(vertical>0.0?vertical:-1e6,max(-along,along-root.w))>d+r*.04)continue;
#ifdef PA_GROWTH_COST_COUNTS
        paGrowthCounts.w+=1.0;
#endif
        float cap=paGrowthCaps(u,.04,.80);
        float across=dot(delta,vec2(-dir.y,dir.x))-shape.x*(.55*u+.45*u*u);
        float cy=root.y+shape.y*u+dir.z*u*(1.0-u),taper=(1.0-.65*u)*cap;
        float f=max(paGrowthEllipse(vec2(across,p.y-cy),max(vec2(1.0),shape.zw*taper)),max(-along,along-root.w));
        d=paGrowthUnion(d,f,r*.04);
    }
#endif
    if(PaMorphologyScale>1)d+=sin(p.x/17.0+sin(p.z/31.0))*sin(p.y/23.0+sin(p.x/41.0))*cos(p.z/19.0)*r*.022*lower.y;
    return clamp(.5-d/mode.w,0.0,1.0);
}
