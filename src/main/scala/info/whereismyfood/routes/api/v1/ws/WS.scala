package info.whereismyfood.routes.api.v1.ws

import info.whereismyfood.models.user.Creds

/**
  * Created by zakgoichman on 10/20/16.
  */
object WS {
  def routes(implicit creds: Creds) =
      Join.routes
}
