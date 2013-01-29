package com.libitec.sce.core

import java.io.File

import scala.concurrent.duration._

import akka.actor._

import org.eligosource.eventsourced.core._
import org.eligosource.eventsourced.journal._


object Main extends App {

  println("Start...")

  new Production().init()
}


class Production
  extends ActorSystemComponent
  with DoerComponent {

  def init() {

    val doer1 = createDoer(1)
    val doer2 = createDoer(2)

    transaction { sync ⇒
      doer1 ! sync(LoadSku("Sku for doer1"))
      doer2 ! sync(LoadSku("Sku for doer2"))
    }
  }
}


trait ActorSystemComponent {
  implicit val actorSystem = ActorSystem("eventsourced-example")
}


case class LoadSku(sku: String)





trait DoerComponent {
  this: ActorSystemComponent ⇒


  def createDoer(id: Long) =
    actorSystem.actorOf(Props(new Doer(id)), name = s"doer-$id")

  val doerJournal = Journal(JournalioJournalProps(new File("target/journal/doers")))
  val doerExtension = EventsourcingExtension(actorSystem, doerJournal)


  /**
   * Actor
   */
  class Doer(doerId: Long) extends Actor {

    var payload = Set.empty[String]
    var cnt = 0

    val doerStore = doerExtension.processorOf(Props(new DoerStore with Receiver with Eventsourced { val id = doerId.toInt } ))(context)
    doerExtension.recover(id ⇒ if (id == doerId) Some(0) else None, 1 minute)


    class DoerStore extends Actor {
      def receive = {
        case LoadSku(sku) ⇒
          cnt += 1
          payload += s"$sku; - $cnt"
          println(s"[Doer] event = $sku ($payload)")

        case Snapshot(bites: Array[Byte]) {
          val p = serializer.fromBinary()
          cnt = p.getCnt
        }
      }
    }


    def receive =  {
      case LoadSku(sku) ⇒
        doerStore ? Message(LoadSku(sku + ";") onSuccess {
          case e ⇒
            payload.
        }
    }

  }
}
