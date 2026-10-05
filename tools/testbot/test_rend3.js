const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('kicked', (r) => log('KICKED: ' + JSON.stringify(r)));
bot.on('end', (r) => log('disconnected: ' + JSON.stringify(r)));

let started = false;
bot.on('spawn', () => {
  if (started) return;
  started = true;
  const t = (ms, f) => setTimeout(f, ms);

  t(500, () => cmd('/gamemode creative'));
  t(700, () => cmd('/emberfall teardown 0'));
  t(1000, () => cmd('/tp EmberTester 200 100 200'));
  t(1500, () => cmd('/forceload add 185 185 215 215'));
  t(5000, () => cmd('/fill 190 99 190 210 99 210 minecraft:stone'));
  t(5500, () => cmd('/fill 190 100 190 210 105 210 minecraft:air'));
  t(6000, () => cmd('/fill 190 100 190 190 105 210 minecraft:stone'));
  t(6300, () => cmd('/fill 210 100 190 210 105 210 minecraft:stone'));
  t(6600, () => cmd('/fill 190 100 190 210 105 190 minecraft:stone'));
  t(6900, () => cmd('/fill 190 100 210 210 105 210 minecraft:stone'));
  t(8000, () => cmd('/setblock 195 100 195 emberfall:marker'));
  t(8300, () => cmd('/data merge block 195 100 195 {MarkerType:"spawn_point"}'));
  t(8600, () => cmd('/setblock 205 100 195 emberfall:marker'));
  t(8900, () => cmd('/data merge block 205 100 195 {MarkerType:"spawn_point"}'));
  t(9200, () => cmd('/setblock 195 100 205 emberfall:marker'));
  t(9500, () => cmd('/data merge block 195 100 205 {MarkerType:"spawn_point"}'));
  t(9800, () => cmd('/setblock 200 100 200 emberfall:marker'));
  t(10100, () => cmd('/data merge block 200 100 200 {MarkerType:"player_entry"}'));
  t(11500, () => cmd('/emberfall capture rendtest2 190 99 190 210 105 210'));
  t(13500, () => cmd('/emberfall paste rendtest2'));
  t(16000, () => cmd('/emberfall wavestart 0'));
  t(18000, () => cmd('/emberfall join 0 EmberTester'));
  t(20000, () => cmd('/emberfall join 0 EmberTester'));
  t(29000, () => cmd('/emberfall selectweapon EmberTester twin_daggers'));
  t(30000, () => cmd('/say fighting now, watching for rend stacks'));
  for (let i = 0; i < 40; i++) {
    t(31000 + i * 1500, () => cmd('/execute as @e[type=emberfall:horde_zombie,distance=..15,sort=nearest,limit=1] at @s run data get entity @s ActiveEffects'));
  }
  
});
