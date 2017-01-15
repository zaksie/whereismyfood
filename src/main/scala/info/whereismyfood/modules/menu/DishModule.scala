package info.whereismyfood.modules.menu

import akka.actor.{Actor, Props}
/**
  * Created by zakgoichman on 10/24/16.
  */

object DishModule {
  case class AddDish(dish: Dish)

  def props = Props[DishActor]
}

class DishActor extends Actor {
  import DishModule._

  override def receive: Receive = {
    case AddDish(dish) =>
      dish.saveToDatastore()
      println("Dish added successfully")
      sender ! true
  }
}



