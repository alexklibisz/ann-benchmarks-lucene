package com.klibisz.annblucene

import com.klibisz.annblucene.LuceneHnswModel.SearchStrategy
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe._
import org.apache.commons.io.FileUtils
import org.apache.lucene.codecs.Codec
import org.apache.lucene.codecs.lucene90.Lucene90VectorReader
import org.apache.lucene.document.{FieldType, VectorField}
import org.apache.lucene.index._
import org.apache.lucene.store.MMapDirectory
import org.apache.lucene.util.hnsw.{HnswGraph, HnswGraphBuilder, NeighborQueue}

import java.nio.file.{Files, Path}
import java.util
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Try}

final class LuceneHnswModel extends Model[LuceneHnswModel.IndexParameters, LuceneHnswModel.SearchParameters] with StrictLogging {

  private val tmpDirectories: mutable.Map[String, Path]                                     = mutable.Map.empty
  private val buffers: mutable.Map[String, ArrayBuffer[Array[Float]]]                       = mutable.Map.empty
  private val fieldTypes: mutable.Map[String, FieldType]                                    = mutable.Map.empty
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
    if (buffers.contains(indexName)) {
      Failure(new IllegalStateException(s"Index $indexName already exists"))
    } else
      Try {
        logger.debug(s"Creating index $indexName.")
        buffers += (indexName -> ArrayBuffer.empty)
        fieldTypes += (indexName -> VectorField.createHnswType(
          indexParams.dims,
          strategyMap(indexParams.searchStrategy),
          indexParams.maxConnections,
          indexParams.beamWidth
        ))
      }
  }

  override def deleteIndex(indexName: String): Try[Unit] =
    Try {
      logger.debug(s"Deleting index $indexName.")
      tmpDirectories.get(indexName).map(_.toFile).foreach(FileUtils.deleteDirectory)
      tmpDirectories.remove(indexName)
    }

  override def indexVectors(
      indexName: String,
      vectors: Seq[Array[Float]]
  ): Try[Int] = {
    if (!buffers.contains(indexName)) {
      Failure(new IllegalStateException(s"Index $indexName does not exist"))
    } else if (vectorValues.contains(indexName)) {
      Failure(new IllegalStateException(s"Index $indexName is closed"))
    } else
      Try {
        buffers(indexName).addAll(vectors)
        logger.debug(s"Added ${vectors.length} new vectors to $indexName.")
        vectors.length
      }
  }

  override def closeIndex(indexName: String): Try[Unit] =
    Try {
      val tmpDir = Files.createTempDirectory("lucenehnsw-")
      FileUtils.forceDeleteOnExit(tmpDir.toFile)

      val indexDir    = new MMapDirectory(tmpDir)
      val writerCfg   = new IndexWriterConfig().setCodec(Codec.forName("Lucene90"))
      val indexWriter = new IndexWriter(indexDir, writerCfg)
      val ft          = fieldTypes(indexName)

      val docs = buffers(indexName).map(vec => util.Collections.singletonList(new VectorField(vecFieldName, vec, ft)))

      indexWriter.addDocuments(docs.asJava)
      indexWriter.flush()
      indexWriter.close()

      val indexReader = DirectoryReader.open(indexDir)
      val leaves      = indexReader.leaves().asScala
      assert(leaves.length == 1, s"Expected 1 leaf but got ${leaves.length}.")

      val cr: CodecReader              = leaves.head.reader().asInstanceOf[CodecReader]
      val vr: Lucene90VectorReader     = cr.getVectorReader.asInstanceOf[Lucene90VectorReader]
      val rv: RandomAccessVectorValues = cr.getVectorValues(vecFieldName).asInstanceOf[RandomAccessVectorValues]
      val gv: KnnGraphValues           = vr.getGraphValues(vecFieldName)

      tmpDirectories += (indexName -> tmpDir)
      vectorValues += (indexName   -> (rv, gv))
    }

  override def search(
      indexName: String,
      k: Int,
      searchParams: LuceneHnswModel.SearchParameters,
      vector: Array[Float]
  ): Try[Array[Int]] =
    Try {
      val (rv, gv)            = vectorValues(indexName)
      val nq: NeighborQueue   = HnswGraph.search(vector, k, searchParams.numSeed, rv, gv, rng)
      val results: Array[Int] = new Array(k)
      var i                   = results.length - 1
      while (i >= 0) {
        results.update(i, nq.pop())
        i -= 1
      }
      results
    }
}

object LuceneHnswModel {

  sealed trait SearchStrategy
  object SearchStrategy {
    case object EuclideanHnsw  extends SearchStrategy
    case object DotProductHnsw extends SearchStrategy
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
  }

  /** Parameters used to execute HNSW search via [[org.apache.lucene.util.hnsw.HnswGraph.search]] */
  case class SearchParameters(numSeed: Int)

  /** Codecs for parsing JSON versions of the above classes. */
  object CirceCodecs {
    implicit val searchStrategyDecoder: Decoder[SearchStrategy] =
      (c: HCursor) =>
        for {
          s <- c.as[String]
          r <- s.toLowerCase() match {
            case "euclideanhnsw"  => Right(SearchStrategy.EuclideanHnsw)
            case "dotproducthnsw" => Right(SearchStrategy.DotProductHnsw)
            case other            => Left(DecodingFailure(s"Invalid search strategy: ${other}", List.empty))
          }
        } yield r
    implicit val searchStrategyEncoder: Encoder[SearchStrategy] = {
      case SearchStrategy.EuclideanHnsw  => Json.fromString("euclideanhnsw")
      case SearchStrategy.DotProductHnsw => Json.fromString("dotproducthnsw")
    }

    implicit val indexParametersDecoder: Decoder[IndexParameters] = deriveDecoder
    implicit val indexParametersEncoder: Encoder[IndexParameters] = deriveEncoder

    implicit val searchParametersDecoder: Decoder[SearchParameters] = deriveDecoder
    implicit val searchParametersEncoder: Encoder[SearchParameters] = deriveEncoder
  }

}
