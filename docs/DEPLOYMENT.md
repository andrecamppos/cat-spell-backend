<!-- generated-by: gsd-doc-writer -->
# Deployment

## Local Development Stack

The `docker-compose.yml` provides the full local infrastructure:

```bash
podman compose up -d          # or: docker compose up -d
```

### Services

| Service | Image | Host Ports | Purpose |
|---------|-------|------------|---------|
| `postgres` | `postgis/postgis:16-3.4-alpine` | `5432` | PostgreSQL with PostGIS |
| `minio` | `minio/minio:latest` | `9002` (API), `9001` (console) | S3-compatible object storage |

### Volumes

- `pgdata` — PostgreSQL data persistence
- `minio-data` — MinIO object storage persistence

### Credentials (local only)

| Service | User | Password |
|---------|------|----------|
| PostgreSQL | `catspell` | `catspell` |
| MinIO | `catspell` | `catspell123` |

## Building for Production

### Build the JAR

```bash
./gradlew bootJar
```

The fat JAR is produced at `build/libs/cat-spell-backend-0.0.1-SNAPSHOT.jar`.

### Run the JAR

```bash
java -jar build/libs/cat-spell-backend-0.0.1-SNAPSHOT.jar
```

All configuration is supplied via environment variables — see [Configuration](CONFIGURATION.md).

## Production Checklist

### Database

- [ ] Use a managed PostgreSQL 16+ instance with PostGIS extension enabled
- [ ] Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` environment variables
- [ ] Flyway migrations (13 total) run automatically on startup — ensure the database user has DDL permissions
- [ ] Back up the database before deploying schema-changing releases
<!-- VERIFY: specific managed database provider and PostGIS setup steps -->

### Authentication

- [ ] Generate a strong JWT secret: `openssl rand -base64 64`
- [ ] Set `JWT_SECRET` to the generated value
- [ ] Never use the development default secret in production

### Object Storage

- [ ] Use AWS S3 or an S3-compatible service (e.g. DigitalOcean Spaces, Cloudflare R2)
- [ ] Create the `catspell-photos` bucket before first deployment (or rely on auto-creation if the app has `s3:CreateBucket` permission)
- [ ] Set `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`
- [ ] Configure bucket CORS if the mobile app uploads directly via presigned URLs
<!-- VERIFY: production S3 provider and bucket CORS configuration -->

### Security

- [ ] Run behind a reverse proxy (nginx, Caddy, or cloud load balancer) with TLS termination
- [ ] The application listens on port `8080` by default — configure `server.port` or `SERVER_PORT` if needed
- [ ] WebSocket endpoint (`/ws`) must be proxied with WebSocket upgrade support
- [ ] Rate limiting is handled in-process via Bucket4j (10 req/min per IP on auth endpoints) — consider additional rate limiting at the proxy level for DDoS protection

### Monitoring

- [ ] The health endpoint at `/actuator/health` returns `UP` when the application is healthy
- [ ] Custom health indicators check S3 connectivity (`S3HealthIndicator`) and WebSocket status (`WebSocketHealthIndicator`)
- [ ] Health details (component-level) require authentication — configure an internal monitoring user or use a sidecar approach

### Container Deployment

No Dockerfile is included in the repository. To containerise the application:

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/cat-spell-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:

```bash
podman build -t cat-spell-backend .
podman run -p 8080:8080 --env-file .env cat-spell-backend
```

## Environment Variables Summary

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DATABASE_URL` | Yes | `jdbc:postgresql://localhost:5432/catspell` | JDBC PostgreSQL URL |
| `DATABASE_USERNAME` | Yes | `catspell` | Database user |
| `DATABASE_PASSWORD` | Yes | `catspell` | Database password |
| `JWT_SECRET` | Yes | Dev default | Base64-encoded HS512 key (≥64 bytes) |
| `S3_ENDPOINT` | Yes | `http://localhost:9002` | S3 endpoint URL |
| `S3_REGION` | Yes | `us-east-1` | S3 region |
| `S3_BUCKET` | Yes | `catspell-photos` | Photo storage bucket name |
| `S3_ACCESS_KEY` | Yes | `catspell` | S3 access key |
| `S3_SECRET_KEY` | Yes | `catspell123` | S3 secret key |
| `SERVER_PORT` | No | `8080` | Override default port |
| `SPRING_PROFILES_ACTIVE` | No | — | Spring profile (e.g. `dev`) |
