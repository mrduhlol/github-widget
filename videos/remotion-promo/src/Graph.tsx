import React, {useMemo} from 'react';

const LEVELS = ['#161B22', '#0E4429', '#006D32', '#26A641', '#39D353'];

// Deterministic demo graph — clearly labeled demo figures per PRODUCT.md
export const ContribGraph: React.FC<{
  weeks?: number;
  seed?: number;
  cell?: number;
  gap?: number;
}> = ({weeks = 26, seed = 7, cell = 14, gap = 4}) => {
  const grid = useMemo(() => {
    const out: number[][] = [];
    for (let w = 0; w < weeks; w++) {
      const col: number[] = [];
      for (let d = 0; d < 7; d++) {
        const v = Math.sin(w * 12.9898 + d * 78.233 + seed * 37.7) * 43758.5453;
        const f = v - Math.floor(v);
        // weekend dip + streak band in middle for visual story
        let lvl = Math.floor(f * 5);
        if (w > weeks * 0.35 && w < weeks * 0.7 && d < 5) lvl = Math.max(lvl, 3);
        col.push(lvl);
      }
      out.push(col);
    }
    return out;
  }, [weeks, seed]);

  return (
    <div style={{display: 'flex', gap}}>
      {grid.map((col, wi) => (
        <div key={wi} style={{display: 'flex', flexDirection: 'column', gap}}>
          {col.map((lvl, di) => (
            <div
              key={di}
              style={{
                width: cell,
                height: cell,
                borderRadius: 4,
                backgroundColor: LEVELS[lvl],
                border: lvl === 0 ? '1px solid #30363D' : 'none',
              }}
            />
          ))}
        </div>
      ))}
    </div>
  );
};
