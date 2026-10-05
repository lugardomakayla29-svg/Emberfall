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
async function query(c, regex) {
  cmd(c); await sleep(120);
  const m = regex.exec(lastMsg || '');
  return m ? m[1] : null;
}

bot.once('spawn', async () => {
  cmd('/gamemode creative'); await sleep(300);
  cmd('/execute in emberfall:expedition run tp EmberTester 0 100 0'); await sleep(400);
  cmd('/kill @e[type=emberfall:tiki_magma]'); await sleep(200);
  cmd('/kill @e[type=emberfall:tiki_segment]'); await sleep(200);
  cmd('/kill @e[type=item_display]'); await sleep(150);
  cmd('/kill @e[type=block_display]'); await sleep(150);
  cmd('/tp @s 6.5 104 0.5'); await sleep(300); // stay well away from the spawn point throughout

  cmd('/execute at @s run tp @s 0.5 100 0.5'); await sleep(150);
  cmd('/emberfall spawnveteran tiki_magma'); await sleep(80);
  cmd('/tp @s 6.5 104 0.5'); await sleep(150); // retreat immediately so the overlap-damage quirk can't confound this reading

  log('=== confirming health stable now that player has backed off ===');
  for (let i = 0; i < 3; i++) {
    log(`health: ${await query('/data get entity @e[type=emberfall:tiki_magma,limit=1] Health', /: ([\d.]+)f/)}`);
    await sleep(400);
  }

  log('=== tracking the furthest-riding (topper) segment X-position over ~2s to confirm it is swaying, not static ===');
  const xs = [];
  for (let i = 0; i < 14; i++) {
    const x = await query('/data get entity @e[type=emberfall:tiki_segment,sort=furthest,limit=1] Pos[0]', /: ([-\d.]+)d/);
    xs.push(x);
    log(`topper-ish segment X: ${x}`);
    await sleep(150);
  }
  log('=== range of sampled X values: ' + (Math.max(...xs.map(Number)) - Math.min(...xs.map(Number))).toFixed(3) + ' (expect a clearly nonzero swing, not ~0) ===');
});

setTimeout(async () => {
  bot.quit();
  await sleep(300);
  process.exit(0);
}, 12000);
