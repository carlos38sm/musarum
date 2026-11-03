package emendatium.backend.analysis;

/** FFT iterativa (Cooley-Tukey) para tamaños potencia de 2. Compartida por los analizadores. */
final class Fft {

    private Fft() {}

    static void transform(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wR = Math.cos(ang), wI = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cR = 1, cI = 0;
                for (int k = 0; k < len / 2; k++) {
                    int u = i + k, v = i + k + len / 2;
                    double tR = cR * re[v] - cI * im[v];
                    double tI = cR * im[v] + cI * re[v];
                    re[v] = re[u] - tR; im[v] = im[u] - tI;
                    re[u] += tR; im[u] += tI;
                    double tmp = cR * wR - cI * wI;
                    cI = cR * wI + cI * wR;
                    cR = tmp;
                }
            }
        }
    }
}