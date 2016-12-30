package info.whereismyfood.modules.comm

/**
  * Created by zakgoichman on 11/26/16.
  */
object JsonProtocol {
  implicit class WithType(json: String){
    def withOpCode(opCode: String): String = {
      s"""{"op":"$opCode", "payload":$json}"""
    }
  }
}