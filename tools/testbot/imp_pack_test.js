// Live check that the wave director really spawns imps in packs. The clamp arithmetic itself is proven separately
// (PackCheck, boundary table); there is no command to raise threat, so a live wave rarely reaches the 40 cap and this test
// does NOT claim to prove the cap. P1 enough samples, P2 imps appear and >=3 are seen at once.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const count = async sel => { await ask(`/execute store result score #n ebt if entity ${sel}`, 150); const r = await ask('/scoreboard players get #n ebt', 200); const m = /has (\d+)/.exec(r); return m ? +m[1] : NaN; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2500);
  await ask('/scoreboard objectives add ebt dummy', 200);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 3 true', 200);
  let maxHostile = 0, maxImps = 0, sawPack = 0, samples = 0;
  for (let i = 0; i < 90; i++) {
    const all = await count('@e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction,type=!minecraft:text_display,type=!minecraft:block_display]');
    const imps = await count('@e[type=emberfall:horde_imp]');
    if (!Number.isNaN(all)) { samples++; if (all > maxHostile) maxHostile = all; }
    if (!Number.isNaN(imps)) { if (imps > maxImps) maxImps = imps; if (imps >= 3) sawPack++; }
    await sleep(800);
  }
  console.log(`  samples ${samples}, max non-player entities ${maxHostile}, max imps at once ${maxImps}, samples with >=3 imps ${sawPack}`);
  R('P1 sampled enough (>=40 readings)', samples >= 40, `${samples}`);
  R('P2 imps spawned in packs (>=3 at once seen)', sawPack > 0, `${sawPack} samples`);
  await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
