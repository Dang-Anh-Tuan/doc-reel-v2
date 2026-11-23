/*
 * DocReel — slide.js (dùng chung cho mọi template)
 *  1. Thanh tiến độ scene i/n (từ body[data-index], body[data-total]).
 *  2. Tô màu code bằng highlight.js local, đánh số dòng, highlight dòng trong data-highlight (đếm từ 1).
 *  3. Chờ font → thu nhỏ chữ cho phần tử [data-shrink] → kiểm tràn [data-fit] và .stage
 *     → ghi body[data-problems] (phân cách bởi dấu phẩy) → body[data-ready="1"].
 *
 * Query (chỉ để xem trực tiếp file tĩnh): ?frame=2  ?theme=petrol-sage
 */
(function () {
  "use strict";

  var body = document.body;
  var params = new URLSearchParams(location.search);

  // ---------- frame + theme qua query (xem file tĩnh) ----------
  if (params.get("frame")) body.setAttribute("data-frame", params.get("frame"));
  if (!body.getAttribute("data-frame")) body.setAttribute("data-frame", "1");
  if (params.get("theme")) {
    var link = document.getElementById("theme-css");
    if (link) link.href = link.href.replace(/[^/]+\.css(\?.*)?$/, params.get("theme") + ".css");
  }

  // ---------- 1. Thanh tiến độ ----------
  function buildProgress() {
    var bar = document.querySelector("[data-progress]");
    if (!bar) return;
    var index = parseInt(body.getAttribute("data-index"), 10) || 1;
    var total = parseInt(body.getAttribute("data-total"), 10) || index;
    bar.textContent = "";
    for (var i = 1; i <= total; i++) {
      var seg = document.createElement("i");
      if (i < index) seg.className = "is-done";
      if (i === index) seg.className = "is-current";
      bar.appendChild(seg);
    }
  }

  // ---------- 2. Code ----------
  var LANG_ALIAS = { tsx: "typescript", ts: "typescript", jsx: "javascript", js: "javascript", sh: "bash", yml: "yaml", html: "xml", kt: "kotlin" };

  function escapeHtml(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  /* Tách HTML của highlight.js theo dòng; span mở qua nhiều dòng được đóng/mở lại ở mỗi dòng. */
  function splitHighlightedLines(html) {
    var lines = [];
    var current = "";
    var open = [];
    var re = /(<span[^>]*>)|(<\/span>)|(\n)|([^<\n]+)/g;
    var m;
    while ((m = re.exec(html)) !== null) {
      if (m[1]) { open.push(m[1]); current += m[1]; }
      else if (m[2]) { open.pop(); current += m[2]; }
      else if (m[3]) {
        current += "</span>".repeat(open.length);
        lines.push(current);
        current = open.join("");
      } else current += m[4];
    }
    lines.push(current);
    return lines;
  }

  function renderCode(pre) {
    var codeEl = pre.querySelector("code") || pre;
    var src = codeEl.textContent.replace(/\r\n?/g, "\n").replace(/\n$/, "");
    var lang = (pre.getAttribute("data-lang") || "").toLowerCase();
    lang = LANG_ALIAS[lang] || lang;
    var marked = {};
    (pre.getAttribute("data-highlight") || "").replace(/\d+/g, function (n) { marked[+n] = true; return n; });

    var html;
    try {
      html = (window.hljs && lang && hljs.getLanguage(lang))
        ? hljs.highlight(src, { language: lang, ignoreIllegals: true }).value
        : escapeHtml(src);
    } catch (e) {
      html = escapeHtml(src);
    }
    var lines = splitHighlightedLines(html);
    var out = "";
    for (var i = 0; i < lines.length; i++) {
      var no = i + 1;
      out += '<span class="ln' + (marked[no] ? " is-hl" : "") + '">' +
        '<span class="ln-no">' + no + "</span>" +
        '<span class="ln-code">' + (lines[i] || " ") + "</span></span>";
    }
    codeEl.innerHTML = out;
    pre.setAttribute("data-lines-count", String(lines.length));
  }

  // ---------- 3. Thu nhỏ + kiểm tràn ----------
  function overflows(el) {
    return el.scrollHeight > el.clientHeight + 2 || el.scrollWidth > el.clientWidth + 2;
  }

  function shrinkToFit(el) {
    var min = parseFloat(el.getAttribute("data-shrink")) || 24;
    var size = parseFloat(getComputedStyle(el).fontSize);
    var guard = 200;
    while (overflows(el) && size > min && guard-- > 0) {
      size = Math.max(min, size - 2);
      el.style.fontSize = size + "px";
    }
  }

  function collectProblems() {
    var problems = [];
    document.querySelectorAll("[data-fit]").forEach(function (el) {
      if (el.offsetParent === null && getComputedStyle(el).position !== "fixed") return; // bị ẩn
      if (overflows(el)) problems.push(el.getAttribute("data-fit"));
    });
    // dòng code vượt giới hạn số dòng của template
    document.querySelectorAll("pre[data-max-lines]").forEach(function (pre) {
      var max = parseInt(pre.getAttribute("data-max-lines"), 10);
      var n = parseInt(pre.getAttribute("data-lines-count"), 10);
      var name = pre.getAttribute("data-fit") || "code";
      if (n > max && problems.indexOf(name) < 0) problems.push(name);
    });
    var stage = document.querySelector(".stage");
    if (stage && overflows(stage)) problems.push("stage");
    // bỏ trùng, giữ thứ tự
    return problems.filter(function (p, i) { return problems.indexOf(p) === i; });
  }

  function loadFonts() {
    if (!document.fonts || !document.fonts.load) return Promise.resolve();
    var sample = "Ẫặữ Hook 0Ox";
    return Promise.all([
      document.fonts.load('500 40px "Be Vietnam Pro"', sample),
      document.fonts.load('700 40px "Be Vietnam Pro"', sample),
      document.fonts.load('500 40px "JetBrains Mono"', sample),
      document.fonts.load('650 40px "Fraunces"', sample)
    ]).catch(function () {}).then(function () { return document.fonts.ready; });
  }

  function nextFrame() {
    return new Promise(function (r) { requestAnimationFrame(function () { requestAnimationFrame(r); }); });
  }

  function run() {
    buildProgress();
    document.querySelectorAll("pre[data-code]").forEach(renderCode);
    loadFonts()
      .then(nextFrame)
      .then(function () {
        document.querySelectorAll("[data-shrink]").forEach(shrinkToFit);
        return nextFrame();
      })
      .then(function () {
        body.setAttribute("data-problems", collectProblems().join(","));
        body.setAttribute("data-ready", "1");
      });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", run);
  else run();
})();
