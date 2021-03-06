package org.extendedmind.db

import java.util.UUID
import scala.collection.JavaConversions.iterableAsScalaIterable
import org.extendedmind.Response._
import org.extendedmind._
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
import org.neo4j.graphdb.traversal.Evaluation
import org.neo4j.kernel.OrderedByTypeExpander
import org.neo4j.graphdb.Relationship
import org.neo4j.graphdb.PathExpander
import org.neo4j.kernel.Uniqueness
import org.neo4j.index.lucene.ValueContext
import org.neo4j.graphdb.index.Index

trait ItemDatabase extends AbstractGraphDatabase {

  // PUBLIC

  def putNewItem(owner: Owner, item: Item): Response[SetResult] = {
    for {
      itemNode <- createItem(owner, item).right
      result <- Right(getSetResult(itemNode, true)).right
      unit <- Right(addToItemsIndex(owner, itemNode, result)).right
    } yield result
  }

  def putExistingItem(owner: Owner, itemUUID: UUID, item: Item): Response[SetResult] = {
    for {
      itemNode <- updateItem(owner, itemUUID, item).right
      result <- Right(getSetResult(itemNode, false)).right
      unit <- Right(updateItemsIndex(itemNode, result)).right      
    } yield result
  }

  def getItem(owner: Owner, itemUUID: UUID): Response[Item] = {
    withTx {
      implicit neo =>
        for {
          itemNode <- getItemNode(owner, itemUUID).right
          item <- toCaseClass[Item](itemNode).right
        } yield item
    }
  }

  def getItems(owner: Owner): Response[Items] = {
    withTx {
      implicit neo =>
        for {
          ownerNodes <- getOwnerNodes(owner).right
          itemNodes <- getItemNodes(ownerNodes).right
          items <- getItems(itemNodes, owner).right
        } yield items
    }
  }
  
  def deleteItem(owner: Owner, itemUUID: UUID): Response[DeleteItemResult] = {
    for {
      deletedItemNode <- deleteItemNode(owner, itemUUID).right
      result <- Right(getDeleteItemResult(deletedItemNode._1, deletedItemNode._2)).right
      unit <- Right(updateItemsIndex(deletedItemNode._1, result.result)).right
    } yield result
  }
  
  def undeleteItem(owner: Owner, itemUUID: UUID, mandatoryLabel: Option[Label] = None): Response[SetResult] = {
    for {
      itemNode <- undeleteItemNode(owner, itemUUID, mandatoryLabel).right
      result <- Right(getSetResult(itemNode, false)).right
      unit <- Right(updateItemsIndex(itemNode, result)).right
    } yield result
  }
   
  def destroyDeletedItems(owner: Owner): Response[CountResult] = {
    withTx {
      implicit neo4j => 
        for {
          ownerNodes <- getOwnerNodes(owner).right
          deleteResult <- Right(destroyDeletedItems(ownerNodes)).right
        } yield deleteResult
      }
  }
  
  def rebuildItemsIndex(ownerUUID: UUID): Response[CountResult] = {
    withTx {
      implicit neo4j => 
        for {
          ownerNode <- getNode(ownerUUID, MainLabel.OWNER).right
          result <- rebuildItemsIndex(ownerNode).right
        } yield result
      }
  }
  
  // PRIVATE

  protected def getItems(itemNodes: Iterable[Node], owner: Owner)(implicit neo4j: DatabaseService): Response[Items] = {
    val itemBuffer = new ListBuffer[Item]
    val taskBuffer = new ListBuffer[Task]
    val noteBuffer = new ListBuffer[Note]
    val tagBuffer = new ListBuffer[Tag]
    
    itemNodes foreach (itemNode =>
      if (itemNode.hasLabel(ItemLabel.NOTE)) {
        val note = toNote(itemNode, owner)
        if (note.isLeft) {
          return fail(INTERNAL_SERVER_ERROR, note.left.get.toString)
        }
        noteBuffer.append(note.right.get)
      } else if (itemNode.hasLabel(ItemLabel.TASK)) {
        val task = toTask(itemNode, owner)
        if (task.isLeft) {
          return fail(INTERNAL_SERVER_ERROR, task.left.get.toString)
        }
        taskBuffer.append(task.right.get)
      } else if (itemNode.hasLabel(ItemLabel.TAG)) {
        val tag = toTag(itemNode, owner)
        if (tag.isLeft) {
          return fail(INTERNAL_SERVER_ERROR, tag.left.get.toString)
        }
        tagBuffer.append(tag.right.get)
        
      }
      else {
        val item = toCaseClass[Item](itemNode)
        if (item.isLeft) {
          return fail(INTERNAL_SERVER_ERROR, "Could not convert item: " + item.left.get)
        }
        itemBuffer.append(item.right.get)
      })
    Right(Items(
      if (itemBuffer.isEmpty) None else Some(itemBuffer.toList),
      if (taskBuffer.isEmpty) None else Some(taskBuffer.toList),
      if (noteBuffer.isEmpty) None else Some(noteBuffer.toList),
      if (tagBuffer.isEmpty) None else Some(tagBuffer.toList)))
  }
  
  // Methods for converting tasks and nodes
  def toTask(taskNode: Node, owner: Owner)(implicit neo4j: DatabaseService): Response[Task];
  def toNote(noteNode: Node, owner: Owner)(implicit neo4j: DatabaseService): Response[Note];
  def toTag(tagNode: Node, owner: Owner)(implicit neo4j: DatabaseService): Response[Tag];

  protected def getItemNodes(ownerNodes: OwnerNodes)(implicit neo4j: DatabaseService): Response[Iterable[Node]] = {
    val itemsFromOwner: TraversalDescription = itemsTraversal
        .evaluator(PropertyEvaluator(
            ItemLabel.TASK, "completed",
            Evaluation.EXCLUDE_AND_PRUNE,
            Evaluation.INCLUDE_AND_CONTINUE))
        .evaluator(PropertyEvaluator(
            MainLabel.ITEM, "deleted",
            Evaluation.EXCLUDE_AND_PRUNE,
            Evaluation.INCLUDE_AND_CONTINUE))

    val traverser = itemsFromOwner.traverse(getOwnerNode(ownerNodes))
    Right(traverser.nodes())
  }

  protected def itemsTraversal(): TraversalDescription = { 
      Traversal.description()
        .relationships(DynamicRelationshipType.withName(SecurityRelationship.OWNS.name),
          Direction.OUTGOING)
        .depthFirst()
        .evaluator(Evaluators.excludeStartPosition())
        .evaluator(LabelEvaluator(List(MainLabel.ITEM)))
  }
  
  protected def createItem(owner: Owner, item: AnyRef, 
                           extraLabel: Option[Label] = None, extraSubLabel: Option[Label] = None): 
                           Response[Node] = {
    withTx {
      implicit neo4j =>
        for {
          ownerNodes <- getOwnerNodes(owner).right
          itemNode <- createItem(ownerNodes, item, extraLabel, extraSubLabel).right
        } yield itemNode
    }
  }
  
  protected def getOwnerNodes(owner: Owner)(implicit neo4j: DatabaseService): Response[OwnerNodes] = {
    for{
      userNode <- getNode(owner.userUUID, OwnerLabel.USER).right
      collectiveNode <- getNodeOption(owner.collectiveUUID, OwnerLabel.COLLECTIVE).right
    } yield OwnerNodes(userNode, collectiveNode)
  }

  protected def createItem(ownerNodes: OwnerNodes, item: AnyRef, extraLabel: Option[Label], extraSubLabel: Option[Label])
            (implicit neo4j: DatabaseService): Response[Node] = {
    val itemNode = createNode(item, MainLabel.ITEM)
    if (extraLabel.isDefined){
      itemNode.addLabel(extraLabel.get)
      if (extraSubLabel.isDefined){
        itemNode.addLabel(extraSubLabel.get)
      }
    }
    if (ownerNodes.collective.isDefined){
      // Collective is the owner, user the creator
      ownerNodes.collective.get --> SecurityRelationship.OWNS --> itemNode
      ownerNodes.user --> SecurityRelationship.IS_CREATOR --> itemNode
    }else{
      // User is the owner
      ownerNodes.user --> SecurityRelationship.OWNS --> itemNode
    }
    
    Right(itemNode)
  }

  protected def updateItem(owner: Owner, itemUUID: UUID, item: AnyRef, 
                          additionalLabel: Option[Label] = None, 
                          additionalSubLabel: Option[Tuple2[Label, Label]] = None): Response[Node] = {
    withTx {
      implicit neo4j =>
        for {
          itemNode <- getItemNode(owner, itemUUID, exactLabelMatch = false).right
          itemNode <- Right(setLabel(itemNode, additionalLabel, additionalSubLabel)).right
          itemNode <- updateNode(itemNode, item).right
        } yield itemNode
    }
    
    
  }

  protected def setLabel(node: Node, additionalLabel: Option[Label], additionalSubLabel: Option[Tuple2[Label, Label]])(implicit neo4j: DatabaseService): Node = {
    if (additionalLabel.isDefined && !node.hasLabel(additionalLabel.get)){
      node.addLabel(additionalLabel.get)
      if (additionalSubLabel.isDefined && !node.hasLabel(additionalSubLabel.get._1)){
        node.addLabel(additionalSubLabel.get._1)
        // Need to remove the other as the sublabel is either or
        if (node.hasLabel(additionalSubLabel.get._2))
          node.removeLabel(additionalSubLabel.get._2)
      }
    }
    node
  }

  protected def getItemNode(owner: Owner, itemUUID: UUID, mandatoryLabel: Option[Label] = None, 
                            acceptDeleted: Boolean = false, exactLabelMatch: Boolean = true)
                           (implicit neo4j: DatabaseService): Response[Node] = {
    val itemNode = if (mandatoryLabel.isDefined) getItemNode(getOwnerUUID(owner), itemUUID, mandatoryLabel.get, acceptDeleted)
                   else getItemNode(getOwnerUUID(owner), itemUUID, MainLabel.ITEM, acceptDeleted)
    if (itemNode.isLeft) return itemNode              
    
    // If searching for just ITEM, needs to fail for tasks and notes
    if (exactLabelMatch && mandatoryLabel.isEmpty && 
        (itemNode.right.get.hasLabel(ItemLabel.NOTE) 
         || itemNode.right.get.hasLabel(ItemLabel.TASK)
         || itemNode.right.get.hasLabel(ItemLabel.TAG))){
      return fail(INVALID_PARAMETER, "item already either note, task or tag with UUID " + itemUUID)
    }
    itemNode
  }
  
  protected def getItemNode(ownerUUID: UUID, itemUUID: UUID, label: Label, acceptDeleted: Boolean)
  						   (implicit neo4j: DatabaseService): Response[Node] = {
    val itemsIndex = neo4j.gds.index().forNodes("items")
    val itemNodeList = itemsIndex.query( "owner:\"" + UUIDUtils.getTrimmedBase64UUID(ownerUUID) 
                                        + "\" AND item:\"" + UUIDUtils.getTrimmedBase64UUID(itemUUID)+ "\"").toList        
    if (itemNodeList.length == 0) {
      fail(INVALID_PARAMETER, "Could not find item " + itemUUID + " for owner " + ownerUUID)
    } else if (itemNodeList.length == 0) {
      fail(INTERNAL_SERVER_ERROR, "More than one item found with item " + itemUUID + " and owner + " + ownerUUID)
    } else{
      val itemNode = itemNodeList(0)
      if (!itemNode.hasLabel(label)){
        fail(INVALID_PARAMETER, "Item " + itemUUID + " does not have label " + label.labelName)
      }else if (!acceptDeleted && itemNode.hasProperty("deleted")){
        fail(INVALID_PARAMETER, "Item " + itemUUID + " is deleted")
      }else {
        Right(itemNode)
      }
    }
  }

  protected def putExistingExtendedItem(owner: Owner, itemUUID: UUID, extItem: ExtendedItem, 
                              label: Label, subLabel: Option[Tuple2[Label, Label]] = None): 
        Response[Node] = {
    withTx {
      implicit neo4j =>
        for {
          itemNode <- updateItem(owner, itemUUID, extItem, Some(label), subLabel).right
          parentNodes <- setParentNodes(itemNode, owner, extItem).right
          tagNodes <- setTagNodes(itemNode, owner, extItem).right
        } yield itemNode
    }
  }

  protected def putNewExtendedItem(owner: Owner, extItem: ExtendedItem, label: Label, subLabel: Option[Label] = None): 
          Response[Node] = {
    withTx {
      implicit neo4j =>
        for {
          itemNode <- createItem(owner, extItem, Some(label), subLabel).right
          parentNodes <- setParentNodes(itemNode, owner, extItem).right
          tagNodes <- setTagNodes(itemNode, owner, extItem).right
        } yield itemNode
    }
  }

  protected def setParentNodes(itemNode: Node,  owner: Owner, extItem: ExtendedItem)(implicit neo4j: DatabaseService): Response[Tuple2[Option[Relationship], Option[Relationship]]] = {
    for {
      oldParentRelationships <- getParentRelationships(itemNode, owner).right
      newParentTaskRelationship <- setParentRelationship(itemNode, owner, extItem.parentTask, 
          oldParentRelationships._1, ItemLabel.TASK).right
      newParentNoteRelationship <- setParentRelationship(itemNode, owner, extItem.parentNote, 
          oldParentRelationships._2, ItemLabel.NOTE).right
      parentList <- Right((newParentTaskRelationship, newParentNoteRelationship)).right
    }yield parentList
  }

  protected def setParentRelationship(itemNode: Node, owner: Owner, parentUUID: Option[UUID], oldParentRelationship: Option[Relationship],
                              parentLabel: Label)(implicit neo4j: DatabaseService): Response[Option[Relationship]] = {
    if (parentUUID.isDefined){
      if (oldParentRelationship.isDefined){
        if (getUUID(oldParentRelationship.get.getEndNode()) 
              == parentUUID.get){
          return Right(oldParentRelationship)
        }else{
          deleteParentRelationship(oldParentRelationship.get)
        }
      }
      val newParentLabel = if (parentLabel == ItemLabel.NOTE) Some(ItemParentLabel.AREA) 
                           else if (parentLabel == ItemLabel.TASK) Some(ItemParentLabel.PROJECT)
                           else None
      for{
        parentNode <- getItemNode(owner, parentUUID.get, Some(parentLabel)).right
        parentRelationship <- createParentRelationship(itemNode, parentNode, newParentLabel).right
      }yield Some(parentRelationship)
    }else{
      if (oldParentRelationship.isDefined){
        deleteParentRelationship(oldParentRelationship.get)
      }
      Right(None)
    } 
  }
  
  protected def deleteParentRelationship(parentRelationship: Relationship)(implicit neo4j: DatabaseService) : Unit = {
    val parentNode = parentRelationship.getEndNode()
    parentRelationship.delete()
    // If there are no more children, remove the parent label as well
    if (!hasChildren(parentNode)){
      removeParentLabel(parentNode)
    }
  }
  
  protected def hasChildren(itemNode: Node)(implicit neo4j: DatabaseService): Boolean = {
    val itemsFromParent: TraversalDescription =
      Traversal.description()
        .depthFirst()
        .relationships(DynamicRelationshipType.withName(ItemRelationship.HAS_PARENT.name), Direction.INCOMING)
        .evaluator(Evaluators.excludeStartPosition())
        .evaluator(PropertyEvaluator(
            MainLabel.ITEM, "deleted",
            Evaluation.EXCLUDE_AND_PRUNE,
            Evaluation.INCLUDE_AND_CONTINUE))
        .depthFirst()
        .evaluator(Evaluators.toDepth(1))
    val traverser = itemsFromParent.traverse(itemNode)
    if (traverser.nodes().toList.length > 0){
      true
    }else{
      false
    }
  }

  protected def removeParentLabel(parentNode: Node){
    if (parentNode.hasLabel(ItemParentLabel.PROJECT))
      parentNode.removeLabel(ItemParentLabel.PROJECT)
    else if (parentNode.hasLabel(ItemParentLabel.AREA))
      parentNode.removeLabel(ItemParentLabel.AREA)    
  }
  
  protected def addParentLabel(parentNode: Node){
    if (!parentNode.hasLabel(ItemParentLabel.PROJECT))
      parentNode.addLabel(ItemParentLabel.PROJECT)
    else if (!parentNode.hasLabel(ItemParentLabel.AREA))
      parentNode.addLabel(ItemParentLabel.AREA)    
  }
  
  protected def createParentRelationship(itemNode: Node, parentNode: Node, newParentLabel: Option[Label])
              (implicit neo4j: DatabaseService): Response[Relationship] = {
    if (newParentLabel.isDefined){
      if(!parentNode.hasLabel(newParentLabel.get))
        parentNode.addLabel(newParentLabel.get)
    }else{
      // Both parent and child need to have the same labels
      itemNode.getLabels() foreach (label => {
        if (!parentNode.hasLabel(label)) fail(INVALID_PARAMETER, "Parent needs to be the same type as the child")
      })
    }
    val relationship = itemNode --> ItemRelationship.HAS_PARENT --> parentNode <;
    Right(relationship)
  }
  
  protected def getParentRelationships(itemNode: Node, owner: Owner)(implicit neo4j: DatabaseService): 
            Response[Tuple3[Option[Relationship], Option[Relationship], Option[Relationship]]] = {
    val parentNodesFromItem: TraversalDescription =
      Traversal.description()
        .depthFirst()
        .expand(new OrderedByTypeExpander()
          .add(DynamicRelationshipType.withName(ItemRelationship.HAS_PARENT.name), Direction.OUTGOING)
          .add(DynamicRelationshipType.withName(SecurityRelationship.OWNS.name), Direction.INCOMING)
          .asInstanceOf[PathExpander[_]])
        .evaluator(Evaluators.excludeStartPosition())
        .evaluator(LabelEvaluator(List(MainLabel.ITEM), 
                                  foundEvaluation = Evaluation.INCLUDE_AND_CONTINUE, 
                                  notFoundEvaluation = Evaluation.EXCLUDE_AND_PRUNE, 
                                  length = Some(1)))
        .evaluator(UUIDEvaluator(getOwnerUUID(owner), length = Some(2)))
        .evaluator(Evaluators.toDepth(2))
        .uniqueness(Uniqueness.NODE_PATH) // We want to get the userUUID twice to be sure that we have the same owner for both paths
        
    val traverser = parentNodesFromItem.traverse(itemNode)
    val relationshipList = traverser.relationships().toArray

    // Correct relationships are in order ITEM->ITEM then OWNER->ITEM
    var parentProject: Option[Relationship] = None
    var parentArea: Option[Relationship] = None
    var parentTag: Option[Relationship] = None
    var previousRelationship: Relationship = null
    relationshipList foreach (relationship => {
      if (relationship.getStartNode().hasLabel(MainLabel.OWNER) 
          && (previousRelationship != null && previousRelationship.getEndNode() == relationship.getEndNode())){
        if (relationship.getEndNode().hasLabel(ItemParentLabel.PROJECT))
          parentProject = Some(previousRelationship)
        else if (relationship.getEndNode().hasLabel(ItemParentLabel.AREA))
          parentArea = Some(previousRelationship)
        else if (relationship.getEndNode().hasLabel(ItemLabel.TAG))
          parentTag = Some(previousRelationship)
      }
      previousRelationship = relationship
    })

    Right((parentProject, parentArea, parentTag))
  }
  
  protected def setTagNodes(itemNode: Node, owner: Owner, extItem: ExtendedItem)
        (implicit neo4j: DatabaseService): Response[Option[List[Relationship]]] = {
    for {
      ownerNodes <- getOwnerNodes(owner).right
      oldTagRelationships <- getTagRelationships(itemNode, owner).right
      newTagRelationships <- setTagRelationships(itemNode, ownerNodes, extItem.tags, 
          oldTagRelationships).right
    }yield newTagRelationships
  }
  
  protected def setTagRelationships(itemNode: Node, ownerNodes: OwnerNodes, tagUUIDList: Option[List[UUID]], 
        oldTagRelationships: Option[List[Relationship]])
        (implicit neo4j: DatabaseService): Response[Option[List[Relationship]]] = {
    if (tagUUIDList.isDefined){
      val oldTagUUIDList = if (oldTagRelationships.isDefined) getEndNodeUUIDList(oldTagRelationships.get) else List()
      // Get all new UUIDs
      val newUUIDList = tagUUIDList.get.diff(oldTagUUIDList)      
      // Get all removed UUIDs
      val removedUUIDList = oldTagUUIDList.diff(tagUUIDList.get)
      
      for {
        newTagNodes <- getTagNodes(newUUIDList, ownerNodes).right
        newTagRelationships <- createTagRelationships(itemNode, newTagNodes).right 
        removedTagNodes <- getTagNodes(removedUUIDList, ownerNodes).right
        removedTagRelationships <- getTagRelationships(itemNode, removedTagNodes).right
        result <- Right(deleteTagRelationships(removedTagRelationships)).right
      }yield newTagRelationships
    }else{
      deleteTagRelationships(oldTagRelationships)
      Right(None)
    } 
  }
  
  protected def getTagNodes(tagUUIDList: List[UUID], ownerNodes: OwnerNodes)
      (implicit neo4j: DatabaseService): Response[List[Node]] = {
    val tagNodes = getNodes(tagUUIDList, ItemLabel.TAG)
    if (tagNodes.isRight){
      // Check that owner has access to all tags
      val ownerFromTag: TraversalDescription =
          Traversal.description()
            .relationships(DynamicRelationshipType.withName(SecurityRelationship.OWNS.name),
              Direction.INCOMING)
            .depthFirst()
            .evaluator(Evaluators.excludeStartPosition())
            .evaluator(PropertyEvaluator(
              MainLabel.ITEM, "deleted",
              Evaluation.EXCLUDE_AND_PRUNE,
              Evaluation.INCLUDE_AND_CONTINUE))
            .uniqueness(Uniqueness.NODE_PATH) // We want to make sure to get the same owner node for all tags
      val traverser = ownerFromTag.traverse(tagNodes.right.get:_*)
      val ownerNodeList = traverser.nodes().toArray
      if (ownerNodeList.length < tagNodes.right.get.length) {
        fail(INTERNAL_SERVER_ERROR, "Some of the tags does not have an owner")
      } else if (ownerNodeList.length > tagNodes.right.get.length) {
        fail(INTERNAL_SERVER_ERROR, "Some of the tags has more than one owner")
      } else {
        val ownerNode = {if (ownerNodes.collective.isDefined) ownerNodes.collective.get else ownerNodes.user}
        ownerNodeList foreach(tagOwner => {
          if (tagOwner != ownerNode){
            fail(INVALID_PARAMETER, "Some of the tags does not belong to the owner " 
                          + getUUID(ownerNode))
          }
        })
      }
    }    
    tagNodes
  }

  protected def deleteTagRelationships(tagRelationships: Option[List[Relationship]])(implicit neo4j: DatabaseService) : Unit = {
    if (tagRelationships.isDefined){
      tagRelationships.get foreach (tagRelationship => {
        tagRelationship.delete()
      })
    }
  }
  
  protected def createTagRelationships(itemNode: Node, tagNodes: List[Node])
              (implicit neo4j: DatabaseService): Response[Option[List[Relationship]]] = {
    if (tagNodes.isEmpty) return Right(None)
    Right(Some(tagNodes map (tagNode => {
      itemNode --> ItemRelationship.HAS_TAG --> tagNode <;
    })))
  }
  
  protected def getTagRelationships(itemNode: Node, owner: Owner)(implicit neo4j: DatabaseService): 
            Response[Option[List[Relationship]]] = {
    val tagNodesFromItem: TraversalDescription =
    Traversal.description()
        .depthFirst()
        .expand(new OrderedByTypeExpander()
          .add(DynamicRelationshipType.withName(ItemRelationship.HAS_TAG.name), Direction.OUTGOING)
          .add(DynamicRelationshipType.withName(SecurityRelationship.OWNS.name), Direction.INCOMING)
          .asInstanceOf[PathExpander[_]])
        .evaluator(Evaluators.excludeStartPosition())
        .evaluator(LabelEvaluator(List(MainLabel.ITEM), 
                                  foundEvaluation = Evaluation.INCLUDE_AND_CONTINUE, 
                                  notFoundEvaluation = Evaluation.EXCLUDE_AND_PRUNE, 
                                  length = Some(1)))
        .evaluator(UUIDEvaluator(getOwnerUUID(owner), length = Some(2)))
        .evaluator(Evaluators.toDepth(2))
        .uniqueness(Uniqueness.NODE_PATH) // We want to get the userUUID twice to be sure that we have the same owner for both paths

    val traverser = tagNodesFromItem.traverse(itemNode)
    val relationshipList = traverser.relationships().toArray

    val tagRelationshipBuffer = new ListBuffer[Relationship]
    var previousRelationship: Relationship = null
    relationshipList foreach (relationship => {
      if (relationship.getStartNode().hasLabel(MainLabel.OWNER) 
          && (previousRelationship != null && previousRelationship.getEndNode() == relationship.getEndNode())){
        if (relationship.getEndNode().hasLabel(ItemLabel.TAG))
          tagRelationshipBuffer.append(previousRelationship)
      }
      previousRelationship = relationship
    })
    
    if (tagRelationshipBuffer.isEmpty) Right(None)
    else Right(Some(tagRelationshipBuffer.toList))
  }
  
  protected def getTagRelationships(itemNode: Node, tagNodes: List[Node])(implicit neo4j: DatabaseService): 
            Response[Option[List[Relationship]]] = {
    if (tagNodes.isEmpty) return Right(None)

    val tagNodesFromItem: TraversalDescription =
      Traversal.description()
          .depthFirst()
          .relationships(DynamicRelationshipType.withName(ItemRelationship.HAS_TAG.name), Direction.OUTGOING)
          .evaluator(Evaluators.excludeStartPosition())
          .evaluator(LabelEvaluator(List(ItemLabel.TAG)))
          .evaluator(Evaluators.endNodeIs(Evaluation.INCLUDE_AND_PRUNE, Evaluation.EXCLUDE_AND_PRUNE, 
                                          tagNodes:_*))
          .evaluator(Evaluators.toDepth(1))

    val traverser = tagNodesFromItem.traverse(itemNode)
    val relationshipList = traverser.relationships().toList

    if (relationshipList.size != tagNodes.size){
      fail(INVALID_PARAMETER, "Every given tag UUID is not attached to the item " + getUUID(itemNode))
    }else{
      Right(Some(relationshipList))
    }
  }
  
  protected def deleteItemNode(owner: Owner, itemUUID: UUID): Response[Tuple2[Node, Long]] = {
    withTx {
      implicit neo =>
        for {
          itemNode <- getItemNode(owner, itemUUID).right
          deleted <- Right(deleteItem(itemNode)).right
        } yield (itemNode, deleted)
    }
  }
  
  protected def undeleteItemNode(owner: Owner, itemUUID: UUID, mandatoryLabel: Option[Label] = None): Response[Node] = {
    withTx {
      implicit neo =>
        for {
          itemNode <- getItemNode(owner, itemUUID, mandatoryLabel, acceptDeleted = true).right
          success <- Right(undeleteItem(itemNode)).right
        } yield itemNode
    }
  }

  protected def deleteItem(itemNode: Node)(implicit neo4j: DatabaseService): Long = {

    val deleted = System.currentTimeMillis()
    itemNode.setProperty("deleted", deleted)
    // Remove parent labels from parents
    val parentNodes = getAllParentNodes(itemNode)
    parentNodes.foreach( parentNode => {
        // If there are no more children, remove the parent label
        if (!hasChildren(parentNode)){
          removeParentLabel(parentNode)
        }
      }
    )
    deleted
  }
  
  protected def getAllParentNodes(itemNode: Node)(implicit neo4j: DatabaseService): List[Node] = {
    val parentNodesFromItem: TraversalDescription =
      Traversal.description()
        .depthFirst()
        .relationships(DynamicRelationshipType.withName(ItemRelationship.HAS_PARENT.name), Direction.OUTGOING)
        .evaluator(Evaluators.excludeStartPosition())
        .evaluator(Evaluators.toDepth(1))
    val traverser = parentNodesFromItem.traverse(itemNode)
    traverser.nodes().toList
  }
  
  protected def undeleteItem(itemNode: Node)(implicit neo4j: DatabaseService): Unit = {
    if(itemNode.hasProperty("deleted")){
      itemNode.removeProperty("deleted")
    }
    val parentNodes = getAllParentNodes(itemNode)
    parentNodes.foreach( parentNode =>
      // If parent node does not have a parent label, set it back!
      addParentLabel(parentNode)
    )
  }

  protected def getDeleteItemResult(item: Node, deleted: Long): DeleteItemResult = {
    withTx {
      implicit neo4j =>
        DeleteItemResult(deleted, getSetResult(item, false))
    }
  }
  
  protected def destroyDeletedItems(ownerNodes: OwnerNodes)(implicit neo4j: DatabaseService): 
          CountResult = {
    val deletedItemsFromOwner: TraversalDescription =
        Traversal.description()
            .relationships(DynamicRelationshipType.withName(SecurityRelationship.OWNS.name),
              Direction.OUTGOING)
            .depthFirst()
            .evaluator(Evaluators.excludeStartPosition())
            .evaluator(LabelEvaluator(List(MainLabel.ITEM)))
            .evaluator(PropertyEvaluator(MainLabel.ITEM, "deleted"))
    
    val traverser = deletedItemsFromOwner.traverse(getOwnerNode(ownerNodes))
    val deletedItemList = traverser.nodes().toList
    val count = deletedItemList.size
    deletedItemList.foreach(deletedItem => {
      destroyItem(deletedItem)
    })
    CountResult(count)
  }
  
  protected def destroyItem(deletedItem: Node)(implicit neo4j: DatabaseService) {
    // Remove all relationships
    val relationShipList = deletedItem.getRelationships().toList
    relationShipList.foreach(relationship => relationship.delete())
    // Delete token itself
    deletedItem.delete()
  }

  protected def addToItemsIndex(owner: Owner, itemNode: Node, setResult: SetResult): Unit = {
    withTx {
      implicit neo4j =>
        addToItemsIndex(getOwnerUUID(owner), itemNode, setResult.modified)
    }  
  }
  
  protected def addToItemsIndex(ownerUUID: UUID, itemNode: Node, modified: Long)(implicit neo4j: DatabaseService): Unit = {
    val itemsIndex = neo4j.gds.index().forNodes("items")
    itemsIndex.add(itemNode, "owner", UUIDUtils.getTrimmedBase64UUID(ownerUUID))
    itemsIndex.add(itemNode, "item", itemNode.getProperty("uuid"))
    addModifiedIndex(itemsIndex, itemNode, modified)
  }

  protected def updateItemsIndex(itemNode: Node, setResult: SetResult): Unit = {
    withTx {
      implicit neo4j =>
	    val itemsIndex = neo4j.gds.index().forNodes("items")
	    updateModifiedIndex(itemsIndex, itemNode, setResult.modified)
    }
  }

  protected def updateModifiedIndex(index: Index[Node], node: Node, modified: Long)(implicit neo4j: DatabaseService): Unit = {
    index.remove(node, "modified")
    addModifiedIndex(index, node, modified)
  }
  
  protected def addModifiedIndex(index: Index[Node], node: Node, modified: Long)(implicit neo4j: DatabaseService): Unit = {
    index.add(node, "modified", new ValueContext(node.getProperty("modified").asInstanceOf[Long] ).indexNumeric())    
  }
  
  protected def rebuildItemsIndex(ownerNode: Node)(implicit neo4j: DatabaseService): Response[CountResult] = {
    val itemsFromOwner: TraversalDescription = itemsTraversal
    val traverser = itemsFromOwner.traverse(ownerNode)
    val itemsIndex = neo4j.gds.index().forNodes("items")
    val ownerUUID = getUUID(ownerNode)
    traverser.nodes.foreach(itemNode => {
      itemsIndex.remove(itemNode)
      addToItemsIndex(ownerUUID, itemNode, itemNode.getProperty("modified").asInstanceOf[Long])
    })
    Right(CountResult(traverser.nodes.size))
  }

}