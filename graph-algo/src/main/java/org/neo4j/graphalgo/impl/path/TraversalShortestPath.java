package org.neo4j.graphalgo.impl.path;

import static org.neo4j.helpers.collection.IteratorUtil.firstOrNull;
import static org.neo4j.kernel.Traversal.levelSelectorOrdering;
import static org.neo4j.kernel.Traversal.traversal;
import static org.neo4j.kernel.Uniqueness.RELATIONSHIP_GLOBAL;

import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.RelationshipExpander;

public class TraversalShortestPath implements PathFinder<Path>
{
    private final RelationshipExpander expander;

    public TraversalShortestPath( RelationshipExpander expander )
    {
        this.expander = expander;
    }
    
    @Override
    public Path findSinglePath( Node start, Node end )
    {
        return firstOrNull( findAllPaths( start, end ) );
    }

    @Override
    public Iterable<Path> findAllPaths( final Node start, final Node end )
    {
        return traversal().breadthFirst().uniqueness( RELATIONSHIP_GLOBAL ).expand( expander )
                .bidirectional( levelSelectorOrdering(), end ).traverse( start );
    }
}
