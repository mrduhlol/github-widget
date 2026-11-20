import React from 'react';
import {AbsoluteFill, Sequence, Audio, staticFile, useCurrentFrame, interpolate} from 'remotion';
import {theme} from './theme';
import {LiveCaption, SubWord} from './Overlays';
import {SceneHook, SceneSetup, SceneStyles, SceneStats, SceneCTA} from './Scenes';
import vo1subs from '../public/audio/vo1.subs.json';
import vo2subs from '../public/audio/vo2.subs.json';
import vo3subs from '../public/audio/vo3.subs.json';
import vo4subs from '../public/audio/vo4.subs.json';
import vo5subs from '../public/audio/vo5.subs.json';

const SCENES: {
  Comp: React.FC<{f: number; len: number}>;
  subs: SubWord[];
  vo: string;
  from: number;
  len: number;
}[] = [
  {Comp: SceneHook, subs: vo1subs as SubWord[], vo: 'audio/vo1.mp3', from: 0, len: 170},
  {Comp: SceneSetup, subs: vo2subs as SubWord[], vo: 'audio/vo2.mp3', from: 170, len: 180},
  {Comp: SceneStyles, subs: vo3subs as SubWord[], vo: 'audio/vo3.mp3', from: 350, len: 180},
  {Comp: SceneStats, subs: vo4subs as SubWord[], vo: 'audio/vo4.mp3', from: 530, len: 180},
  {Comp: SceneCTA, subs: vo5subs as SubWord[], vo: 'audio/vo5.mp3', from: 710, len: 190},
];

const CUTS = [170, 350, 530, 710];

export const Main: React.FC = () => {
  const frame = useCurrentFrame();
  const active = SCENES.find(({from, len}) => frame >= from && frame < from + len);
  const ms = active ? ((frame - active.from) / 30) * 1000 : 0;
  const lastEnd = active ? active.subs[active.subs.length - 1].end : 0;
  const showCaption = !!active && ms < lastEnd + 800;
  return (
    <AbsoluteFill style={{backgroundColor: theme.bg, fontFamily: theme.font}}>
      {SCENES.map(({Comp, from, len}) => (
        <Sequence key={from} from={from} durationInFrames={len} name={`scene-${from}`}>
          <SceneInner Comp={Comp} len={len} />
        </Sequence>
      ))}
      {/* voiceover per scene */}
      {SCENES.map(({vo, from, len}) => (
        <Sequence key={`vo-${from}`} from={from} durationInFrames={len} name={`vo-${from}`}>
          <Audio src={staticFile(vo)} volume={1} />
        </Sequence>
      ))}
      {/* soft whoosh on each cut */}
      {CUTS.map((cut) => (
        <Sequence key={`w-${cut}`} from={cut - 12} durationInFrames={14} name={`whoosh-${cut}`}>
          <Audio src={staticFile('audio/whoosh.mp3')} volume={0.35} />
        </Sequence>
      ))}
      {/* soft music bed under everything */}
      <Audio src={staticFile('audio/music.mp3')} volume={1} />
      {/* crossfade edges — only 10f around each cut */}
      {CUTS.map((from) => {
        if (frame < from - 10 || frame > from + 2) return null;
        const o = interpolate(frame, [from - 10, from], [0, 1], {
          extrapolateLeft: 'clamp',
          extrapolateRight: 'clamp',
        });
        if (o <= 0) return null;
        return <AbsoluteFill key={`fade-${from}`} style={{backgroundColor: '#000', opacity: o * 0.9, pointerEvents: 'none'}} />;
      })}
      {/* clean auto caption, word-synced to the voiceover */}
      {showCaption && active && (
        <div style={{position: 'absolute', bottom: 44, left: 0, right: 0, display: 'flex', justifyContent: 'center'}}>
          <LiveCaption subs={active.subs} ms={ms} />
        </div>
      )}
    </AbsoluteFill>
  );
};

const SceneInner: React.FC<{Comp: React.FC<{f: number; len: number}>; len: number}> = ({Comp, len}) => {
  const frame = useCurrentFrame();
  return <Comp f={frame} len={len} />;
};
