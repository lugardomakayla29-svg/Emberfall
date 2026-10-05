const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({host:'127.0.0.1',port:25565,username:'EmberTester',version:'1.21.11',auth:'offline'});
let target = null;
bot.on('spawn', () => {
  setTimeout(() => bot.chat('/tp 210 101 210'), 500);
  setTimeout(() => bot.chat('/gamemode survival'), 1500);
  setTimeout(() => bot.chat('/effect give @s minecraft:resistance 600 6'), 2200);
  setTimeout(() => bot.chat('/effect give @s minecraft:fire_resistance 600'), 2900);
  setTimeout(() => bot.chat('/give @s minecraft:diamond_sword'), 3200);
  setTimeout(() => bot.chat('/emberfall spawnelite corrupted_sentinel'), 3600);
});
bot.on('message', m => { const s = m.toString(); if (!/^$/.test(s)) console.log('CHAT: ' + s); });

// Keep attacking the sentinel every 600ms to force it into combat and low HP (fortify test)
setInterval(() => {
  const ent = Object.values(bot.entities).find(e => e.name === 'zombie' || (e.displayName && /sentinel/i.test(e.displayName)));
  if (ent) {
    target = ent;
    try {
      bot.lookAt(ent.position.offset(0, 1, 0), true);
      bot.attack(ent);
    } catch (e) {}
  }
}, 600);

setTimeout(() => { bot.quit(); process.exit(0); }, 90000);
