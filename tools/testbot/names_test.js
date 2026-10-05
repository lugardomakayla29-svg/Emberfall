// Spawns every named Emberfall mob and reads its CustomName back from the server: it must keep its text, be bold,
// and be a per-letter gradient (several distinct colours), not plain white.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const chat = []; bot.on('message', m => chat.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
bot.on('kicked', r => console.log('KICKED', JSON.stringify(r).slice(0, 200))); bot.on('end', r => console.log('BOT_END', r));
const ask = async (c, w = 700) => { chat.length = 0; bot.chat(c); await sleep(w); return chat.join(' | '); };
let fails = 0; const check = (n, ok, d = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + (d ? '  ' + d : '')); if (!ok) fails++; };
const CASES = [
  ['horde_zombie', 'Veteran Horde Zombie'], ['horde_skeleton', 'Veteran Horde Skeleton'], ['horde_spider', 'Veteran Horde Spider'],
  ['horde_witch', 'Veteran Horde Witch'], ['cinderbrand_reaver', 'Cinderbrand Reaver'], ['blightfeather_marksman', 'Blightfeather Marksman'],
  ['umbral_magus', 'Umbral Magus'], ['corrupted_sentinel', 'Corrupted Sentinel'], ['bonecaller_necromancer', 'Bonecaller Necromancer'],
  ['plague_colossus', 'Plague Colossus'], ['boil_ridden_marksman', 'Boil-Ridden Marksman'], ['broodmother_stalker', 'Broodmother Stalker'],
  ['pink_slime', 'Pink Slime'],
];
bot.once('spawn', async () => {
  await sleep(5000); await ask('/gamemode creative'); await ask('/op EmberTester');
  await ask('/tp @s 0 80 0', 500);
  for (const [id, want] of CASES) {
    await ask('/kill @e[type=!player]', 500);
    const cmd = id.startsWith('horde_') ? 'spawnveteran' : 'spawnelite';
    const r = await ask(`/emberfall ${cmd} ${id}`, 1500);
    const d = await ask(`/data get entity @e[type=emberfall:${id},limit=1,sort=nearest,distance=..40,nbt={CustomNameVisible:1b}] CustomName`, 1200);
    const cells = [...d.matchAll(/\{color: "(#[0-9A-Fa-f]{6})", text: "(.)", bold: 1b\}/g)];
    const text = cells.map(c => c[2]).join('');
    const colours = new Set(cells.map(c => c[1].toUpperCase()));
    check(`N ${id}: text kept, bold, per letter`, text === want, `want "${want}" got "${text}" spawn=${r.slice(0, 40)}`);
    check(`N ${id}: a real gradient`, colours.size >= 4 && cells.length > 0 && cells[0][1].toUpperCase() !== cells[cells.length - 1][1].toUpperCase(), `${colours.size} distinct, ${cells[0] ? cells[0][1] : '-'} to ${cells.length ? cells[cells.length - 1][1] : '-'}`);
  }
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  bot.quit(); setTimeout(() => process.exit(0), 400);
});
