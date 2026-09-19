/**
 * LFCAZE TV - Liverpool Telegram Admin Bot (v2.1)
 * Cloudflare Worker (100% Free, 24/7 Serverless)
 *
 * Akıllı Özellikler:
 * - Komut yazmadan doğrudan arama: Sadece "sky", "bein", "tnt" yazmanız yeterli!
 * - / (Slash) komut menüsü
 * - Tıklanabilir tek tıkla ekleme butonları
 * - Butona basınca anında "✅ Eklendi" görsel geri bildirimi
 * - Türkçe karakter (UTF-8) tam koruma
 */

const GITHUB_REPO = "alkhan-de0s/lfcaze-tv";
const FILE_PATH = "liverpool.json";
const CHANNELS_FILE_URL = `https://raw.githubusercontent.com/${GITHUB_REPO}/master/channels.json`;

let cachedChannels = null;

const BOT_COMMANDS = [
  { command: "mac", description: "⚽ Maç adını ayarlar (Örn: /mac Liverpool vs Chelsea)" },
  { command: "ara", description: "🔍 Kanal ara ve ekle (Doğrudan kanal adı da yazabilirsiniz)" },
  { command: "durum", description: "📊 Mevcut maçı ve seçili yayınları gösterir" },
  { command: "temizle", description: "🗑️ Seçili kanalları sıfırlar" },
  { command: "ac", description: "🟢 Liverpool bölümünü CloudStream'de açar" },
  { command: "kapat", description: "🔴 Liverpool bölümünü kapatır" },
  { command: "yardim", description: "ℹ️ Komut rehberini gösterir" }
];

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return new Response("LFCAZE TV Bot v2.1 is running!", { status: 200 });
    }

    try {
      const update = await request.json();

      // Buton tıklamaları (Callback Query)
      if (update.callback_query) {
        await handleCallbackQuery(update.callback_query, env);
        return new Response("OK", { status: 200 });
      }

      // Mesajlar
      if (update.message) {
        await handleMessage(update.message, env);
      }
    } catch (e) {
      console.error("Worker root error:", e);
    }

    return new Response("OK", { status: 200 });
  }
};

async function handleMessage(msg, env) {
  const chatId = msg.chat.id;
  const text = (msg.text || "").trim();

  if (!text) return;

  // /start veya /yardim
  if (text === "/start" || text === "/yardim" || text === "/help") {
    await registerCommands(env.TELEGRAM_BOT_TOKEN);
    const welcome = `🔴 *LFCAZE TV - Liverpool Maç Yönetim Botu* 🔴\n\n` +
      `Kanal eklemek için hiçbir komuta gerek yok! Doğrudan kanal adını yazabilirsiniz:\n\n` +
      `🔍 *Örnek aramalar:*\n` +
      `• \`sky\` ➡️ Tüm Sky kanallarını listeler\n` +
      `• \`bein\` ➡️ beIN kanallarını listeler\n` +
      `• \`tnt\` ➡️ TNT Sports kanallarını listeler\n` +
      `• \`premier\` ➡️ Premier League kanallarını listeler\n\n` +
      `*Diğer Komutlar:*\n` +
      `⚽ \`/mac <Maç Adı>\` ➡️ Maç adı belirler (Örn: \`/mac Liverpool vs Arsenal\`)\n` +
      `📊 \`/durum\` ➡️ Şu anki maç ve ekli kanalları gösterir\n` +
      `🗑️ \`/temizle\` ➡️ Kanalları sıfırlar\n` +
      `🟢 \`/ac\` | 🔴 \`/kapat\` ➡️ Bölümü açar / kapatır`;
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, welcome);
    return;
  }

  // /durum
  if (text === "/durum") {
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "❌ GitHub verisi okunamadı. GITHUB_TOKEN ayarını kontrol edin.");
      return;
    }
    const statusEmoji = fileData.data.active ? "🟢 Aktif (CloudStream'de Görünüyor)" : "🔴 Kapalı";
    let statusMsg = `📊 *Mevcut Durum:* ${statusEmoji}\n` +
      `⚽ *Maç:* ${fileData.data.match || "Belirtilmemiş"}\n\n` +
      `📺 *Seçili Yayın Kanalları:*\n`;
    const channels = fileData.data.channels || [];
    if (channels.length === 0) {
      statusMsg += `_(Henüz kanal eklenmemiş. Aşağıya kanal adı yazıp arayabilirsiniz)_\n`;
    } else {
      channels.forEach((ch, idx) => {
        statusMsg += `${idx + 1}. *${ch.name}* (ID: \`${ch.id}\`)\n`;
      });
    }
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, statusMsg);
    return;
  }

  // /mac <Maç Adı>
  if (text.startsWith("/mac")) {
    const matchName = text.replace(/^\/mac\s*/i, "").trim();
    if (!matchName) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "Lütfen maç adı da yazın:\nÖrnek: `/mac Liverpool vs Chelsea`");
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
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, `✅ *Maç belirlendi ve CloudStream'de açıldı!*\n⚽ *${matchName}*\n\nŞimdi kanalları eklemek için kanal adı yazabilirsiniz (örn: \`sky\` veya \`bein\`).`);
    return;
  }

  // /temizle
  if (text === "/temizle") {
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;
    fileData.data.channels = [];
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "🗑️ Tüm kanallar temizlendi. Yeni maça kanal eklemek için arama yapabilirsiniz.");
    return;
  }

  // /ac
  if (text === "/ac") {
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;
    fileData.data.active = true;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "🟢 Liverpool maç kategorisi CloudStream'de *AKTİF* edildi!");
    return;
  }

  // /kapat
  if (text === "/kapat") {
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;
    fileData.data.active = false;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "🔴 Liverpool maç kategorisi *KAPATILDI*.");
    return;
  }

  // /kanallar 35, 38 (elle ID girmek isteyenler için)
  if (text.startsWith("/kanallar")) {
    const input = text.replace(/^\/kanallar\s*/i, "").trim();
    const parts = input.split(/[, ]+/).filter(Boolean);
    if (parts.length === 0) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "Örnek: `/kanallar 35, 38, 120`");
      return;
    }
    const allChannels = await getChannelList();
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;

    const newChannels = parts.map((id, index) => {
      const found = allChannels.find(c => c.id === id);
      const name = found ? found.name : `Kanal ${id}`;
      return { id: id, name: `Yayın ${index + 1} (${name})` };
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

  // AKILLI ARAMA: Kullanıcı /ara yazsa da yazmasa da kanal arar!
  const query = text.replace(/^\/ara\s*/i, "").trim().toLowerCase();
  if (query.length < 2) {
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "Aramak için en az 2 harf yazın (örn: \`sky\`, \`tnt\`, \`bein\`).");
    return;
  }

  const channels = await getChannelList();
  const matches = channels.filter(c => c.name.toLowerCase().includes(query)).slice(0, 10);

  if (matches.length === 0) {
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, `❌ *"${query}"* için sonuç bulunamadı. Başka bir kelime deneyin (örn: \`sky\`, \`bein\`, \`premier\`).`);
    return;
  }

  // Tıklanabilir butonlar oluştur
  const keyboard = matches.map(c => [
    {
      text: `➕ ${c.name} (ID: ${c.id})`,
      callback_data: `add_${c.id}`
    }
  ]);

  await sendTgKeyboard(
    env.TELEGRAM_BOT_TOKEN,
    chatId,
    `🔍 *"${query}" için bulunan kanallar:* (Eklemek için butona tıklayın)`,
    keyboard
  );
}

// Buton Tıklaması (Kanal Ekleme)
async function handleCallbackQuery(cb, env) {
  const data = cb.data;
  const chatId = cb.message ? cb.message.chat.id : cb.from.id;

  if (data.startsWith("add_")) {
    const channelId = data.substring(4);
    const channels = await getChannelList();
    const ch = channels.find(c => c.id === channelId);
    const chName = ch ? ch.name : `Kanal ${channelId}`;

    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) {
      await answerCallback(env.TELEGRAM_BOT_TOKEN, cb.id, "GitHub bağlantı hatası!", true);
      return;
    }

    const currentList = fileData.data.channels || [];
    if (currentList.some(c => c.id === channelId)) {
      await answerCallback(env.TELEGRAM_BOT_TOKEN, cb.id, `⚠️ ${chName} zaten ekli!`, true);
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

    // Bildirim ve onay mesajı
    await answerCallback(env.TELEGRAM_BOT_TOKEN, cb.id, `✅ ${chName} eklendi!`);
    await sendTg(
      env.TELEGRAM_BOT_TOKEN,
      chatId,
      `✅ *${chName}* (ID: \`${channelId}\`) maça eklendi!\n📺 Şu an toplam *${currentList.length}* yayın kanalı aktif.`
    );
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
    console.error("channels.json fetch error:", e);
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

function toBase64(str) {
  const bytes = new TextEncoder().encode(str);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function fromBase64(b64) {
  const binary = atob(b64.replace(/\s/g, ''));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return new TextDecoder().decode(bytes);
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
  const content = fromBase64(json.content);
  return {
    sha: json.sha,
    data: JSON.parse(content)
  };
}

async function updateLiverpoolJson(token, data, sha) {
  const url = `https://api.github.com/repos/${GITHUB_REPO}/contents/${FILE_PATH}`;
  const updatedContent = toBase64(JSON.stringify(data, null, 2));
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
