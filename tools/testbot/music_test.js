const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let sounds = [], stops = [];
bot._client.on('packet', (d, meta) => {
  if (meta.name === 'sound_effect') sounds.push(JSON.stringify(d).slice(0, 220));
  if (meta.name === 'stop_sound') stops.push(JSON.stringify(d));
});
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
const TRACKS = ['nyny_08', 'pump_it_up_hyperdron', 'multi_minecart_drifting', 'final_cubic_generator', 'flight_in_fantasia', 'gamemode88', 'icy_breeze', 'license_to_infinity'];
const NEW_TRACKS = ['flight_in_fantasia', 'gamemode88', 'icy_breeze', 'license_to_infinity'];
const now = async () => { const r = await ask('/emberfall musicnow EmberTester', 700); const m = /MUSIC (\S+)/.exec(r); return m ? m[1] : '?'; };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  R('M0 nothing playing before a run', (await now()) === 'none');
  // music sound packets carry the music source; count only those with the emberfall music id or category 'music'
  const isMusic = s => /music/i.test(s);
  sounds = []; stops = [];
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  // The map builds async (about 32 s). Poll the dimension so what follows is judged in the RUN, not in the hub, and assert it.
  // The music packet is sent on join, so it lands DURING this poll and startPk below still counts it (M2 expects exactly one).
  let inRun = false; for (let i = 0; i < 80 && !inRun; i++) { await sleep(1500); inRun = /expedition/.test(await ask('/data get entity @s Dimension', 400)); }
  R('R0 the bot is inside a run (nothing below means anything in the hub)', inRun, `inRun=${inRun}`);
  const first = await now();
  const startPk = sounds.filter(isMusic).length;
  R('M1 a track starts on entry', TRACKS.includes(first), `(${first})`);
  R('M2 exactly one music packet on entry', startPk === 1, `(${startPk})`);
  await sleep(6000);
  R('M3 no repeat packets over 6s idle', sounds.filter(isMusic).length === startPk, `(${sounds.filter(isMusic).length})`);
  const played = [first];
  for (let i = 0; i < TRACKS.length - 1; i++) { await ask('/emberfall musicskip EmberTester', 300); await sleep(1500); played.push(await now()); }
  R(`M4 ${TRACKS.length} tracks in a row are all different (one full pass) and all 4 new songs are among them`, new Set(played).size === TRACKS.length && NEW_TRACKS.every(t => played.includes(t)), `(${played.join(' > ')})`);
  await ask('/emberfall musicskip EmberTester', 300); await sleep(1500);
  const fourth = await now();
  R('M5 new pass never starts with the track that just played', fourth !== played[TRACKS.length - 1], `(${played[TRACKS.length - 1]} > ${fourth})`);
  const beforeStops = stops.length;
  await ask('/expedition leave', 1200);
  R('M6 leaving sends a stop-sound packet', stops.length > beforeStops, `(${stops.length - beforeStops})`);
  R('M7 nothing playing after leaving', (await now()) === 'none');
  const after = sounds.filter(isMusic).length;
  await sleep(4000);
  R('M8 no music packet after leaving', sounds.filter(isMusic).length === after);
  console.log('     sample music packet:', (sounds.find(isMusic) || 'none').slice(0, 200));
  console.log('     sample stop packet:', stops[stops.length - 1] || 'none');
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
