const mineflayer = require('mineflayer');
const phase = process.argv[2];
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 900) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let music = 0; bot._client.on('packet', (d, meta) => { if (meta.name === 'sound_effect' && /music/.test(JSON.stringify(d))) music++; });
bot.once('spawn', async () => {
  await sleep(3500);
  if (phase === 'join') {
    await ask('/gamemode survival'); await ask('/effect clear @s'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
    console.log('in run, music packets:', music);
  } else {
    const r = await ask('/emberfall musicnow EmberTester', 800);
    await sleep(3000);
    console.log('after rejoin:', /MUSIC \S+/.exec(r)[0], '| music packets received:', music, music === 0 ? 'PASS' : 'FAIL');
  }
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
