package com.github.kisaragieffective.bootie.scraper

import com.github.kisaragieffective.bootie.domain.BoothItem

trait BoothScraper[F[_]] {
  def scrapeNewItems(): F[List[BoothItem]]
}
