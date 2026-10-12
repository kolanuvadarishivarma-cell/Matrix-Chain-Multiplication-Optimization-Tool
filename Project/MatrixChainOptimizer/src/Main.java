import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Matrix Chain Multiplication Optimizer - web server.
 *
 *   GET  /                 the web page
 *   GET  /api/samples      list of the sample .txt files
 *   GET  /samples/<file>   download a sample file
 *   POST /api/solve?name=  body = contents of an uploaded .txt file -> JSON result
 */
public class Main {

    static Path webDir;
    static Path samplesDir;
    static final int MAX_BODY = 5 * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        webDir = root.resolve("web");
        samplesDir = root.resolve("samples");
        if (!Files.isDirectory(webDir)) {
            System.err.println("Cannot find the 'web' folder in " + root);
            System.err.println("Start the program from the project folder (use run.bat on Windows or ./run.sh on Mac/Linux).");
            return;
        }

        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer server = null;
        for (int p = port; p < port + 20 && server == null; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), p), 0);
                port = p;
            } catch (IOException busy) {
                // port in use - try the next one
            }
        }
        if (server == null) {
            System.err.println("Could not open any port between " + port + " and " + (port + 19));
            return;
        }

        server.createContext("/api/solve", Main::handleSolve);
        server.createContext("/api/samples", Main::handleSamples);
        server.createContext("/api/dataset-run", Main::handleDatasetRun);
        server.createContext("/", Main::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        String url = "http://localhost:" + port + "/";
        System.out.println("==============================================================");
        System.out.println("  Matrix Chain Multiplication Optimizer is running");
        System.out.println("  Open this address in your browser:  " + url);
        System.out.println("  (press Ctrl+C to stop)");
        System.out.println("==============================================================");
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Throwable ignored) {
            // no browser available - the user can open the URL by hand
        }
    }

    // ------------------------------------------------------------------ /api/solve
    private static void handleSolve(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, error("Use POST with the file contents as the request body."));
                return;
            }
            String name = queryParam(ex.getRequestURI().getRawQuery(), "name", "uploaded.txt");
            byte[] body = readLimited(ex.getRequestBody());
            if (body == null) {
                sendJson(ex, 413, error("The file is too large (limit 5 MB)."));
                return;
            }
            String text = new String(body, StandardCharsets.UTF_8);
            try {
                MatrixParser.Parsed parsed = MatrixParser.parse(text);
                Map<String, Object> result = Analyzer.analyze(name, parsed);
                sendJson(ex, 200, result);
            } catch (MatrixParser.ParseException pe) {
                sendJson(ex, 400, error(pe.getMessage()));
            }
        } catch (Throwable t) {
            t.printStackTrace();
            sendJson(ex, 500, error("Unexpected server error: " + t));
        }
    }

    // ---------------------------------------------------------------- /api/samples
    private static void handleSamples(HttpExchange ex) throws IOException {
        List<Object> list = new ArrayList<>();
        if (Files.isDirectory(samplesDir)) {
            try (Stream<Path> s = Files.list(samplesDir)) {
                Path[] files = s.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".txt"))
                        .sorted().toArray(Path[]::new);
                for (Path f : files) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    String file = f.getFileName().toString();
                    m.put("file", file);
                    String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                    m.put("title", firstComment(text, file));
                    try {
                        MatrixParser.Parsed p = MatrixParser.parse(text);
                        m.put("valid", true);
                        m.put("format", p.format);
                        m.put("n", p.n());
                    } catch (MatrixParser.ParseException pe) {
                        m.put("valid", false);
                        m.put("format", "invalid");
                        m.put("n", 0);
                        m.put("error", pe.getMessage());
                    }
                    list.add(m);
                }
            }
        }
        sendJson(ex, 200, list);
    }

    // ------------------------------------------------------------ /api/dataset-run
    // Runs the DP solver on every sample file in one pass, so the page can show a
    // single table proving the algorithm across the whole dataset, not just one file.
    private static void handleDatasetRun(HttpExchange ex) throws IOException {
        List<Object> rows = new ArrayList<>();
        int skipped = 0;
        long totalMicros = 0;
        if (Files.isDirectory(samplesDir)) {
            try (Stream<Path> s = Files.list(samplesDir)) {
                Path[] files = s.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".txt"))
                        .sorted().toArray(Path[]::new);
                for (Path f : files) {
                    String file = f.getFileName().toString();
                    String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                    MatrixParser.Parsed parsed;
                    try {
                        parsed = MatrixParser.parse(text);
                    } catch (MatrixParser.ParseException pe) {
                        skipped++;
                        continue; // invalid demo files are not solvable - excluded from the run
                    }
                    int n = parsed.n();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("file", file);
                    row.put("title", firstComment(text, file));
                    row.put("n", n);
                    if (n < 2) {
                        row.put("best", 0);
                        row.put("worst", 0);
                        row.put("savedPct", 0.0);
                        row.put("micros", 0);
                        rows.add(row);
                        continue;
                    }
                    long t0 = System.nanoTime();
                    DPSolver sv = new DPSolver(parsed.dims);
                    long micros = (System.nanoTime() - t0) / 1000;
                    totalMicros += micros;
                    long best = sv.bestCost(), worst = sv.worstCost();
                    row.put("best", best);
                    row.put("worst", worst);
                    row.put("savedPct", worst > 0 ? 100.0 * (worst - best) / worst : 0.0);
                    row.put("micros", micros);
                    rows.add(row);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("count", rows.size());
        result.put("skippedInvalid", skipped);
        result.put("totalMicros", totalMicros);
        result.put("rows", rows);
        sendJson(ex, 200, result);
    }

    private static String firstComment(String text, String fallback) {
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("#")) {
                String c = t.substring(1).trim();
                if (!c.isEmpty()) return c;
            }
        }
        return fallback;
    }

    // ---------------------------------------------------------------------- static
    private static void handleStatic(HttpExchange ex) throws IOException {
        try {
            String path = URLDecoder.decode(ex.getRequestURI().getPath(), "UTF-8");
            if (path.equals("/")) path = "/index.html";
            Path base = webDir;
            if (path.startsWith("/samples/")) {
                base = samplesDir;
                path = path.substring("/samples".length());
            }
            Path file = base.resolve(path.substring(1)).normalize();
            if (!file.startsWith(base) || !Files.isRegularFile(file)) {
                byte[] msg = "404 - not found".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.sendResponseHeaders(404, msg.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
                return;
            }
            byte[] data = Files.readAllBytes(file);
            ex.getResponseHeaders().set("Content-Type", mime(file.getFileName().toString()));
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        } finally {
            ex.close();
        }
    }

    private static String mime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".woff")) return "font/woff";
        return "application/octet-stream";
    }

    // --------------------------------------------------------------------- helpers
    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", message);
        return m;
    }

    private static void sendJson(HttpExchange ex, int code, Object body) throws IOException {
        byte[] data = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        ex.close();
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) {
            bos.write(buf, 0, r);
            if (bos.size() > MAX_BODY) return null;
        }
        return bos.toByteArray();
    }

    private static String queryParam(String query, String key, String def) {
        if (query == null) return def;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                try {
                    String v = URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                    return v.isEmpty() ? def : v;
                } catch (Exception e) {
                    return def;
                }
            }
        }
        return def;
    }
}
