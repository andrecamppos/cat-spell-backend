<!-- GSD:docs-update -->
# Deployment

## Local Development Stack

The `docker-compose.yml` provides the full local infrastructure:

```bash
podman compose up -d
```

### Services

| Service | Image | Ports | Purpose |
|---------|-------|-------|---------|
| `postgres` | `postgis/postgis:16-3.4-alpine` | `5432` | PostgreSQL with PostGIS |
| `minio` | `minio/minio:latest` | `9000` (API), `9001` (console) | S3-compatible object storage |

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

<!-- VERIFY: The following production recommendations are best practices — confirm they match the actual deployment target. -->

### Database

- [ ] Use a managed PostgreSQL instance with PostGIS extension enabled
- [ ] Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` environment variables
- [ ] Flyway migrations run automatically on startup — ensure the database user has DDL permissions
- [ ] Back up the database before deploying schema-changing releases

### Authentication

- [ ] Generate a strong JWT secret: `openssl rand -base64 64`
- [ ] Set `JWT_SECRET` to the generated value
- [ ] Never use the development default secret in production

### Object Storage

- [ ] Use AWS S3 or an S3-compatible service (e.g. DigitalOcean Spaces, Cloudflare R2)
- [ ] Create the photo bucket before first deployment
- [ ] Set `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`
- [ ] Configure bucket CORS if the mobile app uploads directly via presigned URLs

### Security

- [ ] Run behind a reverse proxy (nginx, Caddy, or cloud load balancer) with TLS termination
- [ ] The application listens on port `8080` by default — configure `server.port` or `SERVER_PORT` if needed
- [ ] WebSocket endpoint (`/ws`) should be proxied with WebSocket upgrade support
- [ ] Rate limiting is handled in-process via Bucket4j — consider additional rate limiting at the proxy level for DDoS protection

### Monitoring

- [ ] The health endpoint at `/actuator/health` returns `UP` when the application is healthy
- [ ] Custom health indicators check S3 connectivity and WebSocket status
- [ ] Health details (component-level) require authentication — configure an internal monitoring user or use a sidecar approach

### Container Deployment

<!-- VERIFY: No Dockerfile exists in the repository — these are recommendations for containerised deployment. -->

To containerise the application:

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

### Environment Variables Summary

| Variable | Required | Description |
|----------|----------|-------------|
| `DATABASE_URL` | Yes | JDBC PostgreSQL URL |
| `DATABASE_USERNAME` | Yes | Database user |
| `DATABASE_PASSWORD` | Yes | Database password |
| `JWT_SECRET` | Yes | Base64-encoded HS512 key (≥64 bytes) |
| `S3_ENDPOINT` | Yes | S3 endpoint URL |
| `S3_REGION` | Yes | S3 region |
| `S3_BUCKET` | Yes | Photo storage bucket name |
| `S3_ACCESS_KEY` | Yes | S3 access key |
| `S3_SECRET_KEY` | Yes | S3 secret key |
| `SERVER_PORT` | No | Override default port 8080 |
| `SPRING_PROFILES_ACTIVE` | No | Spring profile (e.g. `dev`) |
