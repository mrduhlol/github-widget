import {EdgeTTS} from 'node-edge-tts';
import {execFileSync} from 'node:child_process';
import {mkdirSync, readdirSync, renameSync, existsSync} from 'node:fs';

const AUD = new URL('../public/audio/', import.meta.url);
mkdirSync(AUD, {recursive: true});
const out = (n) => new URL(`../public/audio/${n}`, import.meta.url).pathname;

const LINES = [
  ['vo1', 'Your GitHub graph, live on your home screen.'],
  ['vo2', 'No sign-in, no token. Just type your username.'],
  ['vo3', 'Three styles, nine palettes. Make it your vibe.'],
  ['vo4', 'Track streaks, best days, and plain language insights.'],
  ['vo5', 'Free. No sign-in. Get G H widgets today!'],
];

for (const [name, text] of LINES) {
  console.log('TTS:', name);
  const tts = new EdgeTTS({
    voice: 'en-US-AriaNeural', // soft, professional anchor voice
    lang: 'en-US',
    rate: '-10%',
    pitch: '-3Hz',
    outputFormat: 'audio-24khz-96kbitrate-mono-mp3',
    saveSubtitles: true,
  });
  await tts.ttsPromise(text, out(`${name}.mp3`));
}

// EdgeTTS writes subtitle sidecars next to the mp3 — normalize to <name>.subs.json
for (const f of readdirSync(AUD)) {
  if (/^vo\d\.mp3\.json$/.test(f) || /^vo\d\.json$/.test(f)) {
    renameSync(new URL(`../public/audio/${f}`, import.meta.url).pathname, out(`${f.slice(0, 3)}.subs.json`));
  }
}
console.log('subs:', readdirSync(AUD).filter((f) => f.endsWith('.subs.json')));

// Soft ambient pad bed, 30s, baked quiet so it sits under the voice.
console.log('MUSIC: music.mp3');
execFileSync('ffmpeg', [
  '-y',
  '-f', 'lavfi', '-i', 'sine=frequency=110:duration=30',
  '-f', 'lavfi', '-i', 'sine=frequency=164.81:duration=30',
  '-f', 'lavfi', '-i', 'sine=frequency=220:duration=30',
  '-f', 'lavfi', '-i', 'sine=frequency=277.18:duration=30',
  '-filter_complex',
  '[0][1][2][3]amix=inputs=4:normalize=0,tremolo=f=0.15:d=0.4,lowpass=f=850,aecho=0.8:0.7:60:0.25,volume=0.16,afade=t=in:st=0:d=2,afade=t=out:st=27:d=3,aresample=44100,aformat=channel_layouts=stereo',
  '-c:a', 'libmp3lame', '-b:a', '128k',
  out('music.mp3'),
]);

// Short soft riser for scene cuts.
console.log('SFX: whoosh.mp3');
execFileSync('ffmpeg', [
  '-y',
  '-f', 'lavfi', '-i', 'aevalsrc=sin(2*PI*(220+1500*t/0.4)*t):s=44100:d=0.4',
  '-af', 'volume=0.35,afade=t=in:st=0:d=0.12,afade=t=out:st=0.15:d=0.25,lowpass=f=2400',
  '-c:a', 'libmp3lame', '-b:a', '128k',
  out('whoosh.mp3'),
]);

console.log('done');
