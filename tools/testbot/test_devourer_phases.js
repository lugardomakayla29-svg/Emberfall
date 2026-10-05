const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let t0 = Date.now();
const log = (m) => console.log(`[${((Date.now()-t0)/1000).toFixed(1)}s] ${m}`);
function cmd(c) { log('CMD: ' + c); bot.chat(c); }
bot.on('message', (msg) => log('CHAT: ' + msg.toString()));
bot.on('error', (e) => log('ERROR: ' + e));

let started = false;
bot.on('spawn', () => {
  if (started) return;
  started = true;
  const t = (ms, f) => setTimeout(f, ms);

  t(500, () => cmd('/gamemode creative'));
  t(1000, () => cmd('/emberfall paste hydraphase'));
  t(3500, () => cmd('/emberfall wavestart 0'));
  t(5500, () => cmd('/emberfall join 0 EmberTester'));
  t(7500, () => cmd('/gamemode spectator'));
  t(9500, () => cmd('/emberfall bossdevourer 0'));

  const SEL = '@e[type=emberfall:devourer_brain,distance=..100,sort=nearest,limit=1]';

  // Let phase 1 run a bit unmolested - watch for dash/burrow/burst logs and displays.
  t(12000, () => cmd(`/execute as @p at @s run data get entity ${SEL} Health`));
  t(12500, () => cmd(`/execute if entity @e[type=minecraft:item_display,distance=..100] run tag @e[type=minecraft:item_display,distance=..100] add ad`));
  t(13000, () => cmd(`/execute if entity @e[type=emberfall:devourer_segment,distance=..100] run say SEGMENTS_ALIVE`));

  // Chunk the HP down toward phase 2 (66%) and confirm minis spawn.
  t(16000, () => cmd(`/execute as ${SEL} run damage @s 90 minecraft:generic`));
  t(18000, () => cmd(`/execute as @p at @s run data get entity ${SEL} Health`));
  t(19000, () => cmd(`/say checking for minis:`));
  t(19200, () => cmd(`/execute if entity @e[type=emberfall:devourer_spawn,distance=..100] run say MINIS_PRESENT`));

  // Push toward phase 3 (33%).
  t(22000, () => cmd(`/execute as ${SEL} run damage @s 90 minecraft:generic`));
  t(24000, () => cmd(`/execute as @p at @s run data get entity ${SEL} Health`));

  // Push toward the 20% mini-wave breakpoint.
  t(27000, () => cmd(`/execute as ${SEL} run damage @s 30 minecraft:generic`));
  t(29000, () => cmd(`/execute as @p at @s run data get entity ${SEL} Health`));
  t(29200, () => cmd(`/execute if entity @e[type=emberfall:devourer_spawn,distance=..100] run say MINIS_PRESENT_P3`));

  // Push toward the 10% breakpoint, then finish it off.
  t(32000, () => cmd(`/execute as ${SEL} run damage @s 25 minecraft:generic`));
  t(34000, () => cmd(`/execute as @p at @s run data get entity ${SEL} Health`));
  t(37000, () => cmd(`/execute as ${SEL} run damage @s 200 minecraft:generic`));
  t(39000, () => cmd(`/execute if entity @e[type=emberfall:devourer_brain,distance=..100] run say BRAIN_STILL_ALIVE`));
  t(39200, () => cmd(`/execute unless entity @e[type=emberfall:devourer_brain,distance=..100] run say BRAIN_GONE`));
  t(39400, () => cmd(`/execute if entity @e[type=emberfall:devourer_segment,distance=..100] run say SEGMENTS_LEAKED`));
  t(39600, () => cmd(`/execute if entity @e[type=minecraft:item_display,distance=..100] run say DISPLAYS_LEAKED`));

  t(42000, () => { log('TEST COMPLETE'); bot.quit(); process.exit(0); });
});

setInterval(() => {
  if (!bot.entity) return;
  const near = Object.values(bot.entities).filter(e => e.position && bot.entity.position.distanceTo(e.position) < 40);
  const displays = near.filter(e => e.name === 'item_display');
  const brains = near.filter(e => e.name === 'devourer_brain');
  const minis = near.filter(e => e.name === 'devourer_spawn');
  log(`[scan] item_displays=${displays.length} brains=${brains.length} minis=${minis.length}`);
}, 2000);
