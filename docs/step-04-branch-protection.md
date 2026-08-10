# Paso 4 — Protección de rama y auto-merge

> Objetivo: que `main` sólo acepte código que ha pasado el CI **y** la revisión de Claude,
> y que una tarea terminada se mergee sola en cuanto ambos estén en verde.

## ⚠️ Estado en este repositorio

**La protección de rama no está activa.** `PabloJC/kitchenai` es privado en plan Free, y en
esa combinación GitHub no ofrece la función:

```
403 Upgrade to GitHub Pro or make this repository public to enable this feature.
```

Lo importante es que la API **acepta la llamada sin aplicar nada** si no controlas el código
de salida. Si `git push` a `main` no te devuelve un rechazo, comprueba antes que nada:

```bash
./scripts/setup-branch-protection.sh --show
```

Consecuencias prácticas:

| | Estado |
|---|---|
| CI y revisión de Claude | ✅ funcionan y comentan en cada PR |
| Push directo a `main` | ⚠️ permitido |
| Mergear con checks en rojo | ⚠️ permitido |
| Merge automático al terminar | ✅ vía `.github/workflows/auto-merge.yml` |

La disciplina de trabajar siempre por PR la pones tú; el remoto no la impone. Para
recuperarla hay dos caminos, y `scripts/setup-branch-protection.sh` queda listo para
cualquiera de los dos:

```bash
gh repo edit --visibility public --accept-visibility-change-consequences
./scripts/setup-branch-protection.sh
```

o bien contratar GitHub Pro y ejecutar el script tal cual. Hacerlo público tiene además una
ventaja nada menor: los minutos de Actions pasan a ser ilimitados, y en privado los runners
de macOS —que usa el job `build-ios`— consumen la cuota **x10**.

---

## 4.0 Merge automático sin protección de rama

El auto-merge nativo de GitHub depende de la protección de rama, así que aquí tampoco está
disponible. Lo cubre `.github/workflows/auto-merge.yml`:

1. Pones la etiqueta **`ready-to-merge`** en la PR.
2. El workflow comprueba que no es borrador, que no tiene conflictos y que **todos** los
   checks están en verde (`gh pr checks` falla tanto si algo va mal como si sigue pendiente).
3. Si es así, mergea con squash y borra la rama.

Se dispara al etiquetar y también cada vez que terminan `CI` o `AI Code Review`, así que da
igual el orden: si etiquetas antes de que acaben los checks, el último en terminar lo
reintenta. Si algo falla, corriges, empujas y se vuelve a intentar solo al ponerse verde.

```bash
gh pr create --fill
gh pr edit --add-label ready-to-merge     # "esta tarea está terminada"
```

La diferencia con la protección de rama es de naturaleza, no de grado: esto **automatiza** el
merge cuando todo está bien, pero no **impide** que mergees a mano ignorando los checks. Es
una comodidad, no una barrera.

---

## 4.1 Qué configuraría el script (cuando esté disponible)

| Ajuste | Valor | Por qué |
|---|---|---|
| Push directo a `main` | prohibido | Todo cambio pasa por PR y por revisión |
| Checks obligatorios | `CI passed`, `Claude review` | Los dos agregados del Paso 3 |
| `strict` (rama al día) | **false** | Ver abajo |
| Aprobaciones humanas | 0 | GitHub no deja aprobar tus propias PRs |
| Historial lineal | sí | Sólo squash: un commit por tarea |
| Force-push y borrado | prohibidos | |
| Conversaciones resueltas | obligatorio | Un comentario del revisor sin cerrar bloquea |
| `enforce_admins` | **false** | Puerta trasera consciente, ver abajo |
| Auto-merge | activado | `gh pr merge --auto` |
| Borrar rama al mergear | sí | |

### Las tres decisiones discutibles

**`strict: false`.** Con `strict: true`, cada merge en `main` deja obsoletas todas las demás
PRs abiertas: hay que actualizarlas y volver a pasar CI y revisión una por una. Eso serializa
el desarrollo, que es justo lo contrario de poder trabajar issues independientes en paralelo
—el modelo de trabajo de este proyecto—. El precio real: dos PRs que pasan por separado
pueden romper `main` juntas. Como `ci.yml` también corre en `push` a `main`, se detecta en
minutos. Si algún día el equipo crece, súbelo a `true`.

**0 aprobaciones humanas.** GitHub no permite aprobar tus propias PRs, así que exigir 1
aprobación en un proyecto de una persona bloquea absolutamente todo. La revisión la aporta
el check `Claude review`. Si más adelante creas un usuario máquina y configuras
`AI_REVIEWER_TOKEN`, ese usuario sí puede emitir un Approve formal y entonces tiene sentido
subirlo a 1.

**`enforce_admins: false`.** Como admin puedes saltarte la protección. Es deliberado: si el
revisor se cae o la API de Anthropic no responde, necesitas poder desbloquearte. La
alternativa —`true`— es más estricta pero te deja encerrado fuera de tu propio repositorio
un domingo por la noche. Si la usas, déjalo anotado en la PR.

---

## 4.2 La trampa de los checks obligatorios

Un check obligatorio que **nunca reporta** bloquea igual que uno en rojo. GitHub se queda
esperando indefinidamente. Eso convierte dos situaciones normales en callejones sin salida:

1. Una PR con la etiqueta `skip-ai-review`.
2. Una PR que modifica `.github/workflows/`, donde el action se salta por validación
   (ver `docs/step-03-ai-code-review.md`, apartado 3.4).

Por eso el salto **no** puede estar en el `if:` del job. En `ai-code-review.yml` el job se
ejecuta siempre y decide dentro:

```yaml
jobs:
  review:
    name: Claude review
    steps:
      - name: ¿Hay que revisar?
        id: gate
        run: |
          if [ "$DRAFT" = "true" ] || [ "$SKIP_LABEL" = "true" ]; then
            echo "skip=true" >> "$GITHUB_OUTPUT"
          ...
      - uses: anthropics/claude-code-action@v1
        if: steps.gate.outputs.skip != 'true'
```

Así el job siempre termina y siempre reporta: en verde y sin revisar cuando toca saltarse,
en verde o rojo con veredicto cuando toca revisar.

`ci-passed` en `ci.yml` ya lo hacía bien con `if: always()`.

---

## 4.3 El flujo de una tarea

```bash
git checkout -b feat/12-listado-recetas
# ... implementar, commitear ...
gh pr create --fill
gh pr merge --squash --auto
```

`--auto` es la pieza que cumple el requisito de "cuando yo diga que la tarea está terminada,
que se mergee sola": la PR queda armada y GitHub la mergea en cuanto `CI passed` y
`Claude review` se pongan en verde. Si el revisor pide cambios, no se mergea nada; corriges,
empujas, se vuelve a revisar y si aprueba entra sola.

Al mergear con squash y `Closes #N` en el cuerpo, la issue se cierra y —con el Paso 2 montado—
pasa a **Done** en el tablero.

---

## 4.4 Verificación

Intenta romperlo. Es la única forma de saber que está puesto:

```bash
git checkout main
echo "// prueba" >> README.md
git commit -am "chore: probar protección"
git push                      # debe ser RECHAZADO por el remoto
git reset --hard origin/main  # deshacer
```

Y comprueba la configuración efectiva:

```bash
./scripts/setup-branch-protection.sh --show
```

---

## ✅ Checklist Paso 4

Con la configuración actual (privado, plan Free):

- [ ] Una PR etiquetada `ready-to-merge` con todo en verde se mergea sola
- [ ] La rama se borra al mergear
- [ ] Una PR etiquetada con checks en rojo **no** se mergea
- [ ] Al corregir y ponerse verde, el merge se reintenta sin volver a etiquetar
- [ ] Una PR con `skip-ai-review` **no** se queda esperando un check que nunca llega
- [ ] Una PR que toca `.github/workflows/` tampoco

Si algún día el repositorio pasa a público o a Pro:

- [ ] `./scripts/setup-branch-protection.sh` termina sin error
- [ ] `git push` directo a `main` es rechazado
- [ ] Una PR con `Claude review` en rojo no ofrece el botón de merge
