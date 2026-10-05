const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let stops = 0, music = 0;
bot._client.on('packet', (d, meta) => { if (meta.name === 'stop_sound') stops++; if (meta.name === 'sound_effect' && /music/.test(JSON.stringify(d))) music++; });
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
const now = async () => { const r = await ask('/emberfall musicnow EmberTester', 700); const m = /MUSIC (\S+)/.exec(r); return m ? m[1] : '?'; };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  R('X0 music on in the run', (await now()) !== 'none');
  const s0 = stops;
  await ask('/damage @s 1000 minecraft:generic', 1500); await sleep(1000);
  R('X1 a lethal hit stops the music', stops > s0, `(${stops - s0} stop)`);
  R('X2 nothing playing after the run ended', (await now()) === 'none');
  await sleep(1500); await ask('/effect clear @s');
  // a second run must start music again, once
  const m0 = music;
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  R('X3 a second run starts exactly one new track', music === m0 + 1, `(${music - m0})`);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
