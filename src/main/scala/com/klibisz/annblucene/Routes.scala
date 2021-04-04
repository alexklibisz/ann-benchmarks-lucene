package com.klibisz.annblucene

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object Routes {

  def apply[IP: Decoder, SP: Decoder](model: Model[IP, SP]): Route = {
    import FailFastCirceSupport._

    case class SearchRequest(k: Int, params: SP, vector: Array[Float])
    object SearchRequest {
      implicit val decoder: Decoder[SearchRequest] = deriveDecoder[SearchRequest]
    }

    pathPrefix(Segment) { indexName: String =>
      (pathEnd & put) {
        entity(as[IP]) { ip =>
          complete(model.createIndex(indexName, ip).map(_ => StatusCodes.OK))
        }
      } ~ (pathEnd & post) {
        entity(as[Vector[Array[Float]]]) { vectors =>
          complete(model.indexVectors(indexName, vectors).map(_ => StatusCodes.Created))
        }
      } ~ (path("close") & post) {
        complete(model.closeIndex(indexName).map(_ => StatusCodes.OK))
      } ~ (path("_search") & post) {
        entity(as[SearchRequest]) { req =>
          complete(model.search(indexName, req.k, req.params, req.vector))
        }
      } ~ (pathEnd & delete) {
        complete(model.deleteIndex(indexName))
      }
    }
  }

}
