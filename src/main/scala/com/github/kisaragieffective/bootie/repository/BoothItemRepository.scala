package com.github.kisaragieffective.bootie.repository

import com.github.kisaragieffective.bootie.domain.BoothItem
import java.time.LocalDateTime

trait BoothItemRepository[F[_]] {
  def findByBoothItemId(boothItemId: String): F[Option[BoothItem]]
  def insert(item: BoothItem): F[Long]
  def markAsPosted(id: Long, postedAt: LocalDateTime): F[Unit]
  def findUnpostedItems(limit: Int): F[List[BoothItem]]
}
