package datastructs

import datastructs.OddsFormat.OddsFormat

/**
  * Created by zakgoichman on 8/8/16.
  */

object OddsFormat extends Enumeration {
  type OddsFormat = Value
  val Rational, Percent = Value
}

object WagerStatus extends Enumeration {
  type WagerStatus = Value
  val Open, Closed, Disputed, Adjucated, Complete = Value
}

abstract class Event {
  val name: String
  val id: String

  def isValid: Boolean = false
  def isResolved: Boolean = false

  import WagerStatus._
  def status: WagerStatus = Open
}

case class BinaryEvent(name: String, id: String) extends Event
case class UserId(id: String)
case class Odds(numericVal: Double, format: OddsFormat)
case class Sum(value: Double, currency: String)
case class OddsSumPair(odds: Odds, sum: Sum)
case class Wager(id: UserId, event: Event, oddsSumPair: OddsSumPair)
case class Game(event: Event, oddsSumPairRange: Array[OddsSumPair])