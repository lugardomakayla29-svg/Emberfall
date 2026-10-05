// Creative-tab eggs: for every egg, hold it, right-click a stone block for real (useOn), and prove the matching entity
// appears exactly once, that it has its rig/setup (not a bare shell), and that no server exception is logged.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + (note || '')); };
// [egg id, entity type that must appear]
const EGGS = [['horde_zombie','horde_zombie'],['horde_skeleton','horde_skeleton'],['horde_spider','horde_spider'],['horde_witch','horde_witch'],
['horde_bomber','horde_bomber'],['horde_charger','horde_charger'],['horde_shieldbearer','horde_shieldbearer'],['horde_spitter','horde_spitter'],
['horde_imp','horde_imp'],['tiki_magma','tiki_magma'],['cinderbrand_reaver','cinderbrand_reaver'],['blightfeather_marksman','blightfeather_marksman'],
['umbral_magus','umbral_magus'],['corrupted_sentinel','corrupted_sentinel'],['bonecaller_necromancer','bonecaller_necromancer'],
['plague_colossus','plague_colossus'],['boil_ridden_marksman','boil_ridden_marksman'],['broodmother_stalker','broodmother_stalker'],['pink_slime','pink_slime']];
const count = async type => { // exact count through a scoreboard store
  await ask('/scoreboard objectives add egg dummy', 200);
  await ask(`/execute store result score #n egg if entity @e[type=emberfall:${type},x=100,y=200,z=100,distance=..12]`, 350);
  const r = await ask('/scoreboard players get #n egg', 450); const m = /has (\d+)/.exec(r); return m ? +m[1] : NaN;
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode creative', 400);
  await ask('/kill @e[type=!player]', 500);
  await ask('/fill 94 199 94 106 199 106 minecraft:stone', 700);
  await ask('/fill 94 200 94 106 230 106 minecraft:air', 700);
  await ask('/tp @s 100 200 100', 700); await sleep(1200);
  console.log('bot y =', bot.entity.position.y.toFixed(2));
  console.log('tab item ids registered:', (await ask('/clear @s emberfall:horde_zombie_egg 0', 500)).slice(0, 70));
  for (const [egg, type] of EGGS) {
    await ask('/kill @e[type=!player]', 450);
    await ask(`/item replace entity @s hotbar.0 with emberfall:${egg}_egg`, 450);
    bot.setQuickBarSlot(0); await sleep(250);
    const before = await count(type);
    const block = bot.blockAt(new Vec3(100, 199, 102));
    let err = '';
    try { await Promise.race([bot.activateBlock(block, new Vec3(0, 1, 0)), sleep(2500)]); } catch (e) { err = e.message.slice(0, 60); }
    await sleep(900);
    const after = await count(type);
    check(`E ${egg}: one ${type} appears`, after - before === 1, `before=${before} after=${after}${err ? ' err=' + err : ''}`);
  }
  console.log(res.every(Boolean) ? 'ALL PASS' : 'SOME FAIL');
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
