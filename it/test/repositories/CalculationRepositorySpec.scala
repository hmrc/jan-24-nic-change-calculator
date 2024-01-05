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
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen, Shrink}
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.gov.hmrc.crypto.Scrambled
import uk.gov.hmrc.mongo.test.{CleanMongoCollectionSupport, DefaultPlayMongoRepositorySupport}

import java.time._
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

  private def datesBetween(min: Instant, max: Instant): Gen[Instant] =
    Gen.choose(min.toEpochMilli, max.toEpochMilli).map {
      millis =>
        Instant.ofEpochMilli(millis)
    }

  private def randomCalculation(from: Option[Instant] = None, to: Option[Instant] = None): Gen[Calculation] =
    for {
      sessionId <- Gen.stringOf(Gen.alphaNumChar).map(Scrambled)
      annualSalary <- Gen.chooseNum(1, 1000000)
      year1EstimatedNic <- Gen.chooseNum(1, 10000)
      year2EstimatedNic <- Gen.chooseNum(1, 10000)
      roundedSaving <- Gen.chooseNum(1, 1000)
      fromGen = from.getOrElse(LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC))
      toGen = to.getOrElse(fromGen + Period.ofMonths(6))
      timestamp <- datesBetween(fromGen, toGen)
    } yield Calculation(sessionId, annualSalary, year1EstimatedNic, year2EstimatedNic, roundedSaving, timestamp)

  private implicit val arbitraryCalculation: Arbitrary[Calculation] =
    Arbitrary(randomCalculation(None, None))

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

  ".lastCalculation" - {

    "must return `None` when there are no calculations" in {
      repository.lastCalculation.futureValue must not be (defined)
    }

    "must return the latest calculation when there are multiple calculations" in {

      val timestamp = Instant.ofEpochSecond(1000)

      val calc1 = Calculation(
        sessionId = Scrambled("foo"),
        annualSalary = 1.1,
        year1EstimatedNic = 2.2,
        year2EstimatedNic = 3.3,
        roundedSaving = 4,
        timestamp = timestamp
      )

      val calc2 = calc1.copy(sessionId = Scrambled("bar"), timestamp = Instant.ofEpochSecond(3000))
      val calc3 = calc1.copy(sessionId = Scrambled("baz"), timestamp = Instant.ofEpochSecond(500))

      Future.traverse(List(calc1, calc2, calc3))(repository.save).futureValue

      repository.lastCalculation.futureValue.value mustEqual calc2
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
        repository.numberOfCalculations().futureValue mustEqual 0
        Future.traverse(calculations)(repository.save).futureValue
        repository.numberOfCalculations().futureValue mustEqual calculations.length
      }
    }

    "must ignore calculations before `from`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculationsGen: Gen[(Instant, List[Calculation], List[Calculation])] = for {
        from <- from
        before = from - Period.ofMonths(6)
        numberOfCalculations <- Gen.chooseNum(0, 50)
        ignoredCalculations <- Gen.listOf(randomCalculation(from = Some(before), to = Some(from)))
        calculations <- Gen.listOfN(numberOfCalculations, randomCalculation(from = Some(from)))
      } yield (from, ignoredCalculations, calculations)

      forAll(calculationsGen) { case (from, ignoredCalculations, calculations) =>
        prepareDatabase()
        repository.numberOfCalculations(from = Some(from)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations)(repository.save).futureValue
        repository.numberOfCalculations(from = Some(from)).futureValue mustEqual calculations.length
      }
    }

    "must ignore calculations after `to`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculationsGen: Gen[(Instant, List[Calculation], List[Calculation])] = for {
        from <- from
        to = from + Period.ofMonths(6)
        after = to + Period.ofMonths(6)
        numberOfCalculations <- Gen.chooseNum(0, 50)
        ignoredCalculations <- Gen.listOf(randomCalculation(from = Some(to), to = Some(after)))
        calculations <- Gen.listOfN(numberOfCalculations, randomCalculation(from = Some(from), to = Some(to)))
      } yield (to, ignoredCalculations, calculations)

      forAll(calculationsGen) { case (to, ignoredCalculations, calculations) =>
        prepareDatabase()
        repository.numberOfCalculations(to = Some(to)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations)(repository.save).futureValue
        repository.numberOfCalculations(to = Some(to)).futureValue mustEqual calculations.length
      }
    }

    "must only report calculations between `from` and `to`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculationsGen: Gen[(Instant, Instant, List[Calculation], List[Calculation])] = for {
        from <- from
        before = from - Period.ofMonths(6)
        to = from + Period.ofMonths(6)
        after = to + Period.ofMonths(6)
        numberOfCalculations <- Gen.chooseNum(0, 50)
        beforeCalculations <- Gen.listOf(randomCalculation(from = Some(before), to = Some(from)))
        afterCalculations <- Gen.listOf(randomCalculation(from = Some(to), to = Some(after)))
        calculations <- Gen.listOfN(numberOfCalculations, randomCalculation(from = Some(from), to = Some(to)))
      } yield (from, to, beforeCalculations ++ afterCalculations, calculations)

      forAll(calculationsGen) { case (from, to, ignoredCalculations, calculations) =>
        prepareDatabase()
        repository.numberOfCalculations(from = Some(from), to = Some(to)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations)(repository.save).futureValue
        repository.numberOfCalculations(from = Some(from), to = Some(to)).futureValue mustEqual calculations.length
      }
    }
  }

  ".numberOfUniqueSessions" - {

    def calculationsWithTheSameSession(from: Option[Instant] = None, to: Option[Instant] = None): Gen[List[Calculation]] = for {
      sessionIdLength <- Gen.chooseNum(10, 100)
      sessionId <- Gen.stringOfN(sessionIdLength, Gen.alphaNumChar)
      calculations <- Gen.nonEmptyListOf(randomCalculation(from, to))
    } yield calculations.map(_.copy(sessionId = Scrambled(sessionId)))

    "must return the number of unique sessionIds in the calculations performed so far" in {

      val calculationsGen: Gen[List[List[Calculation]]] = for {
        expectedNumberOfSessions <- Gen.chooseNum(1, 25)
        calculations <- Gen.listOfN(expectedNumberOfSessions, calculationsWithTheSameSession())
      } yield calculations

      forAll(calculationsGen) { calculations =>
        prepareDatabase()
        repository.numberOfUniqueSessions().futureValue mustEqual 0
        Future.traverse(calculations.flatten)(repository.save).futureValue
        repository.numberOfUniqueSessions().futureValue mustEqual calculations.flatten.map(_.sessionId.value).toSet.size
      }
    }

    "must ignore calculations from before `from`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculationsGen: Gen[(Instant, List[Calculation], List[List[Calculation]])] = for {
        from <- from
        before = from - Period.ofMonths(6)
        ignoredCalculations <- Gen.listOf(randomCalculation(from = Some(before), to = Some(from)))
        expectedNumberOfSessions <- Gen.chooseNum(1, 25)
        calculations <- Gen.listOfN(expectedNumberOfSessions, calculationsWithTheSameSession(from = Some(from)))
      } yield (from, ignoredCalculations, calculations)

      forAll(calculationsGen) { case (from, ignoredCalculations, calculations) =>
        prepareDatabase()
        repository.numberOfUniqueSessions(from = Some(from)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations.flatten)(repository.save).futureValue
        repository.numberOfUniqueSessions(from = Some(from)).futureValue mustEqual calculations.flatten.map(_.sessionId.value).toSet.size
      }
    }

    "must ignore calculations from after `to`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculationsGen: Gen[(Instant, List[Calculation], List[List[Calculation]])] = for {
        from <- from
        to = from + Period.ofMonths(6)
        after = to + Period.ofMonths(6)
        ignoredCalculations <- Gen.listOf(randomCalculation(from = Some(to), to = Some(after)))
        expectedNumberOfSessions <- Gen.chooseNum(1, 25)
        calculations <- Gen.listOfN(expectedNumberOfSessions, calculationsWithTheSameSession(from = Some(from), to = Some(to)))
      } yield (to, ignoredCalculations, calculations)

      forAll(calculationsGen) { case (to, ignoredCalculations, calculations) =>
        prepareDatabase()
        repository.numberOfUniqueSessions(to = Some(to)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations.flatten)(repository.save).futureValue
        repository.numberOfUniqueSessions(to = Some(to)).futureValue mustEqual calculations.flatten.map(_.sessionId.value).toSet.size
      }
    }

    "must only include calculations between `from` and `to`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculationsGen: Gen[(Instant, Instant, List[Calculation], List[List[Calculation]])] = for {
        from <- from
        before = from - Period.ofMonths(6)
        to = from + Period.ofMonths(6)
        after = to + Period.ofMonths(6)
        numberOfCalculations <- Gen.chooseNum(1, 25)
        beforeCalculations <- Gen.listOf(randomCalculation(from = Some(before), to = Some(from)))
        afterCalculations <- Gen.listOf(randomCalculation(from = Some(to), to = Some(after)))
        calculations <- Gen.listOfN(numberOfCalculations, calculationsWithTheSameSession(from = Some(from), to = Some(to)))
      } yield (from, to, beforeCalculations ++ afterCalculations, calculations)

      forAll(calculationsGen) { case (from, to, ignoredCalculations, calculations) =>
        prepareDatabase()
        repository.numberOfUniqueSessions(from = Some(from), to = Some(to)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations.flatten)(repository.save).futureValue
        repository.numberOfUniqueSessions(from = Some(from), to = Some(to)).futureValue mustEqual calculations.length
      }
    }
  }

  ".totalSavings" - {

    "must return the total amount of savings from all calculations" in {

      val calculations: Gen[List[Calculation]] = for {
        numberOfCalculations <- Gen.chooseNum(0, 100)
        calculations <- Gen.listOfN(numberOfCalculations, arbitraryCalculation.arbitrary)
      } yield calculations

      forAll(calculations) { calculations =>
        prepareDatabase()
        repository.totalSavings().futureValue mustEqual 0
        Future.traverse(calculations)(repository.save).futureValue
        repository.totalSavings().futureValue mustEqual calculations.map(_.roundedSaving).sum
      }
    }

    "must ignore calculations from before `from`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculations: Gen[(Instant, List[Calculation], List[Calculation])] = for {
        from <- from
        before = from - Period.ofMonths(6)
        numberOfCalculations <- Gen.chooseNum(0, 100)
        ignoredCalculations <- Gen.listOf(randomCalculation(from = Some(before), to = Some(from)))
        calculations <- Gen.listOfN(numberOfCalculations, randomCalculation(from = Some(from)))
      } yield (from, ignoredCalculations, calculations)

      forAll(calculations) { case (from, ignoredCalculations, calculations) =>
        prepareDatabase()
        repository.totalSavings(from = Some(from)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations)(repository.save).futureValue
        repository.totalSavings(from = Some(from)).futureValue mustEqual calculations.map(_.roundedSaving).sum
      }
    }

    "must ignore calculations from after `to`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculations: Gen[(Instant, List[Calculation], List[Calculation])] = for {
        from <- from
        to = from + Period.ofMonths(6)
        after = to + Period.ofMonths(6)
        numberOfCalculations <- Gen.chooseNum(0, 100)
        ignoredCalculations <- Gen.listOf(randomCalculation(from = Some(to), to = Some(after)))
        calculations <- Gen.listOfN(numberOfCalculations, randomCalculation(from = Some(from)))
      } yield (to, ignoredCalculations, calculations)

      forAll(calculations) { case (to, ignoredCalculations, calculations) =>
        prepareDatabase()
        repository.totalSavings(to = Some(to)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations)(repository.save).futureValue
        repository.totalSavings(to = Some(to)).futureValue mustEqual calculations.map(_.roundedSaving).sum
      }
    }

    "must only include calculations between `from` and `to`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculations: Gen[(Instant, Instant, List[Calculation], List[Calculation])] = for {
        from <- from
        before = from - Period.ofMonths(6)
        to = from + Period.ofMonths(6)
        after = to + Period.ofMonths(6)
        numberOfCalculations <- Gen.chooseNum(0, 100)
        beforeCalculations <- Gen.listOf(randomCalculation(from = Some(before), to = Some(from)))
        afterCalculations <- Gen.listOf(randomCalculation(from = Some(to), to = Some(after)))
        calculations <- Gen.listOfN(numberOfCalculations, randomCalculation(from = Some(from)))
      } yield (from, to, beforeCalculations ++ afterCalculations, calculations)

      forAll(calculations) { case (from, to, ignoredCalculations, calculations) =>
        prepareDatabase()
        repository.totalSavings(from = Some(from), to = Some(to)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations)(repository.save).futureValue
        repository.totalSavings(from = Some(from), to = Some(to)).futureValue mustEqual calculations.map(_.roundedSaving).sum
      }
    }
  }

  ".totalSavingsAveragedBySession" - {

    "must return the total amount of savings where savings are grouped and averaged on sessionId" in {

      val a1 = arbitrary[Calculation].sample.value.copy(sessionId = Scrambled("a"), roundedSaving = 100)
      val a2 = a1.copy(roundedSaving = 201)

      val b1 = arbitrary[Calculation].sample.value.copy(sessionId = Scrambled("b"), roundedSaving = 1000)
      val b2 = b1.copy(roundedSaving = 2000)

      repository.totalSavingsAveragedBySession().futureValue mustEqual 0
      Future.traverse(Seq(a1, a2, b1, b2))(repository.save).futureValue
      repository.totalSavingsAveragedBySession().futureValue mustEqual 1650.5
    }

    "must ignore calculations from before `from`" in {

      val from = LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

      val a1 = arbitrary[Calculation].sample.value.copy(sessionId = Scrambled("a"), roundedSaving = 100, timestamp = from + Period.ofDays(1))
      val a2 = a1.copy(roundedSaving = 201)
      val a3 = a1.copy(timestamp = from - Period.ofDays(1))

      val b1 = arbitrary[Calculation].sample.value.copy(sessionId = Scrambled("b"), roundedSaving = 1000, timestamp = from + Period.ofDays(1))
      val b2 = b1.copy(roundedSaving = 2000)
      val b3 = b1.copy(timestamp = from - Period.ofDays(1))

      repository.totalSavingsAveragedBySession(from = Some(from)).futureValue mustEqual 0
      Future.traverse(Seq(a1, a2, a3, b1, b2, b3))(repository.save).futureValue
      repository.totalSavingsAveragedBySession(from = Some(from)).futureValue mustEqual 1650.5
    }

    "must ignore calculations from after `to`" in {

      val to = LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

      val a1 = arbitrary[Calculation].sample.value.copy(sessionId = Scrambled("a"), roundedSaving = 100, timestamp = to - Period.ofDays(1))
      val a2 = a1.copy(roundedSaving = 201)
      val a3 = a1.copy(timestamp = to + Period.ofDays(1))

      val b1 = arbitrary[Calculation].sample.value.copy(sessionId = Scrambled("b"), roundedSaving = 1000, timestamp = to - Period.ofDays(1))
      val b2 = b1.copy(roundedSaving = 2000)
      val b3 = b1.copy(timestamp = to + Period.ofDays(1))

      repository.totalSavingsAveragedBySession(to = Some(to)).futureValue mustEqual 0
      Future.traverse(Seq(a1, a2, a3, b1, b2, b3))(repository.save).futureValue
      repository.totalSavingsAveragedBySession(to = Some(to)).futureValue mustEqual 1650.5
    }

    "must only include calculations between `from` and `to`" in {

      val from = LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      val to = from + Period.ofMonths(6)

      val a1 = arbitrary[Calculation].sample.value.copy(sessionId = Scrambled("a"), roundedSaving = 100, timestamp = from + Period.ofDays(1))
      val a2 = a1.copy(roundedSaving = 201)
      val a3 = a1.copy(timestamp = from - Period.ofDays(1))

      val b1 = arbitrary[Calculation].sample.value.copy(sessionId = Scrambled("b"), roundedSaving = 1000, timestamp = to - Period.ofDays(1))
      val b2 = b1.copy(roundedSaving = 2000)
      val b3 = b1.copy(timestamp = to + Period.ofDays(1))

      repository.totalSavingsAveragedBySession(from = Some(from), to = Some(to)).futureValue mustEqual 0
      Future.traverse(Seq(a1, a2, a3, b1, b2, b3))(repository.save).futureValue
      repository.totalSavingsAveragedBySession(from = Some(from), to = Some(to)).futureValue mustEqual 1650.5
    }
  }

  ".averageSalary" - {

    "must return the mean salary from all calculations" in {

      val calculationsGen: Gen[List[Calculation]] = for {
        numberOfCalculations <- Gen.chooseNum(0, 50)
        calculations <- Gen.listOfN(numberOfCalculations, arbitraryCalculation.arbitrary)
      } yield calculations

      forAll(calculationsGen) { calculations =>
        val expectedAverageSalary = if (calculations.isEmpty) 0 else (calculations.map(_.annualSalary).sum / calculations.length).toLong
        prepareDatabase()
        repository.averageSalary().futureValue mustEqual 0
        Future.traverse(calculations)(repository.save).futureValue
        repository.averageSalary().futureValue mustEqual expectedAverageSalary
      }
    }

    "must ignore calculations from before `from`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculations: Gen[(Instant, List[Calculation], List[Calculation])] = for {
        from <- from
        before = from - Period.ofMonths(6)
        numberOfCalculations <- Gen.chooseNum(0, 100)
        ignoredCalculations <- Gen.listOf(randomCalculation(from = Some(before), to = Some(from)))
        calculations <- Gen.listOfN(numberOfCalculations, randomCalculation(from = Some(from)))
      } yield (from, ignoredCalculations, calculations)

      forAll(calculations) { case (from, ignoredCalculations, calculations) =>
        val expectedAverageSalary = if (calculations.isEmpty) 0 else (calculations.map(_.annualSalary).sum / calculations.length).toLong
        prepareDatabase()
        repository.averageSalary(from = Some(from)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations)(repository.save).futureValue
        repository.averageSalary(from = Some(from)).futureValue mustEqual expectedAverageSalary
      }
    }

    "must ignore calculations from after `to`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculations: Gen[(Instant, List[Calculation], List[Calculation])] = for {
        from <- from
        to = from + Period.ofMonths(6)
        after = to + Period.ofMonths(6)
        numberOfCalculations <- Gen.chooseNum(0, 100)
        ignoredCalculations <- Gen.listOf(randomCalculation(from = Some(to), to = Some(after)))
        calculations <- Gen.listOfN(numberOfCalculations, randomCalculation(from = Some(from)))
      } yield (to, ignoredCalculations, calculations)

      forAll(calculations) { case (to, ignoredCalculations, calculations) =>
        val expectedAverageSalary = if (calculations.isEmpty) 0 else (calculations.map(_.annualSalary).sum / calculations.length).toLong
        prepareDatabase()
        repository.averageSalary(to = Some(to)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations)(repository.save).futureValue
        repository.averageSalary(to = Some(to)).futureValue mustEqual expectedAverageSalary
      }
    }

    "must only include calculations between `from` and `to`" in {

      val from = datesBetween(
        LocalDate.of(2023, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
        LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      )

      val calculations: Gen[(Instant, Instant, List[Calculation], List[Calculation])] = for {
        from <- from
        before = from - Period.ofMonths(6)
        to = from + Period.ofMonths(6)
        after = to + Period.ofMonths(6)
        numberOfCalculations <- Gen.chooseNum(0, 100)
        beforeCalculations <- Gen.listOf(randomCalculation(from = Some(before), to = Some(from)))
        afterCalculations <- Gen.listOf(randomCalculation(from = Some(to), to = Some(after)))
        calculations <- Gen.listOfN(numberOfCalculations, randomCalculation(from = Some(from)))
      } yield (from, to, beforeCalculations ++ afterCalculations, calculations)

      forAll(calculations) { case (from, to, ignoredCalculations, calculations) =>
        val expectedAverageSalary = if (calculations.isEmpty) 0 else (calculations.map(_.annualSalary).sum / calculations.length).toLong
        prepareDatabase()
        repository.averageSalary(from = Some(from), to = Some(to)).futureValue mustEqual 0
        Future.traverse(ignoredCalculations ++ calculations)(repository.save).futureValue
        repository.averageSalary(from = Some(from), to = Some(to)).futureValue mustEqual expectedAverageSalary
      }
    }
  }

  implicit class RichInstant(instant: Instant) {

    def +(period: Period): Instant =
      LocalDateTime.ofInstant(instant, ZoneOffset.UTC).plus(period).toInstant(ZoneOffset.UTC)

    def -(period: Period): Instant =
      LocalDateTime.ofInstant(instant, ZoneOffset.UTC).minus(period).toInstant(ZoneOffset.UTC)
  }
}
