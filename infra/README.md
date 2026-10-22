# Infrastructure notes

* `init-databases.sql` — creates the `bookleaf_test` database used by the
  integration tests. Mounted into the Postgres container by `docker-compose.yml`.
* `render.yaml` — Render blueprint for a one-click deployment of the web
  service plus its managed Postgres.
