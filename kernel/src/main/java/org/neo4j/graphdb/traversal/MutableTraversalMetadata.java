package org.neo4j.graphdb.traversal;

public interface MutableTraversalMetadata extends TraversalMetadata
{
    void relationshipTraversed();
    
    void unnecessaryRelationshipTraversed();
}
