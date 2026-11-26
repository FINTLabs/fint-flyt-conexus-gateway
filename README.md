# FINT Flyt Conexus Gateway

Gateway for archiving student documents from Conexus Engage and Elevate through FINT Flyt. See [FFS-2426](https://novari-iks.atlassian.net/browse/FFS-2426) for the integration scope.

## Build

Install JDK 25 and use the included Gradle Wrapper. Dependency resolution needs access to Gradle Plugin Portal, Maven Central and the FINTLabs Maven repository. Docker is needed to build the container image.

```bash
./gradlew check bootJar
docker build -t fint-flyt-conexus-gateway:local .
```

`check` includes ktlint. The application JAR is written to `build/libs`.

## Run locally

Docker Compose provides Kafka and Postgres. Add the `tools` profile to include Kafdrop at `http://localhost:19000`.

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local-staging'
```

The local gateway uses port 8111. The `local-staging` profile defines the addresses for other Flyt services. For authenticated API calls, run the local authorization service and configure the Flyt OAuth clients and authorized source application ID. Metadata registration also requires the local integration configuration service on port 8095.

## Conexus API

The JSON endpoints use bearer authentication through the existing Flyt resource server.

- `POST /api/conexus/instances` accepts one document per instance using the grouped fields in Conexus API draft 0.2.
- `POST /api/conexus/metadata` takes a `metadata` object with `dokumenttype`, `integrationDisplayName` and a positive integer `version`, plus an `elements` array. Conexus defines each field with a unique `id`, a `displayName`, a `type` (`String` or `File`) and a `required` flag. Submit the full dataset with a new version when adding, removing or changing fields. Keep IDs stable for fields you retain. The gateway registers metadata synchronously through the Flyt integration configuration service. Instance validation follows the instance API contract.

The OpenAPI description is available at `/api/conexus/v3/api-docs`. Use the configured organisation prefix when calling the gateway externally.

Both endpoints return `400` for invalid JSON or invalid request fields. Metadata returns `422` for invalid field definitions, such as duplicate IDs or unsupported types, and `409` if the version already exists for the integration. A new version returns `202` after registration in Flyt. A valid instance request returns `503` until its processing handler is wired up. The HTTP layer checks base64 encoding. PDF/A conformance and the maximum document size are separate contract decisions.

## Repository automation

CI uses the shared FINT Flyt workflow to build the application and container on pull requests and publish the image on pushes to `main`. CodeQL scans Java and Kotlin, and Dependabot updates Gradle, Docker and GitHub Actions dependencies.

The Gradle build uses dependency locking. When changing dependencies manually, update `gradle.lockfile` with:

```bash
./gradlew dependencies --write-locks
```
