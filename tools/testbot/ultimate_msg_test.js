// QoL proof: when a weapon's ultimate fires, the player sees "<Name> unleashed" on the action bar. One character per run (env MSG_CHAR, MSG_NAME).
// The meter is filled last with a foe beside the player so the weapon's own hit code reaches consumeUltimate. The action bar arrives as a chat
// message with position 'game_info'; mineflayer surfaces it on the 'message' event too, so the text is searched in everything received.
const mineflayer = require('mineflayer');
const CHAR = process.env.MSG_CHAR;
const NAMES = { broadsword: 'Sunbrand Sweep', twin_daggers: 'Phantom Blades', war_halberd: 'War Slam', hunting_bow: 'Storm of Arrows', arcane_staff: 'Starfall', gravechain: 'Grave Legion', spectral_sickles: "Reaper's Rite", ashen_beacon: 'Pyre Nova' };
let NAME = '?';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString())); bot.on('error', e => console.log('ERR', e.message));
const ask = async (x, w = 450) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
let fails = 0; const R = (n, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'} ${n} ${extra}`); if (!ok) fails++; };
const g = async (kills, m) => ask(`/emberfall debugweapongrowth EmberTester grant 0 ${kills} ${m}`, 400);
bot.once('spawn', async () => {
  await sleep(5000);
  await ask('/gamemode survival'); await ask('/effect clear @s');
  await ask(`/character select ${CHAR}`); await ask('/expedition', 2500);
  for (let i = 0; i < 40; i++) { await sleep(2000); if (/expedition/.test(await ask('/data get entity @s Dimension', 400))) break; }
  await sleep(2000);
  await ask('/effect give @s minecraft:resistance 999 4 true', 200); await ask('/effect give @s minecraft:regeneration 999 4 true', 200);
  await ask('/emberfall wavestop 0', 300); await ask('/time set midnight', 200);
  await ask('/kill @e[type=!player,type=!minecraft:item_display,type=!minecraft:interaction]', 900);
  const bx = bot.entity.position.x, by = bot.entity.position.y, bz = bot.entity.position.z;
  const pin = setInterval(() => bot.chat(`/tp @s ${bx.toFixed(2)} ${by.toFixed(2)} ${bz.toFixed(2)} 0 0`), 700);
  await ask('/emberfall debugloadout EmberTester', 600);
  const rep = await ask('/emberfall debugweapongrowth EmberTester', 700);
  const wm = /growth slot 0 ([a-z_]+)/.exec(rep); const WID = wm ? wm[1] : null; NAME = NAMES[WID] || '?';
  console.log('CHAR', CHAR, 'holds', WID, '->', NAME);
  await g(0, 0);
  const n0 = lines.length;
  await ask(`/execute at @s run summon emberfall:horde_zombie ~ ~ ~1.5 {Tags:["keep"],NoAI:1b,Silent:1b,PersistenceRequired:1b}`, 300);
  await ask('/attribute @e[tag=keep,limit=1] minecraft:max_health base set 1024', 100);
  await ask('/data modify entity @e[tag=keep,limit=1] Health set value 1024.0f', 100);
  await g(0, 1000);                                                   // meter FULL as the last step
  await sleep(6000);
  const got = lines.slice(n0).filter(l => l.includes('unleashed'));
  R(`U1 ${NAME}: the action bar says it was unleashed`, got.length >= 1, JSON.stringify(got.slice(0, 2)));
  R(`U2 ${NAME}: it names the right ultimate`, got.some(l => l.includes(NAME)), got[0] || 'none');
  R(`U3 ${NAME}: it says so once, not every tick`, got.length <= 2, `count ${got.length}`);
  clearInterval(pin); await ask('/expedition leave', 800);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails); bot.quit(); setTimeout(() => process.exit(0), 300);
});
