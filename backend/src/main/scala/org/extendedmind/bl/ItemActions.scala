package org.extendedmind.bl

import org.extendedmind.domain.User
import org.extendedmind.db.GraphDatabase
import org.extendedmind.db.EmbeddedGraphDatabase
import org.extendedmind.Settings
import scaldi.Injector
import scaldi.Injectable
import org.extendedmind.search.ElasticSearchIndex
import org.extendedmind.search.SearchIndex
import org.extendedmind.domain.Item
import java.util.UUID

trait ItemActions{

  def db: GraphDatabase;
  def si: SearchIndex;
  
  def putItem(userUUID: UUID, item: Item, itemUUID: Option[UUID]): String = {
    "TODO"
  }
  
  def getItems(userUUID: UUID): List[Item] = {
    List()
  }
}

class ItemActionsImpl(implicit val settings: Settings, implicit val inj: Injector) 
		extends ItemActions with Injectable{
  def db = inject[GraphDatabase]
  def si = inject[SearchIndex]
}