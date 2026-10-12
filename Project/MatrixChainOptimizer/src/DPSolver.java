import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Matrix Chain Multiplication by bottom-up Dynamic Programming.
 *
 * Matrix A_i has size p[i-1] x p[i].
 * m[i][j]  = minimum number of scalar multiplications needed for A_i ... A_j
 * s[i][j]  = the split point k that achieves that minimum
 *
 *   m[i][j] = min over i<=k<j of  m[i][k] + m[k+1][j] + p[i-1]*p[k]*p[j]
 *
 * The same table is also filled with MAX instead of MIN (to know the worst order)
 * and a plain left-to-right order is kept for comparison.
 */
final class DPSolver {

    final int n;
    final int[] p;
    final long[][] mn, mx;             // cheapest / most expensive cost of chain i..j
    final int[][] sOpt, sWorst, sLtr;  // split tables for the three orders

    DPSolver(int[] p) {
        this.p = p;
        this.n = p.length - 1;
        mn = new long[n + 1][n + 1];
        mx = new long[n + 1][n + 1];
        sOpt = new int[n + 1][n + 1];
        sWorst = new int[n + 1][n + 1];
        sLtr = new int[n + 1][n + 1];
        for (int len = 2; len <= n; len++) {
            for (int i = 1; i <= n - len + 1; i++) {
                int j = i + len - 1;
                mn[i][j] = Long.MAX_VALUE;
                mx[i][j] = Long.MIN_VALUE;
                sLtr[i][j] = j - 1;
                for (int k = i; k < j; k++) {
                    long extra = (long) p[i - 1] * p[k] * p[j];
                    long lo = mn[i][k] + mn[k + 1][j] + extra;
                    long hi = mx[i][k] + mx[k + 1][j] + extra;
                    if (lo < mn[i][j]) { mn[i][j] = lo; sOpt[i][j] = k; }
                    if (hi > mx[i][j]) { mx[i][j] = hi; sWorst[i][j] = k; }
                }
            }
        }
    }

    long bestCost()  { return mn[1][n]; }
    long worstCost() { return mx[1][n]; }
    long ltrCost()   { return costOf(sLtr, 1, n); }

    long costOf(int[][] s, int i, int j) {
        if (i >= j) return 0;
        int k = s[i][j];
        return costOf(s, i, k) + costOf(s, k + 1, j) + (long) p[i - 1] * p[k] * p[j];
    }

    String parens(int[][] s, int i, int j) {
        if (i == j) return "A" + i;
        return "(" + parens(s, i, s[i][j]) + " \u00d7 " + parens(s, s[i][j] + 1, j) + ")";
    }

    // ------------------------------------------------------------------ steps
    static final class Step {
        int idx, i, k, j;          // idx = step number, chain A_i..A_j split at k
        String left, right, res;   // names of the two operands and of the result
        int r, m, c;               // (r x m) times (m x c) gives (r x c)
        long cost;                 // r*m*c
    }

    /** Every multiplication, in the order they must be carried out. */
    List<Step> steps(int[][] s) {
        List<Step> out = new ArrayList<>();
        build(s, 1, n, out);
        return out;
    }

    private String build(int[][] s, int i, int j, List<Step> out) {
        if (i == j) return "A" + i;
        int k = s[i][j];
        String l = build(s, i, k, out);
        String r = build(s, k + 1, j, out);
        Step st = new Step();
        st.idx = out.size() + 1;
        st.i = i; st.k = k; st.j = j;
        st.left = l; st.right = r;
        st.res = "T" + st.idx;
        st.r = p[i - 1]; st.m = p[k]; st.c = p[j];
        st.cost = (long) st.r * st.m * st.c;
        out.add(st);
        return st.res;
    }

    // ------------------------------------------------------------------- tree
    /** Nested description of the optimal multiplication tree (used to draw it). */
    Map<String, Object> tree(int[][] s) {
        return treeNode(s, 1, n, new int[]{0});
    }

    private Map<String, Object> treeNode(int[][] s, int i, int j, int[] counter) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("i", i);
        node.put("j", j);
        node.put("rows", p[i - 1]);
        node.put("cols", p[j]);
        if (i == j) {
            node.put("leaf", true);
            node.put("label", "A" + i);
            return node;
        }
        int k = s[i][j];
        Map<String, Object> l = treeNode(s, i, k, counter);
        Map<String, Object> r = treeNode(s, k + 1, j, counter);
        counter[0]++;
        String label = "T" + counter[0];
        node.put("leaf", false);
        node.put("label", label);
        node.put("expr", l.get("label") + " \u00d7 " + r.get("label"));
        node.put("k", k);
        node.put("cost", (long) p[i - 1] * p[k] * p[j]);
        node.put("left", l);
        node.put("right", r);
        return node;
    }
}
