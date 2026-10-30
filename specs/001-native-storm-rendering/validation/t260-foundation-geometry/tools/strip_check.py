"""Proves the T260.16 shader edit cannot reach any program that does not define PA_CONVECTIVE_SYSTEM_T260: evaluates only
the PA_CONVECTIVE_SYSTEM_T260 conditionals as UNDEFINED (every other directive line is kept verbatim with its content)
in the before / after copies and compares the result byte for byte."""
import os, sys
before, after, src_after = sys.argv[1:4]


def strip(text):
    out, stack = [], []  # stack entries: (is_t260, skipping_this_level)
    for line in text.splitlines(keepends=True):
        t = line.strip()
        skipping = any(s for _, s in stack)
        if t.startswith('#if'):
            is_t = t.startswith('#ifdef PA_CONVECTIVE_SYSTEM_T260') or t.startswith('#if defined(PA_CONVECTIVE_SYSTEM_T260)')
            stack.append((is_t, is_t))
            if not is_t and not skipping:
                out.append(line)
            continue
        if t.startswith('#else') and stack:
            is_t, s = stack[-1]
            if is_t:
                stack[-1] = (True, False)  # the #else branch is the one taken without the define
                continue
            if not skipping:
                out.append(line)
            continue
        if t.startswith('#endif') and stack:
            is_t, _ = stack.pop()
            if not is_t and not any(s for _, s in stack):
                out.append(line)
            continue
        if not skipping:
            out.append(line)
    return ''.join(out)


same = diff = t260 = 0
for n in sorted(os.listdir(before)):
    if not n.endswith('.fsh') or n.startswith('SOURCE'):
        continue
    a = open(os.path.join(before, n), encoding='utf-8').read()
    b = open(os.path.join(after, n), encoding='utf-8').read()
    if 'PA_CONVECTIVE_SYSTEM_T260 1' in b:
        t260 += 1
        continue
    if strip(a) == strip(b):
        same += 1
    else:
        diff += 1
        print('DIFFERS outside the T260 blocks:', n)
src_a = open(os.path.join(before, 'SOURCE_cloud_atmosphere_volume.fsh'), encoding='utf-8').read()
src_b = open(src_after, encoding='utf-8').read()
print(f'variants without PA_CONVECTIVE_SYSTEM_T260: {same} identical once the T260 blocks are evaluated as undefined, {diff} differ; '
      f'T260 fixture variants (changed by design): {t260}; source / monolith identical the same way: {strip(src_a) == strip(src_b)}; '
      f'raw before/after identical (sanity, expect False): {src_a == src_b}')
