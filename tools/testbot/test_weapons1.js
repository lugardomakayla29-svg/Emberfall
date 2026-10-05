const mineflayer = require('mineflayer');

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: 25565,
  username: 'EmberTester',
  version: '1.21.11',
  auth: 'offline'
});

let startTime = Date.now();
function log(msg) {
  console.log(`[${((Date.now() - startTime) / 1000).toFixed(1)}s] ${msg}`);
}
function cmd(c) {
  log('CMD: ' + c);
  bot.chat(c);
}
function wait(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }

bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (err) => log('ERROR: ' + err));
bot.on('kicked', (reason) => log('KICKED: ' + JSON.stringify(reason)));
bot.on('end', (reason) => log('disconnected: ' + JSON.stringify(reason)));
bot.on('health', () => log(`health=${bot.health}`));

async function run() {
  await new Promise((resolve) => bot.once('spawn', resolve));
  log('spawned at ' + JSON.stringify(bot.entity.position));

  cmd('/gamemode creative'); await wait(600);
  cmd('/effect give EmberTester minecraft:resistance 999999 4 true'); await wait(600);
  cmd('/tp @s 700 106 700'); await wait(1500);

  cmd('/fill 695 100 695 715 105 715 minecraft:stone'); await wait(800);
  cmd('/fill 696 101 696 714 104 714 minecraft:air'); await wait(800);
  cmd('/setblock 700 101 700 emberfall:marker'); await wait(400);
  cmd('/data merge block 700 101 700 {MarkerType:"spawn_point"}'); await wait(400);
  cmd('/setblock 705 101 705 emberfall:marker'); await wait(400);
  cmd('/data merge block 705 101 705 {MarkerType:"player_entry"}'); await wait(400);

  cmd('/emberfall capture weapontest1 695 100 695 715 105 715'); await wait(1000);
  cmd('/emberfall paste weapontest1'); await wait(1500);
  cmd('/emberfall join 0 EmberTester'); await wait(500);

  log('=== PHASE: starting weapon choice grace-window auto-resolve (expect ~8s) ===');
  await wait(9000);

  log('=== PHASE: weapons command / unlock status ===');
  cmd('/emberfall weapons EmberTester'); await wait(800);

  log('=== PHASE: MELEE_SINGLE (Broadsword, default) vs a spawned elite ===');
  cmd('/emberfall spawnelite corrupted_sentinel'); await wait(500);
  await wait(15000);

  log('=== PHASE: force-equip Twin Daggers (MELEE_DUAL) and fight another ===');
  cmd('/emberfall selectweapon EmberTester twin_daggers'); await wait(500);
  cmd('/emberfall spawnelite corrupted_sentinel'); await wait(500);
  await wait(15000);

  log('=== PHASE: force-equip War Halberd (MELEE_CLEAVE) with 2 zombies nearby ===');
  cmd('/emberfall selectweapon EmberTester war_halberd'); await wait(500);
  cmd('/summon emberfall:horde_zombie 706 101 706'); await wait(300);
  cmd('/summon emberfall:horde_zombie 707 101 706'); await wait(300);
  cmd('/summon emberfall:horde_zombie 706 101 707'); await wait(300);
  await wait(10000);

  log('=== PHASE: force-equip Hunting Bow (RANGED_SINGLE), stand back, spawn a zombie ===');
  cmd('/emberfall selectweapon EmberTester hunting_bow'); await wait(500);
  cmd('/summon emberfall:horde_zombie 709 101 705'); await wait(300);
  await wait(10000);

  log('=== PHASE: force-equip Arcane Staff (RANGED_AOE) with a cluster of zombies ===');
  cmd('/emberfall selectweapon EmberTester arcane_staff'); await wait(500);
  for (let i = 0; i < 6; i++) {
    cmd(`/summon emberfall:horde_zombie ${709 + (i % 3)} 101 ${705 + Math.floor(i / 3)}`);
    await wait(200);
  }
  await wait(15000);

  log('=== PHASE: buyweapon economy check ===');
  cmd('/emberfall balance EmberTester'); await wait(500);
  cmd('/emberfall buyweapon EmberTester hunting_bow'); await wait(500); // should fail: already owned via selectweapon bypass? no - buy checks WeaponUnlocks not held item
  cmd('/emberfall weapons EmberTester'); await wait(800);

  log('=== PHASE: mid-run weapon offer at level 5 via vanilla /experience ===');
  cmd('/experience add EmberTester 4 levels'); await wait(2000);
  cmd('/experience add EmberTester 1 levels'); await wait(1500);
  log('expect a normal Tome offer around level 1-4, and a WEAPON offer at level 5');
  await wait(9500); // grace window auto-resolve for whichever screen opened at level 5

  cmd('/emberfall wavestop 0'); await wait(500);
  cmd('/emberfall leave EmberTester'); await wait(500);
  cmd('/emberfall teardown 0'); await wait(1000);

  log('=== TEST COMPLETE ===');
  bot.quit();
  process.exit(0);
}

run().catch((e) => { log('FATAL: ' + e); process.exit(1); });
process.on('SIGTERM', () => { bot.quit(); process.exit(0); });
