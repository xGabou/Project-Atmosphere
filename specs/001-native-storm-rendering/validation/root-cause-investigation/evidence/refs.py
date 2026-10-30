import os, re, collections
roots = ['main/java', 'main/resources', 'test/java']
files = {}
for r in roots:
    for d, _, fs in os.walk(r):
        for f in fs:
            if f.endswith(('.java', '.json', '.fsh', '.vsh', '.glsl', '.toml', '.mixins.json')):
                p = os.path.join(d, f); files[p] = open(p, encoding='utf-8', errors='replace').read()
classes = {}
for p in files:
    if p.startswith('main/java') and p.endswith('.java') and '/clouds/' in p.replace(os.sep, '/'):
        classes[os.path.basename(p)[:-5]] = p
tok = re.compile(r'\b[A-Z][A-Za-z0-9_]+\b')
mainrefs = collections.Counter(); testrefs = collections.Counter()
for p, s in files.items():
    names = set(tok.findall(s))
    me = os.path.basename(p)[:-5] if p.endswith('.java') else None
    for n in names:
        if n in classes and n != me:
            (testrefs if p.startswith('test') else mainrefs)[n] += 1
for c in sorted(classes, key=lambda c: (mainrefs[c], c)):
    print(mainrefs[c], testrefs[c], c, classes[c].replace(os.sep, '/').split('projectatmosphere/clouds/')[1])
