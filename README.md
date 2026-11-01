# CEO ASİSTANIM (MVP v0.1.0)

## APK'yı GitHub'dan indirme
1. Bu klasörün içeriğini yeni bir GitHub deposuna yükleyin (`.github` klasörü dahil, `main` dalına).
2. Depoda **Actions** sekmesi → **Build APK** → çalışmanın bitmesini bekleyin (~5-8 dk).
3. Çalışmaya tıklayın → en altta **Artifacts** → `CEO-ASISTANIM-debug-apk` indirin → zip içindeki `app-debug.apk` dosyasını telefona kurun (bilinmeyen kaynaklara izin verin).
4. Derleme otomatik başlamazsa: Actions → Build APK → **Run workflow**.

## İmzalı release APK (isteğe bağlı)
Secrets (Settings → Secrets and variables → Actions): `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Keystore üretimi: `keytool -genkeypair -v -keystore release.jks -alias ceo -keyalg RSA -keysize 2048 -validity 10000`
Base64: `base64 -w0 release.jks`. `v1.0.0` gibi bir etiket (tag) atarsanız APK'lar GitHub Release'e de eklenir.

## Gerçekten çalışan (MVP)
Türkçe sesli komut + sesli yanıt, çevrimdışı komutlar (hatırlat / not al / bugünkü işler), görevler ve tekrarlı hatırlatma bildirimleri,
notlar (arama, kategori), CRM (müşteri, görüşme kaydı, durum, sonraki görüşme), toplantılar (gündem, dikte metni, çakışma uyarısı),
CEO paneli, yapay zekâ (OpenAI uyumlu / Anthropic) ile özet, rapor, e-posta ve mesaj taslağı, PIN + biyometrik kilit,
şifreli anahtar saklama, JSON dışa aktarma, tüm verileri silme, koyu/açık tema.

## Henüz YAPILMADI (açıkça)
- Gmail API ve Google Calendar API (OAuth istemci kimliği gerekir). Şimdilik: e-posta taslağı e-posta uygulamasında açılır, takvime ekleme Google Takvim ekranı ile yapılır.
- Rehber (Contacts) okuma, WhatsApp sesli mesaj kaydı/gönderimi (şimdilik wa.me ile hazır metin açılır).
- (Kartvizit tarama eklendi: ML Kit ile cihazda OCR.)
- PDF/Word/Excel okuma ve oluşturma; ses dosyası kaydı (yalnızca dikte → metin).
- Sesli uyandırma kelimesi, WorkManager ile sabah özeti, veritabanı şifreleme (SQLCipher).
- Proje bu ortamda derlenip cihazda test EDİLMEDİ; ilk Actions çalışmasında hata çıkarsa log'u bana iletin.
