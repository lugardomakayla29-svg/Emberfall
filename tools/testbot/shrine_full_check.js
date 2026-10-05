const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let start = Date.now();
const log = (m) => console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

function checkBlocks() {
  log('pos=' + JSON.stringify(bot.entity.position) + ' dim=' + bot.game.dimension);
  const coords = [
    [15,64,15],[15,65,15],[15,66,15],
    [10,64,3],[10,65,3],[10,66,3],
    [10,64,17],[10,65,17],[10,66,17],
  ];
  for (const [x,y,z] of coords) {
    const b = bot.blockAt(new Vec3(x,y,z));
    log(`block(${x},${y},${z}) = ${b ? b.name : 'null'}`);
  }
  const entities = Object.values(bot.entities).filter(e => e.position && bot.entity.position.distanceTo(e.position) < 40 && e.type !== 'player');
  log('nearby non-player entities: ' + entities.length);
  for (const e of entities) {
    log(`  entity name=${e.name} kind=${e.kind} type=${e.type} pos=${JSON.stringify(e.position)} displayName=${JSON.stringify(e.metadata ? e.metadata[8] : undefined)}`);
  }
}

bot.once('spawn', () => {
  log('spawned at ' + JSON.stringify(bot.entity.position) + ' dim=' + bot.game.dimension);
  setTimeout(() => {
    bot.chat('/emberfall paste starter_arena');
  }, 1000);
  setTimeout(() => {
    bot.chat('/emberfall join 0 EmberTester');
  }, 3000);
  setTimeout(() => {
    checkBlocks();
  }, 5500);
  setTimeout(() => {
    process.exit(0);
  }, 8000);
});
