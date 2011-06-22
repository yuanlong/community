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

import static org.neo4j.graphdb.Direction.OUTGOING;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexImplementation;
import org.neo4j.graphdb.index.RelationshipIndex;

public class DummyInGraphIndexImplementation extends IndexImplementation
{
    private final GraphDatabaseService db;

    public DummyInGraphIndexImplementation( GraphDatabaseService db )
    {
        this.db = db;
    }
    
    @Override
    public String getDataSourceName()
    {
        return null;
    }

    @Override
    public Index<Node> nodeIndex( String indexName, Map<String, String> config )
    {
        return new NodeIndex( indexName );
    }

    @Override
    public RelationshipIndex relationshipIndex( String indexName, Map<String, String> config )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> fillInDefaults( Map<String, String> config )
    {
        return config;
    }

    @Override
    public boolean configMatches( Map<String, String> storedConfig, Map<String, String> config )
    {
        return true;
    }
    
    private class NodeIndex implements Index<Node>
    {
        private final String name;

        NodeIndex( String name )
        {
            this.name = name;
        }
        
        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public void add( Node entity, String key, Object value )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove( Node entity, String key, Object value )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove( Node entity, String key )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove( Node entity )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public IndexHits<Node> get( String key, Object value )
        {
            Collection<Node> hits = new ArrayList<Node>();
            for ( Relationship rel : db.getReferenceNode().getRelationships( OUTGOING ) )
            {
                Node node = rel.getEndNode();
                if ( value.equals( node.getProperty( key ) ) )
                {
                    hits.add( node );
                }
            }
            return new IndexHitsImpl<Node>( hits, hits.size() );
        }

        @Override
        public IndexHits<Node> query( String key, Object queryOrQueryObject )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public IndexHits<Node> query( Object queryOrQueryObject )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Class<Node> getEntityType()
        {
            return Node.class;
        }
    }
}
