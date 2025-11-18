package io.github.kisaragieffective.bootie.db

trait DatabaseInitializer[F[_]] {
  def initialize(): F[Unit]
}
