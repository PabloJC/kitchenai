#!/usr/bin/env bash
# Completa el Paso 1 con lo que sólo se puede hacer en tu Mac:
# wrapper de Gradle, repo en GitHub, secrets y primera build.
#
#   ./scripts/finish-setup-macos.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; OFF=$'\033[0m'
step() { printf '\n%s▸ %s%s\n' "$BOLD" "$1" "$OFF"; }
ok()   { printf '  %s✓%s %s\n' "$GREEN" "$OFF" "$1"; }
warn() { printf '  %s!%s %s\n' "$YELLOW" "$OFF" "$1"; }
die()  { printf '  %s✗%s %s\n' "$RED" "$OFF" "$1"; exit 1; }

# --------------------------------------------------------------------------- #
step "1/6 · Comprobando herramientas"

command -v git >/dev/null || die "git no está instalado"
command -v gh  >/dev/null || die "GitHub CLI no está instalado — 'brew install gh'"

if ! command -v java >/dev/null; then
  die "No hay JDK — 'brew install --cask temurin@17'"
fi
JAVA_MAJOR=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
[ "$JAVA_MAJOR" -ge 17 ] || die "Se necesita JDK 17+, tienes $JAVA_MAJOR"
ok "JDK $JAVA_MAJOR"

if [ -z "${ANDROID_HOME:-}" ] && [ ! -d "$HOME/Library/Android/sdk" ]; then
  warn "No encuentro el Android SDK. Ábrelo desde Android Studio → SDK Manager (API 36)."
else
  SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
  ok "Android SDK en $SDK_DIR"
  grep -q '^sdk.dir' local.properties 2>/dev/null || echo "sdk.dir=$SDK_DIR" >> local.properties
fi

command -v xcodebuild >/dev/null && ok "Xcode $(xcodebuild -version | head -1 | awk '{print $2}')" \
  || warn "Xcode no detectado; el target de iOS no compilará"

# --------------------------------------------------------------------------- #
step "2/6 · Gradle wrapper"

if [ -f gradle/wrapper/gradle-wrapper.jar ]; then
  ok "El wrapper ya existe"
elif command -v gradle >/dev/null; then
  gradle wrapper --gradle-version 9.1.0 --distribution-type bin
  ok "Wrapper generado con la instalación local de Gradle"
else
  warn "No hay 'gradle' en el PATH. Opciones:"
  echo "     a) brew install gradle && gradle wrapper --gradle-version 9.1.0"
  echo "     b) abrir el proyecto en Android Studio (lo genera al sincronizar)"
  die "Sin wrapper no se puede continuar"
fi
chmod +x gradlew

# --------------------------------------------------------------------------- #
step "3/6 · Repositorio git"

if [ ! -d .git ]; then
  git init -b main
  ok "Repo local inicializado"
fi

if ! git remote get-url origin >/dev/null 2>&1; then
  gh auth status >/dev/null 2>&1 || gh auth login
  gh repo create kitchenai --private --source=. --remote=origin \
    --description "KitchenAI — Kotlin Multiplatform (Android/iOS) con Firebase"
  ok "Repo creado en GitHub"
else
  ok "Remote origin: $(git remote get-url origin)"
fi

# --------------------------------------------------------------------------- #
step "4/6 · Etiquetas de GitHub"

create_label() { gh label create "$1" --color "$2" --description "${3:-}" --force >/dev/null 2>&1 || true; }
create_label "type:feature"       1D76DB "Nueva funcionalidad"
create_label "type:chore"         C5DEF5 "Infra / tooling"
create_label "type:bug"           D73A4A "Corrección"
create_label "layer:domain"       0E8A16 ""
create_label "layer:data"         5319E7 ""
create_label "layer:ui"           FBCA04 ""
create_label "platform:android"   3DDC84 ""
create_label "platform:ios"       111111 ""
create_label "ai-review:approved" 0E8A16 "La IA aprobó la PR"
create_label "skip-ai-review"     EEEEEE "Saltar la revisión automática"
create_label "automerge"          6F42C1 "Elegible para auto-merge"
ok "Etiquetas creadas"

# --------------------------------------------------------------------------- #
step "5/6 · Firebase"

if [ ! -f androidApp/google-services.json ]; then
  warn "Falta androidApp/google-services.json"
  echo "     1. https://console.firebase.google.com → crea el proyecto 'kitchenai-dev'"
  echo "     2. Añade app Android con package 'com.kitchenai.app' y descarga el JSON"
  echo "     3. Muévelo a androidApp/google-services.json"
  echo "     Mientras tanto uso la plantilla para que compile:"
  cp androidApp/google-services.json.template androidApp/google-services.json
  warn "Usando plantilla — Firebase NO funcionará en runtime hasta que pongas el real"
else
  ok "google-services.json presente"
  gh secret set GOOGLE_SERVICES_JSON --body "$(base64 -i androidApp/google-services.json)" && ok "Secret GOOGLE_SERVICES_JSON"
fi

if [ -f iosApp/iosApp/GoogleService-Info.plist ]; then
  gh secret set GOOGLE_SERVICE_INFO_PLIST --body "$(base64 -i iosApp/iosApp/GoogleService-Info.plist)" && ok "Secret GOOGLE_SERVICE_INFO_PLIST"
else
  warn "Falta iosApp/iosApp/GoogleService-Info.plist (app iOS en la consola de Firebase)"
fi

# --------------------------------------------------------------------------- #
step "6/6 · Primera build"

./gradlew --version >/dev/null
./gradlew :shared:check
ok "Módulo :shared compila y sus tests pasan"

./gradlew :androidApp:assembleDebug
ok "APK debug generado"

printf '\n%s✅ Paso 1 completado en la parte automatizable.%s\n\n' "$GREEN" "$OFF"
cat <<'NEXT'
Comprueba que no se cuela ningún secreto y haz el primer commit:

  git status --short | grep -E 'google-services.json$|GoogleService-Info.plist$' \
    && echo "PARA: hay ficheros de Firebase sin ignorar" \
    || echo "OK: ningún fichero de Firebase en el diff"

  git add -A
  git commit -m "chore: scaffold KMP + Firebase + CI"
  git push -u origin main
NEXT
