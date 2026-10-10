// Fodder HEAD live test. Judge = SERVER REPLIES only (/data). It proves the server put the owner's skull on each fodder type, with the right texture,
// and that the plain Horde Zombie wears none. Whether the head LOOKS right on a real client is UNSEEN.
// Needs a server started with -Demberfall.testMode=true.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
// Expected texture hashes are typed from the owner's /give commands, NOT read back from the code.
const WANT = {
  horde_shieldbearer: 'aef904c66f4cd357eb80a1e405783f521d284503435aede603c89db15e685709',
  horde_charger: '7ba757aa3bc19212eee44c56bd077eccf99febe0ff6f6cc85ae12d9d215da064',
  horde_spitter: '2523ea773e01ccc5c0f7cadd3fb88b2b751f31c27899ef026e647e872cae44e5',
};
const b64 = s => Buffer.from(s, 'base64').toString();
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/kill @e[type=!player]', 600); await sleep(1000);
  await ask('/gamemode creative'); await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  await ask('/emberfall wavestop 0', 400);
  for (const name of Object.keys(WANT)) {
    await ask('/kill @e[type=!player]', 500); await sleep(500);
    const r0 = await ask(`/emberfall spawnveteran ${name}`, 900);
    check(`F0 ${name}: the spawn command worked`, /Spawned/.test(r0), r0.slice(0, 80));
    const type = 'emberfall:' + name;
    // The whole head slot as NBT text; the skull's profile holds the base64 texture value.
    const r = await ask(`/data get entity @e[type=${type},limit=1] equipment.head`, 700);
    check(`F1 ${name}: wears a player head`, /minecraft:player_head/.test(r), r.slice(0, 120));
    const m = /value:\s*"([A-Za-z0-9+/=]+)"/.exec(r);
    const url = m ? (/"url"\s*:\s*"([^"]+)"/.exec(b64(m[1])) || [])[1] : undefined;
    check(`F2 ${name}: the head texture is the owner's chosen one`, !!url && url.endsWith('/' + WANT[name]), String(url).slice(-30));
    const d = await ask(`/data get entity @e[type=${type},limit=1] drop_chances`, 600);
    check(`F3 ${name}: the head drop chance is 0 (never loot)`, /head:\s*0(\.0)?f/.test(d), d.slice(0, 120));
  }
  // The plain Horde Zombie must stay plain.
  await ask('/kill @e[type=!player]', 500); await sleep(500);
  await ask('/emberfall spawnveteran horde_zombie', 900);
  const alive = await ask('/data get entity @e[type=emberfall:horde_zombie,limit=1] Health', 600);
  check('F4a the Horde Zombie really exists (so F4b is not passing on an empty world)', /Health|entity data/.test(alive) && !/No entity was found/.test(alive), alive.slice(0, 100));
  const z = await ask('/data get entity @e[type=emberfall:horde_zombie,limit=1] equipment.head', 700);
  check('F4b the Horde Zombie wears no head', !/player_head/.test(z), z.slice(0, 100));
  console.log('fodder_heads_test | fails', fails);
  console.log(fails === 0 ? 'ALL PASS' : 'FAILED');
  bot.quit(); process.exit(fails === 0 ? 0 : 1);
});
