import re,sys
def stat(path):
    d=open(path,'rb').read()
    i=d.find(b'!!NVfp5.0'); j=d.find(b'\x00',i)
    t=d[i:j].decode('latin-1')
    lines=[l.strip() for l in t.split('\n') if l.strip()]
    temps=re.search(r'^TEMP (.*);$',t,re.M)
    ntemp=len(temps.group(1).split(',')) if temps else 0
    instr=[l for l in lines if re.match(r'^[A-Z][A-Z0-9_.]*\b',l) and not l.startswith(('TEMP','PARAM','ATTRIB','OPTION','OUTPUT','SHORT','END','LONG','INT'))]
    ops={}
    for l in instr:
        op=l.split()[0].split('.')[0]; ops[op]=ops.get(op,0)+1
    subs=len(re.findall(r'^\S+:\s*$',t,re.M))
    return dict(chars=len(t),temps=ntemp,instr=len(instr),CAL=ops.get('CAL',0),RET=ops.get('RET',0),TEX=ops.get('TEX',0)+ops.get('TXL',0)+ops.get('TXD',0),REP=ops.get('REP',0),labels=subs), t
if __name__=='__main__':
    for p in sys.argv[1:]:
        s,t=stat(p); print(p,s)
        open(p+'.txt','w').write(t)
