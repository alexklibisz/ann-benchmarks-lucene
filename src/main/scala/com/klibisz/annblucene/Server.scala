package com.klibisz.annblucene

import akka.actor.ActorSystem
import akka.http.scaladsl.Http

object Server {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("annblucene")
    val server          = Http().newServerAt("localhost", 8080)
    val routes          = Routes(new LuceneHnswModel)
    server.bind(routes)
  }

}
