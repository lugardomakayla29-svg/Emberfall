// V11: inside an ACTIVE run, which planted entities survive? (see docs/audit/N1_stayer_ids.md). Row 4 of that doc swaps the first summon for emberfall:horde_zombie.
// Edited after the runs for readability only (the sleep expression); the planted entities and the polled tags are unchanged. The edited form was not rerun.shouldPurge: plain husk dies, named husk lives, armor_stand lives.
const mineflayer = require('mineflayer'); const sleep = ms => new Promise(r => setTimeout(r, ms));
const mk = name => new Promise(res => { const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' }); b.chat_ = []; b.on('message', m => b.chat_.push(m.toString())); b.once('spawn', () => res(b)); });
(async () => {
  const op = await mk('EmberTester'); await sleep(3500);
  const say = async (cmd, w = 600) => { op.chat_.length = 0; op.chat(cmd); await sleep(w); return op.chat_.join(' | '); };
  await say('/gamemode creative', 300);
  await say('/emberfall bot spawn TrailBot', 2200);
  await say('/effect give @a[name=TrailBot] minecraft:resistance 999 4 true', 300);
  await say('/emberfall bot run TrailBot ranger', 1500);
  for (let i = 0; i < 40; i++) { await sleep(2000); const s = await say('/emberfall bot state TrailBot', 700); if (/scouts=1/.test(s) && /weapons=\w/.test(s)) break; }
  await say('/tp @s @a[name=TrailBot,limit=1]', 600);
  const has = async tag => /passed/.test(await say('/execute if entity @e[tag=' + tag + ']', 450));
  const P = '/execute at @a[name=TrailBot,limit=1] run summon minecraft:';
  await say(P + 'husk ~2 ~ ~2 {Tags:["v_plain"],Invulnerable:1b,PersistenceRequired:1b,NoAI:1b}', 500);
  await say(P + 'husk ~3 ~ ~2 {Tags:["v_named"],CustomName:\'"keep"\',Invulnerable:1b,PersistenceRequired:1b,NoAI:1b}', 500);
  await say(P + 'armor_stand ~4 ~ ~2 {Tags:["v_stand"]}', 500);
  await say(P + 'husk ~5 ~ ~2 {Tags:["v_plain2","emberfall_bot_scout"],Invulnerable:1b,PersistenceRequired:1b,NoAI:1b}', 500);
  let elapsed = 0;
  for (const sec of [0, 1, 2, 4, 8, 15]) {
    if (sec > elapsed) { await sleep((sec - elapsed) * 1000); elapsed = sec; }   // polling itself takes about 2 s, so the labels are nominal
    console.log('t~' + sec + 's plain=' + await has('v_plain') + ' named=' + await has('v_named') + ' stand=' + await has('v_stand') + ' scoutTagged=' + await has('v_plain2'));
  }
  await say('/emberfall wavestop 0', 300); process.exit(0);
})();
