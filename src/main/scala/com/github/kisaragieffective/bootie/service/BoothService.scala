package com.github.kisaragieffective.bootie.service

import fs2.Stream

trait BoothService[F[_]] {
  def scrapeAndStoreItems(): F[Unit]
  def postUnpostedItems(): F[Unit]
  def runPeriodicScraping(): Stream[F, Unit]
  def runPeriodicPosting(): Stream[F, Unit]
}
