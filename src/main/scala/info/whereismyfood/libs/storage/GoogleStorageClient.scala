package info.whereismyfood.libs.storage

import java.io.InputStream
import java.util.UUID

import com.google.cloud.storage.Storage.{BlobWriteOption, PredefinedAcl}
import com.google.cloud.storage.{Acl, BlobId, BlobInfo, StorageOptions}
import info.whereismyfood.aux.MyConfig
import org.slf4j.LoggerFactory


/**
  * Created by zakgoichman on 11/13/16.
  */
object GoogleStorageClient {
  val storage = StorageOptions.getDefaultInstance.getService
  val log = LoggerFactory.getLogger(this.getClass)

  def put(category: String, inputStream: InputStream): Option[String] = {
    try {
      val bucket = MyConfig.get("google.storage.bucket");
      val path = category + "/" + UUID.randomUUID
      val blobId = BlobId.of(bucket, path)
      val blobInfo = BlobInfo.newBuilder(blobId).build()
      storage.create(blobInfo, inputStream, BlobWriteOption.predefinedAcl(PredefinedAcl.PUBLIC_READ))
      Some(s"https://storage.cloud.google.com/$bucket/$path")
    } catch {
      case e: Exception =>
        log.error("GoogleStorageClient error", e)
        None
    }
  }
}
