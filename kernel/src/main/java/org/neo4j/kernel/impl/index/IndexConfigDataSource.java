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

import static org.neo4j.kernel.impl.index.IndexDefineCommand.entityType;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.helpers.UTF8;
import org.neo4j.kernel.Config;
import org.neo4j.kernel.impl.index.IndexCommand.CreateCommand;
import org.neo4j.kernel.impl.index.IndexCommand.DeleteCommand;
import org.neo4j.kernel.impl.transaction.xaframework.LogBackedXaDataSource;
import org.neo4j.kernel.impl.transaction.xaframework.XaCommand;
import org.neo4j.kernel.impl.transaction.xaframework.XaCommandFactory;
import org.neo4j.kernel.impl.transaction.xaframework.XaConnectionHelpImpl;
import org.neo4j.kernel.impl.transaction.xaframework.XaContainer;
import org.neo4j.kernel.impl.transaction.xaframework.XaLogicalLog;
import org.neo4j.kernel.impl.transaction.xaframework.XaResourceHelpImpl;
import org.neo4j.kernel.impl.transaction.xaframework.XaResourceManager;
import org.neo4j.kernel.impl.transaction.xaframework.XaTransaction;
import org.neo4j.kernel.impl.transaction.xaframework.XaTransactionFactory;

public class IndexConfigDataSource extends LogBackedXaDataSource
{
    public static final String DATA_SOURCE_NAME = "idxcfg";
    public static final byte[] BRANCH_ID = UTF8.encode( "123456" );
    
    private final XaContainer xaContainer;
    private final IndexProviderStore indexTxStore;
    private final IndexStore indexStore;
    
    public IndexConfigDataSource( Map<Object, Object> config ) throws InstantiationException
    {
        super( config );
        String storeDir = (String) config.get( "store_dir" );
        String logFileName = new File( storeDir, "index_config_log" ).getAbsolutePath();
        xaContainer = XaContainer.create( this, logFileName,
                new CommandFactory(), new TransactionFactory(), config );
        indexTxStore = new IndexProviderStore( new File( storeDir, "indexconfig" ) );
        indexStore = (IndexStore) config.get( IndexStore.class );

        boolean isReadOnly = Boolean.parseBoolean( (String) config.get( Config.READ_ONLY ) );
        if ( !isReadOnly )
        {
            try
            {
                xaContainer.openLogicalLog();
            }
            catch ( IOException e )
            {
                throw new RuntimeException( "Unable to open log " + logFileName, e );
            }
            
            xaContainer.getLogicalLog().setKeepLogs(
                    shouldKeepLog( (String) config.get( Config.KEEP_LOGICAL_LOGS ), DATA_SOURCE_NAME ) );
            setLogicalLogAtCreationTime( xaContainer.getLogicalLog() );
        }
    }
    
    @Override
    public long getLastCommittedTxId()
    {
        return indexTxStore.getLastCommittedTx();
    }
    
    @Override
    public void setLastCommittedTxId( long txId )
    {
        indexTxStore.setLastCommittedTx( txId );
    }

    @Override
    public XaContainer getXaContainer()
    {
        return xaContainer;
    }
    
    @Override
    public IndexConfigConnection getXaConnection()
    {
        return new IndexConfigConnection( xaContainer.getResourceManager(), getBranchId() );
    }

    @Override
    public void close()
    {
        xaContainer.close();
        indexTxStore.close();
    }
    
    public static class IndexConfigConnection extends XaConnectionHelpImpl
    {
        private final Resource resource;
        
        IndexConfigConnection( XaResourceManager xaRm, byte branchId[] )
        {
            super( xaRm );
            resource = new Resource( "yoyoyo", xaRm, branchId );
        }

        @Override
        public XAResource getXaResource()
        {
            return resource;
        }
        
        @Override
        protected IndexConfigTransaction getTransaction()
        {
            try
            {
                return (IndexConfigTransaction) super.getTransaction();
            }
            catch ( XAException e )
            {
                throw new RuntimeException( e );
            }
        }

        public void createIndex( Class<? extends PropertyContainer> cls, String indexName,
                Map<String, String> config )
        {
            getTransaction().createIndex( cls, indexName, config );
        }

        public void deleIndex( Class<? extends PropertyContainer> cls, String indexName )
        {
            getTransaction().deleteIndex( cls, indexName );
        }

        public void changeConfig( Class<? extends PropertyContainer> cls, String indexName,
                Map<String, String> newConfig )
        {
            getTransaction().changeConfig( cls, indexName, newConfig );
        }
    }
    
    private static class Resource extends XaResourceHelpImpl
    {
        private final Object identifier;
        
        Resource( Object identifier, XaResourceManager xaRm, byte[] branchId )
        {
            super( xaRm, branchId );
            this.identifier = identifier;
        }
        
        @Override
        public boolean isSameRM( XAResource xares )
        {
            return xares instanceof Resource ? identifier.equals( ((Resource) xares).identifier ) : false;
        }
    }

    private static class CommandFactory extends XaCommandFactory
    {
        @Override
        public XaCommand readCommand( ReadableByteChannel byteChannel,
            ByteBuffer buffer ) throws IOException
        {
            return IndexCommand.readCommand( byteChannel, buffer );
        }
    }

    private class TransactionFactory extends XaTransactionFactory
    {
        @Override
        public XaTransaction create( int identifier )
        {
            return new IndexConfigTransaction( identifier, getLogicalLog() );
        }

        @Override
        public long getCurrentVersion()
        {
            return indexTxStore.getVersion();
        }
        
        @Override
        public long getAndSetNewVersion()
        {
            return indexTxStore.incrementVersion();
        }

        @Override
        public long getLastCommittedTx()
        {
            return indexTxStore.getLastCommittedTx();
        }

        @Override
        public void flushAll()
        {
        }
    }
    
    private class IndexConfigTransaction extends XaTransaction
    {
        private IndexDefineCommand definitions;
        private Collection<XaCommand> commands;
        
        IndexConfigTransaction( int identifier, XaLogicalLog log )
        {
            super( identifier, log );
        }
        
        public void createIndex( Class<? extends PropertyContainer> entityType, String name,
                Map<String, String> config )
        {
            IndexCommand command = getDefinitions().create( name, entityType, config );
            commands.add( command );
        }
        
        public void deleteIndex( Class<? extends PropertyContainer> entityType, String name )
        {
            IndexCommand command = getDefinitions().delete( name, entityType );
            commands.add( command );
        }
        
        void changeConfig( Class<? extends PropertyContainer> entityType, String name,
                Map<String, String> newConfig )
        {
            IndexCommand command = getDefinitions().create( name, entityType, newConfig );
            commands.add( command );
        }
        
        private IndexDefineCommand getDefinitions()
        {
            if ( definitions == null )
            {
                definitions = new IndexDefineCommand();
                commands = new ArrayList<XaCommand>();
                commands.add( definitions );
            }
            return definitions;
        }

        @Override
        public boolean isReadOnly()
        {
            return commands.isEmpty();
        }

        @Override
        protected void doAddCommand( XaCommand command )
        {
        }
        
        @Override
        protected void injectCommand( XaCommand command )
        {
            if ( commands == null )
            {
                commands = new ArrayList<XaCommand>();
            }
            
            if ( command instanceof IndexDefineCommand )
            {
                definitions = (IndexDefineCommand) command;
            }
            else
            {
                commands.add( command );
            }
        }

        @Override
        protected void doRollback() throws XAException
        {
        }

        @Override
        protected void doPrepare() throws XAException
        {
            // Write the commands to the logical log
            for ( XaCommand command : commands )
            {
                addCommand( command );
            }
        }

        @Override
        protected void doCommit() throws XAException
        {
            for ( XaCommand command : commands )
            {
                System.out.println( command );
                if ( command instanceof CreateCommand )
                {
                    CreateCommand createCommand = (CreateCommand) command;
                    indexStore.set( entityType( createCommand.getEntityType() ),
                            definitions.getIndexName( createCommand.getIndexNameId() ),
                            createCommand.getConfig() );
                }
                else if ( command instanceof DeleteCommand )
                {
                    DeleteCommand deleteCommand = (DeleteCommand) command;
                    Class<? extends PropertyContainer> entityType = entityType( deleteCommand.getEntityType() );
                    String indexName = definitions.getIndexName( deleteCommand.getIndexNameId() );
                    if ( indexStore.has( entityType, indexName ) )
                    {
                        indexStore.remove( entityType, indexName );
                    }
                }
            }
        }
    }
}
