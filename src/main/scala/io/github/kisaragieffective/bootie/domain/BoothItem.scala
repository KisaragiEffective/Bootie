package io.github.kisaragieffective.bootie.domain

import java.time.LocalDateTime

case class BoothItem(
  id: Option[Long],
  boothItemId: String,
  name: String,
  url: String,
  thumbnailUrl: Option[String],
  shopName: Option[String],
  price: Option[String],
  tags: List[String],
  scrapedAt: LocalDateTime,
  postedToMisskey: Boolean,
  postedAt: Option[LocalDateTime],
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)
