# Matrix Chain Multiplication Optimizer

A web application (Java backend + HTML/CSS/JS front end) that finds the cheapest way to
multiply a chain of matrices using Dynamic Programming, shows every multiplication it
performs, and explains step by step how the algorithm found that order.

Project: DSA-3 (25CS2103E)

## Requirements

- Java **JDK** 11 or newer (not just the JRE — you need `javac`, not only `java`).
  Check with: `javac -version`
- A web browser. No internet connection needed — the page's fonts are bundled in
  `web/fonts/` and nothing is downloaded at runtime.

## How to run

**Mac / Linux**
```
./run.sh
```

**Windows**
```
run.bat
```

(Double-clicking `run.bat` in File Explorer also works.)

The first run compiles the code automatically. The program then prints a URL, for example:

```
Open this address in your browser:  http://localhost:8080/
```

It also tries to open that page for you automatically. If it doesn't, copy the address
into your browser by hand. To stop the server, go back to the terminal window and press
`Ctrl+C`.

If you prefer to run the two steps yourself:
```
javac -d out src/*.java
java -cp out Main
```

## How to use it

1. Go to the **Upload** section and drop in a `.txt` file (or click **Sample Files** below
   to try one of the 53 included examples first — click **Try it** on any card).
2. Click **Analyze this file**.
3. The **Result** section shows: the matrices read from the file, the optimal order, a cost
   comparison against a plain left-to-right order and the worst possible order, every
   multiplication step in order, and a real execution with actual numbers.
4. Click **Learn more → see how DP found this order** to open the **Learn More** section,
   which explains the recurrence Dynamic Programming solves, fills the cost table for your
   own chain cell by cell, shows the complete table, and draws the recursion tree that reads
   the best order back out of that table.

## Input file formats

Two formats are accepted. Lines starting with `#` are comments and are ignored.

**Dimensions only** — fast, and the only practical option for long chains:
```
DIMS 10 30 5 60
```
Matrix *i* has size `p[i-1] x p[i]`. Four numbers here mean 3 matrices: 10×30, 30×5, 5×60.

**Full matrices** — use this to see the real numbers get multiplied:
```
3
2 3
1 2 3
4 5 6
3 4
1 0 0 1
0 1 0 1
0 0 1 1
4 2
1 2
3 4
5 6
7 8
```
The first number is how many matrices follow. Then, for each matrix: its row count and
column count, followed by that many rows of numbers.

## What's in `samples/`

53 ready-to-use `.txt` files:
- `explicit_*.txt` — full matrices with real numbers, small enough to check by hand
- `dims_*.txt` — dimensions-only files, including well-known textbook chains with published
  answers (e.g. the CLRS 6-matrix example, answer 15,125) and very long chains (up to 500
  matrices) to demonstrate that the algorithm stays fast at scale
- `invalid_*.txt` — deliberately broken files, to demonstrate the error messages

## Project structure

```
run.sh / run.bat     one-click launchers
src/                 Java backend
  Main.java          tiny HTTP server (serves the page, the API, the samples)
  MatrixParser.java  reads and validates an uploaded .txt file
  DPSolver.java      the Dynamic Programming algorithm itself
  Analyzer.java      runs the solver, then really multiplies the matrices and
                     records every step for the web page to display
  Json.java          minimal JSON writer (no external libraries required)
web/                 front end
  index.html
  css/fonts.css     local @font-face declarations (Newsreader, IBM Plex Sans/Mono)
  css/style.css
  js/app.js
  fonts/            the bundled font files themselves
samples/             the 53 sample .txt files
```

## Notes on scale

- Matrices are generated with random small numbers (1–9) when the file only gives
  dimensions, so the multiplication can still be demonstrated with real arithmetic.
- For chains where even the optimal order needs more than a few million scalar
  multiplications, the real execution step is skipped (it would make the browser demo slow
  for no benefit) — the DP cost numbers are still exact, they are simply not additionally
  proven by brute execution for those specific huge cases.
- Time complexity: **O(n³)**. Space complexity: **O(n²)**. Verified in this project up to
  n = 500 matrices, solved in well under a second.
