import {
  DEFAULT_DESIGN, PALETTES, FONTS, renderWidget, drawGraph, colors, parseContributions,
  stats as computeStats, renderShareImage, fmt, parse, rgba,
} from "./widget.js";

const USERNAME = "mrduhlol";
const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const isDark = () => document.documentElement.dataset.theme === "dark";

// ------------------------------------------------------------------ data

async function getJson(url, timeout = 6000) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeout);
  try {
    const res = await fetch(url, { signal: ctrl.signal });
    if (!res.ok) throw new Error(res.status);
    return await res.json();
  } finally {
    clearTimeout(t);
  }
}

function loadImage(src) {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

async function loadUser() {
  // Live first, then the snapshot bundled with the site.
  let live = true;
  const [contrib, profile] = await Promise.all([
    getJson(`https://github-contributions-api.jogruber.de/v4/${USERNAME}?y=last`)
      .catch(() => { live = false; return getJson("assets/data/mrduhlol-contributions.json"); }),
    getJson(`https://api.github.com/users/${USERNAME}`)
      .catch(() => getJson("assets/data/mrduhlol-profile.json")),
  ]);
  // Local avatar keeps canvases exportable (no cross-origin taint).
  const avatar = await loadImage("assets/data/mrduhlol-avatar.png");
  return {
    live,
    contributions: parseContributions(contrib, USERNAME),
    profile: { login: profile.login || USERNAME, name: profile.name || profile.login || USERNAME },
    avatar,
  };
}

// ------------------------------------------------------------------ theme + nav

function setupTheme(onChange) {
  const btn = $("#theme-toggle");
  const label = () => btn.setAttribute("aria-label", isDark() ? "Switch to light mode" : "Switch to dark mode");
  label();
  btn.addEventListener("click", () => {
    const next = isDark() ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    localStorage.setItem("ghw-theme", next);
    label();
    onChange();
  });
}

function setupNav() {
  const btn = $("#menu-btn"), menu = $("#menu");
  const close = () => {
    btn.setAttribute("aria-expanded", "false");
    btn.setAttribute("aria-label", "Open menu");
    menu.classList.remove("open");
    setTimeout(() => { if (!menu.classList.contains("open")) menu.hidden = true; }, 400);
  };
  btn.addEventListener("click", () => {
    if (btn.getAttribute("aria-expanded") === "true") return close();
    menu.hidden = false;
    requestAnimationFrame(() => menu.classList.add("open"));
    btn.setAttribute("aria-expanded", "true");
    btn.setAttribute("aria-label", "Close menu");
  });
  $$("a", menu).forEach((a) => a.addEventListener("click", close));
  addEventListener("keydown", (e) => { if (e.key === "Escape" && !menu.hidden) close(); });

  const links = new Map($$(".nav-links a").map((a) => [a.hash.slice(1), a]));
  const io = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (!e.isIntersecting) return;
      const a = links.get(e.target.id);
      links.forEach((l) => l.removeAttribute("aria-current"));
      if (a) {
        links.forEach((l) => l.removeAttribute("aria-current"));
        a.setAttribute("aria-current", "true");
      }
    });
  }, { rootMargin: "-45% 0px -50% 0px" });
  ["top", "live", ...links.keys()].forEach((id) => { const s = document.getElementById(id); if (s) io.observe(s); });
}

function setupReveal() {
  const els = $$(".reveal");
  if (reduceMotion) return els.forEach((el) => el.classList.add("in"));
  // Stagger siblings that enter together.
  const io = new IntersectionObserver((entries) => {
    entries.filter((e) => e.isIntersecting).forEach((e, i) => {
      e.target.style.setProperty("--d", `${i * 0.07}s`);
      e.target.classList.add("in");
      io.unobserve(e.target);
    });
  }, { rootMargin: "0px 0px -8% 0px", threshold: 0.12 });
  els.forEach((el) => io.observe(el));
}

function onceVisible(el, fn, threshold = 0.3) {
  const io = new IntersectionObserver((entries) => {
    if (entries.some((e) => e.isIntersecting)) { io.disconnect(); fn(); }
  }, { threshold });
  io.observe(el);
}

function countUp(el, to, duration = 1400) {
  if (reduceMotion) { el.textContent = fmt.format(to); return; }
  const start = performance.now();
  const step = (now) => {
    const t = Math.min(1, (now - start) / duration);
    const eased = 1 - Math.pow(1 - t, 4);
    el.textContent = fmt.format(Math.round(to * eased));
    if (t < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

// ------------------------------------------------------------------ backgrounds

/** A huge, faint contribution field built from the real days, shifted across the canvas. */
function drawField(canvas, user, { cell = 22, alpha = 0.55 } = {}) {
  const rect = canvas.getBoundingClientRect();
  if (!rect.width) return;
  const scale = Math.min(2, devicePixelRatio || 1);
  canvas.width = rect.width * scale;
  canvas.height = rect.height * scale;
  const ctx = canvas.getContext("2d");
  ctx.setTransform(scale, 0, 0, scale, 0, 0);
  const c = colors(DEFAULT_DESIGN, isDark());
  const days = user.contributions.days;
  const gap = cell * 0.22, step = cell + gap;
  const cols = Math.ceil(rect.width / step) + 1, rows = Math.ceil(rect.height / step) + 1;
  for (let x = 0; x < cols; x++) {
    for (let y = 0; y < rows; y++) {
      const d = days[(x * 7 + y * 3 + ((y / 7) | 0) * 11) % days.length];
      const lvl = d ? d.level : 0;
      ctx.globalAlpha = lvl ? alpha : alpha * 0.55;
      ctx.fillStyle = rgba(c.levels[lvl]);
      ctx.beginPath();
      ctx.roundRect(x * step, y * step, cell, cell, cell * 0.3);
      ctx.fill();
    }
  }
  ctx.globalAlpha = 1;
}

// ------------------------------------------------------------------ heatmap

function setupHeatmap(user, st) {
  const canvas = $("#heatmap"), tip = $("#heat-tip"), core = $(".heatmap-core");
  let cells = [], progress = reduceMotion ? 1 : 0, animStarted = reduceMotion;

  const draw = () => {
    const width = Math.max(760, canvas.parentElement.clientWidth);
    const labelW = 34, monthH = 22;
    const cell = (width - labelW) / (53 + 52 * 0.22);
    const height = monthH + 7 * cell + 6 * cell * 0.22 + 4;
    const scale = Math.min(3, devicePixelRatio || 1);
    canvas.width = width * scale; canvas.height = height * scale;
    canvas.style.height = `${height}px`;
    const ctx = canvas.getContext("2d");
    ctx.setTransform(scale, 0, 0, scale, 0, 0);
    const c = colors(DEFAULT_DESIGN, isDark());
    const muted = getComputedStyle(document.documentElement).getPropertyValue("--muted");
    // Reveal columns left to right by masking newer weeks.
    const days = user.contributions.days;
    const res = drawGraph(ctx, { x: labelW, y: 0, w: width - labelW, h: height - 4 }, days, {
      levels: c.levels, radiusFraction: 0.3, weekStartsMonday: false, weeks: 53, maxCell: 40,
      monthFont: `500 12px Figtree, sans-serif`, monthColor: muted, monthLabelHeight: monthH,
    });
    cells = res.cells;
    ctx.font = "500 11px Figtree, sans-serif";
    ctx.fillStyle = muted;
    const size = cells[0]?.size || cell;
    const top = res.y + monthH;
    [[1, "Mon"], [3, "Wed"], [5, "Fri"]].forEach(([row, l]) => ctx.fillText(l, 0, top + row * size * 1.22 + size * 0.78));
    if (progress < 1) {
      const cut = res.x + res.w * progress;
      ctx.globalCompositeOperation = "destination-out";
      const fade = ctx.createLinearGradient(cut, 0, cut + 120, 0);
      fade.addColorStop(0, "rgba(0,0,0,0)"); fade.addColorStop(1, "rgba(0,0,0,1)");
      ctx.fillStyle = fade;
      ctx.fillRect(cut, monthH - 2, width, height);
      ctx.globalCompositeOperation = "source-over";
    }
  };

  const animate = () => {
    if (animStarted) return;
    animStarted = true;
    const start = performance.now();
    const step = (now) => {
      progress = Math.min(1, (now - start) / 1600);
      progress = 1 - Math.pow(1 - progress, 3);
      draw();
      if (progress < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  };

  const show = (e) => {
    const r = canvas.getBoundingClientRect();
    const x = e.clientX - r.left, y = e.clientY - r.top;
    const hit = cells.find((c) => x >= c.x - 1 && x <= c.x + c.size + 1 && y >= c.y - 1 && y <= c.y + c.size + 1);
    if (!hit) return tip.classList.remove("on");
    const date = parse(hit.date).toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric", year: "numeric" });
    tip.innerHTML = `<b>${hit.count === 0 ? "No" : fmt.format(hit.count)}</b> contribution${hit.count === 1 ? "" : "s"} on ${date}`;
    const cr = core.getBoundingClientRect();
    tip.style.left = `${r.left - cr.left + hit.x + hit.size / 2}px`;
    tip.style.top = `${r.top - cr.top + hit.y}px`;
    tip.classList.add("on");
  };
  canvas.addEventListener("pointermove", show);
  canvas.addEventListener("pointerleave", () => tip.classList.remove("on"));

  draw();
  // Phones scroll the year sideways; start on the latest weeks.
  const sc = canvas.parentElement;
  sc.scrollLeft = sc.scrollWidth;
  onceVisible(canvas, animate, 0.25);

  // Stat row
  const values = {
    total: st.total, longestStreak: st.longestStreak, currentStreak: st.currentStreak,
    best: st.bestDay?.count || 0, activeDays: st.activeDays,
  };
  if (st.bestDay) $("#best-date").textContent = ` on ${parse(st.bestDay.date).toLocaleDateString("en-US", { month: "short", day: "numeric" })}`;
  $("#tracked").textContent = ` of ${fmt.format(st.trackedDays)}`;
  $$(".stat-row [data-count]").forEach((el) => {
    onceVisible(el, () => countUp(el, values[el.dataset.count]));
  });
  return draw;
}

// ------------------------------------------------------------------ studio

function setupStudio(user) {
  const KEY = "ghw-design";
  let design = { ...DEFAULT_DESIGN, ...JSON.parse(localStorage.getItem(KEY) || "{}") };
  const canvas = $("#studio-canvas"), box = $("#resizable"), stage = $(".stage-inner");
  const CELL_W = 88, CELL_H = 84;
  let size = { w: 352, h: 168 };

  // Swatches
  const sw = $("#swatches");
  PALETTES.forEach((p) => {
    const b = document.createElement("button");
    b.type = "button"; b.className = "swatch"; b.dataset.palette = p.id;
    b.style.setProperty("--c", p.accent);
    b.setAttribute("aria-label", p.name); b.title = p.name;
    sw.append(b);
  });
  const custom = document.createElement("label");
  custom.className = "swatch swatch-custom"; custom.title = "Custom color";
  custom.dataset.palette = "custom";
  custom.innerHTML = `<input type="color" value="${design.customColor}" aria-label="Custom color" />`;
  sw.append(custom);

  // Fonts
  const sel = $("#font");
  FONTS.forEach((f) => sel.append(new Option(f.label, f.id)));

  const ensureFont = async (id) => {
    const f = FONTS.find((x) => x.id === id);
    if (!f || id === "SYSTEM") return;
    await Promise.all([400, 500, 700, 800].map((w) => document.fonts.load(`${w} 16px ${f.family}`))).catch(() => {});
  };

  const syncControls = () => {
    $$(".seg[data-key]").forEach((seg) => {
      $$("button", seg).forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.value === design[seg.dataset.key])));
    });
    $$("[data-palette]", sw).forEach((b) => {
      const on = b.dataset.palette === design.paletteId;
      b.setAttribute("aria-pressed", String(on));
      if (b.dataset.palette === "custom") b.style.setProperty("--c", design.customColor);
    });
    $$("[data-toggle]").forEach((t) => { t.checked = !!design[t.dataset.toggle]; });
    $("#opacity").value = 100 - design.opacity;
    $("#opacity-out").textContent = `${100 - design.opacity}%`;
    sel.value = design.font;
  };

  const render = () => {
    renderWidget(canvas, design, user, size.w, size.h, isDark());
    const cw = Math.max(1, Math.round((size.w + 16) / CELL_W)), ch = Math.max(1, Math.round((size.h + 16) / CELL_H));
    $("#size-readout").textContent = `${cw} x ${ch}  ·  ${Math.round(size.w)} x ${Math.round(size.h)} dp`;
    $$(".presets button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.size === `${cw}x${ch}`)));
  };
  const update = async (patch) => {
    design = { ...design, ...patch };
    localStorage.setItem(KEY, JSON.stringify(design));
    syncControls();
    if (patch.font) await ensureFont(patch.font);
    render();
  };

  $$(".seg[data-key]").forEach((seg) => seg.addEventListener("click", (e) => {
    const b = e.target.closest("button");
    if (b) update({ [seg.dataset.key]: b.dataset.value });
  }));
  sw.addEventListener("click", (e) => {
    const b = e.target.closest("button.swatch");
    if (b) update({ paletteId: b.dataset.palette });
  });
  $("input", custom).addEventListener("input", (e) => update({ paletteId: "custom", customColor: e.target.value }));
  $$("[data-toggle]").forEach((t) => t.addEventListener("change", () => update({ [t.dataset.toggle]: t.checked })));
  $("#opacity").addEventListener("input", (e) => update({ opacity: 100 - Number(e.target.value) }));
  sel.addEventListener("change", () => update({ font: sel.value }));
  $("#reset").addEventListener("click", () => { localStorage.removeItem(KEY); design = { ...DEFAULT_DESIGN }; syncControls(); setSize(352, 168); });

  // Wallpapers
  $$(".wallpapers button").forEach((b) => b.addEventListener("click", () => {
    $$(".wallpapers button").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
    stage.dataset.wall = b.dataset.wall;
  }));

  // Resizing
  const bounds = () => {
    const r = stage.getBoundingClientRect();
    return { maxW: Math.max(140, r.width - 56), maxH: Math.max(90, r.height - 72) };
  };
  const setSize = (w, h) => {
    const { maxW, maxH } = bounds();
    size = { w: Math.round(Math.min(maxW, Math.max(110, w))), h: Math.round(Math.min(maxH, Math.max(70, h))) };
    box.style.setProperty("--w", `${size.w}px`);
    box.style.setProperty("--h", `${size.h}px`);
    render();
  };
  const handle = $("#handle");
  handle.addEventListener("pointerdown", (e) => {
    e.preventDefault();
    handle.setPointerCapture(e.pointerId);
    handle.classList.add("dragging");
    const sx = e.clientX, sy = e.clientY, sw0 = size.w, sh0 = size.h;
    // The box is centered, so the corner moves at half speed; double the delta.
    const move = (ev) => setSize(sw0 + (ev.clientX - sx) * 2, sh0 + (ev.clientY - sy) * 2);
    const up = () => { handle.classList.remove("dragging"); handle.removeEventListener("pointermove", move); };
    handle.addEventListener("pointermove", move);
    handle.addEventListener("pointerup", up, { once: true });
    handle.addEventListener("pointercancel", up, { once: true });
  });
  handle.addEventListener("keydown", (e) => {
    const d = e.shiftKey ? 40 : 10;
    const map = { ArrowRight: [d, 0], ArrowLeft: [-d, 0], ArrowDown: [0, d], ArrowUp: [0, -d] };
    if (map[e.key]) { e.preventDefault(); setSize(size.w + map[e.key][0], size.h + map[e.key][1]); }
  });
  $$(".presets button").forEach((b) => b.addEventListener("click", () => {
    const [cw, ch] = b.dataset.size.split("x").map(Number);
    setSize(cw * CELL_W - 16, ch * CELL_H - 16);
  }));

  syncControls();
  ensureFont(design.font).then(() => setSize(size.w, size.h));
  return () => setSize(size.w, size.h);
}

// ------------------------------------------------------------------ stats tab

function setupStats(user, st) {
  const last = st.last30, prev = st.previous30;
  const change = prev != null && prev > 0 ? Math.round(((last - prev) * 100) / prev) : null;
  const count = (n) => (n === 1 ? "1 contribution" : `${fmt.format(n)} contributions`);
  const sentence =
    last === 0 && (prev == null || prev === 0) ? "No contributions in the last 30 days. The next one will show up here."
    : last === 0 ? `No contributions in the last 30 days, down from ${fmt.format(prev)} the 30 days before.`
    : prev == null ? `${count(last)} in the last 30 days.`
    : prev === 0 ? `${count(last)}, up from none the 30 days before.`
    : Math.abs(change) < 5 ? `${count(last)}, about the same as the 30 days before.`
    : change > 300 ? `${count(last)}, up from ${fmt.format(prev)} the 30 days before.`
    : change > 0 ? `${count(last)}, ${change}% more than the 30 days before.`
    : `${count(last)}, ${Math.abs(change)}% fewer than the 30 days before.`;
  $("#insight").textContent = sentence;
  const big = $(".big-num[data-count='last30']");
  onceVisible(big, () => countUp(big, last));

  const bars = (el, items) => {
    const max = Math.max(1, ...items.map((i) => i.v));
    el.innerHTML = items.map((i) => `
      <div class="bar" title="${i.title}">
        <div class="bar-fill${i.v === max ? " max" : ""}" data-v="${fmt.format(i.v)}" style="height:${Math.max(2, (i.v / max) * 100)}%"></div>
        <span>${i.label}</span>
      </div>`).join("");
    onceVisible(el, () => {
      $$(".bar-fill", el).forEach((b, i) => { b.style.transitionDelay = `${i * 0.04}s`; });
      el.classList.add("shown");
    });
  };
  bars($("#month-bars"), st.months.map((m) => ({
    v: m.total,
    label: m.month.toLocaleString("en-US", { month: "short" }).slice(0, 3),
    title: `${m.month.toLocaleString("en-US", { month: "long", year: "numeric" })}: ${fmt.format(m.total)}`,
  })));
  const names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  const full = ["Mondays", "Tuesdays", "Wednesdays", "Thursdays", "Fridays", "Saturdays", "Sundays"];
  bars($("#week-bars"), st.weekdayTotals.map((v, i) => ({ v, label: names[i], title: `${full[i]}: ${fmt.format(v)}` })));
  if (st.activeDays >= 7) {
    const i = st.weekdayTotals.indexOf(Math.max(...st.weekdayTotals));
    $("#busiest").textContent = `${full[i]} are the busiest. Average ${(st.total / Math.max(1, st.activeDays)).toFixed(1)} per active day.`;
  }

  // Share image
  const share = $("#share-canvas");
  const draw = () => renderShareImage(share, { ...DEFAULT_DESIGN, ...JSON.parse(localStorage.getItem("ghw-design") || "{}") }, user);
  draw();
  $("#share-btn").addEventListener("click", () => {
    draw();
    share.toBlob((blob) => {
      if (!blob) return;
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = `${USERNAME}-github-year.png`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 2000);
    }, "image/png");
  });
  return draw;
}

// ------------------------------------------------------------------ screens + setup

const SHOTS = [
  ["02-username.webp", "Find yourself", "Type a username and confirm the photo."],
  ["04-pin-dialog.webp", "Add to home screen", "One tap. Android asks where it goes."],
  ["05-widget-tab.webp", "Widget tab", "The live preview, drawn by the widget's own code."],
  ["06-widget-options.webp", "Every option", "Style, colors, background, graph and text."],
  ["07-widget-variant.webp", "Another look", "Swap palettes and layouts in a tap."],
  ["08-stats.webp", "Stats", "The full year you can tap through."],
  ["09-stats-more.webp", "Streaks and records", "Longest streak, best day, active days."],
  ["10-stats-insights.webp", "Insights", "Plain-language trends, plus Share your year."],
  ["11-settings.webp", "Settings", "Refresh interval, number font and account."],
  ["12-home.webp", "On the home screen", "Dark mode, refreshed in the background."],
  ["13-home-light.webp", "In light mode", "Auto background follows the system theme."],
];

function setupScreens() {
  const rail = $("#rail");
  rail.innerHTML = SHOTS.map(([file, title, body]) => `
    <figure class="shot">
      <div class="phone"><div class="phone-screen"><img src="assets/screens/${file}" alt="GH Widgets: ${title}" width="1080" height="2400" loading="lazy" /></div></div>
      <figcaption><strong>${title}</strong><span>${body}</span></figcaption>
    </figure>`).join("");
  // Drop any screenshot that isn't there.
  $$("img", rail).forEach((img) => img.addEventListener("error", () => img.closest(".shot").remove()));
  $$("[data-rail]").forEach((b) => b.addEventListener("click", () => {
    rail.scrollBy({ left: Number(b.dataset.rail) * (rail.clientWidth * 0.8), behavior: reduceMotion ? "auto" : "smooth" });
  }));

  const shot = $("#step-shot");
  $$("#steps button").forEach((b) => b.addEventListener("click", () => {
    $$("#steps button").forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
    shot.style.opacity = "0";
    setTimeout(() => { shot.src = `assets/screens/${b.dataset.shot}`; shot.style.opacity = "1"; }, reduceMotion ? 0 : 200);
  }));
}

function syncHeroShot() {
  const img = $(".hero .phone-screen img");
  const want = isDark() ? img.dataset.dark : img.dataset.light;
  if (!img.src.endsWith(want)) {
    const probe = new Image();
    probe.onload = () => { img.src = want; };
    probe.src = want;
  }
}

// ------------------------------------------------------------------ boot

async function main() {
  setupNav();
  setupReveal();
  setupScreens();
  syncHeroShot();

  const user = await loadUser();
  const st = computeStats(user.contributions);
  $("#live-name").textContent = user.profile.name;
  const sync = $("#sync-state");
  sync.textContent = user.live ? "Live from GitHub" : "Snapshot (GitHub unreachable)";
  sync.classList.toggle("live-on", user.live);

  const heroWidgets = () => {
    renderWidget($("[data-widget='hero-numbers']"), { ...DEFAULT_DESIGN, layout: "NUMBERS", corners: "ROUND" }, user, 300, 150, isDark());
    renderWidget($("[data-widget='hero-graph']"), { ...DEFAULT_DESIGN, layout: "GRAPH", paletteId: "github", cellShape: "CIRCLE", range: "MONTH_3" }, user, 220, 120, isDark());
  };
  heroWidgets();
  const fields = () => {
    drawField($("#hero-field"), user, { cell: 26, alpha: isDark() ? 0.5 : 0.6 });
    drawField($("#final-field"), user, { cell: 20, alpha: 0.9 });
  };
  fields();
  const redrawHeatmap = setupHeatmap(user, st);
  const redrawStudio = setupStudio(user);
  const redrawShare = setupStats(user, st);

  setupTheme(() => { heroWidgets(); fields(); redrawHeatmap(); redrawStudio(); syncHeroShot(); });
  addEventListener("storage", redrawShare);
  $("#controls").addEventListener("click", () => setTimeout(redrawShare, 0));
  $("#controls").addEventListener("change", () => setTimeout(redrawShare, 0));

  let raf;
  addEventListener("resize", () => {
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(() => { fields(); redrawHeatmap(); redrawStudio(); });
  });
}

main();
