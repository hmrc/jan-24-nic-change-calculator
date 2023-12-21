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
import org.scalacheck.{Arbitrary, Gen, Shrink}
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.gov.hmrc.crypto.Scrambled
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport}

import java.time.{Instant, LocalDate, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CalculationRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[Calculation]
    with CleanMongoCollectionSupport
    with ScalaFutures
    with IntegrationPatience
    with OptionValues
    with ScalaCheckPropertyChecks {

  private implicit def noShrink[A]: Shrink[A] = Shrink.shrinkAny

  private def datesBetween(min: LocalDate, max: LocalDate): Gen[Instant] = {

    def toMillis(date: LocalDate): Long =
      date.atStartOfDay.atZone(ZoneOffset.UTC).toInstant.toEpochMilli

    Gen.choose(toMillis(min), toMillis(max)).map {
      millis =>
        Instant.ofEpochMilli(millis)
    }
  }

  private implicit val arbitraryCalculation: Arbitrary[Calculation] =
    Arbitrary {
      for {
        sessionId <- Gen.stringOf(Gen.alphaNumChar).map(Scrambled)
        annualSalary <- Gen.chooseNum(1, 1000000)
        year1EstimatedNic <- Gen.chooseNum(1, 10000)
        year2EstimatedNic <- Gen.chooseNum(1, 10000)
        roundedSaving <- Gen.chooseNum(1, 1000)
        timestamp <- datesBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1))
      } yield Calculation(sessionId, annualSalary, year1EstimatedNic, year2EstimatedNic, roundedSaving, timestamp)
    }

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

  ".numberOfCalculations" - {

    "must return the number of calculations that have been performed so far" in {

      val calculationsGen: Gen[List[Calculation]] = for {
        numberOfCalculations <- Gen.chooseNum(0, 50)
        calculations         <- Gen.listOfN(numberOfCalculations, arbitraryCalculation.arbitrary)
      } yield calculations

      forAll(calculationsGen) { calculations =>
        prepareDatabase()
        repository.numberOfCalculations.futureValue mustEqual 0
        Future.traverse(calculations)(repository.save).futureValue
        repository.numberOfCalculations.futureValue mustEqual calculations.length
      }
    }
  }

  ".numberOfUniqueSessions" - {

    "must return the number of unique sessionIds in the calculations performed so far" in {

      val calculationsWithTheSameSession: Gen[List[Calculation]] = for {
        sessionIdLength <- Gen.chooseNum(10, 100)
        sessionId <- Gen.stringOfN(sessionIdLength, Gen.alphaNumChar)
        calculations <- Gen.nonEmptyListOf(arbitraryCalculation.arbitrary)
      } yield calculations.map(_.copy(sessionId = Scrambled(sessionId)))

      val calculationsGen: Gen[List[List[Calculation]]] = for {
        expectedNumberOfSessions <- Gen.chooseNum(1, 25)
        calculations <- Gen.listOfN(expectedNumberOfSessions, calculationsWithTheSameSession)
      } yield calculations

      forAll(calculationsGen) { calculations =>
        prepareDatabase()
        repository.numberOfUniqueSessions.futureValue mustEqual 0
        Future.traverse(calculations.flatten)(repository.save).futureValue
        repository.numberOfUniqueSessions.futureValue mustEqual calculations.flatten.map(_.sessionId.value).toSet.size
      }
    }
  }
}
