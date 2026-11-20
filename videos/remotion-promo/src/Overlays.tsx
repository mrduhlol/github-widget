import React from 'react';
import {theme} from './theme';

export type SubWord = {part: string; start: number; end: number};

// Clean auto caption: word-synced karaoke pill driven by TTS timings.
// Spoken words glow green, the live word pops, upcoming words stay dim.
export const LiveCaption: React.FC<{subs: SubWord[]; ms: number}> = ({subs, ms}) => (
  <div
    style={{
      backgroundColor: 'rgba(1,4,9,0.85)',
      border: '1px solid #30363D',
      borderRadius: 999,
      padding: '14px 32px',
      fontFamily: theme.font,
      fontSize: 30,
      fontWeight: 700,
      letterSpacing: -0.3,
      backdropFilter: 'blur(8px)',
      maxWidth: 1200,
      textAlign: 'center',
      lineHeight: 1.35,
    }}
  >
    {subs.map((w, i) => {
      const done = ms >= w.end;
      const live = ms >= w.start && ms < w.end;
      return (
        <span
          key={i}
          style={{
            color: done || live ? theme.green : 'rgba(240,246,252,0.55)',
            backgroundColor: live ? 'rgba(57,211,83,0.16)' : 'transparent',
            borderRadius: 8,
            padding: live ? '2px 6px' : undefined,
            transition: 'none',
          }}
        >
          {w.part}
        </span>
      );
    })}
  </div>
);

// Legacy static caption pill (kept for fallback).
export const Caption: React.FC<{text: string; accent?: string}> = ({text, accent}) => {
  if (!accent) {
    return (
      <div
        style={{
          backgroundColor: 'rgba(1,4,9,0.82)',
          border: '1px solid #30363D',
          borderRadius: 999,
          padding: '14px 30px',
          color: theme.text,
          fontFamily: theme.font,
          fontSize: 30,
          fontWeight: 700,
          letterSpacing: -0.3,
          backdropFilter: 'blur(8px)',
        }}
      >
        {text}
      </div>
    );
  }
  const [head, tail] = text.split(accent);
  return (
    <div
      style={{
        backgroundColor: 'rgba(1,4,9,0.82)',
        border: '1px solid #30363D',
        borderRadius: 999,
        padding: '14px 30px',
        color: theme.text,
        fontFamily: theme.font,
        fontSize: 30,
        fontWeight: 700,
        backdropFilter: 'blur(8px)',
      }}
    >
      {head}
      <span style={{color: theme.green}}>{accent}</span>
      {tail}
    </div>
  );
};

export const HeadlineCard: React.FC<{
  lines: string[];
  tilt?: number;
  width?: number;
}> = ({lines, tilt = -4, width = 440}) => (
  <div
    style={{
      width,
      backgroundColor: theme.card,
      borderRadius: 36,
      padding: '44px 40px',
      transform: `rotate(${tilt}deg)`,
      boxShadow: '0 24px 80px rgba(29,185,84,0.35)',
    }}
  >
    {lines.map((l) => (
      <div
        key={l}
        style={{
          color: 'white',
          fontFamily: theme.font,
          fontWeight: 900,
          fontSize: 62,
          lineHeight: 0.98,
          letterSpacing: -2,
          textTransform: 'uppercase',
        }}
      >
        {l}
      </div>
    ))}
  </div>
);
