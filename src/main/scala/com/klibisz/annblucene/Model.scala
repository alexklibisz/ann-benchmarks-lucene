package com.klibisz.annblucene

import scala.util.Try

/** Interface for a Lucene-backed ANN model. */
trait Model[IP, SP] {

  def createIndex(indexName: String, indexParams: IP): Try[Unit]

  def deleteIndex(indexName: String): Try[Unit]

  def indexVectors(indexName: String, vector: Seq[Array[Float]]): Try[Int]

  def closeIndex(indexName: String): Try[Unit]

  def search(indexName: String, k: Int, searchParams: SP, vector: Array[Float]): Try[Array[Int]]

}
