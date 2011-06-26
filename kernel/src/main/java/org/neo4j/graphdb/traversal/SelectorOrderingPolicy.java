package org.neo4j.graphdb.traversal;

public interface SelectorOrderingPolicy
{
    SelectorOrderer create( BranchSelector start, BranchSelector end );
}
