package com.github.kisaragieffective.bootie.db

import cats.effect.Sync
import cats.syntax.all.*
import com.github.kisaragieffective.bootie.config.DatabaseConfig
import org.flywaydb.core.Flyway
import scalikejdbc.{ConnectionPool, ConnectionPoolSettings}
import scribe.Scribe

trait DatabaseInitializer[F[_]] {
  def initialize(): F[Unit]
}

class DatabaseInitializerImpl[F[_]: Sync](
  config: DatabaseConfig,
  logger: Scribe[F]
) extends DatabaseInitializer[F] {

  override def initialize(): F[Unit] = {
    for {
      _ <- logger.info("Initializing database connection pool")
      _ <- initializeConnectionPool()
      _ <- logger.info("Running Flyway migrations")
      _ <- runMigrations()
      _ <- logger.info("Database initialization completed")
    } yield ()
  }

  private def initializeConnectionPool(): F[Unit] = Sync[F].delay {
    val settings = ConnectionPoolSettings(
      initialSize = config.poolInitialSize,
      maxSize = config.poolMaxSize,
      connectionTimeoutMillis = config.poolConnectionTimeoutMillis
    )

    ConnectionPool.singleton(
      config.url,
      config.user,
      config.password,
      settings
    )
  }

  private def runMigrations(): F[Unit] = Sync[F].delay {
    val flyway = Flyway.configure()
      .dataSource(config.url, config.user, config.password)
      .locations("classpath:db/migration")
      .load()

    flyway.migrate()
    ()
  }
}
