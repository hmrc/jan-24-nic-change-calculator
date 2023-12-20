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

package services

import models.{Calculation, CalculationRequest, Done}
import play.api.Configuration
import repositories.CalculationRepository
import uk.gov.hmrc.crypto.{OnewayCryptoFactory, PlainText}

import java.time.{Clock, Instant}
import javax.inject.Inject
import scala.concurrent.Future

class CalculationService @Inject()(
                                    repository: CalculationRepository,
                                    configuration: Configuration,
                                    clock: Clock
                                  ) {

  def save(sessionId: String, calculationRequest: CalculationRequest): Future[Done] = {

    val crypto = OnewayCryptoFactory.shaCryptoFromConfig("crypto", configuration.underlying)
    val keyedAndHashedSessionId = crypto.hash(PlainText(sessionId))

    val calculation =
      Calculation(
        sessionId         = keyedAndHashedSessionId,
        annualSalary      = calculationRequest.annualSalary,
        year1EstimatedNic = calculationRequest.year1EstimatedNic,
        year2EstimatedNic = calculationRequest.year2EstimatedNic,
        roundedSaving     = calculationRequest.roundedSaving,
        timestamp         = Instant.now(clock)
      )

    repository.save(calculation)
  }
}
