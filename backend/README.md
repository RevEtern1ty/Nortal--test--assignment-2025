# Backend (Spring Boot) overview

Modules:
- `core` - domain entities and library-service rules.
- `persistence` - Spring Data JPA adapters for H2; simple schema via `schema.sql`.
- `api` - Spring Boot app, controllers, security, seed data.
- The assignment uses the Spring Boot stack (core/persistence/api).

## Run
- Install JDK 21.
- From the repository root, run `node tools/run-backend.mjs start`.
- The API listens on `127.0.0.1:8080` for local use. The H2 console is at `http://127.0.0.1:8080/h2-console` (JDBC URL `jdbc:h2:mem:library`).
- Dev seeds: members `m1..m4`, books `b1..b6`.

## Local demo scope

The browser UI uses the local API without authentication. Optional assignment-only JWT wiring uses public sample keys for local testing. Keep this service on your computer and use sample records.

## API surface
- `GET /api/books` -> `{ items: [{ id, title, loanedTo, reservationQueue }] }`
- `GET /api/members` -> `{ items: [{ id, name }] }`
- `POST /api/books|members` with `{ id, title|name }` -> `{ ok, reason? }`
- `PUT /api/books|members` same body -> `{ ok, reason? }`
- `DELETE /api/books|members` with `{ id }` -> `{ ok, reason? }`
- `POST /api/borrow` `{ bookId, memberId }` -> `{ ok, reason? }`
- `POST /api/reserve` `{ bookId, memberId }` -> `{ ok, reason? }`
- `POST /api/cancel-reservation` `{ bookId, memberId }` -> `{ ok, reason? }`
- `POST /api/return` `{ bookId, memberId }` -> `{ ok, reason?, nextMemberId? }`
- `GET /api/health` -> `{ status: "ok" }`

## Useful properties
- `library.security.enforce` (default `false`) - toggle auth.
- `library.security.print-demo-token` (default `false`) - print a demo JWT at startup.
