# SüperApp (Card + CEO asistanı)

Tek APK, alt menüde: Panel · Asistan · **Card** (CardMaster, olduğu gibi) · Müşteri · Daha (Takvim & Görevler, Notlar, Ayarlar).

## APK nasıl alınır
1. GitHub'da yeni repo aç, bu klasörün TÜM içeriğini (gizli `.github` klasörü dahil) yükle.
2. Actions → "Build APK" otomatik çalışır (~5-8 dk).
3. Releases bölümünden `SuperApp.apk` indir, telefona kur (CardMaster'ın üstüne de kurulur, kartvizitler korunur).
4. Ayarlar sekmesinden Gemini API anahtarını gir (Card ile ortak).

## Gerçekten çalışanlar (ilk sürüm)
Card (OCR, rehber, yedek), Gemini asistan (yazılı/sesli, sesli yanıt, not/görev/etkinlik/alarm/müşteri komutları; e-posta, WhatsApp ve iptal için onay), çevrimdışı basit komutlar, Panel + günlük AI özeti + danışman, Not, Görev (tekrarlı), Takvim (çakışma uyarısı, hatırlatma bildirimi, Google Takvim'e ekle), Müşteri/CRM (görüşme geçmişi, teklif, AI önerisi), PIN kilidi, şifreli API anahtarı.

## Henüz YOK (sonraki aşama)
Toplantı kaydı/özeti, E-posta ekranı, Rehber/WhatsApp ekranı, dosya (PDF/Word/Excel) okuma-oluşturma, tüm veride arama, biyometrik kilit, Gmail API ve Google Calendar API (OAuth), otomatik sabah özeti bildirimi. Kod derleme bu ortamda test edilemedi; ilk Actions çalışmasında hata çıkarsa log'u gönder.
