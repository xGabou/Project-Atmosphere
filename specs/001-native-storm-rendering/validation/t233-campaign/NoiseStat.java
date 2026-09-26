import net.Gabou.projectatmosphere.clouds.client.render.volumetric.CloudNoiseFieldModel;
public class NoiseStat {
    public static void main(String[] a) {
        byte[] d = CloudNoiseFieldModel.bakeBase();
        int N = CloudNoiseFieldModel.BASE_SIZE, n = N*N*N;
        double[][] ch = new double[4][n];
        for (int i = 0; i < n; i++) for (int c = 0; c < 4; c++) ch[c][i] = (d[i*4+c] & 0xFF) / 255.0;
        double[] mean = new double[4], sd = new double[4];
        for (int c = 0; c < 4; c++) { double s=0,s2=0; for (double v: ch[c]) {s+=v;s2+=v*v;} mean[c]=s/n; sd[c]=Math.sqrt(s2/n-mean[c]*mean[c]); }
        String names="rgba";
        for (int c = 0; c < 4; c++) System.out.printf("%c mean %.4f sd %.4f%n", names.charAt(c), mean[c], sd[c]);
        double cov=0; for (int i=0;i<n;i++) cov+=(ch[1][i]-mean[1])*(ch[2][i]-mean[2]);
        System.out.printf("corr g-b %.4f%n", cov/n/(sd[1]*sd[2]));
        // autocorrelation along x at lags (texels; 1 texel = 25 blocks)
        for (int c = 1; c <= 2; c++) {
            StringBuilder sb = new StringBuilder(names.charAt(c)+" acf:");
            for (int lag : new int[]{1,2,3,4,6,8,12,16}) {
                double s=0; for (int z=0;z<N;z++) for(int y=0;y<N;y++) for(int x=0;x<N;x++){int i=(z*N+y)*N+x, j=(z*N+y)*N+((x+lag)%N); s+=(ch[c][i]-mean[c])*(ch[c][j]-mean[c]);}
                sb.append(String.format(" lag%d(%db)=%.3f", lag, lag*25, s/n/(sd[c]*sd[c])));
            }
            System.out.println(sb);
        }
        // percentiles of signed b
        double[] sb2 = new double[n]; for (int i=0;i<n;i++) sb2[i]=(ch[2][i]-mean[2])/sd[2];
        java.util.Arrays.sort(sb2);
        System.out.printf("b signed p1 %.2f p5 %.2f p50 %.2f p95 %.2f p99 %.2f%n", sb2[n/100], sb2[n/20], sb2[n/2], sb2[n*95/100], sb2[n*99/100]);
    }
}
