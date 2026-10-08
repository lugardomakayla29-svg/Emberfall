// Cone geometry test. The boss aims the Fan at the NEAREST player (the run player, 3 blocks east). Two bystanders are
// pinned at 5 blocks: one 36 degrees off the aim line (inside the 40 degree half angle) and one 44 degrees off (outside, 4 degrees past the edge).
// Which players a Fan hit is read from the server's own ATKDBG log line (EmberGuardian.resolveFan, graded by attack_grade.sh), never inferred from hp (regeneration hides hits).
const mineflayer = require('mineflayer');
const mk = u => mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: u, version: '1.21.11', auth: 'offline' });
const bot = mk('EmberTester'); const inA = mk('PlainInside'); const outB = mk('PlainOutside');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const G = '@e[type=emberfall:ember_guardian,limit=1]';
const res = []; const check = (n, ok, note) => { res.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); };
bot.once('spawn', async () => {
  await sleep(8000);
  for (const u of ['PlainInside', 'PlainOutside']) { await ask(`/gamemode survival ${u}`, 250); await ask(`/effect give ${u} minecraft:resistance 999 4 true`, 250); await ask(`/effect give ${u} minecraft:regeneration 999 4 true`, 250); }
  await ask('/kill @e[type=!player]', 600); await ask('/kill @e[type=minecraft:item_display]', 600); await sleep(1200);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 250); await ask('/effect give @s minecraft:regeneration 999 4 true', 250);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  // The map builds async (about 32 s). Poll the dimension so the boss is spawned in the RUN, not in the hub, and assert it (R0).
  let inRun = false; for (let i = 0; i < 80 && !inRun; i++) { await sleep(1500); inRun = /entity data: "emberfall:expedition/.test(await ask('/data get entity @s Dimension', 400)); }
  check('R0 the bot is inside a run (nothing below means anything in the hub)', inRun, `inRun=${inRun}`);
  await sleep(1500);
  await ask('/emberfall boss 0', 900);
  await ask('/emberfall wavestop 0', 300);
  // Pin everyone every 250 ms in the background from the FIRST tick after the boss exists (so no attack fires before they are placed).
  // Aim line = +x from the boss. Inside bystander at +36 degrees, outside at -44 degrees, both 5 blocks out.
  const c = (deg, r) => [(Math.cos(deg * Math.PI / 180) * r).toFixed(3), (Math.sin(deg * Math.PI / 180) * r).toFixed(3)];
  const [ix, iz] = c(36, 5), [ox, oz] = c(-44, 5);
  let pin = true;
  (async () => { while (pin) {
    await ask(`/execute as ${G} at @s run tp EmberTester ~3 ~ ~`, 60);
    await ask(`/execute as ${G} at @s run tp PlainInside ~${ix} ~ ~${iz}`, 60);
    await ask(`/execute as ${G} at @s run tp PlainOutside ~${ox} ~ ~${oz}`, 60);
    await sleep(120); } })();
  await sleep(16000); pin = false; await sleep(600);
  // The verdict on the cone is read from the server's ATKDBG lines by attack_grade.sh (never from hp: regeneration hides hits).
  console.log('ATTACK_DONE (cone verdict is graded from the server log by attack_grade.sh, not here)');
  const bad = res.filter(x => !x).length; console.log(bad === 0 ? 'ALL PASS (' + res.length + ', plus the server-log grade)' : 'SOME FAIL ' + bad);
  bot.quit(); inA.quit(); outB.quit();
  setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
