import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an uploaded .txt file and turns it into a chain of matrices.
 *
 * Two formats are accepted (comments start with # and are ignored):
 *
 * FORMAT 1 - full matrices (real numbers, used to show the actual multiplication):
 *     3                 <- number of matrices
 *     2 3               <- rows and columns of the first matrix
 *     1 2 3             <- its rows
 *     4 5 6
 *     3 2               <- next matrix ...
 *
 * FORMAT 2 - dimensions only (fast, works for very long chains):
 *     DIMS 10 30 5 60   <- matrix i has size p[i-1] x p[i]
 */
final class MatrixParser {

    static final int MAX_MATRICES = 600;
    static final int MAX_DIM = 100000;
    static final int MAX_EXPLICIT_VALUES = 400000;

    static final class Parsed {
        String format;            // "matrices" or "dims"
        int[] dims;               // p[0..n]
        BigInteger[][][] mats;    // 1-indexed (mats[1] = A1); null for dims-only files
        List<String> warnings = new ArrayList<>();
        int n() { return dims.length - 1; }
    }

    static final class ParseException extends Exception {
        ParseException(String message) { super(message); }
    }

    static Parsed parse(String text) throws ParseException {
        StringBuilder clean = new StringBuilder();
        for (String line : text.split("\\R")) {
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            clean.append(line).append('\n');
        }
        String body = clean.toString()
                .replace('\uFEFF', ' ')
                .replace(',', ' ')
                .replace(';', ' ')
                .trim();
        if (body.isEmpty()) throw new ParseException("The file is empty (nothing left after removing # comment lines).");

        String[] tok = body.split("\\s+");
        String head = tok[0].toLowerCase().replace(":", "");
        if (head.equals("dims") || head.equals("dimensions") || head.equals("dim")) {
            return parseDims(tok);
        }
        return parseMatrices(tok);
    }

    // ---------------------------------------------------------------- format 2
    private static Parsed parseDims(String[] tok) throws ParseException {
        int count = tok.length - 1;
        if (count < 3) {
            throw new ParseException("A dimensions file needs at least 3 numbers (that makes 2 matrices). "
                    + "Example:  DIMS 10 30 5 60");
        }
        int[] dims = new int[count];
        for (int i = 0; i < count; i++) {
            dims[i] = toInt(tok[i + 1], "dimension #" + (i + 1));
            if (dims[i] < 1 || dims[i] > MAX_DIM)
                throw new ParseException("Dimension #" + (i + 1) + " is " + dims[i] + ". Every dimension must be between 1 and " + MAX_DIM + ".");
        }
        if (dims.length - 1 > MAX_MATRICES)
            throw new ParseException("This chain has " + (dims.length - 1) + " matrices. The web demo supports up to " + MAX_MATRICES + ".");
        Parsed p = new Parsed();
        p.format = "dims";
        p.dims = dims;
        return p;
    }

    // ---------------------------------------------------------------- format 1
    private static Parsed parseMatrices(String[] tok) throws ParseException {
        int pos = 0;
        int n = toInt(tok[pos++], "the number of matrices (first number in the file)");
        if (n < 2) throw new ParseException("You need at least 2 matrices to multiply. The first number in the file says " + n + ".");
        if (n > MAX_MATRICES) throw new ParseException("This chain has " + n + " matrices. The web demo supports up to " + MAX_MATRICES + ".");

        int[] rows = new int[n + 1];
        int[] cols = new int[n + 1];
        BigInteger[][][] mats = new BigInteger[n + 1][][];
        long totalValues = 0;

        for (int m = 1; m <= n; m++) {
            if (pos + 1 >= tok.length)
                throw new ParseException("The file ends before the size of matrix A" + m + " is given. Expected " + n + " matrices.");
            rows[m] = toInt(tok[pos++], "the row count of A" + m);
            cols[m] = toInt(tok[pos++], "the column count of A" + m);
            if (rows[m] < 1 || cols[m] < 1 || rows[m] > MAX_DIM || cols[m] > MAX_DIM)
                throw new ParseException("Matrix A" + m + " has an invalid size " + rows[m] + " x " + cols[m] + ".");
            if (m > 1 && cols[m - 1] != rows[m]) {
                throw new ParseException("A" + (m - 1) + " is " + rows[m - 1] + " x " + cols[m - 1] + " and A" + m + " is "
                        + rows[m] + " x " + cols[m] + ". They cannot be multiplied: the columns of A" + (m - 1)
                        + " (" + cols[m - 1] + ") must equal the rows of A" + m + " (" + rows[m] + ").");
            }
            long need = (long) rows[m] * cols[m];
            totalValues += need;
            if (totalValues > MAX_EXPLICIT_VALUES)
                throw new ParseException("The file contains more than " + MAX_EXPLICIT_VALUES + " numbers. "
                        + "For big chains use the dimensions-only format:  DIMS p0 p1 ... pn");
            if (pos + need > tok.length) {
                long have = tok.length - pos;
                throw new ParseException("Matrix A" + m + " (" + rows[m] + " x " + cols[m] + ") needs " + need
                        + " numbers but the file only has " + have + " left.");
            }
            BigInteger[][] a = new BigInteger[rows[m]][cols[m]];
            for (int r = 0; r < rows[m]; r++) {
                for (int c = 0; c < cols[m]; c++) {
                    String t = tok[pos++];
                    try {
                        a[r][c] = new BigInteger(t);
                    } catch (NumberFormatException e) {
                        String hint = t.contains(".") ? " Only whole numbers are supported." : "";
                        throw new ParseException("Matrix A" + m + ", row " + (r + 1) + ", column " + (c + 1)
                                + ": '" + t + "' is not a whole number." + hint);
                    }
                }
            }
            mats[m] = a;
        }

        Parsed p = new Parsed();
        p.format = "matrices";
        p.dims = new int[n + 1];
        p.dims[0] = rows[1];
        for (int m = 1; m <= n; m++) p.dims[m] = cols[m];
        p.mats = mats;
        if (pos < tok.length) p.warnings.add("The file has extra data after the last matrix. It was ignored.");
        return p;
    }

    private static int toInt(String t, String what) throws ParseException {
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw new ParseException("Expected a whole number for " + what + " but found '" + t + "'.");
        }
    }
}
