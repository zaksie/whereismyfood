package info.whereismyfood.libs.storage

import java.io.InputStream
import java.util.UUID

import com.google.cloud.storage.Storage.{BlobWriteOption, PredefinedAcl}
import com.google.cloud.storage.{BlobId, BlobInfo, StorageOptions}
import info.whereismyfood.aux.MyConfig
import org.slf4j.LoggerFactory


/**
  * Created by zakgoichman on 11/13/16.
  */
object GoogleStorageClient {
  private val storage = StorageOptions.getDefaultInstance.getService
  private val log = LoggerFactory.getLogger(this.getClass)

  val Extension = ".*\\.([0-9a-z]+)$".r
  def put(fileName: String, category: String, inputStream: InputStream): Option[String] = {
    try {
      val Extension(ext) = fileName
      val bucket = MyConfig.get("google.storage.bucket");
      val path = s"$category/${UUID.randomUUID}.$ext"
      val blobId = BlobId.of(bucket, path)
      val blobInfo = BlobInfo.newBuilder(blobId).build()
      storage.create(blobInfo, inputStream, BlobWriteOption.predefinedAcl(PredefinedAcl.PUBLIC_READ))
      log.info(s"Finished uploading $path")
      Some(s"https://storage.googleapis.com/$bucket/$path")
    } catch {
      case e: Exception =>
        log.error("GoogleStorageClient error", e)
        None
    }
  }
}
