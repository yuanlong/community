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
package org.neo4j.cypher.pipes.matching

import org.neo4j.graphdb.Node
import org.neo4j.cypher.commands.{VariableLengthPath, RelatedTo, Pattern}
import org.neo4j.cypher.{SyntaxException, SymbolTable}

class MatchingContext(patterns: Seq[Pattern], boundIdentifiers:SymbolTable) {
  val patternGraph: Seq[PatternElement] = buildPatternGraph(boundIdentifiers)

  def getMatches(bindings: Map[String, Any]): Traversable[Map[String, Any]] = {
    val (pinnedName, pinnedNode) = bindings.head

    val pinnedPatternNode = patternGraph.find(_.key == pinnedName).get.asInstanceOf[PatternNode]

    pinnedPatternNode.pin(pinnedNode.asInstanceOf[Node])

    new PatternMatcher(pinnedPatternNode, bindings)
  }

  def buildPatternGraph(bindings:SymbolTable): Seq[PatternElement] = {
    val patternNodeMap: scala.collection.mutable.Map[String, PatternNode] = scala.collection.mutable.Map()
    val patternRelMap: scala.collection.mutable.Map[String, PatternRelationship] = scala.collection.mutable.Map()

    patterns.foreach(_ match {
      case RelatedTo(left, right, rel, relType, dir) => {
        val leftNode: PatternNode = patternNodeMap.getOrElseUpdate(left, new PatternNode(left))
        val rightNode: PatternNode = patternNodeMap.getOrElseUpdate(right, new PatternNode(right))

        patternRelMap(rel) = leftNode.relateTo(rel, rightNode, relType, dir)
      }
      case VariableLengthPath(pathName, start, end, minHops, maxHops, relType, dir) => {
        val startNode: PatternNode = patternNodeMap.getOrElseUpdate(start, new PatternNode(start))
        val endNode: PatternNode = patternNodeMap.getOrElseUpdate(end, new PatternNode(end))
        patternRelMap(pathName) = startNode.relateViaVariableLengthPathTo(pathName, endNode, minHops, maxHops, relType, dir)
      }
    })

    val patternElements = (patternNodeMap.values ++ patternRelMap.values).toSeq

    validatePattern(patternElements, bindings)

    patternElements
  }

  def validatePattern(patternElements: Seq[PatternElement], bindings: SymbolTable) {
    val elementsMap = patternElements.map(x => (x.key -> x)).toMap
    var visited = scala.collection.mutable.Seq[PatternElement]()

    def visit(x: PatternElement) {
      if (!visited.contains(x))
      {
        visited = visited ++ Seq(x)
        x match {
          case nod: PatternNode => nod.relationships.foreach(visit)
          case rel: PatternRelationship => {
            visit(rel.leftNode)
            visit(rel.rightNode)
          }
        }
      }
    }

    bindings.identifiers.foreach(id=>{
      val el = elementsMap.get(id.name)
      el match {
        case None =>
        case Some(x) => visit(x)
      }
    })

    val notVisited:Seq[PatternElement] = patternElements.toList -- visited.toList

    if(notVisited.nonEmpty){
      throw new SyntaxException("All parts of the pattern must either directly or indirectly be connected to at least one bound entity. These identifiers were found to be disconnected: " + notVisited.map(_.key).mkString("", ", ", ""))
    }

  }

}