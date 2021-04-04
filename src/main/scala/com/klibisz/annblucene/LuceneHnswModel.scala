package com.klibisz.annblucene

import com.klibisz.annblucene.LuceneHnswModel.{IndexParameters, SearchStrategy}
import io.circe.Decoder.Result
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import org.apache.lucene.codecs.Codec
import org.apache.lucene.codecs.lucene90.Lucene90VectorReader
import org.apache.lucene.document.{FieldType, VectorField}
import org.apache.lucene.index._
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.util.hnsw.{HnswGraph, HnswGraphBuilder, NeighborQueue}

import java.nio.file.{Files, Path}
import java.util.Collections
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.{Failure, Try}

final class LuceneHnswModel extends Model[LuceneHnswModel.IndexParameters, LuceneHnswModel.SearchParameters] {

  private val tmpDirectories: mutable.Map[String, Path]                                     = mutable.Map.empty
  private val indexWriters: mutable.Map[String, IndexWriter]                                = mutable.Map.empty
  private val fieldTypes: mutable.Map[String, FieldType]                                    = mutable.Map.empty
  private val indexReaders: mutable.Map[String, IndexReader]                                = mutable.Map.empty
  private val vectorValues: mutable.Map[String, (RandomAccessVectorValues, KnnGraphValues)] = mutable.Map.empty

  private val vecFieldName = "vec"

  private val rng = new java.util.Random(0)

  private val strategyMap: Map[SearchStrategy, VectorValues.SearchStrategy] = Map(
    SearchStrategy.EuclideanHnsw  -> VectorValues.SearchStrategy.EUCLIDEAN_HNSW,
    SearchStrategy.DotProductHnsw -> VectorValues.SearchStrategy.DOT_PRODUCT_HNSW
  )

  override def createIndex(
      indexName: String,
      indexParams: LuceneHnswModel.IndexParameters
  ): Try[Unit] = {
    if (tmpDirectories.contains(indexName)) {
      Failure(new IllegalStateException(s"Index [$indexName] already exists"))
    } else
      Try {
        val tmpDir            = Files.createTempDirectory(null)
        val indexDir          = new MMapDirectory(tmpDir)
        val indexWriterConfig = new IndexWriterConfig().setCodec(Codec.forName("Lucene90"))
        val indexWriter       = new IndexWriter(indexDir, indexWriterConfig)
        val fieldType: FieldType = VectorField.createHnswType(
          indexParams.dims,
          strategyMap(indexParams.searchStrategy),
          indexParams.maxConnections,
          indexParams.beamWidth
        )
        fieldTypes += (indexName     -> fieldType)
        tmpDirectories += (indexName -> tmpDir)
        indexWriters += (indexName   -> indexWriter)
      }
  }

  override def deleteIndex(indexName: String): Try[Unit] =
    Try {
      indexWriters.remove(indexName)
      indexReaders.remove(indexName)
      tmpDirectories.get(indexName).foreach(Files.deleteIfExists)
      tmpDirectories.remove(indexName)
    }

  override def indexVectors(
      indexName: String,
      vectors: Seq[Array[Float]]
  ): Try[Int] =
    if (!tmpDirectories.contains(indexName)) {
      Failure(new IllegalStateException(s"Index [$indexName] does not exist"))
    } else if (vectorValues.contains(indexName)) {
      Failure(new IllegalStateException(s"Index [$indexName] is closed"))
    } else
      Try {
        val writer    = indexWriters(indexName)
        val fieldType = fieldTypes(indexName)
        val documents = vectors.map(vec => Collections.singletonList(new VectorField(vecFieldName, vec, fieldType)))
        writer.addDocuments(documents.asJavaCollection)
        documents.length
      }

  override def closeIndex(indexName: String): Try[Unit] =
    Try {
      val w = indexWriters(indexName)
      w.forceMerge(1, true)
      w.close()

      val indexReader = DirectoryReader.open(new MMapDirectory(tmpDirectories(indexName)))
      val leaves      = indexReader.leaves().asScala
      assert(leaves.length == 1, s"Expected 1 leaf but got ${leaves.length}.")

      val cr: CodecReader              = leaves.head.reader().asInstanceOf[CodecReader]
      val vr: Lucene90VectorReader     = cr.getVectorReader.asInstanceOf[Lucene90VectorReader]
      val rv: RandomAccessVectorValues = cr.getVectorValues(vecFieldName).asInstanceOf[RandomAccessVectorValues]
      val gv: KnnGraphValues           = vr.getGraphValues(vecFieldName)
      vectorValues += (indexName -> (rv, gv))
    }

  override def search(
      indexName: String,
      k: Int,
      searchParams: LuceneHnswModel.SearchParameters,
      vector: Array[Float]
  ): Try[Vector[(Int, Float)]] =
    Try {
      val (rv, gv)                     = vectorValues(indexName)
      val nq: NeighborQueue            = HnswGraph.search(vector, k, searchParams.numSeed, rv, gv, rng)
      val results: Array[(Int, Float)] = new Array(k)

      var i = results.length - 1
      while (i >= 0) {
        results.update(i, (nq.topNode(), nq.topScore()))
        nq.pop()
        i -= 1
      }
      results.toVector
    }
}

object LuceneHnswModel {

  sealed trait SearchStrategy
  object SearchStrategy {
    case object EuclideanHnsw  extends SearchStrategy
    case object DotProductHnsw extends SearchStrategy

    implicit val decoder: Decoder[SearchStrategy] =
      (c: HCursor) =>
        for {
          s <- c.as[String]
          r <- s.toLowerCase() match {
            case "euclideanhnsw"  => Right(EuclideanHnsw)
            case "dotproducthnsw" => Right(DotProductHnsw)
            case other            => Left(DecodingFailure(s"Invalid search strategy: ${other}", List.empty))
          }
        } yield r

  }

  /** Parameters used to construct HNSW model via [[org.apache.lucene.document.VectorField.createHnswType]] */
  case class IndexParameters(
      dims: Int,
      searchStrategy: SearchStrategy,
      maxConnections: Int,
      beamWidth: Int
  )
  object IndexParameters {
    def apply(dims: Int, searchStrategy: SearchStrategy) =
      new IndexParameters(dims, searchStrategy, HnswGraphBuilder.DEFAULT_MAX_CONN, HnswGraphBuilder.DEFAULT_BEAM_WIDTH)

    implicit val decoder: Decoder[IndexParameters] = deriveDecoder[IndexParameters]
  }

  /** Parameters used to execute HNSW search via [[org.apache.lucene.util.hnsw.HnswGraph.search]] */
  case class SearchParameters(numSeed: Int)
  object SearchParameters {
    implicit val decoder: Decoder[SearchParameters] = deriveDecoder[SearchParameters]
  }
}
