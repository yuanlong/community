package org.neo4j.graphdb.traversal;

import org.neo4j.graphdb.Direction;

public interface SelectorOrderer extends BranchSelector
{
    /**
     * OUTGOING for start selector, INCOMING for end selector.
     */
    Direction currentSelector();
}
