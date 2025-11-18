# AI Agent Development Guide

This document describes the architecture, design principles, and development guidelines for the Bootie project to assist AI agents in understanding and contributing to the codebase.

## Project Overview

Bootie is a Scala 3 application that scrapes BOOTH (a Japanese marketplace) for new VRChat-tagged products and automatically posts them to Misskey (a decentralized social network).

### Core Technologies

- **Language**: Scala 3.7.4
- **Runtime**: JDK 25
- **Database**: PostgreSQL 18
- **Effect System**: cats-effect (functional effects)
- **Streaming**: fs2 (functional streams)
- **HTTP Client**: http4s with Ember backend
- **JSON**: circe
- **Database Access**: ScalikeJDBC
- **Schema Migration**: Flyway
- **HTML Parsing**: jsoup
- **Logging**: scribe
- **Retry Logic**: cats-retry

## Architecture Principles

### 1. Tagless Final Pattern

**DO NOT use `IO` directly in business logic.** All effects are abstracted using the tagless final pattern with type parameters like `F[_]`.

```scala
// ✅ GOOD: Abstract over effect type
trait BoothService[F[_]] {
  def scrapeAndStoreItems(): F[Unit]
}

class BoothServiceImpl[F[_]: Temporal: Sleep](
  scraper: BoothScraper[F],
  // ...
) extends BoothService[F]

// ❌ BAD: Direct IO usage outside Main
class BoothServiceImpl(scraper: BoothScraper[IO]) {
  def scrapeAndStoreItems(): IO[Unit] = ???
}
```

**Only use `IO` in the `Main.scala` entry point (IOApp).**

### 2. Effect Separation

All side effects must be wrapped in `Sync.delay` or appropriate effect constructors:

```scala
// ✅ GOOD: Wrapped side effects
def findByBoothItemId(boothItemId: String): F[Option[BoothItem]] =
  Sync[F].delay {
    DB.readOnly { implicit session =>
      sql"SELECT * FROM booth_items WHERE booth_item_id = $boothItemId"
        .map(toBoothItem).single.apply()
    }
  }

// ❌ BAD: Raw side effects
def findByBoothItemId(boothItemId: String): F[Option[BoothItem]] = {
  DB.readOnly { implicit session =>
    sql"SELECT * FROM booth_items WHERE booth_item_id = $boothItemId"
      .map(toBoothItem).single.apply()
  }
}
```

### 3. Interface-Implementation Separation

Traits define interfaces, and implementation classes are in separate files:

```
src/main/scala/io/github/kisaragieffective/bootie/
├── service/
│   ├── BoothService.scala       # Interface (trait)
│   └── BoothServiceImpl.scala   # Implementation
├── repository/
│   ├── BoothItemRepository.scala     # Interface
│   └── BoothItemRepositoryImpl.scala # Implementation
```

### 4. Error Handling Strategy

Use **cats-retry** for automatic retry with exponential backoff:

```scala
// Retry policy
private val retryPolicy: RetryPolicy[F] =
  limitRetries[F](5) |+| exponentialBackoff[F](1.second, maxDelay = 30.seconds)

// Apply retry
action.retryingOnAllErrors(
  policy = retryPolicy,
  onError = logRetry
)
```

**Failed items are automatically re-queued** by leaving `posted_to_misskey=false` in the database. They will be retried in the next batch.

### 5. Stream-Based Processing

Use **fs2** for all periodic and parallel processing:

```scala
// Periodic execution
Stream.awakeEvery[F](interval.seconds)
  .evalMap(_ => performAction())

// Parallel processing with rate limiting
Stream.emits(items)
  .metered(interval.seconds)        // Rate limiting
  .parEvalMap(parallelism) { item => // Parallelism control
    processItem(item)
  }
```

## Project Structure

```
bootie/
├── src/
│   ├── main/
│   │   ├── scala/io/github/kisaragieffective/bootie/
│   │   │   ├── Main.scala                    # IOApp entry point (only IO usage)
│   │   │   ├── client/
│   │   │   │   ├── MisskeyClient.scala       # Interface
│   │   │   │   └── MisskeyClientImpl.scala   # Implementation
│   │   │   ├── config/
│   │   │   │   └── AppConfig.scala           # Configuration
│   │   │   ├── db/
│   │   │   │   ├── DatabaseInitializer.scala     # Interface
│   │   │   │   └── DatabaseInitializerImpl.scala # Implementation
│   │   │   ├── domain/
│   │   │   │   └── BoothItem.scala           # Domain model
│   │   │   ├── repository/
│   │   │   │   ├── BoothItemRepository.scala     # Interface
│   │   │   │   └── BoothItemRepositoryImpl.scala # Implementation
│   │   │   ├── scraper/
│   │   │   │   ├── BoothScraper.scala        # Interface
│   │   │   │   └── BoothScraperImpl.scala    # Implementation
│   │   │   └── service/
│   │   │       ├── BoothService.scala        # Interface
│   │   │       └── BoothServiceImpl.scala    # Implementation
│   │   └── resources/
│   │       ├── application.conf              # Typesafe Config
│   │       └── db/migration/                 # Flyway migrations
│   │           └── V1__Create_booth_items_table.sql
│   └── test/
│       └── scala/io/github/kisaragieffective/bootie/
│           └── service/
│               └── BoothServiceSpec.scala    # Tests
├── build.sbt                                 # Build configuration
├── Dockerfile                                # Multi-stage build
├── docker-compose.yaml                       # Local development
└── .github/
    └── workflows/
        ├── ci.yaml                           # Continuous Integration
        └── cd.yaml                           # Continuous Deployment
```

## Development Guidelines

### Adding New Features

1. **Define the interface** as a trait with `F[_]` type parameter
2. **Implement in a separate file** with appropriate effect constraints
3. **Use `Sync.delay`** for all side effects
4. **Add retry logic** for network operations using cats-retry
5. **Write tests** with mocked dependencies
6. **Update documentation** (README.md, this file)

### Testing

- Use **munit-cats-effect** for effect testing
- Use **mockito-scala** for mocking dependencies
- Test parallelism and concurrency behavior
- Verify retry logic and error handling

### Configuration

- All configuration goes in `src/main/resources/application.conf`
- Support environment variable overrides using `${?ENV_VAR}` syntax
- Document all configuration options in README.md

### Database

- Use **Flyway** for schema migrations (versioned SQL files)
- Use **ScalikeJDBC** for database access
- Wrap all database operations in `Sync.delay`
- Use connection pooling (configured in AppConfig)

### Logging

- Use **scribe** with cats-effect integration
- Log at appropriate levels (debug, info, warn, error)
- Include context in log messages

### HTTP Requests

- Always set `User-Agent` header
- Use custom User-Agent (not browser mimicry): `Bootie/VERSION (+REPO_URL)`
- Handle errors with retry logic
- Use http4s circe integration for JSON

## Common Patterns

### Creating a New Service

```scala
// 1. Define interface
trait MyService[F[_]] {
  def doSomething(): F[Unit]
}

// 2. Implement in separate file
class MyServiceImpl[F[_]: Sync](
  dependency: SomeDependency[F],
  logger: Scribe[F]
) extends MyService[F] {

  override def doSomething(): F[Unit] = {
    for {
      _ <- logger.info("Doing something")
      result <- dependency.fetch()
      _ <- Sync[F].delay {
        // Side effects here
      }
    } yield ()
  }
}
```

### Creating a New Repository

```scala
// 1. Define interface
trait MyRepository[F[_]] {
  def findById(id: Long): F[Option[MyEntity]]
  def insert(entity: MyEntity): F[Long]
}

// 2. Implement with ScalikeJDBC
class MyRepositoryImpl[F[_]: Sync] extends MyRepository[F] {

  override def findById(id: Long): F[Option[MyEntity]] =
    Sync[F].delay {
      DB.readOnly { implicit session =>
        sql"SELECT * FROM my_table WHERE id = $id"
          .map(toEntity).single.apply()
      }
    }

  override def insert(entity: MyEntity): F[Long] =
    Sync[F].delay {
      DB.localTx { implicit session =>
        sql"INSERT INTO my_table (...) VALUES (...)"
          .updateAndReturnGeneratedKey.apply()
      }
    }
}
```

### Adding Retry Logic

```scala
import retry.*
import retry.RetryPolicies.*
import retry.syntax.all.*

class MyService[F[_]: Temporal: Sleep](logger: Scribe[F]) {

  private val retryPolicy: RetryPolicy[F] =
    limitRetries[F](5) |+| exponentialBackoff[F](1.second, maxDelay = 30.seconds)

  private def logRetry(error: Throwable, details: RetryDetails): F[Unit] = {
    details match {
      case RetryDetails.WillDelayAndRetry(nextDelay, retriesSoFar, _) =>
        logger.warn(s"Retrying after ${nextDelay.toMillis}ms (attempt ${retriesSoFar + 1})", error)
      case RetryDetails.GivingUp(totalRetries, _) =>
        logger.error(s"Giving up after $totalRetries retries", error)
    }
  }

  def doSomethingWithRetry(): F[Unit] = {
    val action = ??? // Your action here

    action.retryingOnAllErrors(
      policy = retryPolicy,
      onError = logRetry
    )
  }
}
```

## CI/CD

### Continuous Integration (.github/workflows/ci.yaml)

Runs on every push and pull request:
- Compile Scala code
- Run tests
- Check formatting (optional)

### Continuous Deployment (.github/workflows/cd.yaml)

Runs on release creation:
- Build Docker image
- Tag with release version and `latest`
- Push to GitHub Container Registry (GHCR)

## Docker

### Multi-Stage Build

The Dockerfile uses a multi-stage build:
1. **Builder stage**: Compile Scala code with sbt
2. **Runtime stage**: Minimal JRE image with compiled application

### Running Locally

```bash
docker-compose up -d
```

This starts:
- PostgreSQL 18 (with health check)
- Bootie application

## Key Constraints and Rules

1. ❌ **NEVER** use `IO` directly outside `Main.scala`
2. ✅ **ALWAYS** use `F[_]` with appropriate constraints (`Sync`, `Temporal`, `Concurrent`)
3. ✅ **ALWAYS** wrap side effects in `Sync.delay`
4. ✅ **ALWAYS** use cats-retry for network operations
5. ✅ **ALWAYS** separate interfaces and implementations
6. ✅ **ALWAYS** use fs2 for streaming and periodic tasks
7. ✅ **ALWAYS** set User-Agent header for HTTP requests
8. ✅ **ALWAYS** write tests for new features
9. ✅ **ALWAYS** use Flyway for database schema changes
10. ✅ **ALWAYS** document configuration options

## Troubleshooting

### Tests Failing

- Ensure mocked dependencies return expected types
- Check effect type constraints (Sync, Temporal, Concurrent, Sleep)
- Verify test resources are properly cleaned up

### Build Failing

- Check Scala version compatibility (3.7.4)
- Verify all dependencies are compatible
- Ensure sbt-native-packager is enabled

### Docker Build Failing

- Check that `sbt stage` works locally
- Verify Dockerfile uses correct JDK version (25)
- Ensure all resources are included in the build

## Resources

- [cats-effect documentation](https://typelevel.org/cats-effect/)
- [fs2 documentation](https://fs2.io/)
- [http4s documentation](https://http4s.org/)
- [ScalikeJDBC documentation](http://scalikejdbc.org/)
- [Flyway documentation](https://flywaydb.org/)

## Contributing

When making changes:
1. Follow the architecture principles above
2. Write tests for new functionality
3. Update documentation as needed
4. Ensure CI passes before merging
5. Keep commits focused and descriptive
