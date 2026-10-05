const mineflayer = require('mineflayer');

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: 25577,
  username: 'TesterBot',
  version: '1.21.11',
  auth: 'offline'
});

let startTime = Date.now();
function log(msg) {
  console.log(`[${((Date.now() - startTime) / 1000).toFixed(1)}s] ${msg}`);
}

let phase = 'none';
let lastHealth = 20;

bot.on('spawn', () => {
  log('spawned in world at ' + JSON.stringify(bot.entity.position));
  lastHealth = bot.health;
  setTimeout(() => { log('spawning boss'); bot.chat('/asctest'); }, 2000);

  // back away to build distance right after spawn
  setTimeout(() => {
    phase = 'retreat1';
    bot.setControlState('back', true);
  }, 3000);
  setTimeout(() => {
    bot.setControlState('back', false);
    phase = 'watched';
    log('=== PHASE: WATCHED ===');
  }, 5500);

  setTimeout(() => {
    phase = 'retreat2';
    bot.setControlState('back', true);
  }, 15500);
  setTimeout(() => {
    bot.setControlState('back', false);
    phase = 'unwatched';
    log('=== PHASE: UNWATCHED ===');
  }, 18000);

  setTimeout(() => { log('=== TEST COMPLETE ==='); process.exit(0); }, 28000);
});

bot.on('health', () => {
  if (bot.health < lastHealth) {
    log(`>>> TOOK DAMAGE: ${lastHealth} -> ${bot.health} (phase=${phase})`);
  }
  lastHealth = bot.health;
});

bot.on('message', (jsonMsg) => log('CHAT: ' + jsonMsg.toString()));
bot.on('error', (err) => log('ERROR: ' + err));
bot.on('kicked', (reason) => log('KICKED: ' + reason));
bot.on('end', () => log('disconnected'));

function findBoss() {
  return Object.values(bot.entities).find(e => e.name === 'creaking');
}

setInterval(() => {
  if (!bot.entity) return;
  const boss = findBoss();
  if (!boss) return;
  const dist = bot.entity.position.distanceTo(boss.position);
  log(`phase=${phase} dist=${dist.toFixed(2)} botHealth=${bot.health}`);
  if (phase === 'watched') {
    bot.lookAt(boss.position.offset(0, 1, 0));
  } else if (phase === 'unwatched') {
    bot.look(bot.entity.yaw + Math.PI, 0, true);
  }
}, 500);
