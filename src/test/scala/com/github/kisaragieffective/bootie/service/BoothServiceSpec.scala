package com.github.kisaragieffective.bootie.service

import cats.effect.{IO, Ref}
import cats.syntax.all.*
import com.github.kisaragieffective.bootie.client.MisskeyClient
import com.github.kisaragieffective.bootie.config.{AppConfig, MisskeyConfig, PostingConfig, ScrapingConfig, DatabaseConfig}
import com.github.kisaragieffective.bootie.domain.BoothItem
import com.github.kisaragieffective.bootie.repository.BoothItemRepository
import com.github.kisaragieffective.bootie.scraper.BoothScraper
import fs2.Stream
import munit.CatsEffectSuite
import scribe.Scribe
import scribe.cats.io

import java.time.LocalDateTime
import scala.concurrent.duration.*

class BoothServiceSpec extends CatsEffectSuite {

  given Scribe[IO] = io

  // モック用のテストデータ
  def createTestItem(id: String): BoothItem = BoothItem(
    id = Some(id.toLong),
    boothItemId = id,
    name = s"Test Item $id",
    url = s"https://booth.pm/items/$id",
    thumbnailUrl = None,
    shopName = Some("Test Shop"),
    price = Some("1000円"),
    tags = List("VRChat"),
    scrapedAt = LocalDateTime.now(),
    postedToMisskey = false,
    postedAt = None,
    createdAt = LocalDateTime.now(),
    updatedAt = LocalDateTime.now()
  )

  // テスト用の設定
  def createTestConfig(parallelism: Int, intervalSeconds: Int): AppConfig = AppConfig(
    database = DatabaseConfig(
      driver = "org.postgresql.Driver",
      url = "jdbc:postgresql://localhost:5432/test",
      user = "test",
      password = "test",
      poolInitialSize = 1,
      poolMaxSize = 5,
      poolConnectionTimeoutMillis = 1000
    ),
    misskey = MisskeyConfig(
      instanceUrl = "https://test.example.com",
      token = "test-token",
      visibility = "home"
    ),
    posting = PostingConfig(
      intervalSeconds = intervalSeconds,
      parallelism = parallelism
    ),
    scraping = ScrapingConfig(
      intervalSeconds = 10,
      userAgent = "Test/1.0",
      boothUrl = "https://test.booth.pm",
      targetTag = "VRChat"
    )
  )

  test("postUnpostedItems should respect parallelism setting") {
    // 並列度が1の場合、アイテムは順次処理されるべき
    val parallelism = 1
    val testItems = (1 to 5).map(i => createTestItem(i.toString)).toList

    // 並列処理中のアイテム数を追跡するための共有状態
    Ref.of[IO, Int](0).flatMap { activeCountRef =>
      Ref.of[IO, Int](0).flatMap { maxActiveCountRef =>
        Ref.of[IO, List[BoothItem]](List.empty).flatMap { postedItemsRef =>

          // モックリポジトリ
          val mockRepository = new BoothItemRepository[IO] {
            override def findByBoothItemId(boothItemId: String): IO[Option[BoothItem]] =
              IO.pure(testItems.find(_.boothItemId == boothItemId))

            override def insert(item: BoothItem): IO[Long] =
              IO.pure(item.id.get)

            override def markAsPosted(id: Long, postedAt: LocalDateTime): IO[Unit] =
              IO.unit

            override def findUnpostedItems(limit: Int): IO[List[BoothItem]] =
              IO.pure(testItems.take(limit))
          }

          // モックMisskeyクライアント
          val mockMisskeyClient = new MisskeyClient[IO] {
            override def createNote(item: BoothItem): IO[Unit] = {
              for {
                // アクティブカウントを増やす
                active <- activeCountRef.updateAndGet(_ + 1)
                // 最大アクティブカウントを更新
                _ <- maxActiveCountRef.update(max => if (active > max) active else max)
                // 少し待機（並列処理をシミュレート）
                _ <- IO.sleep(50.millis)
                // 投稿されたアイテムを記録
                _ <- postedItemsRef.update(_ :+ item)
                // アクティブカウントを減らす
                _ <- activeCountRef.update(_ - 1)
              } yield ()
            }
          }

          // モックスクレイパー
          val mockScraper = new BoothScraper[IO] {
            override def scrapeNewItems(): IO[List[BoothItem]] =
              IO.pure(List.empty)
          }

          val config = createTestConfig(parallelism, intervalSeconds = 0)
          val service = new BoothServiceImpl[IO](
            mockScraper,
            mockRepository,
            mockMisskeyClient,
            config,
            io
          )

          // テスト実行
          for {
            _ <- service.postUnpostedItems()
            maxActive <- maxActiveCountRef.get
            postedItems <- postedItemsRef.get
          } yield {
            // 並列度が1の場合、最大アクティブ数は1であるべき
            assertEquals(maxActive, parallelism)
            // すべてのアイテムが投稿されているべき
            assertEquals(postedItems.length, testItems.length)
          }
        }
      }
    }
  }

  test("postUnpostedItems should process items in parallel when parallelism > 1") {
    // 並列度が3の場合、複数のアイテムが同時に処理される
    val parallelism = 3
    val testItems = (1 to 10).map(i => createTestItem(i.toString)).toList

    Ref.of[IO, Int](0).flatMap { activeCountRef =>
      Ref.of[IO, Int](0).flatMap { maxActiveCountRef =>
        Ref.of[IO, List[BoothItem]](List.empty).flatMap { postedItemsRef =>

          val mockRepository = new BoothItemRepository[IO] {
            override def findByBoothItemId(boothItemId: String): IO[Option[BoothItem]] =
              IO.pure(testItems.find(_.boothItemId == boothItemId))

            override def insert(item: BoothItem): IO[Long] =
              IO.pure(item.id.get)

            override def markAsPosted(id: Long, postedAt: LocalDateTime): IO[Unit] =
              IO.unit

            override def findUnpostedItems(limit: Int): IO[List[BoothItem]] =
              IO.pure(testItems.take(limit))
          }

          val mockMisskeyClient = new MisskeyClient[IO] {
            override def createNote(item: BoothItem): IO[Unit] = {
              for {
                active <- activeCountRef.updateAndGet(_ + 1)
                _ <- maxActiveCountRef.update(max => if (active > max) active else max)
                _ <- IO.sleep(100.millis)
                _ <- postedItemsRef.update(_ :+ item)
                _ <- activeCountRef.update(_ - 1)
              } yield ()
            }
          }

          val mockScraper = new BoothScraper[IO] {
            override def scrapeNewItems(): IO[List[BoothItem]] =
              IO.pure(List.empty)
          }

          val config = createTestConfig(parallelism, intervalSeconds = 0)
          val service = new BoothServiceImpl[IO](
            mockScraper,
            mockRepository,
            mockMisskeyClient,
            config,
            io
          )

          for {
            _ <- service.postUnpostedItems()
            maxActive <- maxActiveCountRef.get
            postedItems <- postedItemsRef.get
          } yield {
            // 並列度が3の場合、最大アクティブ数は3以下であるべき
            assert(maxActive <= parallelism)
            // 少なくとも2つ以上が並列処理されたはず
            assert(maxActive >= 2)
            // すべてのアイテムが投稿されているべき
            assertEquals(postedItems.length, testItems.length)
          }
        }
      }
    }
  }

  test("scrapeAndStoreItems should handle duplicate items correctly") {
    val testItems = List(createTestItem("1"), createTestItem("2"))
    val existingItem = createTestItem("1")

    Ref.of[IO, List[BoothItem]](List(existingItem)).flatMap { storedItemsRef =>

      val mockRepository = new BoothItemRepository[IO] {
        override def findByBoothItemId(boothItemId: String): IO[Option[BoothItem]] =
          storedItemsRef.get.map(_.find(_.boothItemId == boothItemId))

        override def insert(item: BoothItem): IO[Long] = {
          storedItemsRef.update(_ :+ item) *> IO.pure(item.id.get)
        }

        override def markAsPosted(id: Long, postedAt: LocalDateTime): IO[Unit] =
          IO.unit

        override def findUnpostedItems(limit: Int): IO[List[BoothItem]] =
          IO.pure(List.empty)
      }

      val mockScraper = new BoothScraper[IO] {
        override def scrapeNewItems(): IO[List[BoothItem]] =
          IO.pure(testItems)
      }

      val mockMisskeyClient = new MisskeyClient[IO] {
        override def createNote(item: BoothItem): IO[Unit] =
          IO.unit
      }

      val config = createTestConfig(parallelism = 1, intervalSeconds = 10)
      val service = new BoothServiceImpl[IO](
        mockScraper,
        mockRepository,
        mockMisskeyClient,
        config,
        io
      )

      for {
        _ <- service.scrapeAndStoreItems()
        storedItems <- storedItemsRef.get
      } yield {
        // 既存のアイテム1つ + 新しいアイテム1つ = 2つ
        assertEquals(storedItems.length, 2)
        // 重複したアイテムは追加されていないべき
        assertEquals(storedItems.count(_.boothItemId == "1"), 1)
        assertEquals(storedItems.count(_.boothItemId == "2"), 1)
      }
    }
  }
}
