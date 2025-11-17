package com.github.kisaragieffective.bootie.scraper

import cats.effect.Sync
import cats.syntax.all.*
import com.github.kisaragieffective.bootie.config.ScrapingConfig
import com.github.kisaragieffective.bootie.domain.BoothItem
import org.http4s.client.Client
import org.http4s.{Headers, Request, Uri}
import org.http4s.headers.`User-Agent`
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import scribe.Scribe
import scala.jdk.CollectionConverters.*
import java.time.LocalDateTime

trait BoothScraper[F[_]] {
  def scrapeNewItems(): F[List[BoothItem]]
}

class BoothScraperImpl[F[_]: Sync](
  client: Client[F],
  config: ScrapingConfig,
  logger: Scribe[F]
) extends BoothScraper[F] {

  override def scrapeNewItems(): F[List[BoothItem]] = {
    val url = s"${config.boothUrl}?sort=new"

    for {
      _ <- logger.info(s"Scraping BOOTH items from: $url")
      uri <- Sync[F].fromEither(Uri.fromString(url))
      request = Request[F](
        uri = uri
      ).putHeaders(`User-Agent`.parse(config.userAgent).getOrElse(`User-Agent`.unsafeFromString(config.userAgent)))

      html <- client.expect[String](request)
      items <- parseHtml(html)
      filtered = items.filter(_.tags.contains(config.targetTag))
      _ <- logger.info(s"Found ${items.length} items, ${filtered.length} with ${config.targetTag} tag")
    } yield filtered
  }

  private def parseHtml(html: String): F[List[BoothItem]] = Sync[F].delay {
    val doc = Jsoup.parse(html)
    val itemElements = doc.select(".item-card").asScala.toList

    itemElements.flatMap { element =>
      try {
        val boothItemId = extractItemId(element)
        val name = element.select(".item-card__title").text()
        val url = element.select("a.item-card__thumbnail-wrapper").attr("abs:href")
        val thumbnailUrl = element.select(".item-card__thumbnail img").attr("abs:src")
        val shopName = element.select(".item-card__shop-name").text()
        val price = element.select(".item-card__price").text()

        // タグの取得
        val tags = element.select(".item-card__tag").asScala.map(_.text().trim).toList

        val now = LocalDateTime.now()

        if (boothItemId.nonEmpty && name.nonEmpty) {
          Some(BoothItem(
            id = None,
            boothItemId = boothItemId,
            name = name,
            url = url,
            thumbnailUrl = Option(thumbnailUrl).filter(_.nonEmpty),
            shopName = Option(shopName).filter(_.nonEmpty),
            price = Option(price).filter(_.nonEmpty),
            tags = tags,
            scrapedAt = now,
            postedToMisskey = false,
            postedAt = None,
            createdAt = now,
            updatedAt = now
          ))
        } else {
          None
        }
      } catch {
        case e: Exception =>
          // ログを出力するが、パース失敗した個別アイテムは無視して続行
          None
      }
    }
  }

  private def extractItemId(element: Element): String = {
    // URLからアイテムIDを抽出
    val href = element.select("a.item-card__thumbnail-wrapper").attr("href")
    href.split("/").lastOption.getOrElse("")
  }
}
