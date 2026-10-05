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

bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (err) => log('ERROR: ' + err));
bot.on('kicked', (reason) => log('KICKED: ' + JSON.stringify(reason)));
bot.on('end', (reason) => log('disconnected: ' + JSON.stringify(reason)));

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function run() {
  await new Promise((resolve) => bot.once('spawn', resolve));
  log('spawned at ' + JSON.stringify(bot.entity.position));

  cmd('/gamemode creative'); await wait(600);
  cmd('/give EmberTester minecraft:netherite_sword 1'); await wait(600);
  cmd('/tp @s 405 106 405'); await wait(1500);

  // Small walled platform at a fresh area, built and fully settled BEFORE any teleport happens.
  cmd('/fill 400 100 400 410 105 410 minecraft:stone'); await wait(800);
  cmd('/fill 401 101 401 409 104 409 minecraft:air'); await wait(800);

  cmd('/setblock 402 101 402 emberfall:marker'); await wait(500);
  cmd('/data merge block 402 101 402 {MarkerType:"spawn_point"}'); await wait(500);
  cmd('/setblock 408 101 402 emberfall:marker'); await wait(500);
  cmd('/data merge block 408 101 402 {MarkerType:"spawn_point"}'); await wait(500);
  cmd('/setblock 402 101 408 emberfall:marker'); await wait(500);
  cmd('/data merge block 402 101 408 {MarkerType:"spawn_point"}'); await wait(500);
  cmd('/setblock 405 101 405 emberfall:marker'); await wait(500);
  cmd('/data merge block 405 101 405 {MarkerType:"player_entry"}'); await wait(500);

  cmd('/emberfall capture wavetest3 400 100 400 410 105 410'); await wait(1000);
  cmd('/emberfall paste wavetest3'); await wait(1500);
  cmd('/emberfall wavestart 0'); await wait(800);
  cmd('/emberfall join 0 EmberTester'); await wait(1000);

  // Now purely poll wavestatus - no more overworld-position-dependent commands.
  for (let i = 0; i < 22; i++) {
    await wait(15000);
    cmd('/emberfall wavestatus 0');
  }

  await wait(3000);
  log('TEST COMPLETE');
  bot.quit();
  process.exit(0);
}

run().catch((e) => { log('FATAL: ' + e); process.exit(1); });

process.on('SIGTERM', () => { bot.quit(); process.exit(0); });
