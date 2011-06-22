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
package org.neo4j.kernel.impl.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.neo4j.graphdb.DynamicRelationshipType.withName;
import static org.neo4j.helpers.collection.MapUtil.stringMap;
import static org.neo4j.kernel.impl.util.FileUtils.deleteRecursively;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.EmbeddedGraphDatabase;

public class TestInGraphIndex
{
    @Test
    public void makeSureAnInGraphIndexCanBeAssociatedWithAnIndex() throws Exception
    {
        String path = "target/var/ingraphindex";
        deleteRecursively( new File( path ) );
        GraphDatabaseService db = new EmbeddedGraphDatabase( path );
        Index<Node> index = db.index().forNodes( "dummy", stringMap( "provider", DummyInGraphIndexProvider.SERVICE_NAME ) );
        
        RelationshipType type = withName( "type" );
        Transaction tx = db.beginTx();
        Map<Integer, Node> nodes = new HashMap<Integer, Node>();
        for ( int i = 0; i < 10; i++ )
        {
            Node node = db.createNode();
            node.setProperty( "name", "Node " + i );
            db.getReferenceNode().createRelationshipTo( node, type );
            nodes.put( i, node );
        }
        tx.success();
        tx.finish();
        
        assertEquals( nodes.get( 5 ), index.get( "name", "Node " + 5 ).getSingle() );
        db.shutdown();
        
        // Assert that it still associates with that index after a restart
        db = new EmbeddedGraphDatabase( path );
        index = db.index().forNodes( "dummy" );
        Node node = index.get( "name", "Node " + 5 ).getSingle();
        assertEquals( nodes.get( 5 ), node );
        tx = db.beginTx();
        node.setProperty( "name", "something" );
        assertNull( index.get( "name", "Node " + 5 ).getSingle() );
        tx.success();
        tx.finish();
        assertNull( index.get( "name", "Node " + 5 ).getSingle() );
        assertEquals( node, index.get( "name", "something" ).getSingle() );
        db.shutdown();
    }
}
