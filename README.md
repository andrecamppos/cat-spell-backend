# cat-spell-backend

## Local Development

**Prerequisites:** JDK 17+, [Podman](https://podman.io/)

```bash
# Start PostgreSQL
podman compose up -d

# Run the app
./gradlew bootRun

# Run tests
./gradlew test

# Stop PostgreSQL
podman compose down
```
