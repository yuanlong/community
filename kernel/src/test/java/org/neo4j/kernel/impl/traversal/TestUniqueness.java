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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.neo4j.graphdb.Direction.OUTGOING;
import static org.neo4j.graphdb.DynamicRelationshipType.withName;
import static org.neo4j.graphdb.traversal.Evaluators.includeWhereEndNodeIs;
import static org.neo4j.kernel.Traversal.traversal;
import static org.neo4j.kernel.Uniqueness.NODE_LEVEL;

import org.junit.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.traversal.Traverser;

public class TestUniqueness extends AbstractTestBase
{
    @Test
    public void levelUniqueness() throws Exception
    {
        /**
         *         (b)
         *       /  |  \
         *    (e)==(a)--(c)
         *       \  |
         *         (d)
         */
        
        createGraph( "a TO b", "a TO c", "a TO d", "a TO e", "a TO e", "b TO e", "d TO e", "c TO b" );
        RelationshipType to = withName( "TO" );
        Node a = getNodeWithName( "a" );
        Node e = getNodeWithName( "e" );
        Path[] paths = splitPathsOnePerLevel( traversal().relationships( to, OUTGOING )
                .uniqueness( NODE_LEVEL ).evaluator( includeWhereEndNodeIs( e ) ).traverse( a ) );
        NodePathRepresentation pathRepresentation = new NodePathRepresentation( NAME_PROPERTY_REPRESENTATION );
        assertEquals( "a,e", pathRepresentation.represent( paths[1] ) );
        String levelTwoPathRepresentation = pathRepresentation.represent( paths[2] );
        assertTrue( levelTwoPathRepresentation.equals( "a,b,e" ) || levelTwoPathRepresentation.equals( "a,d,e" ) );
        assertEquals( "a,c,b,e", pathRepresentation.represent( paths[3] ) );
    }
    
    private Path[] splitPathsOnePerLevel( Traverser traverser )
    {
        Path[] paths = new Path[10];
        for ( Path path : traverser )
        {
            int depth = path.length();
            if ( paths[depth] != null )
            {
                fail( "More than one path one depth " + depth );
            }
            paths[depth] = path;
        }
        return paths;
    }
}
