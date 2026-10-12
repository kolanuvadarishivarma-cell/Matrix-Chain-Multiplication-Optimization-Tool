import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Turns a parsed chain into the JSON-ready result shown by the web page. */
final class Analyzer {

    static final int TRACE_MAX_N = 20;        // cell-by-cell trace is produced up to this many matrices
    static final int TABLE_MAX_N = 60;        // DP table is sent up to this many matrices
    static final int ORDER_STRING_MAX_N = 40; // parenthesised strings for comparison orders
    static final int TREE_MAX_N = 40;
    static final long EXEC_LIMIT_OPT = 3_000_000L;    // real execution only if optimal order needs <= this many mults
    static final long EXEC_LIMIT_OTHER = 10_000_000L; // same for the comparison orders
    static final int PREVIEW_MAX = 10;                // matrices are previewed up to 10 x 10
    static final BigInteger MOD = BigInteger.valueOf(1_000_000_007L);

    private Analyzer() {}

    static Map<String, Object> analyze(String fileName, MatrixParser.Parsed in) {
        int[] p = in.dims;
        int n = in.n();

        long t0 = System.nanoTime();
        DPSolver sv = new DPSolver(p);
        long solveMicros = (System.nanoTime() - t0) / 1000;

        long bestC = sv.bestCost(), worstC = sv.worstCost(), ltrC = sv.ltrCost();
        List<DPSolver.Step> steps = sv.steps(sv.sOpt);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("name", fileName);
        out.put("format", in.format);
        out.put("n", n);
        out.put("dims", p);
        out.put("solveMicros", solveMicros);
        out.put("warnings", in.warnings);

        List<Object> matrices = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", "A" + i);
            m.put("rows", p[i - 1]);
            m.put("cols", p[i]);
            matrices.add(m);
        }
        out.put("matrices", matrices);

        // ------------------------------------------------ DP trace (cell by cell)
        Map<Long, List<Object>> altsByCell = new HashMap<>();
        if (n <= TRACE_MAX_N) {
            List<Object> trace = new ArrayList<>();
            for (int len = 2; len <= n; len++) {
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("len", len);
                List<Object> cells = new ArrayList<>();
                for (int i = 1; i <= n - len + 1; i++) {
                    int j = i + len - 1;
                    Map<String, Object> cell = new LinkedHashMap<>();
                    cell.put("i", i);
                    cell.put("j", j);
                    cell.put("best", sv.mn[i][j]);
                    cell.put("k", sv.sOpt[i][j]);
                    List<Object> cands = new ArrayList<>();
                    List<Object> alts = new ArrayList<>();
                    for (int k = i; k < j; k++) {
                        long mult = (long) p[i - 1] * p[k] * p[j];
                        long total = sv.mn[i][k] + sv.mn[k + 1][j] + mult;
                        Map<String, Object> c = new LinkedHashMap<>();
                        c.put("k", k);
                        c.put("left", sv.mn[i][k]);
                        c.put("right", sv.mn[k + 1][j]);
                        c.put("factors", new int[]{p[i - 1], p[k], p[j]});
                        c.put("mult", mult);
                        c.put("total", total);
                        cands.add(c);
                        Map<String, Object> a = new LinkedHashMap<>();
                        a.put("k", k);
                        a.put("total", total);
                        a.put("chosen", k == sv.sOpt[i][j]);
                        alts.add(a);
                    }
                    cell.put("cands", cands);
                    altsByCell.put((long) i * 1000 + j, alts);
                    cells.add(cell);
                }
                group.put("cells", cells);
                trace.add(group);
            }
            out.put("trace", trace);
        } else {
            out.put("trace", null);
        }

        // ------------------------------------------------ DP table
        if (n <= TABLE_MAX_N) {
            long[][] m = new long[n][n];
            int[][] s = new int[n][n];
            for (int i = 1; i <= n; i++)
                for (int j = i; j <= n; j++) {
                    m[i - 1][j - 1] = sv.mn[i][j];
                    s[i - 1][j - 1] = sv.sOpt[i][j];
                }
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("m", toRows(m));
            table.put("s", s);
            out.put("table", table);
        } else {
            out.put("table", null);
        }

        // ------------------------------------------------ best order
        Map<String, Object> best = new LinkedHashMap<>();
        best.put("cost", bestC);
        String order = sv.parens(sv.sOpt, 1, n);
        best.put("order", n <= ORDER_STRING_MAX_N * 5 ? order : null);
        best.put("tree", n <= TREE_MAX_N ? sv.tree(sv.sOpt) : null);

        List<Object> stepList = new ArrayList<>();
        long running = 0;
        for (DPSolver.Step st : steps) {
            running += st.cost;
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("idx", st.idx);
            sm.put("i", st.i);
            sm.put("k", st.k);
            sm.put("j", st.j);
            sm.put("left", st.left);
            sm.put("right", st.right);
            sm.put("res", st.res);
            sm.put("r", st.r);
            sm.put("m", st.m);
            sm.put("c", st.c);
            sm.put("cost", st.cost);
            sm.put("cumulative", running);
            List<Object> alts = altsByCell.get((long) st.i * 1000 + st.j);
            sm.put("alternatives", alts);
            stepList.add(sm);
        }
        best.put("steps", stepList);
        out.put("best", best);

        // ------------------------------------------------ comparison orders
        Map<String, Object> ltr = new LinkedHashMap<>();
        ltr.put("cost", ltrC);
        ltr.put("order", n <= ORDER_STRING_MAX_N ? sv.parens(sv.sLtr, 1, n) : null);
        out.put("ltr", ltr);
        Map<String, Object> worst = new LinkedHashMap<>();
        worst.put("cost", worstC);
        worst.put("order", n <= ORDER_STRING_MAX_N ? sv.parens(sv.sWorst, 1, n) : null);
        out.put("worst", worst);
        out.put("savedVsWorst", worstC > 0 ? 100.0 * (worstC - bestC) / worstC : 0.0);
        out.put("savedVsLtr", ltrC > 0 ? 100.0 * (ltrC - bestC) / ltrC : 0.0);

        // ------------------------------------------------ real execution
        out.put("execution", execute(in, sv, steps, stepList, bestC, ltrC, worstC));
        return out;
    }

    // ==================================================================== execution
    private static Map<String, Object> execute(MatrixParser.Parsed in, DPSolver sv, List<DPSolver.Step> steps,
                                               List<Object> stepList, long bestC, long ltrC, long worstC) {
        int n = sv.n;
        int[] p = sv.p;
        Map<String, Object> ex = new LinkedHashMap<>();
        if (bestC > EXEC_LIMIT_OPT) {
            ex.put("performed", false);
            ex.put("reason", String.format("Even the best order needs %,d scalar multiplications, which is too heavy to run "
                    + "in the browser demo (limit %,d). The order and costs above are still exact - they come from the DP.",
                    bestC, EXEC_LIMIT_OPT));
            return ex;
        }

        boolean generated = in.mats == null;
        BigInteger mod = generated ? MOD : null;
        BigInteger[][][] mats;
        if (generated) {
            Random rnd = new Random(2025);
            mats = new BigInteger[n + 1][][];
            for (int i = 1; i <= n; i++) {
                BigInteger[][] a = new BigInteger[p[i - 1]][p[i]];
                for (int r = 0; r < a.length; r++)
                    for (int c = 0; c < a[r].length; c++) a[r][c] = BigInteger.valueOf(1 + rnd.nextInt(9));
                mats[i] = a;
            }
        } else {
            mats = in.mats;
        }

        ex.put("performed", true);
        ex.put("generated", generated);
        ex.put("modulus", mod == null ? null : mod.toString());

        // timed runs of the three orders
        long t = System.nanoTime();
        BigInteger[][] resOpt = run(sv.sOpt, mats, 1, n, mod);
        double msOpt = (System.nanoTime() - t) / 1e6;

        Double msLtr = null, msWorst = null;
        boolean same = true;
        if (ltrC <= EXEC_LIMIT_OTHER) {
            t = System.nanoTime();
            BigInteger[][] r = run(sv.sLtr, mats, 1, n, mod);
            msLtr = (System.nanoTime() - t) / 1e6;
            same &= java.util.Arrays.deepEquals(r, resOpt);
        }
        if (worstC <= EXEC_LIMIT_OTHER) {
            t = System.nanoTime();
            BigInteger[][] r = run(sv.sWorst, mats, 1, n, mod);
            msWorst = (System.nanoTime() - t) / 1e6;
            same &= java.util.Arrays.deepEquals(r, resOpt);
        }
        Map<String, Object> timings = new LinkedHashMap<>();
        timings.put("opt", msOpt);
        timings.put("ltr", msLtr);
        timings.put("worst", msWorst);
        ex.put("timings", timings);
        ex.put("verified", same);

        // step-by-step run that records the numbers of every multiplication
        Map<String, BigInteger[][]> vals = new HashMap<>();
        for (int i = 1; i <= n; i++) vals.put("A" + i, mats[i]);
        for (int idx = 0; idx < steps.size(); idx++) {
            DPSolver.Step st = steps.get(idx);
            BigInteger[][] a = vals.get(st.left);
            BigInteger[][] b = vals.get(st.right);
            BigInteger[][] c = multiply(a, b, mod);
            vals.put(st.res, c);
            @SuppressWarnings("unchecked")
            Map<String, Object> sm = (Map<String, Object>) stepList.get(idx);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("left", preview(a));
            values.put("right", preview(b));
            values.put("result", preview(c));
            values.put("example", example(a, b, c, 0, 0));
            if (c.length > 1 || c[0].length > 1) values.put("example2", example(a, b, c, c.length - 1, c[0].length - 1));
            sm.put("values", values);
        }
        ex.put("finalResult", preview(resOpt));
        if (n <= 12) {
            List<Object> inputs = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", "A" + i);
                m.put("values", preview(mats[i]));
                inputs.add(m);
            }
            ex.put("inputs", inputs);
        }
        return ex;
    }

    private static BigInteger[][] run(int[][] s, BigInteger[][][] mats, int i, int j, BigInteger mod) {
        if (i == j) return mats[i];
        int k = s[i][j];
        return multiply(run(s, mats, i, k, mod), run(s, mats, k + 1, j, mod), mod);
    }

    static BigInteger[][] multiply(BigInteger[][] a, BigInteger[][] b, BigInteger mod) {
        int r = a.length, inner = b.length, cc = b[0].length;
        BigInteger[][] res = new BigInteger[r][cc];
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < cc; j++) {
                BigInteger sum = BigInteger.ZERO;
                for (int k = 0; k < inner; k++) sum = sum.add(a[i][k].multiply(b[k][j]));
                res[i][j] = mod == null ? sum : sum.mod(mod);
            }
        }
        return res;
    }

    /** Top-left corner of a matrix (at most 10 x 10) as strings. */
    private static Map<String, Object> preview(BigInteger[][] a) {
        int r = a.length, c = a[0].length;
        int sr = Math.min(r, PREVIEW_MAX), sc = Math.min(c, PREVIEW_MAX);
        List<Object> data = new ArrayList<>();
        for (int i = 0; i < sr; i++) {
            List<Object> row = new ArrayList<>();
            for (int j = 0; j < sc; j++) row.add(a[i][j].toString());
            data.add(row);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rows", r);
        m.put("cols", c);
        m.put("data", data);
        return m;
    }

    /** Shows exactly which numbers were multiplied and added to get one element of the result. */
    private static Map<String, Object> example(BigInteger[][] a, BigInteger[][] b, BigInteger[][] c, int row, int col) {
        int inner = b.length;
        int shown = Math.min(inner, 6);
        List<Object> terms = new ArrayList<>();
        for (int k = 0; k < shown; k++) {
            terms.add(new String[]{a[row][k].toString(), b[k][col].toString()});
        }
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("row", row + 1);
        e.put("col", col + 1);
        e.put("terms", terms);
        e.put("totalTerms", inner);
        e.put("value", c[row][col].toString());
        return e;
    }

    private static List<Object> toRows(long[][] a) {
        List<Object> rows = new ArrayList<>();
        for (long[] r : a) rows.add(r);
        return rows;
    }
}
