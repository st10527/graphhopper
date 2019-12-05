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

import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;

public class RoutingCHGraphImpl implements RoutingCHGraph {
    /**
     * can be a CHGraph or a QueryGraph wrapping a CHGraph
     */
    private final Graph graph;
    /**
     * the CHGraph, might be the same as graph
     */
    private final CHGraph chGraph;
    /**
     * the base graph
     */
    private final Graph baseGraph;
    private final Weighting weighting;
    private final TurnWeighting turnWeighting;
    private final BooleanEncodedValue accessEnc;

    public RoutingCHGraphImpl(Graph graph, Weighting weighting) {
        this(graph, weighting, null);
    }

    public RoutingCHGraphImpl(Graph graph, Weighting weighting, TurnWeighting turnWeighting) {
        // todonow: maybe instead of the two arguments we can do something like if instanceof TurnWeighting
        // turnWeighting.getSuperWeighting ?
        this.graph = graph;
        if (graph instanceof QueryGraph) {
            chGraph = (CHGraph) ((QueryGraph) graph).getMainGraph();
        } else {
            chGraph = (CHGraph) graph;
        }
        baseGraph = chGraph.getBaseGraph();
        this.weighting = weighting;
        this.turnWeighting = turnWeighting;
        this.accessEnc = weighting.getFlagEncoder().getAccessEnc();
    }

    @Override
    public int getNodes() {
        return graph.getNodes();
    }

    @Override
    public int getEdges() {
        return graph.getEdges();
    }

    @Override
    public int getOriginalEdges() {
        return baseGraph.getEdges();
    }

    @Override
    public int getOtherNode(int edge, int node) {
        return graph.getOtherNode(edge, node);
    }

    @Override
    public boolean isAdjacentToNode(int edge, int node) {
        return graph.isAdjacentToNode(edge, node);
    }

    @Override
    public RoutingCHEdgeExplorer createInEdgeExplorer() {
        return new RoutingCHEdgeIteratorImpl(graph.createEdgeExplorer(DefaultEdgeFilter.inEdges(accessEnc)), weighting);
    }

    public RoutingCHEdgeExplorer createOutEdgeExplorer() {
        return new RoutingCHEdgeIteratorImpl(graph.createEdgeExplorer(DefaultEdgeFilter.outEdges(accessEnc)), weighting);
    }

    @Override
    public RoutingCHEdgeExplorer createAllEdgeExplorer() {
        return new RoutingCHEdgeIteratorImpl(graph.createEdgeExplorer(DefaultEdgeFilter.allEdges(accessEnc)), weighting);
    }

    @Override
    public RoutingCHEdgeExplorer createOriginalInEdgeExplorer() {
        return new RoutingCHEdgeIteratorImpl(graph.getBaseGraph().createEdgeExplorer(DefaultEdgeFilter.inEdges(accessEnc)), weighting);
    }

    @Override
    public RoutingCHEdgeExplorer createOriginalOutEdgeExplorer() {
        return new RoutingCHEdgeIteratorImpl(graph.getBaseGraph().createEdgeExplorer(DefaultEdgeFilter.outEdges(accessEnc)), weighting);
    }

    @Override
    public RoutingCHEdgeIteratorState getEdgeIteratorState(int edgeId, int adjNode) {
        EdgeIteratorState edgeState = graph.getEdgeIteratorState(edgeId, adjNode);
        return edgeState == null ? null : new RoutingCHEdgeIteratorStateImpl(edgeState, weighting);
    }

    @Override
    public int getLevel(int node) {
        return chGraph.getLevel(node);
    }

    @Override
    public Graph getGraph() {
        return graph;
    }

    // todonow: should not be used too often
    @Override
    public Graph getBaseGraph() {
        return chGraph.getBaseGraph();
    }

    @Override
    public Weighting getWeighting() {
        return turnWeighting != null ? turnWeighting : weighting;
    }

    @Override
    public double getTurnWeight(int edgeFrom, int nodeVia, int edgeTo) {
        if (turnWeighting == null) {
            return 0;
        }
        return turnWeighting.calcTurnWeight(edgeFrom, nodeVia, edgeTo);
    }

}
