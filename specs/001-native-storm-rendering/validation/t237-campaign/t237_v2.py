ROOT = 'C:/Users/matga/Downloads/Project-Atmosphere/'
P = ROOT + 'src/main/java/net/Gabou/projectatmosphere/clouds/simulation/CloudMorphologyGenerators.java'
raw = open(P, encoding='utf-8', newline='').read()
crlf = '\r\n' in raw
s = raw.replace('\r\n', '\n')


def rep(a, b, n=1):
    global s
    assert s.count(a) == n, (a[:70], s.count(a))
    s = s.replace(a, b)


rep('''    private static final float[] V1_ANVIL_RADIUS = {0.42F, 0.62F, 0.72F, 0.60F};
''', '''    private static final float[] V1_ANVIL_RADIUS = {0.42F, 0.62F, 0.72F, 0.60F};
    // T237 V2 (the one revision): the V1 layout with the anvil deck lowered
    // onto the tower's visible top (V1 left a sky gap in the along-wind view),
    // a thinner deck and broader lobes (V1's small lobes read as balls).
    private static final float[] V2_ANVIL_ALONG = {0.10F, 0.50F, 0.90F, 1.30F};
    private static final float[] V2_ANVIL_CROSS = {0.12F, -0.20F, 0.22F, -0.10F};
    private static final float[] V2_ANVIL_RADIUS = {0.55F, 0.72F, 0.86F, 0.80F};
''')
rep('''        if (stormTemplateVersion == 1) {
            return stormCellV1(''', '''        if (stormTemplateVersion >= 1) {
            return stormCellV1(''')
rep('''        if (stormTemplateVersion == 1) {
            return stormLobeSpecV1(''', '''        if (stormTemplateVersion >= 1) {
            return stormLobeSpecV1(''')
# spec: anvil deck thickness and radius
rep('''                int a = Math.min(V1_ANVIL_RADIUS.length - 1, Math.max(0, clusterIndex - anvilStart));
                radiusMultiplier = V1_ANVIL_RADIUS[a];
                baseY = centerY - t * 0.10F;
                topY = centerY + t * 0.10F;''', '''                int a = Math.min(V1_ANVIL_RADIUS.length - 1, Math.max(0, clusterIndex - anvilStart));
                boolean v2 = stormTemplateVersion >= 2;
                radiusMultiplier = (v2 ? V2_ANVIL_RADIUS : V1_ANVIL_RADIUS)[a];
                float halfDeck = v2 ? 0.08F : 0.10F;
                baseY = centerY - t * halfDeck;
                topY = centerY + t * halfDeck;''')
# placement: anvil row lowered
rep('''                int a = Math.min(V1_ANVIL_ALONG.length - 1, Math.max(0, clusterIndex - anvilStart));
                along = g * V1_ANVIL_ALONG[a] * (0.88F + (jitterA + 0.5F) * 0.24F);
                cross = g * (V1_ANVIL_CROSS[a] + jitterC * 0.10F);
                y = t * (0.80F - 0.01F * a);''', '''                int a = Math.min(V1_ANVIL_ALONG.length - 1, Math.max(0, clusterIndex - anvilStart));
                boolean v2 = stormTemplateVersion >= 2;
                along = g * (v2 ? V2_ANVIL_ALONG : V1_ANVIL_ALONG)[a] * (0.88F + (jitterA + 0.5F) * 0.24F);
                cross = g * ((v2 ? V2_ANVIL_CROSS : V1_ANVIL_CROSS)[a] + jitterC * 0.10F);
                y = t * ((v2 ? 0.74F : 0.80F) - 0.01F * a);''')
if crlf:
    s = s.replace('\n', '\r\n')
open(P, 'w', encoding='utf-8', newline='').write(s)
print('ok')
