package info.whereismyfood.libs.payments

import com.paypal.base.rest.APIContext
import info.whereismyfood.aux.MyConfig

/**
  * Created by zakgoichman on 1/24/17.
  */
object PayPal {
  private val id = MyConfig.get("payments.paypal.id")
  private val secret = MyConfig.get("payments.paypal.secret")
  private val env = MyConfig.get("payments.paypal.env")
  val context = new APIContext(id, secret, env)
}
