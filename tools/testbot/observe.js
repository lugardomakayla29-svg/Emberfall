const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const out = []; bot.on('message', m => out.push(m.toString()));
async function dist(maxd) { // binary-ish: smallest d in list where zombie within d of villager
  for (const d of [1.5,2.5,4,6,8,10,12]) { out.length=0; bot.chat(`/execute at @e[tag=vil,limit=1] if entity @e[tag=mobx,distance=..${d}]`); await sleep(220); if (out.some(s=>/Test passed/.test(s))) return d; }
  return '>12';
}
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/difficulty hard'); await sleep(300);
  bot.chat('/kill @e[tag=vt]'); await sleep(400);
  bot.chat('/tp @s 200 119 222'); await sleep(1200);
  bot.chat('/fill 188 118 186 212 118 226 minecraft:stone'); await sleep(400);
  bot.chat('/fill 188 119 186 212 124 226 minecraft:air'); await sleep(400);
  bot.chat('/summon minecraft:villager 200 119 196 {Tags:["vt","vil"],Invulnerable:1b}'); await sleep(400);
  bot.chat('/summon minecraft:zombie 200 119 206 {Tags:["vt","mobx"],Invulnerable:1b}'); await sleep(400);
  bot.chat('/gamemode spectator'); await sleep(300);
  for (let i=0;i<10;i++){ await sleep(1000); console.log(`t+${i+1}s zombie within ${await dist()} of villager`); }
  bot.chat('/data get entity @e[tag=mobx,limit=1] NoAI'); await sleep(400); console.log(out.slice(-2).join(' | '));
  bot.chat('/kill @e[tag=vt]'); await sleep(300); bot.quit(); process.exit(0);
});
