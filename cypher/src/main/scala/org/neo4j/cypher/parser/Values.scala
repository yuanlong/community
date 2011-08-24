package org.neo4j.cypher.parser

/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import org.neo4j.cypher.commands._
import scala.util.parsing.combinator._

trait Values extends JavaTokenParsers with Tokens {

  def entityValue : Parser[Value] = identity ^^ {
    case x => EntityValue(x)
  }

  def value: Parser[Value] = (boolean | relationshipType | pathLength | property | stringValue | decimal)

  def property: Parser[Value] = identity ~ "." ~ identity ^^ {  case v ~ "." ~ p => PropertyValue(v,p) }

  def nullableProperty: Parser[Value] = property ~ "?" ^^ {
    case PropertyValue(e,p) ~ "?" => NullablePropertyValue(e, p)
  }

  def stringValue: Parser[Value] = string ^^ { case str => Literal(str) }

  def decimal: Parser[Value] = decimalNumber ^^ { case num => Literal(num.toDouble) }

  def boolean: Parser[Value] = (trueX | falseX)
  def trueX: Parser[Value] = ignoreCase("true") ^^ { case str => Literal(true) }
  def falseX: Parser[Value] = ignoreCase("false") ^^ { case str => Literal(false) }

  def relationshipType: Parser[Value] = identity <~ ".TYPE" ^^ {  case v => RelationshipTypeValue(v) }

  def pathLength: Parser[Value] = identity <~ ".LENGTH" ^^ { case v => PathLengthValue(v) }
}














