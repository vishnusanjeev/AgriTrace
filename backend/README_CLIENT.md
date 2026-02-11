# AgriTrace Local Stack (Docker)

Run the full local stack with one command:

```bash
docker compose up --build
```

What starts:
- MySQL (with schema auto-imported from `api/schema_query.sql`)
- PHP/Apache API on port 8080
- Hardhat local chain on port 8545
- Chain bridge on port 5055 (auto-deploys contract if missing)

Health checks:

```bash
curl http://localhost:5055/bridge/health
curl http://localhost:8080/batches/my_products.php
```

Troubleshooting:
- Ports in use: stop the conflicting process or change ports in `docker-compose.yml`.
- Rebuild images after changes: `docker compose up --build`.
- Reset MySQL data: stop stack and delete `./docker/mysql_data`.

Verification steps:
1) `docker compose up --build`
2) Record a hash:
   ```bash
   curl -X POST http://localhost:5055/bridge/batchCreated \
     -H "Content-Type: application/json" \
     -d "{\"batch_id\":1,\"hash_hex\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"
   ```
3) Read back:
   ```bash
   curl http://localhost:5055/chain/batch/1
   ```
4) Confirm schema exists:
   ```bash
   mysql -h 127.0.0.1 -u agritrace -pagritrace -e "USE agritrace; SHOW TABLES;"
   ```
