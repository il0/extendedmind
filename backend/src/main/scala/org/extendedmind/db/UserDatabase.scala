package org.extendedmind.db

import java.util.UUID
import scala.collection.JavaConversions.iterableAsScalaIterable
import org.apache.commons.codec.binary.Base64
import org.extendedmind._
import org.extendedmind.Response._
import org.extendedmind.domain._
import org.extendedmind.security._
import org.neo4j.graphdb.Direction
import org.neo4j.graphdb.DynamicRelationshipType
import org.neo4j.graphdb.Node
import org.neo4j.graphdb.traversal.Evaluators
import org.neo4j.graphdb.traversal.TraversalDescription
import org.neo4j.kernel.Traversal
import org.neo4j.scala.DatabaseService
import scala.collection.mutable.ListBuffer
import org.neo4j.index.lucene.ValueContext
import org.neo4j.index.lucene.QueryContext

trait UserDatabase extends AbstractGraphDatabase {

  // PUBLIC

  def putNewUser(user: User, password: String, adminSignUp: Boolean): Response[SetResult] = {
    for{
      user <- createUser(user, password, (if (adminSignUp) Some(UserLabel.ADMIN) else None)).right
      result <- Right(getSetResult(user, true)).right
    }yield result
  }

  def getUser(email: String): Response[User] = {
    withTx{
      implicit neo =>
        for{
          userNode <- getUserNode(email).right
          user <- toCaseClass[User](userNode).right
        }yield user
    }
  }
  
  def getUser(uuid: UUID): Response[User] = {
    withTx{
      implicit neo =>
        for{
          userNode <- getNode(uuid, OwnerLabel.USER).right
          user <- toCaseClass[User](userNode).right
        }yield user
    }
  }
  
  def putNewInviteRequest(inviteRequest: InviteRequest): Response[SetResult] = {
    for{
      ir <- createInviteRequest(inviteRequest).right
      createResponse <- Right(createInviteRequestModifiedIndex(ir)).right
      result <- Right(getSetResult(ir, true)).right
    }yield result
  }
  
  def putExistingInviteRequest(inviteRequestUUID: UUID, inviteRequest: InviteRequest): Response[Tuple3[SetResult, Node, Long]] = {
    for{
      updatedInviteRequest <- updateInviteRequest(inviteRequestUUID, inviteRequest).right
      result <- Right(getSetResult(updatedInviteRequest._1, false)).right
    }yield (result, updatedInviteRequest._1, updatedInviteRequest._2)
  }
  
  def createInviteRequestModifiedIndex(inviteRequestNode: Node): Unit = {
    withTx {
      implicit neo =>
        val inviteRequests = neo.gds.index().forNodes("inviteRequests")
        inviteRequests.add(inviteRequestNode, "modified", 
            new ValueContext(inviteRequestNode.getProperty("modified").asInstanceOf[Long] ).indexNumeric())
    }
  }

  def updateInviteRequestModifiedIndex(inviteRequestNode: Node, oldModified: Long): Unit = {
    withTx {
      implicit neo =>
        val inviteRequests = neo.gds.index().forNodes("inviteRequests")
        inviteRequests.remove(inviteRequestNode, "modified", oldModified)
        inviteRequests.add(inviteRequestNode, "modified", 
            new ValueContext(inviteRequestNode.getProperty("modified").asInstanceOf[Long] ).indexNumeric())
    }
  }
  
  def getInviteRequests(): Response[List[InviteRequest]] = {
    withTx{
      implicit neo =>
        val inviteRequests = neo.gds.index().forNodes("inviteRequests")
        val inviteRequestNodeList = inviteRequests.query( "modified", 
            QueryContext.numericRange("modified", 0, Long.MaxValue).sort("modified")).toList        
        if (inviteRequestNodeList.isEmpty){
          Right(List())}
        else {
          Right(inviteRequestNodeList map (inviteRequestNode => {
            val response = toCaseClass[InviteRequest](inviteRequestNode)
            if (response.isLeft) return Left(response.left.get)
            else response.right.get
          }))
        }
    }
  }
  
  def getInviteRequestQueueNumber(inviteRequestUUID: UUID): Response[InviteRequestQueueNumber] = {
    withTx{
      implicit neo =>
        val inviteRequest = getNode(inviteRequestUUID, MainLabel.REQUEST)
        if (inviteRequest.isLeft) Left(inviteRequest.left.get)
        else{
          val inviteRequests = neo.gds.index().forNodes("inviteRequests")
          val inviteRequestNodeList = inviteRequests.query( "modified", 
              QueryContext.numericRange("modified", 0, Long.MaxValue).sort("modified")).toList
          val queueNumber = inviteRequestNodeList.indexOf(inviteRequest.right.get)
          if (queueNumber < -1){
            fail(INTERNAL_SERVER_ERROR, "Invite request could not be found from invite request index with UUID: " + inviteRequestUUID)
          }else{
            return Right(InviteRequestQueueNumber(queueNumber+1))
          }
        }
    }
  }
  
  def acceptInviteRequest(userUUID: UUID, inviteRequestUUID: UUID, invite: Invite): Response[(SetResult, String)] = {
    for{
      userNode <- getNode(userUUID, OwnerLabel.USER).right
      ir <- createInvite(userNode, inviteRequestUUID, invite).right
      result <- Right(getSetResult(ir._1, true)).right
    }yield (result, ir._2)
  }
  
  def putExistingInvite(inviteUUID: UUID, invite: Invite): Response[SetResult] = {
    for{
      updatedInvite <- updateInvite(inviteUUID, invite).right
      result <- Right(getSetResult(updatedInvite, false)).right
    }yield result
  }
  
  // PRIVATE
  
  protected def createUser(user: User, plainPassword: String, userLabel: Option[Label] = None): Response[Node] = {
    withTx{
      implicit neo4j =>
        val userNode = createNode(user, MainLabel.OWNER, OwnerLabel.USER)
        if (userLabel.isDefined) userNode.addLabel(userLabel.get)
        val salt = PasswordService.generateSalt
        val encryptedPassword = PasswordService.getEncryptedPassword(
          plainPassword, salt, PasswordService.ALGORITHM, PasswordService.ITERATIONS)
        userNode.setProperty("passwordAlgorithm", encryptedPassword.algorithm)
        userNode.setProperty("passwordIterations", encryptedPassword.iterations)
        userNode.setProperty("passwordHash", Base64.encodeBase64String(encryptedPassword.passwordHash))
        userNode.setProperty("passwordSalt", encryptedPassword.salt)
        userNode.setProperty("email", user.email)
        
        // Give user read permissions to common collectives
        val collectivesList = findNodesByLabelAndProperty(OwnerLabel.COLLECTIVE, "common", java.lang.Boolean.TRUE).toList
        if (!collectivesList.isEmpty) {
          collectivesList.foreach(collective => {
            userNode --> SecurityRelationship.CAN_READ --> collective;
          })
        }
        Right(userNode)
    }
  }

  protected def getUserNode(email: String): Response[Node] = {
    withTx {
      implicit neo =>
        val nodeIter = findNodesByLabelAndProperty(OwnerLabel.USER, "email", email)
        if (nodeIter.toList.isEmpty) {
          fail(INVALID_PARAMETER, "No users found with given email " + email)
        } else if (nodeIter.toList.size > 1) {
          fail(INTERNAL_SERVER_ERROR, "Ḿore than one user found with given email " + email)
        } else
          Right(nodeIter.toList(0))
    }
  }

  protected def getUserNode(tokenNode: Node)(implicit neo4j: DatabaseService): Response[Node] = {
    // Check that token is still valid
    val expires = tokenNode.getProperty("expires").asInstanceOf[Long]
    if (System.currentTimeMillis() > expires) {
      fail(TOKEN_EXPIRED, "Token has expired")
    }else{
      val userFromToken: TraversalDescription = 
        Traversal.description()
          .relationships(DynamicRelationshipType.withName(SecurityRelationship.IDS.name), 
                         Direction.OUTGOING)
          .depthFirst()
          .evaluator(Evaluators.excludeStartPosition())
          .evaluator(LabelEvaluator(List(OwnerLabel.USER)))
          
      val traverser = userFromToken.traverse(tokenNode)
      val userNodeList = traverser.nodes().toArray

      if (userNodeList.length == 0) {
        fail(INTERNAL_SERVER_ERROR, "Token attached to no users")
      } else if (userNodeList.length > 1) {
        fail(INTERNAL_SERVER_ERROR, "Token attached to more than one user")
      } else {
        Right(userNodeList.head)
      }
    }
  }

  protected def getUserNode(token: Token): Response[Node] = {
    withTx {
      implicit neo =>
        for {
          tokenNode <- getTokenNode(token).right
          userNode <- getUserNode(tokenNode).right
        }yield userNode
    }
  }
  
  protected def createInviteRequest(inviteRequest: InviteRequest): Response[Node] = {
    withTx {
      implicit neo =>
        Right(createNode(inviteRequest, MainLabel.REQUEST))
    }
  }
  
  protected def updateInviteRequest(inviteRequestUUID: UUID, inviteRequest: InviteRequest): Response[Tuple2[Node, Long]] = {
    withTx {
      implicit neo4j =>
        for {
          inviteRequestNode <- getNode(inviteRequestUUID, MainLabel.REQUEST).right
          updatedNode <- updateNode(inviteRequestNode, inviteRequest).right
        } yield (updatedNode, updatedNode.getProperty("modified").asInstanceOf[Long])
    }
  }
  
  protected def updateInvite(inviteUUID: UUID, invite: Invite): Response[Node] = {
    withTx {
      implicit neo4j =>
        for {
          inviteNode <- getNode(inviteUUID, MainLabel.INVITE).right
          updatedNode <- updateNode(inviteNode, invite).right
        } yield updatedNode
    }
  }
  
  protected def createInvite(userNode: Node, inviteRequestUUID: UUID, invite: Invite): Response[(Node, String)] = {
    withTx{
      implicit neo =>
        val inviteRequestNode = getNode(inviteRequestUUID, MainLabel.REQUEST)
        if (inviteRequestNode.isLeft) Left(inviteRequestNode.left.get)
        else{
          // Create an invite from the invite request
          val email = inviteRequestNode.right.get.getProperty("email").asInstanceOf[String]
          
          val inviteNode = createNode(invite, MainLabel.INVITE)
          inviteRequestNode.right.get --> SecurityRelationship.IS_ORIGIN --> inviteNode
          userNode --> SecurityRelationship.IS_ACCEPTER --> inviteNode
          Right(inviteNode,email)
        }
    }
  }
}