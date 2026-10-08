// Rift manager, live: the shard item, spacing, open-air, idle expiry, natural event, zero entities.
// Every claim has a control: a refusal is shown beside an acceptance with the only difference being the thing under test.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0, total = 0; const R = (n, ok, extra = '') => { total++; console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
let spawns = []; let counting = false;
bot._client.on('packet', (d, m) => { if (counting && m.name === 'spawn_entity') spawns.push({ type: d.type, x: d.x, y: d.y, z: d.z }); });
const state = async () => { const r = await ask('/emberfall rift state', 600); const m = /RIFT active=(\d+) entities=(\d+) rifts=(\d+)/.exec(r); return m ? { active: +m[1], entities: +m[2], rifts: +m[3], raw: r } : { rifts: -1, raw: r }; };
const shards = async () => { const r = await ask('/clear @s emberfall:rift_shard 0', 500); const m = /(\d+) (?:item|matching)/.exec(r) || /Found (\d+)/.exec(r); return m ? +m[1] : (/No items/.test(r) ? 0 : -1); };
const useShard = async (pos) => { const from = lines.length; bot.setQuickBarSlot(0); await sleep(200); const b = bot.blockAt(pos); try { await Promise.race([bot.activateBlock(b, new Vec3(0, 1, 0)), sleep(2500)]); } catch (e) {} await sleep(700); return lines.slice(from).join(' | '); };
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamerule spawn_mobs false', 150); await ask('/gamerule mob_spawning false', 150); await ask('/difficulty peaceful', 150);
  await ask('/gamemode survival', 300); await ask('/emberfall rift clear', 200); await ask('/emberfall rift closeall', 200);
  await ask('/fill 94 199 94 106 199 106 minecraft:stone', 700); await ask('/fill 94 200 94 106 235 106 minecraft:air', 700);
  await ask('/tp @s 100 200 100', 700); await sleep(1200);
  await ask('/kill @e[type=!player]', 500); await sleep(2000);
  // ---- A: the command path and the manager list
  const s0 = await state(); R('A0 no Rifts at the start', s0.rifts === 0, s0.raw.slice(-50));
  // the debug command opens 6 blocks ahead, 5 up, in the bot's look direction
  const o1 = await ask('/emberfall rift open', 700); R('A1 the first Rift opens', /RIFT opening events=(\d+)/.test(o1), o1.slice(0, 60));
  await sleep(300); const s1 = await state(); R('A2 it is listed', s1.rifts === 1, s1.raw.slice(-70));
  R('A3 not yet enterable at age ~1 s (opening takes 100 ticks)', /open=false/.test(s1.raw), s1.raw.slice(-60));
  const o2 = await ask('/emberfall rift open', 600);
  R('A4 a second Rift at the same spot is refused for spacing', /refused: too close to another Rift/.test(o2), o2.slice(0, 80));
  await sleep(5500); const s2 = await state(); R('A5 after 5 s the Rift is enterable', /open=true/.test(s2.raw) && s2.rifts === 1, s2.raw.slice(-60));
  // ---- B: close, then the list empties after the closing show, then a new one is accepted (control for A4)
  const c1 = await ask('/emberfall rift close', 500); R('B1 close starts the closing show', /RIFT closing events=(\d+)/.test(c1), c1.slice(0, 60));
  await sleep(2500); const s3 = await state(); R('B2 the closed Rift is gone from the list', s3.rifts === 0, s3.raw.slice(-50));
  const o3 = await ask('/emberfall rift open', 600); R('B3 CONTROL: with no Rift near, opening works again', /RIFT opening events/.test(o3), o3.slice(0, 60));
  await ask('/emberfall rift close', 400); await sleep(2500);
  // ---- C: the shard item. Survival; the platform at y 199 is open air all around.
  await ask('/tp @s 100 200 100', 500); await sleep(800);
  await ask('/item replace entity @s hotbar.0 with emberfall:rift_shard 3', 500);
  const n0 = await shards(); R('C0 the bot holds 3 shards', n0 === 3, `n=${n0}`);
  spawns = []; counting = true;
  await useShard(new Vec3(100, 199, 102));
  const n1 = await shards(); const sC = await state();
  R('C1 using a shard opens a Rift', sC.rifts === 1, sC.raw.slice(-60));
  R('C2 survival uses up exactly one shard', n1 === 2, `${n0} -> ${n1}`);
  await sleep(500);
  const c3text = await useShard(new Vec3(100, 199, 102));
  const n2 = await shards(); const sD = await state();
  R('C3 a second use next to it is refused and keeps the shard', sD.rifts === 1 && n2 === 2, `rifts=${sD.rifts} shards=${n2}`);
  // READ FROM HANDLER, NOT SEEN ARRIVING (no live run yet): RiftShardItem sends "The shard cannot open a Rift here: <reason>." with overlay=true; mineflayer 4.39.0
  // systemChat emits 'message' for that packet too (chat.js:133-142), so the existing lines capture should hold it. The count assert above stays: this one adds the WHY.
  R('C3b the refusal TEXT says too close to another Rift (not just "nothing happened")', /shard cannot open a Rift here: too close to another Rift/.test(c3text), `text=${JSON.stringify(c3text.slice(0, 120))}`);
  await sleep(5500); counting = false;
  R('C4 ZERO entities spawned for the Rift (whole window, any type)', spawns.length === 0, `spawns=${JSON.stringify(spawns.slice(0, 3))}`);
  await ask('/emberfall rift closeall', 400); await sleep(2500);
  const sE = await state(); R('C5 closeall empties the list', sE.rifts === 0, sE.raw.slice(-40));
  await useShard(new Vec3(100, 199, 102)); const n3 = await shards(); const sF = await state();
  R('C6 CONTROL: with the first Rift gone, a shard works again (one more used)', sF.rifts === 1 && n3 === 1, `rifts=${sF.rifts} shards=${n3}`);
  await ask('/emberfall rift closeall', 400); await sleep(2500);
  // ---- D: open air. /fill is capped at 32768 blocks, so every fill here is well under it (23x15x23 = 7935). The Rift's box is 15 wide and 13 high and
  // hangs ~6.5 above the clicked face; bury x 89..111, y 249..263, z 89..111 in stone with ONE 1x2x1 pocket for the bot at (100, 250..251, 100).
  const fillOk = async (cmd) => { const r = await ask(cmd, 1500); return /Successfully filled|filled \d+/i.test(r) ? true : r.slice(0, 80); };
  const bury = await fillOk('/fill 89 249 89 111 263 111 minecraft:stone'); R('D-setup the burying fill was ACCEPTED by the server (not over the block cap)', bury === true, String(bury));
  await ask('/fill 100 250 100 100 251 100 minecraft:air', 700);
  await ask('/tp @s 100 250 100', 700); await sleep(1500);
  await ask('/item replace entity @s hotbar.0 with emberfall:rift_shard 2', 500);
  const d0 = await shards(); R('D0 the bot is alive and holds 2 shards before the buried test', d0 === 2, `n=${d0}`);
  const d1text = await useShard(new Vec3(100, 249, 100)); const d1 = await shards(); const sG = await state();
  R('D1 a shard used with the Rift buried in solid rock is refused and kept', sG.rifts === 0 && d1 === d0, `rifts=${sG.rifts} shards ${d0}->${d1}`);
  // READ FROM HANDLER, NOT SEEN ARRIVING. Spacing and the dimension guard are checked before open air, so the count assert alone cannot tell WHY it was refused.
  R('D1b the refusal TEXT says no open air', /shard cannot open a Rift here: no open air/.test(d1text), `text=${JSON.stringify(d1text.slice(0, 120))}`);
  // control: hollow out the box above the platform (also under the cap); the click block, the shard and the bot are unchanged, only the air differs
  const hollow = await fillOk('/fill 89 250 89 111 263 111 minecraft:air'); R('D-setup the hollowing fill was ACCEPTED', hollow === true, String(hollow));
  await ask('/tp @s 100 250 100', 700); await sleep(1500);
  await useShard(new Vec3(100, 249, 100)); const d2 = await shards(); const sG2 = await state();
  R('D2 CONTROL: with the box hollowed out the same shard opens a Rift and is used', sG2.rifts === 1 && d2 === d0 - 1, `rifts=${sG2.rifts} shards ${d0}->${d2}`);
  await ask('/emberfall rift closeall', 400); await sleep(2500);
  await ask('/tp @s 100 200 100', 600); await sleep(800);   // leave the platform BEFORE it is removed, so the bot does not fall
  await fillOk('/fill 89 249 89 111 263 111 minecraft:air');
  // ---- E: creative keeps the shard
  await ask('/fill 94 199 94 106 199 106 minecraft:stone', 700); await ask('/tp @s 100 200 100', 600); await sleep(800);
  await ask('/gamemode creative', 400); await ask('/item replace entity @s hotbar.0 with emberfall:rift_shard 2', 500);
  const e0 = await shards(); await useShard(new Vec3(100, 199, 102)); const e1 = await shards(); const sH = await state();
  R('E1 creative opens a Rift and the player keeps the shard (vanilla restores a creative stack, so this does NOT test the instabuild guard: measured, see PR)', sH.rifts === 1 && e1 === e0, `rifts=${sH.rifts} shards ${e0}->${e1}`);
  await ask('/emberfall rift closeall', 400); await sleep(2500); await ask('/gamemode survival', 300);
  // ---- F: natural event, forced by the debug command (the 1-in-30 timer is proven pure)
  const f0 = (await ask('/emberfall rift natural', 900)); const sI = await state();
  R('F1 a natural Rift opens', /natural opened/.test(f0) && sI.rifts === 1, f0.slice(0, 50));
  R('F2 the player got the natural chat line (bot has no lang file, so the KEY arrives)', lines.slice(-8).some(l => /emberfall\.rift\.natural/.test(l)), '');
  const m = /\[(-?\d+),(-?\d+),(-?\d+) f(\d)/.exec(sI.raw);
  if (m) { const p = bot.entity.position; const d = Math.hypot(+m[1] - p.x, +m[3] - p.z); R('F3 it is 16 to 32 blocks away', d >= 15.5 && d <= 32.5, `d=${d.toFixed(1)}`);
    const nrm = [[0, 1], [-1, 0], [0, -1], [1, 0]][+m[4]]; const dot = nrm[0] * (p.x - +m[1]) + nrm[1] * (p.z - +m[3]); R('F4 it faces the player', dot > 0, `facing=${m[4]} dot=${dot.toFixed(1)}`); }
  else { R('F3 it is 16 to 32 blocks away', false, 'no position'); R('F4 it faces the player', false, 'no position'); }
  await ask('/emberfall rift closeall', 400); await sleep(2500);
  // ---- G: idle expiry is proven by the pure RiftRules check; here only that a Rift with nobody waiting is still there after 20 s
  await ask('/emberfall rift open', 600); await sleep(20000); const sJ = await state(); R('G1 an idle Rift is still open after 20 s (the 10 minute timer has not run out)', sJ.rifts === 1, sJ.raw.slice(-40));
  await ask('/emberfall rift closeall', 400); await sleep(2500); const sK = await state(); R('G2 nothing left at the end', sK.rifts === 0, sK.raw.slice(-40));
  console.log(fails === 0 ? `ALL PASS (${total})` : `FAILED ${fails} of ${total}`); bot.quit(); process.exit(fails ? 1 : 0);
});
