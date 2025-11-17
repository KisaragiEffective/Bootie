package com.github.kisaragieffective.bootie.service

import cats.effect.{Sync, Temporal}
import cats.syntax.all.*
import com.github.kisaragieffective.bootie.client.MisskeyClient
import com.github.kisaragieffective.bootie.config.AppConfig
import com.github.kisaragieffective.bootie.domain.BoothItem
import com.github.kisaragieffective.bootie.repository.BoothItemRepository
import com.github.kisaragieffective.bootie.scraper.BoothScraper
import fs2.Stream
import retry.*
import retry.RetryPolicies.*
import retry.syntax.all.*
import scribe.Scribe
import scala.concurrent.duration.*
import java.time.LocalDateTime

class BoothServiceImpl[F[_]: Temporal: Sleep](
  scraper: BoothScraper[F],
  repository: BoothItemRepository[F],
  misskeyClient: MisskeyClient[F],
  config: AppConfig,
  logger: Scribe[F]
) extends BoothService[F] {

  // リトライポリシー: 指数バックオフで最大5回リトライ
  // 初期遅延: 1秒、最大遅延: 30秒
  private val retryPolicy: RetryPolicy[F] =
    limitRetries[F](5) |+| exponentialBackoff[F](1.second, maxDelay = 30.seconds)

  // リトライ時のログ出力
  private def logRetry(error: Throwable, details: RetryDetails): F[Unit] = {
    details match {
      case RetryDetails.WillDelayAndRetry(nextDelay, retriesSoFar, _) =>
        logger.warn(s"Retrying after ${nextDelay.toMillis}ms (attempt ${retriesSoFar + 1})", error)
      case RetryDetails.GivingUp(totalRetries, _) =>
        logger.error(s"Giving up after $totalRetries retries", error)
    }
  }

  override def scrapeAndStoreItems(): F[Unit] = {
    val action = for {
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

    // スクレイピングもリトライする
    action.retryingOnAllErrors(
      policy = retryPolicy,
      onError = logRetry
    ).handleErrorWith { error =>
      logger.error("Error during scraping after all retries", error)
    }
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
          postSingleItemWithRetry(item)
        }
        .compile
        .drain
      _ <- logger.info("Posting process completed")
    } yield ()
  }.handleErrorWith { error =>
    logger.error("Error during posting", error)
  }

  private def postSingleItemWithRetry(item: BoothItem): F[Unit] = {
    val postAction = for {
      _ <- logger.info(s"Attempting to post: ${item.name}")
      _ <- misskeyClient.createNote(item)
      _ <- repository.markAsPosted(item.id.get, LocalDateTime.now())
      _ <- logger.info(s"Successfully posted and marked: ${item.name}")
    } yield ()

    postAction.retryingOnAllErrors(
      policy = retryPolicy,
      onError = (error, details) => {
        logger.warn(s"Failed to post ${item.name}", error) *> logRetry(error, details)
      }
    ).handleErrorWith { error =>
      // すべてのリトライが失敗した場合、ログを出力
      // アイテムはデータベース上でposted_to_misskey=falseのままなので、
      // 次回のバッチで再度取得される（キューの最後尾に積み直される）
      logger.error(
        s"Failed to post ${item.name} after all retries. " +
        s"Item will be retried in the next batch.",
        error
      )
    }
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
