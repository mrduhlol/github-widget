import React from 'react';
import {theme} from './theme';
import {ContribGraph} from './Graph';

export const Phone: React.FC<{
  width?: number;
  title?: string;
  subtitle?: string;
  stats?: {label: string; value: string}[] | null;
  seed?: number;
  style?: React.CSSProperties;
}> = ({width = 340, title = 'mrduhlol', subtitle = '1,284 contributions', stats, seed = 7, style}) => {
  return (
    <div
      style={{
        width,
        borderRadius: 48,
        backgroundColor: '#010409',
        border: '3px solid #30363D',
        padding: 14,
        boxShadow: '0 40px 120px rgba(57,211,83,0.25), 0 8px 40px rgba(0,0,0,0.6)',
        ...style,
      }}
    >
      <div
        style={{
          borderRadius: 34,
          backgroundColor: theme.panel,
          overflow: 'hidden',
          border: '1px solid #21262D',
        }}
      >
        {/* status bar */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '12px 20px 8px',
            color: theme.text,
            fontFamily: theme.font,
            fontSize: 15,
            fontWeight: 600,
          }}
        >
          <span>9:41</span>
          <div style={{width: 90, height: 26, borderRadius: 14, backgroundColor: '#000'}} />
          <span>▲ ● ●</span>
        </div>
        {/* widget */}
        <div style={{margin: 14, borderRadius: 22, backgroundColor: '#0D1117', border: '1px solid #30363D', padding: 18}}>
          <div style={{display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12}}>
            <div style={{width: 44, height: 44, borderRadius: 22, backgroundColor: theme.greenDeep, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', fontWeight: 900, fontSize: 20, fontFamily: theme.font}}>
              M
            </div>
            <div>
              <div style={{color: theme.text, fontWeight: 800, fontSize: 19, fontFamily: theme.font}}>{title}</div>
              <div style={{color: theme.muted, fontSize: 14, fontFamily: theme.font}}>{subtitle} · demo</div>
            </div>
            <div style={{marginLeft: 'auto', backgroundColor: theme.greenDark, color: 'white', fontSize: 12, fontWeight: 800, padding: '4px 10px', borderRadius: 20, fontFamily: theme.font}}>
              ● LIVE
            </div>
          </div>
          <ContribGraph weeks={18} seed={seed} cell={13} gap={4} />
          {stats !== null && (
            <div style={{display: 'flex', gap: 10, marginTop: 14}}>
              {(stats ?? [
                {label: 'STREAK', value: '24d'},
                {label: 'TODAY', value: '7'},
                {label: 'BEST', value: '31'},
              ]).map((s) => (
                <div key={s.label} style={{flex: 1, backgroundColor: '#161B22', borderRadius: 14, padding: '10px 6px', textAlign: 'center', border: '1px solid #21262D'}}>
                  <div style={{color: theme.green, fontWeight: 900, fontSize: 22, fontFamily: theme.font}}>{s.value}</div>
                  <div style={{color: theme.muted, fontWeight: 800, fontSize: 11, letterSpacing: 1, fontFamily: theme.font}}>{s.label}</div>
                </div>
              ))}
            </div>
          )}
        </div>
        {/* home indicator */}
        <div style={{display: 'flex', justifyContent: 'center', padding: '6px 0 14px'}}>
          <div style={{width: 120, height: 5, borderRadius: 3, backgroundColor: '#484F58'}} />
        </div>
      </div>
    </div>
  );
};
