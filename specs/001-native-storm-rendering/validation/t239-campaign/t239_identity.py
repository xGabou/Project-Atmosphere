# T239. Storm identity: normalized-silhouette IoU between storms, same view.
import sys, os, itertools
import numpy as np
from PIL import Image
def mask(p):
    a=np.asarray(Image.open(p).convert('RGB')).astype(int); r,g,b=a[...,0],a[...,1],a[...,2]
    return (b<225)&(r>100)&(abs(g-r)<30)
def norm(m, size=128):
    ys,xs=np.nonzero(m)
    c=m[ys.min():ys.max()+1, xs.min():xs.max()+1]
    return np.asarray(Image.fromarray((c*255).astype(np.uint8)).resize((size,size)))>127
dirs=sys.argv[1].split(',')
for arm in sys.argv[2].split(','):
    for pose in ['FAR','LATERAL','SIDE','ABOVE']:
        ms=[]
        for d in dirs:
            f=os.path.join(d,f'T239_{arm}_{pose}.png')
            if os.path.exists(f):
                m=mask(f)
                if m.sum()>500: ms.append(norm(m))
        ious=[ (a&b).sum()/max((a|b).sum(),1) for a,b in itertools.combinations(ms,2)]
        if ious: print(arm,pose,'storms',len(ms),'pairwise IoU',' '.join('%.3f'%v for v in ious))
