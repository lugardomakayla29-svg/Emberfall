const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'EmberTester', version: '1.21.11', auth: 'offline' });
bot.on('spawn', () => console.log('SPAWNED'));
bot.on('error', (e) => console.log('ERROR: ' + e));
bot.on('kicked', (r) => console.log('KICKED: ' + JSON.stringify(r)));
bot.on('end', (r) => console.log('ENDED: ' + JSON.stringify(r)));
