package org.neo4j.kernel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.PathCollisionDetector;
import org.neo4j.graphdb.traversal.TraversalBranch;

public class AbstractPathCollisionDetector implements PathCollisionDetector
{
    private final Map<Node, Collection<TraversalBranch>[]> paths =
            new HashMap<Node, Collection<TraversalBranch>[]>( 1000 );

    @SuppressWarnings( "unchecked" )
    public Collection<Path> evaluate( TraversalBranch branch, Direction direction )
    {
        // [0] for paths from start, [1] for paths from end
        Collection<TraversalBranch>[] pathsHere = paths.get( branch.endNode() );
        int index = direction == Direction.OUTGOING ? 0 : 1;
        if ( pathsHere == null )
        {
            pathsHere = new Collection[]
            { new ArrayList<TraversalBranch>(), new ArrayList<TraversalBranch>() };
            paths.put( branch.endNode(), pathsHere );
        }
        pathsHere[index].add( branch );

        // If there are paths from the other side then include all the
        // combined paths
        Collection<TraversalBranch> otherCollections = pathsHere[index == 0 ? 1 : 0];
        if ( !otherCollections.isEmpty() )
        {
            pathFoundFrom( branch );
            Collection<Path> foundPaths = new ArrayList<Path>();
            for ( TraversalBranch otherBranch : otherCollections )
            {
                pathFoundTo( otherBranch );
                TraversalBranch startPath = index == 0 ? branch : otherBranch;
                TraversalBranch endPath = index == 0 ? otherBranch : branch;
                BidirectionalTraversalBranchPath path = new BidirectionalTraversalBranchPath(
                        startPath, endPath );
                if ( includePath( path ) )
                {
                    foundPaths.add( path );
                }
            }
            
            if ( !foundPaths.isEmpty() )
            {
                return foundPaths;
            }
        }
        return null;
    }

    protected boolean includePath( BidirectionalTraversalBranchPath path )
    {
        return true;
    }

    protected void pathFoundFrom( TraversalBranch branch )
    {
    }

    protected void pathFoundTo( TraversalBranch otherBranch )
    {
    }
}
