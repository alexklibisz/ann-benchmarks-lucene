package com.klibisz.annblucene

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging

object Server extends StrictLogging {

  def main(args: Array[String]): Unit = {
    val routes: Route = if (args.length != 1) {
      throw new IllegalArgumentException(s"Usage: <program> <model type (elastiknn or lucenehnsw)>")
    } else if (args.head == "lucenehnsw") {
      import LuceneHnswModel.CirceCodecs._
      new ModelRoutes(new LuceneHnswModel).route
    } else ???

    implicit val system = ActorSystem("annblucene")
    val server          = Http().newServerAt("localhost", 8080)
    logger.info(s"Starting ${args.head} model on localhost:8080")
    server.bind(routes)
  }

}
