/* Matrix Chain Multiplication Optimizer - front end */
(() => {
  "use strict";

  const $ = (sel) => document.querySelector(sel);
  const el = (tag, cls, html) => {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html !== undefined) e.innerHTML = html;
    return e;
  };
  const fmt = (n) => {
    if (n === null || n === undefined) return "-";
    if (typeof n === "string") {
      // big integers arrive as strings (values beyond JS safe range)
      return n.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    }
    return Number(n).toLocaleString("en-US");
  };
  const pct = (n) => (n === null || n === undefined ? "-" : n.toFixed(2) + "%");

  let lastResult = null;

  // ---------------------------------------------------------------- upload
  const dropzone = $("#dropzone");
  const fileInput = $("#fileInput");
  const analyzeBtn = $("#analyzeBtn");
  const fileMeta = $("#fileMeta");
  const uploadError = $("#uploadError");
  let pendingFile = null; // {name, text}

  function setPendingFile(name, text) {
    pendingFile = { name, text };
    $("#fileMetaName").textContent = name + "  (" + text.length.toLocaleString() + " characters)";
    fileMeta.classList.remove("hidden");
    analyzeBtn.disabled = false;
    hideError();
  }

  function clearPendingFile() {
    pendingFile = null;
    fileInput.value = "";
    fileMeta.classList.add("hidden");
    analyzeBtn.disabled = true;
  }

  function showError(msg) {
    uploadError.innerHTML = '<div class="err-title">This file could not be read</div>' + escapeHtml(msg);
    uploadError.classList.remove("hidden");
  }
  function hideError() { uploadError.classList.add("hidden"); }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  dropzone.addEventListener("click", () => fileInput.click());
  dropzone.addEventListener("dragover", (e) => { e.preventDefault(); dropzone.classList.add("dragover"); });
  dropzone.addEventListener("dragleave", () => dropzone.classList.remove("dragover"));
  dropzone.addEventListener("drop", (e) => {
    e.preventDefault();
    dropzone.classList.remove("dragover");
    if (e.dataTransfer.files.length) handleFile(e.dataTransfer.files[0]);
  });
  fileInput.addEventListener("change", () => {
    if (fileInput.files.length) handleFile(fileInput.files[0]);
  });
  $("#clearFile").addEventListener("click", clearPendingFile);

  function handleFile(file) {
    hideError();
    if (file.size > 5 * 1024 * 1024) { showError("The file is larger than 5 MB."); return; }
    if (!file.name.toLowerCase().endsWith(".txt")) { showError("Please choose a .txt file."); return; }
    const reader = new FileReader();
    reader.onload = () => setPendingFile(file.name, reader.result);
    reader.onerror = () => showError("The browser could not read this file.");
    reader.readAsText(file);
  }

  document.querySelectorAll(".format-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".format-btn").forEach((b) => b.classList.remove("active"));
      document.querySelectorAll(".format-box").forEach((b) => b.classList.add("hidden"));
      btn.classList.add("active");
      $("#" + btn.dataset.target).classList.remove("hidden");
    });
  });

  analyzeBtn.addEventListener("click", () => {
    if (!pendingFile) return;
    runAnalysis(pendingFile.name, pendingFile.text);
  });

  // ---------------------------------------------------------------- samples
  let samples = [];
  async function loadSamples() {
    const list = $("#sampleList");
    try {
      const res = await fetch("/api/samples");
      samples = await res.json();
      renderSamples("all");
    } catch (e) {
      list.innerHTML = '<div class="loading">Could not load the sample list. Is the server running?</div>';
    }
  }

  function renderSamples(filter) {
    const list = $("#sampleList");
    list.innerHTML = "";
    let items = samples;
    if (filter === "explicit") items = samples.filter((s) => s.format === "matrices");
    else if (filter === "dims") items = samples.filter((s) => s.format === "dims");
    else if (filter === "invalid") items = samples.filter((s) => !s.valid);

    if (items.length === 0) {
      list.innerHTML = '<div class="loading">No files in this category.</div>';
      return;
    }
    for (const s of items) {
      const card = el("div", "sample-card");
      const title = el("div", "sc-title", escapeHtml(s.title || s.file));
      const meta = el("div", "sc-meta");
      if (!s.valid) {
        meta.appendChild(el("span", "tag tag-invalid", "invalid file"));
      } else {
        meta.appendChild(el("span", "tag " + (s.format === "dims" ? "tag-dims" : "tag-matrices"), s.format === "dims" ? "dims only" : "full matrices"));
        meta.appendChild(el("span", "tag", s.n + " matrices"));
      }
      const actions = el("div", "sc-actions");
      const tryBtn = el("button", "sc-btn primary", "Try it");
      tryBtn.addEventListener("click", async () => {
        tryBtn.textContent = "Loading...";
        try {
          const r = await fetch("/samples/" + encodeURIComponent(s.file));
          const text = await r.text();
          setPendingFile(s.file, text);
          document.querySelector("#upload").scrollIntoView({ behavior: "smooth", block: "start" });
          runAnalysis(s.file, text);
        } finally {
          tryBtn.textContent = "Try it";
        }
      });
      const dlLink = el("a", "sc-btn", "Download");
      dlLink.href = "/samples/" + encodeURIComponent(s.file);
      dlLink.download = s.file;
      actions.appendChild(tryBtn);
      actions.appendChild(dlLink);
      card.appendChild(title);
      card.appendChild(meta);
      card.appendChild(actions);
      list.appendChild(card);
    }
  }

  document.querySelectorAll(".filter-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".filter-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      renderSamples(btn.dataset.filter);
    });
  });

  // ---------------------------------------------------------- run whole dataset
  const runDatasetBtn = $("#runDatasetBtn");
  runDatasetBtn.addEventListener("click", async () => {
    const out = $("#datasetRunResult");
    runDatasetBtn.disabled = true;
    runDatasetBtn.innerHTML = '<span class="spinner"></span>Running every sample file...';
    out.innerHTML = "";
    try {
      const res = await fetch("/api/dataset-run");
      const data = await res.json();
      if (!data.ok) { out.innerHTML = '<div class="error-box">Could not run the dataset.</div>'; return; }
      renderDatasetRun(data);
    } catch (e) {
      out.innerHTML = '<div class="error-box">Could not reach the server.</div>';
    } finally {
      runDatasetBtn.disabled = false;
      runDatasetBtn.textContent = "Run the entire dataset";
    }
  });

  function renderDatasetRun(data) {
    const out = $("#datasetRunResult");
    out.innerHTML = "";

    const kpis = el("div", "kpi-row");
    kpis.appendChild(kpi("Files solved", data.count, null));
    kpis.appendChild(kpi("Invalid files skipped", data.skippedInvalid, "(the deliberately broken demo files)"));
    kpis.appendChild(kpi("Total DP time", fmt(data.totalMicros) + " &micro;s", "for every file, combined", true));
    out.appendChild(kpis);

    const rows = data.rows.slice().sort((a, b) => a.n - b.n);
    const table = el("table", "compare-table");
    let body = `<tr><th>File</th><th class="num">Matrices</th><th class="num">Optimal cost</th><th class="num">Worst cost</th><th class="num">Saved</th><th class="num">Time</th></tr>`;
    rows.forEach((r) => {
      body += `<tr><td class="order-cell">${escapeHtml(r.title || r.file)}</td>
        <td class="num">${fmt(r.n)}</td>
        <td class="num">${r.n < 2 ? "&mdash;" : fmt(r.best)}</td>
        <td class="num">${r.n < 2 ? "&mdash;" : fmt(r.worst)}</td>
        <td class="num">${r.n < 2 ? "&mdash;" : r.savedPct.toFixed(2) + "%"}</td>
        <td class="num">${r.n < 2 ? "&mdash;" : fmt(r.micros) + " &micro;s"}</td></tr>`;
    });
    table.innerHTML = body;
    out.appendChild(el("div", "section-title", `Every test case, solved <span class="n">(sorted by chain length)</span>`));
    out.appendChild(table);
  }

  // ---------------------------------------------------------------- analyze
  async function runAnalysis(name, text) {
    hideError();
    analyzeBtn.disabled = true;
    analyzeBtn.innerHTML = '<span class="spinner"></span>Analyzing...';
    try {
      const res = await fetch("/api/solve?name=" + encodeURIComponent(name), { method: "POST", body: text });
      const data = await res.json();
      if (!data.ok) {
        showError(data.error || "Unknown error.");
        $("#result").classList.add("hidden");
        $("#learn").classList.add("hidden");
        $("#nav-result").classList.add("hidden");
        $("#nav-learn").classList.add("hidden");
        return;
      }
      lastResult = data;
      renderResult(data);
      renderLearn(data);
      $("#nav-result").classList.remove("hidden");
      $("#nav-learn").classList.remove("hidden");
      $("#result").classList.remove("hidden");
      $("#learn").classList.remove("hidden");
      $("#result").scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (e) {
      showError("Could not reach the server. Is it still running?");
    } finally {
      analyzeBtn.disabled = false;
      analyzeBtn.textContent = "Analyze this file";
    }
  }

  // ================================================================ RESULT
  function renderResult(d) {
    const body = $("#resultBody");
    body.innerHTML = "";

    // KPI row
    const kpis = el("div", "kpi-row");
    kpis.appendChild(kpi("Matrices in chain", d.n, null));
    kpis.appendChild(kpi("Optimal cost", fmt(d.best.cost) + " mults", null, true));
    kpis.appendChild(kpi("Saved vs. worst order", pct(d.savedVsWorst), null, true));
    kpis.appendChild(kpi("DP solve time", fmt(d.solveMicros) + " &micro;s", null));
    body.appendChild(kpis);
    if (d.warnings && d.warnings.length) {
      const w = el("div", "warn-box", d.warnings.map(escapeHtml).join("<br>"));
      body.appendChild(w);
    }

    // matrices
    body.appendChild(el("div", "section-title", `Matrices read from the file <span class="n">(${d.n})</span>`));
    const chips = el("div", "matrix-chip-row");
    d.matrices.slice(0, 60).forEach((m) => {
      chips.appendChild(el("span", "matrix-chip", `<b>${m.name}</b> &nbsp;${fmt(m.rows)} &times; ${fmt(m.cols)}`));
    });
    if (d.matrices.length > 60) chips.appendChild(el("span", "matrix-chip", `+ ${d.matrices.length - 60} more`));
    body.appendChild(chips);

    // best order
    body.appendChild(el("div", "section-title", "Optimal multiplication order"));
    const orderBox = el("div", "order-box", d.best.order ? escapeHtml(d.best.order) : "(chain too long to print as one line - see the step list below)");
    body.appendChild(orderBox);

    // comparison table
    body.appendChild(el("div", "section-title", "Cost comparison"));
    const table = el("table", "compare-table");
    table.innerHTML = `
      <tr><th>Strategy</th><th class="num">Cost (scalar mults)</th><th>Saved</th><th>Order</th></tr>
      <tr class="best-row"><td>Optimal order (Dynamic Programming)</td><td class="num">${fmt(d.best.cost)}</td><td>&mdash;</td>
          <td class="order-cell">${d.best.order ? escapeHtml(truncate(d.best.order, 90)) : "(too long)"}</td></tr>
      <tr><td>Plain left-to-right order</td><td class="num">${fmt(d.ltr.cost)}</td><td>${pct(d.savedVsLtr)}</td>
          <td class="order-cell">${d.ltr.order ? escapeHtml(truncate(d.ltr.order, 90)) : "(too long)"}</td></tr>
      <tr><td>Worst possible order</td><td class="num">${fmt(d.worst.cost)}</td><td>${pct(d.savedVsWorst)}</td>
          <td class="order-cell">${d.worst.order ? escapeHtml(truncate(d.worst.order, 90)) : "(too long)"}</td></tr>
    `;
    body.appendChild(table);

    // step list
    body.appendChild(el("div", "section-title", `Multiplication steps, in execution order <span class="n">(${d.best.steps.length} steps)</span>`));
    const stepList = el("div", "step-list");
    const steps = d.best.steps;
    const showAll = steps.length <= 25;
    const shown = showAll ? steps : steps.slice(0, 10).concat(steps.slice(-5));
    let insertedNote = false;
    shown.forEach((st, idx) => {
      if (!showAll && !insertedNote && idx === 10) {
        stepList.appendChild(el("div", "step-more-note", `&hellip; ${steps.length - 15} more steps not shown &hellip;`));
        insertedNote = true;
      }
      stepList.appendChild(stepCard(st));
    });
    body.appendChild(stepList);

    // execution
    body.appendChild(el("div", "section-title", "Real execution"));
    body.appendChild(executionBlock(d));

    // actions
    const actions = el("div", "actions-row");
    const learnBtn = el("button", "btn-primary", "See the full derivation");
    learnBtn.addEventListener("click", () => $("#learn").scrollIntoView({ behavior: "smooth", block: "start" }));
    const newBtn = el("button", "btn-secondary", "Analyze another file");
    newBtn.addEventListener("click", () => $("#upload").scrollIntoView({ behavior: "smooth", block: "start" }));
    actions.appendChild(learnBtn);
    actions.appendChild(newBtn);
    body.appendChild(actions);
  }

  function kpi(label, value, sub, good) {
    const k = el("div", "kpi" + (good ? " kpi-good" : ""));
    k.appendChild(el("div", "kpi-label", label));
    k.appendChild(el("div", "kpi-value", value));
    if (sub) k.appendChild(el("div", "kpi-sub", sub));
    return k;
  }

  function truncate(s, n) { return s.length <= n ? s : s.slice(0, n - 3) + "..."; }

  function stepCard(st) {
    const card = el("div", "step-card");
    const head = el("div", "step-head");
    head.appendChild(el("span", "step-badge", st.idx));
    head.appendChild(el("span", null, `${st.left} <b>(${fmt(st.r)}&times;${fmt(st.m)})</b> &nbsp;\u00d7&nbsp; ${st.right} <b>(${fmt(st.m)}&times;${fmt(st.c)})</b> &nbsp;=&nbsp; ${st.res} <b>(${fmt(st.r)}&times;${fmt(st.c)})</b>`));
    head.appendChild(el("span", "step-cost", `${fmt(st.r)}\u00d7${fmt(st.m)}\u00d7${fmt(st.c)} = ${fmt(st.cost)} mults`));
    card.appendChild(head);

    if (st.values && st.values.example) {
      const ex = el("div", "step-example");
      const e1 = st.values.example;
      ex.appendChild(el("div", null, `Example: element (row ${e1.row}, col ${e1.col}) of ${st.res} = ` +
        e1.terms.map((t) => `${t[0]}&times;${t[1]}`).join(" + ") +
        (e1.totalTerms > e1.terms.length ? ` + &hellip; (${e1.totalTerms} terms total)` : "") +
        ` = ${fmt(e1.value)}`));
      card.appendChild(ex);
    }
    return card;
  }

  function miniMatrix(m, label) {
    const wrap = el("div");
    if (label) wrap.appendChild(el("div", "mini-matrix-wrap", label + (m.rows > m.data.length || m.cols > (m.data[0] || []).length ? ` &nbsp;(showing top-left ${m.data.length}\u00d7${(m.data[0]||[]).length} of ${m.rows}\u00d7${m.cols})` : ` &nbsp;(${m.rows}\u00d7${m.cols})`)));
    const t = el("table", "mini-matrix");
    m.data.forEach((row) => {
      const tr = el("tr");
      row.forEach((v) => tr.appendChild(el("td", null, fmt(v))));
      t.appendChild(tr);
    });
    wrap.appendChild(t);
    return wrap;
  }

  function executionBlock(d) {
    const wrap = el("div");
    const exe = d.execution;
    if (!exe.performed) {
      wrap.appendChild(el("div", "limit-note", escapeHtml(exe.reason)));
      return wrap;
    }
    const note = el("div", "limit-note",
      (exe.generated
        ? "The uploaded file only gave matrix sizes, so the matrices were filled with random numbers (1-9, fixed seed) to demonstrate the multiplication. "
        : "These are the exact numbers from your uploaded file. ") +
      (exe.modulus ? `Arithmetic is done modulo ${fmt(exe.modulus)} to keep the displayed numbers manageable. ` : "") +
      `Verification: all executed orders produced the <b style="color:var(--good)">${exe.verified ? "identical" : "DIFFERENT"}</b> final matrix.`);
    wrap.appendChild(note);

    const timing = el("table", "compare-table");
    timing.innerHTML = `<tr><th>Order</th><th class="num">Time taken</th></tr>
      <tr class="best-row"><td>Optimal (DP)</td><td class="num">${exe.timings.opt.toFixed(3)} ms</td></tr>
      <tr><td>Left-to-right</td><td class="num">${exe.timings.ltr === null ? "skipped (too heavy)" : exe.timings.ltr.toFixed(3) + " ms"}</td></tr>
      <tr><td>Worst order</td><td class="num">${exe.timings.worst === null ? "skipped (too heavy)" : exe.timings.worst.toFixed(3) + " ms"}</td></tr>`;
    wrap.appendChild(timing);

    if (exe.inputs) {
      wrap.appendChild(el("div", "section-title", "Input matrices used"));
      const row = el("div", "matrix-chip-row");
      exe.inputs.forEach((m) => {
        const box = el("div");
        box.appendChild(miniMatrix(m.values, m.name));
        row.appendChild(box);
      });
      wrap.appendChild(row);
    }

    wrap.appendChild(el("div", "section-title", "Final result matrix"));
    wrap.appendChild(miniMatrix(exe.finalResult, "Result"));
    return wrap;
  }

  // ================================================================ LEARN MORE
  function renderLearn(d) {
    const body = $("#learnBody");
    body.innerHTML = "";

    // 1. concept
    const b1 = el("div", "learn-block");
    b1.appendChild(el("h3", null, "1. Why order matters"));
    b1.appendChild(el("p", null,
      "Multiplying matrices is associative: (A&times;B)&times;C always equals A&times;(B&times;C). " +
      "But the two groupings can need very different amounts of work. An (a&times;b) matrix times a (b&times;c) " +
      "matrix costs a&times;b&times;c scalar multiplications and produces an (a&times;c) result. Choosing which " +
      "pair to multiply first changes the sizes of every matrix that follows, so the total cost changes too."));
    body.appendChild(b1);

    // 2. recurrence
    const b2 = el("div", "learn-block");
    b2.appendChild(el("h3", null, "2. The recurrence Dynamic Programming solves"));
    b2.appendChild(el("p", null,
      "Let m[i][j] be the cheapest cost of multiplying the matrices A\u1d62 through A\u2c7c together, as one group. " +
      "For a single matrix that cost is 0. For a longer chain, try every possible place k to split it into two " +
      "groups (A\u1d62..A\u2096) and (A\u2096\u208a\u2081..A\u2c7c), and keep whichever split is cheapest:"));
    b2.appendChild(el("div", "formula",
      "m[i][j] = min over i &le; k &lt; j of:\n" +
      "    m[i][k]  +  m[k+1][j]  +  p[i-1] \u00d7 p[k] \u00d7 p[j]\n\n" +
      "m[i][k]      = cost of the best order for the left group\n" +
      "m[k+1][j]    = cost of the best order for the right group\n" +
      "p[i-1]\u00d7p[k]\u00d7p[j] = cost of the one multiplication that joins the two groups"));
    b2.appendChild(el("p", null,
      "This is solved bottom-up: first all chains of length 2, then length 3, and so on, so that by the time " +
      "a longer chain is being solved, every shorter chain it depends on is already known. That is what makes " +
      "it O(n\u00b3) instead of exponential - each of the O(n\u00b2) cells does an O(n) scan over split points."));
    body.appendChild(b2);

    // 3. cell-by-cell trace
    const b3 = el("div", "learn-block");
    b3.appendChild(el("h3", null, "3. Filling the table for your chain, cell by cell"));
    if (d.trace) {
      b3.appendChild(el("p", null,
        "Each block below is one diagonal of the table (chains of the same length). Every cell lists every " +
        "split point k that was tried, and marks the one that was kept."));
      d.trace.forEach((group) => {
        b3.appendChild(el("div", "len-group-label", `Chains of length ${group.len} (${group.cells.length} cell${group.cells.length > 1 ? "s" : ""})`));
        group.cells.forEach((cell) => {
          const box = el("div", "cell-trace");
          box.appendChild(el("div", "ct-head", `m[${cell.i}][${cell.j}]  &mdash;  best cost for A${cell.i}..A${cell.j} = <b>${fmt(cell.best)}</b>`));
          cell.cands.forEach((c) => {
            const row = el("div", "ct-cand" + (c.k === cell.k ? " chosen" : ""));
            row.appendChild(el("span", "ct-k", `k = ${c.k}`));
            row.appendChild(el("span", "ct-formula",
              `m[${cell.i}][${c.k}] + m[${c.k + 1}][${cell.j}] + ${c.factors[0]}\u00d7${c.factors[1]}\u00d7${c.factors[2]}` +
              `  =  ${fmt(c.left)} + ${fmt(c.right)} + ${fmt(c.mult)}  =  ${fmt(c.total)}`));
            row.appendChild(el("span", "ct-check", c.k === cell.k ? "\u2713" : ""));
            box.appendChild(row);
          });
          b3.appendChild(box);
        });
      });
    } else {
      b3.appendChild(el("div", "limit-note",
        `This chain has ${d.n} matrices. A full cell-by-cell trace is only generated for chains of ` +
        `20 or fewer matrices, since it would otherwise be thousands of lines. The DP table (if available) ` +
        `and the final order above are still exact.`));
    }
    body.appendChild(b3);

    // 4. full table
    const b4 = el("div", "learn-block");
    b4.appendChild(el("h3", null, "4. The complete cost table"));
    if (d.table) {
      b4.appendChild(el("p", null, "Hover a cell to see which split point k produced that value. The diagonal (i = j) is always 0: a single matrix needs no multiplication."));
      b4.appendChild(dpTable(d));
    } else {
      b4.appendChild(el("div", "limit-note", `This chain has ${d.n} matrices, so the full ${d.n}\u00d7${d.n} table is not sent to the browser. The step-by-step result above was still computed with the same table internally.`));
    }
    body.appendChild(b4);

    // 5. tree
    const b5 = el("div", "learn-block");
    b5.appendChild(el("h3", null, "5. Reading the answer back out: the multiplication tree"));
    b5.appendChild(el("p", null,
      "Once every m[i][j] is known, the best order is read back out starting from m[1][n]: split at its chosen " +
      "k, then recursively do the same for the left and right groups until only single matrices are left. " +
      "That recursion is exactly this tree - it is also exactly the order of the steps listed in the result above."));
    if (d.best.tree) {
      const tw = el("div", "tree-wrap");
      tw.appendChild(buildTree(d.best.tree));
      b5.appendChild(tw);
    } else {
      b5.appendChild(el("div", "limit-note", `This chain has ${d.n} matrices, so the visual tree is skipped (it would be too wide to read). The step list in the result above lists the same recursion as text.`));
    }
    body.appendChild(b5);

    // 6. complexity
    const b6 = el("div", "learn-block");
    b6.appendChild(el("h3", null, "6. Why this is efficient"));
    b6.appendChild(el("p", null,
      `This chain has n = ${d.n} matrices. Trying every possible grouping directly would mean checking ` +
      `Catalan(n-1) different orders, which grows almost exponentially and becomes impossible past ~20 matrices. ` +
      `Dynamic Programming instead fills n(n+1)/2 = ${(d.n * (d.n + 1) / 2).toLocaleString()} table cells, ` +
      `each taking at most n split points to check, giving O(n\u00b3) time and O(n\u00b2) space - which is why ` +
      `this tool can solve a 500-matrix chain in about a tenth of a second.`));
    body.appendChild(b6);
  }

  function dpTable(d) {
    const wrap = el("div", "dp-table-wrap");
    const t = el("table", "dp-table");
    const n = d.n;
    const head = el("tr");
    head.appendChild(el("th", null, "i \\ j"));
    for (let j = 1; j <= n; j++) head.appendChild(el("th", null, "A" + j));
    t.appendChild(head);
    for (let i = 1; i <= n; i++) {
      const tr = el("tr");
      tr.appendChild(el("th", null, "A" + i));
      for (let j = 1; j <= n; j++) {
        if (j < i) { tr.appendChild(el("td", "diag", "")); continue; }
        const val = d.table.m[i - 1][j - 1];
        const k = d.table.s[i - 1][j - 1];
        const td = el("td", i === j ? "diag" : "chosen-cell", fmt(val));
        if (i !== j) td.title = `split at k = ${k}  (between A${k} and A${k + 1})`;
        tr.appendChild(td);
      }
      t.appendChild(tr);
    }
    wrap.appendChild(t);
    return wrap;
  }

  function buildTree(node) {
    const box = el("div", "tree-node");
    box.style.display = "inline-flex";
    box.style.flexDirection = "column";
    box.style.alignItems = "center";
    box.style.padding = "0 10px";
    const label = el("div");
    label.style.padding = "8px 14px";
    label.style.border = "1px solid var(--border)";
    label.style.borderRadius = "6px";
    label.style.background = node.leaf ? "var(--panel)" : "var(--panel-2)";
    label.style.fontFamily = "var(--mono)";
    label.style.fontSize = "12px";
    label.style.whiteSpace = "nowrap";
    label.innerHTML = node.leaf
      ? `<b>${node.label}</b> (${fmt(node.rows)}\u00d7${fmt(node.cols)})`
      : `<b>${node.label}</b> = ${escapeHtml(node.expr)}<br><span style="color:var(--text-faint)">${fmt(node.rows)}\u00d7${fmt(node.cols)}, cost ${fmt(node.cost)}</span>`;
    box.appendChild(label);
    if (!node.leaf) {
      const kids = el("div");
      kids.style.display = "flex";
      kids.style.marginTop = "14px";
      kids.style.position = "relative";
      kids.style.gap = "6px";
      kids.appendChild(buildTree(node.left));
      kids.appendChild(buildTree(node.right));
      box.appendChild(kids);
    }
    return box;
  }

  loadSamples();
})();
