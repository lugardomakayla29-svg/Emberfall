// Do real waves spawn Shieldbearers, and do they hold a shield and a gradient name?
// Let a live wave run with the player protected, and count every horde type seen. Expect the Shieldbearer near 8% of fodder.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const count = async sel => { await ask(`/execute store result score #c emberfall_t if entity ${sel}`, 150); const r = await ask('/scoreboard players get #c emberfall_t', 220); const m = /has (\d+)/.exec(r); return m ? +m[1] : NaN; };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/scoreboard objectives add emberfall_t dummy', 200);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  // keep them alive so the mix is not thinned by the auto-weapon between samples: tag what we see
  const types = ['horde_zombie', 'horde_skeleton', 'horde_spider', 'horde_witch', 'horde_shieldbearer'];
  const seen = {}; types.forEach(t => seen[t] = new Set());
  let plainChecked = 0, plainOk = 0, vetShieldbearers = 0;
  const t0 = Date.now();
  while (Date.now() - t0 < 150000) {
    await sleep(2500);
    for (const t of types) {
      const n = await count(`@e[type=emberfall:${t},tag=!seen]`);
      if (n > 0) { seen[t].add(Date.now() + ':' + n); await ask(`/tag @e[type=emberfall:${t},tag=!seen] add seen`, 150); seen[t].total = (seen[t].total || 0) + n; }
    }
    // every Shieldbearer the wave director made must hold a shield in the off hand and carry a gradient name
    const r = await ask('/data get entity @e[type=emberfall:horde_shieldbearer,tag=!chk,limit=1] {}', 300);
    if (/equipment/.test(r) || /CustomName/.test(r)) {
      const vet = /Veteran/.test(r);
      const shield = /offhand: \{[^}]*minecraft:shield/.test(r), named = /bold: 1b/.test(r);
      const hp = /Health: ([\d.]+)f/.exec(r);
      if (vet) vetShieldbearers++; else plainChecked++;
      if (shield && named) plainOk++;
      console.log(`  shieldbearer: ${vet ? 'veteran' : 'plain'} shield ${shield} gradient-name ${named} hp ${hp && hp[1]}`);
      await ask('/tag @e[type=emberfall:horde_shieldbearer,tag=!chk,limit=1] add chk', 150);
    }
  }
  const tot = types.reduce((a, t) => a + (seen[t].total || 0), 0);
  const line = types.map(t => `${t.replace('horde_', '')} ${seen[t].total || 0}`).join('  ');
  console.log(`seen ${tot}:  ${line}`);
  const share = tot ? (seen.horde_shieldbearer.total || 0) / tot : 0;
  R('BW1 waves spawn Shieldbearers at all', (seen.horde_shieldbearer.total || 0) > 0, `${seen.horde_shieldbearer.total || 0} of ${tot}`);
  // Not an assertion: ~15-20 mobs cannot verify a 7% weight (3 of 15 is an ordinary draw, ~8% chance). The weight is a source constant.
  console.log(`  INFO BW2 Shieldbearer share of this sample: ${(share * 100).toFixed(1)}% (weight 0.07, sample too small to assert)`);
  R('BW3 every wave-spawned Shieldbearer holds a shield and has a gradient name', plainChecked + vetShieldbearers > 0 && plainOk === plainChecked + vetShieldbearers, `${plainOk}/${plainChecked + vetShieldbearers} (${vetShieldbearers} veteran)`);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 500); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
