package com.github.kisaragieffective.bootie.config

import com.typesafe.config.ConfigFactory
import scala.jdk.CollectionConverters.*

case class DatabaseConfig(
  driver: String,
  url: String,
  user: String,
  password: String,
  poolInitialSize: Int,
  poolMaxSize: Int,
  poolConnectionTimeoutMillis: Long
)

case class MisskeyConfig(
  instanceUrl: String,
  token: String,
  visibility: String
)

case class PostingConfig(
  intervalSeconds: Int,
  parallelism: Int
)

case class ScrapingConfig(
  intervalSeconds: Int,
  userAgent: String,
  boothUrl: String,
  targetTag: String
)

case class AppConfig(
  database: DatabaseConfig,
  misskey: MisskeyConfig,
  posting: PostingConfig,
  scraping: ScrapingConfig
)

object AppConfig {
  def load(): AppConfig = {
    val config = ConfigFactory.load()

    val database = DatabaseConfig(
      driver = config.getString("db.default.driver"),
      url = config.getString("db.default.url"),
      user = config.getString("db.default.user"),
      password = config.getString("db.default.password"),
      poolInitialSize = config.getInt("db.default.poolInitialSize"),
      poolMaxSize = config.getInt("db.default.poolMaxSize"),
      poolConnectionTimeoutMillis = config.getLong("db.default.poolConnectionTimeoutMillis")
    )

    val misskey = MisskeyConfig(
      instanceUrl = config.getString("misskey.instance-url"),
      token = config.getString("misskey.token"),
      visibility = config.getString("misskey.visibility")
    )

    val posting = PostingConfig(
      intervalSeconds = config.getInt("posting.interval-seconds"),
      parallelism = config.getInt("posting.parallelism")
    )

    val scraping = ScrapingConfig(
      intervalSeconds = config.getInt("scraping.interval-seconds"),
      userAgent = config.getString("scraping.user-agent"),
      boothUrl = config.getString("scraping.booth-url"),
      targetTag = config.getString("scraping.target-tag")
    )

    AppConfig(database, misskey, posting, scraping)
  }
}
