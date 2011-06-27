package org.neo4j.graphdb.traversal;

public interface TraversalContext extends TraversalMetadata
{
    void relationshipTraversed();
    
    void unnecessaryRelationshipTraversed();
    
    boolean okToProceedFirst( TraversalBranch branch );
    
    boolean okToProceed( TraversalBranch branch );
    
    Evaluation evaluate( TraversalBranch branch );
}
