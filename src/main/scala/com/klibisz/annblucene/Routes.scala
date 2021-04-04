package com.klibisz.annblucene

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object Routes {

  def apply[IP, SP](model: Model[IP, SP]): Route = {
    path(Segment) { index =>
      put {
        complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"Put ${index}")))
      } ~ post {
        complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"Post ${index}")))
      }
    } ~ path(Segment / "_search") { index =>
      post {
        complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(s"POST ${index}/_search")))
      }
    }
  }

}
