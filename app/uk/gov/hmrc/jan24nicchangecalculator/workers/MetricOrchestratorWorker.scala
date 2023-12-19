/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.jan24nicchangecalculator.workers

import org.apache.pekko.actor.ActorSystem
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logging}
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

@Singleton
class MetricOrchestratorWorker @Inject() (
                                           configuration: Configuration,
                                           lifecycle: ApplicationLifecycle,
                                           actorSystem: ActorSystem,
                                           metricOrchestrator: MetricOrchestrator
                                         )(implicit ec: ExecutionContext) extends Logging {

  private val scheduler = actorSystem.scheduler
  private val initialDelay: FiniteDuration = configuration.get[FiniteDuration]("workers.metric-orchestrator-worker.initial-delay")
  private val interval: FiniteDuration = configuration.get[FiniteDuration]("workers.metric-orchestrator-worker.interval")

  logger.info("Starting metric orchestration worker")

  private val task = scheduler.scheduleAtFixedRate(initialDelay, interval) { () =>
    logger.info("Attempting metric refresh")
    metricOrchestrator.attemptMetricRefresh().onComplete {
      case Success(_) => logger.info("Metrics refreshed")
      case Failure(_) => logger.warn("Unable to refresh metrics")
    }
  }

  lifecycle.addStopHook { () =>
    logger.info("Stopping metric orchestration worker")
    task.cancel()
    Future.unit
  }
}
