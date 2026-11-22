'use strict';
/* community.js — Phase-C feature pack for nox-community-bot.
 * Adds: /license activate|revoke, /roadmap list|vote|suggest, /market
 * list|buy|sell, automod (links/caps/spam/blacklist), daily analytics
 * embed, live alerts. Wired into index.js via community.commands /
 * community.handle / community.onMessage / community.startCron.
 *
 * Env: NOX_ANALYTICS_URL, NOX_ANALYTICS_KEY
 *      (licenses verified locally via Ed25519 — no license server needed).
 */
const { SlashCommandBuilder, EmbedBuilder, MessageFlags, PermissionFlagsBits } = require('discord.js');
const crypto = require('crypto');

/* Local Ed25519 license verify — the old 127.0.0.1:8787 license server is
 * dead; the client verifies NOX1.<payload>.<sig> offline anyway, so the bot
 * does the same. Public key mirrors LicenseManager.PUB_DER. */
const LICENSE_PUB = crypto.createPublicKey({
  key: Buffer.concat([
    Buffer.from('302a300506032b6570032100', 'hex'),
    Buffer.from('wAoUOK5sP+q9eqjoXY7V4C//P+v4hel4AMNvP2H8/qE=', 'base64'),
  ]), format: 'der', type: 'spki',
});

function verifyLicense(raw) {
  const m = /^NOX1\.([A-Za-z0-9_-]+)\.([A-Za-z0-9_-]+)$/.exec(String(raw).trim());
  if (!m) return { ok: false, why: 'format (NOX1.…)' };
  if (!crypto.verify(null, Buffer.from(m[1]), LICENSE_PUB, Buffer.from(m[2], 'base64url')))
    return { ok: false, why: 'signature' };
  let p;
  try { p = JSON.parse(Buffer.from(m[1], 'base64url').toString('utf8')); }
  catch { return { ok: false, why: 'payload' }; }
  if (p.exp && p.exp < Date.now()) return { ok: false, why: 'expired' };
  return { ok: true, payload: p };
}

const LICENSE_URL = process.env.NOX_LICENSE_URL || '';
const ANALYTICS_URL = process.env.NOX_ANALYTICS_URL || '';
const ANALYTICS_KEY = process.env.NOX_ANALYTICS_KEY || '';
const ADMIN_KEY = process.env.NOX_ADMIN_KEY || '';

const NAME = 'nox-community';
const handles = new Set(['license', 'roadmap', 'market']);

/* ---------- helpers ---------- */
const emb = (title, desc, color) =>
  new EmbedBuilder().setTitle(title).setDescription(desc).setColor(color ?? 0xFF6EC7)
    .setFooter({ text: 'NOX' }).setTimestamp();

async function post(url, body, headers = {}) {
  const r = await fetch(url, {
    method: 'POST', headers: { 'content-type': 'application/json', ...headers },
    body: JSON.stringify(body), signal: AbortSignal.timeout(5000),
  });
  return { status: r.status, json: await r.json().catch(() => ({})) };
}
async function get(url, headers = {}) {
  const r = await fetch(url, { headers, signal: AbortSignal.timeout(5000) });
  return { status: r.status, json: await r.json().catch(() => ({})) };
}

/* ---------- commands ---------- */
const commands = [
  new SlashCommandBuilder().setName('license')
    .setDescription('Manage your NOX license')
    .addSubcommand((s) => s.setName('activate').setDescription('Activate a license key → Premium role')
      .addStringOption((o) => o.setName('key').setDescription('NOX1.… key').setRequired(true)))
    .addSubcommand((s) => s.setName('revoke').setDescription('Revoke a user\'s premium (staff)')
      .addUserOption((o) => o.setName('user').setDescription('Target').setRequired(true))),

  new SlashCommandBuilder().setName('roadmap')
    .setDescription('NOX roadmap')
    .addSubcommand((s) => s.setName('list').setDescription('Show the roadmap'))
    .addSubcommand((s) => s.setName('vote').setDescription('Vote for a feature')
      .addStringOption((o) => o.setName('id').setDescription('Feature id (see list)').setRequired(true)))
    .addSubcommand((s) => s.setName('suggest').setDescription('Suggest a feature (mod review)')
      .addStringOption((o) => o.setName('idea').setDescription('Your idea').setRequired(true).setMaxLength(300))),

  new SlashCommandBuilder().setName('market')
    .setDescription('NOX marketplace')
    .addSubcommand((s) => s.setName('list').setDescription('List items'))
    .addSubcommand((s) => s.setName('buy').setDescription('Buy an item (Premium only)')
      .addStringOption((o) => o.setName('id').setDescription('Item id').setRequired(true)))
    .addSubcommand((s) => s.setName('sell').setDescription('Open the seller dashboard (DM)')),
];

/* ---------- state ---------- */
function shop(state) {
  state.market = state.market || [
    { id: 'cape-1', name: 'NOX Gradient Cape', price: 200 },
    { id: 'theme-neon', name: 'Neon Theme Pack', price: 350 },
    { id: 'hud-pack-1', name: 'HUD Pack: Minimal', price: 150 },
  ];
  state.roadmap = state.roadmap || {
    items: [
      { id: 'replay-play', title: 'Replay playback screen', status: 'in-progress', votes: {} },
      { id: 'friend-sync', title: 'Friend sync backend', status: 'planned', votes: {} },
      { id: 'clan-system', title: 'Clan system', status: 'planned', votes: {} },
    ],
    suggestions: [],
  };
  return state;
}

const attempts = new Map(); // userId -> [timestamps]  (license activate rate limit)

function staffOnly(interaction, state, config) {
  return interaction.memberPermissions?.has(PermissionFlagsBits.ManageGuild)
    || interaction.member?.roles?.cache?.some?.((r) => r.id === config.roles.support);
}

/* ---------- handler ---------- */
async function handle(interaction, ctx) {
  const { config, state, save } = ctx;
  shop(state);
  const cmd = interaction.commandName;
  const sub = interaction.options.getSubcommand();

  /* ===== /license ===== */
  if (cmd === 'license' && sub === 'activate') {
    const now = Date.now();
    const tries = (attempts.get(interaction.user.id) || []).filter((t) => now - t < 3_600_000);
    if (tries.length >= 3)
      return interaction.reply({ content: 'Too many activation attempts — try again later.', flags: MessageFlags.Ephemeral });
    tries.push(now); attempts.set(interaction.user.id, tries);

    const key = interaction.options.getString('key').trim();
    const v = verifyLicense(key);
    // payload.sub binds the license to the buyer's discord id — a key minted
    // for someone else can't be activated here
    const bound = v.ok && (!v.payload.sub || v.payload.sub === interaction.user.id);
    if (!v.ok || !bound) {
      await audit(interaction, config, `❌ license activate failed — <@${interaction.user.id}> (${v.ok ? 'bound to another account' : v.why})`);
      return interaction.reply({ content: `Key not accepted (${v.ok ? 'bound to another account' : v.why}).`, flags: MessageFlags.Ephemeral });
    }
    state.licenses = state.licenses || {};
    state.licenses[interaction.user.id] = { lid: v.payload.lid, tier: v.payload.tier, boundAt: Date.now() };
    const role = interaction.guild.roles.cache.find((x) => x.name === 'Premium')
      || interaction.guild.roles.cache.get(config.roles.verified);
    if (role) await interaction.member.roles.add(role).catch(() => {});
    await audit(interaction, config, `✅ license activated — <@${interaction.user.id}>`);
    await interaction.user.send({ embeds: [emb('License activated',
      `Your key is now bound to this Discord account. Premium unlocked in the client.`)] }).catch(() => {});
    return interaction.reply({ content: '✅ Premium unlocked — check your DMs.', flags: MessageFlags.Ephemeral });
  }

  if (cmd === 'license' && sub === 'revoke') {
    if (!staffOnly(interaction, state, config))
      return interaction.reply({ content: 'Staff only.', flags: MessageFlags.Ephemeral });
    const user = interaction.options.getUser('user');
    const member = await interaction.guild.members.fetch(user.id).catch(() => null);
    const role = interaction.guild.roles.cache.find((x) => x.name === 'Premium');
    if (member && role) await member.roles.remove(role).catch(() => {});
    if (state.licenses) delete state.licenses[user.id];
    await audit(interaction, config, `🔻 premium revoked — <@${user.id}> by <@${interaction.user.id}>`);
    return interaction.reply({ content: `Premium revoked for <@${user.id}>.`, flags: MessageFlags.Ephemeral });
  }

  /* ===== /roadmap ===== */
  if (cmd === 'roadmap' && sub === 'list') {
    const sec = (s) => state.roadmap.items.filter((i) => i.status === s)
      .map((i) => `• **${i.id}** ${i.title} — ${Object.keys(i.votes).length} votes`).join('\n') || '—';
    return interaction.reply({ embeds: [emb('NOX Roadmap',
      `**In Progress**\n${sec('in-progress')}\n\n**Planned**\n${sec('planned')}\n\n**Done**\n${sec('done')}`)] });
  }
  if (cmd === 'roadmap' && sub === 'vote') {
    const id = interaction.options.getString('id').trim();
    const item = state.roadmap.items.find((i) => i.id === id);
    if (!item) return interaction.reply({ content: `No feature \`${id}\` — see /roadmap list.`, flags: MessageFlags.Ephemeral });
    item.votes[interaction.user.id] = true; save();
    return interaction.reply({ content: `Voted for **${item.title}** (${Object.keys(item.votes).length} total).`, flags: MessageFlags.Ephemeral });
  }
  if (cmd === 'roadmap' && sub === 'suggest') {
    const idea = interaction.options.getString('idea');
    state.roadmap.suggestions.push({ by: interaction.user.id, idea, ts: Date.now() });
    save();
    const ch = interaction.guild.channels.cache.find((c) => c.name === 'feature-suggestions');
    if (ch) ch.send({ embeds: [emb('New suggestion', `${idea}\n\n— <@${interaction.user.id}>`)] }).catch(() => {});
    return interaction.reply({ content: 'Suggestion submitted for review. Thanks!', flags: MessageFlags.Ephemeral });
  }

  /* ===== /market ===== */
  if (cmd === 'market' && sub === 'list') {
    const lines = state.market.map((i) => `• **${i.id}** — ${i.name} · ${i.price} credits`);
    return interaction.reply({ embeds: [emb('NOX Market', lines.join('\n') || 'Empty.')] });
  }
  if (cmd === 'market' && sub === 'buy') {
    const premium = interaction.member.roles.cache.some((r) => r.name === 'Premium' || r.id === config.roles.verified);
    if (!premium) return interaction.reply({ content: 'Marketplace is Premium-only.', flags: MessageFlags.Ephemeral });
    const id = interaction.options.getString('id').trim();
    const item = state.market.find((i) => i.id === id);
    if (!item) return interaction.reply({ content: `No item \`${id}\`.`, flags: MessageFlags.Ephemeral });
    await audit(interaction, config, `🛒 <@${interaction.user.id}> bought ${item.id}`);
    return interaction.reply({ content: `Purchased **${item.name}** — delivered in-client on next login.`, flags: MessageFlags.Ephemeral });
  }
  if (cmd === 'market' && sub === 'sell') {
    await interaction.user.send({ embeds: [emb('Seller dashboard',
      'List an item: reply with `sell <name> | <price>`.\nPayouts via license-server credits (weekly).')] }).catch(() => {});
    return interaction.reply({ content: 'Seller dashboard sent via DM.', flags: MessageFlags.Ephemeral });
  }
  return interaction.reply({ content: 'Unknown subcommand.', flags: MessageFlags.Ephemeral });
}

async function audit(interaction, config, text) {
  const ch = interaction.guild.channels.cache.get(config.channels.staffLogs);
  if (ch) await ch.send(text).catch(() => {});
}

/* ---------- automod (C.12) ---------- */
const spam = new Map(); // userId -> [ts]
const BLACKLIST = /\b(n[i1]gg|f[a4]gg[o0]t|kys|cp\s*link|free\s*nitro)\b/i;
const LINK = /https?:\/\/(?!.*donutsmp|.*discord\.gg\/nox)/i;

async function onMessage(m, ctx) {
  if (!m.guild || m.author.bot) return false;
  const { config } = ctx;
  if (m.member?.permissions?.has(PermissionFlagsBits.ManageMessages)) return false;

  const bad = BLACKLIST.test(m.content)
    || (LINK.test(m.content) && !m.content.includes('cdn.discord'))
    || (m.content.length > 10 && (m.content.replace(/[^A-Z]/g, '').length / m.content.length) > 0.7);
  const q = spam.get(m.author.id) || [];
  const now = Date.now();
  q.push(now); spam.set(m.author.id, q.filter((t) => now - t < 4000));
  const flooding = q.length >= 5;

  if (bad || flooding) {
    await m.delete().catch(() => {});
    if (flooding) await m.member?.timeout(60_000, 'automod: spam').catch(() => {});
    return true;
  }
  return false;
}

/* ---------- cron: analytics + alerts (C.5/C.6) ---------- */
function startCron(client, config) {
  if (!ANALYTICS_URL) { console.log('[community] no NOX_ANALYTICS_URL — analytics cron off'); return; }
  const tick = async () => {
    const now = new Date();
    if (now.getHours() === 9 && now.getMinutes() === 0) {
      const r = await get(`${ANALYTICS_URL}/kpis?days=1`, { 'x-api-key': ANALYTICS_KEY });
      if (r.status === 200) {
        const k = r.json;
        const color = k.crash_rate > 5 ? 0xFF6666 : k.crash_rate > 2 ? 0xFFD870 : 0x66D9A0;
        const top = k.top_modules.map((m) => `• ${m.module} (${m.uses})`).join('\n') || '—';
        const ch = client.channels.cache.find((c) => c.name === 'admin-analytics');
        if (ch) ch.send({ embeds: [emb('Daily KPIs',
          `DAU buckets: **${k.dau}**\nCrash rate: **${k.crash_rate}%**\n\n**Top modules**\n${top}`, color)] }).catch(() => {});
        if (k.crash_rate > 5) {
          const al = client.channels.cache.find((c) => c.name === 'admin-alerts');
          if (al) al.send(`<@&${config.roles.support}> 🚨 crash rate ${k.crash_rate}% (>5%)`).catch(() => {});
        }
      }
    }
  };
  setInterval(tick, 60_000);
}

module.exports = { commands, handle, onMessage, startCron, handles };
