import hashlib,json,os,shutil,sys
root='C:/Users/matga/Downloads/Project-Atmosphere/'
V=root+'specs/001-native-storm-rendering/validation/'
plumb=['src/main/resources/assets/projectatmosphere/shaders/core/cloud_atmosphere_volume.fsh',
'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/CoreCostDiagnosticProgram.java',
'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/ExperimentalStormMorphologyState.java',
'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormMorphologyCapture.java',
'src/main/java/net/Gabou/projectatmosphere/clouds/morphology/ConvectiveGrowthFieldPrototype.java',
'src/main/java/net/Gabou/projectatmosphere/clouds/morphology/ConvectiveGrowthFieldA.java',
'src/main/resources/assets/projectatmosphere/shaders/include/cloud_convective_growth_field.glsl',
'src/main/resources/assets/projectatmosphere/shaders/include/cloud_convective_growth_field_a.glsl',
'src/main/java/net/Gabou/projectatmosphere/clouds/morphology/StormGeometryProgram.java',
'build.gradle','PAchangelog.md']
def sha(p): return hashlib.sha256(open(p,'rb').read()).hexdigest()
def tree(g):
    h=hashlib.sha256();n=0
    for dp,dn,fn in sorted(os.walk(g)):
        dn.sort()
        for f in sorted(fn):
            q=os.path.join(dp,f);h.update(os.path.relpath(q,g).replace(os.sep,'/').encode());h.update(open(q,'rb').read());n+=1
    return dict(files=n,sha256=h.hexdigest())
gal=['t257-convective-growth-field/native/ab-v3','t257-convective-growth-field/native/diag-v3','t257-convective-growth-field/native/ab-v2-complete','t257-convective-growth-field/diagnostics']
if len(sys.argv)>1 and sys.argv[1]=='verify':
    ref=json.load(open(sys.argv[2]))
    ok=True
    for g,v in ref['galleries'].items():
        cur=tree(V+g);good=cur==v;ok&=good;print('gallery',g,cur['files'],'unchanged' if good else 'CHANGED')
    good=sha(root+'PAchangelog.md')==ref['files']['PAchangelog.md'];ok&=good;print('PAchangelog unchanged' if good else 'PAchangelog CHANGED')
    print('PASS' if ok else 'FAIL');sys.exit(0 if ok else 1)
out={'note':'T258 start snapshot (14 Oct 2026). Plumbing copies are the exact pre-Candidate-C bytes; galleries are hashed to prove they are never overwritten.','files':{},'galleries':{}}
os.makedirs('sources',exist_ok=True)
for p in plumb:
    out['files'][p]=sha(root+p)
    shutil.copyfile(root+p,'sources/'+os.path.basename(p))
for g in gal: out['galleries'][g]=tree(V+g)
json.dump(out,open('t258-start-freeze.json','w'),indent=1)
print(json.dumps(out['galleries'],indent=1));print(out['files']['PAchangelog.md'])
