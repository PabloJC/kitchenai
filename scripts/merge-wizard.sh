#!/usr/bin/env bash
# Fusiona el proyecto generado por el wizard de KMP (kmp.jetbrains.com)
# con el scaffold de este repo.
#
#   ./scripts/merge-wizard.sh ~/Downloads/KitchenAI.zip
#
# Del wizard tomamos lo que no se puede escribir a mano:
#   · gradle/wrapper/gradle-wrapper.jar + gradlew
#   · iosApp/iosApp.xcodeproj (el .pbxproj)
#   · la matriz de versiones del toolchain (kotlin/agp/compose), ya verificada
#
# De este repo conservamos todo lo demás: :shared, Firebase, CI, CLAUDE.md, docs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BOLD=$'\033[1m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; OFF=$'\033[0m'
step() { printf '\n%s▸ %s%s\n' "$BOLD" "$1" "$OFF"; }
ok()   { printf '  %s✓%s %s\n' "$GREEN" "$OFF" "$1"; }
warn() { printf '  %s!%s %s\n' "$YELLOW" "$OFF" "$1"; }
die()  { printf '  %s✗%s %s\n' "$RED" "$OFF" "$1"; exit 1; }

ZIP="${1:-}"
[ -n "$ZIP" ] || die "Uso: $0 <ruta-al-zip-del-wizard>"
[ -f "$ZIP" ] || die "No existe: $ZIP"

# --------------------------------------------------------------------------- #
step "0/7 · Copia de seguridad"

BACKUP=".wizard-backup-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"
for f in settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml iosApp; do
  [ -e "$f" ] && cp -R "$f" "$BACKUP/" 2>/dev/null || true
done
ok "Backup en $BACKUP/ (bórralo cuando compile)"

# --------------------------------------------------------------------------- #
step "1/7 · Descomprimiendo el wizard"

WIZ="$(mktemp -d)"
trap 'rm -rf "$WIZ"' EXIT
unzip -q "$ZIP" -d "$WIZ"

SRC="$(find "$WIZ" -maxdepth 3 -name settings.gradle.kts -exec dirname {} \; | head -1)"
[ -n "$SRC" ] || die "No encuentro settings.gradle.kts dentro del zip"
ok "Proyecto del wizard en $(basename "$SRC")"

# --------------------------------------------------------------------------- #
step "2/7 · Wrapper de Gradle"

cp -R "$SRC/gradle/wrapper" gradle/
cp "$SRC/gradlew" "$SRC/gradlew.bat" .
chmod +x gradlew

# Todo lo que sale de un ZIP descargado arrastra com.apple.quarantine. En terminal
# no molesta, pero la Run Script Phase de Xcode falla al ejecutar gradlew con
# "Operation not permitted" (EPERM, no EACCES: no es el bit de ejecución).
if command -v xattr >/dev/null; then
  xattr -dr com.apple.quarantine . 2>/dev/null || true
  ok "Cuarentena de Gatekeeper eliminada"
fi

ok "gradle-wrapper.jar + gradlew ($(grep -o 'gradle-[0-9.]*-bin' gradle/wrapper/gradle-wrapper.properties))"

# --------------------------------------------------------------------------- #
step "3/7 · Proyecto Xcode"

MY_SWIFT="$(mktemp -d)"
cp iosApp/iosApp/*.swift "$MY_SWIFT/" 2>/dev/null || true
cp iosApp/README.md "$MY_SWIFT/" 2>/dev/null || true

rm -rf iosApp
cp -R "$SRC/iosApp" .
ok "iosApp/ reemplazado (incluye iosApp.xcodeproj)"

# Nuestros .swift llevan FirebaseApp.configure() y el arranque de Koin.
# El Info.plist lo dejamos como venga del wizard: el .pbxproj lo referencia.
cp "$MY_SWIFT"/*.swift iosApp/iosApp/ 2>/dev/null || true
cp "$MY_SWIFT/README.md" iosApp/ 2>/dev/null || true
rm -rf "$MY_SWIFT"
ok "Restaurados iOSApp.swift y ContentView.swift"

# --------------------------------------------------------------------------- #
step "4/7 · gradle.properties"

# BUG corregido: antes se hacía backup pero nunca se fusionaban las propiedades
# del wizard, y con ellas se perdían flags que el toolchain nuevo necesita.
python3 - "$SRC/gradle.properties" <<'PYPROPS'
import sys, pathlib
wizard = pathlib.Path(sys.argv[1])
mine   = pathlib.Path("gradle.properties")
if not wizard.exists():
    print("  ! el wizard no trae gradle.properties"); raise SystemExit
have = {l.split('=')[0].strip() for l in mine.read_text().splitlines()
        if '=' in l and not l.strip().startswith('#')}
added = [l for l in wizard.read_text().splitlines()
         if '=' in l and not l.strip().startswith('#')
         and l.split('=')[0].strip() not in have]
if added:
    with mine.open('a') as f:
        f.write("\n# --- Importadas del wizard ---\n")
        f.write("\n".join(added) + "\n")
    for l in added:
        print(f"  \033[32m+\033[0m {l.strip()}")
else:
    print("  \033[32m✓\033[0m nada nuevo que importar")
PYPROPS

step "5/7 · Versiones del toolchain"

python3 - "$SRC/gradle/libs.versions.toml" <<'PY'
import re, sys, pathlib

wizard = pathlib.Path(sys.argv[1]).read_text()
mine_p = pathlib.Path("gradle/libs.versions.toml")
mine   = mine_p.read_text()

def versions_of(text):
    out, sec = {}, None
    for line in text.splitlines():
        l = line.split('#')[0].strip()
        m = re.match(r'\[(\w+)\]', l)
        if m:
            sec = m.group(1); continue
        if sec == 'versions' and '=' in l:
            k, v = l.split('=', 1)
            out[k.strip()] = v.strip().strip('"')
    return out

w = versions_of(wizard)

# El wizard nombra las claves distinto que nosotros.
MAP = {
    'kotlin':               ['kotlin'],
    'agp':                  ['agp', 'android-gradle-plugin', 'androidGradlePlugin'],
    'composeMultiplatform': ['compose-multiplatform', 'composeMultiplatform', 'compose'],
    'androidCompileSdk':    ['android-compileSdk', 'androidCompileSdk'],
    'androidMinSdk':        ['android-minSdk', 'androidMinSdk'],
    'androidTargetSdk':     ['android-targetSdk', 'androidTargetSdk'],
    'androidxActivity':     ['androidx-activity-compose', 'androidx-activity', 'androidxActivity'],
    'androidxLifecycle':    ['androidx-lifecycle', 'androidxLifecycle'],
}

changed = []
for ours, candidates in MAP.items():
    val = next((w[c] for c in candidates if c in w), None)
    if val is None:
        continue
    pat = re.compile(rf'^(\s*{re.escape(ours)}\s*=\s*)"[^"]*"', re.M)
    if pat.search(mine):
        old = pat.search(mine).group(0).split('"')[1]
        if old != val:
            mine = pat.sub(rf'\g<1>"{val}"', mine, count=1)
            changed.append(f"{ours}: {old} → {val}")
        else:
            changed.append(f"{ours}: {val} (sin cambios)")

# minSdk: el wizard suele poner 24; nosotros queremos 26. No lo pisamos.
mine = re.sub(r'^(\s*androidMinSdk\s*=\s*)"[^"]*"', r'\g<1>"26"', mine, count=1, flags=re.M)

mine_p.write_text(mine)
for c in changed:
    print(f"  \033[32m✓\033[0m {c}")
print("  \033[33m!\033[0m androidMinSdk forzado a 26 (el wizard pone 24)")
PY

# --------------------------------------------------------------------------- #
step "6/7 · Limpiando duplicados del wizard"

# El wizard mete su propio Platform/Greeting en composeApp; los nuestros viven
# en :shared. Dejar los dos rompe la compilación por símbolos duplicados.
REMOVED=0
while IFS= read -r f; do
  rm -f "$f"; REMOVED=$((REMOVED+1))
done < <(find "$SRC/composeApp/src" \( -name 'Greeting.kt' -o -name 'Platform*.kt' \) 2>/dev/null | sed "s|$SRC/||")

# composeResources sí lo queremos: es donde van iconos y strings.
if [ -d "$SRC/composeApp/src/commonMain/composeResources" ]; then
  mkdir -p composeApp/src/commonMain
  cp -R "$SRC/composeApp/src/commonMain/composeResources" composeApp/src/commonMain/
  ok "composeResources/ importado"
fi

# Iconos del launcher: van a :androidApp, no a la biblioteca de UI.
if [ -d "$SRC/composeApp/src/androidMain/res" ]; then
  mkdir -p androidApp/src/main/res
  cp -Rn "$SRC/composeApp/src/androidMain/res/." androidApp/src/main/res/ 2>/dev/null || true
  ok "Recursos de Android importados en :androidApp (sin pisar themes.xml)"
fi

ok "Duplicados descartados"

# --------------------------------------------------------------------------- #
step "7/7 · Verificación"

MISSING=0
for f in gradlew gradle/wrapper/gradle-wrapper.jar settings.gradle.kts \
         shared/build.gradle.kts composeApp/build.gradle.kts androidApp/build.gradle.kts CLAUDE.md \
         .github/workflows/ai-code-review.yml firebase/firestore.rules; do
  [ -e "$f" ] || { warn "FALTA $f"; MISSING=1; }
done
[ -d iosApp/iosApp.xcodeproj ] || { warn "FALTA iosApp/iosApp.xcodeproj"; MISSING=1; }
for m in shared composeApp androidApp; do
  grep -q "include(\":$m\")" settings.gradle.kts || { warn "settings.gradle.kts no incluye :$m"; MISSING=1; }
done
[ "$MISSING" -eq 0 ] && ok "Todo en su sitio"

printf '\n%s✅ Merge completado.%s\n\n' "$GREEN" "$OFF"
cat <<'NEXT'
Ahora, en este orden:

  1. ./gradlew :shared:check
  2. ./gradlew :androidApp:assembleDebug
  3. Abrir iosApp/iosApp.xcodeproj y aplicar los 3 ajustes de iosApp/README.md
     (Run Script Phase, User Script Sandboxing = No, paquete de firebase-ios-sdk)

Si el paso 1 o 2 falla por versiones, el backup del toolchain original está en
.wizard-backup-*/libs.versions.toml
NEXT
