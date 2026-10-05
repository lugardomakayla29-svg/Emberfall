const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/difficulty hard'); await sleep(300);
  bot.chat('/kill @e[tag=vt]'); await sleep(400);
  bot.chat('/tp @s 200 119 222'); await sleep(1200);
  bot.chat('/fill 188 118 186 212 118 226 minecraft:stone'); await sleep(400);
  bot.chat('/fill 188 119 186 212 124 226 minecraft:air'); await sleep(400);
  bot.chat('/summon minecraft:villager 200 119 208 {Tags:["vt"],Invulnerable:1b}'); await sleep(300);
  bot.chat('/summon minecraft:iron_golem 204 119 208 {Tags:["vt"],Invulnerable:1b}'); await sleep(300);
  for (const t of ['plague_colossus','horde_zombie','bonecaller_necromancer','horde_skeleton','horde_spider'])
    { bot.chat(`/summon emberfall:${t} 200 119 206 {Tags:["vt"],Invulnerable:1b}`); await sleep(300); }
  bot.chat('/gamemode spectator'); await sleep(15000);
  bot.chat('/kill @e[tag=vt]'); await sleep(400); bot.quit(); process.exit(0);
});
