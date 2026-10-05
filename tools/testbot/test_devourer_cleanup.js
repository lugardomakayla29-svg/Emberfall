const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

let started = false;
bot.on('spawn', () => {
  if (started) return;
  started = true;
  const t = (ms, f) => setTimeout(f, ms);

  t(500, () => cmd('/gamemode creative'));
  t(1000, () => cmd('/emberfall paste hydraphase'));
  t(4000, () => cmd('/emberfall wavestart 0'));
  t(7000, () => cmd('/emberfall join 0 EmberTester'));
  t(10000, () => cmd('/gamemode spectator'));
  t(13000, () => cmd('/emberfall bossdevourer 0'));

  // Let phase 2/3 fire so minis exist too, then one-shot kill it.
  t(17000, () => cmd('/execute as @e[type=emberfall:devourer_brain,limit=1] run damage @s 90 minecraft:generic'));
  t(21000, () => cmd('/execute as @e[type=emberfall:devourer_brain,limit=1] run damage @s 90 minecraft:generic'));
  t(25000, () => cmd('/execute as @e[type=emberfall:devourer_brain,limit=1] run damage @s 1000 minecraft:generic'));

  // Give teardown a couple seconds, then dump exact remaining entity detail.
  t(29000, () => cmd('/execute as @e[type=minecraft:item_display] at @s run data get entity @s item.components'));
  t(30000, () => cmd('/execute as @e[type=emberfall:devourer_segment] at @s run data get entity @s Pos'));
  t(31000, () => cmd('/execute as @e[type=emberfall:devourer_spawn] at @s run data get entity @s Health'));
  t(32000, () => cmd('/execute as @e[type=emberfall:devourer_brain] at @s run data get entity @s Health'));
  t(33000, () => cmd('/say cleanup check done'));

  t(36000, () => { log('TEST COMPLETE'); bot.quit(); process.exit(0); });
});
