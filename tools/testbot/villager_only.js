const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const out = []; bot.on('message', m => out.push(m.toString()));
async function q(cmd) { out.length = 0; bot.chat(cmd); await sleep(350); return out.some(s => /Test passed/.test(s)); }
// Player parked FAR away (spectator, 60+ blocks) so the villager is the mob's only candidate victim.
async function trial(label, type) {
  bot.chat('/kill @e[tag=vt]'); await sleep(500);
  bot.chat('/tp @s 200 119 222'); await sleep(1200);
  bot.chat('/fill 188 118 186 212 118 226 minecraft:stone'); await sleep(400);
  bot.chat('/fill 188 119 186 212 124 226 minecraft:air'); await sleep(400);
  bot.chat('/summon minecraft:villager 200 119 196 {Tags:["vt","vil"],NoAI:1b,Invulnerable:1b}'); await sleep(400);
  bot.chat(`/summon ${type} 200 119 206 {Tags:["vt","mobx"],Invulnerable:1b}`); await sleep(400);
  bot.chat('/gamemode spectator'); await sleep(300);
  bot.chat('/tp @s 200 125 240'); await sleep(200);
  let reached = false;
  for (let i = 0; i < 16; i++) {
    await sleep(750);
    if (await q('/execute at @e[tag=vil,limit=1] if entity @e[tag=mobx,distance=..2.5]')) { reached = true; break; }
  }
  console.log(`${label}: reached the villager (sole victim) = ${reached}`);
  bot.chat('/gamemode survival'); await sleep(300);
  bot.chat('/kill @e[tag=vt]'); await sleep(300);
}
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/difficulty hard'); await sleep(300);
  await trial('CONTROL minecraft:zombie ', 'minecraft:zombie');
  await trial('TEST horde_zombie        ', 'emberfall:horde_zombie');
  await trial('TEST plague_colossus     ', 'emberfall:plague_colossus');
  bot.quit(); process.exit(0);
});
