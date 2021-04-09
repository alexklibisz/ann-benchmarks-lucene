package com.klibisz.annblucene

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestDuration
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.DurationInt

class LuceneHnswModelSpec extends AnyFreeSpec with Matchers with GloveVectors with ScalatestRouteTest {

  "end-to-end with glove vectors" - {

    val model        = new LuceneHnswModel
    val indexName    = "test"
    val indexParams  = LuceneHnswModel.IndexParameters(50, LuceneHnswModel.SearchStrategy.DotProductHnsw, 32, 100)
    val searchParams = LuceneHnswModel.SearchParameters(100)

    import FailFastCirceSupport._
    import LuceneHnswModel.CirceCodecs._
    val modelRoutes = new ModelRoutes(model)
    val route       = modelRoutes.route

    implicit val timeout = RouteTestTimeout(5.seconds.dilated)

    "read vectors" in {
      getGloveVectors.length shouldBe 400000
    }

    "create index" in {
      Put(s"/$indexName", indexParams) ~> route ~> check {
        response.status shouldBe StatusCodes.OK
      }
    }

    "index vectors" in {
      getGloveVectors.map(_._2).grouped(1).foreach { batch =>
        Post(s"/$indexName", batch) ~> route ~> check {
          response.status shouldBe StatusCodes.Created
        }
      }
    }

    "close index" in {
      Post(s"/$indexName/close") ~> route ~> check {
        response.status shouldBe StatusCodes.OK
      }
    }

    "search" in {
      val queryVector = getGloveVectors.filter(_._1 == "president").head._2
      Post(s"/$indexName/search", modelRoutes.SearchRequest(5, searchParams, queryVector)) ~> route ~> check {
        response.status shouldBe StatusCodes.OK
        val results = entityAs[Vector[Int]]
        val words   = results.map(getGloveVectors(_)._1)
        words shouldBe Vector("president", "minister", "secretary", "chairman", "leader")
      }
    }

    "delete index" in {
      Delete(s"/$indexName") ~> route ~> check {
        response.status shouldBe StatusCodes.OK
      }
    }

  }

}
