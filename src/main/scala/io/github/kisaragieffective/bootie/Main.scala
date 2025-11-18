package io.github.kisaragieffective.bootie

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import io.github.kisaragieffective.bootie.client.MisskeyClientImpl
import io.github.kisaragieffective.bootie.config.AppConfig
import io.github.kisaragieffective.bootie.db.DatabaseInitializerImpl
import io.github.kisaragieffective.bootie.repository.BoothItemRepositoryImpl
import io.github.kisaragieffective.bootie.scraper.BoothScraperImpl
import io.github.kisaragieffective.bootie.service.BoothServiceImpl
import fs2.Stream
import org.http4s.ember.client.EmberClientBuilder
import scribe.Scribe
import scribe.cats.io

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    // ロガーの初期化
    given Scribe[IO] = io

    val config = AppConfig.load()

    EmberClientBuilder.default[IO].build.use { httpClient =>
      for {
        _ <- io.info("Starting Bootie application")

        // データベース初期化
        dbInitializer = new DatabaseInitializerImpl[IO](config.database, io)
        _ <- dbInitializer.initialize()

        // 依存関係の構築
        repository = new BoothItemRepositoryImpl[IO]()
        scraper = new BoothScraperImpl[IO](httpClient, config.scraping, io)
        misskeyClient = new MisskeyClientImpl[IO](httpClient, config.misskey, io)
        service = new BoothServiceImpl[IO](scraper, repository, misskeyClient, config, io)

        _ <- io.info("All components initialized successfully")

        // スクレイピングと投稿のストリームを並列実行
        _ <- Stream(
          service.runPeriodicScraping(),
          service.runPeriodicPosting()
        ).parJoinUnbounded.compile.drain

      } yield ExitCode.Success
    }.handleErrorWith { error =>
      io.error("Fatal error in main application", error) *>
        IO.pure(ExitCode.Error)
    }
  }
}
