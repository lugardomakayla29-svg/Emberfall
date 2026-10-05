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

bot.on('spawn', () => {
  log('spawned at ' + JSON.stringify(bot.entity.position));

  setTimeout(() => cmd('/gamemode creative'), 500);

  // Build a small flat platform at 300,101,300 .. 310,101,310, walled so mobs don't wander off.
  setTimeout(() => cmd('/fill 300 100 300 310 100 310 minecraft:stone'), 1000);
  setTimeout(() => cmd('/fill 300 101 300 310 104 310 minecraft:air'), 1500);
  setTimeout(() => cmd('/fill 300 101 300 310 104 300 minecraft:stone'), 2000);
  setTimeout(() => cmd('/fill 300 101 300 310 104 310 minecraft:stone keep'), 2200); // no-op safeguard skip
  setTimeout(() => cmd('/fill 300 101 300 300 104 310 minecraft:stone'), 2500);
  setTimeout(() => cmd('/fill 310 101 300 310 104 310 minecraft:stone'), 2700);
  setTimeout(() => cmd('/fill 300 101 310 310 104 310 minecraft:stone'), 2900);
  setTimeout(() => cmd('/fill 300 101 300 310 104 300 minecraft:stone'), 3100);

  // Marker blocks: 3 spawn_point + 1 player_entry, all just above the floor.
  setTimeout(() => cmd('/setblock 302 101 302 emberfall:marker'), 4000);
  setTimeout(() => cmd('/data merge block 302 101 302 {MarkerType:"spawn_point"}'), 4300);
  setTimeout(() => cmd('/setblock 308 101 302 emberfall:marker'), 4600);
  setTimeout(() => cmd('/data merge block 308 101 302 {MarkerType:"spawn_point"}'), 4900);
  setTimeout(() => cmd('/setblock 302 101 308 emberfall:marker'), 5200);
  setTimeout(() => cmd('/data merge block 302 101 308 {MarkerType:"spawn_point"}'), 5500);
  setTimeout(() => cmd('/setblock 305 101 305 emberfall:marker'), 5800);
  setTimeout(() => cmd('/data merge block 305 101 305 {MarkerType:"player_entry"}'), 6100);

  // Capture, paste, wavestart, join.
  setTimeout(() => cmd('/emberfall capture wavetest 300 101 300 310 104 310'), 7000);
  setTimeout(() => cmd('/emberfall paste wavetest'), 8000);

  // Give the paste a moment, then read chat to know slot number - but since we
  // can't easily parse it back into further commands, use a fixed slot 0
  // (first paste in a fresh server run always allocates slot 0).
  setTimeout(() => cmd('/emberfall wavestart 0'), 9000);
  setTimeout(() => cmd('/emberfall join 0 EmberTester'), 9500);
  setTimeout(() => cmd('/gamemode survival'), 10000);

  // Poll wavestatus every 15s for 2.5 minutes to watch totalElitesSpawned climb.
  for (let t = 20000; t <= 160000; t += 15000) {
    setTimeout(() => cmd('/emberfall wavestatus 0'), t);
  }

  setTimeout(() => {
    log('TEST COMPLETE - disconnecting');
    bot.quit();
    process.exit(0);
  }, 165000);
});

process.on('SIGTERM', () => { bot.quit(); process.exit(0); });
