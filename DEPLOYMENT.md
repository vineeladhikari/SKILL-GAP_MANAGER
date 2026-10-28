# Deployment

The frontend and API are deployed separately. Email/password accounts, profile details, and checked skills are stored by the Spring Boot API in Render PostgreSQL. A signed-in user can edit and save the same profile and skills repeatedly; each save updates the latest copy. The frontend sends API requests to that service; it does not store profile data or passwords in browser storage.

## Before deploying

1. Push your changes to the connected GitHub repository (`https://github.com/vineeladhikari/SKILL-GAP_MANAGER.git`).
2. Create accounts with Render and Vercel and connect the GitHub repository to each service.

## Deploy the API to Render

1. In Render, create a **Blueprint** and select the connected repository.
2. Render reads the root `render.yaml`. The API service builds with `BACKEND/Dockerfile`; the static frontend builds with `npm install && npm run build` from `FRONTEND` and publishes `dist`.
3. Both services declare the `main` branch and automatic deploys. Build filters trigger the frontend for `FRONTEND/**` changes and the API for `BACKEND/**` changes; changes to `render.yaml` trigger both. The API's `APP_CORS_ALLOWED_ORIGINS` allows `https://vk-skilltech.onrender.com`.
4. The database and mail credentials remain external/secret Render settings; the Blueprint references them without storing secret values.
5. After a Blueprint sync, verify the existing services were updated rather than duplicated, that both remain connected to this repository's `main` branch, and that `/api/roles` returns the role list.

The API blueprint uses Render's free service plan, which may spin down while idle (taking ~40–60 seconds on the first request to wake up). The frontend allows up to 90 seconds per API request, then displays an error and re-enables sign-in so it cannot spin indefinitely. To keep the free instance warm and avoid sign-in delays, you can set up a free uptime monitor (such as UptimeRobot or cron-job.org) to ping `https://<your-render-url>/api/roles` every 10–14 minutes. The PostgreSQL blueprint uses the `basic-256mb` plan for durable database storage; review Render's current pricing before creating resources. Database contents will remain stored independently of API restarts.

## Deploy the frontend to Render

For the existing website at `https://vk-skilltech.onrender.com`, the Blueprint configures:

- **Production branch:** `main`
- **Auto-Deploy:** enabled
- **Root Directory:** `FRONTEND`
- **Build Command:** `npm install && npm run build`
- **Publish Directory:** `dist`
- **API base URL:** `https://vk-skilltech-backend.onrender.com`

The API base URL is supplied at build time as `VITE_API_BASE_URL`, so no manual environment-variable setup is needed when the Blueprint is synced.

## Verify existing Render services

The local repository cannot read the Render Dashboard's service-to-repository connections or deployment settings. Before syncing the Blueprint, check each existing service in Render:

1. In **Settings → Build & Deploy**, confirm both services point to `vineeladhikari/SKILL-GAP_MANAGER` and production branch `main`.
2. Confirm **Auto-Deploy** is enabled for both services (or select **On Commit**).
3. If Render reports that the Blueprint would create a duplicate rather than update an existing service, stop the sync and keep the existing service; configure its branch, auto-deploy, build filter, and frontend `VITE_API_BASE_URL` in the service settings instead.

Render's Dashboard controls whether a Blueprint is connected and synced. The checked-in YAML alone cannot change or prove those account-level settings.

## Deploy the frontend to Vercel

1. In Vercel, add a project and select the same GitHub repository.
2. Set **Root Directory** to `FRONTEND`.
3. Keep the Vite build settings from `FRONTEND/vercel.json`: `npm run build` with `dist` as the output directory.
4. The Vercel rewrite proxies `/api/*` to `https://vk-skilltech-backend.onrender.com` (or your assigned Render API URL), keeping session cookies same-origin in the browser. Leave `VITE_API_BASE_URL` unset on Vercel so it uses this same-origin proxy. If Render assigns a different API URL, update the rewrite destination in `FRONTEND/vercel.json` before deploying.
5. Deploy and open the assigned Vercel URL.

After the first Vercel deployment, verify `/api/roles` through the Vercel site. If using a different frontend origin to call the API directly, add that exact origin to Render's `APP_CORS_ALLOWED_ORIGINS`; preview domains are not automatically trusted.

## Configure password reset email

1. Enable 2-Step Verification on the Gmail account and create a Google App Password. Use the App Password, not the account's normal password.
2. In the Render API service environment, set `MAIL_USERNAME` to the Gmail address, `MAIL_PASSWORD` to the App Password, and `MAIL_FROM` to the same Gmail address. `MAIL_HOST` and `MAIL_PORT` default to `smtp.gmail.com` and `587`.
3. Redeploy the API and test the forgot-password flow. It sends a 6-digit OTP that expires after 20 minutes and can be used once. The API returns the same accepted message whether or not the account exists.

Keep the App Password only in Render's environment settings; never commit it. If the SMTP credentials are missing, the reset endpoint returns a service-unavailable response while the rest of the API remains available. Resources have not been provisioned by this workspace; the `render.yaml` file only describes the proposed Render resources.

For local testing, set the Gmail values in the same PowerShell window used to start the API, then run Maven:

```powershell
$env:MAIL_USERNAME = "yourgmail@gmail.com"
$env:MAIL_PASSWORD = "your-16-character-app-password"
$env:MAIL_FROM = "yourgmail@gmail.com"
mvn spring-boot:run
```

These environment values apply only to that PowerShell window. Do not paste a real App Password into project files or source control.

Accounts use email and password; passwords must be at least 4 characters. Passwords are stored as BCrypt hashes and are never returned to the frontend. Registration does not verify email ownership. For production, consider requiring longer passwords because four-character passwords are easy to guess.

## Local checks

From `FRONTEND`, run `npm install` and `npm run build`. Local API requests default to `http://localhost:8081` because port 8080 may already be in use; set `VITE_API_BASE_URL` if your local API runs at a different origin. Password reset email requires the Gmail SMTP environment variables described above.

From `BACKEND`, run `mvn clean verify` with JDK 25. The default local profile uses a file-backed H2 database (`BACKEND/skillgap-data.mv.db`) so account data survives API restarts. On Windows with PostgreSQL 18 installed, open a PowerShell terminal in `BACKEND` and run `.\scripts\setup-local-postgres.ps1`. Enter the PostgreSQL administrator password only at its hidden terminal prompt. The setup creates a dedicated `skillgap_manager` database and `skillgap_app` role; it generates that app password and saves it encrypted for the current Windows account in a git-ignored file. Then run `.\scripts\run-local-postgres.ps1` to start the API on port 8081 using PostgreSQL. Use pgAdmin to connect to `localhost:5432`, database `skillgap_manager`; run [`BACKEND/sql/view-user-profiles.sql`](BACKEND/sql/view-user-profiles.sql) to inspect registered accounts and saved profile/skills. Password hashes and session data are intentionally excluded from that query. Existing H2 data is not automatically copied to this new PostgreSQL database.

The Render API activates the `postgres` profile and uses its PostgreSQL host, database, username, and password settings. Never put real database passwords in source files.
