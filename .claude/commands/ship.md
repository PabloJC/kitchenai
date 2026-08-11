---
description: Push the current branch, open its pull request and drive it to merged
argument-hint: "[número de PR ya abierta]"
allowed-tools: Bash, Read, Grep
---

Lleva el trabajo de la rama actual hasta `main`. Si te pasan un número de PR (`$1`), retoma
esa en vez de crear una nueva.

No arregles el código si algo falla. Si un check se pone rojo, para y cuéntalo: decidir qué
hacer con un fallo es del humano, y un `/ship` que además parchea es un `/ship` en el que ya
no se puede confiar.

## 1. Comprobaciones previas

```bash
git branch --show-current
git status --porcelain
git fetch -q origin main
```

Para si estás en `main`, si hay cambios sin commitear, o si la rama no tiene commits propios
por encima de `origin/main`. Nada que enviar no es un error que haya que resolver a la brava.

El `fetch` no es higiene: todo lo que viene después compara contra `origin/main`, y con una
`main` local desactualizada esa comparación miente en las dos direcciones —lista ficheros que
ya están en `main` y se pierde los que llegaron después—. Si esa mentira cae en el paso 3, la
PR se crea sin `skip-ai-review` y el check obligatorio se queda rojo para siempre.

## 2. La issue que cierra

Busca `Closes #N` en los commits de la rama:

```bash
git log origin/main..HEAD --format=%B | grep -oiE '(clos(e|es|ed)|fix(es|ed)?|resolv(e|es|ed)) +#[0-9]+'
```

Si no aparece, dedúcelo del nombre de la rama (`chore/11-slug` → `#11`) y **confírmalo con el
humano antes de seguir**. Una PR sin `Closes #N` se mergea igual y deja la issue abierta y su
tarjeta encallada: el tablero entero cuelga de esa línea.

## 3. La trampa de los workflows

```bash
git diff --name-only origin/main..HEAD | grep '^\.github/workflows/' && echo "TOCA WORKFLOWS"
```

Si toca, la PR **tiene que crearse con `--label skip-ai-review`**. La action de Claude se
niega a ejecutarse sobre un workflow que difiere del de `main`, se salta en silencio, no
publica veredicto, y el check `Claude review` —que es obligatorio— se queda rojo para
siempre. La etiqueta debe estar cuando el workflow arranca: `ai-code-review.yml` dispara en
`opened` y `synchronize`, no en `labeled`, así que ponerla después no sirve de nada. Si se te
olvidó, la salida es `git commit --allow-empty -m "chore: re-run"`.

## 4. Subir y abrir

El cuerpo explica el *porqué* y las desviaciones respecto al plan de la issue; el diff ya
dice el *qué*. Si la issue tiene criterios de aceptación, van como checklist reflejando la
realidad — los que no se pueden cumplir desde un diff (consola, dispositivo físico) van
marcados como pendientes con su motivo, no callados.

```bash
git push -u origin HEAD
gh pr create --fill-first --body-file <(...)      # + --label skip-ai-review si aplica
```

## 5. Esperar a los checks

```bash
gh pr checks <n> --watch --interval 30
```

Los obligatorios son `CI passed` y `Claude review`. Si alguno falla:

```bash
gh run view <run-id> --log-failed
```

Resume qué falló y **para ahí**. Un caso concreto que no es lo que parece: si `Claude review`
falla con la transcripción vacía, busca `workflow validation` en el log del step antes de
tocar nada — es el salto silencioso del punto 3, no un hallazgo de la revisión.

## 6. Mergear

No mergees a mano. Pon la etiqueta y deja que `auto-merge.yml` haga el squash y borre la
rama; sólo lo hace con todos los checks en verde, así que no hay forma de colar una PR que la
revisión haya rechazado.

Poner aquí `ready-to-merge` no contradice a `/issue`, que la reserva explícitamente para el
humano: la decisión de mergear se toma al invocar `/ship`, y esto es su ejecución. `/issue`
termina en la PR abierta justamente para que esa decisión siga siendo un acto aparte.

```bash
gh pr edit <n> --add-label ready-to-merge
```

Espera y confirma que se mergeó de verdad:

```bash
gh pr view <n> --json state,mergedAt
```

Si sigue abierta pasados un par de minutos, mira los logs de Auto-merge: lo normal es que
quede un check pendiente o que la rama tenga conflictos con `main`.

## 7. Comprobar que la tarjeta se movió

Cerrar la PR sin que la tarjeta llegue a **Done** es dejar el trabajo a medias.

```bash
gh run list --workflow="Project board" --limit 3
```

Si el job falló o no llegó a existir, mira `docs/infra.md` antes de improvisar: las dos
causas conocidas están escritas ahí, y ninguna de las dos se parece a lo que dice el error.
`unknown owner type` es un problema del `PROJECT_TOKEN`, no del tablero. Y una tarjeta que no
se mueve tras un merge automático suele ser el cortafuegos del `GITHUB_TOKEN`: los eventos
que genera no despiertan workflows.

## 8. Informar

La URL de la PR, si se mergeó, en qué columna quedó la tarjeta, y lo que la issue pedía y
sólo puede terminar un humano con acceso a alguna consola.

---

**Orden entre PRs.** Si hay varias abiertas y una arregla la infraestructura de la que
dependen las otras —un workflow, el tablero, CI—, esa va primero: `workflow_run` siempre
ejecuta la copia que está en `main`, así que un arreglo de workflow no surte efecto en la PR
que lo introduce. Dilo en vez de enviar la que tengas más a mano.
