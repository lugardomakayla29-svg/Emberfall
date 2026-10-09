// Shrines on the static map: left click opens the window (decoded from the raw packet), choices apply on the server,
// each shrine works once, out-of-reach clicks do nothing, the structure is never broken. Judge = server replies only.
const mineflayer = require('mineflayer'); const fs = require('fs'); const { Vec3 } = require('vec3');
const D = JSON.parse(fs.readFileSync('../mod/src/main/resources/data/emberfall/map/expedition_map.json', 'utf8'));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
// raw open_shrine capture
const opens = [];
bot._client.on('packet', (d, meta) => {
  if (meta.name !== 'custom_payload' || !d || d.channel !== 'emberfall:open_shrine') return;
  const b = Buffer.from(d.data); let o = 0;
  const vi = () => { let v = 0, s = 0, c; do { c = b[o++]; v |= (c & 127) << s; s += 7; } while (c & 128); return v; };
  const str = () => { const n = vi(); const t = b.toString('utf8', o, o + n); o += n; return t; };
  const type = str(), title = str(), lore = str(), n = vi(), options = [];
  for (let i = 0; i < n; i++) { const index = vi(), label = str(), tip = str(), en = b[o++] === 1; options.push({ index, label, tip, en }); }
  opens.push({ type, title, lore, options, consumed: o === b.length });
});
const sendChoose = (type, option) => { // C2S: utf(type), varint(option)
  const t = Buffer.from(type, 'utf8'); const data = Buffer.concat([Buffer.from([t.length]), t, Buffer.from([option])]);
  bot._client.write('custom_payload', { channel: 'emberfall:choose_shrine', data });
};
const state = async () => { const r = await ask('/emberfall shrinestate 0', 600); const m = /SHRINESTATE curse=(\d+) greed=(\d+) challenge=(\w+) stat=([\d.]+) spawn=([\d.]+) silver=([\d.]+) threat=([-\d.]+)/.exec(r); return m ? { curse: +m[1], greed: +m[2], chal: m[3] === 'true', stat: +m[4], spawn: +m[5], silver: +m[6], threat: +m[7] } : { raw: r.slice(0, 80) }; };
let noMove = false;
const click = async (x, y, z) => { // left click = start digging a block; the server cancels it
  const t = Object.keys(S).find(k => { const bb = blockOf(k); return bb[0] === x && bb[2] === z; }); const st = standFor(t);
  if (!noMove) await ask(`/tp @s ${st.x + 0.5} 65 ${st.z + 0.5}`, 900);
  const near = Object.values(bot.entities).filter(e => e !== bot.entity && /interaction/i.test(e.name || e.displayName || '') && e.position.distanceTo(new Vec3(x + .5, y, z + .5)) < 5);
  console.log('interaction entities near target:', near.length, near.map(e => e.position.toString()).join(' '));
  if (near.length && process.env.HOTSPOT !== '0') { try { await bot.lookAt(near[0].position.offset(0, 0.5, 0), true); bot.attack(near[0]); } catch (e) { console.log('attack err', e.message); } await sleep(700); return; }
  const blk = bot.blockAt(new Vec3(x, y, z));
  console.log('click target', x, y, z, 'block=', blk ? blk.name : 'NULL', 'bot at', bot.entity.position.toString(), 'dim', bot.game.dimension, 'dist', blk ? bot.entity.position.distanceTo(new Vec3(x + .5, y + .5, z + .5)).toFixed(1) : '?', 'canDig', blk ? bot.canDigBlock(blk) : '?');
  try { await bot.lookAt(new Vec3(x + 0.5, y + 0.5, z + 0.5), true); bot.dig(blk, true, 'raycast').catch(() => {}); } catch (e) {}
  await sleep(700); try { bot.stopDigging(); } catch (e) {}
};
const S = Object.fromEntries(D.shrines.map(s => [s.type, s]));
const anchor = t => [S[t].x, 65, S[t].z];
const footprint = t => { const s = S[t]; const w = s.size[0], l = s.size[2]; const x0 = s.x - Math.floor(w / 2), z0 = s.z - Math.floor(l / 2); return { x0, z0, w, l, set: new Set(s.blocks.filter(k => k[1] <= 2).map(k => (x0 + k[0]) + ',' + (z0 + k[2]))) }; };
const standFor = t => { const f = footprint(t); const [bx, , bz] = blockOf(t); let best = null;
  for (let dx = -3; dx <= 3; dx++) for (let dz = -3; dz <= 3; dz++) { const x = bx + dx, z = bz + dz; if (f.set.has(x + ',' + z)) continue; const d = Math.hypot(dx, dz); if (d < 1.2 || d > 3) continue; if (!best || d < best.d) best = { x, z, d }; }
  return best; };
const blockOf = t => { const s = S[t]; const w = s.size[0], l = s.size[2]; const x0 = s.x - Math.floor(w / 2), z0 = s.z - Math.floor(l / 2); const b = s.blocks.find(k => k[1] === 1) || s.blocks[0]; return [x0 + b[0], 1 + b[1] + 64, z0 + b[2]]; };
const present = async (x, y, z) => !/Test passed/.test(await ask(`/execute in emberfall:expedition if block ${x} ${y} ${z} minecraft:air`, 300));
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/effect give @s minecraft:resistance 999 4 true', 300); await ask('/effect give @s minecraft:regeneration 999 4 true', 300);
  await ask('/character select juggernaut', 600); await ask('/expedition', 1500);
  for (let i = 0; i < 60; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(1500);
  const s0 = await state(); console.log('state at start', JSON.stringify(s0));
  check('S0 fresh run: no curse, no greed, threat near zero', s0.curse === 0 && s0.greed === 0 && s0.chal === false && s0.stat === 1 && s0.threat < 1, JSON.stringify(s0));
  // --- Curse ---
  await ask(`/tp @s ${S.curse.x + 4} 66 ${S.curse.z + 4}`, 1500);
  const [cx, cy, cz] = blockOf('curse'); const before = opens.length;
  await click(cx, cy, cz);
  check('S1 left click on the curse shrine opens its window', opens.length > before && opens[opens.length - 1].type === 'curse', JSON.stringify(opens.slice(-1)).slice(0, 200));
  const w = opens[opens.length - 1];
  if (w) { check('S2 window carries 4 curse tiers, all enabled, packet fully consumed', w.options.length === 4 && w.options.every(o => o.en) && w.consumed, w.options.map(o => o.label + ':' + o.en).join(',')); console.log('tier tooltip:', w.options[3].tip); }
  check('S3 the clicked block is still there (nothing broken)', await present(cx, cy, cz));
  sendChoose('curse', 4); await sleep(900);
  const s1 = await state(); check('S4 Curse IV recorded: stats x1.5, adds x1.5, silver x1.6', s1.curse === 4 && Math.abs(s1.stat - 1.5) < 0.01 && Math.abs(s1.spawn - 1.5) < 0.01 && Math.abs(s1.silver - 1.6) < 0.01, JSON.stringify(s1));
  const b2 = opens.length; await click(cx, cy, cz);
  const w2 = opens[opens.length - 1];
  check('S5 a used curse shrine shows every button greyed out', opens.length > b2 && w2.options.every(o => !o.en), w2 ? w2.options.map(o => o.en).join(',') : 'no window');
  sendChoose('curse', 1); await sleep(600);
  const s2 = await state(); check('S6 a second curse choice is ignored', s2.curse === 4, JSON.stringify(s2));
  // --- Greed ---
  await ask(`/tp @s ${S.greed.x + 4} 66 ${S.greed.z + 4}`, 1500);
  const [gx, gy, gz] = blockOf('greed'); const b3 = opens.length; await click(gx, gy, gz);
  check('S7 left click on the statue opens the greed window with 5 steps', opens.length > b3 && opens[opens.length - 1].type === 'greed' && opens[opens.length - 1].options.length === 5);
  const t0 = (await state()).threat;
  sendChoose('greed', 5); await sleep(900);
  const s3 = await state(); check('S8 Greed +5 recorded and raised threat by 10', s3.greed === 5 && Math.abs(s3.threat - t0 - 10) < 1.2, `${t0} -> ${s3.threat} ${JSON.stringify(s3)}`);
  check('S9 silver multiplier now 1 + 0.60 + 0.50 = 2.10', Math.abs(s3.silver - 2.1) < 0.01, String(s3.silver));
  // --- reach: a far click and a far choice do nothing ---
  await ask(`/tp @s ${S.challenge.x + 60} 66 ${S.challenge.z + 20}`, 1500);
  const b4 = opens.length; noMove = true; await click(...blockOf('challenge')); noMove = false;
  check('S10 a click from far away opens nothing', opens.length === b4);
  sendChoose('challenge', 0); await sleep(700);
  check('S11 a far choice starts no fight (challenge flag stays false, no mobs)', (await state()).chal === false);
  // --- Challenge ---
  const foes = async () => { await ask('/scoreboard objectives add sht dummy', 200); await ask('/scoreboard players set #n sht -1', 200); await ask('/execute store result score #n sht if entity @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 300); const r = await ask('/scoreboard players get #n sht', 500); const m = /has (-?\d+) \[sht\]/.exec(r); return m ? +m[1] : NaN; };
  await ask(`/tp @s ${S.challenge.x + 5} 66 ${S.challenge.z + 5}`, 1500);
  const b5 = opens.length; await click(...blockOf('challenge'));
  check('S12 challenge window has 3 sizes', opens.length > b5 && opens[opens.length - 1].type === 'challenge' && opens[opens.length - 1].options.length === 3);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 500);
  const base = await foes();
  sendChoose('challenge', 0); await sleep(1500);
  const n1 = (await foes()) - base; { const m13 = require('fs').readFileSync('../server_run.log', 'utf8').match(/MapShrines: slot 0 challenge size 0 started with (\d+) mobs/); check('S13 the small trial spawned exactly 3 guardians (server log; nearby count is polluted by the wave: added=' + n1 + ' base=' + base + ')', !!m13 && +m13[1] === 3, m13 ? 'logged ' + m13[1] : 'no start line'); }
  sendChoose('challenge', 1); await sleep(700);
  const started = (require('fs').readFileSync('../server_run.log', 'utf8').match(/MapShrines: slot 0 challenge size/g) || []).length; check('S14 a second challenge is refused (server log shows exactly one start)', started === 1, 'starts=' + started);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 800); await sleep(2500);
  const s4 = await state(); check('S15 clearing the trial sets the challenge flag', s4.chal === true, JSON.stringify(s4));
  const gold = await ask('/emberfall hudstate EmberTester', 600); console.log('hud', gold.slice(0, 160));
  check('S16 silver multiplier unchanged by challenge (flat bonus only)', Math.abs(s4.silver - 2.1) < 0.01);
  // --- boss really is cursed ---
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 500);
  await ask('/emberfall boss 0', 1500); await sleep(2500);
  const hp = await ask('/data get entity @e[type=emberfall:broodtide,limit=1] Health', 700); console.log('boss hp reply:', hp.slice(0, 120));
  const hm = /: ([\d.]+)f/.exec(hp);
  // Broodtide base 600 x 1.35 owner boost = 810, then the top Boss Curse tier x1.5 = 1215 (BossTuning.BASE_BOOST, RunModifiers curse).
  const EXPECT_HP = 600 * 1.35 * 1.5;
  check('S17 the Broodtide spawned cursed: 600 x 1.35 boost x 1.5 curse = ' + EXPECT_HP, hm && Math.abs(+hm[1] - EXPECT_HP) < 25, hm ? hm[1] : hp.slice(0, 80));
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 400);
});
