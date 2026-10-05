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
  t(1000, () => cmd('/tp EmberTester 500 100 500'));
  t(1500, () => cmd('/forceload add 485 485 515 515'));
  t(5000, () => cmd('/fill 490 99 490 510 99 510 minecraft:stone'));
  t(5500, () => cmd('/fill 490 100 490 510 105 510 minecraft:air'));
  t(6000, () => cmd('/fill 490 100 490 490 105 510 minecraft:stone'));
  t(6300, () => cmd('/fill 510 100 490 510 105 510 minecraft:stone'));
  t(6600, () => cmd('/fill 490 100 490 510 105 490 minecraft:stone'));
  t(6900, () => cmd('/fill 490 100 510 510 105 510 minecraft:stone'));
  t(8000, () => cmd('/setblock 495 100 495 emberfall:marker'));
  t(8300, () => cmd('/data merge block 495 100 495 {MarkerType:"spawn_point"}'));
  t(8600, () => cmd('/setblock 505 100 495 emberfall:marker'));
  t(8900, () => cmd('/data merge block 505 100 495 {MarkerType:"spawn_point"}'));
  t(9200, () => cmd('/setblock 495 100 505 emberfall:marker'));
  t(9500, () => cmd('/data merge block 495 100 505 {MarkerType:"spawn_point"}'));
  t(9800, () => cmd('/setblock 500 100 500 emberfall:marker'));
  t(10100, () => cmd('/data merge block 500 100 500 {MarkerType:"player_entry"}'));
  t(11500, () => cmd('/emberfall capture sundertest 490 99 490 510 105 510'));
  t(13500, () => cmd('/emberfall paste sundertest'));
  t(16000, () => cmd('/emberfall wavestart 0'));
  t(18000, () => cmd('/emberfall join 0 EmberTester'));
  t(20000, () => cmd('/emberfall join 0 EmberTester'));
  t(29000, () => cmd('/emberfall selectweapon EmberTester war_halberd'));
  t(30000, () => cmd('/say fighting now, watching for sunder stacks'));
  for (let i = 0; i < 20; i++) {
    t(31000 + i * 2000, () => cmd('/execute as @e[type=emberfall:horde_zombie,distance=..15,sort=nearest,limit=1] at @s run data get entity @s'));
  }
});
