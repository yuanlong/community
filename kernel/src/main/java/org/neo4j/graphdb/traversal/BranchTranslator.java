package org.neo4j.graphdb.traversal;

import org.neo4j.graphdb.Path;

public interface BranchTranslator
{
    Path translate( TraversalBranch branch );
}
