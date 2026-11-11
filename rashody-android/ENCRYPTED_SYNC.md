# Защищённая синхронизация «Мои деньги»

Владелец отправляет расходы и фото чеков в ChatGPT. ChatGPT записывает проверенные строки в закрытую Google Таблицу, затем **обновляет зашифрованный снимок** в `rashody/data.enc.json` (публичный GitHub Pages). Публичный репозиторий **никогда не содержит исходные финансовые записи**.

## Первое подключение телефона

1. Установить APK версии 1.2+ и нажать «Подключить один раз».
2. Приложение генерирует P-256 ECDH ключ и хранит закрытую часть *неэкспортируемой* в IndexedDB внутри приложения.
3. Отправить **только публичный** ключ (base64url от SPKI DER) в этот чат.
4. Ассистент проверяет ключ, обновляет Google Таблицу и публикует `rashody/data.enc.json` в виде ciphertext. **Никаких токенов Google или банковских данных на GitHub.**

## Формат

Открытые данные перед шифрованием:
```json
{"format":"moi-dengi-v1","entries":[],"shopping":[]}
```
Публикуемый JSON:
```json
{"version":1,"alg":"P256-HKDF-SHA256-AES256GCM","ephemeral":"base64url(DER SPKI)","salt":"base64url(16 random bytes)","iv":"base64url(12 random bytes)","ciphertext":"base64url(AES-GCM ciphertext with tag)"}
```

Шифрование в Python (модуль cryptography): `serialization.load_der_public_key(base64url_decode(pubkey))`; создать новый ephemeral `ec.generate_private_key(ec.SECP256R1())`; общий секрет `ephemeral.exchange(ec.ECDH(), pubkey)`; `HKDF(algorithm=hashes.SHA256(), length=32, salt=random(16), info=b"moi-dengi-feed-v1")`; затем `AESGCM(key).encrypt(iv=random(12), data=json_utf8, associated_data=b"moi-dengi-v1")`. Публичный ключ ephemeral кодируется DER SPKI.

На каждом обновлении снимка создаются **новые ephemeral ключ, salt и iv**. APK декодирует пакет через Web Crypto P-256, HKDF и AES-GCM. Подмена ciphertext не проходит аутентификацию.

## Согласованность

- Перед шифрованием прочитать листы Google Sheets «Расходы» и «Список покупок» и проверить ID, даты, суммы, валюты и дубли.
- Обновить `rashody/data.enc.json` только при успешной записи в Google Таблицу. При ошибке не утверждать, что приложение обновлено.
- Покупки, добавленные в приложение без доступа к чату, остаются **локальными** до отправки пользователем в чат. Галочки облачных покупок сохраняются на телефоне.
- Записи из чата в Android **только для чтения**; корректировки делать через ChatGPT и Google Таблицу.
- Не публиковать открытые расходы, токены API, приватный ключ или plaintext бэкапы в GitHub.
- Если приложение переустановлено, прежний ключ теряется; нужно повторное подключение.
