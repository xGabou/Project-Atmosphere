import os, re, sys
root = sys.argv[1]
for d, _, fs in sorted(os.walk(root)):
    for f in sorted(fs):
        if not f.endswith('.java'): continue
        p = os.path.join(d, f)
        s = open(p, encoding='utf-8', errors='replace').read()
        n = s.count('\n')
        m = re.search(r'/\*\*(.*?)\*/\s*(?:@[\w.()"=, ]+\s*)*(?:public |final |abstract |sealed )*(?:class|record|enum|interface)', s, re.S)
        doc = ''
        if m:
            doc = re.sub(r'\s*\*\s?', ' ', m.group(1)).strip()
            doc = re.sub(r'<[^>]+>', '', doc)
            doc = re.sub(r'\{@\w+ ([^}]*)\}', r'\1', doc)
            doc = doc.split('. ')[0][:220]
        print(f"{os.path.relpath(p, root).replace(os.sep,'/')}\t{n}\t{doc}")
