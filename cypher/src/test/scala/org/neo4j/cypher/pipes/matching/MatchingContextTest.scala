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

import org.scalatest.Assertions
import org.neo4j.graphdb.Direction
import org.junit.{Ignore, Test}
import org.neo4j.cypher.{SymbolTable, GraphDatabaseTestBase}
import org.neo4j.cypher.commands.{NodeIdentifier, VariableLengthPath, RelatedTo, Pattern}
import java.lang.AssertionError


/*
A few of the tests cast the result to a set before comparing with the expected values. This is because
Set doesn't care about ordering, but Seq does. The tests should not care about ordering
 */
class MatchingContextTest extends GraphDatabaseTestBase with Assertions {

  @Test def singleHopSingleMatch() {
    val a = createNode()
    val b = createNode()
    val r = relate(a, b, "rel")

    val patterns: Seq[Pattern] = Seq(RelatedTo("a", "b", "r", "rel", Direction.OUTGOING))
    val matchingContext = new MatchingContext(patterns, bind("a"))

    assertMatches(matchingContext.getMatches(Map("a" -> a)), 1, Map("a" -> a, "b" -> b, "r" -> r))
  }

  @Test def singleHopDoubleMatch() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    val r1 = relate(a, b, "rel")
    val r2 = relate(a, c, "rel")

    val patterns: Seq[Pattern] = Seq(RelatedTo("a", "b", "r", "rel", Direction.OUTGOING))
    val matchingContext = new MatchingContext(patterns, bind("a"))


    assertMatches(matchingContext.getMatches(Map("a" -> a)), 2,
      Map("a" -> a, "b" -> b, "r" -> r1),
      Map("a" -> a, "b" -> c, "r" -> r2))
  }

  @Test def doubleHopDoubleMatch() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    val r1 = relate(a, b, "rel")
    val r2 = relate(a, c, "rel")

    val patterns: Seq[Pattern] = Seq(
      RelatedTo("a", "b", "r1", None, Direction.OUTGOING),
      RelatedTo("a", "c", "r2", None, Direction.OUTGOING)
    )
    val matchingContext = new MatchingContext(patterns, bind("a"))

    assertMatches(matchingContext.getMatches(Map("a" -> a)), 2,
      Map("a" -> a, "b" -> c, "c" -> b, "r1" -> r2, "r2" -> r1),
      Map("a" -> a, "b" -> b, "c" -> c, "r1" -> r1, "r2" -> r2))
  }

  @Test def theDreadedDiamondTest() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    val d = createNode()

    val r1 = relate(a, b, "x")
    val r2 = relate(a, c, "x")
    val r3 = relate(b, d, "x")
    val r4 = relate(c, d, "x")

    val patterns: Seq[Pattern] = Seq(
      RelatedTo("a", "b", "r1", None, Direction.OUTGOING),
      RelatedTo("a", "c", "r2", None, Direction.OUTGOING),
      RelatedTo("b", "d", "r3", None, Direction.OUTGOING),
      RelatedTo("c", "d", "r4", None, Direction.OUTGOING)
    )

    val matchingContext = new MatchingContext(patterns, bind("a"))

    assertMatches(matchingContext.getMatches(Map("a" -> a)), 2,
      Map("a" -> a, "b" -> b, "c" -> c, "d" -> d, "r1" -> r1, "r2" -> r2, "r3" -> r3, "r4" -> r4),
      Map("a" -> a, "b" -> c, "c" -> b, "d" -> d, "r1" -> r2, "r2" -> r1, "r3" -> r4, "r4" -> r3))
  }


  @Test def pinnedNodeMakesNoMatchesInDisjunctGraph() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    relate(a, b, "rel")

    val patterns: Seq[Pattern] = Seq(RelatedTo("a", "c", "r", "rel", Direction.OUTGOING))
    val matchingContext = new MatchingContext(patterns, bind("a", "c"))

    assertMatches(matchingContext.getMatches(Map("a" -> a, "c" -> c)), 0)
  }

  @Test def pinnedNodeMakesNoMatches() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    val d = createNode()

    val r1 = relate(a, b, "x")
    val r2 = relate(a, c, "x")
    val r3 = relate(b, d, "x")
    val r4 = relate(c, d, "x")

    val patterns: Seq[Pattern] = Seq(
      RelatedTo("a", "b", "r1", None, Direction.OUTGOING),
      RelatedTo("a", "c", "r2", None, Direction.OUTGOING),
      RelatedTo("b", "d", "r3", None, Direction.OUTGOING),
      RelatedTo("c", "d", "r4", None, Direction.OUTGOING)
    )
    val matchingContext = new MatchingContext(patterns, bind("a", "b"))

    assertMatches(matchingContext.getMatches(Map("a" -> a, "b" -> b)), 1,
      Map("a" -> a, "b" -> b, "c" -> c, "d" -> d, "r1" -> r1, "r2" -> r2, "r3" -> r3, "r4" -> r4))
  }

  @Test def directionConstraintFiltersMatches() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    val r1 = relate(a, b, "rel")
    val r2 = relate(c, a, "rel")

    val patterns: Seq[Pattern] = Seq(RelatedTo("a", "b", "r", "rel", Direction.OUTGOING))
    val matchingContext = new MatchingContext(patterns, bind("a"))

    assertMatches(matchingContext.getMatches(Map("a" -> a)), 1, Map("a" -> a, "b" -> b, "r" -> r1))
    assertMatches(matchingContext.getMatches(Map("b" -> a)), 1, Map("b" -> a, "a" -> c, "r" -> r2))
  }

  @Test def typeConstraintFiltersMatches() {
    val a = createNode()
    val b = createNode()
    val r1 = relate(a, b, "t1")
    relate(a, b, "t2")

    val patterns: Seq[Pattern] = Seq(RelatedTo("a", "b", "r", "t1", Direction.OUTGOING))
    val matchingContext = new MatchingContext(patterns, bind("a"))

    assertMatches(matchingContext.getMatches(Map("a" -> a)), 1, Map("a" -> a, "b" -> b, "r" -> r1))
  }

  @Test def variableLengthPath() {

    val a = createNode()
    val b = createNode()
    val c = createNode()
    relate(a, b, "rel")
    relate(b, c, "rel")

    val patterns: Seq[Pattern] = Seq(VariableLengthPath("p", "a", "c", 1, 2, "rel", Direction.OUTGOING))
    val matchingContext = new MatchingContext(patterns, bind("a"))

    assertMatches(matchingContext.getMatches(Map("a" -> a)), 2, Map("a" -> a, "c" -> b), Map("a" -> a, "c" -> c))
  }

  @Test def variableLengthPathWithOneHopBefore() {

    val a = createNode()
    val b = createNode()
    val c = createNode()
    val d = createNode()
    val r1 = relate(a, b, "rel")
    relate(b, c, "rel")
    relate(c, d, "rel")

    val patterns: Seq[Pattern] = Seq(
      RelatedTo("a", "b", "r1", "rel", Direction.OUTGOING),
      VariableLengthPath("p", "b", "c", 1, 2, "rel", Direction.OUTGOING))
    val matchingContext = new MatchingContext(patterns, bind("a"))

    assertMatches(matchingContext.getMatches(Map("a" -> a)), 2, Map("a" -> a, "r1" -> r1, "b" -> b, "c" -> c), Map("a" -> a, "r1" -> r1, "b" -> b, "c" -> d))
  }

  @Test def variableLengthPathWithOneHopBeforeWithDifferentType() {

    val a = createNode()
    val b = createNode()
    val c = createNode()
    val d = createNode()
    val r1 = relate(a, b, "t1")
    relate(b, c, "t1")
    relate(c, d, "t2")

    val patterns: Seq[Pattern] = Seq(
      RelatedTo("a", "b", "r1", "t1", Direction.OUTGOING),
      VariableLengthPath("p", "b", "c", 1, 2, "t1", Direction.OUTGOING))
    val matchingContext = new MatchingContext(patterns, bind("a"))

    assertMatches(matchingContext.getMatches(Map("a" -> a)), 1, Map("a" -> a, "r1" -> r1, "b" -> b, "c" -> c))
  }

  @Test def variableLengthPathWithBranch() {

    val a = createNode()
    val b = createNode()
    val c = createNode()
    val d = createNode()
    relate(a, b, "t1")
    relate(b, c, "t1")
    relate(b, d, "t1")

    val patterns: Seq[Pattern] = Seq(
      VariableLengthPath("p", "a", "x", 1, 2, "t1", Direction.OUTGOING))
    val matchingContext = new MatchingContext(patterns, bind("a"))

    assertMatches(matchingContext.getMatches(Map("a" -> a)), 3, Map("a" -> a, "x" -> b), Map("a" -> a, "x" -> c), Map("a" -> a, "x" -> d))
  }

  @Test def variableLengthPathWithPinnedEndNode() {

    val a = createNode()
    val b = createNode()
    val c = createNode()
    val d = createNode()
    relate(a, b, "t1")
    relate(b, c, "t1")
    relate(b, d, "t1")

    val patterns: Seq[Pattern] = Seq(
      VariableLengthPath("p", "a", "x", 1, 2, "t1", Direction.OUTGOING))
    val matchingContext = new MatchingContext(patterns, bind("a", "x"))

    assertMatches(matchingContext.getMatches(Map("a" -> a, "x" -> d)), 1, Map("a" -> a, "x" -> d))
  }

  @Test def variableLengthPathInDiamond() {

    val a = createNode()
    val b = createNode()
    val c = createNode()
    val d = createNode()
    val r1 = relate(a, b, "rel")
    val r2 = relate(a, c, "rel")
    relate(b, d, "rel")
    relate(c, d, "rel")
    relate(b, c, "rel")

    val patterns: Seq[Pattern] = Seq(
      RelatedTo("a", "b", "r1", "rel", Direction.OUTGOING),
      RelatedTo("a", "c", "r2", "rel", Direction.OUTGOING),
      VariableLengthPath("p", "b", "c", 1, 3, "rel", Direction.BOTH))
    val matchingContext = new MatchingContext(patterns, bind("a"))

    assertMatches(matchingContext.getMatches(Map("a" -> a)), 4, Map("a" -> a, "r1" -> r1, "b" -> b, "c" -> c, "r2" -> r2), Map("a" -> a, "r1" -> r2, "b" -> c, "c" -> b, "r2" -> r1))
  }

  @Test
  @Ignore def zeroLengthVariableLengthPatternNotAllowed() {
    // TBD?
  }

  def bind(boundSymbols: String*): SymbolTable = {
    val toSet = boundSymbols.map(NodeIdentifier(_))
    new SymbolTable(toSet)
  }

  def assertMatches(matches: Traversable[Map[String, Any]], expectedSize: Int, expected: Map[String, Any]*) {
    val matchesList = matches.toList
    assert(matchesList.size === expectedSize)

    expected.foreach(expectation => {
      if (!matches.exists(compare(_, expectation)))
        throw new Exception("Didn't find the expected row: " + expectation)
    })

  }

  def compare(matches: Map[String, Any], expecations: Map[String, Any]): Boolean = {
    expecations.foreach(kv =>
      matches.get(kv._1) match {
        case None => return false
        case Some(x) => if (x != kv._2) return false
      })

    true
  }


}