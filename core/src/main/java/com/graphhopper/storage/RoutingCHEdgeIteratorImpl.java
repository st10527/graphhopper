/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.storage;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;

public class RoutingCHEdgeIteratorImpl extends RoutingCHEdgeIteratorStateImpl implements RoutingCHEdgeExplorer, RoutingCHEdgeIterator {
    private final EdgeExplorer edgeExplorer;
    private final DefaultEdgeFilter shortcutFilter;
    private EdgeIterator edgeIterator;

    public static RoutingCHEdgeIteratorImpl inEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new RoutingCHEdgeIteratorImpl(edgeExplorer, weighting, DefaultEdgeFilter.inEdges(weighting.getFlagEncoder()));
    }

    public static RoutingCHEdgeIteratorImpl outEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new RoutingCHEdgeIteratorImpl(edgeExplorer, weighting, DefaultEdgeFilter.outEdges(weighting.getFlagEncoder()));
    }

    public static RoutingCHEdgeIteratorImpl allEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new RoutingCHEdgeIteratorImpl(edgeExplorer, weighting, DefaultEdgeFilter.allEdges(weighting.getFlagEncoder()));
    }

    public RoutingCHEdgeIteratorImpl(EdgeExplorer edgeExplorer, Weighting weighting, DefaultEdgeFilter shortcutFilter) {
        super(null, weighting);
        this.edgeExplorer = edgeExplorer;
        this.shortcutFilter = shortcutFilter;
    }

    @Override
    EdgeIteratorState edgeState() {
        return edgeIterator;
    }

    @Override
    public RoutingCHEdgeIterator setBaseNode(int baseNode) {
        edgeIterator = edgeExplorer.setBaseNode(baseNode);
        return this;
    }

    @Override
    public boolean next() {
        while (true) {
            boolean hasNext = edgeIterator.next();
            if (!hasNext) {
                return false;
            } else if (hasAccess()) {
                return true;
            }
        }
    }

    private boolean hasAccess() {
        // todonow: consider weighting here
        return shortcutFilter.accept(edgeIterator);
    }

}
