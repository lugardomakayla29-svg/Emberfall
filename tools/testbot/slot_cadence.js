// Counts real attacks per weapon, from the server's own answers rather than client packets.
// Method: hold one 1000 HP target, and every 500ms read its Health. The drop between reads is the damage that
// landed in that interval. Independent cadence means the halberd's damage rate must NOT fall when the bow is
// added, and the bow must add its own on top.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const lines = []; bot.on('message', m => lines.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(6000);
  const c = async (x, w = 800) => { bot.chat(x); await sleep(w); };
  const ask = async (x, w = 1000) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).filter(l => !/Teleported/.test(l)).join(' | '); };
  const hp = async tag => { const r = await ask(`/data get entity @e[tag=${tag},limit=1] Health`, 900); const m = r.match(/data: ([\d.]+)f/); return m ? parseFloat(m[1]) : null; };
  await c('/gamemode survival', 700); await c('/character select juggernaut', 700);
  await c('/expedition leave', 800); await c('/expedition', 4000);
  await c('/effect give @s minecraft:resistance 900 4 true', 400);
  await c('/effect give @s minecraft:regeneration 900 4 true', 400);
  const purge = setInterval(() => bot.chat('/kill @e[type=!player,tag=!keep,distance=..60]'), 2000);
  await sleep(2500);
  const pin = setInterval(() => bot.chat('/tp @s ' + bot.entity.position.x.toFixed(2) + ' ' + bot.entity.position.y.toFixed(2) + ' ' + bot.entity.position.z.toFixed(2)), 1000);
  const spawn = async tag => {
    await c(`/execute at @s run summon emberfall:horde_zombie ~2.5 ~ ~0 {Tags:["${tag}","keep"],PersistenceRequired:1b}`, 600);
    await c(`/attribute @e[tag=${tag},limit=1] minecraft:max_health base set 1000`, 300);
    await c(`/data merge entity @e[tag=${tag},limit=1] {Health:1000f}`, 300);
  };
  const hold = tag => setInterval(() => bot.chat(`/execute at @s run tp @e[tag=${tag},limit=1] ~2.5 ~ ~0`), 700);
  const measure = async (label, tag, secs) => {
    const start = await hp(tag); await sleep(secs * 1000); const end = await hp(tag);
    const lost = start !== null && end !== null ? start - end : null;
    console.log(label.padEnd(26), 'start', start, 'end', end, 'damage', lost === null ? '?' : lost.toFixed(1), 'per sec', lost === null ? '?' : (lost / secs).toFixed(2));
  };
  await spawn('c1'); let h = hold('c1');
  console.log('loadout:', await ask('/emberfall debugloadout EmberTester'));
  await measure('halberd alone (run 1)', 'c1', 15);
  await measure('halberd alone (run 2)', 'c1', 15);
  clearInterval(h); await c('/kill @e[tag=c1]', 300);
  await c('/emberfall selectweapon EmberTester hunting_bow', 700);
  console.log('loadout:', await ask('/emberfall debugloadout EmberTester'));
  await spawn('c2'); h = hold('c2');
  await measure('halberd + bow (run 1)', 'c2', 15);
  await measure('halberd + bow (run 2)', 'c2', 15);
  clearInterval(h); clearInterval(purge); clearInterval(pin);
  await c('/expedition leave', 1200);
  bot.quit(); setTimeout(() => process.exit(0), 600);
});
