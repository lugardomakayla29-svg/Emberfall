// Facing through a real chase: for each tier, one Tiki chases a player who keeps walking; the server logs TIKI_FACE lines
// (head yaw, body yaw, bearing to the target, same tick). This script only drives the scene; the analysis reads the log.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (c, w = 500) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
const SPAWN = {
  fodder: 'emberfall spawnegg_placeholder',
  veteran: 'emberfall spawnveteran tiki_magma',
  elite: 'emberfall spawnelite tiki_magma',
  corrupted: 'emberfall spawnelite tiki_magma_corrupted',
};
bot.once('spawn', async () => {
  await sleep(6000); await ask('/op EmberTester'); await ask('/gamemode creative');
  await ask('/fill -30 199 -30 30 199 30 minecraft:stone', 1500);
  await ask('/fill -30 200 -30 30 215 30 minecraft:air', 1500);
  await ask('/tp @s 0 200 0', 800); await sleep(1200);
  for (const tier of (process.argv[2] || 'fodder,veteran,elite,corrupted').split(',')) {
    await ask('/kill @e[type=!player]', 700); await sleep(600);
    console.log('TIER_BEGIN', tier);
    if (tier === 'fodder') {
      await ask('/item replace entity @s hotbar.0 with emberfall:tiki_magma_egg', 500);
      bot.setQuickBarSlot(0); await sleep(300);
      const { Vec3 } = require('vec3');
      await ask('/tp @s 14 200 0', 600); await sleep(800);
      try { await Promise.race([bot.activateBlock(bot.blockAt(new Vec3(14, 199, 2)), new Vec3(0, 1, 0)), sleep(2500)]); } catch (e) {}
      await sleep(800);
    } else {
      await ask('/tp @s 14 200 0', 600); await sleep(800);
      await ask(`/execute at @s positioned ~0 ~ ~2 run ${SPAWN[tier]}`, 1500);
    }
    await ask('/effect give @s minecraft:resistance 999 4 true', 300);
    // walk a square around the origin so the bearing keeps changing
    const path = [[14, 0], [14, 14], [0, 14], [-14, 14], [-14, 0], [-14, -14], [0, -14], [14, -14]];
    for (let lap = 0; lap < 2; lap++) for (const [x, z] of path) { await ask(`/tp @s ${x} 200 ${z}`, 300); await sleep(1300); }
    console.log('TIER_END', tier);
  }
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
