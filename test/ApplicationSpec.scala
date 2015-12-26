import java.io.{ByteArrayInputStream, ByteArrayOutputStream, File}
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.nio.file.{StandardOpenOption, Files, StandardCopyOption}
import java.security.{DigestOutputStream, MessageDigest}

import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.junit.runner._
import org.specs2.mutable.Specification
import org.specs2.runner._
import play.api.http.{ContentTypeOf, Writeable}
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest, WithApplication, _}

import scala.concurrent.Future
import scala.language.implicitConversions

import scala.concurrent.ExecutionContext.Implicits.global

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  "Application" should {
    /**
     * An ugly way to create multipartFormData using Apache HTTP MultipartEntityBuilder.
     *
     * The reason for def instead of a implicit Writeable instance is because we need multipart boundary to pass into
     * ContentTypeOf constructor and creating a Writeable requires a ContentTypeOf instance.
     *
     * @param request fake request on which the Writeable instance will be created
     */
    def writeableOf_multipartFormData(request: FakeRequest[MultipartFormData[TemporaryFile]]) = {
      val builder = MultipartEntityBuilder.create()
      request.body.dataParts.foreach { case (k, vs) => builder.addTextBody(k, vs.mkString)}

      // ContentType part is necessary here because it gets parsed as a DataPart otherwise.
      request.body.files.foreach { case f => builder.addBinaryBody(f.filename, f.ref.file, ContentType.create(f.contentType.get, null: Charset), f.filename)}

      val entity = builder.build()

      implicit val contentTypeOf_MultipartFormData: ContentTypeOf[MultipartFormData[TemporaryFile]] = ContentTypeOf[MultipartFormData[TemporaryFile]](Some(entity.getContentType.getValue))

      Writeable[MultipartFormData[TemporaryFile]] {
        (mfd: MultipartFormData[TemporaryFile]) =>
          val outputStream = new ByteArrayOutputStream()

          entity.writeTo(outputStream)

          outputStream.toByteArray
      }
    }

    /**
     * Creates a fake request to given URL and sends it.
     *
     * @param url url of the controller to send the request
     * @param parameters some parameters that you would like to pass as a data part
     */
    def sendUploadRequest(url: String, file: File, mimeType: String, parameters: Map[String, String]): Future[Result] = {
      // Your original file will be deleted after the controller executes if you don't do the copy part below
      val tempFile = TemporaryFile("TEST_REMOVE_")
      Files.copy(file.toPath, tempFile.file.toPath, StandardCopyOption.REPLACE_EXISTING)

      val part = FilePart[TemporaryFile](key = tempFile.file.getName, filename = tempFile.file.getName, contentType = Some(mimeType), ref = tempFile)
      val formData = MultipartFormData(dataParts = parameters.map { case (k, v) => k -> Seq(v)}, files = Seq(part), badParts = Nil, missingFileParts = Nil)
      val request = FakeRequest("POST", url, FakeHeaders(), formData)

      implicit val writeable = writeableOf_multipartFormData(request)

      route(request)(writeable).get
    }


    "upload a test file successfully" in new WithApplication {
      val tempFile = TemporaryFile("TEST_")
      val fileRef = tempFile.file

      Files.write(fileRef.toPath, """{"hello":"world"}""".getBytes, StandardOpenOption.WRITE)

      val parameters = Map("foo" -> "bar")

      val future = sendUploadRequest(controllers.routes.Application.uploadToBytes().url, fileRef, "application/json", parameters)

      status(future) ==== OK
      contentAsString(future) must beMatching("File TEST_REMOVE_[0-9]+ successfully streamed.")
    }

    "upload a test file and get its MD5 hash" in new WithApplication {
      val tempFile = TemporaryFile("TEST_")
      val fileRef = tempFile.file

      Files.write(fileRef.toPath, """{"hello":"world"}""".getBytes, StandardOpenOption.WRITE)

      val parameters = Map("foo" -> "bar")

      val future = sendUploadRequest(controllers.routes.Application.uploadToHash().url, fileRef, "application/json", parameters)

      status(future) ==== OK
      contentAsString(future) must beMatching("File TEST_REMOVE_[0-9]+ successfully streamed.  Hash: 334645069913620745014919525874457631478")
    }

//    "md5 hash of output stream" in {
//
//
////      val md5Digest = MessageDigest.getInstance("MD5")
////      val outStream = Channels.newChannel(
////        new DigestOutputStream(new ByteArrayOutputStream(), md5Digest))
////
////      //val in = Channels.newChannel(new ByteArrayInputStream("hello world".getBytes()))
////      val buffer = ByteBuffer.allocate(1024 * 1024)
////      in.read(buffer)
////      out.write(buffer)
////
////      val md5Actual = new BigInteger(1, md5Digest.digest())
////      println(md5Actual)
//
//
//      val in = Channels.newChannel(new ByteArrayInputStream("hello world".getBytes))
//      val md5Digest = MessageDigest.getInstance("MD5")
//      val out = Channels.newChannel(
//        new DigestOutputStream(new ByteArrayOutputStream(), md5Digest))  // new
//      val buffer: ByteBuffer = ByteBuffer.allocate(1024 * 1024) // 1 MB
//
//      while (in.read(buffer) != -1) {
//        buffer.flip()
//        //md5Digest.update(buffer.asReadOnlyBuffer());  // old
//        out.write(buffer)
//        buffer.clear()
//      }
//
//      val md5Actual = new BigInteger(1, md5Digest.digest())
//
//      println(s"md5: $md5Actual")
//      success
//    }
//
//    "md5 hash of output stream 2" in {
//      val in = Channels.newChannel(new ByteArrayInputStream("hello world".getBytes))
//      val md5Digest = MessageDigest.getInstance("MD5")
//      val out = Channels.newChannel(
//        new DigestOutputStream(new ByteArrayOutputStream(), md5Digest))  // new
//      val buffer: ByteBuffer = ByteBuffer.allocate(1024 * 1024) // 1 MB
//
//      while (in.read(buffer) != -1) {
//        buffer.flip()
//        //md5Digest.update(buffer.asReadOnlyBuffer());  // old
//        out.write(buffer)
//        buffer.clear()
//      }
//
//      val md5Actual = new BigInteger(1, md5Digest.digest())
//
//      println(s"md5: $md5Actual")
//      success
//    }
//

  }
}
