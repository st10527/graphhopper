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
package com.graphhopper.routing;

import com.graphhopper.routing.ch.NodeBasedCHBidirPathExtractor;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.BeelineWeightApproximator;
import com.graphhopper.routing.weighting.ConsistentWeightApproximator;
import com.graphhopper.routing.weighting.WeightApproximator;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.SPTEntry;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

public class AStarBidirectionCH extends AbstractBidirCHAlgo implements RecalculationHook {
    private ConsistentWeightApproximator weightApprox;

    public AStarBidirectionCH(Graph graph, Weighting weighting) {
        super(graph, weighting, TraversalMode.NODE_BASED);
        BeelineWeightApproximator defaultApprox = new BeelineWeightApproximator(nodeAccess, weighting);
        defaultApprox.setDistanceCalc(Helper.DIST_PLANE);
        setApproximation(defaultApprox);
    }

    @Override
    protected void initCollections(int size) {
        super.initCollections(Math.min(size, 2000));
    }

    @Override
    protected boolean finished() {
        // we need to finish BOTH searches for CH!
        if (finishedFrom && finishedTo)
            return true;

        // changed finish condition for CH
        return currFrom.weight >= bestWeight && currTo.weight >= bestWeight;
    }

    @Override
    protected BidirPathExtractor createPathExtractor(Graph graph, Weighting weighting) {
        return new NodeBasedCHBidirPathExtractor(graph, graph.getBaseGraph(), weighting);
    }

    @Override
    public String getName() {
        return "astarbi|ch";
    }

    @Override
    public String toString() {
        return getName() + "|" + weighting;
    }

    @Override
    void init(int from, double fromWeight, int to, double toWeight) {
        weightApprox.setFrom(from);
        weightApprox.setTo(to);
        super.init(from, fromWeight, to, toWeight);
    }

    @Override
    protected SPTEntry createStartEntry(int node, double weight, boolean reverse) {
        double heapWeight = weight + weightApprox.approximate(node, reverse);
        return new AStar.AStarEntry(EdgeIterator.NO_EDGE, node, heapWeight, weight);
    }

    @Override
    protected SPTEntry createEntry(EdgeIteratorState edge, int incEdge, double weight, SPTEntry parent, boolean reverse) {
        int neighborNode = edge.getAdjNode();
        double heapWeight = weight + weightApprox.approximate(neighborNode, reverse);
        AStar.AStarEntry entry = new AStar.AStarEntry(edge.getEdge(), neighborNode, heapWeight, weight);
        entry.parent = parent;
        return entry;
    }

    @Override
    protected void updateEntry(SPTEntry entry, EdgeIteratorState edge, int edgeId, double weight, SPTEntry parent, boolean reverse) {
        entry.edge = edge.getEdge();
        entry.weight = weight + weightApprox.approximate(edge.getAdjNode(), reverse);
        ((AStar.AStarEntry) entry).weightOfVisitedPath = weight;
        entry.parent = parent;
    }

    @Override
    protected double calcWeight(EdgeIteratorState iter, SPTEntry currEdge, boolean reverse) {
        // TODO performance: check if the node is already existent in the opposite direction
        // then we could avoid the approximation as we already know the exact complete path!
        return super.calcWeight(iter, currEdge, reverse);
    }

    public WeightApproximator getApproximation() {
        return weightApprox.getApproximation();
    }

    /**
     * @param approx if true it enables approximate distance calculation from lat,lon values
     */
    public AStarBidirectionCH setApproximation(WeightApproximator approx) {
        weightApprox = new ConsistentWeightApproximator(approx);
        return this;
    }

    void setFromDataStructures(AStarBidirectionCH astar) {
        super.setFromDataStructures(astar);
        weightApprox.setFrom(astar.currFrom.adjNode);
    }

    void setToDataStructures(AStarBidirectionCH astar) {
        super.setToDataStructures(astar);
        weightApprox.setTo(astar.currTo.adjNode);
    }

    @Override
    public void afterHeuristicChange(boolean forward, boolean backward) {
        if (forward) {

            // update PQ due to heuristic change (i.e. weight changed)
            if (!pqOpenSetFrom.isEmpty()) {
                // copy into temporary array to avoid pointer change of PQ
                AStar.AStarEntry[] entries = pqOpenSetFrom.toArray(new AStar.AStarEntry[pqOpenSetFrom.size()]);
                pqOpenSetFrom.clear();
                for (AStar.AStarEntry value : entries) {
                    value.weight = value.weightOfVisitedPath + weightApprox.approximate(value.adjNode, false);
                    // does not work for edge based
                    // ignoreExplorationFrom.add(value.adjNode);

                    pqOpenSetFrom.add(value);
                }
            }
        }

        if (backward) {
            if (!pqOpenSetTo.isEmpty()) {
                AStar.AStarEntry[] entries = pqOpenSetTo.toArray(new AStar.AStarEntry[pqOpenSetTo.size()]);
                pqOpenSetTo.clear();
                for (AStar.AStarEntry value : entries) {
                    value.weight = value.weightOfVisitedPath + weightApprox.approximate(value.adjNode, true);
                    // ignoreExplorationTo.add(value.adjNode);

                    pqOpenSetTo.add(value);
                }
            }
        }
    }
}
