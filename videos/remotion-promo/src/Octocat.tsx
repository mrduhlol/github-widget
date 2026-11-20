import React from 'react';

// GitHub mark (Octocat head) — CC0 Simple Icons path, white fill
export const Octocat: React.FC<{size?: number; style?: React.CSSProperties}> = ({
  size = 120,
  style,
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="white"
    style={{display: 'block', ...style}}
  >
    <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12" />
  </svg>
);

export type CatMode = 'float' | 'walk' | 'wave' | 'point' | 'cheer' | 'perch';

// Animated GitHub-cat character: badge body, waving/pointing arms,
// shuffling feet, squash-and-stretch jumps. Fully deterministic from f.
export const OctocatHero: React.FC<{
  size?: number;
  f: number;
  mode?: CatMode;
  style?: React.CSSProperties;
}> = ({size = 160, f, mode = 'float', style}) => {
  const u = size / 160; // unit scale
  const sin = Math.sin;
  let bodyY = 0;
  let rock = 0;
  let squash = 1;
  let armL = -18;
  let armR = 18;
  let footL = 0;
  let footR = 0;

  if (mode === 'float') {
    bodyY = sin(f / 16) * 8 * u;
    rock = sin(f / 22) * 5;
  } else if (mode === 'walk') {
    const p = f / 3.2;
    footL = Math.max(0, sin(p)) * 12 * u;
    footR = Math.max(0, sin(p + Math.PI)) * 12 * u;
    bodyY = -Math.abs(sin(p)) * 7 * u;
    rock = sin(p) * 5;
    armL = -18 + sin(p) * 14;
    armR = 18 - sin(p) * 14;
  } else if (mode === 'wave') {
    bodyY = sin(f / 16) * 6 * u;
    armR = -150 + sin(f / 4.5) * 26; // big friendly wave
    armL = -18;
    rock = sin(f / 20) * 3;
  } else if (mode === 'point') {
    bodyY = sin(f / 18) * 4 * u;
    armR = -72; // extended toward the phone
    armL = -14;
    rock = 6;
  } else if (mode === 'cheer') {
    const jump = Math.abs(sin(f / 6.5));
    bodyY = -jump * 30 * u;
    squash = 1 - (1 - jump) * 0.1;
    armL = -150;
    armR = 150;
    rock = sin(f / 6.5) * 6;
  } else {
    // perch: settled, gentle breathing + occasional blink-rock
    bodyY = sin(f / 20) * 3 * u;
    rock = sin(f / 26) * 2;
  }

  const arm = (side: 1 | -1, deg: number) => (
    <div
      style={{
        position: 'absolute',
        top: 62 * u,
        left: side < 0 ? -8 * u : undefined,
        right: side > 0 ? -8 * u : undefined,
        width: 30 * u,
        height: 64 * u,
        borderRadius: 15 * u,
        backgroundColor: '#1A7F37',
        transform: `rotate(${side * deg}deg)`,
        transformOrigin: '50% 12%',
        boxShadow: '0 6px 18px rgba(0,0,0,0.35)',
      }}
    />
  );

  return (
    <div style={{width: size, height: size + 34 * u, position: 'relative', ...style}}>
      {/* shadow */}
      <div
        style={{
          position: 'absolute',
          bottom: 0,
          left: '50%',
          width: 110 * u,
          height: 20 * u,
          borderRadius: '50%',
          backgroundColor: 'rgba(0,0,0,0.45)',
          transform: `translateX(-50%) scaleX(${1 + bodyY / (220 * u)})`,
          opacity: 0.7,
        }}
      />
      {/* feet */}
      <div style={{position: 'absolute', bottom: 12 * u, left: 38 * u, width: 34 * u, height: 22 * u, borderRadius: 11 * u, backgroundColor: '#0E4429', transform: `translateY(${-footL}px)`}} />
      <div style={{position: 'absolute', bottom: 12 * u, right: 38 * u, width: 34 * u, height: 22 * u, borderRadius: 11 * u, backgroundColor: '#0E4429', transform: `translateY(${-footR}px)`}} />
      {/* body */}
      <div
        style={{
          position: 'absolute',
          bottom: 22 * u,
          left: '50%',
          width: size,
          height: size,
          transform: `translateX(-50%) translateY(${bodyY}px) rotate(${rock}deg) scaleY(${squash})`,
        }}
      >
        {arm(-1, armL)}
        {arm(1, armR)}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            borderRadius: '50%',
            backgroundColor: '#238636',
            border: `${4 * u}px solid rgba(255,255,255,0.14)`,
            boxShadow: '0 16px 60px rgba(0,0,0,0.5), 0 0 44px rgba(57,211,83,0.35)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Octocat size={size * 0.6} />
        </div>
      </div>
    </div>
  );
};
