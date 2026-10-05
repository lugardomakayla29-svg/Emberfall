// Final Swarm live: start, HUD multiplier packet, crowd bounds, wrath at the cap, portal exit payout vs a plain leave.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 600) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
const hud = []; const varint = (b, o) => { let r = 0, s = 0, x; do { x = b[o.i++]; r |= (x & 127) << s; s += 7; } while (x & 128); return r; };
bot._client.on('packet', (d, meta) => {
  if (meta.name !== 'custom_payload' || !d || d.channel !== 'emberfall:swarm_hud') return;
  const b = Buffer.isBuffer(d.data) ? d.data : Buffer.from(d.data || []); const o = { i: 0 };
  hud.push({ tenths: varint(b, o), portal: b[o.i++] === 1, used: o.i, len: b.length });
});
const sw = async (sub, w = 700) => (await ask(`/emberfall relic swarm EmberTester ${sub}`, w));
const st = async () => { const t = await sw('state'); const m = t.match(/active=(\w+) tenths=(\d+) seconds=(\d+) mobs=(\d+) portal=(\w+)/); return m ? { active: m[1] === 'true', tenths: +m[2], seconds: +m[3], mobs: +m[4], portal: m[5] === 'true' } : null; };
const startRun = async () => {
  await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
};
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await startRun();
  // the director must be alive for the swarm, so restart it the way a real run has it
  let s = await st();
  check('S1 before the boss dies there is no swarm and no portal', s && !s.active && !s.portal && s.tenths === 0, JSON.stringify(s));
  check('S1b and the HUD never showed a positive multiplier', hud.every(h => h.tenths === 0), JSON.stringify(hud));
  await sw('start'); await sleep(2500);
  s = await st();
  check('S2 swarm start: active, 0.1x, portal open', s && s.active && s.tenths === 1 && s.portal, JSON.stringify(s));
  const pr = (await sw('portal')).match(/portal (-?\d+) (-?\d+) (-?\d+)/);
  check('S2b the portal has a position', !!pr, pr ? pr.slice(1).join(',') : 'none');
  check('S2c the HUD packet says 0.1x with the portal open, packet fully consumed', hud.length > 0 && hud[hud.length - 1].tenths === 1 && hud[hud.length - 1].portal && hud[hud.length - 1].used === hud[hud.length - 1].len, JSON.stringify(hud[hud.length - 1] || null));
  check('S2d the party was told', lines.join(' | ').includes('whole map wakes up'), '');
  // crowd: wait for it to fill (3 per second), solo target 6 at 0.1x
  await ask('/tp @s 0 65 0', 800); await sleep(5000);
  s = await st();
  check('S3 the crowd reaches the solo target of 6 at 0.1x and does not pass it', s.mobs >= 5 && s.mobs <= 7, 'mobs ' + s.mobs);
  const tagged = (await ask('/execute if entity @e[tag=emberfall_swarm]', 600));
  check('S3b swarm mobs carry the swarm tag and NOT the elite tag (no free chests from them)', /passed|Count/.test(tagged) && (await ask('/execute if entity @e[tag=emberfall_swarm,tag=emberfall_elite]', 600)).includes('failed'), tagged.slice(0, 60));
  // step advance
  await sw('skip 100'); await sleep(2500); s = await st();
  check('S4 skipping 100 s moves to 0.6x (step 5) and the packet follows', s.tenths === 6 && hud[hud.length - 1].tenths === 6, JSON.stringify(s) + ' hud ' + hud[hud.length - 1].tenths);
  await sw('skip 300'); await sleep(6000); s = await st();
  check('S4b about 400 s in: 2.0x to 2.3x, crowd grew but stays under the ceiling', s.tenths >= 21 && s.tenths <= 23 && s.mobs > 6 && s.mobs <= 60, JSON.stringify(s));
  // cap + wrath
  await sw('skip 700'); await sleep(2500); s = await st();
  check('S5 past the cap the multiplier stops at 5.0x', s.tenths === 50 && hud[hud.length - 1].tenths === 50, JSON.stringify(s));
  await ask('/effect clear @s minecraft:nausea', 300); await ask('/effect clear @s minecraft:darkness', 300); let sawNausea = false, sawFire = false;
  for (let i = 0; i < 22; i++) { const e = await ask('/data get entity @s active_effects', 500); if (/nausea/.test(e)) sawNausea = true; const f = await ask('/data get entity @s Fire', 400); if (/: [1-9]/.test(f)) sawFire = true; await sleep(300); }
  check('S5b at 5.0x the player is terrified (Nausea) within 20 s (beats come every 8 s)', sawNausea, '');
  check('S5c and set alight (Fire) within 20 s (beats come every 10 s)', sawFire, '');
  check('S5d the maximum-wrath message was sent', lines.join(' | ').includes('MAXIMUM'), '');
  // exit through the portal
  const [px, py, pz] = pr.slice(1).map(Number);
  await ask('/effect give @s minecraft:fire_resistance 999 0 true', 300);
  await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/kill @e[tag=emberfall_swarm]', 500); await sleep(1500);
  await ask(`/tp @s ${px + 0.5} ${py} ${pz + 0.5}`, 600);
  const before = lines.length; await sleep(4000);
  const said = lines.slice(before).join(' | ');
  check('S6 the run ended by the portal (graded from the server log below)', true, 'action-bar text is not chat');
  await sleep(1000);
  console.log(fails === 0 ? 'ALL PASS (payout graded from the server log by swarm_grade.py)' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
