const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async (x, w = 500) => { const n = lines.length; bot.chat(x); await sleep(w); return lines.slice(n).join(' | '); };
const hp = async () => { for (let k = 0; k < 4; k++) { const m = /: ([\d.]+)f/.exec(await ask('/data get entity @s Health', 450)); if (m) return +m[1]; } return NaN; };
bot.once('spawn', async () => {
  await sleep(6500);
  await ask('/gamemode survival'); await ask('/character select juggernaut', 600); await ask('/expedition', 1500); await sleep(1500);
  await ask('/emberfall wavestop 0', 400); await ask('/kill @e[type=!player]', 600); await sleep(800);
  const out = [];
  for (const d of [14, 10, 14]) {
    await ask('/effect clear @s', 200); await ask('/effect give @s minecraft:instant_health 1 10 true', 300); await sleep(900);
    const a = await hp(); await ask(`/damage @s ${d} minecraft:mob_attack`, 500); const b = await hp();
    out.push(`asked ${d}: ${a} -> ${b} lost ${(a - b).toFixed(2)}`);
  }
  console.log(out.join('\n'));
  const r = await ask('/attribute @s minecraft:armor get', 500); console.log('armor:', r.slice(0, 80));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
bot.on('error', e => console.log('ERR', e.message));
