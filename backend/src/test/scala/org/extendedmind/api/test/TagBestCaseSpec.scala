package org.extendedmind.api.test

import java.io.PrintWriter
import java.util.UUID
import org.extendedmind._
import org.extendedmind.bl._
import org.extendedmind.db._
import org.extendedmind.domain._
import org.extendedmind.security._
import org.extendedmind.email._
import org.extendedmind.test._
import org.extendedmind.test.TestGraphDatabase._
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.mockito.Matchers.{ eq => mockEq }
import scaldi.Module
import spray.http.BasicHttpCredentials
import spray.http.HttpHeaders.Authorization
import org.zeroturnaround.zip.ZipUtil
import java.io.File
import org.zeroturnaround.zip.FileUtil
import org.apache.commons.io.FileUtils
import org.extendedmind.api.JsonImplicits._
import spray.httpx.SprayJsonSupport._
import spray.httpx.marshalling._
import spray.json.DefaultJsonProtocol._
import scala.concurrent.Future

/**
 * Best case test for tags. Also generates .json files.
 */
class TagBestCaseSpec extends ServiceSpecBase {

  val mockMailgunClient = mock[MailgunClient]

  object TestDataGeneratorConfiguration extends Module {
    bind[GraphDatabase] to db
    bind[MailgunClient] to mockMailgunClient
  }

  override def configurations = TestDataGeneratorConfiguration :: new Configuration(settings, actorRefFactory)
 
  before {
    db.insertTestData()
  }

  after {
    cleanDb(db.ds.gds)
    reset(mockMailgunClient)
  }

  describe("In the best case, TagService") {
    it("should successfully put new tag on PUT to /[userUUID]/tag, "
      + "update it with PUT to /[userUUID]/tag/[tagUUID] "
      + "and get it back with GET to /[userUUID]/tag/[tagUUID]") {
      val authenticateResponse = emailPasswordAuthenticate(TIMO_EMAIL, TIMO_PASSWORD)
      val newTag = Tag("home", None, CONTEXT, None, None)
      Put("/" + authenticateResponse.userUUID + "/tag",
        marshal(newTag).right.get) ~> addHeader("Content-Type", "application/json") ~> addCredentials(BasicHttpCredentials("token", authenticateResponse.token.get)) ~> route ~> check {
          val putTagResponse = entityAs[SetResult]
          writeJsonOutput("putTagResponse", entityAs[String])
          putTagResponse.modified should not be None
          putTagResponse.uuid should not be None
          val updatedTag = newTag.copy(description = Some("my home"))
          Put("/" + authenticateResponse.userUUID + "/tag/" + putTagResponse.uuid.get,
            marshal(updatedTag).right.get) ~> addHeader("Content-Type", "application/json") ~> addCredentials(BasicHttpCredentials("token", authenticateResponse.token.get)) ~> route ~> check {
              val putExistingTagResponse = entityAs[String]
              writeJsonOutput("putExistingTagResponse", putExistingTagResponse)
              putExistingTagResponse should include("modified")
              putExistingTagResponse should not include ("uuid")
              Get("/" + authenticateResponse.userUUID + "/tag/" + putTagResponse.uuid.get) ~> addCredentials(BasicHttpCredentials("token", authenticateResponse.token.get)) ~> route ~> check {
                val tagResponse = entityAs[Tag]
                writeJsonOutput("tagResponse", entityAs[String])
                tagResponse.description.get should be("my home")
                // Add the tag to a Note
                val newNote = Note("bike details", None, Some("model: 12345"), None,
                  Some(ExtendedItemRelationships(None, None, Some(List(putTagResponse.uuid.get)))))
                val putNoteResponse = putNewNote(newNote, authenticateResponse)
                val noteWithTag = getNote(putNoteResponse.uuid.get, authenticateResponse)
                noteWithTag.relationships.get.tags.get should be(List(putTagResponse.uuid.get))
              }
            }
        }
    }
  }

}
