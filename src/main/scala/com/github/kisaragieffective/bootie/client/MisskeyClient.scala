package com.github.kisaragieffective.bootie.client

import com.github.kisaragieffective.bootie.domain.BoothItem

trait MisskeyClient[F[_]] {
  def createNote(item: BoothItem): F[Unit]
}
