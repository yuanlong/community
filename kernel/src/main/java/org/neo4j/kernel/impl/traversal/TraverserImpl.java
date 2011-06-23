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

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchSelector;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.TraversalBranch;
import org.neo4j.graphdb.traversal.TraversalBranchCreator;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.UniquenessFilter;
import org.neo4j.helpers.collection.CombiningIterator;
import org.neo4j.helpers.collection.IterableWrapper;
import org.neo4j.helpers.collection.PrefetchingIterator;

class TraverserImpl implements Traverser
{
    private final TraversalDescriptionImpl description;
    final Collection<Node> startNodes;

    TraverserImpl( TraversalDescriptionImpl description, Collection<Node> startNodes )
    {
        this.description = description;
        this.startNodes = startNodes;
    }

    public Iterator<Path> iterator()
    {
        return new TraverserIterator();
    }

    public Iterable<Node> nodes()
    {
        return new IterableWrapper<Node, Path>( this )
        {
            @Override
            protected Node underlyingObjectToObject( Path position )
            {
                return position.endNode();
            }
        };
    }

    public Iterable<Relationship> relationships()
    {
        return new IterableWrapper<Relationship, Path>( this )
        {
            @Override
            public Iterator<Relationship> iterator()
            {
                Iterator<Relationship> iter = super.iterator();
                if ( iter.hasNext() )
                {
                    Relationship first = iter.next();
                    // If the first position represents the start node, the
                    // first relationship will be null, in that case skip it.
                    if ( first == null ) return iter;
                    // Otherwise re-include it.
                    return new CombiningIterator<Relationship>( first, iter );
                }
                else
                {
                    return iter;
                }
            }

            @Override
            protected Relationship underlyingObjectToObject( Path position )
            {
                return position.lastRelationship();
            }
        };
    }

    class TraverserIterator extends PrefetchingIterator<Path> implements TraversalBranchCreator
    {
        final UniquenessFilter uniqueness;
        private final BranchSelector sourceSelector;
        final TraversalDescriptionImpl description;

        TraverserIterator()
        {
            this.description = TraverserImpl.this.description;
            this.uniqueness = description.uniqueness.create( description.uniquenessParameter );
            this.sourceSelector = description.branchSelector.create(
                    new AsOneStartBranch( this, startNodes ), this );
        }

        boolean okToProceedFirst( TraversalBranch source )
        {
            return this.uniqueness.checkFirst( source );
        }

        boolean okToProceed( TraversalBranch source )
        {
            return this.uniqueness.check( source );
        }
        
        @Override
        public TraversalBranch create( Node node, Node... additionalNodes )
        {
            Collection<Node> nodes = new ArrayList<Node>( additionalNodes.length+1 );
            nodes.add( node );
            nodes.addAll( asList( additionalNodes ) );
            return new AsOneStartBranch( this, nodes );
        }

        @Override
        protected Path fetchNextOrNull()
        {
            TraversalBranch result = null;
            while ( true )
            {
                result = sourceSelector.next();
                if ( result == null )
                {
                    return null;
                }
                if ( result.evaluation().includes() )
                {
                    return result.position();
                }
            }
        }
    }
    
    private static class AsOneStartBranch implements TraversalBranch
    {
        private final TraverserIterator traverser;
        private final Iterator<TraversalBranch> branches;
        private int expanded;

        AsOneStartBranch( TraverserIterator traverser, Collection<Node> nodes )
        {
            this.traverser = traverser;
            this.branches = toBranches( nodes );
        }
        
        private Iterator<TraversalBranch> toBranches( Collection<Node> nodes )
        {
            Collection<TraversalBranch> branches = new ArrayList<TraversalBranch>();
            for ( Node node : nodes )
            {
                TraversalBranch branch = new StartNodeTraversalBranch( traverser, this, node,
                        traverser.description.expander );
                branches.add( branch );
            }
            return branches.iterator();
        }

        @Override
        public TraversalBranch parent()
        {
            return null;
        }

        @Override
        public Path position()
        {
            return null;
        }

        @Override
        public int depth()
        {
            return -1;
        }

        @Override
        public Node node()
        {
            return null;
        }

        @Override
        public Relationship relationship()
        {
            return null;
        }

        @Override
        public TraversalBranch next()
        {
            if ( branches.hasNext() )
            {
                expanded++;
                return branches.next().next();
            }
            return null;
        }

        @Override
        public int expanded()
        {
            return expanded;
        }

        @Override
        public Evaluation evaluation()
        {
            return Evaluation.EXCLUDE_AND_CONTINUE;
        }

        @Override
        public void initialize()
        {
        }
    }
}
