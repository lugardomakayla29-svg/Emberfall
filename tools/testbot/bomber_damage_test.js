// How hard does one Bomber blast hit, base and veteran? 5 trials each on a pinned survival player with no armour effects.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask('/character select juggernaut'); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 500);
  for (const kind of ['base', 'veteran']) {
    const hits = [];
    for (let t = 0; t < 5; t++) {
      await ask('/effect give @s minecraft:instant_health 1 10 true', 200); await sleep(600);
      const hp0 = bot.health;
      if (kind === 'veteran') await ask('/execute at @s positioned ~6 ~ ~ run emberfall spawnveteran horde_bomber', 400);
      else {
        await ask('/execute at @s positioned ~6 ~ ~ run emberfall spawnveteran horde_bomber', 400);
        // turn the veteran into a base-tier blast by rewriting fuse/radius the way prepare() does for a plain Bomber
        await ask('/data merge entity @e[type=emberfall:horde_bomber,limit=1] {ExplosionRadius:2b,Fuse:20s}', 200);
      }
      await ask('/tag @e[type=emberfall:horde_bomber,limit=1] add bm', 150);
      await ask('/data merge entity @e[tag=bm,limit=1] {Invulnerable:1b,PersistenceRequired:1b}', 250);
      let low = hp0; const t0 = Date.now();
      while (Date.now() - t0 < 7000) { await sleep(120); if (bot.health < low) low = bot.health; const c = await ask('/execute if entity @e[tag=bm]', 100); if (/Test failed/.test(c)) break; }
      await sleep(400); if (bot.health < low) low = bot.health;
      hits.push(+(hp0 - low).toFixed(1)); await ask('/kill @e[type=emberfall:horde_bomber]', 200);
    }
    console.log(`${kind}: blast damage per trial ${hits.join(' ')}  (max hp ${bot.health.toFixed(1)}+)`);
  }
  clearInterval(pin); await ask('/expedition leave', 800);
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
