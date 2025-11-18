package io.github.kisaragieffective.bootie.repository

import cats.effect.Sync
import cats.syntax.all.*
import io.github.kisaragieffective.bootie.domain.BoothItem
import scalikejdbc.*
import java.time.LocalDateTime

class BoothItemRepositoryImpl[F[_]: Sync] extends BoothItemRepository[F] {

  private def toBoothItem(rs: WrappedResultSet): BoothItem = {
    val tagsArray = rs.arrayOpt("tags").map { arr =>
      arr.getArray.asInstanceOf[Array[Object]].map(_.toString).toList
    }.getOrElse(List.empty)

    BoothItem(
      id = Some(rs.long("id")),
      boothItemId = rs.string("booth_item_id"),
      name = rs.string("name"),
      url = rs.string("url"),
      thumbnailUrl = rs.stringOpt("thumbnail_url"),
      shopName = rs.stringOpt("shop_name"),
      price = rs.stringOpt("price"),
      tags = tagsArray,
      scrapedAt = rs.localDateTime("scraped_at"),
      postedToMisskey = rs.boolean("posted_to_misskey"),
      postedAt = rs.localDateTimeOpt("posted_at"),
      createdAt = rs.localDateTime("created_at"),
      updatedAt = rs.localDateTime("updated_at")
    )
  }

  override def findByBoothItemId(boothItemId: String): F[Option[BoothItem]] =
    Sync[F].delay {
      DB.readOnly { implicit session =>
        sql"""
          SELECT * FROM booth_items
          WHERE booth_item_id = $boothItemId
        """.map(toBoothItem).single.apply()
      }
    }

  override def insert(item: BoothItem): F[Long] =
    Sync[F].delay {
      DB.localTx { implicit session =>
        val tagsArray = item.tags.toArray
        sql"""
          INSERT INTO booth_items (
            booth_item_id, name, url, thumbnail_url, shop_name, price, tags,
            scraped_at, posted_to_misskey, created_at, updated_at
          ) VALUES (
            ${item.boothItemId}, ${item.name}, ${item.url}, ${item.thumbnailUrl},
            ${item.shopName}, ${item.price}, $tagsArray,
            ${item.scrapedAt}, ${item.postedToMisskey}, ${item.createdAt}, ${item.updatedAt}
          )
          RETURNING id
        """.map(_.long("id")).single.apply().get
      }
    }

  override def markAsPosted(id: Long, postedAt: LocalDateTime): F[Unit] =
    Sync[F].delay {
      DB.localTx { implicit session =>
        sql"""
          UPDATE booth_items
          SET posted_to_misskey = true, posted_at = $postedAt, updated_at = CURRENT_TIMESTAMP
          WHERE id = $id
        """.update.apply()
        ()
      }
    }

  override def findUnpostedItems(limit: Int): F[List[BoothItem]] =
    Sync[F].delay {
      DB.readOnly { implicit session =>
        sql"""
          SELECT * FROM booth_items
          WHERE posted_to_misskey = false
          ORDER BY scraped_at ASC
          LIMIT $limit
        """.map(toBoothItem).list.apply()
      }
    }
}
