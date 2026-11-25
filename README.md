# Library lending and reservation demo

An Angular and Spring Boot implementation of the Nortal LEAP coding assignment. The demo shows a workflow with rules that matter in practice: the same book cannot be loaned to two people, reservations keep their order, and returns are tied to the current borrower.

## Try it locally

Install **Node.js 24 LTS** and the **Java 21 JDK**, then open the downloaded repository folder in PowerShell, Terminal, or a similar application. Run these commands in separate terminal windows, both from the repository folder. The first start downloads project dependencies.

**Terminal 1**

```sh
node tools/run-backend.mjs start
```

**Terminal 2**

```sh
node tools/run-frontend.mjs install
node tools/run-frontend.mjs start
```

Open [http://localhost:4200](http://localhost:4200). The catalog and member list are seeded with sample records. The demo listens locally, uses an in-memory database, and resets the sample catalog when the backend restarts.

### Try a reservation handoff

1. Select **b1 — Clean Code** and member **m1 — Kertu**, then click **Borrow**.
2. Keep the same book, switch to **m2 — Rasmus**, and click **Reserve**. The reservation queue shows `m2`.
3. Switch back to **m1 — Kertu** and click **Return**. The browser shows the successful handoff, and the book's borrower changes to `m2`.

If the app cannot reach the API, open its [health check](http://127.0.0.1:8080/api/health). When the response shows `{"status":"ok"}`, refresh the browser app. If a port is already in use, stop the conflicting process before retrying.

## What to inspect

- [`frontend`](frontend/) contains the Angular UI and API client.
- [`backend/core`](backend/core/) contains lending and reservation rules and their unit tests.
- [`backend/api`](backend/api/) contains HTTP endpoints, seeded data, and integration tests.
- [`backend/persistence`](backend/persistence/) contains the database adapters.

From the repository folder, run `node tools/run-backend.mjs test` for backend tests and `node tools/run-frontend.mjs build` for the production frontend build.

The [original assignment brief](ASSIGNMENT.md), [AI usage notes](AI_USAGE.md), and [MIT license](LICENSE) accompany the candidate solution. Use sample data in this local demonstration.
