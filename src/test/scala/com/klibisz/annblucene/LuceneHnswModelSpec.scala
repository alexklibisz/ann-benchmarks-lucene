package com.klibisz.annblucene

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration
import scala.util.Success

class LuceneHnswModelSpec extends AnyFreeSpec with Matchers with GloveVectors with Timing {

  "sanity check on glove vectors" - {

    val model        = new LuceneHnswModel
    val indexName    = "test"
    val indexParams  = LuceneHnswModel.IndexParameters(50, LuceneHnswModel.SearchStrategy.DotProductHnsw, 32, 100)
    val searchParams = LuceneHnswModel.SearchParameters(100)

    "read vectors" in {
      getGloveVectors.length shouldBe 400000
    }

    "end-to-end" in {
      timeInfo("create index") {
        model.createIndex(indexName, indexParams) shouldBe Success(())
      }

      timeInfo("index vectors") {
        model.indexVectors(indexName, getGloveVectors.map(_._2)) shouldBe Success(getGloveVectors.length)
      }

      timeInfo("close index") {
        model.closeIndex(indexName) shouldBe Success(())
      }

      timeInfo("search") {
        val queryVector  = getGloveVectors.filter(_._1 == "president").head._2
        val indexResults = model.search(indexName, 5, searchParams, queryVector)
        val words        = indexResults.map(_.map(_._1).map(getGloveVectors(_)._1))
        words shouldBe Success(Vector("president", "minister", "secretary", "chairman", "leader"))
      }
    }

  }

}
