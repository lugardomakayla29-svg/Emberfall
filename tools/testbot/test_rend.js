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
  t(1000, () => cmd('/tp EmberTester 100 100 100'));
  t(1500, () => cmd('/forceload add 85 85 115 115'));
  t(5000, () => cmd('/fill 90 99 90 110 99 110 minecraft:stone'));
  t(5500, () => cmd('/fill 90 100 90 110 105 110 minecraft:air'));
  t(6000, () => cmd('/fill 90 100 90 90 105 110 minecraft:stone'));
  t(6300, () => cmd('/fill 110 100 90 110 105 110 minecraft:stone'));
  t(6600, () => cmd('/fill 90 100 90 110 105 90 minecraft:stone'));
  t(6900, () => cmd('/fill 90 100 110 110 105 110 minecraft:stone'));
  t(8000, () => cmd('/setblock 95 100 95 emberfall:marker'));
  t(8300, () => cmd('/data merge block 95 100 95 {MarkerType:"spawn_point"}'));
  t(8600, () => cmd('/setblock 105 100 95 emberfall:marker'));
  t(8900, () => cmd('/data merge block 105 100 95 {MarkerType:"spawn_point"}'));
  t(9200, () => cmd('/setblock 95 100 105 emberfall:marker'));
  t(9500, () => cmd('/data merge block 95 100 105 {MarkerType:"spawn_point"}'));
  t(9800, () => cmd('/setblock 100 100 100 emberfall:marker'));
  t(10100, () => cmd('/data merge block 100 100 100 {MarkerType:"player_entry"}'));
  t(11500, () => cmd('/emberfall capture rendtest 90 99 90 110 105 110'));
  t(13500, () => cmd('/emberfall paste rendtest'));
  t(16000, () => cmd('/emberfall wavestart 0'));
  t(18000, () => cmd('/emberfall join 0 EmberTester'));
  // Starting Weapon Choice has an 8s grace-window auto-resolve (to
  // Broadsword, first offer) for a headless/AFK client - wait it out
  // BEFORE our manual override so it can't silently stomp our pick.
  t(29000, () => cmd('/emberfall selectweapon EmberTester twin_daggers'));
  t(30000, () => cmd('/say fighting now, watching for rend stacks'));
  for (let i = 0; i < 8; i++) {
    t(31000 + i * 1500, () => cmd('/execute as @e[type=emberfall:horde_zombie,distance=..15,sort=nearest,limit=1] at @s run data get entity @s ActiveEffects'));
  }
  t(45000, () => { log('TEST COMPLETE'); bot.quit(); process.exit(0); });
});
