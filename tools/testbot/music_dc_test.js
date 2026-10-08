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
    await ask('/gamemode survival'); await ask('/effect clear @s'); await ask('/character select juggernaut'); await ask('/expedition', 1500);
    // The map builds async (about 32 s). Without this wait the bot stands in the hub, where no music ever plays, and the rejoin phase
    // below would "pass" on music === 0 for the wrong reason. Poll the dimension, then require that music really started in the run.
    let inRun = false; for (let i = 0; i < 80 && !inRun; i++) { await sleep(1500); inRun = /expedition/.test(await ask('/data get entity @s Dimension', 400)); }
    await sleep(4000);
    console.log('in run, music packets:', music, '| in the expedition dimension:', inRun, inRun && music > 0 ? 'PASS J0' : 'FAIL J0 (no run or no music, so the rejoin phase proves nothing)');
  } else {
    // The rejoin phase is only meaningful if the player is still in a run: in the hub no music ever plays, so music === 0 would pass for
    // the wrong reason. Require the expedition dimension first and say so in the verdict.
    const dim = await ask('/data get entity @s Dimension', 600);
    const inRun = /expedition/.test(dim);
    const r = await ask('/emberfall musicnow EmberTester', 800);
    await sleep(3000);
    const now = (/MUSIC \S+/.exec(r) || ['MUSIC ?'])[0];
    console.log('after rejoin:', now, '| in the expedition dimension:', inRun, '| music packets received:', music,
      !inRun ? 'FAIL R0 (not in a run, so music === 0 proves nothing)' : (music === 0 ? 'PASS' : 'FAIL'));
  }
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
bot.on('error', e => console.log('ERR', e.message));
