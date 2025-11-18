package io.github.kisaragieffective.bootie.client

import io.github.kisaragieffective.bootie.domain.BoothItem

trait MisskeyClient[F[_]] {
  def createNote(item: BoothItem): F[Unit]
}
