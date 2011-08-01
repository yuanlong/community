package org.neo4j.graphalgo.impl.path;

import static org.neo4j.helpers.collection.IteratorUtil.firstOrNull;

import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.TraversalMetadata;
import org.neo4j.graphdb.traversal.Traverser;

public abstract class TraversalPathFinder implements PathFinder<Path>
{
    private Traverser lastTraverser;
    
    @Override
    public Path findSinglePath( Node start, Node end )
    {
        return firstOrNull( findAllPaths( start, end ) );
    }

    @Override
    public Iterable<Path> findAllPaths( Node start, Node end )
    {
        lastTraverser = instantiateTraverser( start, end );
        return lastTraverser;
    }

    protected abstract Traverser instantiateTraverser( Node start, Node end );

    @Override
    public TraversalMetadata metadata()
    {
        if ( lastTraverser == null )
        {
            throw new IllegalStateException( "No traversal have been made" );
        }
        return lastTraverser.metadata();
    }
}
