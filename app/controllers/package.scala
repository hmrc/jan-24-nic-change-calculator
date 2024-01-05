/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.mvc.QueryStringBindable

import java.time.Instant
import java.time.format.DateTimeFormatter
import scala.util.Try

package object controllers {

  implicit val instantQueryStringBinder: QueryStringBindable[Instant] =
    new QueryStringBindable[Instant] {

      private val dateFormat = DateTimeFormatter.ISO_INSTANT

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Instant]] = {
        for {
          values <- params.get(key)
          value  <- values.headOption
        } yield Try {
          Instant.parse(value)
        }.toEither.left.map(_ => s"$key: Error parsing instant")
      }

      override def unbind(key: String, value: Instant): String =
        s"$key=${dateFormat.format(value)}"
    }
}
