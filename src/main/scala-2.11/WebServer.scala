/**
  * Created by zakgoichman on 8/7/16.
  */
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RouteResult}
import akka.util.Timeout
import search.{ElasticRequestActor}


object WebServer {
  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher
    implicit val as = ActorSystem("ElasticActor")
    implicit val timeout = new Timeout(1000)


    val actorRef = as actorOf Props[ElasticRequestActor]

    val route =
      path("search") { gameId =>
        get { req =>
          parameters('q) { q: String =>
            ask(actorRef, q).mapTo[RouteResult]
          }
        }
      }

    //val bindingFuture =
      Http().bindAndHandle(route, "localhost", 8080)

    /*
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
    */
  }
}
