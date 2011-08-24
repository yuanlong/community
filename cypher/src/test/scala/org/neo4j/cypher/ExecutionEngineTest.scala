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
package org.neo4j.cypher

import org.neo4j.cypher.commands._
import org.junit.Assert._
import java.lang.String
import org.junit.{Ignore, Test}
import parser.CypherParser
import scala.collection.JavaConverters._
import org.junit.matchers.JUnitMatchers._
import org.neo4j.graphdb.{Path, Relationship, Direction, Node}

class ExecutionEngineTest extends ExecutionEngineTestBase {

  @Test def shouldGetReferenceNode() {
    val query = Query.
      start(NodeById("node", 0)).
      returns(ValueReturnItem(EntityValue("node")))

    val result = execute(query)
    assertEquals(List(refNode), result.columnAs[Node]("node").toList)
  }

  @Test def shouldFilterOnGreaterThan() {
    val query = Query.
      start(NodeById("node", 0)).
      where(LessThan(Literal(0), Literal(1))).
      returns(ValueReturnItem(EntityValue("node")))


    val result = execute(query)
    assertEquals(List(refNode), result.columnAs[Node]("node").toList)
  }

  @Test def shouldFilterOnRegexp() {
    val n1 = createNode(Map("name" -> "Andres"))
    val n2 = createNode(Map("name" -> "Jim"))
    val query = Query.
      start(NodeById("node", n1.getId, n2.getId)).
      where(RegularExpression(PropertyValue("node", "name"), "And.*")).
      returns(ValueReturnItem(EntityValue("node")))

    val result = execute(query)
    assertEquals(List(n1), result.columnAs[Node]("node").toList)
  }

  @Test def shouldGetOtherNode() {
    val node: Node = createNode()

    val query = Query.
      start(NodeById("node", node.getId)).
      returns(ValueReturnItem(EntityValue("node")))

    val result = execute(query)
    assertEquals(List(node), result.columnAs[Node]("node").toList)
  }

  @Ignore("graph-matching doesn't support using relationships as start points. revisit when it does.")
  @Test def shouldGetRelationship() {
    val node: Node = createNode()
    val rel: Relationship = relate(refNode, node, "yo")

    val query = Query.
      start(RelationshipById("rel", rel.getId)).
      returns(ValueReturnItem(EntityValue("rel")))

    val result = execute(query)
    assertEquals(List(rel), result.columnAs[Relationship]("rel").toList)
  }

  @Test def shouldGetTwoNodes() {
    val node: Node = createNode()

    val query = Query.
      start(NodeById("node", refNode.getId, node.getId)).
      returns(ValueReturnItem(EntityValue("node")))

    val result = execute(query)
    assertEquals(List(refNode, node), result.columnAs[Node]("node").toList)
  }

  @Test def shouldGetNodeProperty() {
    val name = "Andres"
    val node: Node = createNode(Map("name" -> name))

    val query = Query.
      start(NodeById("node", node.getId)).
      returns(ValueReturnItem(PropertyValue("node", "name")))

    val result = execute(query)
    val list = result.columnAs[String]("node.name").toList
    assertEquals(List(name), list)
  }

  @Test def shouldFilterOutBasedOnNodePropName() {
    val name = "Andres"
    val start: Node = createNode()
    val a1: Node = createNode(Map("name" -> "Someone Else"))
    val a2: Node = createNode(Map("name" -> name))
    relate(start, a1, "x")
    relate(start, a2, "x")

    val query = Query.
      start(NodeById("start", start.getId)).
      matches(RelatedTo("start", "a", "rel", Some("x"), Direction.BOTH)).
      where(Equals(PropertyValue("a", "name"), Literal(name))).
      returns(ValueReturnItem(EntityValue("a")))

    val result = execute(query)
    assertEquals(List(a2), result.columnAs[Node]("a").toList)
  }

  @Test def shouldFilterBasedOnRelPropName() {
    val start: Node = createNode()
    val a: Node = createNode()
    val b: Node = createNode()
    relate(start, a, "KNOWS", Map("name" -> "monkey"))
    relate(start, b, "KNOWS", Map("name" -> "woot"))

    val query = Query.
      start(NodeById("start", start.getId)).
      matches(RelatedTo("start", "a", "r", Some("KNOWS"), Direction.BOTH)).
      where(Equals(PropertyValue("r", "name"), Literal("monkey"))).
      returns(ValueReturnItem(EntityValue("a")))

    val result = execute(query)
    assertEquals(List(a), result.columnAs[Node]("a").toList)
  }

  @Ignore("Maybe later")
  @Test def shouldOutputTheCartesianProductOfTwoNodes() {
    val n1: Node = createNode()
    val n2: Node = createNode()

    val query = Query.
      start(NodeById("n1", n1.getId), NodeById("n2", n2.getId)).
      returns(ValueReturnItem(EntityValue("n1")), ValueReturnItem(EntityValue("n2")))

    val result = execute(query)

    assertEquals(List(Map("n1" -> n1, "n2" -> n2)), result.toList)
  }

  @Test def shouldGetNeighbours() {
    val n1: Node = createNode()
    val n2: Node = createNode()
    relate(n1, n2, "KNOWS")

    val query = Query.
      start(NodeById("n1", n1.getId)).
      matches(RelatedTo("n1", "n2", "rel", Some("KNOWS"), Direction.OUTGOING)).
      returns(ValueReturnItem(EntityValue("n1")), ValueReturnItem(EntityValue("n2")))

    val result = execute(query)

    assertEquals(List(Map("n1" -> n1, "n2" -> n2)), result.toList)
  }

  @Test def shouldGetTwoRelatedNodes() {
    val n1: Node = createNode()
    val n2: Node = createNode()
    val n3: Node = createNode()
    relate(n1, n2, "KNOWS")
    relate(n1, n3, "KNOWS")

    val query = Query.
      start(NodeById("start", n1.getId)).
      matches(RelatedTo("start", "x", "rel", Some("KNOWS"), Direction.OUTGOING)).
      returns(ValueReturnItem(EntityValue("x")))

    val result = execute(query)

    assertEquals(List(Map("x" -> n2), Map("x" -> n3)), result.toList)
  }

  @Test def executionResultTextualOutput() {
    val n1: Node = createNode()
    val n2: Node = createNode()
    val n3: Node = createNode()
    relate(n1, n2, "KNOWS")
    relate(n1, n3, "KNOWS")

    val query = Query.
      start(NodeById("start", n1.getId)).
      matches(RelatedTo("start", "x", "rel", Some("KNOWS"), Direction.OUTGOING)).
      returns(ValueReturnItem(EntityValue("x")), ValueReturnItem(EntityValue("start")))

    val result = execute(query)

    val textOutput = result.dumpToString()

    println(textOutput)
  }

  @Test def doesNotFailOnVisualizingEmptyOutput() {
    val query = Query.
      start(NodeById("start", refNode.getId)).
      where(Equals(Literal(1), Literal(0))).
      returns(ValueReturnItem(EntityValue("start")))

    val result = execute(query)

    println(result.dumpToString())
  }

  @Test def shouldGetRelatedToRelatedTo() {
    val n1: Node = createNode()
    val n2: Node = createNode()
    val n3: Node = createNode()
    relate(n1, n2, "KNOWS")
    relate(n2, n3, "FRIEND")

    val query = Query.
      start(NodeById("start", n1.getId)).
      matches(
      RelatedTo("start", "a", "rel", Some("KNOWS"), Direction.OUTGOING),
      RelatedTo("a", "b", "rel2", Some("FRIEND"), Direction.OUTGOING)).
      returns(ValueReturnItem(EntityValue("b")))

    val result = execute(query)

    assertEquals(List(Map("b" -> n3)), result.toList)
  }

  @Test def shouldFindNodesByExactIndexLookup() {
    val n = createNode()
    val idxName = "idxName"
    val key = "key"
    val value = "andres"
    indexNode(n, idxName, key, value)

    val query = Query.
      start(NodeByIndex("n", idxName, key, value)).
      returns(ValueReturnItem(EntityValue("n")))

    val result = execute(query)

    assertEquals(List(Map("n" -> n)), result.toList)
  }

  @Test def shouldFindNodesByIndexQuery() {
    val n = createNode()
    val idxName = "idxName"
    val key = "key"
    val value = "andres"
    indexNode(n, idxName, key, value)

    val query = Query.
      start(NodeByIndexQuery("n", idxName, key + ":" + value)).
      returns(ValueReturnItem(EntityValue("n")))

    val result = execute(query)

    assertEquals(List(Map("n" -> n)), result.toList)
  }

  @Test def shouldFindNodesByIndexWildcardQuery() {
    val n = createNode()
    val idxName = "idxName"
    val key = "key"
    val value = "andres"
    indexNode(n, idxName, key, value)

    val query = Query.
      start(NodeByIndexQuery("n", idxName, key + ":andr*")).
      returns(ValueReturnItem(EntityValue("n")))

    val result = execute(query)

    assertEquals(List(Map("n" -> n)), result.toList)
  }

  @Test def shouldHandleOrFilters() {
    val n1 = createNode(Map("name" -> "boy"))
    val n2 = createNode(Map("name" -> "girl"))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId)).
      where(Or(
      Equals(PropertyValue("n", "name"), Literal("boy")),
      Equals(PropertyValue("n", "name"), Literal("girl")))).
      returns(ValueReturnItem(EntityValue("n")))

    val result = execute(query)

    assertEquals(List(n1, n2), result.columnAs[Node]("n").toList)
  }


  @Test def shouldHandleNestedAndOrFilters() {
    val n1 = createNode(Map("animal" -> "monkey", "food" -> "banana"))
    val n2 = createNode(Map("animal" -> "cow", "food" -> "grass"))
    val n3 = createNode(Map("animal" -> "cow", "food" -> "banana"))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId, n3.getId)).
      where(Or(
      And(
        Equals(PropertyValue("n", "animal"), Literal("monkey")),
        Equals(PropertyValue("n", "food"), Literal("banana"))),
      And(
        Equals(PropertyValue("n", "animal"), Literal("cow")),
        Equals(PropertyValue("n", "food"), Literal("grass"))))).
      returns(ValueReturnItem(EntityValue("n")))

    val result = execute(query)

    assertEquals(List(n1, n2), result.columnAs[Node]("n").toList)
  }

  @Test def shouldBeAbleToOutputNullForMissingProperties() {
    val query = Query.
      start(NodeById("node", 0)).
      returns(ValueReturnItem(NullablePropertyValue("node", "name")))

    val result = execute(query)
    assertEquals(List(Map("node.name" -> null)), result.toList)
  }

  @Test def shouldHandleComparisonBetweenNodeProperties() {
    //start n = node(1,4) match (n) --> (x) where n.animal = x.animal return n,x
    val n1 = createNode(Map("animal" -> "monkey"))
    val n2 = createNode(Map("animal" -> "cow"))
    val n3 = createNode(Map("animal" -> "monkey"))
    val n4 = createNode(Map("animal" -> "cow"))

    relate(n1, n2, "A")
    relate(n1, n3, "A")
    relate(n4, n2, "A")
    relate(n4, n3, "A")

    val query = Query.
      start(NodeById("n", n1.getId, n4.getId)).
      matches(RelatedTo("n", "x", "rel", None, Direction.OUTGOING)).
      where(Equals(PropertyValue("n", "animal"), PropertyValue("x", "animal"))).
      returns(ValueReturnItem(EntityValue("n")), ValueReturnItem(EntityValue("x")))

    val result = execute(query)

    assertEquals(List(
      Map("n" -> n1, "x" -> n3),
      Map("n" -> n4, "x" -> n2)), result.toList)

    assertEquals(List("n", "x"), result.columns)
  }

  @Test def comparingNumbersShouldWorkNicely() {
    val n1 = createNode(Map("x" -> 50))
    val n2 = createNode(Map("x" -> 50l))
    val n3 = createNode(Map("x" -> 50f))
    val n4 = createNode(Map("x" -> 50d))
    val n5 = createNode(Map("x" -> 50.toByte))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId, n3.getId, n4.getId, n5.getId)).
      where(LessThan(PropertyValue("n", "x"), Literal(100))).
      returns(ValueReturnItem(EntityValue("n")))

    val result = execute(query)

    assertEquals(List(n1, n2, n3, n4, n5), result.columnAs[Node]("n").toList)
  }

  @Test def comparingStringAndCharsShouldWorkNicely() {
    val n1 = createNode(Map("x" -> "Anders"))
    val n2 = createNode(Map("x" -> 'C'))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId)).
      where(And(
      LessThan(PropertyValue("n", "x"), Literal("Z")),
      LessThan(PropertyValue("n", "x"), Literal('Z')))).
      returns(ValueReturnItem(EntityValue("n")))

    val result = execute(query)

    assertEquals(List(n1, n2), result.columnAs[Node]("n").toList)
  }

  @Test def shouldBeAbleToCountNodes() {
    val a = createNode() //start a = (0) match (a) --> (b) return a, count(*)
    val b = createNode()
    relate(refNode, a, "A")
    relate(refNode, b, "A")

    val query = Query.
      start(NodeById("a", refNode.getId)).
      matches(RelatedTo("a", "b", "rel", None, Direction.OUTGOING)).
      aggregation(CountStar()).
      returns(ValueReturnItem(EntityValue("a")))

    val result = execute(query)

    assertEquals(List(Map("a" -> refNode, "count(*)" -> 2)), result.toList)
  }

  @Test def shouldLimitToTwoHits() {
    createNodes("A", "B", "C", "D", "E")

    val query = Query.
      start(NodeById("start", nodeIds: _*)).
      limit(2).
      returns(ValueReturnItem(EntityValue("start")))

    val result = execute(query)

    assertEquals("Result was not trimmed down", 2, result.size)
  }

  @Test def shouldStartTheResultFromSecondRow() {
    val nodes = createNodes("A", "B", "C", "D", "E")

    val query = Query.
      start(NodeById("start", nodeIds: _*)).
      orderBy(SortItem(ValueReturnItem(PropertyValue("start", "name")), true)).
      skip(2).
      returns(ValueReturnItem(EntityValue("start")))

    val result = execute(query)

    assertEquals(nodes.drop(2).toList, result.columnAs[Node]("start").toList)
  }

  @Test def shouldGetStuffInTheMiddle() {
    val nodes = createNodes("A", "B", "C", "D", "E")

    val query = Query.
      start(NodeById("start", nodeIds: _*)).
      orderBy(SortItem(ValueReturnItem(PropertyValue("start", "name")), true)).
      limit(2).
      skip(2).
      returns(ValueReturnItem(EntityValue("start")))

    val result = execute(query)

    assertEquals(nodes.slice(2, 4).toList, result.columnAs[Node]("start").toList)
  }

  @Test def shouldSortOnAggregatedFunction() {
    val n1 = createNode(Map("name" -> "andres", "divison" -> "Sweden", "age" -> 33))
    val n2 = createNode(Map("name" -> "michael", "divison" -> "Germany", "age" -> 22))
    val n3 = createNode(Map("name" -> "jim", "divison" -> "England", "age" -> 55))
    val n4 = createNode(Map("name" -> "anders", "divison" -> "Sweden", "age" -> 35))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId, n3.getId, n4.getId)).
      aggregation(ValueAggregationItem(Max(PropertyValue("n", "age")))).
      orderBy(SortItem(ValueAggregationItem(Max(PropertyValue("n", "age"))), true)).
      returns(ValueReturnItem(PropertyValue("n", "divison")))

    val result = execute(query)

    assertEquals(List("Germany", "Sweden", "England"), result.columnAs[String]("n.divison").toList)
  }

  @Test def shouldSortOnAggregatedFunctionAndNormalProperty() {
    val n1 = createNode(Map("name" -> "andres", "division" -> "Sweden"))
    val n2 = createNode(Map("name" -> "michael", "division" -> "Germany"))
    val n3 = createNode(Map("name" -> "jim", "division" -> "England"))
    val n4 = createNode(Map("name" -> "mattias", "division" -> "Sweden"))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId, n3.getId, n4.getId)).
      aggregation(CountStar()).
      orderBy(SortItem(CountStar(), false), SortItem(ValueReturnItem(PropertyValue("n", "division")), true)).
      returns(ValueReturnItem(PropertyValue("n", "division")))

    val result = execute(query)

    assertEquals(List("Sweden", "England", "Germany"), result.columnAs[String]("n.division").toList)
    assertEquals(List(2, 1, 1), result.columnAs[Int]("count(*)").toList)
  }

  @Test def magicRelTypeWorksAsExpected() {
    createNodes("A", "B", "C")
    relate("A" -> "KNOWS" -> "B")
    relate("A" -> "HATES" -> "C")

    val query = Query.
      start(NodeById("n", 1)).
      matches(RelatedTo("n", "x", "r", None, Direction.OUTGOING)).
      where(Equals(RelationshipTypeValue("r"), Literal("KNOWS"))).
      returns(ValueReturnItem(EntityValue("x")))

    val result = execute(query)

    assertEquals(List(node("B")), result.columnAs[Node]("x").toList)
  }

  @Test def magicRelTypeOutput() {
    createNodes("A", "B", "C")
    relate("A" -> "KNOWS" -> "B")
    relate("A" -> "HATES" -> "C")

    val query = Query.
      start(NodeById("n", 1)).
      matches(RelatedTo("n", "x", "r", None, Direction.OUTGOING)).
      returns(ValueReturnItem(RelationshipTypeValue("r")))

    val result = execute(query)

    assertEquals(List("KNOWS", "HATES"), result.columnAs[String]("r~TYPE").toList)
  }

  @Test def shouldAggregateOnProperties() {
    val n1 = createNode(Map("x" -> 33))
    val n2 = createNode(Map("x" -> 33))
    val n3 = createNode(Map("x" -> 42))

    val query = Query.
      start(NodeById("node", n1.getId, n2.getId, n3.getId)).
      aggregation(CountStar()).
      returns(ValueReturnItem(PropertyValue("node", "x")))

    val result = execute(query)

    assertThat(result.toList.asJava, hasItems[Map[String, Any]](Map("node.x" -> 33, "count(*)" -> 2), Map("node.x" -> 42, "count(*)" -> 1)))
  }

  @Test def shouldCountNonNullValues() {
    val n1 = createNode(Map("y" -> "a", "x" -> 33))
    val n2 = createNode(Map("y" -> "a"))
    val n3 = createNode(Map("y" -> "b", "x" -> 42))

    val query = Query.
      start(NodeById("node", n1.getId, n2.getId, n3.getId)).
      aggregation(ValueAggregationItem(Count(NullablePropertyValue("node", "x")))).
      returns(ValueReturnItem(PropertyValue("node", "y")))

    val result = execute(query)

    assertThat(result.toList.asJava,
      hasItems[Map[String, Any]](
        Map("node.y" -> "a", "count(node.x)" -> 1),
        Map("node.y" -> "b", "count(node.x)" -> 1)))
  }


  @Test def shouldSumNonNullValues() {
    val n1 = createNode(Map("y" -> "a", "x" -> 33))
    val n2 = createNode(Map("y" -> "a"))
    val n3 = createNode(Map("y" -> "a", "x" -> 42))

    val query = Query.
      start(NodeById("node", n1.getId, n2.getId, n3.getId)).
      aggregation(ValueAggregationItem(Sum(NullablePropertyValue("node", "x")))).
      returns(ValueReturnItem(PropertyValue("node", "y")))

    val result = execute(query)

    assertThat(result.toList.asJava,
      hasItems[Map[String, Any]](Map("node.y" -> "a", "sum(node.x)" -> 75)))
  }

  @Test def shouldWalkAlternativeRelationships() {
    val nodes: List[Node] = createNodes("A", "B", "C")
    relate("A" -> "KNOWS" -> "B")
    relate("A" -> "HATES" -> "C")

    val query = Query.
      start(NodeById("n", 1)).
      matches(RelatedTo("n", "x", "r", None, Direction.OUTGOING)).
      where(Or(Equals(RelationshipTypeValue("r"), Literal("KNOWS")), Equals(RelationshipTypeValue("r"), Literal("HATES")))).
      returns(ValueReturnItem(EntityValue("x")))

    val result = execute(query)

    assertEquals(nodes.slice(1, 3), result.columnAs[Node]("x").toList)
  }

  @Test def shouldReturnASimplePath() {
    createNodes("A", "B")
    val r = relate("A" -> "KNOWS" -> "B")

    val query = Query.
      start(NodeById("a",1)).
      matches(PathItem("p", RelatedTo("a", "b", "rel", None, Direction.OUTGOING))).
      returns(ValueReturnItem(EntityValue("p")))  //  new CypherParser().parse("start a=(1) match p=(a-->b) return p")

    val result = execute(query)

    assertEquals(List(PathImpl(node("A"), r, node("B"))), result.columnAs[Path]("p").toList)
  }

  @Test def shouldReturnAThreeNodePath() {
    createNodes("A", "B", "C")
    val r1 = relate("A" -> "KNOWS" -> "B")
    val r2 = relate("B" -> "KNOWS" -> "C")


    val query = Query.
      start(NodeById("a",1)).
      matches(PathItem("p",
        RelatedTo("a", "b", "rel1", None, Direction.OUTGOING),
        RelatedTo("b", "c", "rel2", None, Direction.OUTGOING) )).
      returns(ValueReturnItem(EntityValue("p")))  //  new CypherParser().parse("start a=(1) match p=(a-->b) return p")

    val result = execute(query)

    assertEquals(List(PathImpl(node("A"), r1, node("B"), r2, node("C"))), result.columnAs[Path]("p").toList)
  }

  @Test def shouldWalkAlternativeRelationships2() {
    val nodes: List[Node] = createNodes("A", "B", "C")
    relate("A" -> "KNOWS" -> "B")
    relate("A" -> "HATES" -> "C")

    val result = parseAndExecute("start n=(1) match (n)-[r]->(x) where r.TYPE='KNOWS' or r.TYPE='HATES' return x")

    assertEquals(nodes.slice(1, 3), result.columnAs[Node]("x").toList)
  }

  @Test def shouldNotReturnAnythingBecausePathLengthDoesntMatch() {
    createNodes("A", "B")
    relate("A" -> "KNOWS" -> "B")

    val result = parseAndExecute("start n=(1) match p = n-->x where p.LENGTH=10 return x")

    assertTrue("Result set should be empty, but it wasn't", result.isEmpty)
  }

  @Test def shouldPassThePathLengthTest() {
    createNodes("A", "B")
    relate("A" -> "KNOWS" -> "B")

    val result = parseAndExecute("start n=(1) match p = n-->x where p.LENGTH=3 return x")

    assertTrue("Result set should not be empty, but it was", !result.isEmpty)
  }

  @Test def shouldReturnPathLength() {
    createNodes("A", "B")
    relate("A" -> "KNOWS" -> "B")

    val result = parseAndExecute("start n=(1) match p = n-->x return p.LENGTH")


    assertEquals(List(3), result.columnAs[Int]("p.LENGTH").toList)
  }

  @Test def shouldThrowNiceErrorMessageWhenPropertyIsMissing() {
    val query = new CypherParser().parse("start n=(0) return n.A_PROPERTY_THAT_IS_MISSING")
    try {
      execute(query).toList
    } catch {
      case x: SyntaxException => assertEquals("n.A_PROPERTY_THAT_IS_MISSING does not exist on Node[0]", x.getMessage)
    }
  }

  private def parseAndExecute(q: String): ExecutionResult = {
    val query = new CypherParser().parse(q)
    execute(query)
  }


}

