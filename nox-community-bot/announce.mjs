// One-off announcement: NOX goes open source — post to the general channel.
// Usage: node announce.mjs   (reads .env, optional ANNOUNCE_CHANNEL override)
import { Client, GatewayIntentBits, EmbedBuilder } from 'discord.js';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
try { // .env loader — same as index.js
  for (const line of fs.readFileSync(path.join(__dirname, '.env'), 'utf8').split('\n')) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/i);
    if (m && process.env[m[1]] === undefined)
      process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
} catch {}

const config = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf8'));
config.token = process.env.DISCORD_TOKEN || config.token;
const channelId = process.env.ANNOUNCE_CHANNEL || config.channels?.general;
if (!channelId) { console.error('no announce channel configured'); process.exit(1); }

const client = new Client({ intents: [GatewayIntentBits.Guilds] });
client.once('clientReady', async () => {
  try {
    const ch = await client.channels.fetch(channelId);
    // remove the previous announcement from this bot, if any
    const msgs = await ch.messages.fetch({ limit: 20 });
    for (const m of msgs.values())
      if (m.author.id === client.user.id && m.embeds.length) await m.delete().catch(() => {});

    const embed = new EmbedBuilder()
      .setColor(0x7A2BE2)
      .setTitle('NOX is now fully open source 🎉')
      .setDescription(
        'The entire NOX client is now released under the **MIT license** — ' +
        'everyone can read, fork, improve and co-develop the code.\n\n' +
        '**We are looking for contributors:**\n' +
        '• 🎨 **Designers** — themes, icons, HUD layouts, cape/sky textures, artwork\n' +
        '• 💻 **Developers** — new modules, fixes, Fabric 26.3\n' +
        '• 🤖 **Bot people** — the discord.js community bot\n\n' +
        'Grab the **Designer** or **Dev** role in the role panel and jump in — ' +
        'everything you need is in `CONTRIBUTING.md`.')
      .addFields(
        { name: 'Changelog 0.1.0-alpha', value:
          'New GUI looks (Panels + Hub), ~140 modules, capes & shop,\n' +
          'HUD auto-fit, live module diagnostics — details in `CHANGELOG.md`.' },
        { name: 'Website', value: 'https://j6ilbrk.is-a.dev (live after merge)' },
      )
      .setFooter({ text: 'NOX · early alpha' })
      .setTimestamp();
    await ch.send({ embeds: [embed] });
    console.log('announced in', ch.name);
  } catch (e) {
    console.error('announce failed:', e.message);
  } finally {
    client.destroy();
  }
});
client.login(config.token);
