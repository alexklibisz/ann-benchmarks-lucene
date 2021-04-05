package com.klibisz.annblucene

import com.klibisz.annblucene.LuceneHnswModel.SearchStrategy
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import org.apache.commons.io.FileUtils
import org.apache.lucene.codecs.Codec
import org.apache.lucene.codecs.lucene90.Lucene90VectorReader
import org.apache.lucene.document.{FieldType, VectorField}
import org.apache.lucene.index._
import org.apache.lucene.store.{Directory, MMapDirectory}
import org.apache.lucene.util.hnsw.{HnswGraph, HnswGraphBuilder, NeighborQueue}

import java.nio.file.{Files, Path}
import java.util.Collections
import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.concurrent.duration.DurationLong
import scala.util.{Failure, Try}

final class LuceneHnswModel extends Model[LuceneHnswModel.IndexParameters, LuceneHnswModel.SearchParameters] with StrictLogging {

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
      Failure(new IllegalStateException(s"Index $indexName already exists"))
    } else
      Try {
        val tmpDir = Files.createTempDirectory("lucenehnsw-")
        FileUtils.forceDeleteOnExit(tmpDir.toFile)

        logger.info(s"Creating new index $indexName with parameters $indexParams in directory $tmpDir")

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
      tmpDirectories.get(indexName).map(_.toFile).foreach(FileUtils.deleteDirectory)
      tmpDirectories.remove(indexName)
    }

  override def indexVectors(
      indexName: String,
      vectors: Seq[Array[Float]]
  ): Try[Int] = {
    // Note: The call to forceMerge in closeIndex seems to be way faster if you use larger batches when indexing.
    if (!tmpDirectories.contains(indexName)) {
      Failure(new IllegalStateException(s"Index $indexName does not exist"))
    } else if (vectorValues.contains(indexName)) {
      Failure(new IllegalStateException(s"Index $indexName is closed"))
    } else
      Try {
        val t0        = System.nanoTime()
        val writer    = indexWriters(indexName)
        val fieldType = fieldTypes(indexName)
        val documents = vectors.map(vec => Collections.singletonList(new VectorField(vecFieldName, vec, fieldType)))
        writer.addDocuments(documents.asJavaCollection)
        writer.flush()
        val dur = System.nanoTime() - t0
        logger.info(s"Indexed ${vectors.length} vectors to $indexName in ${dur.nanos.toMillis} ms")
        documents.length
      }
  }

  override def closeIndex(indexName: String): Try[Unit] =
    Try {
      val t0 = System.nanoTime()
      val w  = indexWriters(indexName)
      w.forceMerge(1)
      w.close()
      val dur = System.nanoTime() - t0
      logger.info(s"Closed and merged index $indexName in ${dur.nanos.toMillis} ms")

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
