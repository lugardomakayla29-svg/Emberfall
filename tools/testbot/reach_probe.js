const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('error', e => console.log('ERROR', e));
const out = [];
bot.on('message', m => out.push(m.toString()));
// Ask the server a yes/no question: "Test passed" means the condition held.
async function q(cmd) { out.length = 0; bot.chat(cmd); await sleep(350); if (out.some(s => /rror|Unknown|Incorrect/.test(s))) console.log('CMD ERR:', out.join(' | ')); return out.some(s => /Test passed/.test(s)); }
async function trial(label, type) {
  bot.chat('/kill @e[tag=vt]'); await sleep(500);
  bot.chat('/tp @s 200 119 222'); await sleep(1500);
  // Flat stone pad so nothing blocks pathing. Villager north (z=190), mob middle (z=204), player south (z=222).
  bot.chat('/fill 188 118 186 212 118 226 minecraft:stone'); await sleep(400);
  bot.chat('/fill 188 119 186 212 124 226 minecraft:air'); await sleep(400);
  bot.chat('/tp @s 200 119 222'); await sleep(600);
  bot.chat('/summon minecraft:villager 200 119 190 {Tags:["vt","vil"],NoAI:1b,Invulnerable:1b}'); await sleep(400);
  bot.chat(`/summon ${type} 200 119 204 {Tags:["vt","mobx"],Invulnerable:1b}`); await sleep(500);
  let nearVil = false, nearPlayer = false;
  for (let i = 0; i < 16; i++) {
    await sleep(750);
    if (await q('/execute at @e[tag=vil,limit=1] if entity @e[tag=mobx,distance=..3.5]')) nearVil = true;
    if (await q('/execute at @p if entity @e[tag=mobx,distance=..4.5]')) nearPlayer = true;
  }
  const alive = await q('/execute if entity @e[tag=mobx]');
  console.log(`${label}: reached villager=${nearVil}  reached player=${nearPlayer}  mob alive at end=${alive}`);
  bot.chat('/kill @e[tag=vt]'); await sleep(300);
}
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/gamemode survival'); await sleep(400);
  bot.chat('/effect give @s minecraft:resistance 600 4 true'); await sleep(300);
  bot.chat('/effect give @s minecraft:regeneration 600 4 true'); await sleep(300);
  bot.chat('/difficulty hard'); await sleep(300);
  await trial('CONTROL minecraft:zombie   ', 'minecraft:zombie');
  await trial('TEST horde_zombie          ', 'emberfall:horde_zombie');
  await trial('TEST plague_colossus       ', 'emberfall:plague_colossus');
  bot.quit(); process.exit(0);
});
