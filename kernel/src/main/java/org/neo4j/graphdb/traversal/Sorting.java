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
package org.neo4j.graphdb.traversal;

import java.util.Comparator;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;

public abstract class Sorting
{
    // No instances
    private Sorting()
    {
    }
    
    public static Comparator<? super Path> endNodeProperty( final String propertyKey )
    {
        return new EndNodeComparator()
        {
            @SuppressWarnings( { "rawtypes", "unchecked" } )
            @Override
            protected int compareNodes( Node endNode1, Node endNode2 )
            {
                Comparable p1 = (Comparable) endNode1.getProperty( propertyKey );
                Comparable p2 = (Comparable) endNode2.getProperty( propertyKey );
                if ( p1 == p2 )
                {
                    return 0;
                }
                else if ( p1 == null )
                {
                    return Integer.MIN_VALUE;
                }
                else if ( p2 == null )
                {
                    return Integer.MAX_VALUE;
                }
                else
                {
                    return p1.compareTo( p2 );
                }
            }
        };
    }
    
    private static abstract class EndNodeComparator implements Comparator<Path>
    {
        @Override
        public int compare( Path p1, Path p2 )
        {
            return compareNodes( p1.endNode(), p2.endNode() );
        }

        protected abstract int compareNodes( Node endNode1, Node endNode2 );
    }
}
