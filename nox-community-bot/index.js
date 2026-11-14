'use strict';

const fs = require('node:fs');
const path = require('node:path');

/* .env loader — no dependency: KEY=VALUE per line, # comments, quoted values ok */
try {
  for (const line of fs.readFileSync(path.join(__dirname, '.env'), 'utf8').split('\n')) {
    const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/);
    if (!m || m[1].startsWith('#')) continue;
    if (process.env[m[1]] === undefined)
      process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
} catch { /* .env optional — real env vars work too */ }

const crypto = require('node:crypto');
const {
  Client, GatewayIntentBits, Events, PermissionFlagsBits, ChannelType,
  ActionRowBuilder, ButtonBuilder, ButtonStyle, StringSelectMenuBuilder,
  EmbedBuilder, AttachmentBuilder, MessageFlags, SlashCommandBuilder,
  ModalBuilder, TextInputBuilder, TextInputStyle,
} = require('discord.js');

const config = require('./config.json');
// env overrides config.json — keeps secrets out of the repo
config.token = process.env.DISCORD_TOKEN || config.token;
config.guildId = process.env.GUILD_ID || config.guildId;
if (!config.token) {
  console.error('[fatal] no bot token — set DISCORD_TOKEN in .env or the environment');
  process.exit(1);
}

const DATA = path.join(__dirname, 'data');
if (!fs.existsSync(DATA)) fs.mkdirSync(DATA);

const stateFile = path.join(DATA, 'state.json');
const state = fs.existsSync(stateFile)
  ? JSON.parse(fs.readFileSync(stateFile, 'utf8'))
  : { panels: {}, tickets: {} };
const save = () => fs.writeFileSync(stateFile, JSON.stringify(state, null, 2));

const ACCENT = 0x8b5cf6;
const NEUTRAL = 0x2b2d31;

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildVoiceStates,
  ],
});

/* Members intent is not enabled in the portal, so fetch the member when the
   role cache matters. */
async function memberHasRole(interaction, roleId) {
  const member = await interaction.guild.members.fetch(interaction.user.id).catch(() => interaction.member);
  return member.roles.cache.has(roleId);
}

/* ------------------------------------------------------------------ panels */

const TICKET_TYPES = [
  ['support', 'Support', 'General help, installation, questions', '❓'],
  ['bug', 'Bug Report', 'Client errors, crashes, logs', '🐛'],
  ['report', 'User Report', 'Rule violations, scams', '🚨'],
  ['cheat', 'Cheat Report', 'Proof required', '🛡️'],
  ['purchase', 'Purchase', 'Keys, payment, delivery', '🛒'],
  ['hwid', 'HWID Reset', 'New hardware', '🔑'],
  ['refund', 'Refund', 'Refund request', '↩️'],
  ['partner', 'Partnership', 'Cooperation', '🤝'],
  ['application', 'Application', 'Team, media, clipper', '📄'],
  ['appeal', 'Ban Appeal', 'Contest a ban', '⚖️'],
  ['clip', 'Clip Submission', 'Competition entry', '🎬'],
  ['other', 'Other', 'Everything else', '💬'],
];

const ROLE_BUTTONS = [
  ['pc', 'PC', '💻'], ['playstation', 'PlayStation', '🎮'], ['xbox', 'Xbox', '🟩'],
  ['switch', 'Switch', '🔴'], ['mobile', 'Mobile', '📱'],
  ['clipper', 'Clipper', '🎬'], ['designer', 'Designer', '✨'], ['dev', 'Developer', '🛠️'],
];

const PING_BUTTONS = [
  ['ping_announcements', 'Announcements', '📢'], ['ping_giveaways', 'Giveaways', '🎁'],
  ['ping_clips', 'Clips', '🎬'], ['ping_events', 'Events', '📅'], ['ping_lfg', 'LFG', '🔍'],
];

const ASSETS = path.join(__dirname, 'assets');

let EMOJIS = {};
try {
  EMOJIS = JSON.parse(fs.readFileSync(path.join(ASSETS, 'emojis.json'), 'utf8'));
} catch { /* optional */ }
const emojiOf = (name) => {
  const e = EMOJIS[name];
  if (!e) return null;
  return typeof e === 'string' ? { id: e, animated: true } : { id: e.id, animated: !!e.a };
};
const ce = (name) => {
  const e = emojiOf(name);
  return e ? `<${e.animated ? 'a' : ''}:${name}:${e.id}> ` : '';
};
const FOOTER = 'NOX · DonutSMP';

function baseEmbed(title, description) {
  return new EmbedBuilder()
    .setTitle(title)
    .setDescription(description)
    .setColor(ACCENT)
    .setFooter({ text: FOOTER });
}

/* Animated panel banner: attached as a GIF, so it works without boosts. */
function withBanner(key, embed, components) {
  const file = path.join(ASSETS, `panel-${key}.gif`);
  const payload = { embeds: [embed], components };
  if (fs.existsSync(file)) {
    embed.setImage('attachment://panel.gif');
    payload.files = [new AttachmentBuilder(file, { name: 'panel.gif' })];
  }
  return payload;
}

function verifyPayload() {
  return withBanner('verify',
    baseEmbed('Access',
      `${ce('noxcheck')}You can read and write right away — **no click needed**.\n\n` +
      `${ce('noxdot')}The button only adds the **Verified** role. Nothing is locked behind it.`),
    [new ActionRowBuilder().addComponents(
      new ButtonBuilder().setCustomId('verify:accept').setLabel('Get verified')
        .setEmoji(emojiOf('noxcheck') ?? '✅').setStyle(ButtonStyle.Success),
    )]);
}

function ticketPayload() {
  const menu = new StringSelectMenuBuilder()
    .setCustomId('ticket:create')
    .setPlaceholder('Choose a topic')
    .addOptions(TICKET_TYPES.map(([value, label, description, emoji]) => ({ value, label, description, emoji })));

  return withBanner('ticket',
    baseEmbed('Support',
      `${ce('noxdot')}Choose a topic. A private channel opens for you and the team.\n\n` +
      `${ce('noxarrow')}No support via DMs.`),
    [new ActionRowBuilder().addComponents(menu)]);
}

function rolesPayload() {
  const rows = [];
  for (let i = 0; i < ROLE_BUTTONS.length; i += 5) {
    const row = new ActionRowBuilder();
    for (const [key, label, emoji] of ROLE_BUTTONS.slice(i, i + 5)) {
      row.addComponents(new ButtonBuilder()
        .setCustomId('role:' + key).setLabel(label).setEmoji(emoji).setStyle(ButtonStyle.Secondary));
    }
    rows.push(row);
  }
  return withBanner('roles',
    baseEmbed('Roles', `${ce('noxdot')}Pick what you play and what you create. **Click again** to remove.`),
    rows);
}

function pingsPayload() {
  const row = new ActionRowBuilder();
  for (const [key, label, emoji] of PING_BUTTONS) {
    row.addComponents(new ButtonBuilder()
      .setCustomId('ping:' + key).setLabel(label).setEmoji(emoji).setStyle(ButtonStyle.Secondary));
  }
  return withBanner('pings',
    baseEmbed('Notifications', `${ce('noxdot')}Only the pings you actually want. **Click again** to turn one off.`),
    [row]);
}

/* -------------------------------------------------------------------- shop */

/* Wallets: PAY_WALLETS env = JSON {COIN[@CHAIN]: {addr, fee?, network?}} —
   same shape as the old system. fee = markup %; when omitted the volatility
   tier applies (stablecoins 0, majors low, alts higher). */
const VOLATILITY_FEE = {
  USDT: 0, USDC: 0, DAI: 0, BUSD: 0,
  BTC: 4, ETH: 4,
  LTC: 5, TRX: 5, BNB: 5, XRP: 5,
  SOL: 6, DOGE: 6, AVAX: 6,
  POL: 7, ADA: 7, DOT: 7, LINK: 7,
};
const feeFor = (asset, override) =>
  override != null && override !== '' ? Number(override) : (VOLATILITY_FEE[asset] ?? 8);

function wallets() {
  try {
    const raw = JSON.parse(process.env.PAY_WALLETS || process.env.NOX_WALLETS || '{}');
    return Object.entries(raw).map(([coin, v]) => ({
      coin,
      asset: coin.split('@')[0],
      addr: v.addr ?? v,
      fee: feeFor(coin.split('@')[0], v.fee),
      network: v.network ?? '',
    }));
  } catch { return []; }
}

/* Products live in config.json — {name, price: "5€", description} */
function parsePriceCents(p) {
  const m = String(p).replace(',', '.').match(/(\d+(?:\.\d+)?)/);
  return m ? Math.round(parseFloat(m[1]) * 100) : 0;
}
const eur = (c) => `€${(c / 100).toFixed(2)}`;

state.orders = state.orders || {};
state.channels = state.channels || {};
const ORDER_TTL_MS = 24 * 60 * 60 * 1000;

function newOrder(userId, product) {
  const id = `nx-${crypto.randomBytes(4).toString('hex')}`;
  // 1–20 random cents → every order has a unique total so staff can match
  // payments on the shared wallet ("who sent €5.43?")
  const uniqueCents = crypto.randomInt(1, 21);
  const order = {
    id, userId, product: product.name, status: 'open',
    base: parsePriceCents(product.price), uniqueCents,
    created: Date.now(), expires: Date.now() + ORDER_TTL_MS,
    channelId: null, txRef: null, coin: null, license: null,
  };
  state.orders[id] = order;
  save();
  return order;
}

function activeOrderOf(userId) {
  return Object.values(state.orders).find((o) =>
    o.userId === userId && (o.status === 'open' || o.status === 'review'));
}

function licenseFor(order) {
  // Ed25519-signed NOX1.* key when the private seed is configured — else a
  // clearly-marked unsigned key for manual handling.
  const payload = Buffer.from(JSON.stringify({
    u: order.userId, o: order.id, p: order.product, i: Date.now(),
  })).toString('base64url');
  const seed = process.env.NOX_LICENSE_KEY;
  if (seed) {
    try {
      const key = crypto.createPrivateKey({
        key: Buffer.concat([
          Buffer.from('302e020100300506032b657004220420', 'hex'),
          Buffer.from(seed, 'base64url'),
        ]),
        format: 'der', type: 'pkcs8',
      });
      const sig = crypto.sign(null, Buffer.from(payload), key).toString('base64url');
      return `NOX1.${payload}.${sig}`;
    } catch { /* fall through to unsigned */ }
  }
  return `NOX-${crypto.randomBytes(12).toString('hex').toUpperCase()}`;
}

function walletLines(order) {
  const ws = wallets();
  if (!ws.length) return '_No payment methods configured — staff will assist manually._';
  return ws.map((w) => {
    const total = Math.round(order.base * (1 + w.fee / 100)) + order.uniqueCents;
    const net = w.network ? ` — **${w.network}**` : '';
    return `**${w.coin}**${net}\n\`${w.addr}\`\nAmount: **${eur(total)}** worth of ${w.asset}`;
  }).join('\n\n');
}

function orderEmbed(order, note) {
  const statusColor = { open: 0xf59e0b, review: 0x3b82f6, paid: 0x22c55e, cancelled: 0x6b7280, expired: 0x6b7280 }[order.status] ?? NEUTRAL;
  const e = new EmbedBuilder()
    .setTitle(`Order ${order.id} — ${order.product}`)
    .setColor(statusColor)
    .setDescription(
      `Buyer: <@${order.userId}>\n` +
      `Total: **${eur(order.base)}** +${order.uniqueCents}¢ unique → send the **exact** amount shown below\n\n` +
      walletLines(order) +
      `\n\nSend payment, then press **Submit TXID**. Order expires <t:${Math.floor(order.expires / 1000)}:R>.` +
      (order.txRef ? `\n\nTX ref: \`${order.txRef}\`` : '') +
      (note ? `\n\n${note}` : ''))
    .setFooter({ text: 'NOX · Store' });
  return e;
}

function orderRows(order) {
  if (order.status === 'paid' || order.status === 'cancelled' || order.status === 'expired') return [];
  const buyer = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId(`order:tx:${order.id}`).setLabel('Submit TXID').setStyle(ButtonStyle.Primary),
    new ButtonBuilder().setCustomId(`order:cancel:${order.id}`).setLabel('Cancel').setStyle(ButtonStyle.Secondary),
  );
  const staff = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId(`order:paid:${order.id}`).setLabel('Mark paid (staff)').setStyle(ButtonStyle.Success),
    new ButtonBuilder().setCustomId(`order:refund:${order.id}`).setLabel('Close (staff)').setStyle(ButtonStyle.Danger),
  );
  return [buyer, staff];
}

async function buyChannel(guild) {
  if (state.channels.buy) {
    const ch = await guild.channels.fetch(state.channels.buy).catch(() => null);
    if (ch) return ch;
  }
  if (config.channels.buy) {
    const ch = await guild.channels.fetch(config.channels.buy).catch(() => null);
    if (ch) { state.channels.buy = ch.id; save(); return ch; }
  }
  const found = guild.channels.cache.find((c) => {
    const n = c.name.normalize('NFKD').toLowerCase();
    return n.includes('buy') || n.includes('shop') || n.includes('store') || c.name.includes('🛒');
  });
  if (found) { state.channels.buy = found.id; save(); return found; }
  const ch = await guild.channels.create({
    name: '✦🛒・ʙᴜʏ', type: ChannelType.GuildText, parent: '1570449670067458129',
    topic: 'Purchase NOX — crypto accepted',
    permissionOverwrites: [
      { id: guild.roles.everyone.id, deny: [PermissionFlagsBits.SendMessages], allow: [PermissionFlagsBits.ViewChannel] },
    ],
  });
  state.channels.buy = ch.id; save();
  return ch;
}

function buyPayload() {
  const list = (config.products || [])
    .map((p) => `**${p.name}** — ${p.price}\n${p.description}`).join('\n\n') || '_No products configured._';
  return withBanner('buy',
    baseEmbed('Store',
      `${ce('noxdot')}${list}\n\n` +
      `${ce('noxarrow')}Crypto accepted — press **Buy** to get your payment details. A private order channel opens for you.`),
    [new ActionRowBuilder().addComponents(
      new ButtonBuilder().setCustomId('buy:start').setLabel('Buy').setEmoji('🛒').setStyle(ButtonStyle.Success),
    )]);
}

async function openOrder(interaction, product) {
  const existing = activeOrderOf(interaction.user.id);
  if (existing) {
    return interaction.reply({
      content: `You already have an open order \`${existing.id}\` — finish or cancel it first: <#${existing.channelId}>`,
      flags: MessageFlags.Ephemeral,
    });
  }
  const order = newOrder(interaction.user.id, product);
  const safe = interaction.user.username.toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 12) || 'user';
  const channel = await interaction.guild.channels.create({
    name: `order-${safe}`,
    type: ChannelType.GuildText,
    parent: config.ticketCategoryId,
    topic: `Order ${order.id} · ${product.name} · ${interaction.user.tag}`,
    permissionOverwrites: [
      { id: interaction.guild.roles.everyone.id, deny: [PermissionFlagsBits.ViewChannel] },
      {
        id: interaction.user.id,
        allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory],
      },
      {
        id: config.roles.support,
        allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory, PermissionFlagsBits.ManageMessages],
      },
    ],
  });
  order.channelId = channel.id;
  save();
  await channel.send({
    content: `<@${interaction.user.id}> <@&${config.roles.support}>`,
    embeds: [orderEmbed(order)], components: orderRows(order),
  });
  await interaction.reply({ content: `Order \`${order.id}\` created: <#${channel.id}>`, flags: MessageFlags.Ephemeral });
}

async function finishOrder(interaction, order, ok) {
  order.status = ok ? 'paid' : 'cancelled';
  if (ok) {
    order.license = licenseFor(order);
    save();
    const buyer = await client.users.fetch(order.userId).catch(() => null);
    if (buyer) {
      await buyer.send({
        embeds: [baseEmbed('Your NOX license',
          `Thanks for your purchase! Order \`${order.id}\` is confirmed.\n\n` +
          `**License key**\n\`\`\`${order.license}\`\`\`\n` +
          `Paste it in the client under **Account → License**. Keep it private — it is tied to your Discord account.`)],
      }).then(() => { order.delivery = 'dm'; }).catch(() => { order.delivery = 'dm_failed'; });
    }
    const member = await interaction.guild.members.fetch(order.userId).catch(() => null);
    if (member) await member.roles.add(config.roles.verified).catch(() => {});
    await interaction.channel.send({
      embeds: [orderEmbed(order, `✅ **Paid** — confirmed by <@${interaction.user.id}>. License ${order.delivery === 'dm_failed' ? '**could not be DM\'d (DMs closed) — shown below**' : 'delivered via DM'}.`)],
      components: [],
    });
    if (order.delivery === 'dm_failed') {
      await interaction.channel.send({ content: `<@${order.userId}> your key: \`${order.license}\`` });
    }
    const log = await interaction.guild.channels.fetch(config.channels.staffLogs).catch(() => null);
    log?.send({ embeds: [baseEmbed('Order paid', `\`${order.id}\` · <@${order.userId}> · ${order.product} · ${eur(order.base)}`)] }).catch(() => {});
  } else {
    save();
    await interaction.channel.send({ embeds: [orderEmbed(order, '❌ Closed by staff.')], components: [] });
  }
  save();
}

/* expire stale orders — runs every 5 min */
async function orderSweep() {
  const now = Date.now();
  for (const order of Object.values(state.orders)) {
    if ((order.status === 'open' || order.status === 'review') && now > order.expires) {
      order.status = 'expired';
      save();
      const ch = await client.channels.fetch(order.channelId).catch(() => null);
      ch?.send({ embeds: [orderEmbed(order, '⌛ **Expired** — no payment received in 24h. Open a new order from the store channel.')], components: [] }).catch(() => {});
    }
  }
}

/* Ephemeral FS → orders must be rebuilt from the order channels themselves
   after every restart (topic carries id+product, perms carry the buyer). */
async function rehydrateOrders(guild) {
  let n = 0;
  for (const ch of guild.channels.cache.values()) {
    if (ch.parentId !== config.ticketCategoryId || !ch.name.startsWith('order-')) continue;
    const m = (ch.topic || '').match(/Order (nx-[0-9a-f]+) · (.+?) · (.+)/);
    if (!m || state.orders[m[1]]) continue;
    const buyerId = [...ch.permissionOverwrites.cache.keys()].find((id) =>
      id !== guild.roles.everyone.id && id !== config.roles.support);
    state.orders[m[1]] = {
      id: m[1], userId: buyerId ?? '0', product: m[2], status: 'open',
      base: 0, uniqueCents: 0, created: ch.createdTimestamp,
      expires: ch.createdTimestamp + ORDER_TTL_MS,
      channelId: ch.id, txRef: null, coin: null, license: null,
    };
    n++;
  }
  if (n) { save(); console.log('[order] rehydrated', n); }
}

const PANELS = [
  ['verify', config.channels.verify, verifyPayload],
  ['ticket', config.channels.openTicket, ticketPayload],
  ['roles', config.channels.roles, rolesPayload],
  ['pings', config.channels.pings, pingsPayload],
];

/* Serialized so /panel refresh and the ready hook can never overlap — an
   overlapping run was what created duplicate panels. */
let panelLock = Promise.resolve();
function withPanelLock(fn) {
  const run = panelLock.then(fn, fn);
  panelLock = run.catch(() => {});
  return run;
}

/* A panel message is identified by being authored by THIS bot and carrying
   the canonical footer — survives restarts and lost state files. */
function isOurPanel(m) {
  return m.author.id === client.user.id
    && m.embeds.some((e) => e.footer?.text === FOOTER);
}

async function adoptOrPostPanel(channel, key, payload) {
  // 1. stored id → edit in place
  let msg = state.panels[key]
    ? await channel.messages.fetch(state.panels[key]).catch(() => null)
    : null;

  // 2. lost/404 → scan history, adopt the newest existing panel and
  //    DELETE older duplicates instead of posting a 5th copy
  if (!msg) {
    const recent = await channel.messages.fetch({ limit: 100 }).catch(() => null);
    if (recent) {
      const ours = [...recent.values()].filter(isOurPanel)
        .sort((a, b) => b.createdTimestamp - a.createdTimestamp);
      msg = ours.shift() ?? null;
      for (const dup of ours) {
        await dup.delete().then(() => console.log('[panel] removed duplicate', key, dup.id)).catch(() => {});
      }
      if (msg) console.log('[panel] adopted', key, msg.id);
    }
  }

  if (msg) {
    await msg.edit({ ...payload, attachments: [] }).catch((err) => console.error('[panel] edit', key, err.message));
    if (!msg.pinned) await msg.pin().catch(() => {});
    state.panels[key] = msg.id;
    save();
    return;
  }

  const sent = await channel.send(payload);
  state.panels[key] = sent.id;
  save();
  await sent.pin().catch(() => {});
  console.log('[panel] posted', key);
}

async function ensurePanels(guild) {
  return withPanelLock(async () => {
    for (const [key, channelId, build] of PANELS) {
      if (!channelId) { console.warn('[panel] no channel for', key); continue; }
      const channel = await guild.channels.fetch(channelId).catch(() => null);
      if (!channel) { console.warn('[panel] missing channel', key); continue; }
      await adoptOrPostPanel(channel, key, build());
    }
    const store = await buyChannel(guild).catch((e) => { console.warn('[panel] buy channel', e.message); return null; });
    if (store) await adoptOrPostPanel(store, 'buy', buyPayload());
  });
}


/* Extra stickers are staged in assets/stickers and uploaded automatically as
   soon as the guild has a free slot (5 at tier 0, 15/30/60 with boosts). */
const STICKER_CAPS = [5, 15, 30, 60];

async function syncStickers(guild) {
  const dir = path.join(ASSETS, 'stickers');
  if (!fs.existsSync(dir)) return;
  const files = fs.readdirSync(dir).filter((f) => f.endsWith('.png'));
  if (!files.length) return;

  await guild.stickers.fetch().catch(() => {});
  const cap = STICKER_CAPS[guild.premiumTier] ?? 5;
  if (guild.stickers.cache.size >= cap) return;

  const have = new Set(guild.stickers.cache.map((s) => s.name));
  for (const file of files) {
    const name = path.basename(file, '.png').slice(0, 30);
    if (have.has(name)) continue;
    if (guild.stickers.cache.size >= cap) break;
    try {
      await guild.stickers.create({
        file: path.join(dir, file), name, tags: 'nox,donutsmp', description: 'NOX',
      });
      console.log('[sticker] uploaded', name);
      have.add(name);
    } catch (err) {
      console.warn('[sticker] skipped', name, err.code ?? err.message);
      break;
    }
  }
}

/* ------------------------------------------------------------------ tickets */

function transcriptOf(channel) {
  const lines = [];
  const messages = [...channel.messages.cache.values()].sort((a, b) => a.createdTimestamp - b.createdTimestamp);
  for (const m of messages) {
    const time = new Date(m.createdTimestamp).toISOString().replace('T', ' ').slice(0, 16);
    const body = m.content || (m.embeds.length ? '[embed]' : '');
    lines.push(`[${time}] ${m.author.tag}: ${body}`);
  }
  return lines.join('\n');
}

async function createTicket(interaction, type) {
  const guild = interaction.guild;
  const label = TICKET_TYPES.find(([v]) => v === type)?.[1] ?? 'Ticket';
  const safe = interaction.user.username.toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 16) || 'user';

  const channel = await guild.channels.create({
    name: `↳ ${type}-${safe}`,
    type: ChannelType.GuildText,
    parent: config.ticketCategoryId,
    topic: `${label} · ${interaction.user.tag} · ${interaction.user.id}`,
    permissionOverwrites: [
      { id: guild.roles.everyone.id, deny: [PermissionFlagsBits.ViewChannel] },
      {
        id: interaction.user.id,
        allow: [
          PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages,
          PermissionFlagsBits.ReadMessageHistory, PermissionFlagsBits.AttachFiles,
          PermissionFlagsBits.EmbedLinks, PermissionFlagsBits.AddReactions,
        ],
      },
      {
        id: config.roles.support,
        allow: [
          PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages,
          PermissionFlagsBits.ReadMessageHistory, PermissionFlagsBits.AttachFiles,
          PermissionFlagsBits.EmbedLinks, PermissionFlagsBits.ManageMessages,
        ],
      },
    ],
  });

  state.tickets[channel.id] = { userId: interaction.user.id, type, label, opened: Date.now() };
  save();

  const row = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId('ticket:claim').setLabel('Claim').setStyle(ButtonStyle.Primary),
    new ButtonBuilder().setCustomId('ticket:close').setLabel('Close').setStyle(ButtonStyle.Danger),
  );

  await channel.send({
    content: `<@${interaction.user.id}> <@&${config.roles.support}>`,
    embeds: [new EmbedBuilder()
      .setTitle(label)
      .setDescription('Describe your request. Screenshots, logs and proof help a lot.\nA team member will pick this up shortly.\nClose anytime with the button or `/ticket close`.')
      .setColor(ACCENT)
      .setFooter({ text: `Ticket · ${interaction.user.tag}` })],
    components: [row],
  });

  await interaction.editReply({ content: `Your ticket: <#${channel.id}>` });
}

async function closeTicket(interaction) {
  const channel = interaction.channel;
  const ticket = state.tickets[channel.id];
  const isStaff = await memberHasRole(interaction, config.roles.support)
    || interaction.memberPermissions?.has(PermissionFlagsBits.ManageChannels);
  if (!isStaff && ticket?.userId !== interaction.user.id) {
    return interaction.reply({ content: 'Only the ticket owner or staff can close this.', flags: MessageFlags.Ephemeral });
  }

  await interaction.reply({ content: 'Closing and saving transcript…' });
  await channel.messages.fetch({ limit: 100 }).catch(() => {});
  const text = transcriptOf(channel);
  const logChannel = await channel.guild.channels.fetch(config.channels.transcripts).catch(() => null);
  if (logChannel) {
    await logChannel.send({
      embeds: [baseEmbed(`Transcript · ${ticket?.label ?? 'Ticket'}`,
        `User: <@${ticket?.userId ?? '0'}>\nClosed by: <@${interaction.user.id}>`)],
      files: [new AttachmentBuilder(Buffer.from(text || 'empty', 'utf8'), { name: `ticket-${channel.id}.txt` })],
    }).catch(() => {});
  }

  const staffLog = await channel.guild.channels.fetch(config.channels.staffLogs).catch(() => null);
  if (staffLog) {
    staffLog.send({ embeds: [baseEmbed('Ticket closed',
      `<#${channel.id}> · ${ticket?.label ?? 'Ticket'}\nBy <@${interaction.user.id}>`)] }).catch(() => {});
  }

  delete state.tickets[channel.id];
  save();
  await channel.delete().catch(() => {});
}

/* ------------------------------------------------------------------ commands */

const COMMANDS = [
  new SlashCommandBuilder()
    .setName('ticket')
    .setDescription('Manage the ticket you are in')
    .addSubcommand((s) => s.setName('claim').setDescription('Claim this ticket as staff'))
    .addSubcommand((s) => s.setName('close').setDescription('Close and save a transcript'))
    .addSubcommand((s) => s
      .setName('add').setDescription('Add a member to this ticket')
      .addUserOption((o) => o.setName('user').setDescription('Member').setRequired(true)))
    .addSubcommand((s) => s
      .setName('remove').setDescription('Remove a member from this ticket')
      .addUserOption((o) => o.setName('user').setDescription('Member').setRequired(true)))
    .addSubcommand((s) => s
      .setName('rename').setDescription('Rename this ticket')
      .addStringOption((o) => o.setName('name').setDescription('New name').setRequired(true).setMaxLength(60))),
  new SlashCommandBuilder()
    .setName('lock')
    .setDescription('Lock a channel for everyone')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageChannels)
    .addChannelOption((o) => o.setName('channel').setDescription('Channel (default: this one)')),
  new SlashCommandBuilder()
    .setName('unlock')
    .setDescription('Unlock a channel again')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageChannels)
    .addChannelOption((o) => o.setName('channel').setDescription('Channel (default: this one)')),
  new SlashCommandBuilder()
    .setName('panel')
    .setDescription('Maintain the NOX panels')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageChannels)
    .addSubcommand((s) => s.setName('refresh').setDescription('Re-render and update all panels')),
];

async function isStaff(interaction) {
  if (interaction.memberPermissions?.has(PermissionFlagsBits.ManageChannels)) return true;
  return memberHasRole(interaction, config.roles.support);
}

const SEND_KEYS = ['SendMessages', 'SendMessagesInThreads', 'AddReactions', 'CreatePublicThreads', 'CreatePrivateThreads'];

async function setLock(interaction, channel, locked) {
  state.locks = state.locks || {};
  const everyone = channel.guild.roles.everyone.id;
  const existing = channel.permissionOverwrites.cache.get(everyone);
  const keys = channel.isVoiceBased() ? ['Speak'] : SEND_KEYS;

  if (locked) {
    if (state.locks[channel.id] === undefined) {
      state.locks[channel.id] = existing
        ? Object.fromEntries(keys.map((k) => {
          const bit = PermissionFlagsBits[k];
          return [k, existing.allow.has(bit) ? true : existing.deny.has(bit) ? false : null];
        }))
        : null;
      save();
    }
    const deny = {};
    for (const k of keys) deny[k] = false;
    await channel.permissionOverwrites.edit(everyone, deny, { reason: `Locked by ${interaction.user.tag}` });
    return;
  }

  const backup = state.locks[channel.id];
  if (backup) {
    await channel.permissionOverwrites.edit(everyone, backup, {
      reason: `Unlocked by ${interaction.user.tag}`,
    });
  } else {
    const clear = {};
    for (const k of keys) clear[k] = null;
    await channel.permissionOverwrites.edit(everyone, clear, { reason: `Unlocked by ${interaction.user.tag}` });
    const now = channel.permissionOverwrites.cache.get(everyone);
    if (now && now.allow.bitfield === 0n && now.deny.bitfield === 0n) {
      await channel.permissionOverwrites.delete(everyone).catch(() => {});
    }
  }
  delete state.locks[channel.id];
  save();
}

async function handleCommand(interaction) {
  const sub = interaction.options.getSubcommand(false);

  if (interaction.commandName === 'panel') {
    await interaction.deferReply({ flags: MessageFlags.Ephemeral });
    await ensurePanels(interaction.guild);
    return interaction.editReply({ content: 'Panels updated.' });
  }

  if (interaction.commandName === 'lock' || interaction.commandName === 'unlock') {
    const channel = interaction.options.getChannel('channel') ?? interaction.channel;
    const locked = interaction.commandName === 'lock';
    await interaction.deferReply({ flags: MessageFlags.Ephemeral });
    await setLock(interaction, channel, locked);
    return interaction.editReply({
      content: locked
        ? `${ce('noxlock')}<#${channel.id}> is locked. Only admins can still write.`
        : `${ce('noxcheck')}<#${channel.id}> is open again.`,
    });
  }

  /* ticket subcommands */
  const channel = interaction.channel;
  const ticket = state.tickets[channel.id];
  if (!ticket) {
    return interaction.reply({ content: 'Use this inside a ticket channel.', flags: MessageFlags.Ephemeral });
  }

  if (sub === 'close') return closeTicket(interaction);

  const staff = await isStaff(interaction);
  const owner = ticket.userId === interaction.user.id;

  if (sub === 'claim') {
    if (!staff) return interaction.reply({ content: 'Only staff can claim a ticket.', flags: MessageFlags.Ephemeral });
    ticket.claimedBy = interaction.user.id;
    save();
    await channel.setTopic(`${ticket.label} · ${interaction.user.tag} · claimed by ${interaction.user.tag}`).catch(() => {});
    return interaction.reply({ content: `${ce('noxcheck')}Claimed by <@${interaction.user.id}>.` });
  }

  if (sub === 'add' || sub === 'remove') {
    if (!staff) return interaction.reply({ content: 'Only staff can change ticket members.', flags: MessageFlags.Ephemeral });
    const user = interaction.options.getUser('user', true);
    if (user.bot) return interaction.reply({ content: 'Bots cannot be added.', flags: MessageFlags.Ephemeral });
    if (user.id === ticket.userId) {
      return interaction.reply({ content: 'That member opened this ticket.', flags: MessageFlags.Ephemeral });
    }
    if (sub === 'add') {
      await channel.permissionOverwrites.edit(user.id, {
        ViewChannel: true, SendMessages: true, ReadMessageHistory: true,
        AttachFiles: true, EmbedLinks: true, AddReactions: true,
      });
    } else {
      await channel.permissionOverwrites.delete(user.id).catch(() => {});
    }
    return interaction.reply({
      content: `${ce(sub === 'add' ? 'noxcheck' : 'noxarrow')}${sub === 'add' ? 'Added' : 'Removed'} <@${user.id}>.`,
    });
  }

  if (sub === 'rename') {
    if (!staff) return interaction.reply({ content: 'Only staff can rename a ticket.', flags: MessageFlags.Ephemeral });
    const raw = interaction.options.getString('name', true);
    const safe = raw.toLowerCase().normalize('NFKD').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40);
    if (!safe) return interaction.reply({ content: 'Use letters or numbers.', flags: MessageFlags.Ephemeral });
    await channel.setName(`↳ ${safe}`);
    return interaction.reply({ content: `${ce('noxcheck')}Renamed to **${safe}**.` });
  }

  if (!staff && !owner) {
    return interaction.reply({ content: 'Not allowed.', flags: MessageFlags.Ephemeral });
  }
}

/* ------------------------------------------------------------------ stats */

async function updateStats() {
  const guild = await client.guilds.fetch(config.guildId).catch(() => null);
  if (!guild) return;

  const full = await guild.fetch().catch(() => guild);
  const members = full.memberCount ?? 0;
  const boosts = full.premiumSubscriptionCount ?? 0;
  const online = full.approximatePresenceCount ?? 0;

  const set = async (id, name) => {
    const ch = await guild.channels.fetch(id).catch(() => null);
    if (ch && ch.name !== name) await ch.setName(name).catch(() => {});
  };
  await set(config.channels.counters.members, `✦👥・ᴍᴇᴍʙᴇʀꜱ: ${members}`);
  await set(config.channels.counters.online, `✦🟢・ᴏɴʟɪɴᴇ: ${online}`);
  await set(config.channels.counters.boosts, `✦💎・ʙᴏᴏꜱᴛꜱ: ${boosts}`);
}

/* ------------------------------------------------------------ channel style */

/* One-shot restyle, versioned via state.styleV. Format:  ✦<emoji>・ɴᴀᴍᴇ
   — one special symbol, one emoji, small-caps label. Skips dynamic
   order/ticket channels and counters (updateStats owns those names). */
const STYLE_V = 3;
const STYLE_SYM = '✦';
const SMALL = Object.fromEntries(Object.entries({
  a:'ᴀ',b:'ʙ',c:'ᴄ',d:'ᴅ',e:'ᴇ',f:'ꜰ',g:'ɢ',h:'ʜ',i:'ɪ',j:'ᴊ',k:'ᴋ',l:'ʟ',m:'ᴍ',
  n:'ɴ',o:'ᴏ',p:'ᴘ',q:'ǫ',r:'ʀ',s:'ꜱ',t:'ᴛ',u:'ᴜ',v:'ᴠ',w:'ᴡ',x:'x',y:'ʏ',z:'ᴢ',
}));
const smallCaps = (s) => [...s.toLowerCase()].map((c) => SMALL[c] ?? c).join('');

function restyleName(name, isCategory) {
  // split off "⌬💬・" style prefixes — keep the emoji, restyle the words
  const parts = name.split('・');
  let emoji = '📌'; let label = name;
  if (parts.length > 1) {
    const head = parts[0].replace(/[✦⌬◆◇⟡⟐⋆✧❖⬡⬢⎔⌖⌗◈⭓⌇]/g, '').trim();
    if (head) emoji = head;
    label = parts.slice(1).join('・');
  }
  // NFKD folds 𝙨𝙩𝙮𝙡𝙞𝙨𝙝 math-font letters back to plain a-z for smallCaps
  const clean = label.replace(/[✦⌬◆◇⟡⟐⋆✧❖⬡⬢⎔⌖⌗◈⭓⌇]/g, '')
    .normalize('NFKD').replace(/[̀-ͯ]/g, '').trim() || 'channel';
  return isCategory ? `${STYLE_SYM} ${smallCaps(clean)}` : `${STYLE_SYM}${emoji}・${smallCaps(clean)}`;
}

async function restyleChannels(guild) {
  const channels = await guild.channels.fetch().catch(() => null);
  if (!channels) return;
  for (const ch of channels.values()) {
    if (!ch || ch.isThread()) continue;
    const n = ch.name;
    // skip dynamic channels + counters (updateStats renames them itself)
    if (n.startsWith('↳') || n.startsWith('order-') || n.startsWith('ticket-')) continue;
    if (Object.values(config.channels.counters).includes(ch.id)) continue;
    if (ch.isVoiceBased() && n.toLowerCase().includes('afk')) continue;
    const next = restyleName(n, ch.type === ChannelType.GuildCategory);
    if (next === n) continue;
    await ch.setName(next).then(() => console.log('[style]', n, '→', next))
      .catch((e) => console.warn('[style] failed', n, e.message));
    await new Promise((r) => setTimeout(r, 1200)); // gentle on rate limits
  }
}

/* ------------------------------------------------------------- one-shots */

const REPO_URL = 'https://github.com/neuberghero-debug/nox';
const OLD_REPO = 'a0yark/patch-devin';
const ONBOARD_CHANNEL = '1571067253137342576';

/* Replace the stale repo link in our own general announcements. */
async function fixRepoLinks(guild) {
  const ch = await guild.channels.fetch(config.channels.general).catch(() => null);
  if (!ch) return;
  const msgs = await ch.messages.fetch({ limit: 50 }).catch(() => null);
  if (!msgs) return;
  for (const m of msgs.values()) {
    if (m.author.id !== client.user.id) continue;
    const hit = m.content.includes(OLD_REPO)
      || m.embeds.some((e) => (e.description || '').includes(OLD_REPO) || (e.url || '').includes(OLD_REPO));
    if (!hit) continue;
    const edit = {};
    if (m.content.includes(OLD_REPO)) edit.content = m.content.replaceAll(OLD_REPO, 'neuberghero-debug/nox');
    if (m.embeds.length) {
      edit.embeds = m.embeds.map((e) => EmbedBuilder.from(e)
        .setDescription((e.description || '').replaceAll(OLD_REPO, 'neuberghero-debug/nox'))
        .setURL(e.url ? e.url.replace(OLD_REPO, 'neuberghero-debug/nox') : e.url));
    }
    await m.edit(edit).then(() => console.log('[fix] repo link updated in', m.id)).catch(() => {});
  }
}

const ONBOARD_MSGS = [
  `**NOX — Developer / Designer Onboarding**\n\n` +
  `**What this is:** NOX is a fully open-source Fabric client for **Minecraft 26.3**, built for DonutSMP. Java 25, Fabric Loom, custom GUI/HUD, ~130 modules, premium licensing, Discord integration.\n\n` +
  `**Repo:** ${REPO_URL}\n` +
  `**Download:** GitHub Releases → \`nox-0.1.0-alpha.jar\` → drop into a Fabric 26.3 mods folder.\n\n` +
  `**Layout:** \`src/main/java/dev/noxclient\` — \`module/\` modules, \`gui/\` screens+helpers, \`hud/\` widgets, \`settings/\` typed settings, \`discord/\` Rich Presence, \`premium/\` licensing, \`util/TamperGuard\` (always on, never exposed as a module).`,

  `**Build & test — read this first**\n` +
  `• \`bash tools/devloop.sh\` — build → \`nox_doctor\` static checks → atomic deploy to the Modrinth NOX profile. Never \`cp\` onto the deployed jar.\n` +
  `• \`python3 tools/nox_doctor.py\` must pass (0 errors) before anything ships — it validates mixins, jar integrity, TamperGuard signature, assets.\n` +
  `• Runtime testing is mandatory, not optional: \`tools/relaunch.sh\`, then drive the client via \`python3 tools/noxctl.py "gui|shot|state|toggle|set|click X Y [B]"\`.\n` +
  `• Every module must actually *work* in-game — "compiles" is not "done".\n\n` +
  `**Module conventions**\n` +
  `• Every module: useful typed \`Setting\`s, \`status()\` one-liner showing live state (e.g. \`ESP [on] 0 entities tracked\`).\n` +
  `• Toggle **and** Hold activation modes; keybinds are NOX-internal (separate from MC keys).\n` +
  `• Animations are delta-time based — never fixed per-frame steps. Colors are full ARGB.\n` +
  `• 26.3 API quirks: \`Identifier\` (not ResourceLocation), \`GuiGraphicsExtractor\`, SDL scancodes, endpoint-based \`blit\`. See \`AGENTS.md\` in the repo — it lists every trap we already hit.`,

  `**Design system (must match)**\n` +
  `• Palette: bg \`#0D0F14\`, panel \`#141821\`, card \`#1C222E\`, accent \`#8B5CF6\` (theme-cyclable), text \`#E6EAF2\` / muted \`#8B93A7\`.\n` +
  `• Components: rounded everything (r 10–16), subtle \`rgba(255,255,255,0.08)\` borders, NanoVG glow/vignette/sheen pass, glassmorphism over solid black.\n` +
  `• GUI modes: Cards / Compact / Sidebar / Panels (floating columns + top pill) / Hub (sidebar + tiles) — new screens must respect the mode system and \`Interface\` settings.\n` +
  `• Reference direction: modern high-end clients (Rise, Novoline, Linear-style dark UI) — **inspired, never cloned 1:1**.\n\n` +
  `**Allowed / not allowed**\n` +
  `• ✅ Modules, HUD widgets, themes, cosmetics, addons, docs, bug reports, UI/UX design, marketing assets.\n` +
  `• ❌ No secrets/keys/tokens in the repo, no external payload downloads, no code that harms users' machines, no ripping other clients' assets/names.\n` +
  `• TamperGuard stays hidden and always-on — don't wire it into module lists.\n\n` +
  `**Contribute:** fork → PR against \`main\` → describe what you tested at runtime. Questions → this channel or a ticket in <#1570449832496074956>. Active areas: module polish, NanoVG effects, HUD widgets, docs.`,
];

/* The host filesystem is ephemeral — state.json does not survive restarts.
   Every one-shot must therefore verify against channel history, not state. */
async function postOnboarding(guild) {
  const ch = await guild.channels.fetch(ONBOARD_CHANNEL).catch(() => null);
  if (!ch) { console.warn('[onboard] channel missing'); return; }
  const recent = await ch.messages.fetch({ limit: 50 }).catch(() => null);
  const ours = recent ? [...recent.values()].filter((m) =>
    m.author.id === client.user.id && m.content.includes('Developer / Designer Onboarding')) : [];
  if (ours.length >= ONBOARD_MSGS.length) {
    console.log('[onboard] already present — skipped');
    return;
  }
  // partial/stale posts → remove, then post the full set once
  for (const m of ours) await m.delete().catch(() => {});
  for (const text of ONBOARD_MSGS) {
    await ch.send(text).then((m) => m.pin().catch(() => {})).catch((e) => console.warn('[onboard] send', e.message));
    await new Promise((r) => setTimeout(r, 800));
  }
  console.log('[onboard] posted', ONBOARD_MSGS.length);
}

/* ------------------------------------------------------------------ events */

client.once(Events.ClientReady, async () => {
  console.log(`[ready] ${client.user.tag}`);
  const guild = await client.guilds.fetch(config.guildId).catch(() => null);
  if (guild) {
    if ((state.styleV ?? 0) < STYLE_V) {
      await restyleChannels(guild).catch((e) => console.warn('[style]', e.message));
      state.styleV = STYLE_V;
      save();
    }
    if (!state.fixRepoV1) {
      await fixRepoLinks(guild).catch((e) => console.warn('[fix]', e.message));
      state.fixRepoV1 = true;
      save();
    }
    if (!state.onboardV1) {
      await postOnboarding(guild).catch((e) => console.warn('[onboard]', e.message));
      state.onboardV1 = true;
      save();
    }
    await ensurePanels(guild);
    await rehydrateOrders(guild).catch((e) => console.warn('[order]', e.message));
    await syncStickers(guild);
    // Discord attachment URLs expire after 24h – refresh the banners every 6h.
    setInterval(() => {
      ensurePanels(guild).catch(console.error);
      syncStickers(guild).catch(console.error);
    }, 6 * 60 * 60 * 1000);
    try {
      await guild.commands.set(COMMANDS.map((c) => c.toJSON()));
      console.log('[commands] registered', COMMANDS.length);
    } catch (err) {
      console.error('[commands]', err.message);
    }
  }
  await updateStats();
  setInterval(() => updateStats().catch(console.error), 5 * 60 * 1000);
  await orderSweep();
  setInterval(() => orderSweep().catch(console.error), 5 * 60 * 1000);
});

client.on(Events.GuildMemberAdd, async (member) => {
  if (member.user.bot) return;
  const ch = await member.guild.channels.fetch(config.channels.general).catch(() => null);
  if (ch) {
    ch.send({ embeds: [baseEmbed('Welcome', `<@${member.id}> — welcome to NOX.\nChat is open. Grab roles in <#${config.channels.roles}> if you want them.`)] }).catch(() => {});
  }
});

client.on(Events.InteractionCreate, async (interaction) => {
  try {
    if (interaction.isChatInputCommand()) return await handleCommand(interaction);

    if (interaction.isButton()) {
      const [scope, action] = interaction.customId.split(':');

      if (scope === 'verify') {
        await interaction.member.roles.add([config.roles.verified, config.roles.member]).catch(() => {});
        return interaction.reply({ content: 'Verified. Welcome to NOX.', flags: MessageFlags.Ephemeral });
      }

      if (scope === 'role') {
        const roleId = config.roles[action];
        if (!roleId) return interaction.reply({ content: 'Unknown role.', flags: MessageFlags.Ephemeral });
        const member = await interaction.guild.members.fetch(interaction.user.id);
        const has = member.roles.cache.has(roleId);
        if (has) await member.roles.remove(roleId);
        else await member.roles.add(roleId);
        return interaction.reply({ content: has ? 'Role removed.' : 'Role added.', flags: MessageFlags.Ephemeral });
      }

      if (scope === 'ping') {
        const roleId = config.roles[action];
        if (!roleId) return interaction.reply({ content: 'Unknown role.', flags: MessageFlags.Ephemeral });
        const member = await interaction.guild.members.fetch(interaction.user.id);
        const has = member.roles.cache.has(roleId);
        if (has) await member.roles.remove(roleId);
        else await member.roles.add(roleId);
        return interaction.reply({ content: has ? 'Notifications off.' : 'Notifications on.', flags: MessageFlags.Ephemeral });
      }

      if (scope === 'ticket' && action === 'claim') {
        const staff = await memberHasRole(interaction, config.roles.support)
          || interaction.memberPermissions?.has(PermissionFlagsBits.ManageChannels);
        if (!staff) return interaction.reply({ content: 'Only staff can claim a ticket.', flags: MessageFlags.Ephemeral });
        const ticket = state.tickets[interaction.channel.id];
        if (ticket) {
          ticket.claimedBy = interaction.user.id;
          save();
        }
        await interaction.channel.setTopic(`${ticket?.label ?? 'Ticket'} · claimed by ${interaction.user.tag}`).catch(() => {});
        return interaction.reply({ content: `Claimed by <@${interaction.user.id}>.` });
      }

      if (scope === 'ticket' && action === 'close') {
        return closeTicket(interaction);
      }

      if (scope === 'buy' && action === 'start') {
        const products = config.products || [];
        if (!products.length) return interaction.reply({ content: 'Nothing for sale right now.', flags: MessageFlags.Ephemeral });
        if (products.length === 1) return openOrder(interaction, products[0]);
        const menu = new StringSelectMenuBuilder()
          .setCustomId('buy:pick')
          .setPlaceholder('Choose a product')
          .addOptions(products.map((p, i) => ({ label: p.name, value: String(i), description: `${p.price} — ${String(p.description).slice(0, 80)}` })));
        return interaction.reply({
          content: 'Pick a product — a private order channel opens for you.',
          components: [new ActionRowBuilder().addComponents(menu)],
          flags: MessageFlags.Ephemeral,
        });
      }

      if (scope === 'order') {
        const order = state.orders[interaction.customId.split(':')[2]];
        if (!order) return interaction.reply({ content: 'Unknown order.', flags: MessageFlags.Ephemeral });
        const mine = order.userId === interaction.user.id;
        const staff = await isStaff(interaction);

        if (action === 'tx') {
          if (!mine) return interaction.reply({ content: 'This is not your order.', flags: MessageFlags.Ephemeral });
          if (order.status !== 'open' && order.status !== 'review') {
            return interaction.reply({ content: `Order is ${order.status}.`, flags: MessageFlags.Ephemeral });
          }
          const modal = new ModalBuilder().setCustomId(`txmodal:${order.id}`).setTitle('Submit payment proof');
          modal.addComponents(new ActionRowBuilder().addComponents(
            new TextInputBuilder().setCustomId('txref').setLabel('TXID / explorer link')
              .setStyle(TextInputStyle.Short).setRequired(true).setMaxLength(200),
          ));
          return interaction.showModal(modal);
        }
        if (action === 'cancel') {
          if (!mine && !staff) return interaction.reply({ content: 'Not your order.', flags: MessageFlags.Ephemeral });
          if (order.status !== 'open' && order.status !== 'review') {
            return interaction.reply({ content: `Order is already ${order.status}.`, flags: MessageFlags.Ephemeral });
          }
          order.status = 'cancelled'; save();
          await interaction.update({ embeds: [orderEmbed(order, `Cancelled by <@${interaction.user.id}>.`)], components: [] });
          setTimeout(() => interaction.channel.delete().catch(() => {}), 60_000);
          return;
        }
        if (action === 'paid' || action === 'refund') {
          if (!staff) return interaction.reply({ content: 'Staff only.', flags: MessageFlags.Ephemeral });
          if (order.status === 'paid' || order.status === 'cancelled' || order.status === 'expired') {
            return interaction.reply({ content: `Order is already ${order.status}.`, flags: MessageFlags.Ephemeral });
          }
          await interaction.update({ embeds: [orderEmbed(order, 'Processing…')], components: [] });
          return finishOrder(interaction, order, action === 'paid');
        }
      }
    }

    if (interaction.isModalSubmit() && interaction.customId.startsWith('txmodal:')) {
      const order = state.orders[interaction.customId.split(':')[1]];
      if (!order || order.userId !== interaction.user.id) {
        return interaction.reply({ content: 'Unknown order.', flags: MessageFlags.Ephemeral });
      }
      order.txRef = interaction.fields.getTextInputValue('txref').slice(0, 200);
      order.status = 'review';
      save();
      await interaction.reply({ content: `${ce('noxcheck')}Payment proof submitted — staff will verify on-chain and mark the order paid.`, flags: MessageFlags.Ephemeral });
      await interaction.channel.send({
        content: `<@&${config.roles.support}>`,
        embeds: [orderEmbed(order, `🔎 **Payment proof submitted** by <@${order.userId}> — verify on-chain, then press **Mark paid**.`)],
        components: orderRows(order),
      });
      return;
    }

    if (interaction.isStringSelectMenu() && interaction.customId === 'buy:pick') {
      const product = (config.products || [])[Number(interaction.values[0])];
      if (!product) return interaction.reply({ content: 'Unknown product.', flags: MessageFlags.Ephemeral });
      return openOrder(interaction, product);
    }

    if (interaction.isStringSelectMenu() && interaction.customId === 'ticket:create') {
      await interaction.deferReply({ flags: MessageFlags.Ephemeral });
      return createTicket(interaction, interaction.values[0]);
    }
  } catch (err) {
    console.error(err);
    if (interaction.deferred || interaction.replied) {
      interaction.followUp({ content: 'Something went wrong.', flags: MessageFlags.Ephemeral }).catch(() => {});
    } else {
      interaction.reply({ content: 'Something went wrong.', flags: MessageFlags.Ephemeral }).catch(() => {});
    }
  }
});

process.on('unhandledRejection', console.error);
for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    console.log(`[exit] ${sig} — shutting down`);
    client.destroy();
    process.exit(0);
  });
}
client.login(config.token);
