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

package repositories

import models.Calculation
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.crypto.Scrambled
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class CalculationRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[Calculation]
    with CleanMongoCollectionSupport
    with ScalaFutures
    with IntegrationPatience
    with OptionValues {

  protected override val repository = new CalculationRepository(mongoComponent)

  ".save" - {

    "must save a calculation" in {

      val timestamp = Instant.ofEpochSecond(1)

      val calculation = Calculation(
        sessionId = Scrambled("foo"),
        annualSalary = 1.1,
        year1EstimatedNic = 2.2,
        year2EstimatedNic = 3.3,
        roundedSaving = 4,
        timestamp = timestamp
      )

      repository.save(calculation).futureValue

      val calculations = repository.collection.find().toFuture().futureValue
      calculations.size mustEqual 1
      calculations.head mustEqual calculation
    }
  }
}
