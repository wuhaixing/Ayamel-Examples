package service

import java.io._
import play.api.Play
import play.api.Play.current
import concurrent.Future
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import java.util.Date
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import play.api.mvc.MultipartFormData.FilePart


trait UploadEngine {
  def upload(inputStream: InputStream, filename: String, contentLength: Long, contentType: String): Future[String]

  def upload(file: File, filename: String, contentType: String): Future[String] = {
    val contentLength = file.length()
    val inputStream = new FileInputStream(file)
    upload(inputStream, filename, contentLength, contentType)
  }
}

object FileUploader {

  private val uploadEngines = Map[String, UploadEngine](
    "s3" -> S3Uploader
  )

  def normalizeAndUploadFile(tempFile: FilePart[TemporaryFile]): Future[String] = {
    val file = tempFile.ref.file
    val filename = uniqueFilename(tempFile.filename)
    val contentType = tempFile.contentType.getOrElse("application/octet-stream")

    if (contentType.startsWith("image")) {

      // Do extra processing on images
      val normalizedImage = ImageTools.getNormalizedImageFromFile(file)
      uploadImage(normalizedImage, filename)
    } else
      uploadFile(file, filename, contentType)
  }

  def uniqueFilename(filename: String): String = {
    val extension = filename.substring(filename.lastIndexOf("."))
    val unique = HashTools.md5Hex(filename + new Date().getTime)
    unique + extension
  }

  def uploadFile(file: File, filename: String, contentType: String): Future[String] = {
    val engine = Play.configuration.getString("uploadEngine").get
    uploadEngines(engine).upload(file, filename, contentType)
  }

  def uploadFile(tempFile: FilePart[TemporaryFile]): Future[String] = {
    val file = tempFile.ref.file
    val filename = uniqueFilename(tempFile.filename)
    val contentType = tempFile.contentType.getOrElse("application/octet-stream")
    uploadFile(file, filename, contentType)
  }

  def uploadStream(inputStream: InputStream, filename: String, contentLength: Long, contentType: String): Future[String] = {
    val engine = Play.configuration.getString("uploadEngine").get
    uploadEngines(engine).upload(inputStream, filename, contentLength, contentType)
  }

  def uploadImage(image: BufferedImage, filename: String): Future[String] = {

    // Convert the BufferedImage to an input stream so we can upload it
    val outputStream = new ByteArrayOutputStream()

    // For now, always assume a jpeg
    ImageIO.write(image, "jpeg", outputStream)
    val inputStream = new ByteArrayInputStream(outputStream.toByteArray)
    uploadStream(inputStream, filename, outputStream.size(), "image/jpeg")
  }
}
