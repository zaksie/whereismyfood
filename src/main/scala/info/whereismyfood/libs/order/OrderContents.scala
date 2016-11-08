package info.whereismyfood.libs.order

import com.google.cloud.datastore.{EntityValue, PathElement}

import collection.JavaConverters._

/**
  * Created by zakgoichman on 11/8/16.
  */
case class OrderContents(items: Seq[OrderItem]) {
  def asDatastoreEntityList: java.util.List[EntityValue] ={
    items.map(x=>new EntityValue(x.asDatastoreEntity.get)).asJava
  }
}
