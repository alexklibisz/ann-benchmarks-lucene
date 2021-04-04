package com.klibisz.annblucene

import java.util.zip.GZIPInputStream
import scala.io.Source

trait GloveVectors {

  private[this] lazy val gloveVectors = {
    val resourceStream = getClass.getResourceAsStream("glove.6B.50d.txt.gz")
    val gzipStream     = new GZIPInputStream(resourceStream)
    val source         = Source.fromInputStream(gzipStream)
    try source
      .getLines()
      .map { s =>
        val tokens = s.split(" ")
        val word   = tokens.head
        val vector = tokens.tail.map(_.toFloat)
        (word, vector)
      }
      .toVector
    finally resourceStream.close()
  }

  def getGloveVectors: Vector[(String, Array[Float])] = gloveVectors

}
