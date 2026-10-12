# Skill Gap Manager

A responsive skill assessment app. Create an account with your email and password, save your profile and skills, and return later to update your progress.

## Requirements

- Node.js 18 or newer
- npm

## Run locally

Start the Java API from `BACKEND` with JDK 25. It uses a local file-backed H2 database by default. For local PostgreSQL 18, use the `BACKEND/scripts/setup-local-postgres.ps1` setup script once, then `BACKEND/scripts/run-local-postgres.ps1` whenever you want to run the API against PostgreSQL. The SQL query for viewing saved account/profile data is `BACKEND/sql/view-user-profiles.sql`; details are in the repository [deployment guide](../DEPLOYMENT.md).

Optionally set `VITE_API_BASE_URL` if the API is not at `http://localhost:8081`, then run:

```sh
npm install
npm run dev
```

Open the local URL printed by Vite. For production, see the repository's [deployment guide](../DEPLOYMENT.md). To create and preview a production build:

```sh
npm run build
npm run preview
```

## Included roles

- Java Developer
- Python Developer
- Web Developer
- Data Analyst
- Full Stack Developer

## Accounts and data

Email/password authentication is handled by the Java API. Passwords must be at least 4 characters and are stored as BCrypt hashes. Profile fields and per-role skill selections are saved in the backend database and are restored after signing in. Forgot-password sends a 6-digit OTP through Gmail SMTP when the API's `MAIL_USERNAME`, `MAIL_PASSWORD` (a Gmail App Password), and `MAIL_FROM` settings are configured. Codes expire after 20 minutes and are valid once. Registration does not verify email ownership; see the repository [deployment guide](../DEPLOYMENT.md) for production setup.

## Backend tests and coverage

From `BACKEND`, run `mvn clean verify` with JDK 25. This runs the backend unit and account integration tests and generates the JaCoCo report at `target/site/jacoco/index.html`.
