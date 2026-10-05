const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { bot.chat(c); }
let lastMsg = null;
bot.on('message', (msg) => { lastMsg = msg.toString(); log('CHAT: ' + lastMsg); });
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('end', (r) => log('DISCONNECTED: ' + JSON.stringify(r)));
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/kill @e[type=emberfall:tiki_magma]'); await sleep(200);
  cmd('/kill @e[type=emberfall:tiki_segment]'); await sleep(200);
  cmd('/kill @e[type=item_display]'); await sleep(150);
  cmd('/kill @e[type=block_display]'); await sleep(150);
  cmd('/tp @s 8.5 105 0.5'); await sleep(300); // stay off the exact spawn point throughout

  log('=== FODDER TIKI MAGMA (regular horde-filler) ===');
  cmd('/execute positioned 0 100 0 run summon emberfall:tiki_magma'); await sleep(300);
  cmd('/data get entity @e[type=emberfall:tiki_magma,limit=1] Passengers'); await sleep(300);
  cmd('/kill @e[type=emberfall:tiki_magma]'); await sleep(200);
  cmd('/kill @e[type=emberfall:tiki_segment]'); await sleep(200);
  cmd('/kill @e[type=item_display]'); await sleep(150);
  cmd('/kill @e[type=block_display]'); await sleep(150);

  log('=== ELITE TIKI (via /emberfall spawnelite) ===');
  cmd('/execute at @s run tp @s 0.5 100 0.5'); await sleep(150);
  cmd('/emberfall spawnelite tiki_magma'); await sleep(150);
  cmd('/tp @s 8.5 105 0.5'); await sleep(150);
  cmd('/data get entity @e[type=emberfall:tiki_magma,limit=1] CustomName'); await sleep(300);
  cmd('/data get entity @e[type=emberfall:tiki_magma,limit=1] Attributes'); await sleep(300);
  cmd('/data get entity @e[type=emberfall:tiki_segment]'); await sleep(400);
  cmd('/data get entity @e[type=item_display]'); await sleep(500);
  cmd('/kill @e[type=emberfall:tiki_magma]'); await sleep(200);
  cmd('/kill @e[type=emberfall:tiki_segment]'); await sleep(200);
  cmd('/kill @e[type=item_display]'); await sleep(150);
  cmd('/kill @e[type=block_display]'); await sleep(150);

  log('=== CORRUPTED TIKI (via /emberfall spawnelite tiki_magma_corrupted) ===');
  cmd('/execute at @s run tp @s 0.5 100 0.5'); await sleep(150);
  cmd('/emberfall spawnelite tiki_magma_corrupted'); await sleep(150);
  cmd('/tp @s 8.5 105 0.5'); await sleep(150);
  cmd('/data get entity @e[type=emberfall:tiki_magma,limit=1] CustomName'); await sleep(300);
  cmd('/data get entity @e[type=emberfall:tiki_magma,limit=1] Attributes'); await sleep(300);
  bot.chat('/execute if entity @e[type=emberfall:tiki_segment,limit=1]');
  await sleep(300);

  log('=== segment X-positions of the corrupted totem over ~1.5s (confirm still swaying at bigger scale) ===');
  for (let i = 0; i < 8; i++) {
    cmd('/data get entity @e[type=emberfall:tiki_segment,sort=furthest,limit=1] Pos[0]');
    await sleep(180);
  }
});

setTimeout(async () => {
  bot.quit();
  await sleep(300);
  process.exit(0);
}, 15000);
