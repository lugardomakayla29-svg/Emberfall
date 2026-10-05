const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
function log(m) { console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`); }
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
function wait(ms) { return new Promise(r => setTimeout(r, ms)); }
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));
bot.on('kicked', (r) => log('KICKED: ' + JSON.stringify(r)));

async function run() {
  await new Promise((resolve) => bot.once('spawn', resolve));
  log('spawned at ' + JSON.stringify(bot.entity.position));
  cmd('/gamemode creative'); await wait(500);
  cmd('/tp @s 800 106 800'); await wait(1200);
  cmd('/fill 795 100 795 815 105 815 minecraft:stone'); await wait(800);
  cmd('/fill 796 101 796 814 104 814 minecraft:air'); await wait(800);
  cmd('/setblock 800 101 800 emberfall:marker'); await wait(400);
  cmd('/data merge block 800 101 800 {MarkerType:"spawn_point"}'); await wait(400);
  cmd('/setblock 805 101 805 emberfall:marker'); await wait(400);
  cmd('/data merge block 805 101 805 {MarkerType:"player_entry"}'); await wait(400);
  cmd('/emberfall capture jointest1 795 100 795 815 105 815'); await wait(1000);
  cmd('/emberfall paste jointest1'); await wait(1500);
  log('=== attempt 1: join ==='); cmd('/emberfall join 0 EmberTester'); await wait(2000);
  log('position after attempt1: ' + JSON.stringify(bot.entity.position) + ' dim=' + bot.game.dimension);
  log('=== attempt 2: join (retry) ==='); cmd('/emberfall join 0 EmberTester'); await wait(2000);
  log('position after attempt2: ' + JSON.stringify(bot.entity.position) + ' dim=' + bot.game.dimension);
  bot.quit(); process.exit(0);
}
run().catch(e => { log('FATAL: ' + e); process.exit(1); });
