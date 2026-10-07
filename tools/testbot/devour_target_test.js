// BROODTIDE PROTOTYPE A, part 2: does a weapon still STRIKE a hidden (swallowed) mob? Two identical frozen zombies, one visible and one hidden,
// both well inside the juggernaut halberd's reach. A hidden mob that keeps taking damage (or keeps being picked) would waste player strikes.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => { const t = m.toString(); if (!/^Teleported EmberTester/.test(t)) lines.push(t); }); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const R = (n, ok, extra = '') => console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`);
async function waitInExpedition(ms = 90000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { const r = await ask('/data get entity @s Dimension', 400); if (/emberfall:expedition/.test(r)) return true; await sleep(1500); } return false; }
const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 450); const m = /entity data: (-?[\d.]+)f/.exec(r); return m ? +m[1] : NaN; };
bot.once('spawn', async () => {
  await sleep(4000);
  await ask('/gamemode survival'); await ask('/effect clear @s'); await ask('/character select juggernaut'); await ask('/expedition leave', 800); await ask('/expedition', 800);
  const arrived = await waitInExpedition(); R('T0 the player reached the expedition', arrived); if (!arrived) { bot.quit(); process.exit(0); }
  await ask('/effect give @s minecraft:resistance 999 4 true'); await ask('/effect give @s minecraft:regeneration 999 4 true');
  await ask('/emberfall wavestop 0', 300); await ask('/kill @e[type=!player,distance=..90]', 900);
  // Max health is clamped at 1024 by the game.
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  // NO pin loop: measured (devour_probe3) that tp-pinning suppresses the halberd's auto-attack (0 damage with the pin, 48 in 10 s without).
  const foe = async (tag, dx, dz) => {
    await ask(`/execute at @s run summon emberfall:horde_zombie ~${dx} ~ ~${dz} {Tags:["${tag}","keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 300);
    await ask(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set 1024`, 120);
    await ask(`/data modify entity @e[tag=${tag},limit=1] Health set value 1024.0f`, 120);
  };
  // The halberd commits to ONE primary foe (measured, devour_probe4). Put the HIDDEN one clearly nearest, the visible one a little farther, same side:
  // if the weapon wastes its swings on the hidden mob the visible one is never hit; if it skips hidden mobs the visible one is.
  await foe('hid', 1.4, 0.8); await foe('vis', 3.0, 0.8);
  const v0 = await hp('vis'), h0 = await hp('hid');
  R('T1 both subjects exist with full HP', Number.isFinite(v0) && Number.isFinite(h0) && v0 > 1000 && h0 > 1000, `vis=${v0} hid=${h0}`);
  // HIDE exactly as the Devour would (Prototype A): invulnerable + silent + invisible, NoAI already set.
  await ask('/data merge entity @e[tag=hid,limit=1] {Invulnerable:1b,Silent:1b}', 400); await ask('/effect give @e[tag=hid,limit=1] minecraft:invisibility 999 0 true', 300);
  await sleep(14000);   // the halberd auto-attacks on its own
  const v1 = await hp('vis'), h1 = await hp('hid');
  console.log(`     12 s of halberd: visible ${v0} -> ${v1} (lost ${(v0 - v1).toFixed(1)}), hidden ${h0} -> ${h1} (lost ${(h0 - h1).toFixed(1)})`);
  R('T2 the visible zombie is hurt (the weapon skips the hidden one, or cleaves onto it)', v0 - v1 > 20, `lost ${(v0 - v1).toFixed(1)}`);
  R('T3 a HIDDEN (invulnerable) zombie takes no damage', h1 >= h0 - 0.01, `lost ${(h0 - h1).toFixed(1)}`);
  await ask('/kill @e[tag=vis]', 300); await ask('/kill @e[tag=hid]', 300); await ask('/expedition leave', 800); bot.quit(); process.exit(0);
});
