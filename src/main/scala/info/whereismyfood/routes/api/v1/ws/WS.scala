package info.whereismyfood.routes.api.v1.ws

import info.whereismyfood.libs.auth.Creds

/**
  * Created by zakgoichman on 10/20/16.
  */
object WS {
  def routes(implicit creds: Creds) =
      Tracking.routes
}
