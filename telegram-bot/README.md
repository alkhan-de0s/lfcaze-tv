# LFCAZE TV - Telegram Admin Bot

Bu bot, telefonunuzdan tek bir Telegram mesajıyla CloudStream uygulamasındaki **"🔴 Liverpool Maç Yayınları"** kategorisini ve kanallarını 7/24 anında güncellemenizi sağlar.

---

## 1. Telegram Bot Token Alma (1 Dakika)
1. Telegram'da `@BotFather` kullanıcısını açın.
2. `/newbot` yazın.
3. Botunuza isim verin (Örn: `LFCAZE Admin`).
4. Botunuza kullanıcı adı belirleyin (Örn: `lfcaze_tv_bot`).
5. Size verilen **HTTP API Token**'ı kopyalayın.

---

## 2. GitHub Personal Access Token (PAT) Alma (1 Dakika)
Botun `liverpool.json` dosyasını güncelleyebilmesi için:
1. GitHub'da sağ üst profil ikonunuza tıklayın > **Settings** > **Developer settings** (en altta).
2. **Personal access tokens** > **Tokens (classic)** > **Generate new token (classic)**.
3. Note: `LFCAZE Bot`
4. İzinlerden sadece **`repo`** kutucuğunu işaretleyin.
5. Sayfanın altından **Generate token** butonuna basıp çıkan token'ı kopyalayın (`ghp_...`).

---

## 3. Kurulum Seçenekleri

### Seçenek A: Cloudflare Workers ile 7/24 Bulutta Çalıştırma (Önerilen, 0 TL, 0 PC)
1. [Cloudflare Dashboard](https://dash.cloudflare.com)'a ücretsiz üye olun / giriş yapın.
2. Sol menüden **Workers & Pages** > **Create application** > **Create Worker** deyin.
3. Bir isim verip **Deploy**'a basın.
4. **Edit code** butonuna basıp [`worker.js`](worker.js) dosyasındaki tüm kodları yapıştırıp **Save and deploy** deyin.
5. Worker sayfasında **Settings** > **Variables and Secrets** sekmesine gidin ve 2 adet Secret ekleyin:
   - `TELEGRAM_BOT_TOKEN`: @BotFather'dan aldığınız token
   - `GITHUB_TOKEN`: GitHub PAT token'ınız
6. Worker URL'inizi kopyalayın (Örn: `https://lfcaze-bot.xxx.workers.dev`).
7. Tarayıcınızda şu linki açarak Telegram Webhook'unu aktif edin:
   ```text
   https://api.telegram.org/bot<TELEGRAM_TOKEN>/setWebhook?url=https://lfcaze-bot.xxx.workers.dev
   ```
Artık botunuz 7/24 bulutta hazırdır!

---

### Seçenek B: Kendi Bilgisayarınızda Python ile Çalıştırma
1. Gerekli kütüphaneleri yükleyin:
   ```bash
   pip install requests python-telegram-bot
   ```
2. [`bot.py`](bot.py) içindeki `TELEGRAM_BOT_TOKEN` ve `GITHUB_TOKEN` değişkenlerine kendi token'larınızı yazın.
3. Çalıştırın:
   ```bash
   python bot.py
   ```

---

## 4. Bot Komutları ve Kullanım

Telegram'da botunuza şu mesajları atarak CloudStream'i yönetebilirsiniz:

* `/mac Liverpool vs Manchester City` ➡️ Maç başlığını ayarlar ve kategoriyi açar.
* `/kanallar 51, 84, 120` ➡️ Yayın kanallarını belirler (51: Sky Sports, 84: TNT Sports, 120: beIN Sports).
* `/liste` ➡️ Popüler spor kanallarının ID numaralarını listeler.
* `/durum` ➡️ Şu anki aktif maçı ve yayın kanallarını gösterir.
* `/ac` ➡️ Liverpool bölümünü CloudStream'de görünür yapar.
* `/kapat` ➡️ Maç bittiğinde bölümü kapatır.
