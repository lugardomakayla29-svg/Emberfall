// EmberTester visibility: a human client must (1) receive a PlayerInfo for the bot, UNLISTED, with its skin tag, and (2) be sent the bot ENTITY.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let fails = 0; const check = (n, ok, note = '') => { console.log((ok ? 'PASS ' : 'FAIL ') + n + ' ' + note); if (!ok) fails++; };
(async () => {
  const human = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'PlainPlayer', version: '1.21.11', auth: 'offline' });
  const infos = [], adds = [], chat = [];
  human._client.on('player_info', p => infos.push(p));
  human._client.on('spawn_entity', p => adds.push(p));
  human.on('messagepack', () => {}); human.on('message', m => chat.push(m.toString()));
  await new Promise(r => human.once('spawn', r)); await sleep(2500);
    infos.length = 0; adds.length = 0;
  human.chat('/emberfall bot spawn VisBot1'); await sleep(3500);
  const spawnReply = chat.filter(m => /BOT spawn/.test(m)).join(' ');
  check('V0 the spawn command itself worked (so later failures mean the fix, not the setup)', /BOT spawn VisBot1 ok/.test(spawnReply), spawnReply.slice(0, 80) || chat.slice(-1)[0]);
  const botInfo = infos.flatMap(p => (p.data || []).map(e => ({ action: p.action, ...e }))).filter(e => e.player?.name === 'VisBot1' || (e.uuid && e.player));
  console.log('infos seen:', infos.length, 'adds seen:', adds.length, 'chat tail:', chat.slice(-2).join(' | ').slice(0, 120));
  const entry = infos.flatMap(p => (p.data || [])).find(e => e.player && e.player.name === 'VisBot1');
  check('V1 the human is sent a PlayerInfo entry for the bot (old code cancelled it)', !!entry, entry ? '' : JSON.stringify(infos[0] || {}).slice(0, 160));
  check('V2 that entry is UNLISTED (so the tab list never shows it)', !!entry && Number(entry.listed) === 0, entry ? 'listed=' + entry.listed : '');
  // Control: the human's own entry in the same stream must be LISTED, otherwise V2 could be reading a flag that is always 0.
  const hEntry = human.players['PlainPlayer'];
  check('V2b control: a real player is listed (so "unlisted" above is meaningful)', !!hEntry && Number(hEntry.listed) === 1, hEntry ? 'listed=' + hEntry.listed : 'no entry');
  const props = entry && entry.player && entry.player.properties || [];
  const tag = props.find(p => p.key === 'emberfall_bot_skin' || p.name === 'emberfall_bot_skin');
  check('V3 the skin tag survives the packet', !!tag && /^([1-9]|10)$/.test(String(tag.value)), tag ? 'value=' + tag.value : JSON.stringify(props).slice(0, 120));
  const ent = Object.values(human.entities).find(e => e.type === 'player' && e.username === 'VisBot1');
  check('V4 the human has the bot ENTITY (this is what was missing)', !!ent, ent ? 'id ' + ent.id : 'entities: ' + Object.values(human.entities).map(e => e.username || e.name).join(','));
  const tab = Object.values(human.players).filter(p => p.username === 'VisBot1');
  console.log('mineflayer tab players named VisBot1:', tab.length);
  console.log(fails === 0 ? 'ALL PASS' : 'SOME FAIL ' + fails);
  human.quit(); setTimeout(() => process.exit(0), 400);
})();
