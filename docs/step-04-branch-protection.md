# Paso 4 — Protección de rama y auto-merge

> Objetivo: que `main` sólo acepte código que ha pasado el CI **y** la revisión de Claude,
> y que una tarea terminada se mergee sola en cuanto ambos estén en verde.

Hasta ahora los dos checks existían pero eran decorativos: podías ignorar un `request_changes`
y mergear igual. Este paso los convierte en una barrera real.

```bash
./scripts/setup-branch-protection.sh
```

---

## 4.1 Qué configura

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

- [ ] `git push` directo a `main` es rechazado
- [ ] Una PR con `Claude review` en rojo no ofrece el botón de merge
- [ ] Una PR con ambos checks en verde y `--auto` se mergea sola
- [ ] La rama se borra al mergear
- [ ] Una PR con `skip-ai-review` **no** se queda bloqueada esperando el check
- [ ] Una PR que toca `.github/workflows/` tampoco se queda bloqueada
