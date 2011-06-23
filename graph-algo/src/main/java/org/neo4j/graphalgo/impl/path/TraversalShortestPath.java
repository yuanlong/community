package org.neo4j.graphalgo.impl.path;

import static org.neo4j.helpers.collection.IteratorUtil.firstOrNull;
import static org.neo4j.kernel.Traversal.preorderBreadthFirst;
import static org.neo4j.kernel.Traversal.traversal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphalgo.impl.util.AlternatingBidirectionalOrderPolicy;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.kernel.Uniqueness;

public class TraversalShortestPath implements PathFinder<Path>
{
    @Override
    public Path findSinglePath( Node start, Node end )
    {
        return firstOrNull( findAllPaths( start, end ) );
    }

    @Override
    public Iterable<Path> findAllPaths( final Node start, final Node end )
    {
        final Collection<Path> foundPaths = new ArrayList<Path>();
        Evaluator pathCollisionEvaluator = new Evaluator()
        {
            Map<Node, Collection<Path>[]> paths = new HashMap<Node, Collection<Path>[]>( 1000 );
            
            @SuppressWarnings( "unchecked" )
            @Override
            public Evaluation evaluate( Path path )
            {
                // [0] for paths from start, [1] for paths from end
                Collection<Path>[] pathsHere = paths.get( path.endNode() );
                int index = path.startNode().equals( start ) ? 0 : 1;
                if ( pathsHere == null )
                {
                    pathsHere = new Collection[] { new ArrayList<Path>(), new ArrayList<Path>() };
                }
                
                Collection<Path> collection = pathsHere[index];
                if ( !collection.isEmpty() )
                {
                    int length = path.length();
                    removePathsMoreShallowThan( collection, length );
                }
                collection.add( path );
            }

            private void removePathsMoreShallowThan( Collection<Path> paths, int length )
            {
                for ( Path path : paths.toArray( new Path[paths.size()]) )
                {
                    if ( path.length() < length )
                    {
                        paths.remove( path );
                    }
                }
            }
        };
        
        return traversal().order( new AlternatingBidirectionalOrderPolicy(
                preorderBreadthFirst(), preorderBreadthFirst(), end ) )
                .uniqueness( Uniqueness.RELATIONSHIP_GLOBAL )
                .evaluator( pathCollisionEvaluator ).traverse( start );
    }
}
