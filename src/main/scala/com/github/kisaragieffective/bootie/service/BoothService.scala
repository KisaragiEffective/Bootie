package com.github.kisaragieffective.bootie.service

import cats.effect.{Sync, Temporal}
import cats.syntax.all.*
import com.github.kisaragieffective.bootie.client.MisskeyClient
import com.github.kisaragieffective.bootie.config.{AppConfig, PostingConfig, ScrapingConfig}
import com.github.kisaragieffective.bootie.domain.BoothItem
import com.github.kisaragieffective.bootie.repository.BoothItemRepository
import com.github.kisaragieffective.bootie.scraper.BoothScraper
import fs2.Stream
import scribe.Scribe
import scala.concurrent.duration.*
import java.time.LocalDateTime

trait BoothService[F[_]] {
  def scrapeAndStoreItems(): F[Unit]
  def postUnpostedItems(): F[Unit]
  def runPeriodicScraping(): Stream[F, Unit]
  def runPeriodicPosting(): Stream[F, Unit]
}

class BoothServiceImpl[F[_]: Temporal](
  scraper: BoothScraper[F],
  repository: BoothItemRepository[F],
  misskeyClient: MisskeyClient[F],
  config: AppConfig,
  logger: Scribe[F]
) extends BoothService[F] {

  override def scrapeAndStoreItems(): F[Unit] = {
    for {
      _ <- logger.info("Starting scraping process")
      items <- scraper.scrapeNewItems()
      _ <- logger.info(s"Scraped ${items.length} items")
      stored <- items.traverse { item =>
        repository.findByBoothItemId(item.boothItemId).flatMap {
          case Some(_) =>
            logger.debug(s"Item already exists: ${item.boothItemId}") *> Sync[F].pure(false)
          case None =>
            repository.insert(item) *>
              logger.info(s"Stored new item: ${item.name}") *>
              Sync[F].pure(true)
        }
      }
      newItemsCount = stored.count(identity)
      _ <- logger.info(s"Stored $newItemsCount new items")
    } yield ()
  }.handleErrorWith { error =>
    logger.error("Error during scraping", error)
  }

  override def postUnpostedItems(): F[Unit] = {
    val batchSize = 10 // 一度に取得する未投稿アイテムの数

    for {
      _ <- logger.info("Starting posting process")
      items <- repository.findUnpostedItems(batchSize)
      _ <- logger.info(s"Found ${items.length} unposted items")
      _ <- Stream.emits(items)
        .metered(config.posting.intervalSeconds.seconds) // 投稿間隔を制御
        .parEvalMap(config.posting.parallelism) { item => // 並列度を制御
          (for {
            _ <- misskeyClient.createNote(item)
            _ <- repository.markAsPosted(item.id.get, LocalDateTime.now())
            _ <- logger.info(s"Successfully posted and marked: ${item.name}")
          } yield ()).handleErrorWith { error =>
            logger.error(s"Failed to post item: ${item.name}", error)
          }
        }
        .compile
        .drain
      _ <- logger.info("Posting process completed")
    } yield ()
  }.handleErrorWith { error =>
    logger.error("Error during posting", error)
  }

  override def runPeriodicScraping(): Stream[F, Unit] = {
    Stream.awakeEvery[F](config.scraping.intervalSeconds.seconds)
      .evalMap(_ => scrapeAndStoreItems())
  }

  override def runPeriodicPosting(): Stream[F, Unit] = {
    Stream.awakeEvery[F](config.posting.intervalSeconds.seconds)
      .evalMap(_ => postUnpostedItems())
  }
}
