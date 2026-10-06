# Adding to the catalogue by hand

Two scripts under `tools/` write one ingredient or recipe at a time through the `writeCatalogue`
callable (#165). Both take everything from arguments or the environment: nothing identifying the
project or the account lives in the repository. `firebase/firestore.rules` is not involved: the
catalogue collections stay closed to every client, and only the callable writes them.

## Once: make an account an admin

The function only accepts callers whose ID token carries `admin: true`. Set it with your own
credentials, from the repository root, after `npm install` in `tools/`:

```bash
gcloud auth application-default login
node tools/grant-admin.mjs --project <projectId> --uid <uid>
```

Other claims on the account are kept. `--revoke` removes only `admin`. The change reaches the
token on its next refresh, so the account must sign in again.

## Each time: write an entry

The payload file is exactly what the callable takes (`functions/README.md` lists the fields):

```json
{
  "kind": "ingredient",
  "id": "<id>",
  "data": { "labels": { "en": "<name>" }, "defaultUnitTaxonomy": "units", "defaultUnitTerm": "gram" }
}
```

Check it first. This needs no credentials and no network, and uses the function's own validator
(it runs the TypeScript source directly, so Node 22.18+ is required):

```bash
node tools/catalogue.mjs --dry-run entry.json
```

Then write it, with an ID token for the admin account in the environment:

```bash
KITCHENAI_ID_TOKEN=<idToken> node tools/catalogue.mjs --project <projectId> entry.json
```

The payload may also come from stdin (`-` or no file). The result is printed as
`{"kind":"...","id":"...","created":true}`, and a second line says whether the id was new or an
existing document was replaced: the function does `set` without merge, like the seed script.

### Getting the ID token

- Without a token, the script can sign in as the admin itself: with Application Default
  Credentials of a service account (or impersonating one), it mints a custom token for the uid and
  trades it for an ID token. Pass `--uid <uid>` and `--api-key <webApiKey>`.
- Or hand it any valid ID token of an admin account in `KITCHENAI_ID_TOKEN`.

Environment equivalents: `KITCHENAI_PROJECT`, `KITCHENAI_REGION` (default `europe-southwest1`),
`KITCHENAI_ADMIN_UID`, `KITCHENAI_API_KEY`, `KITCHENAI_ID_TOKEN`. The token has no flag on purpose:
a command line ends up in shell history.

### Errors

| Message | Meaning |
|---|---|
| `invalid payload: ...` | The local validation failed, same message the function would send |
| `no admin claim` | Signed in, but the token has no `admin: true`: run `grant-admin`, sign in again |
| `not signed in` | The token is missing, expired or rejected |
| `function not found` | Wrong `--project` or `--region`, or the function is not deployed |
