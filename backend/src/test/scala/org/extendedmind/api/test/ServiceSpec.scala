package org.extendedmind.api.test
import org.extendedmind.bl.UserActions
import scaldi.Module
import org.mockito.Mockito._
import org.extendedmind.domain.User
import org.extendedmind.test.SpecBase
import java.io.PrintWriter

class ServiceSpec extends SpecBase{

  // Mock out all action classes to test only the Service class
  val mockUserActions = mock[UserActions]
  object ServiceTestConfiguration extends Module{
    bind [UserActions] to mockUserActions
  }
  def configurations = ServiceTestConfiguration 
  
  // Reset mocks after each test to be able to use verify after each test
  after{
    reset(mockUserActions)
  }

  describe("Extended Mind Service"){
    it("should return a list of available commands at root"){
      Get() ~> emRoute ~> check { entityAs[String] should include("is running") }
    }
    it("should return a list of users at /users"){
      val users = List(User("timo@ext.md"), User("jp@ext.md"))
      stub(mockUserActions.getUsers()).toReturn(users);
      Get("/users") ~> emRoute ~> check { 
        val getUsersResponse = entityAs[String]
        writeJsonOutput("getUsersResponse", getUsersResponse)
        getUsersResponse should include("timo@ext.md")
      }
      verify(mockUserActions).getUsers()
    }
  }
  
  
  // Helper file writer
  def writeJsonOutput(filename: String, contents: String): Unit = {
    Some(new PrintWriter("target/test-classes/" + filename + ".json")).foreach{p => p.write(contents); p.close}
  }
}