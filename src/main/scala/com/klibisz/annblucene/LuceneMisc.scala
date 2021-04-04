package com.klibisz.annblucene

import org.apache.lucene.codecs.Codec
import org.apache.lucene.codecs.lucene90.Lucene90VectorReader
import org.apache.lucene.document.{Document, StoredField, VectorField}
import org.apache.lucene.index._
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.util.hnsw.{HnswGraph, HnswGraphBuilder, NeighborQueue}

import java.nio.file.Files
import java.util.Random
import scala.jdk.CollectionConverters._

object LuceneMisc extends App {

  // TestHnsw in Lucene is quite helpful.

  val tmpDir         = Files.createTempDirectory(null)
  val indexDir       = new MMapDirectory(tmpDir)
  val indexWriterCfg = new IndexWriterConfig().setCodec(Codec.forName("Lucene90"))
  val indexWriter    = new IndexWriter(indexDir, indexWriterCfg)

  val dims  = 3
  val field = "vec"

  val fieldType = VectorField.createHnswType(
    dims,
    VectorValues.SearchStrategy.EUCLIDEAN_HNSW,
    HnswGraphBuilder.DEFAULT_MAX_CONN,
    HnswGraphBuilder.DEFAULT_BEAM_WIDTH
  )

  val vec = Array(0.1f, 0.2f, 0.3f)
  val id  = "test1"

  val doc = new Document
  doc.add(new VectorField(field, vec, fieldType))
  doc.add(new StoredField("id", id))

  indexWriter.addDocument(doc)
  indexWriter.close()

  val indexReader = DirectoryReader.open(indexDir)

//  for (ctx <- indexReader.leaves().asScala) {
//    val vecValues = ctx.reader().getVectorValues(field)
//    vecValues.nextDoc()
//    println(vecValues.docID())
//    println(vecValues.dimension())
//    println(vecValues.vectorValue().toVector)
//
//    ctx.reader match {
//      case cr: CodecReader =>
//        cr.getVectorReader match {
//          case l9vr: Lucene90VectorReader =>
//            val graphValues = l9vr.getGraphValues(field)
//            println(graphValues)
//        }
//    }
//  }

  for (ctx <- indexReader.leaves().asScala) {
    ctx.reader match {
      case cr: CodecReader =>
        cr.getVectorReader match {
          case l9vr: Lucene90VectorReader =>
            val graphValues = l9vr.getGraphValues(field)
            cr.getVectorValues(field) match {
              case r: RandomAccessVectorValues =>
                val query = Array(0.11f, 0.2f, 0.3f)
                val nn: NeighborQueue =
                  HnswGraph.search(query, 10, 100, r, graphValues, new Random())
                for (_ <- 0 until nn.size()) {
                  println(nn.pop())
                }
            }
        }
    }
  }
}
