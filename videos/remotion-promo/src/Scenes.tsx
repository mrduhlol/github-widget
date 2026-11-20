import React from 'react';
import {interpolate, spring, useVideoConfig, Easing} from 'remotion';
import {theme} from './theme';
import {Phone} from './Phone';
import {HeadlineCard} from './Overlays';
import {OctocatHero, CatMode} from './Octocat';
import {ContribGraph} from './Graph';

const ease = Easing.out(Easing.cubic);

export const Bg: React.FC<{zoom: number; children?: React.ReactNode}> = ({zoom, children}) => (
  <div
    style={{
      position: 'absolute',
      inset: -60,
      backgroundColor: theme.bg,
      backgroundImage: 'radial-gradient(#21262D 1.5px, transparent 1.5px)',
      backgroundSize: '34px 34px',
      transform: `scale(${zoom})`,
      overflow: 'hidden',
    }}
  >
    <div style={{position: 'absolute', width: 700, height: 700, left: -160, top: -180, background: 'radial-gradient(circle, rgba(57,211,83,0.22), transparent 65%)'}} />
    <div style={{position: 'absolute', width: 560, height: 560, right: -120, bottom: -160, background: 'radial-gradient(circle, rgba(31,111,235,0.20), transparent 65%)'}} />
    {children}
  </div>
);

const useZoom = (f: number, len: number) =>
  interpolate(f, [0, len], [1, 1.09], {easing: ease, extrapolateRight: 'clamp'});

// Quick punch-in zoom around a beat frame (dynamic zoom hit).
const punch = (f: number, at: number, width = 16, amp = 0.045) => {
  const d = Math.abs(f - at);
  if (d > width) return 1;
  return 1 + amp * Math.cos((d / width) * (Math.PI / 2)) ** 2;
};

const pop = (f: number, fps: number, delay = 0) =>
  spring({frame: f - delay, fps, config: {damping: 14, stiffness: 160}});

const slideX = (f: number, fps: number, delay: number, dist = 120) => {
  const p = pop(f, fps, delay);
  return interpolate(p, [0, 1], [dist, 0]);
};

// Tap ripple: a finger-tap hint on the phone at frame `at`.
const Tap: React.FC<{x: number; y: number; f: number; at: number; fps: number}> = ({x, y, f, at, fps}) => {
  const t = f - at;
  if (t < 0 || t > 34) return null;
  const r = interpolate(t, [0, 30], [6, 46], {easing: ease, extrapolateRight: 'clamp'});
  const o = interpolate(t, [0, 30], [0.9, 0], {extrapolateRight: 'clamp'});
  const dot = pop(f, fps, at);
  return (
    <div style={{position: 'absolute', left: x, top: y, pointerEvents: 'none'}}>
      <div style={{position: 'absolute', left: -r, top: -r, width: r * 2, height: r * 2, borderRadius: '50%', border: '3px solid white', opacity: o}} />
      <div style={{position: 'absolute', left: -9, top: -9, width: 18, height: 18, borderRadius: '50%', backgroundColor: 'white', opacity: 0.9, transform: `scale(${dot})`}} />
    </div>
  );
};

// Cat entrance wrapper: spring drop/slide + float shadow handled by hero.
const CatEnter: React.FC<{
  f: number;
  fps: number;
  delay: number;
  fromX: number;
  fromY: number;
  mode: CatMode;
  size: number;
  children?: React.ReactNode;
}> = ({f, fps, delay, fromX, fromY, mode, size, children}) => {
  const p = pop(f, fps, delay);
  const x = interpolate(p, [0, 1], [fromX, 0]);
  const y = interpolate(p, [0, 1], [fromY, 0]);
  const o = interpolate(f, [delay, delay + 14], [0, 1], {extrapolateRight: 'clamp'});
  return (
    <div style={{transform: `translate(${x}px, ${y}px)`, opacity: o}}>
      <OctocatHero size={size} f={f} mode={mode} />
      {children}
    </div>
  );
};

export const SceneHook: React.FC<{f: number; len: number}> = ({f, len}) => {
  const {fps} = useVideoConfig();
  const zoom = useZoom(f, len) * punch(f, 42);
  return (
    <Bg zoom={zoom}>
      <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 70}}>
        <div style={{transform: `translateX(${slideX(f, fps, 5)}px)`, opacity: interpolate(f, [5, 25], [0, 1])}}>
          <HeadlineCard lines={['Your graph.', 'On your', 'home screen.']} tilt={-4} />
          <div style={{display: 'flex', gap: 12, marginTop: 26, marginLeft: 10}}>
            {['FREE', 'NO SIGN-IN', 'ANDROID 8.0+'].map((b, i) => (
              <div key={b} style={{backgroundColor: '#161B22', border: '1px solid #30363D', color: theme.text, fontFamily: theme.font, fontWeight: 800, fontSize: 20, padding: '10px 18px', borderRadius: 999, transform: `translateY(${interpolate(pop(f, fps, 20 + i * 8), [0, 1], [20, 0])}px)`, opacity: pop(f, fps, 20 + i * 8)}}>
                {b}
              </div>
            ))}
          </div>
        </div>
        <div style={{transform: `translateX(${-slideX(f, fps, 12)}px) scale(${interpolate(pop(f, fps, 12), [0, 1], [0.92, 1])})`, opacity: interpolate(f, [12, 32], [0, 1])}}>
          <Phone seed={7} />
        </div>
      </div>
      {/* the cat drops in from above and floats beside the phone */}
      <div style={{position: 'absolute', top: 150, right: 170}}>
        <CatEnter f={f} fps={fps} delay={28} fromX={180} fromY={-420} mode="float" size={150} />
      </div>
    </Bg>
  );
};

export const SceneSetup: React.FC<{f: number; len: number}> = ({f, len}) => {
  const {fps} = useVideoConfig();
  const zoom = useZoom(f, len) * punch(f, 92);
  const typed = Math.max(0, Math.min(8, Math.floor((f - 40) / 6)));
  const checked = f >= 92;
  return (
    <Bg zoom={zoom}>
      <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 60}}>
        <div style={{position: 'relative', transform: `scale(${interpolate(pop(f, fps, 8), [0, 1], [0.9, 1])})`, opacity: interpolate(f, [8, 26], [0, 1])}}>
          <Phone seed={21} title={'mrduhlol'.slice(0, typed) + (typed < 8 ? '▍' : '')} subtitle="just your username · demo" />
          <Tap x={170} y={250} f={f} at={96} fps={fps} />
        </div>
        <div style={{opacity: interpolate(f, [5, 25], [0, 1])}}>
          <HeadlineCard lines={['No sign-in.', 'Just your', 'username.']} tilt={3} width={470} />
          <div style={{marginTop: 24, display: 'flex', alignItems: 'center', gap: 14}}>
            <div style={{backgroundColor: '#0D1117', border: '1px solid #30363D', borderRadius: 18, padding: '16px 22px', fontFamily: theme.font, color: theme.text, fontSize: 30, fontWeight: 700}}>
              @{'mrduhlol'.slice(0, typed)}
              <span style={{opacity: (f % 20) < 10 ? 1 : 0}}>▍</span>
            </div>
            <div style={{backgroundColor: theme.green, borderRadius: 18, padding: '16px 24px', fontFamily: theme.font, color: '#04120A', fontSize: 28, fontWeight: 900, transform: `scale(${pop(f, fps, 92)})`}}>
              ✓ IT'S YOU
            </div>
          </div>
          {/* the cat points at the username, then waves when confirmed */}
          <div style={{marginTop: 18, marginLeft: 240}}>
            <CatEnter f={f} fps={fps} delay={30} fromX={220} fromY={60} mode={checked ? 'wave' : 'point'} size={120} />
          </div>
        </div>
      </div>
    </Bg>
  );
};

export const SceneStyles: React.FC<{f: number; len: number}> = ({f, len}) => {
  const {fps} = useVideoConfig();
  const zoom = useZoom(f, len) * punch(f, 62);
  const cards = [
    {t: 'CLASSIC', seed: 3},
    {t: 'GRAPH ONLY', seed: 11},
    {t: 'NUMBERS', seed: 29},
  ];
  // the cat moonwalks across the top while palettes pop (linear: constant speed)
  const walkX = interpolate(f, [10, len - 10], [-560, 560], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
  return (
    <Bg zoom={zoom}>
      <div style={{position: 'absolute', top: 132, left: '50%', transform: `translateX(${walkX}px)`, opacity: interpolate(f, [10, 26], [0, 1])}}>
        <OctocatHero size={104} f={f} mode="walk" />
      </div>
      <div style={{position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 26, paddingTop: 70}}>
        <div style={{display: 'flex', gap: 22, alignItems: 'center'}}>
          <HeadlineCard lines={['3 styles.', '9 palettes.']} tilt={-3} width={400} />
          <div style={{position: 'relative', backgroundColor: '#161B22', border: '1px solid #30363D', borderRadius: 24, padding: 22, transform: 'rotate(2deg)'}}>
            <div style={{color: theme.text, fontFamily: theme.font, fontWeight: 900, fontSize: 30, marginBottom: 12}}>YOUR VIBE.</div>
            <div style={{display: 'flex', gap: 10}}>
              {['#39D353', '#1F6FEB', '#A371F7', '#F778BA', '#FFA657', '#FFFFFF'].map((c, i) => (
                <div key={c} style={{width: 44, height: 44, borderRadius: 22, backgroundColor: c, border: '2px solid #0D1117', transform: `scale(${pop(f, fps, 34 + i * 7)})`}} />
              ))}
            </div>
            <Tap x={150} y={96} f={f} at={70} fps={fps} />
          </div>
        </div>
        <div style={{display: 'flex', gap: 28}}>
          {cards.map((c, i) => (
            <div key={c.t} style={{backgroundColor: '#0D1117', border: '1px solid #30363D', borderRadius: 26, padding: 22, transform: `translateY(${interpolate(pop(f, fps, 24 + i * 12), [0, 1], [60, 0])}px) rotate(${(i - 1) * 2}deg)`, opacity: pop(f, fps, 24 + i * 12)}}>
              <div style={{color: theme.muted, fontFamily: theme.font, fontWeight: 800, fontSize: 18, letterSpacing: 2, marginBottom: 12}}>{c.t}</div>
              <ContribGraph weeks={12} seed={c.seed} cell={11} gap={3} />
            </div>
          ))}
        </div>
      </div>
    </Bg>
  );
};

export const SceneStats: React.FC<{f: number; len: number}> = ({f, len}) => {
  const {fps} = useVideoConfig();
  const zoom = useZoom(f, len) * punch(f, 104);
  const bars = [0.4, 0.65, 0.5, 0.9, 0.7, 1.0, 0.55, 0.8, 0.62, 0.95, 0.48, 0.72];
  const grown = f >= 104;
  return (
    <Bg zoom={zoom}>
      <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 70}}>
        <div style={{opacity: interpolate(f, [5, 25], [0, 1])}}>
          <HeadlineCard lines={['Streaks.', 'Best day.', 'Insights.']} tilt={-4} width={430} />
          <div style={{position: 'relative', marginTop: 22, backgroundColor: '#161B22', border: '1px solid #30363D', borderRadius: 20, padding: '18px 24px', maxWidth: 430}}>
            <div style={{color: theme.green, fontFamily: theme.font, fontWeight: 800, fontSize: 24}}>“Your best week is Tue–Thu.”</div>
            <div style={{color: theme.muted, fontFamily: theme.font, fontSize: 20, marginTop: 4}}>plain-language insights · demo figures</div>
            <Tap x={390} y={60} f={f} at={112} fps={fps} />
          </div>
          <div style={{marginTop: 16, marginLeft: 40}}>
            <CatEnter f={f} fps={fps} delay={20} fromX={-260} fromY={40} mode={grown ? 'cheer' : 'point'} size={118} />
          </div>
        </div>
        <div style={{display: 'flex', flexDirection: 'column', gap: 18, transform: `translateY(${slideX(f, fps, 10, 60)}px)`, opacity: interpolate(f, [10, 30], [0, 1])}}>
          <div style={{display: 'flex', gap: 14}}>
            {[
              {v: '24d', l: 'STREAK'},
              {v: '31', l: 'BEST DAY'},
              {v: '1.2k', l: 'TOTAL'},
            ].map((s, i) => (
              <div key={s.l} style={{width: 150, backgroundColor: '#0D1117', border: '1px solid #30363D', borderRadius: 22, padding: '20px 10px', textAlign: 'center', transform: `scale(${pop(f, fps, 28 + i * 10)})`}}>
                <div style={{color: theme.green, fontFamily: theme.font, fontWeight: 900, fontSize: 44}}>{s.v}</div>
                <div style={{color: theme.muted, fontFamily: theme.font, fontWeight: 800, fontSize: 16, letterSpacing: 1.5}}>{s.l}</div>
              </div>
            ))}
          </div>
          <div style={{backgroundColor: '#0D1117', border: '1px solid #30363D', borderRadius: 22, padding: 24}}>
            <div style={{color: theme.muted, fontFamily: theme.font, fontWeight: 800, fontSize: 18, letterSpacing: 2, marginBottom: 14}}>WEEKLY COMMITS · DEMO</div>
            <div style={{display: 'flex', alignItems: 'flex-end', gap: 10, height: 130}}>
              {bars.map((b, i) => (
                <div key={i} style={{flex: 1, height: interpolate(pop(f, fps, 42 + i * 5), [0, 1], [8, b * 130]), backgroundColor: i === 5 ? theme.green : '#21262D', borderRadius: 8}} />
              ))}
            </div>
          </div>
        </div>
      </div>
    </Bg>
  );
};

const CONFETTI = ['#39D353', '#1F6FEB', '#FFFFFF', '#F778BA', '#FFA657', '#A371F7'];

export const SceneCTA: React.FC<{f: number; len: number}> = ({f, len}) => {
  const {fps} = useVideoConfig();
  const zoom = useZoom(f, len) * punch(f, 24);
  const s = pop(f, fps, 10);
  return (
    <Bg zoom={zoom}>
      {/* confetti rain */}
      {Array.from({length: 26}).map((_, i) => {
        const h1 = Math.abs(Math.sin(i * 12.9898) * 43758.5453) % 1;
        const h2 = Math.abs(Math.sin(i * 78.233 + 4.2) * 12543.123) % 1;
        const x0 = h1 * 1440;
        const speed = 7 + h2 * 6;
        const y = ((f * speed + h2 * 1100) % 1250) - 90;
        const x = x0 + Math.sin((f + i * 41) / 17) * 34;
        const rot = f * (3 + h1 * 5) + i * 47;
        const sz = 8 + h2 * 9;
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: x,
              top: y,
              width: sz,
              height: sz * 0.62,
              borderRadius: 3,
              backgroundColor: CONFETTI[i % CONFETTI.length],
              transform: `rotate(${rot}deg)`,
              opacity: interpolate(y, [900, 1080], [1, 0], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}) * 0.9,
            }}
          />
        );
      })}
      <div style={{position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 16}}>
        <div style={{transform: `scale(${s})`, opacity: s}}>
          <OctocatHero size={148} f={f} mode="cheer" />
        </div>
        <div style={{fontFamily: theme.font, fontWeight: 900, fontSize: 62, color: 'white', letterSpacing: -2, textAlign: 'center', lineHeight: 1, transform: `translateY(${interpolate(s, [0, 1], [30, 0])}px)`, opacity: s}}>
          FREE. NO SIGN-IN.
          <br />
          <span style={{color: theme.green}}>JUST YOUR USERNAME.</span>
        </div>
        {/* download link card */}
        <div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10, transform: `scale(${pop(f, fps, 48)})`}}>
          <div style={{backgroundColor: theme.greenDark, borderRadius: 20, padding: '20px 44px', fontFamily: theme.font, fontWeight: 900, fontSize: 44, color: 'white', letterSpacing: -0.5, boxShadow: '0 16px 60px rgba(57,211,83,0.35)'}}>
            gh.abhishek-a.in
          </div>
          <div style={{backgroundColor: '#161B22', border: '1px solid #30363D', borderRadius: 999, padding: '12px 30px', fontFamily: theme.font, fontWeight: 800, fontSize: 24, color: theme.text}}>
            ▣ &nbsp;Download free APK · Android 8.0+
          </div>
        </div>
        <div style={{fontFamily: theme.font, color: theme.muted, fontSize: 22, fontWeight: 600, opacity: pop(f, fps, 64)}}>
          works offline · demo figures
        </div>
      </div>
    </Bg>
  );
};
