# TableTap — restaurant ordering SaaS

Spring Boot 3 (Java 21, layered) + React (Vite) + PostgreSQL. One Docker image serves both the API and the UI.

![TableTap architecture](docs/architecture.svg)

*Full-size: [docs/architecture.svg](docs/architecture.svg)*

## Run locally

```bash
docker compose up --build
```

Open http://localhost:8080 (use another port with `APP_PORT=8081 docker compose up --build`). Demo accounts (seeded when `DEMO_DATA=true`; passwords come from `.env` / compose defaults):

| Role   | Email                 | Default password |
|--------|-----------------------|------------------|
| Admin  | admin@tabletap.local  | `ChangeMe123!`   |
| Owner  | owner@demo.test       | `Password123!`   |
| Waiter | waiter@demo.test      | `Password123!`   |
| Waiter (Manager title) | manager@demo.test | `Password123!` |
| Chef   | chef@demo.test        | `Password123!`   |

Frontend hot reload: `cd frontend && npm install && npm run dev` (proxies `/api` to :8080).

## Running in a restaurant (works without internet)

TableTap runs on a computer inside the restaurant (mini PC, old laptop, Mac mini). Phones and tablets
connect to it over the restaurant's Wi-Fi, so an internet outage doesn't stop service.

```bash
./scripts/setup-local.sh      # first time: creates .env with strong secrets, starts everything, prints the address
```

- Everything restarts automatically after a power cut or reboot (`restart: unless-stopped`).
- Database backups every 6 hours to `./backups` (kept 14 days). Copy that folder somewhere safe now and then.
- **Wi-Fi blips:** phones keep the last menu/floor plan. "Send to kitchen" saves the order on the phone and
  sends it automatically when the connection returns. Each order carries a unique id so a resend is never
  duplicated. Staff see an "Offline" banner and a "waiting to send" counter.
- **The app opens even with the server off** (saved on the device by a service worker). Browsers only allow
  this over HTTPS or on `localhost`. On a plain `http://192.168.x.x` address everything above still works
  while the page stays open, but a full page reload needs the server. Next step: local HTTPS (e.g. Caddy with a
  local certificate).
- Not yet: syncing the restaurant's data up to a cloud server (for remote owner access, central billing, off-site backup).

## Host it in the cloud (Azure or AWS)

The Docker setup above runs TableTap inside one restaurant. To offer it online as a service, host the same
Docker image on **Azure** or **AWS**. One script sets everything up, and a GitHub pipeline then deploys every push
to `main`.

> **The pipeline is switched off as shipped.** It lives in `github/workflows/deploy.yml` — a folder *without* the
> leading dot, which GitHub ignores. Nothing is built or deployed until you turn it on:
>
> ```bash
> git mv github .github && git commit -m "Enable deploy pipeline" && git push
> ```
>
> After that, GitHub runs it on every push to `main` (and you can run it by hand from the Actions tab).
> To switch it off again: `git mv .github github`, commit, push.

### What gets created

| | Azure | AWS |
|---|---|---|
| App | Azure Container Apps (0.5 vCPU, 1 GB, scales to zero when idle) | AWS App Runner (1 vCPU, 2 GB) |
| Database | Azure Database for PostgreSQL, Standard_B1ms, 32 GB | Amazon RDS PostgreSQL, db.t4g.micro, 20 GB, encrypted |
| Image registry | GitHub Container Registry (free, private) | Amazon ECR |
| Passwords | Container App secrets | SSM Parameter Store (SecureString) |
| Database access | Only from inside Azure, TLS required | Private subnet, only from the app's security group |
| GitHub → cloud login | OpenID Connect (no stored passwords) | OpenID Connect (no stored passwords) |

Cost: on a new Azure free account, the database size is free for 12 months and the app stays inside the monthly
Container Apps free grant at low use. On AWS, App Runner is billed from the first hour (roughly US$10–30/month
for this size) and RDS depends on your account's free-tier or credits. Check each provider's pricing calculator
before you start.

### Option A: automatic (recommended)

1. **Turn the pipeline on** (see the note above): `git mv github .github`, commit, push. Until step 3 sets
   `DEPLOY_TARGET` it skips every run, so this is harmless on its own.
2. **Log in to the cloud CLI:** `az login` for Azure, or `aws configure` (or `aws sso login`) for AWS, with an
   account that can create resources and IAM roles.
3. **Run the script** from the project folder:

   ```bash
   python3 deploy/cloud_deploy.py azure
   ```

   ```bash
   python3 deploy/cloud_deploy.py aws
   ```

   It asks for:
   - the admin email for TableTap;
   - a **GitHub token** (classic, scopes `repo` + `workflow`, create at
     <https://github.com/settings/tokens/new>). It's only used while the script runs; delete it afterwards;
   - **Azure only:** a second classic token with just `read:packages`, which Azure keeps so it can download
     your private image. Give it a long expiry.

   Then it creates the database and app, connects GitHub to the cloud, sets the pipeline variables, runs the
   pipeline to build the image, starts the app, waits until it answers, and prints the web address and admin
   password. Generated passwords are kept in `deploy/.cloud-state.json` (git-ignored, readable only by you).
   It's safe to run again.

4. **From now on, push to `main`**, and the pipeline builds and deploys automatically.

To remove everything it created: `python3 deploy/cloud_deploy.py azure --delete` (or `aws --delete`).

### Option B: by hand — what the pipeline needs

The pipeline (`.github/workflows/deploy.yml` once enabled) reads these **repository variables**
(GitHub → your repo → Settings → Secrets and variables → Actions → **Variables** tab). None of them are
passwords.

| Variable | Azure | AWS |
|---|---|---|
| `DEPLOY_TARGET` | `azure` | `aws` |
| Login | `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` | `AWS_ROLE_ARN`, `AWS_REGION` |
| Where to deploy | `AZURE_RESOURCE_GROUP`, `AZURE_CONTAINER_APP` | `AWS_ECR_REPOSITORY`, `AWS_APPRUNNER_SERVICE` |

On each push to `main` the pipeline:
1. builds the Docker image (tagged with the commit id and `latest`);
2. pushes it: to `ghcr.io/<owner>/tabletap` (Azure) or your ECR repository (AWS);
3. logs in to the cloud with OpenID Connect;
4. rolls out the new version: `az containerapp update --image …` or `aws apprunner start-deployment`;
5. checks `https://<app>/actuator/health` returns `UP`, and fails the run if it doesn't.

To set it up by hand you need:

**Azure:**
- a Microsoft Entra app registration with a *federated credential* for
  `repo:<owner>/<repo>:ref:refs/heads/main`, given the **Contributor** role on the resource group;
- a Container App running `ghcr.io/<owner>/tabletap:latest` on port 8080, with the environment variables below.

**AWS:**
- the IAM OIDC provider `token.actions.githubusercontent.com`;
- a role it can assume (restricted to the same `repo:…:ref:refs/heads/main`), allowed to push to ECR and to call
  `apprunner:StartDeployment`;
- an App Runner service running `<account>.dkr.ecr.<region>.amazonaws.com/tabletap:latest` on port 8080 with a
  VPC connector to the database.

The app's environment variables in the cloud are:

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://<db-host>:5432/tabletap?sslmode=require` |
| `DB_USER` / `DB_PASSWORD` | database login (store the password as a secret) |
| `JWT_SECRET` | 48+ random characters (secret) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | the platform admin account (password as a secret) |
| `DEMO_DATA` | `false`; with this set, the app refuses to start on default secrets |

Keep the app at **one running copy** for now: live updates and login lockouts are held in memory.

## Architecture

```
web/          REST controllers (thin: HTTP <-> DTO)
service/      business rules + access checks (AccessService is the single source of "who can do what")
repository/   Spring Data JPA
domain/       JPA entities
dto/          request/response records
security/     JWT issue/verify, CurrentUser
config/       security, SPA fallback, seeding, typed app properties
```

**Roles** (`domain/Role.java`): `ADMIN`, `OWNER`, `WAITER`, `CHEF`. Admin ⊇ Owner ⊇ staff via `AccessService`. Chefs run the kitchen screen, update order status and mark dishes sold out, but can't take orders or edit the menu.
To add a new account type: add the enum value, then extend `AccessService` (and `SecurityConfig` if it gets its own URL space).

| Feature | Where |
|---|---|
| Owner self-signup (SaaS) | `POST /api/auth/register-owner` |
| Multiple restaurants per owner | `RestaurantService` |
| Owner adds waiters/managers | `POST /api/restaurants/{id}/staff` |
| Menu template (categories, items, options) | `MenuService` |
| No floor plan (free) | Waiters type a table number on a keypad; tables with open orders are shown as buttons. |
| Floor-plan add-on (paid, $/month) | Unlocks the designer: any number of areas, tables of any shape (round, square, long, triangle, hexagon, curved booth, hand-drawn), walls/curved walls/bar/kitchen/doors, and ready-made layouts. Waiters then tap tables on the plan. Owner accepts the price first; cancelling removes the plan. Enforced server-side (HTTP 402). `POST /api/restaurants/{id}/floor/tier`, `…/floor/template` |
| Waiter takes order → kitchen | `POST /api/restaurants/{id}/orders`, kitchen screen polls `GET …/orders?open=true` |
| Clock in / out | `/api/shifts/*` (waiters must be clocked in to send orders) |
| Org tree (owner → restaurants → staff) | `GET /api/tree` |
| Admin "log in as" owner/staff | `POST /api/admin/impersonate/{userId}` (token carries `imp` claim, audited in logs) |
| Usage billing (live) | `GET /api/billing/usage` — $/restaurant/month + floor-plan add-on $/month (both **prorated from the day added**) + $/order |
| Order history / "Today" | `GET /api/restaurants/{id}/orders/day?date=&tz=` — every order (nothing deleted), totals, items sold, per-waiter; printable day sheet |
| Live updates | `GET /api/events` (Server-Sent Events). Clock in/out, orders, menu, staff, restaurants and billing refresh on every open screen |

## Security

- Passwords: bcrypt, min 10 chars with a letter and a number; constant-time login (no email enumeration).
- Brute force: 5 failed logins per account and 20 per IP per 15 min → HTTP 429; 5 sign-ups per IP per 15 min.
- Sessions: signed JWT (HS256) re-checked against the DB on **every** request. Disabling a user, resetting a
  password or suspending an owner kills existing sessions and live streams immediately (token version).
  Suspending an owner locks out all their staff. Impersonation tokens last 1 hour and are audit-logged.
- Authorization: every endpoint goes through `AccessService` (tenant isolation: owners only see their restaurants,
  staff only their own). Waiters don't see revenue. Order status can only move forward (SENT→…→SERVED).
- Input limits on every field; errors never leak stack traces.
- Headers: strict CSP, X-Frame-Options DENY, no-referrer, Permissions-Policy, HSTS (on HTTPS).
- Production guard: with `DEMO_DATA=false` the app refuses to start with default JWT secret / admin password.
- Container runs as non-root; DB is not exposed outside Docker.

## Next steps (not built yet)
- Kitchen docket printing — listen for `ORDER_CREATED` events (print service / ESC-POS printer)
- Real billing — Stripe metered subscriptions in `BillingService`
- Flyway migrations instead of `ddl-auto: update`
- Redis for live events + rate limits if you ever run more than one app server
- httpOnly cookie sessions + refresh tokens, 2FA for owners/admins
- Option groups (e.g. "pick exactly one: Rare/Medium/Well") instead of flat multi-select options
