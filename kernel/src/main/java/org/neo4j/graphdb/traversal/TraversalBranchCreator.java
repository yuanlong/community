package org.neo4j.graphdb.traversal;

import org.neo4j.graphdb.Node;

public interface TraversalBranchCreator
{
    TraversalBranch create( Node node, Node... additionalNodes );
}
