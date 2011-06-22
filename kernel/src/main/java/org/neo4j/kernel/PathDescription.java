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
package org.neo4j.kernel;

import static java.util.Collections.emptyList;
import static org.neo4j.kernel.Traversal.expanderForTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipExpander;
import org.neo4j.graphdb.RelationshipType;

public class PathDescription
{
    private final List<RelationshipExpander> steps;
    
    public PathDescription()
    {
        this( new ArrayList<RelationshipExpander>() );
    }
    
    private PathDescription( List<RelationshipExpander> steps )
    {
        this.steps = steps;
    }
    
    public PathDescription step( RelationshipType type )
    {
        return step( expanderForTypes( type ) );
    }
    
    public PathDescription step( RelationshipType type, Direction direction )
    {
        return step( expanderForTypes( type, direction ) );
    }
    
    public PathDescription step( RelationshipExpander expander )
    {
        List<RelationshipExpander> newSteps = new ArrayList<RelationshipExpander>( steps );
        newSteps.add( expander );
        return new PathDescription( newSteps );
    }
    
    public RelationshipExpander build()
    {
        return new CrudeAggregatedExpander( steps );
    }
    
    private static class CrudeAggregatedExpander implements RelationshipExpander
    {
        private final List<RelationshipExpander> steps;

        CrudeAggregatedExpander( List<RelationshipExpander> steps )
        {
            this.steps = steps;
        }
        
        @Override
        public Iterable<Relationship> expand( Path path )
        {
            RelationshipExpander expansion;
            try
            {
                expansion = steps.get( path.length() );
            }
            catch ( IndexOutOfBoundsException e )
            {
                return emptyList();
            }
            return expansion.expand( path );
        }

        @Override
        public RelationshipExpander reversed()
        {
            List<RelationshipExpander> reversedSteps = new ArrayList<RelationshipExpander>();
            for ( RelationshipExpander step : steps )
            {
                reversedSteps.add( step.reversed() );
            }
            Collections.reverse( reversedSteps );
            return new CrudeAggregatedExpander( reversedSteps );
        }
    }
}
