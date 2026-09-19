# CloudStream DaddyLive (DLHD) Eklentisi

Bu depo, [CloudStream 3](https://github.com/recloudstream/cloudstream) uygulaması için **DaddyLive (`dlive.sx`)** canlı TV ve 24/7 yayın eklentisini içerir.

## Özellikler

- **1000+ Canlı TV Kanalı:** `https://dlive.sx/24-7-channels.php` üzerindeki tüm 24/7 kanallar (Spor, Sinema, Haber, Ulusal, Belgesel vb.).
- **Canlı Spor ve Etkinlikler:** Ana sayfada yer alan günün canlı maçları ve etkinlik yayınları.
- **Otomatik Şifre Çözme:** DaddyLive Clappr oynatıcısındaki `window._econfig` şifrelemesi doğrudan eklenti içinde çözülerek orijinal canlı HLS (`.m3u8`) akışı oynatıcıya teslim edilir.
- **Yedek Oynatıcı Desteği:** Birden fazla yayın rotası (`/stream/`, `/cast/`, `/watch/`, `/player/`) denenerek erişim engelleri aşılır.

---

## Kurulum ve CloudStream'e Ekleme

Bu eklentiyi CloudStream'e "Raw GitHub Eklentisi" veya "Repository" olarak eklemek için aşağıdaki adımları uygulayın:

### 1. Projeyi GitHub'a Yükleyin (Push)
Projenizi kendi GitHub hesabınızda yeni bir depoya (Public / Herkese Açık) yükleyin:

```bash
git init
git add .
git commit -m "Initial commit for DaddyLive Cloudstream plugin"
git branch -M main
git remote add origin https://github.com/KULLANICI_ADINIZ/DEPO_ADINIZ.git
git push -u origin main
```

### 2. GitHub Actions Otomatik Derlemesi
- Koda push yapıldığında `.github/workflows/build.yml` iş akışı otomatik olarak devreye girer.
- Eklentiyi (`.cs3`) ve `plugins.json` dizinini derleyip deponuzun **`builds`** branch'ine basar.
- GitHub repo ayarlarından **Actions > General > Workflow permissions** seçeneğinin **"Read and write permissions"** olduğundan emin olun.

### 3. CloudStream'e Depoyu Ekleyin
CloudStream uygulamasında:
1. **Ayarlar (Settings) > Uzantılar (Extensions / Plugins)** bölümüne gidin.
2. **Depo Ekle (Add Repository)** butonuna tıklayın.
3. Depo URL'si olarak aşağıdakilerden birini girin:
   - `https://raw.githubusercontent.com/KULLANICI_ADINIZ/DEPO_ADINIZ/builds`
   veya doğrudan GitHub depo adresiniz:
   - `https://github.com/KULLANICI_ADINIZ/DEPO_ADINIZ`
4. **İndir / Yükle (Download / Install)** butonuna basarak **DaddyLive** eklentisini kurun.

---

## Yerel Geliştirme ve Test (Local Build)

Bilgisayarınızda test etmek veya doğrudan ADB ile cihazınıza yüklemek için:

```bash
# Eklentiyi derlemek için
./gradlew DaddyLive:make

# USB hata ayıklama ile bağlı Android cihazınıza doğrudan yüklemek için
./gradlew DaddyLive:deployWithAdb
```
