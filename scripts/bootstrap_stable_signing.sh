#!/usr/bin/env bash
# Run on a trusted computer/Termux. No private key is committed or published.
set -euo pipefail
umask 077
repo='kanunal99-jpg/LANU-Yalan-n-Seveyim'
for command in gh keytool openssl; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Eksik araç: $command. Termux: pkg install gh openjdk-17 openssl" >&2; exit 1
  fi
done
gh auth status >/dev/null
lanu_signing_dir="${XDG_DATA_HOME:-$HOME/.local/share}/lanu-signing/yalanini-seveyim"
mkdir -p "$lanu_signing_dir"
chmod 700 "$lanu_signing_dir"
lanu_key_file="$lanu_signing_dir/release.jks"
lanu_password_file="$lanu_signing_dir/store-password.txt"
if [[ -e "$lanu_key_file" && ! -s "$lanu_password_file" ]]; then
  echo 'Mevcut anahtarın parola dosyası eksik. Anahtar değiştirilmedi; yedekten kurtar.' >&2; exit 1
fi
if [[ ! -e "$lanu_key_file" ]]; then
  if [[ -e "$lanu_password_file" ]]; then echo 'Anahtar eksik fakat parola var. Önce anahtarı kurtar; otomatik değiştirilmedi.' >&2; exit 1; fi
  openssl rand -hex 32 > "$lanu_password_file"
  export LANU_BOOTSTRAP_PASSWORD
  LANU_BOOTSTRAP_PASSWORD="$(cat "$lanu_password_file")"
  keytool -genkeypair -noprompt -keystore "$lanu_key_file" -storetype JKS -alias lanu -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=LANU Android' -storepass:env LANU_BOOTSTRAP_PASSWORD -keypass:env LANU_BOOTSTRAP_PASSWORD
fi
export LANU_BOOTSTRAP_PASSWORD
LANU_BOOTSTRAP_PASSWORD="$(cat "$lanu_password_file")"
lanu_fingerprint="$(keytool -exportcert -keystore "$lanu_key_file" -alias lanu -storepass:env LANU_BOOTSTRAP_PASSWORD | openssl dgst -sha256 | awk '{print $NF}')"
[[ "$lanu_fingerprint" =~ ^[0-9a-fA-F]{64}$ ]]
openssl base64 -A -in "$lanu_key_file" | gh secret set LANU_KEYSTORE_BASE64 --repo "$repo"
printf '%s' "$LANU_BOOTSTRAP_PASSWORD" | gh secret set LANU_STORE_PASSWORD --repo "$repo"
printf '%s' 'lanu' | gh secret set LANU_KEY_ALIAS --repo "$repo"
printf '%s' "$LANU_BOOTSTRAP_PASSWORD" | gh secret set LANU_KEY_PASSWORD --repo "$repo"
printf '%s' "$lanu_fingerprint" | gh secret set LANU_CERT_SHA256 --repo "$repo"
unset LANU_BOOTSTRAP_PASSWORD
printf 'Kalıcı imza secret’ları kaydedildi. Sertifika SHA-256: %s\n' "$lanu_fingerprint"
printf 'Özel anahtarını ve parola dosyasını ayrı bir güvenli yere yedekle: %s\n' "$lanu_signing_dir"
echo 'İlk kalıcı imzalı sürüm eski test anahtarıyla uyuşmayabilir. Kurulu uygulamadan önce parolalı ilerleme yedeği oluştur.'
