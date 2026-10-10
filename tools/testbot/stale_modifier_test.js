// Proves the stale run-modifier fix on a live server. The saved player file already carries tome_swift_boots_1_<uuid> (a killed server left it).
// OLD jar: granting swift_boots (stack 1) rebuilds the same id and the server throws "Modifier is already applied". NEW jar: join strips it, grant works.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
let dead = false; bot.on('end', () => { dead = true; });
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, d) => { if (!ok) fails++; console.log((ok ? 'PASS ' : 'FAIL ') + n + ' :: ' + d); };
const speed = async () => { const r = await ask('/attribute @s minecraft:movement_speed modifier value get emberfall:tome_swift_boots_1_d3eeca9b-f438-32aa-95be-84060e2a1371', 600); return r; };
bot.once('spawn', async () => {
  await sleep(4000);
  const before = await speed();
  console.log('STALE-CHECK after join: ' + before.slice(-150));
  const stripped = /No modifier|not found|Can't find|no such/i.test(before) || !/\d/.test(before.replace(/[a-f0-9-]{36}/g, ''));
  R('M1 after join the stale tome modifier is gone (or never reloaded)', stripped, before.slice(-120));
  const g = await ask('/emberfall granttome EmberTester swift_boots', 900);
  console.log('GRANT: ' + g.slice(-160));
  await sleep(1500);
  R('M2 granting the same tome at stack 1 works and the bot is still connected', !dead && /Granted/i.test(g), g.slice(-120));
  const g2 = await ask('/emberfall granttome EmberTester swift_boots', 900);
  R('M3 a second stack (stack 2) also works', !dead && /stack 2/i.test(g2), g2.slice(-100));
  console.log(fails === 0 ? 'ALL PASS' : 'FAILED ' + fails);
  process.exit(fails === 0 ? 0 : 1);
});
setTimeout(() => { console.log('TIMEOUT dead=' + dead); process.exit(2); }, 60000);
