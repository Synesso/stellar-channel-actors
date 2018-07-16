package stellar.channel

import akka.actor.{Actor, ActorRef, Stash}
import com.typesafe.scalalogging.LazyLogging
import stellar.channel.Channel.{Close, Pay}
import stellar.channel.ChannelAccount.{Flush, Initialise}
import stellar.sdk.inet.TxnFailure
import stellar.sdk.op.{AccountMergeOperation, PaymentOperation}
import stellar.sdk.resp.TransactionPostResp
import stellar.sdk.{Account, KeyPair, Network, Transaction}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

// todo - what is accnKey vs accountKey?
class ChannelAccount(signerKey: KeyPair, accountKey: KeyPair, implicit val network: Network) extends Actor with Stash with LazyLogging {

  import context.dispatcher

  val MaxTxnLength = 100

  override def preStart = {
    network.account(accountKey).map(_.toAccount).onComplete {
      case Success(accn) => self ! Initialise(accountKey, accn)
      case Failure(t) => logger.error(s"Failed to get account details for $accountKey", t)
    }
  }

  override def receive: Receive = {
    case Initialise(accnKey, accn) =>
      logger.debug(s"Channel account ready with $accnKey")
      context.become(ready(accnKey, accn, sender()))
      unstashAll()
    case _ => stash()
  }

  def ready(accnKey: KeyPair, accn: Account, replyTo: ActorRef, batch: Seq[PaymentOperation] = Nil): Receive = {

    def transmit(operations: Seq[PaymentOperation]) = {
      logger.debug(s"$self transmitting ${operations.size} payments")
      operations.map(_.sourceAccount).zipWithIndex.foreach(z => logger.debug(z.toString))
      val paymentResponse: Future[TransactionPostResp] = for {
        txn <- Future.fromTry {
          operations.foldLeft(Transaction(accn))(_ add _).sign(accnKey, signerKey)
        }
        resp <- txn.submit()
      } yield resp
      replyTo ! Await.result(paymentResponse, 5.minutes)
    }

    {
      case Pay(recipient, amount) =>
        logger.debug(s"Received Pay($recipient, $amount)")
        val payment = PaymentOperation(recipient, amount, sourceAccount = Some(signerKey))
        batch match {
          case Nil =>
            context.system.scheduler.scheduleOnce(1.second, self, Flush) // flush the batch in the future
            context.become(ready(accnKey, accn, replyTo, Seq(payment)))  // add this payment to the batch
          case _ if batch.size == MaxTxnLength - 1 =>
            transmit(payment +: batch)                               // transact the batch right now
            context.become(ready(accnKey, accn.withIncSeq, replyTo)) // clear the batch
          case _ =>
            context.become(ready(accnKey, accn, replyTo, payment +: batch)) // add this payment to the batch
        }

      case Flush if batch.nonEmpty =>
        transmit(batch)
        context.become(ready(accnKey, accn.withIncSeq, replyTo))

      case Close if batch.nonEmpty =>
        self ! Flush
        self ! Close

      case Close =>
        val closeResponse: Future[TransactionPostResp] = for {
          txn <- Future.fromTry(Transaction(accn).add(AccountMergeOperation(signerKey)).sign(accountKey))
          resp <- txn.submit()
        } yield resp
        closeResponse onComplete {
          case Success(tpr) => replyTo ! tpr
          case Failure(t: TxnFailure) =>
            logger.debug(s"xyz ${t.getMessage}: ${t.resultXDR}")
        }

      case any => logger.debug(s"$self Received $any")
    }
  }

}

object ChannelAccount {
  case class Initialise(accnKey: KeyPair, accn: Account)
  case object Flush
}
