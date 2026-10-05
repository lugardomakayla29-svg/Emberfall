const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

// No distance filter at all - there is only ever one devourer_brain alive.
const SEL = '@e[type=emberfall:devourer_brain,limit=1]';

let started = false;
bot.on('spawn', () => {
  if (started) return;
  started = true;
  const t = (ms, f) => setTimeout(f, ms);

  t(500, () => cmd('/gamemode creative'));
  t(1000, () => cmd('/emberfall paste hydraphase'));
  t(4000, () => cmd('/emberfall wavestart 0'));
  t(7000, () => cmd('/emberfall join 0 EmberTester'));
  t(10000, () => cmd('/gamemode spectator'));
  t(13000, () => cmd('/emberfall bossdevourer 0'));

  t(17000, () => { log('self pos: ' + JSON.stringify(bot.entity.position)); cmd(`/data get entity ${SEL} Pos`); });
  t(20000, () => cmd(`/data get entity ${SEL} Health`));
  t(24000, () => { log('self pos: ' + JSON.stringify(bot.entity.position)); cmd(`/execute as ${SEL} run damage @s 90 minecraft:generic`); });
  t(28000, () => cmd(`/data get entity ${SEL} Health`));
  t(32000, () => { log('self pos: ' + JSON.stringify(bot.entity.position)); cmd(`/execute as ${SEL} run damage @s 90 minecraft:generic`); });
  t(36000, () => cmd(`/data get entity ${SEL} Health`));
  t(40000, () => { log('self pos: ' + JSON.stringify(bot.entity.position)); cmd(`/execute if entity @e[type=emberfall:devourer_spawn] run say MINIS_PRESENT`); });
  t(44000, () => cmd(`/execute as ${SEL} run damage @s 30 minecraft:generic`));
  t(48000, () => cmd(`/data get entity ${SEL} Health`));
  t(52000, () => cmd(`/execute as ${SEL} run damage @s 30 minecraft:generic`));
  t(56000, () => cmd(`/data get entity ${SEL} Health`));
  t(60000, () => cmd(`/execute if entity @e[type=emberfall:devourer_spawn] run say MINIS_PRESENT_P3`));
  t(64000, () => cmd(`/execute as ${SEL} run damage @s 200 minecraft:generic`));
  t(68000, () => cmd(`/execute unless entity ${SEL} run say BRAIN_GONE`));
  t(68500, () => cmd(`/execute if entity @e[type=emberfall:devourer_segment] run say SEGMENTS_LEAKED`));
  t(69000, () => cmd(`/execute if entity @e[type=minecraft:item_display] run say DISPLAYS_LEAKED`));

  t(72000, () => { log('TEST COMPLETE'); bot.quit(); process.exit(0); });
});
