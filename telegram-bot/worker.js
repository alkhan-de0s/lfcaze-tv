/**
 * LFCAZE TV - Liverpool Telegram Admin Bot (v2.0)
 * Cloudflare Worker (100% Free, 24/7 Serverless)
 *
 * Features:
 * - / (Slash) command autocomplete menu
 * - Live channel search with interactive buttons (/ara <kelime>)
 * - 1-Click channel adding via inline buttons
 * - Instant synchronization with GitHub liverpool.json & CloudStream
 */

const GITHUB_REPO = "alkhan-de0s/lfcaze-tv";
const FILE_PATH = "liverpool.json";
const CHANNELS_FILE_URL = `https://raw.githubusercontent.com/${GITHUB_REPO}/master/channels.json`;

let cachedChannels = null;

const BOT_COMMANDS = [
  { command: "mac", description: "⚽ Maç adını ayarlar (Örn: /mac Liverpool vs Chelsea)" },
  { command: "ara", description: "🔍 Kanal ara ve tek tıkla ekle (Örn: /ara sky veya /ara bein)" },
  { command: "kanallar", description: "📺 Kanal ID'lerini elle ekle (Örn: /kanallar 35, 38)" },
  { command: "durum", description: "📊 Mevcut maçı ve seçili yayınları gösterir" },
  { command: "temizle", description: "🗑️ Seçili kanalları sıfırlar" },
  { command: "ac", description: "🟢 Liverpool bölümünü CloudStream'de açar" },
  { command: "kapat", description: "🔴 Liverpool bölümünü kapatır" },
  { command: "yardim", description: "ℹ️ Komut rehberini gösterir" }
];

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return new Response("LFCAZE TV Bot v2.0 is running!", { status: 200 });
    }

    try {
      const update = await request.json();

      // Handle button clicks (Callback Query)
      if (update.callback_query) {
        await handleCallbackQuery(update.callback_query, env);
        return new Response("OK", { status: 200 });
      }

      // Handle text messages
      if (update.message) {
        await handleMessage(update.message, env);
      }
    } catch (e) {
      console.error("Worker error:", e);
    }

    return new Response("OK", { status: 200 });
  }
};

async function handleMessage(msg, env) {
  const chatId = msg.chat.id;
  const text = (msg.text || "").trim();

  // Optional: check admin ID if configured
  if (env.ADMIN_TELEGRAM_ID && String(chatId) !== String(env.ADMIN_TELEGRAM_ID)) {
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "⛔ Yetkisiz erişim!");
    return;
  }

  // Register commands on /start
  if (text === "/start" || text === "/yardim" || text === "/help") {
    await registerCommands(env.TELEGRAM_BOT_TOKEN);
    const welcome = `🔴 *LFCAZE TV - Liverpool Maç Yönetim Botu* 🔴\n\n` +
      `*Kullanabileceğiniz Komutlar:*\n\n` +
      `🔍 \`/ara <kanal adı>\` ➡️ Kanalları arar ve tıklanabilir ekleme butonları sunar! (Örn: \`/ara sky\` veya \`/ara bein\`)\n\n` +
      `⚽ \`/mac <Maç Adı>\` ➡️ Maç adı belirler ve bölümü açar (Örn: \`/mac Liverpool vs Real Madrid\`)\n\n` +
      `📺 \`/kanallar <ID'ler>\` ➡️ Kanal numaralarını toplu yazar (Örn: \`/kanallar 35, 38, 120\`)\n\n` +
      `📊 \`/durum\` ➡️ Şu an CloudStream'de hangi maç ve kanalların olduğunu gösterir\n\n` +
      `🗑️ \`/temizle\` ➡️ Yeni maça geçerken eski kanalları temizler\n\n` +
      `🟢 \`/ac\` | 🔴 \`/kapat\` ➡️ Bölümü anında açar / kapatır`;
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, welcome);
    return;
  }

  // Live search: /ara <kelime>
  if (text.startsWith("/ara")) {
    const query = text.substring(4).trim().toLowerCase();
    if (!query) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "Lütfen aramak istediğiniz kanal adını yazın.\nÖrnek: `/ara sky` veya `/ara bein` veya `/ara premier`");
      return;
    }

    const channels = await getChannelList();
    const matches = channels.filter(c => c.name.toLowerCase().includes(query)).slice(0, 10);

    if (matches.length === 0) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, `❌ "${query}" içeren kanal bulunamadı.`);
      return;
    }

    // Build inline keyboard buttons
    const keyboard = matches.map(c => [
      {
        text: `➕ ${c.name} (ID: ${c.id})`,
        callback_data: `add_${c.id}`
      }
    ]);

    await sendTgKeyboard(
      env.TELEGRAM_BOT_TOKEN,
      chatId,
      `🔍 *"${query}" için bulunan kanallar:* (Eklemek istediğinize tıklayın)`,
      keyboard
    );
    return;
  }

  // Set match name: /mac <Maç Adı>
  if (text.startsWith("/mac ")) {
    const matchName = text.substring(5).trim();
    if (!matchName) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "Lütfen maç adı girin: `/mac Liverpool vs Arsenal`");
      return;
    }
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "❌ GitHub bağlantı hatası!");
      return;
    }
    fileData.data.match = matchName;
    fileData.data.active = true;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, `✅ Maç belirlendi ve CloudStream'de açıldı:\n⚽ *${matchName}*\n\nŞimdi \`/ara\` komutuyla kanalları ekleyebilirsiniz.`);
    return;
  }

  // Set multiple channel IDs: /kanallar 35, 38, 120
  if (text.startsWith("/kanallar ")) {
    const input = text.substring(10).trim();
    const parts = input.split(/[, ]+/).filter(Boolean);
    if (parts.length === 0) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "Lütfen kanal ID'lerini yazın: `/kanallar 35, 38`");
      return;
    }

    const allChannels = await getChannelList();
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;

    const newChannels = parts.map((id, index) => {
      const found = allChannels.find(c => c.id === id);
      const name = found ? found.name : `Kanal ${id}`;
      return {
        id: id,
        name: `Yayın ${index + 1} (${name})`
      };
    });

    fileData.data.channels = newChannels;
    fileData.data.active = true;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);

    let resMsg = `✅ *Kanallar kaydedildi:*\n`;
    for (const ch of newChannels) {
      resMsg += `• ${ch.name} (ID: ${ch.id})\n`;
    }
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, resMsg);
    return;
  }

  // Show status: /durum
  if (text === "/durum") {
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "❌ liverpool.json okunamadı.");
      return;
    }
    const statusEmoji = fileData.data.active ? "🟢 Aktif (CloudStream'de Görünüyor)" : "🔴 Kapalı";
    let statusMsg = `📊 *Mevcut Durum:* ${statusEmoji}\n` +
      `⚽ *Maç:* ${fileData.data.match || "Belirtilmemiş"}\n\n` +
      `📺 *Seçili Yayın Kanalları:*\n`;
    const channels = fileData.data.channels || [];
    if (channels.length === 0) {
      statusMsg += `_(Henüz kanal eklenmemiş. /ara komutuyla ekleyebilirsiniz)_\n`;
    } else {
      channels.forEach((ch, idx) => {
        statusMsg += `${idx + 1}. *${ch.name}* (ID: \`${ch.id}\`)\n`;
      });
    }
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, statusMsg);
    return;
  }

  // Clear channels: /temizle
  if (text === "/temizle") {
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;
    fileData.data.channels = [];
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "🗑️ Tüm maç yayın kanalları temizlendi. \`/ara\` komutuyla yenilerini ekleyebilirsiniz.");
    return;
  }

  // Enable: /ac
  if (text === "/ac") {
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;
    fileData.data.active = true;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "🟢 Liverpool maç kategorisi CloudStream'de *AKTİF* edildi!");
    return;
  }

  // Disable: /kapat
  if (text === "/kapat") {
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;
    fileData.data.active = false;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "🔴 Liverpool maç kategorisi *KAPATILDI*.");
    return;
  }

  await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "Komut anlaşılamadı. Komutları görmek için `/` yazabilir veya /yardim diyebilirsiniz.");
}

// Handle clicking inline button (+ Channel)
async function handleCallbackQuery(cb, env) {
  const data = cb.data;
  const chatId = cb.message.chat.id;

  if (data.startsWith("add_")) {
    const channelId = data.substring(4);
    const channels = await getChannelList();
    const ch = channels.find(c => c.id === channelId);
    const chName = ch ? ch.name : `Kanal ${channelId}`;

    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) {
      await answerCallback(env.TELEGRAM_BOT_TOKEN, cb.id, "GitHub hatası!");
      return;
    }

    const currentList = fileData.data.channels || [];
    // Prevent duplicate
    if (currentList.some(c => c.id === channelId)) {
      await answerCallback(env.TELEGRAM_BOT_TOKEN, cb.id, "⚠️ Bu kanal zaten ekli!");
      return;
    }

    const nextIndex = currentList.length + 1;
    currentList.push({
      id: channelId,
      name: `Yayın ${nextIndex} (${chName})`
    });

    fileData.data.channels = currentList;
    fileData.data.active = true;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);

    await answerCallback(env.TELEGRAM_BOT_TOKEN, cb.id, `✅ ${chName} maça eklendi!`, true);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, `✅ *${chName}* (ID: ${channelId}) maça başarıyla eklendi!\nŞu an toplam *${currentList.length}* kanal yayında.`);
  }
}

async function getChannelList() {
  if (cachedChannels && cachedChannels.length > 0) {
    return cachedChannels;
  }
  try {
    const res = await fetch(CHANNELS_FILE_URL, { headers: { "Cache-Control": "no-cache" } });
    if (res.ok) {
      cachedChannels = await res.json();
      return cachedChannels;
    }
  } catch (e) {
    console.error("Failed to fetch channels.json:", e);
  }
  return [];
}

async function registerCommands(botToken) {
  const url = `https://api.telegram.org/bot${botToken}/setMyCommands`;
  await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ commands: BOT_COMMANDS })
  });
}

async function getLiverpoolJson(token) {
  const url = `https://api.github.com/repos/${GITHUB_REPO}/contents/${FILE_PATH}`;
  const res = await fetch(url, {
    headers: {
      "Authorization": `Bearer ${token}`,
      "User-Agent": "LFCAZE-Bot",
      "Accept": "application/vnd.github+json"
    }
  });
  if (!res.ok) return null;
  const json = await res.json();
  const content = atob(json.content.replace(/\s/g, ""));
  return {
    sha: json.sha,
    data: JSON.parse(content)
  };
}

async function updateLiverpoolJson(token, data, sha) {
  const url = `https://api.github.com/repos/${GITHUB_REPO}/contents/${FILE_PATH}`;
  const updatedContent = btoa(unescape(encodeURIComponent(JSON.stringify(data, null, 2))));
  const res = await fetch(url, {
    method: "PUT",
    headers: {
      "Authorization": `Bearer ${token}`,
      "User-Agent": "LFCAZE-Bot",
      "Accept": "application/vnd.github+json",
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      message: `admin: update ${data.match || "match"} via bot`,
      content: updatedContent,
      sha: sha
    })
  });
  return res.ok;
}

async function sendTg(botToken, chatId, text) {
  const url = `https://api.telegram.org/bot${botToken}/sendMessage`;
  await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      chat_id: chatId,
      text: text,
      parse_mode: "Markdown"
    })
  });
}

async function sendTgKeyboard(botToken, chatId, text, keyboard) {
  const url = `https://api.telegram.org/bot${botToken}/sendMessage`;
  await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      chat_id: chatId,
      text: text,
      parse_mode: "Markdown",
      reply_markup: { inline_keyboard: keyboard }
    })
  });
}

async function answerCallback(botToken, callbackQueryId, text, showAlert = false) {
  const url = `https://api.telegram.org/bot${botToken}/answerCallbackQuery`;
  await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      callback_query_id: callbackQueryId,
      text: text,
      show_alert: showAlert
    })
  });
}
