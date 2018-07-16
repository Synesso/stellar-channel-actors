package stellar.channel

import akka.actor._
import akka.routing.{ActorRefRoutee, Broadcast, RoundRobinRoutingLogic, Router}
import com.typesafe.scalalogging.LazyLogging
import stellar.channel.Channel.{Close, Initialise, Pay}
import stellar.sdk._
import stellar.sdk.op.CreateAccountOperation
import stellar.sdk.resp.TransactionPostResp

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Responsible for creating channel accounts and coordinating the channel operations.
  */
class Channel(signer: KeyPair, implicit val network: Network, width: Int = 10) extends Actor with Stash with LazyLogging {

  import context.dispatcher

  override def preStart = {
    val accounts = Vector.fill(width)(KeyPair.random)
    val createOps = accounts.map(CreateAccountOperation(_, Amount.lumens(2)))

    val futureRouter = for {
      signerAccount <- network.account(signer).map(_.toAccount)
      createTxn = createOps.foldLeft(Transaction(signerAccount))(_ add _)
      signedTxn <- Future.fromTry(createTxn.sign(signer))
      response <- signedTxn.submit()
      routees = accounts.map { channelAccount =>
        val r = context.actorOf(Props(classOf[ChannelAccount], signer, channelAccount, network))
        context watch r
        ActorRefRoutee(r)
      }
    } yield Router(RoundRobinRoutingLogic(), routees)

    futureRouter onComplete {
      case Success(router) => self ! Initialise(router)
      case Failure(t) => throw t
    }
  }

  override def receive: Receive = {
    case Initialise(router) =>
      logger.debug(s"Initialised with $router")
      context.become(initialised(router))
      unstashAll()
    case _ => stash()
  }

  def initialised(router: Router): Receive = {
    case paymentRequest: Pay => router.route(paymentRequest, self)
    case t: TransactionPostResp => logger.debug(s"!!! $t")
    case Close => router.route(Broadcast(Close), self)
    case Terminated(a) => context.become(initialised(router.removeRoutee(a)))
    case x => logger.debug(s">>> $x")
  }
}

object Channel {
  case class Initialise(r: Router)
  case class Pay(recipient: PublicKey, amount: Amount)
  case object Close
}
