const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/gamemode creative'), 300);
  setTimeout(() => bot.chat('/effect give @s minecraft:resistance 90 5'), 700);
  setTimeout(() => bot.chat('/emberfall spawnelite blightfeather_marksman'), 1200);
  setTimeout(() => bot.chat('/tp -110 64 -30'), 1700);
});
bot.on('message', m => console.log('CHAT: ' + JSON.stringify(m.toString())));
setTimeout(() => {
  console.log('BOT POS: ' + JSON.stringify(bot.entity.position));
  for (const id in bot.entities) {
    const e = bot.entities[id];
    if (e.name && e.name !== 'player') console.log('ENTITY: ' + e.name + ' at ' + JSON.stringify(e.position) + ' displayName=' + (e.displayName ? e.displayName.toString() : ''));
  }
}, 4000);
setTimeout(() => process.exit(0), 60000);
