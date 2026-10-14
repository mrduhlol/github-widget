// A browser port of the app's widget drawing code, so the site shows exactly
// what lands on the home screen. Mirrors:
//   design/Palettes.kt, widget/GraphPainter.kt, widget/WidgetRenderer.kt, data/Stats.kt
// Sizes are in dp (CSS px here); `scale` is the canvas pixel ratio.

// ------------------------------------------------------------------ colors

const hex = (h) => {
  const n = parseInt(h.replace("#", ""), 16);
  return h.length > 7
    ? { a: (n >>> 24) & 255, r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
    : { a: 255, r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
};
const rgba = (c) => `rgba(${c.r},${c.g},${c.b},${(c.a / 255).toFixed(3)})`;
const withAlpha = (c, a) => ({ ...c, a });
const blend = (c1, c2, t) => ({
  a: Math.round(c1.a + (c2.a - c1.a) * t),
  r: Math.round(c1.r + (c2.r - c1.r) * t),
  g: Math.round(c1.g + (c2.g - c1.g) * t),
  b: Math.round(c1.b + (c2.b - c1.b) * t),
});

export const PALETTES = [
  { id: "github", name: "GitHub", accent: "#39D353",
    dark: ["#0E4429", "#006D32", "#26A641", "#39D353"],
    light: ["#9BE9A8", "#40C463", "#30A14E", "#216E39"] },
  { id: "ocean", name: "Ocean", accent: "#388BFD" },
  { id: "violet", name: "Violet", accent: "#A371F7" },
  { id: "rose", name: "Rose", accent: "#F778BA" },
  { id: "sunset", name: "Sunset", accent: "#FB8F44" },
  { id: "gold", name: "Gold", accent: "#E3B341" },
  { id: "teal", name: "Teal", accent: "#2DD4BF" },
  { id: "crimson", name: "Crimson", accent: "#F85149" },
  { id: "mono", name: "Mono", accent: "#9AA4B2" },
];

export const FONTS = [
  { id: "SYSTEM", label: "System", family: "Roboto, 'Segoe UI', system-ui, sans-serif" },
  { id: "INTER", label: "Inter", family: "'GHW Inter'" },
  { id: "SPACE_GROTESK", label: "Space Grotesk", family: "'GHW Space Grotesk'" },
  { id: "MANROPE", label: "Manrope", family: "'GHW Manrope'" },
  { id: "OUTFIT", label: "Outfit", family: "'GHW Outfit'" },
  { id: "FIGTREE", label: "Figtree", family: "'GHW Figtree'" },
  { id: "PLUS_JAKARTA", label: "Plus Jakarta", family: "'GHW Plus Jakarta'" },
];

export const CELL_SHAPES = { SQUARE: 0.12, ROUNDED: 0.3, CIRCLE: 0.5 };
export const CORNERS = { SUBTLE: 10, ROUND: 22, EXTRA: 32 };
export const RANGES = { AUTO: null, MONTH_3: 13, MONTH_6: 26, YEAR: 53 };

export const DEFAULT_DESIGN = {
  paletteId: "github",
  customColor: "#39D353",
  layout: "CLASSIC",
  background: "AUTO",
  opacity: 100,
  corners: "ROUND",
  range: "AUTO",
  cellShape: "ROUNDED",
  showName: true,
  showAvatar: true,
  showTotal: true,
  showStreak: true,
  showToday: true,
  showMonths: true,
  weekStartsMonday: false,
  font: "SYSTEM",
};

function generateRamp(accent, base, dark) {
  const solid = withAlpha(base, 255);
  const steps = dark ? [0.3, 0.52, 0.76, 1] : [0.35, 0.58, 0.8, 1];
  return steps.map((t, i) => {
    const top = !dark && i === 3 ? blend(accent, hex("#000000"), 0.18) : accent;
    return blend(solid, top, t);
  });
}

export function colors(design, dark) {
  const card = design.background === "BLACK" ? hex("#000000") : dark ? hex("#0D1117") : hex("#FFFFFF");
  const see = design.opacity < 45;
  const empty = see && dark ? hex("#33FFFFFF")
    : see ? hex("#26000000")
    : design.background === "BLACK" ? hex("#161B22")
    : dark ? hex("#151B23") : hex("#EFF2F5");
  const p = design.paletteId === "custom"
    ? { id: "custom", accent: design.customColor }
    : PALETTES.find((x) => x.id === design.paletteId) || PALETTES[0];
  const accent = hex(p.accent);
  const hand = dark ? p.dark : p.light;
  const ramp = hand && !see ? hand.map(hex) : generateRamp(accent, see ? card : empty, dark);
  return {
    card,
    border: dark ? hex("#1FFFFFFF") : hex("#1A1F2328"),
    textPrimary: dark ? hex("#F0F6FC") : hex("#1F2328"),
    textSecondary: dark ? hex("#9198A1") : hex("#59636E"),
    accent: p.id === "github" && !dark ? hex("#1A7F37") : accent,
    levels: [empty, ...ramp],
    isDark: dark,
  };
}

// ------------------------------------------------------------------ data

const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const parse = (s) => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); };
const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

/** Same shape as data/GitHubApi.fetchContributions: never shows days that haven't happened locally. */
export function parseContributions(json, username) {
  const today = iso(new Date());
  const days = (json.contributions || [])
    .filter((o) => o.date <= today)
    .map((o) => ({ date: o.date, count: o.count | 0, level: Math.min(4, Math.max(0, o.level | 0)) }))
    .sort((a, b) => (a.date < b.date ? -1 : 1));
  return { username, total: json.total?.lastYear ?? 0, days };
}

export function stats(c) {
  const days = c.days;
  const today = days.length ? parse(days[days.length - 1].date) : new Date();
  let longest = 0, run = 0, active = 0, best = null;
  const weekdays = [0, 0, 0, 0, 0, 0, 0]; // Monday first
  const months = new Map();
  for (const d of days) {
    if (d.count > 0) { run++; active++; if (!best || d.count > best.count) best = d; } else run = 0;
    longest = Math.max(longest, run);
    const dt = parse(d.date);
    weekdays[(dt.getDay() + 6) % 7] += d.count;
    const key = d.date.slice(0, 7);
    months.set(key, (months.get(key) || 0) + d.count);
  }
  // A quiet "today" doesn't break yesterday's streak.
  let current = 0, i = days.length - 1;
  if (i >= 0 && days[i].count === 0) i--;
  while (i >= 0 && days[i].count > 0) { current++; i--; }

  const last30Start = iso(addDays(today, -29));
  const prev30Start = iso(addDays(today, -59));
  const monthList = [];
  for (let b = 11; b >= 0; b--) {
    const m = new Date(today.getFullYear(), today.getMonth() - b, 1);
    const key = iso(m).slice(0, 7);
    monthList.push({ month: m, total: months.get(key) || 0 });
  }
  const weekStart = iso(addDays(today, -((today.getDay() + 6) % 7)));
  return {
    total: c.total,
    today: days.length && days[days.length - 1].date === iso(today) ? days[days.length - 1].count : 0,
    thisWeek: days.filter((d) => d.date >= weekStart).reduce((s, d) => s + d.count, 0),
    currentStreak: current,
    longestStreak: longest,
    activeDays: active,
    trackedDays: days.length,
    bestDay: best,
    weekdayTotals: weekdays,
    months: monthList,
    last30: days.filter((d) => d.date >= last30Start).reduce((s, d) => s + d.count, 0),
    previous30: days.length && days[0].date <= prev30Start
      ? days.filter((d) => d.date >= prev30Start && d.date < last30Start).reduce((s, d) => s + d.count, 0)
      : null,
  };
}

export const streakText = (n) => (n === 0 ? "No streak yet" : n === 1 ? "1 day streak" : `${n} day streak`);
export const todayText = (n) => (n === 0 ? "None today" : `${n} today`);
const fmt = new Intl.NumberFormat("en-US");

// ------------------------------------------------------------------ graph

const GAP = 0.22;
const cellForHeight = (h) => h / (7 + 6 * GAP);
const weeksForWidth = (w, cell) => Math.min(53, Math.max(1, Math.floor((w + cell * GAP) / (cell * (1 + GAP)))));
function weekStartOf(date, monday) {
  const offset = monday ? (date.getDay() + 6) % 7 : date.getDay();
  return addDays(date, -offset);
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, Math.min(r, w / 2, h / 2));
  ctx.fill();
}

/** GraphPainter.draw: 7 rows by N weeks, newest on the right. Returns the occupied rect. */
export function drawGraph(ctx, area, days, spec) {
  const labelH = spec.monthFont ? spec.monthLabelHeight : 0;
  const gridH = area.h - labelH;
  let cell = cellForHeight(gridH);
  let weeks;
  if (spec.weeks != null) {
    weeks = spec.weeks;
    cell = Math.min(cell, area.w / (weeks + (weeks - 1) * GAP));
  } else {
    cell = Math.min(cell, spec.maxCell);
    weeks = weeksForWidth(area.w, cell);
    cell = Math.min(cellForHeight(gridH), area.w / (weeks + (weeks - 1) * GAP));
  }
  const gap = cell * GAP;
  const gridW = weeks * cell + (weeks - 1) * gap;
  const totalH = labelH + 7 * cell + 6 * gap;
  const left = area.x + (area.w - gridW) / 2;
  const top = area.y + (area.h - totalH) / 2;
  const gridTop = top + labelH;

  const byDate = new Map(days.map((d) => [d.date, d]));
  const today = days.length ? parse(days[days.length - 1].date) : new Date();
  const todayIso = iso(today);
  const firstWeek = addDays(weekStartOf(today, spec.weekStartsMonday), -7 * (weeks - 1));
  const radius = cell * spec.radiusFraction;

  let lastLabelEnd = -Infinity, prevMonth = -1;
  const cells = [];
  for (let col = 0; col < weeks; col++) {
    const colStart = addDays(firstWeek, col * 7);
    const x = left + col * (cell + gap);
    if (spec.monthFont) {
      const month = colStart.getMonth();
      if (month !== prevMonth) {
        if (prevMonth !== -1 || colStart.getDate() <= 7) {
          const label = colStart.toLocaleString("en-US", { month: "short" });
          ctx.font = spec.monthFont;
          ctx.fillStyle = spec.monthColor;
          const w = ctx.measureText(label).width;
          if (x > lastLabelEnd + cell && x + w <= left + gridW + cell) {
            ctx.fillText(label, x, top + labelH * 0.66);
            lastLabelEnd = x + w;
          }
        }
        prevMonth = month;
      }
    }
    for (let row = 0; row < 7; row++) {
      const date = iso(addDays(colStart, row));
      if (date > todayIso) break;
      const day = byDate.get(date);
      const level = day ? day.level : 0;
      ctx.fillStyle = rgba(spec.levels[level]);
      const y = gridTop + row * (cell + gap);
      roundRect(ctx, x, y, cell, cell, radius);
      cells.push({ x, y, size: cell, date, count: day ? day.count : 0 });
    }
  }
  return { x: left, y: top, w: gridW, h: totalH, cells };
}

// ------------------------------------------------------------------ widget

/**
 * Draws the whole widget into `canvas` at widthDp x heightDp.
 * user = { contributions, profile: { login, name }, avatar: HTMLImageElement|null }
 */
export function renderWidget(canvas, design, user, widthDp, heightDp, systemDark) {
  const scale = Math.min(3, window.devicePixelRatio || 1);
  canvas.width = Math.round(widthDp * scale);
  canvas.height = Math.round(heightDp * scale);
  canvas.style.width = `${widthDp}px`;
  canvas.style.height = `${heightDp}px`;
  const ctx = canvas.getContext("2d");
  ctx.setTransform(scale, 0, 0, scale, 0, 0);
  ctx.clearRect(0, 0, widthDp, heightDp);

  const isDark = design.background === "AUTO" ? systemDark : design.background !== "LIGHT";
  const c = colors(design, isDark);
  const family = (FONTS.find((f) => f.id === design.font) || FONTS[0]).family;
  const seeThrough = design.opacity < 45;
  const font = (size, weight = 400) => `${weight} ${size}px ${family}`;
  const text = (str, x, y, size, color, weight = 400, align = "left") => {
    ctx.font = font(size, weight);
    ctx.fillStyle = rgba(color);
    ctx.textAlign = align;
    if (seeThrough) {
      ctx.shadowColor = isDark ? "rgba(0,0,0,.6)" : "rgba(255,255,255,.4)";
      ctx.shadowBlur = 3; ctx.shadowOffsetY = 1;
    }
    ctx.fillText(str, x, y);
    ctx.shadowColor = "transparent"; ctx.shadowBlur = 0; ctx.shadowOffsetY = 0;
    ctx.textAlign = "left";
  };
  const measure = (str, size, weight = 400) => { ctx.font = font(size, weight); return ctx.measureText(str).width; };
  const ellipsize = (str, size, weight, width) => {
    if (measure(str, size, weight) <= width) return str;
    for (let end = str.length; end > 0; end--) {
      const cand = str.slice(0, end).trimEnd() + "…";
      if (measure(cand, size, weight) <= width) return cand;
    }
    return "";
  };

  // card
  const r = CORNERS[design.corners];
  if (design.opacity > 0) {
    ctx.fillStyle = rgba(withAlpha(c.card, Math.round((design.opacity * 255) / 100)));
    roundRect(ctx, 0, 0, widthDp, heightDp, r);
  }
  if (design.opacity >= 30) {
    ctx.strokeStyle = rgba(c.border);
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.roundRect(0.5, 0.5, widthDp - 1, heightDp - 1, r);
    ctx.stroke();
  }

  const pad = widthDp < 180 || heightDp < 100 ? 12 : 16;
  const area = { x: pad, y: pad, w: widthDp - 2 * pad, h: heightDp - 2 * pad };
  const data = user.contributions;
  const st = stats(data);
  let gridRect = null;

  const graph = (a, months, maxCell = 13) => drawGraph(ctx, a, data.days, {
    levels: c.levels,
    radiusFraction: CELL_SHAPES[design.cellShape],
    weekStartsMonday: design.weekStartsMonday,
    weeks: RANGES[design.range],
    maxCell,
    monthFont: months ? font(9.5, 500) : null,
    monthColor: rgba(c.textSecondary),
    monthLabelHeight: months ? 15 : 0,
  });
  const roomForMonths = (h) => design.showMonths && h >= 72;

  const drawAvatar = (x, y, size) => {
    ctx.save();
    ctx.beginPath();
    ctx.arc(x + size / 2, y + size / 2, size / 2, 0, Math.PI * 2);
    ctx.clip();
    if (user.avatar) {
      ctx.drawImage(user.avatar, x, y, size, size);
    } else {
      ctx.fillStyle = rgba(c.levels[2]);
      ctx.fillRect(x, y, size, size);
      text((user.profile?.name || data.username).slice(0, 1).toUpperCase(), x + size / 2, y + size / 2 + 12 * 0.36, 12, c.textPrimary, 700, "center");
    }
    ctx.restore();
  };

  const drawHeader = (hr) => {
    let left = hr.x;
    const cy = hr.y + hr.h / 2;
    if (design.showAvatar) { drawAvatar(left, cy - 12, 24); left += 32; }
    let right = hr.x + hr.w;
    if (design.showTotal) {
      const numStr = fmt.format(st.total);
      const labelStr = hr.w > 220 ? " contributions" : "";
      const labelW = measure(labelStr, 11);
      const numW = measure(numStr, 16, 700);
      const base = cy + 16 * 0.36;
      text(labelStr, right - labelW, base, 11, c.textSecondary);
      text(numStr, right - labelW - numW, base, 16, c.accent, 700);
      right -= labelW + numW + 10;
    }
    if (design.showName) {
      const name = user.profile?.name || data.username;
      const avail = right - left;
      if (avail > 24) text(ellipsize(name, 14, 500, avail), left, cy + 14 * 0.36, 14, c.textPrimary, 500);
    }
  };

  const drawFooter = (fr) => {
    const parts = [];
    if (design.showStreak) parts.push(streakText(st.currentStreak));
    if (design.showToday) parts.push(todayText(st.today));
    const cy = fr.y + fr.h / 2;
    const base = cy + 11 * 0.36;
    let x = fr.x;
    parts.forEach((s, i) => {
      const active = i === 0 && design.showStreak ? st.currentStreak > 0 : st.today > 0;
      ctx.fillStyle = rgba(active ? c.accent : c.levels[0]);
      ctx.beginPath(); ctx.arc(x + 3.5, cy, 3.5, 0, Math.PI * 2); ctx.fill();
      x += 12;
      const shown = ellipsize(s, 11, 400, fr.x + fr.w - x);
      text(shown, x, base, 11, c.textSecondary);
      x += measure(shown, 11) + 14;
    });
    const legendW = 5 * 10 + 4 * 3;
    const lessW = measure("Less ", 10), moreW = measure(" More", 10);
    const total = lessW + legendW + moreW;
    if (fr.x + fr.w - x > total + 8) {
      let lx = fr.x + fr.w - total;
      text("Less", lx, base, 10, c.textSecondary);
      lx += lessW;
      for (let lvl = 0; lvl <= 4; lvl++) {
        ctx.fillStyle = rgba(c.levels[lvl]);
        roundRect(ctx, lx, cy - 5, 10, 10, 10 * CELL_SHAPES[design.cellShape]);
        lx += 13;
      }
      text("More", lx + 1, base, 10, c.textSecondary);
    }
  };

  const drawBigStats = (br, compact) => {
    const bigSize = compact ? Math.min(30, Math.max(18, br.h * 0.34)) : 30;
    const showTotal = design.showTotal || (!design.showStreak && !design.showToday);
    const lines = [];
    if (design.showStreak) lines.push(streakText(st.currentStreak));
    if (design.showToday) lines.push(todayText(st.today));
    const bigH = showTotal ? bigSize + 2 + 11 : 0;
    const lineH = 13 * 1.45;
    const lineCount = compact ? Math.min(lines.length, 1) : lines.length;
    const block = bigH + (showTotal && lines.length ? 10 : 0) + lineCount * lineH;
    let y = br.y + Math.max(0, (br.h - block) / 2);
    if (showTotal) {
      y += bigSize * 0.9;
      text(fmt.format(st.total), br.x, y, bigSize, c.accent, 700);
      y += 2 + 11;
      let ls = 11;
      const label = "contributions this year";
      while (measure(label, ls) > br.w && ls > 8) ls -= 0.5;
      text(label, br.x, y, ls, c.textSecondary);
      y += 10;
    }
    if (compact && lines.length) {
      const joined = lines.join("  ·  ");
      const t = measure(joined, 13, 500) <= br.w ? joined : ellipsize(lines[0], 13, 500, br.w);
      y += 13;
      text(t, br.x, y, 13, c.textPrimary, 500);
    } else {
      lines.forEach((l) => { y += lineH; text(ellipsize(l, 13, 500, br.w), br.x, y - lineH * 0.3, 13, c.textPrimary, 500); });
    }
  };

  if (design.layout === "GRAPH") {
    gridRect = graph(area, roomForMonths(area.h), 16);
  } else if (design.layout === "NUMBERS") {
    const wide = area.w > area.h * 1.55;
    if (wide) {
      const colW = Math.min(170, Math.max(96, area.w * 0.36));
      drawBigStats({ x: area.x, y: area.y, w: colW, h: area.h }, false);
      const g = { x: area.x + colW + 12, y: area.y, w: area.w - colW - 12, h: area.h };
      gridRect = graph(g, roomForMonths(g.h) && g.h > 100);
    } else {
      const statsH = Math.min(92, area.h * 0.48);
      drawBigStats({ x: area.x, y: area.y, w: area.w, h: statsH }, true);
      const g = { x: area.x, y: area.y + statsH + 8, w: area.w, h: area.h - statsH - 8 };
      if (g.h > 24) gridRect = graph(g, false);
    }
  } else {
    const wantHeader = design.showName || design.showAvatar || design.showTotal;
    const wantFooter = design.showStreak || design.showToday;
    const headerH = 26, footerH = 16, gap = 10, minGrid = 46;
    let header = wantHeader, footer = wantFooter;
    const gridH = () => area.h - (header ? headerH + gap : 0) - (footer ? footerH + gap * 0.8 : 0);
    if (gridH() < minGrid) footer = false;
    if (gridH() < minGrid) header = false;
    let top = area.y;
    if (header) { drawHeader({ x: area.x, y: top, w: area.w, h: headerH }); top += headerH + gap; }
    const bottom = footer ? area.y + area.h - footerH - gap * 0.8 : area.y + area.h;
    const ga = { x: area.x, y: top, w: area.w, h: bottom - top };
    gridRect = graph(ga, roomForMonths(ga.h));
    if (footer) drawFooter({ x: gridRect.x, y: area.y + area.h - footerH, w: gridRect.w, h: footerH });
  }
  return { gridRect, colors: c, stats: st };
}

export { fmt, parse, iso, rgba };

// ------------------------------------------------------------------ share image

/** Port of ui/stats/ShareImage.render: the 1080x1350 "Share your year" picture. */
export function renderShareImage(canvas, design, user) {
  const W = 1080, H = 1350, pad = 72;
  canvas.width = W; canvas.height = H;
  const ctx = canvas.getContext("2d");
  const c = colors({ ...design, background: "DARK", opacity: 100 }, true);
  const st = stats(user.contributions);
  const family = (FONTS.find((f) => f.id === design.font) || FONTS[0]).family;
  const sys = "Roboto, 'Segoe UI', system-ui, sans-serif";
  const WHITE = "#F0F6FC", GRAY = "#9198A1", DIM = "#656D76";
  const put = (s, x, y, size, color, weight = 400, fam = sys, spacing = 0) => {
    ctx.font = `${weight} ${size}px ${fam}`;
    ctx.letterSpacing = `${spacing}px`;
    ctx.fillStyle = color;
    ctx.fillText(s, x, y);
    ctx.letterSpacing = "0px";
  };
  const tile = (x, y, w, h, r) => {
    ctx.fillStyle = "#161B22"; roundRect(ctx, x, y, w, h, r);
    ctx.strokeStyle = "#262C36"; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.roundRect(x, y, w, h, r); ctx.stroke();
  };

  const bg = ctx.createLinearGradient(0, 0, 0, H);
  bg.addColorStop(0, "#0D1117"); bg.addColorStop(1, "#05070A");
  ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
  const glow = ctx.createRadialGradient(W * 0.88, 40, 0, W * 0.88, 40, 760);
  glow.addColorStop(0, rgba(withAlpha(c.accent, 64))); glow.addColorStop(1, "rgba(0,0,0,0)");
  ctx.fillStyle = glow; ctx.fillRect(0, 0, W, H);

  const login = user.profile?.login || user.contributions.username;
  const name = user.profile?.name || login;
  const av = 128, avTop = 96;
  ctx.save(); ctx.beginPath(); ctx.arc(pad + av / 2, avTop + av / 2, av / 2, 0, Math.PI * 2); ctx.clip();
  if (user.avatar) ctx.drawImage(user.avatar, pad, avTop, av, av);
  else { ctx.fillStyle = rgba(withAlpha(c.accent, 60)); ctx.fillRect(pad, avTop, av, av); }
  ctx.restore();
  ctx.strokeStyle = "rgba(255,255,255,.2)"; ctx.lineWidth = 3;
  ctx.beginPath(); ctx.arc(pad + av / 2, avTop + av / 2, av / 2 - 1.5, 0, Math.PI * 2); ctx.stroke();
  put(name, pad + av + 32, avTop + 62, 54, WHITE, 700);
  put(`@${login}`, pad + av + 32, avTop + 110, 34, GRAY);

  put(fmt.format(st.total), pad - 6, 470, 184, WHITE, 800, family, -5.5);
  put(st.total === 1 ? "contribution in the last year" : "contributions in the last year", pad, 584, 40, GRAY);

  tile(pad, 640, W - 2 * pad, 232, 36);
  drawGraph(ctx, { x: pad + 36, y: 668, w: W - 2 * pad - 72, h: 176 }, user.contributions.days, {
    levels: [hex("#21262D"), ...c.levels.slice(1)],
    radiusFraction: CELL_SHAPES[design.cellShape],
    weekStartsMonday: design.weekStartsMonday,
    weeks: 53, maxCell: 40,
    monthFont: `400 24px ${sys}`, monthColor: GRAY, monthLabelHeight: 44,
  });

  const best = st.bestDay ? parse(st.bestDay.date).toLocaleString("en-US", { month: "short", day: "numeric" }) : "";
  const tiles = [
    ["Current streak", fmt.format(st.currentStreak), st.currentStreak === 1 ? " day" : " days"],
    ["Longest streak", fmt.format(st.longestStreak), st.longestStreak === 1 ? " day" : " days"],
    ["Best day", fmt.format(st.bestDay?.count || 0), best ? `  on ${best}` : ""],
    ["Active days", fmt.format(st.activeDays), ` of ${fmt.format(st.trackedDays)}`],
  ];
  const gap = 24, tileW = (W - 2 * pad - gap) / 2, tileH = 164;
  tiles.forEach(([label, value, suffix], i) => {
    const x = pad + (i % 2) * (tileW + gap), y = 916 + Math.floor(i / 2) * (tileH + gap);
    tile(x, y, tileW, tileH, 32);
    put(label, x + 36, y + 60, 30, GRAY, 500);
    put(value, x + 36, y + 134, 72, WHITE, 700, family, -1.4);
    ctx.font = `700 72px ${family}`;
    const vw = ctx.measureText(value).width;
    put(suffix, x + 36 + vw, y + 134, 32, GRAY, 500);
  });

  ctx.font = `500 28px ${sys}`;
  const label = "Made with GH Widgets", mark = 28;
  const total = mark + 14 + ctx.measureText(label).width;
  const fx = (W - total) / 2, fy = 1312, sq = (mark - 4) / 2;
  [c.levels[2], c.levels[4], c.levels[4], c.levels[3]].forEach((col, i) => {
    ctx.fillStyle = rgba(col);
    roundRect(ctx, fx + (i % 2) * (sq + 4), fy - mark + 4 + Math.floor(i / 2) * (sq + 4), sq, sq, 3);
  });
  put(label, fx + mark + 14, fy, 28, DIM, 500);
}
