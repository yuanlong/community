package org.neo4j.graphdb.traversal;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;

public interface PathCollisionDetector
{
    Iterable<Path> evaluate( TraversalBranch branch, Direction direction );
}
