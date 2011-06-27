package org.neo4j.kernel;

import org.neo4j.graphdb.traversal.TraversalBranch;

public class ShortestPathCollisionDetector extends AbstractPathCollisionDetector
{
    private int depth = -1;
    
    @Override
    protected boolean includePath( BidirectionalTraversalBranchPath path )
    {
        if ( depth == -1 )
        {
            depth = path.length();
            return true;
        }
        return path.length() == depth;
    }

    @Override
    protected void pathFoundFrom( TraversalBranch branch )
    {
        branch.prune();
    }

    @Override
    protected void pathFoundTo( TraversalBranch otherBranch )
    {
        otherBranch.prune();
    }
}
