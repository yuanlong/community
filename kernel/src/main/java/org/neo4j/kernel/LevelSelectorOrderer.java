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

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.neo4j.graphdb.traversal.BranchSelector;
import org.neo4j.graphdb.traversal.TraversalBranch;
import org.neo4j.graphdb.traversal.TraversalContext;
import org.neo4j.helpers.Pair;

public class LevelSelectorOrderer extends
        AbstractSelectorOrderer<Pair<AtomicInteger, Queue<TraversalBranch>>>
{
    private final boolean stopDescentOnResult;

    public LevelSelectorOrderer( BranchSelector startSelector, BranchSelector endSelector,
            boolean stopDescentOnResult )
    {
        super( startSelector, endSelector );
        this.stopDescentOnResult = stopDescentOnResult;
    }

    protected Pair<AtomicInteger, Queue<TraversalBranch>> initialState()
    {
        return Pair.<AtomicInteger, Queue<TraversalBranch>> of( new AtomicInteger(),
                new LinkedList<TraversalBranch>() );
    }

    @Override
    public TraversalBranch next( TraversalContext metadata )
    {
        TraversalBranch branch = nextBranchFromCurrentSelector( metadata, false );
        Pair<AtomicInteger, Queue<TraversalBranch>> state = getStateForCurrentSelector();
        AtomicInteger previousDepth = state.first();
        if ( branch != null && branch.length() == previousDepth.get() )
        {
            return branch;
        }
        else
        {
            if ( stopDescentOnResult && metadata.getNumberOfPathsReturned() > 0 )
            {
                nextSelector();
//                return getStateForCurrentSelector().other().poll();
                return null;
            }

            if ( branch != null )
            {
                previousDepth.set( branch.length() );
                state.other().add( branch );
            }
            BranchSelector otherSelector = nextSelector();
            TraversalBranch otherBranch = getStateForCurrentSelector().other().poll();
            if ( otherBranch != null )
            {
                return otherBranch;
            }

            otherBranch = otherSelector.next( metadata );
            return otherBranch != null ? otherBranch : branch;
        }
    }
}
