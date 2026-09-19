/**
 * LFCAZE TV - Liverpool Telegram Admin Bot
 * Cloudflare Worker (100% Free, 24/7 Serverless)
 *
 * Environment Secrets required in Cloudflare Worker settings:
 * - TELEGRAM_BOT_TOKEN: From @BotFather
 * - GITHUB_TOKEN: GitHub Personal Access Token (classic with 'repo' scope, or fine-grained)
 * - ADMIN_TELEGRAM_ID: (Optional) Your Telegram numeric ID to prevent others from using the bot
 */

const GITHUB_REPO = "alkhan-de0s/lfcaze-tv";
const FILE_PATH = "liverpool.json";

// Quick channel presets for convenience
const CHANNEL_PRESETS = {
  "51": "Sky Sports Premier League",
  "49": "Sky Sports Main Event",
  "50": "Sky Sports Football",
  "84": "TNT Sports 1",
  "85": "TNT Sports 2",
  "86": "TNT Sports 3",
  "120": "beIN Sports 1 English",
  "121": "beIN Sports 2 English",
  "122": "beIN Sports 3 English",
  "60": "NBC Sports / USA Network",
  "140": "Canal+ Sport",
  "200": "DAZN 1"
};

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return new Response("LFCAZE TV Bot is running!", { status: 200 });
    }

    try {
      const update = await request.json();
      if (update.message) {
        await handleMessage(update.message, env);
      }
    } catch (e) {
      console.error(e);
    }

    return new Response("OK", { status: 200 });
  }
};

async function handleMessage(msg, env) {
  const chatId = msg.chat.id;
  const text = (msg.text || "").trim();

  // Optional: check admin ID
  if (env.ADMIN_TELEGRAM_ID && String(chatId) !== String(env.ADMIN_TELEGRAM_ID)) {
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "⛔ Yetkisiz erişim!");
    return;
  }

  if (text === "/start" || text === "/yardim" || text === "/help") {
    const helpMsg = `🔴 *LFCAZE TV - Liverpool Maç Yönetim Botu* 🔴\n\n` +
      `*Komutlar:*\n` +
      `📌 \`/mac <Maç Adı>\`\nÖrn: \`/mac Liverpool vs Manchester City\`\n\n` +
      `📺 \`/kanallar <Kanal ID'leri>\`\nÖrn: \`/kanallar 51, 84, 120\`\n\n` +
      `🟢 \`/ac\` - Liverpool maç kategorisini aktif eder\n` +
      `🔴 \`/kapat\` - Maç kategorisini kapatır\n` +
      `📊 \`/durum\` - Şu anki aktif maçı ve kanalları gösterir\n` +
      `📋 \`/liste\` - Popüler spor kanalları ve ID listesi`;
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, helpMsg);
    return;
  }

  if (text === "/liste") {
    let listMsg = `📋 *Popüler Spor Kanalı ID'leri:*\n\n`;
    for (const [id, name] of Object.entries(CHANNEL_PRESETS)) {
      listMsg += `• *${id}*: ${name}\n`;
    }
    listMsg += `\n_İpucu:_ \`/kanallar 51, 84, 120\` şeklinde hızlıca ayarlayabilirsiniz.`;
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, listMsg);
    return;
  }

  if (text === "/durum") {
    const current = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!current) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "❌ liverpool.json okunamadı.");
      return;
    }
    const statusEmoji = current.data.active ? "🟢 Aktif" : "🔴 Kapalı";
    let statusMsg = `📊 *Mevcut Durum:* ${statusEmoji}\n` +
      `⚽ *Maç:* ${current.data.match || "Belirtilmemiş"}\n` +
      `📺 *Kanallar:*\n`;
    for (const ch of current.data.channels || []) {
      statusMsg += `  • ${ch.name} (ID: ${ch.id})\n`;
    }
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, statusMsg);
    return;
  }

  if (text.startsWith("/mac ")) {
    const matchName = text.substring(5).trim();
    if (!matchName) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "Lütfen bir maç adı yazın. Örn: `/mac Liverpool vs Arsenal`");
      return;
    }
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;

    fileData.data.match = matchName;
    fileData.data.active = true;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, `✅ Maç güncellendi ve aktif edildi:\n⚽ *${matchName}*`);
    return;
  }

  if (text.startsWith("/kanallar ")) {
    const input = text.substring(10).trim();
    const parts = input.split(/[, ]+/).filter(Boolean);
    if (parts.length === 0) {
      await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "Lütfen kanal ID'lerini yazın. Örn: `/kanallar 51, 84, 120`");
      return;
    }

    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;

    const newChannels = parts.map((id, index) => {
      const knownName = CHANNEL_PRESETS[id] || `Kanal ${id}`;
      return {
        id: id,
        name: `Yayın ${index + 1} (${knownName})`
      };
    });

    fileData.data.channels = newChannels;
    fileData.data.active = true;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);

    let resMsg = `✅ Kanallar güncellendi:\n`;
    for (const ch of newChannels) {
      resMsg += `• ${ch.name} (ID: ${ch.id})\n`;
    }
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, resMsg);
    return;
  }

  if (text === "/ac") {
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;
    fileData.data.active = true;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "🟢 Liverpool maç kategorisi CloudStream'de *AKTİF* edildi!");
    return;
  }

  if (text === "/kapat") {
    const fileData = await getLiverpoolJson(env.GITHUB_TOKEN);
    if (!fileData) return;
    fileData.data.active = false;
    await updateLiverpoolJson(env.GITHUB_TOKEN, fileData.data, fileData.sha);
    await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "🔴 Liverpool maç kategorisi *KAPATILDI*.");
    return;
  }

  await sendTg(env.TELEGRAM_BOT_TOKEN, chatId, "Bilinmeyen komut. Komutları görmek için /yardim yazın.");
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
      message: `admin: update ${data.match || "liverpool match"} via Telegram bot`,
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
