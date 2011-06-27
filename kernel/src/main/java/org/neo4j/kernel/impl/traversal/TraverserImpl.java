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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchSelector;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.MutableTraversalMetadata;
import org.neo4j.graphdb.traversal.SelectorOrderer;
import org.neo4j.graphdb.traversal.TraversalBranch;
import org.neo4j.graphdb.traversal.TraversalBranchCreator;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.graphdb.traversal.UniquenessFilter;
import org.neo4j.helpers.collection.CombiningIterator;
import org.neo4j.helpers.collection.IterableWrapper;
import org.neo4j.helpers.collection.PrefetchingIterator;
import org.neo4j.kernel.BidirectionalTraversalBranchPath;

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
        return description.selectorOrdering != null ?
                new BidirectionalTraverserIterator() : new TraverserIterator();
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

    class TraverserIterator extends PrefetchingIterator<Path>
            implements TraversalBranchCreator, MutableTraversalMetadata
    {
        final UniquenessFilter uniqueness;
        final BranchSelector selector;
        final TraversalDescriptionImpl description;
        int numberOfPathsReturned;
        int numberOfRelationshipsTraversed;

        TraverserIterator()
        {
            this.description = TraverserImpl.this.description;
            this.uniqueness = description.uniqueness.create( description.uniquenessParameter );
            this.selector = instantiateSelector( description );
        }
        
        @Override
        public int getNumberOfPathsReturned()
        {
            return numberOfPathsReturned;
        }
        
        @Override
        public int getNumberOfRelationshipsTraversed()
        {
            return 0;
        }
        
        @Override
        public void relationshipTraversed()
        {
            numberOfRelationshipsTraversed++;
        }
        
        @Override
        public void unnecessaryRelationshipTraversed()
        {
            numberOfRelationshipsTraversed++;
        }
        
        protected BranchSelector selector()
        {
            return selector;
        }

        protected BranchSelector instantiateSelector( TraversalDescriptionImpl description )
        {
            return description.branchSelector.create(
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
                result = selector.next( this );
                if ( result == null )
                {
                    return null;
                }
                if ( result.evaluation().includes() )
                {
                    numberOfPathsReturned++;
                    return result;
                }
            }
        }
    }
    
    class BidirectionalTraverserIterator extends TraverserIterator
    {
        private final PathCollisionDetector collisionDetector = new PathCollisionDetector();
        private Iterator<Path> foundPaths;
        
        @Override
        protected BranchSelector instantiateSelector( TraversalDescriptionImpl description )
        {
            BranchSelector startSelector = description.branchSelector.create(
                    new AsOneStartBranch( this, startNodes ), this );
            BranchSelector endSelector = description.branchSelector.create(
                    new AsOneStartBranch( this, asList( description.endNode ) ), this );
            return description.selectorOrdering.create( startSelector, endSelector );
        }
        
        @Override
        protected SelectorOrderer selector()
        {
            return (SelectorOrderer) super.selector();
        }

        @Override
        protected Path fetchNextOrNull()
        {
            if ( foundPaths != null )
            {
                if ( foundPaths.hasNext() )
                {
                    System.out.println( "returning path" );
                    numberOfPathsReturned++;
                    return foundPaths.next();
                }
                foundPaths = null;
            }
            
            TraversalBranch result = null;
            SelectorOrderer selector = selector();
            while ( true )
            {
                result = selector.next( this );
                if ( result == null )
                {
                    return null;
                }
                Collection<Path> pathCollisions = collisionDetector.evaluate( result, selector.currentSelector() );
                if ( pathCollisions != null )
                {
                    foundPaths = pathCollisions.iterator();
                    numberOfPathsReturned++;
                    System.out.println( "returning path" );
                    return foundPaths.next();
                }
            }
        }
    }
    
    private static class PathCollisionDetector
    {
        private final Map<Node, Collection<TraversalBranch>[]> paths =
                new HashMap<Node, Collection<TraversalBranch>[]>( 1000 );
                
        @SuppressWarnings( "unchecked" )
        public Collection<Path> evaluate( TraversalBranch branch, Direction direction )
        {
            // [0] for paths from start, [1] for paths from end
            Collection<TraversalBranch>[] pathsHere = paths.get( branch.endNode() );
            int index = direction == Direction.OUTGOING ? 0 : 1;
            if ( pathsHere == null )
            {
                pathsHere = new Collection[] { new ArrayList<TraversalBranch>(),
                        new ArrayList<TraversalBranch>() };
                paths.put( branch.endNode(), pathsHere );
            }
            pathsHere[index].add( branch );
            
            // If there are paths from the other side then include all the
            // combined paths
            Collection<TraversalBranch> otherCollections = pathsHere[index==0?1:0];
            if ( !otherCollections.isEmpty() )
            {
                branch.prune();
                Collection<Path> foundPaths = new ArrayList<Path>();
                for ( TraversalBranch otherPath : otherCollections )
                {
                    otherPath.prune();
                    TraversalBranch startPath = index == 0 ? branch : otherPath;
                    TraversalBranch endPath = index == 0 ? otherPath : branch;
                    BidirectionalTraversalBranchPath path = new BidirectionalTraversalBranchPath( startPath, endPath );
                    foundPaths.add( path );
                }
                return foundPaths;
            }
            return null;
        }
    };
    
    private static class AsOneStartBranch implements TraversalBranch
    {
        private final TraverserIterator traverser;
        private Iterator<TraversalBranch> branches;
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
        public int length()
        {
            return -1;
        }

        @Override
        public Node endNode()
        {
            return null;
        }

        @Override
        public Relationship lastRelationship()
        {
            return null;
        }

        @Override
        public TraversalBranch next( MutableTraversalMetadata metadata )
        {
            if ( branches.hasNext() )
            {
                expanded++;
                return branches.next().next( metadata );
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

        @Override
        public Node startNode()
        {
            return null;
        }

        @Override
        public Iterable<Relationship> relationships()
        {
            return null;
        }

        @Override
        public Iterable<Node> nodes()
        {
            return null;
        }

        @Override
        public Iterator<PropertyContainer> iterator()
        {
            return null;
        }
        
        @Override
        public void prune()
        {
            branches = Collections.<TraversalBranch>emptyList().iterator();
        }
    }
}
