package service

import play.api.libs.json.{JsValue, JsArray, Json}
import concurrent.{ExecutionContext, Future}
import play.api.libs.MimeTypes
import ExecutionContext.Implicits.global
import dataAccess.ResourceController

/**
 * This builds upon the Resource API wrapper in ResourceController by providing functions which do some level of data
 * manipulation.
 */
object ResourceHelper {

  /**
   * Attempts to retrieve the mime type from the uri. Doesn't deal with the resource library, but this is used by other
   * functions in this object.
   * @param uri The uri the mime type will be for
   * @return The mime type
   */
  def getMimeFromUri(uri: String): String = {
    val extension = uri.substring(uri.lastIndexOf("."))
    val mime1 = MimeTypes.forExtension(extension)
    val mime2 = MimeTypes.forFileName(uri)
    if (mime1.isDefined)
      mime1.get
    else
    if (mime2.isDefined)
      mime2.get
    else
      "application/octet-stream"
  }

  /**
   * Creates a new resource and sets a download uri
   * @param title The title of the new resource
   * @param uri The download uri of the resource
   * @param description A description of the resource
   * @param resourceType The type of resource
   * @param mime The mime type of the file at the uri
   * @return The id of the resource in a future
   */
  def createResourceWithUri(title: String, description: String, keywords: String, categories: List[String],
                            resourceType: String, uri: String, mime: String): Future[JsValue] = {

    // Create the resource
    ResourceController.createResource(title, description, keywords, categories, resourceType).flatMap(json => {
      val contentUploadUrl = (json \ "content_upload_url").as[String]

      // Add information about the file
      val fileInfo = Json.obj(
        "downloadUri" -> uri,
        "mime" -> mime,
        "representation" -> "original",
        "quality" -> "1"
      )
      val remoteFiles = Json.obj("remoteFiles" -> Json.arr(fileInfo))

      // Save this info and return the updated resource
      ResourceController.setRemoteFiles(contentUploadUrl, remoteFiles).map(_ \ "resource")
    })
  }

  /**
   * Adds a remote file to a resource by getting an upload url and appending the file to the existing list of files.
   * @param id The id of the resource
   * @param uri The url of the new file
   * @param mime The mime type of the new file
   * @param representation How the file represents the resource
   */
  def addRemoteFile(id: String, uri: String, mime: Option[String] = None, representation: String = "original"): Future[JsValue] = {
    // Get the resource
    ResourceController.getResource(id).flatMap(resourceResponse => {
      val resource = resourceResponse \ "resource"

      // Set the new file information
      val mimeType = mime.getOrElse(getMimeFromUri(uri))
      val fileInfo = Json.obj(
        "downloadUri" -> uri,
        "mime" -> mimeType,
        "representation" -> representation,
        "quality" -> "1"
      )
      val files = (resource \ "content" \ "files").as[JsArray].value.toList
      val newFiles = JsArray(files ::: List(fileInfo))

      // Get an upload url
      ResourceController.requestUploadUrl(id).flatMap(uploadUrlResponse => {
        val uploadUrl = (uploadUrlResponse \ "content_upload_url").as[String]

        // Set the new files
        val obj = Json.obj("remoteFiles" -> newFiles)
        ResourceController.setRemoteFiles(uploadUrl, obj).map(_ \ "resource")
      })
    })
  }

  /**
   * Adds a thumbnail to the resource
   * @param id The id of the resource
   * @param thumbnailUri The url of the thumbnail
   * @param mime The mime type of the thumbnail
   */
  def addThumbnail(id: String, thumbnailUri: String, mime: Option[String] = None): Future[JsValue] =
    addRemoteFile(id, thumbnailUri, mime, "summary")

}
