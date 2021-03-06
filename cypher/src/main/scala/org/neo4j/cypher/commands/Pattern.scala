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
package org.neo4j.cypher.commands

import org.neo4j.graphdb.Direction
import scala.Some

abstract class Pattern

object RelatedTo {
  def apply(left: String, right: String, relName: String, relType: String, direction: Direction) =
    new RelatedTo(left, right, relName, Some(relType), direction)
}

object VariableLengthPath {
  def apply(pathName: String, start: String, end: String, minHops: Int, maxHops: Int, relType: String, direction: Direction) =
    new VariableLengthPath(pathName, start, end, minHops, maxHops, Some(relType), direction)
}

case class RelatedTo(left: String, right: String, relName: String, relType: Option[String], direction: Direction) extends Pattern

case class VariableLengthPath(pathName: String, start: String, end: String, minHops: Int, maxHops: Int, relType: Option[String], direction: Direction) extends Pattern

case class NamedPath(pathName: String, pathPattern: Pattern*) extends Traversable[Pattern] {
  def foreach[U](f: (Pattern) => U) {
    pathPattern.foreach(f)
  }
}