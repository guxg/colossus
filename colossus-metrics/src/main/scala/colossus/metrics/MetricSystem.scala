package colossus.metrics

import akka.actor._
import akka.agent.Agent
import colossus.metrics.MetricAddress.Root

import scala.concurrent.duration._


case class MetricSystemId(id: Long)

/**
 * The MetricSystem is a set of actors which handle the background operations of dealing with metrics. In most cases,
 * you only want to have one MetricSystem per application.
 *
 * Metrics are generated periodically by a Tick message published on the global event bus. By default this happens once
 * per second, but it can be configured to any time interval. So while events are being collected as they occur,
 * compiled metrics (such as rates and histogram percentiles) are generated once per tick.
 *
 * @param id The ID of the metrics system
 * @param namespace the base of the url describing the location of metrics within the system
 * @param clock an actor which serves as the clock for the metric system
 * @param database an actor which serves as the database of the metric system
 * @param snapshot
 * @param tickPeriod The frequency of the tick message
 */
case class MetricSystem(id: MetricSystemId, namespace: MetricAddress, clock: ActorRef, database: ActorRef, snapshot: Agent[MetricMap], tickPeriod: FiniteDuration) {


  def query(filter: MetricFilter): MetricMap = snapshot().filter(filter)  

  def query(queryString: String): MetricMap = query(MetricFilter(queryString))

  /**
   * Configures the reporting of the metric system.
   * @param config The [[MetricReporterConfig]] to use when configuring reporting
   * @param fact
   * @return
   */
  def report(config: MetricReporterConfig)(implicit fact: ActorRefFactory): ActorRef = MetricReporter(config)(this, fact)

  def sharedCollection(globalTags: TagMap = TagMap.Empty)(implicit fact: ActorRefFactory) = {
    implicit val me = this
    SharedCollection(globalTags)
  }

  def last: MetricMap = snapshot()

}

object MetricSystem {
  /**
   * Constructs a metric system
   * @param namespace the base of the url describing the location of metrics within the system
   * @param tickPeriod The frequency of the tick message
   * @param collectSystemMetrics whether to collect metrics from the system as well
   * @param system the actor system the metric system should use
   * @return
   */
  def apply(namespace: MetricAddress, tickPeriod: FiniteDuration = 1.second, collectSystemMetrics: Boolean = true)
  (implicit system: ActorSystem): MetricSystem = {
    import system.dispatcher
    val id = MetricSystemId(System.nanoTime)
    val clock = system.actorOf(Props(classOf[MetricClock], id, tickPeriod), name =  s"${namespace.idString}-clock")
    val snap = Agent[MetricMap](Map())
    val db = system.actorOf(Props(classOf[MetricDatabase], id, namespace, snap, collectSystemMetrics))

    val metrics = MetricSystem(id, namespace, clock, db, snap, tickPeriod)

    metrics
  }

  def deadSystem(implicit system: ActorSystem) = {
    import scala.concurrent.ExecutionContext.Implicits.global //this is ok since we're using it to create an agent that's never used
    MetricSystem(MetricSystemId(System.nanoTime), Root / "DEAD", system.deadLetters, system.deadLetters, Agent[MetricMap](Map()), 0.seconds)
  }

  object Global {
    //coming soon
  }
}



