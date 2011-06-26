package org.neo4j.kernel;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.traversal.BranchSelector;
import org.neo4j.graphdb.traversal.SelectorOrderer;
import org.neo4j.graphdb.traversal.TraversalBranch;

public abstract class AbstractSelectorOrderer<T> implements SelectorOrderer
{
    private static final BranchSelector EMPTY_SELECTOR = new BranchSelector()
    {
        @Override
        public TraversalBranch next()
        {
            return null;
        }
    };
    
    private final BranchSelector[] selectors;
    @SuppressWarnings( "unchecked" )
    private final T[] states = (T[]) new Object[2];
    private int selectorIndex;
    
    public AbstractSelectorOrderer( BranchSelector startSelector, BranchSelector endSelector )
    {
        selectors = new BranchSelector[] { startSelector, endSelector };
        states[0] = initialState();
        states[1] = initialState();
    }
    
    protected T initialState()
    {
        return null;
    }
    
    protected void setStateForCurrentSelector( T state )
    {
        states[selectorIndex] = state;
    }
    
    protected T getStateForCurrentSelector()
    {
        return states[selectorIndex];
    }
    
    protected TraversalBranch nextBranchFromCurrentSelector( boolean switchIfExhausted )
    {
        return nextBranchFromSelector( selectors[selectorIndex], switchIfExhausted );
    }
    
    protected TraversalBranch nextBranchFromNextSelector( boolean switchIfExhausted )
    {
        return nextBranchFromSelector( nextSelector(), switchIfExhausted );
    }
    
    private TraversalBranch nextBranchFromSelector( BranchSelector selector,
            boolean switchIfExhausted )
    {
        TraversalBranch result = selector.next();
        if ( result == null )
        {
            selectors[selectorIndex] = EMPTY_SELECTOR;
            if ( switchIfExhausted )
            {
                result = nextSelector().next();
                if ( result == null )
                {
                    selectors[selectorIndex] = EMPTY_SELECTOR;
                }
            }
        }
        return result;
    }
    
    protected BranchSelector nextSelector()
    {
        selectorIndex = (selectorIndex+1)%2;
        BranchSelector selector = selectors[selectorIndex];
        return selector;
    }

    @Override
    public Direction currentSelector()
    {
        return selectorIndex == 0 ? Direction.OUTGOING : Direction.INCOMING;
    }
}
