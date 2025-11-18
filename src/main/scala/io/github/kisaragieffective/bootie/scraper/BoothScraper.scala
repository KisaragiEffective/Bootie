package io.github.kisaragieffective.bootie.scraper

import io.github.kisaragieffective.bootie.domain.BoothItem

trait BoothScraper[F[_]] {
  def scrapeNewItems(): F[List[BoothItem]]
}
