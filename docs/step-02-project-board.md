# Paso 2 — Tablero de GitHub Projects

> Objetivo: que el estado real del desarrollo se lea en el tablero sin que nadie
> arrastre tarjetas a mano.

```
issue creada       ──► Todo
PR abierta         ──► In progress      (para las issues con `Closes #N`)
Claude aprueba     ──► In review
PR mergeada        ──► Done
```

---

## 2.1 Puesta en marcha

El scope `project` no viene con la autenticación por defecto de `gh`:

```bash
gh auth refresh -s project,read:project
./scripts/setup-project.sh
```

El script crea el tablero, añade los campos **Capa** y **Paralelizable**, y guarda
`PROJECT_NUMBER` y `PROJECT_OWNER` como variables del repositorio.

Falta un paso manual, porque la API de Projects no permite editar las opciones de un campo
`Status` ya creado: abre el tablero y deja las columnas exactamente así, respetando
mayúsculas —el workflow compara los nombres literalmente—:

```
Todo · In progress · In review · Done
```

Y el token, que es la parte que no se puede automatizar:

```bash
gh secret set PROJECT_TOKEN
```

Un *fine-grained PAT* con acceso sólo a este repositorio y permisos `Projects: Read and
write`, `Issues: Read-only`, `Metadata: Read-only`.

**Por qué hace falta un PAT.** El `GITHUB_TOKEN` de Actions está limitado al repositorio, y
un Project vive fuera de él —cuelga del usuario o de la organización—. No hay permiso que
puedas pedir en el `permissions:` del workflow que le dé acceso. Es una limitación de
GitHub, no una decisión de diseño.

Si el secret no está, el workflow **no falla**: se salta el paso y lo dice en el log. Un
tablero desincronizado es un incordio; un CI en rojo en cada evento del repositorio es
mucho peor.

---

## 2.2 Cómo decide el workflow

`.github/workflows/project-sync.yml` sólo mueve issues que la PR declara cerrar, y sólo con
las palabras que GitHub reconoce como cierre:

```
Closes #12    Fixes #7    Resolves #3     ✅ mueven la tarjeta
ver también #99                            ❌ es una mención, no un cierre
```

Una PR cerrada **sin** mergear no mueve nada: la issue sigue viva y su tarjeta debe quedarse
donde estaba en lugar de retroceder.

El paso a `In review` se dispara con la etiqueta `ai-review:approved`, que pone el workflow
del Paso 3. Así el tablero refleja el veredicto del revisor sin duplicar su lógica.

---

## 2.3 La issue como especificación

`.github/ISSUE_TEMPLATE/tarea.yml` obliga a rellenar cinco campos: contexto, plan de
desarrollo paso a paso, ficheros afectados, criterios de aceptación y dependencias.

Dos de ellos hacen trabajo real más allá de documentar:

**Ficheros afectados** es lo que permite paralelizar. Dos issues que declaran el mismo
fichero no pueden desarrollarse a la vez; con la lista delante, el conflicto se ve antes de
escribir la primera línea en lugar de en el merge.

**Criterios de aceptación** los usa el revisor literalmente: si la PR dice `Closes #N`,
Claude lee la issue y comprueba uno por uno. Un criterio vago aquí es una revisión inútil
después.

---

## 2.4 El ciclo completo

```bash
gh issue create                        # plantilla -> tarjeta en Todo
git checkout -b feat/12-listado-recetas
gh pr create --fill                    # cuerpo con "Closes #12" -> In progress
                                       # Claude aprueba          -> In review
gh pr edit --add-label ready-to-merge   # -> merge solo -> issue cerrada -> Done
```

---

## ✅ Checklist Paso 2

- [ ] `./scripts/setup-project.sh --show` muestra las cuatro columnas con el nombre exacto
- [ ] `gh secret list` incluye `PROJECT_TOKEN`
- [ ] `gh variable list` incluye `PROJECT_NUMBER` y `PROJECT_OWNER`
- [ ] Una issue nueva aparece sola en **Todo**
- [ ] Al abrir su PR con `Closes #N` pasa a **In progress**
- [ ] Al aprobar el revisor pasa a **In review**
- [ ] Al mergear pasa a **Done** y la issue queda cerrada
