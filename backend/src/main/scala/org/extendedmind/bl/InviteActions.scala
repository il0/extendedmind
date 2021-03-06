package org.extendedmind.bl

import org.extendedmind.domain._
import org.extendedmind.db._
import org.extendedmind._
import org.extendedmind.email._
import org.extendedmind.security._
import org.extendedmind.Response._
import scaldi.Injector
import scaldi.Injectable
import org.extendedmind.db.EmbeddedGraphDatabase
import spray.util.LoggingContext
import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext
import akka.actor.ActorRefFactory
import akka.actor.ActorSystem
import java.util.UUID

trait InviteActions {

  def db: GraphDatabase
  def mailgun: MailgunClient
  def settings: Settings

  def actorRefFactory: ActorRefFactory
  implicit val implicitActorRefFactory = actorRefFactory
  implicit val implicitExecutionContext = actorRefFactory.dispatcher 
    
  def requestInvite(inviteRequest: InviteRequest)(implicit log: LoggingContext): Response[SetResult] = {
    log.info("requestInvite: email {}", inviteRequest.email)
    val setResult = for {
      isUnique <- db.validateEmailUniqueness(inviteRequest.email).right
      setResult <- db.putNewInviteRequest(inviteRequest).right
    } yield setResult
    
    if (setResult.isRight){
      val futureMailResponse = mailgun.sendRequestInviteConfirmation(inviteRequest.email, setResult.right.get.uuid.get)
      futureMailResponse onSuccess {
        case SendEmailResponse(message, id) => {
          val saveResponse = for{
            putExistingResponse <- db.putExistingInviteRequest(setResult.right.get.uuid.get, 
                                                               inviteRequest.copy(emailId = Some(id))).right
            updateResponse <- Right(db.updateInviteRequestModifiedIndex(putExistingResponse._2, 
                                                                        putExistingResponse._3)).right
          } yield putExistingResponse._1
          if (saveResponse.isLeft) 
            log.error("Error updating invite request for email {} with id {}, error: {}", 
                inviteRequest.email, id, saveResponse.left.get.head)
          else log.info("Saved invite request with email: {} and UUID: {} to emailId: {}", 
                          inviteRequest.email, setResult.right.get.uuid.get, id)
        }case _ =>
          log.error("Could not send invite request confirmation email to {}", inviteRequest.email)
      }
    }
    setResult
  }
  
  def putNewInviteRequest(inviteRequest: InviteRequest)(implicit log: LoggingContext): Response[SetResult] = {
    log.info("putNewInviteRequest: {}", inviteRequest)
    for {
      isUnique <- db.validateEmailUniqueness(inviteRequest.email).right
      setResult <- db.putNewInviteRequest(inviteRequest).right
      uuidResult <- db.forceUUID(setResult, inviteRequest.uuid, MainLabel.REQUEST).right
    } yield uuidResult
  }
  
  def getInviteRequests() (implicit log: LoggingContext): Response[InviteRequests] = {
    log.info("getInviteRequests")
    db.getInviteRequests    
  }
  
  def getInvites() (implicit log: LoggingContext): Response[Invites] = {
    log.info("getInvites")
    db.getInvites
  }
  
  def getInviteRequestQueueNumber(inviteRequestUUID: UUID) (implicit log: LoggingContext): 
        Response[InviteRequestQueueNumber] = {
    log.info("getInviteRequestQueueNumber for UUID {}", inviteRequestUUID)
    db.getInviteRequestQueueNumber(inviteRequestUUID)
  }
  
  def getInvite(code: Long, email: String) (implicit log: LoggingContext): 
        Response[Invite] = {
    log.info("getInvite for code {}, email {}", code, email)
    db.getInvite(code, email)
  }

  def acceptInviteRequest(userUUID: UUID, inviteRequestUUID: UUID, details: Option[InviteRequestAcceptDetails])
                         (implicit log: LoggingContext): Response[(SetResult, Invite)] = {
    log.info("acceptInviteRequest: request {}", inviteRequestUUID)
    
    val acceptResult = db.acceptInviteRequest(userUUID, inviteRequestUUID, 
        if (details.isDefined) Some(details.get.message) else None)
    
    if (acceptResult.isRight){
      val invite = acceptResult.right.get._2
      val futureMailResponse = mailgun.sendInvite(invite)
      futureMailResponse onSuccess {
       case SendEmailResponse(message, id) => {
          val saveResponse = db.putExistingInvite(acceptResult.right.get._1.uuid.get, 
                                                        invite.copy(emailId = Some(id)))
          if (saveResponse.isLeft) 
            log.error("Error updating invite for email {} with id {}, error: {}", 
                invite.email, id, saveResponse.left.get.head)
          else log.info("Accepted invite request with email: {} and UUID: {} with emailId: {}", 
                          invite.email, acceptResult.right.get._1.uuid.get, id)
        }
        case _ =>
          log.error("Could not send invite email to {}", invite.email)
      }
    }
    acceptResult
    
  }
  
  def acceptInvite(code: Long, signUp: SignUp) (implicit log: LoggingContext): 
        Response[SetResult] = {
    log.info("acceptInvite for code {}, email {}, with signUpMode {}", code, signUp.email, settings.signUpMode)
    db.acceptInvite(signUp, code, settings.signUpMode)
  }
  
  def destroyInviteRequest(inviteRequstUUID: UUID)(implicit log: LoggingContext): Response[DestroyResult] = {
    log.info("destroyInviteRequest: request {}", inviteRequstUUID)
    db.destroyInviteRequest(inviteRequstUUID)
  }
}

class InviteActionsImpl(implicit val implSettings: Settings, implicit val inj: Injector, 
                      implicit val implActorRefFactory: ActorRefFactory)
  extends InviteActions with Injectable {
  override def settings  = implSettings
  override def db = inject[GraphDatabase]
  override def mailgun = inject[MailgunClient]
  override def actorRefFactory = implActorRefFactory
}
