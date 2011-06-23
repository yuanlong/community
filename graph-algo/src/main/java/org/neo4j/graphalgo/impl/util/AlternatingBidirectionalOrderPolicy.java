package org.neo4j.graphalgo.impl.util;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.traversal.BranchOrderingPolicy;
import org.neo4j.graphdb.traversal.BranchSelector;
import org.neo4j.graphdb.traversal.TraversalBranch;
import org.neo4j.graphdb.traversal.TraversalBranchCreator;

public class AlternatingBidirectionalOrderPolicy implements BranchOrderingPolicy
{
    private final BranchOrderingPolicy startPolicy;
    private final BranchOrderingPolicy endPolicy;
    private final Node endNode;

    public AlternatingBidirectionalOrderPolicy( BranchOrderingPolicy startPolicy,
            BranchOrderingPolicy endPolicy, Node endNode )
    {
        this.startPolicy = startPolicy;
        this.endPolicy = endPolicy;
        this.endNode = endNode;
    }
    
    @Override
    public BranchSelector create( final TraversalBranch startBranch,
            final TraversalBranchCreator branchCreator )
    {
        return new BranchSelector()
        {
            private final BranchSelector[] selectors = new BranchSelector[] {
                    startPolicy.create( startBranch, branchCreator ),
                    endPolicy.create( branchCreator.create( endNode ), branchCreator )
            };
            private int selectorIndex;
            
            @Override
            public TraversalBranch next()
            {
                TraversalBranch result = nextSelector().next();
                return result != null ? result : nextSelector().next();
            }

            private BranchSelector nextSelector()
            {
                BranchSelector selector = selectors[selectorIndex];
                selectorIndex = (selectorIndex+1)%2;
                return selector;
            }
        };
    }
}
