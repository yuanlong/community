/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.traversal;

import java.util.Iterator;
import java.util.LinkedList;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipExpander;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.TraversalBranch;
import org.neo4j.graphdb.traversal.TraversalMetatada;
import org.neo4j.kernel.impl.traversal.TraverserImpl.TraverserIterator;

class TraversalBranchImpl implements TraversalBranch
{
    private static final Iterator<Relationship> PRUNED_ITERATOR = new Iterator<Relationship>()
    {
        @Override
        public boolean hasNext()
        {
            return false;
        }

        @Override
        public Relationship next()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    };
    
    private final TraversalBranch parent;
    private final Node source;
    private Iterator<Relationship> relationships;
    private final Relationship howIGotHere;
    private final int depth;
    final TraverserIterator traverser;
    private int expandedCount;
    private Evaluation evaluation;

    /*
     * For expansion sources for all nodes except the start node
     */
    TraversalBranchImpl( TraverserIterator traverser, TraversalBranch parent, int depth,
            Node source, RelationshipExpander expander, Relationship toHere )
    {
        this.traverser = traverser;
        this.parent = parent;
        this.source = source;
        this.howIGotHere = toHere;
        this.depth = depth;
    }

    @Override
    public String toString()
    {
        return "TraversalBranch[source=" + source + ",howIGotHere=" + howIGotHere + ",depth=" + depth + "]";
    }

    /*
     * For the start node expansion source
     */
    TraversalBranchImpl( TraverserIterator traverser, TraversalBranch parent, Node source,
            RelationshipExpander expander )
    {
        this.traverser = traverser;
        this.parent = parent;
        this.source = source;
        this.howIGotHere = null;
        this.depth = 0;
        this.evaluation = traverser.description.evaluator.evaluate( this );
    }

    private void expandRelationships()
    {
        if ( evaluation.continues() )
        {
            expandRelationshipsWithoutChecks();
        }
        else
        {
            relationships = PRUNED_ITERATOR;
        }
    }
    
    protected void expandRelationshipsWithoutChecks()
    {
        relationships = traverser.description.expander.expand( this ).iterator();
    }

    protected boolean hasExpandedRelationships()
    {
        return relationships != null;
    }

    public void initialize()
    {
        evaluation = traverser.description.evaluator.evaluate( this );
        expandRelationships();
    }

    public TraversalBranch next( TraversalMetatada metadata )
    {
        while ( relationships.hasNext() )
        {
            Relationship relationship = relationships.next();
            if ( relationship.equals( howIGotHere ) )
            {
                continue;
            }
            expandedCount++;
            Node node = relationship.getOtherNode( source );
            TraversalBranch next = new TraversalBranchImpl( traverser, this, depth + 1, node,
                    traverser.description.expander, relationship );
            if ( traverser.okToProceed( next ) )
            {
                next.initialize();
                return next;
            }
        }
        // Just to help GC
        relationships = PRUNED_ITERATOR;
        return null;
    }
    
    @Override
    public void prune()
    {
        relationships = PRUNED_ITERATOR;
    }

    public int length()
    {
        return depth;
    }

    public TraversalBranch parent()
    {
        return this.parent;
    }

    public int expanded()
    {
        return expandedCount;
    }

    public Evaluation evaluation()
    {
        return evaluation;
    }

    public Node startNode()
    {
        return findStartBranch().endNode();
    }

    private TraversalBranch findStartBranch()
    {
        TraversalBranch branch = this;
        while ( branch.length() > 0 )
        {
            branch = branch.parent();
        }
        return branch;
    }

    public Node endNode()
    {
        return source;
    }

    public Relationship lastRelationship()
    {
        return howIGotHere;
    }

    public Iterable<Relationship> relationships()
    {
        LinkedList<Relationship> relationships = new LinkedList<Relationship>();
        TraversalBranch branch = this;
        while ( branch.length() > 0 )
        {
            relationships.addFirst( branch.lastRelationship() );
            branch = branch.parent();
        }
        return relationships;
    }

    public Iterable<Node> nodes()
    {
        LinkedList<Node> nodes = new LinkedList<Node>();
        TraversalBranch branch = this;
        while ( branch.length() > 0 )
        {
            nodes.addFirst( branch.endNode() );
            branch = branch.parent();
        }
        nodes.addFirst( branch.endNode() );
        return nodes;
    }

    public Iterator<PropertyContainer> iterator()
    {
        LinkedList<PropertyContainer> entities = new LinkedList<PropertyContainer>();
        TraversalBranch branch = this;
        while ( branch.length() > 0 )
        {
            entities.addFirst( branch.endNode() );
            entities.addFirst( branch.lastRelationship() );
            branch = branch.parent();
        }
        entities.addFirst( branch.endNode() );
        return entities.iterator();
    }
    
    @Override
    public int hashCode()
    {
        TraversalBranch branch = this;
        int hashCode = 1;
        while ( branch.length() > 0 )
        {
            Relationship relationship = branch.lastRelationship();
            hashCode = 31*hashCode + relationship.hashCode();
            branch = branch.parent();
        }
        if ( hashCode == 1 )
        {
            hashCode = endNode().hashCode();
        }
        return hashCode;
    }
    
    @Override
    public boolean equals( Object obj )
    {
        if ( obj == this)
        {
            return true;
        }
        if ( !( obj instanceof TraversalBranch ) )
        {
            return false;
        }

        TraversalBranch branch = this;
        TraversalBranch other = (TraversalBranch) obj;
        if ( branch.length() != other.length() )
        {
            return false;
        }
        
        while ( branch.length() > 0 )
        {
            if ( !branch.lastRelationship().equals( other.lastRelationship() ) )
            {
                return false;
            }
            branch = branch.parent();
            other = other.parent();
        }
        return true;
    }
}
