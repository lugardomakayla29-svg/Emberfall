const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
function log(m) { console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`); }
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
function wait(ms) { return new Promise(r => setTimeout(r, ms)); }
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

async function run() {
  await new Promise((resolve) => bot.once('spawn', resolve));
  log('spawned dim=' + bot.game.dimension);
  cmd('/gamemode creative'); await wait(500);
  cmd('/tp @s 900 106 900'); await wait(1200);
  cmd('/fill 895 100 895 915 105 915 minecraft:stone'); await wait(800);
  cmd('/fill 896 101 896 914 104 914 minecraft:air'); await wait(800);
  cmd('/setblock 900 101 900 emberfall:marker'); await wait(400);
  cmd('/data merge block 900 101 900 {MarkerType:"spawn_point"}'); await wait(400);
  cmd('/setblock 905 101 905 emberfall:marker'); await wait(400);
  cmd('/data merge block 905 101 905 {MarkerType:"player_entry"}'); await wait(400);
  cmd('/emberfall capture jointest2 895 100 895 915 105 915'); await wait(1000);
  cmd('/emberfall paste jointest2'); await wait(1500);
  cmd('/emberfall join 0 EmberTester'); await wait(2000);
  log('position: ' + JSON.stringify(bot.entity.position) + ' dim=' + bot.game.dimension);
  bot.quit(); process.exit(0);
}
run().catch(e => { log('FATAL: ' + e); process.exit(1); });
