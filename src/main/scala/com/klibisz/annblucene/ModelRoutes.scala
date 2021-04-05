package com.klibisz.annblucene

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

final class ModelRoutes[IP: Encoder: Decoder, SP: Encoder: Decoder](model: Model[IP, SP]) {

  import FailFastCirceSupport._
  case class SearchRequest(k: Int, params: SP, vector: Array[Float])
  object SearchRequest {
    implicit val decoder: Decoder[SearchRequest] = deriveDecoder[SearchRequest]
    implicit val encoder: Encoder[SearchRequest] = deriveEncoder[SearchRequest]
  }

  def route: Route =
    pathPrefix(Segment) { indexName: String =>
      (pathEnd & put) {
        entity(as[IP]) { ip =>
          complete(model.createIndex(indexName, ip).map(_ => StatusCodes.OK))
        }
      } ~ (pathEnd & post) {
        entity(as[Vector[Array[Float]]]) { vectors =>
          complete {
            model.indexVectors(indexName, vectors).map(_ => StatusCodes.Created)
          }
        }
      } ~ (path("close") & post) {
        complete(model.closeIndex(indexName).map(_ => StatusCodes.OK))
      } ~ (path("search") & post) {
        entity(as[SearchRequest]) { req =>
          complete(model.search(indexName, req.k, req.params, req.vector))
        }
      } ~ (pathEnd & delete) {
        complete(model.deleteIndex(indexName).map(_ => StatusCodes.OK))
      }
    }

}
