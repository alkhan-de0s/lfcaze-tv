"""
LFCAZE TV - Liverpool Telegram Admin Bot (Python Local / Server version)
Gereksinimler: pip install requests python-telegram-bot
"""

import base64
import json
import os
import requests
from telegram import Update
from telegram.ext import ApplicationBuilder, CommandHandler, ContextTypes

TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "YOUR_TELEGRAM_BOT_TOKEN")
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN", "YOUR_GITHUB_PAT_TOKEN")
GITHUB_REPO = "alkhan-de0s/lfcaze-tv"
FILE_PATH = "liverpool.json"

CHANNEL_PRESETS = {
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
}

def get_liverpool_json():
    url = f"https://api.github.com/repos/{GITHUB_REPO}/contents/{FILE_PATH}"
    headers = {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github+json"
    }
    r = requests.get(url, headers=headers)
    if r.status_code == 200:
        data = r.json()
        raw_bytes = base64.b64decode(data["content"])
        return data["sha"], json.loads(raw_bytes.decode("utf-8"))
    return None, None

def update_liverpool_json(content_dict, sha):
    url = f"https://api.github.com/repos/{GITHUB_REPO}/contents/{FILE_PATH}"
    headers = {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github+json"
    }
    encoded = base64.b64encode(json.dumps(content_dict, indent=2, ensure_ascii=False).encode("utf-8")).decode("utf-8")
    payload = {
        "message": f"admin: update {content_dict.get('match', 'match')} via bot",
        "content": encoded,
        "sha": sha
    }
    r = requests.put(url, headers=headers, json=payload)
    return r.status_code in (200, 201)

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    msg = (
        "🔴 *LFCAZE TV - Liverpool Maç Yönetim Botu* 🔴\n\n"
        "*Komutlar:*\n"
        "📌 `/mac <Maç Adı>` - Maç adı ayarlar (örn: `/mac Liverpool vs Man City`)\n"
        "📺 `/kanallar <ID'ler>` - Yayın kanallarını seçer (örn: `/kanallar 51, 84, 120`)\n"
        "🟢 `/ac` - Kategoriyi CloudStream'de aktif eder\n"
        "🔴 `/kapat` - Kategoriyi kapatır\n"
        "📊 `/durum` - Mevcut maçı ve kanalları gösterir\n"
        "📋 `/liste` - Popüler kanal ID'lerini listeler"
    )
    await update.message.reply_markdown(msg)

async def liste(update: Update, context: ContextTypes.DEFAULT_TYPE):
    lines = ["📋 *Popüler Spor Kanalı ID'leri:*\n"]
    for cid, name in CHANNEL_PRESETS.items():
        lines.append(f"• *{cid}*: {name}")
    lines.append("\n_Kullanım:_ `/kanallar 51, 84, 120`")
    await update.message.reply_markdown("\n".join(lines))

async def durum(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sha, data = get_liverpool_json()
    if not data:
        await update.message.reply_text("❌ liverpool.json okunamadı.")
        return
    status = "🟢 Aktif" if data.get("active") else "🔴 Kapalı"
    lines = [
        f"📊 *Durum:* {status}",
        f"⚽ *Maç:* {data.get('match', 'Yok')}",
        "📺 *Kanallar:*"
    ]
    for ch in data.get("channels", []):
        lines.append(f"  • {ch.get('name')} (ID: {ch.get('id')})")
    await update.message.reply_markdown("\n".join(lines))

async def mac(update: Update, context: ContextTypes.DEFAULT_TYPE):
    match_name = " ".join(context.args).strip()
    if not match_name:
        await update.message.reply_text("Lütfen maç adı girin: `/mac Liverpool vs Real Madrid`", parse_mode="Markdown")
        return
    sha, data = get_liverpool_json()
    if not data:
        return
    data["match"] = match_name
    data["active"] = True
    if update_liverpool_json(data, sha):
        await update.message.reply_markdown(f"✅ Maç ayarlandı ve aktif edildi:\n⚽ *{match_name}*")
    else:
        await update.message.reply_text("❌ Güncelleme başarısız.")

async def kanallar(update: Update, context: ContextTypes.DEFAULT_TYPE):
    raw = " ".join(context.args).replace(",", " ").split()
    if not raw:
        await update.message.reply_text("Lütfen kanal ID'lerini yazın: `/kanallar 51 84 120`", parse_mode="Markdown")
        return
    sha, data = get_liverpool_json()
    if not data:
        return
    channels = []
    for idx, cid in enumerate(raw):
        kname = CHANNEL_PRESETS.get(cid, f"Kanal {cid}")
        channels.append({
            "id": cid,
            "name": f"Yayın {idx + 1} ({kname})"
        })
    data["channels"] = channels
    data["active"] = True
    if update_liverpool_json(data, sha):
        res = ["✅ Kanallar güncellendi:"]
        for ch in channels:
            res.append(f"• {ch['name']} (ID: {ch['id']})")
        await update.message.reply_text("\n".join(res))
    else:
        await update.message.reply_text("❌ Güncelleme başarısız.")

async def ac(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sha, data = get_liverpool_json()
    if not data: return
    data["active"] = True
    update_liverpool_json(data, sha)
    await update.message.reply_markdown("🟢 Liverpool maç kategorisi CloudStream'de *AKTİF* edildi!")

async def kapat(update: Update, context: ContextTypes.DEFAULT_TYPE):
    sha, data = get_liverpool_json()
    if not data: return
    data["active"] = False
    update_liverpool_json(data, sha)
    await update.message.reply_markdown("🔴 Liverpool maç kategorisi *KAPATILDI*.")

def main():
    app = ApplicationBuilder().token(TELEGRAM_BOT_TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CommandHandler("yardim", start))
    app.add_handler(CommandHandler("liste", liste))
    app.add_handler(CommandHandler("durum", durum))
    app.add_handler(CommandHandler("mac", mac))
    app.add_handler(CommandHandler("kanallar", kanallar))
    app.add_handler(CommandHandler("ac", ac))
    app.add_handler(CommandHandler("kapat", kapat))
    print("Bot çalışıyor...")
    app.run_polling()

if __name__ == "__main__":
    main()
