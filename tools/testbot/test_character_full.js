const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
let start = Date.now();
const log = (m) => console.log(`[${((Date.now()-start)/1000).toFixed(1)}s] ${m}`);
bot.on('message', (m) => log('CHAT: ' + m.toString()));
bot.on('error', (e) => log('ERROR: ' + e));
let started = false;
const chars = ['vanguard','duelist','juggernaut','ranger','battlemage'];
const expectedWeapon = { vanguard: 'broadsword', duelist: 'twin_daggers', juggernaut: 'war_halberd', ranger: 'hunting_bow', battlemage: 'arcane_staff' };
bot.on('spawn', () => {
  if (started) return;
  started = true;
  log('spawn');
  let t0 = 1000;
  const t = (ms, f) => setTimeout(f, ms);
  t(t0, () => { log('CMD: /character list'); bot.chat('/character list'); });
  t0 += 2500;
  chars.forEach((c) => {
    t(t0, () => { bot.chat('/character select ' + c); });
    t0 += 1200;
    t(t0, () => { bot.chat('/expedition'); });
    t0 += 1800;
    t(t0, () => {
      const held = bot.heldItem;
      const heldName = held ? held.name : 'none';
      log(`character=${c} expectedWeapon=${expectedWeapon[c]} heldItem=${heldName}`);
    });
    t0 += 500;
    t(t0, () => { bot.chat('/expedition leave'); });
    t0 += 1500;
  });
  t(t0 + 500, () => { log('DONE full character regression'); bot.quit(); process.exit(0); });
});
