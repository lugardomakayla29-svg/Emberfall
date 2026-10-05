// Packet cost of the Devourer worm per length: counts entity movement packets for item displays while the boss chases a moving bot.
// Server must be started with -Demberfall.wormParts=N (EXTRA_JVM). Judge = packet counts only.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const kinds = {}; const ids = new Set(); let counting = false; let bytes = 0;
bot._client.on('packet', (d, m, raw) => {
  if (!counting) return;
  const n = m.name;
  if (/entity_teleport|sync_entity_position|rel_entity_move|entity_move_look|entity_look|entity_metadata|set_entity_data|move_entity/.test(n)) {
    kinds[n] = (kinds[n] || 0) + 1; if (d && d.entityId !== undefined) ids.add(d.entityId);
    if (raw) bytes += raw.length;
  }
});
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  console.log('boss:', (await ask('/emberfall bossdevourer 0', 1200)).slice(0, 90)); await sleep(2500);   // boss FIRST: it needs the live Wave Director
  await ask('/emberfall wavestop 0', 400); await ask('/kill @e[type=!player,type=!emberfall:devourer_brain,type=!emberfall:devourer_spawn,type=!minecraft:item_display]', 600);
  await ask('/effect give @e[type=emberfall:devourer_brain,limit=1] minecraft:resistance 999 4 true', 200);
  const parts = await ask('/execute if entity @e[type=minecraft:item_display,tag=emberfall_worm]', 400);
  const cnt = async sel => { await ask('/scoreboard objectives add wc dummy', 150); await ask('/scoreboard players set #n wc -1', 150); await ask(`/execute store result score #n wc if entity ${sel}`, 250); const r = await ask('/scoreboard players get #n wc', 400); const m = /has (-?\d+) \[wc\]/.exec(r); return m ? +m[1] : NaN; };
  let wormDisplays = NaN; for (let k = 0; k < 4 && Number.isNaN(wormDisplays); k++) wormDisplays = await cnt('@e[type=minecraft:item_display,tag=emberfall_worm]');
  console.log('worm displays alive:', wormDisplays);
  // keep the bot moving in a slow circle so the boss keeps chasing; count for 30 s
  counting = true; const t0 = Date.now(); let a = 0;
  while (Date.now() - t0 < 30000) { a += 0.5; await ask(`/tp @s ${Math.round(Math.cos(a) * 14)} 66 ${Math.round(-24 + Math.sin(a) * 14)}`, 900); }
  counting = false; const secs = (Date.now() - t0) / 1000;
  const total = Object.values(kinds).reduce((x, y) => x + y, 0);
  console.log(`RESULT displays=${wormDisplays} packets=${total} in ${secs.toFixed(1)}s = ${(total / secs).toFixed(1)}/s  distinctEntities=${ids.size} bytes/s=${(bytes / secs).toFixed(0)}`);
  console.log('kinds', JSON.stringify(kinds));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
