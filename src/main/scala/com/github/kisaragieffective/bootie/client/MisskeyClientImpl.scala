package com.github.kisaragieffective.bootie.client

import cats.effect.Sync
import cats.syntax.all.*
import com.github.kisaragieffective.bootie.config.MisskeyConfig
import com.github.kisaragieffective.bootie.domain.BoothItem
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import scribe.Scribe

class MisskeyClientImpl[F[_]: Sync](
  client: Client[F],
  config: MisskeyConfig,
  logger: Scribe[F]
) extends MisskeyClient[F] {

  case class CreateNoteRequest(
    i: String,          // token
    text: String,       // note content
    visibility: String  // visibility (public, home, followers, specified)
  )

  case class CreateNoteResponse(
    createdNote: JsonObject
  )

  given EntityDecoder[F, CreateNoteResponse] = jsonOf[F, CreateNoteResponse]
  given EntityEncoder[F, CreateNoteRequest] = jsonEncoderOf[F, CreateNoteRequest]

  override def createNote(item: BoothItem): F[Unit] = {
    val noteText = formatNoteText(item)
    val requestBody = CreateNoteRequest(
      i = config.token,
      text = noteText,
      visibility = config.visibility
    )

    val endpoint = s"${config.instanceUrl}/api/notes/create"

    for {
      _ <- logger.info(s"Posting to Misskey: ${item.name}")
      uri <- Sync[F].fromEither(Uri.fromString(endpoint))
      request = Request[F](
        method = Method.POST,
        uri = uri
      ).withEntity(requestBody)
        .withContentType(`Content-Type`(MediaType.application.json))

      response <- client.expect[CreateNoteResponse](request)
      _ <- logger.info(s"Successfully posted: ${item.name}")
    } yield ()
  }.handleErrorWith { error =>
    logger.error(s"Failed to post to Misskey: ${item.name}", error) *>
      Sync[F].raiseError(error)
  }

  private def formatNoteText(item: BoothItem): String = {
    val tagText = if (item.tags.nonEmpty) {
      item.tags.map(tag => s"#$tag").mkString(" ")
    } else {
      ""
    }

    val shopText = item.shopName.map(name => s"by $name").getOrElse("")
    val priceText = item.price.map(p => s"💰 $p").getOrElse("")

    s"""🆕 BOOTH新着商品
       |
       |${item.name}
       |$shopText
       |$priceText
       |
       |$tagText
       |
       |${item.url}
       |""".stripMargin.trim
  }
}
