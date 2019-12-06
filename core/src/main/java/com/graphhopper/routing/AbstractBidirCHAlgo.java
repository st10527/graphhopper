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

import com.carrotsearch.hppc.IntObjectMap;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.storage.*;
import com.graphhopper.util.EdgeIterator;

import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

import static com.graphhopper.util.EdgeIterator.ANY_EDGE;

/**
 * Common subclass for bidirectional CH algorithms.
 * <p>
 *
 * @author Peter Karich
 * @author easbar
 */
public abstract class AbstractBidirCHAlgo implements BidirRoutingAlgorithm {
    protected final RoutingCHGraph graph;
    protected final TraversalMode traversalMode;
    protected int from;
    protected int to;
    protected int fromOutEdge;
    protected int toInEdge;
    protected IntObjectMap<SPTEntry> bestWeightMapFrom;
    protected IntObjectMap<SPTEntry> bestWeightMapTo;
    protected IntObjectMap<SPTEntry> bestWeightMapOther;
    protected SPTEntry currFrom;
    protected SPTEntry currTo;
    protected SPTEntry bestFwdEntry;
    protected SPTEntry bestBwdEntry;
    protected double bestWeight;
    protected NodeAccess nodeAccess;
    protected RoutingCHEdgeExplorer allEdgeExplorer;
    protected RoutingCHEdgeExplorer inEdgeExplorer;
    protected RoutingCHEdgeExplorer outEdgeExplorer;
    protected int maxVisitedNodes = Integer.MAX_VALUE;
    protected CHEdgeFilter levelEdgeFilter;
    PriorityQueue<SPTEntry> pqOpenSetFrom;
    PriorityQueue<SPTEntry> pqOpenSetTo;
    private boolean updateBestPath = true;
    protected boolean finishedFrom;
    protected boolean finishedTo;
    int visitedCountFrom;
    int visitedCountTo;
    private boolean alreadyRun;

    public AbstractBidirCHAlgo(RoutingCHGraph graph, TraversalMode tMode) {
        this.graph = graph;
        this.traversalMode = tMode;
        this.nodeAccess = graph.getGraph().getNodeAccess();
        allEdgeExplorer = graph.createAllEdgeExplorer();
        outEdgeExplorer = graph.createOutEdgeExplorer();
        inEdgeExplorer = graph.createInEdgeExplorer();
        levelEdgeFilter = new CHLevelEdgeFilter(graph);
        fromOutEdge = ANY_EDGE;
        toInEdge = ANY_EDGE;
        bestWeight = Double.MAX_VALUE;
        int size = Math.min(Math.max(200, graph.getNodes() / 10), 150_000);
        initCollections(size);
    }

    protected void initCollections(int size) {
        pqOpenSetFrom = new PriorityQueue<>(size);
        bestWeightMapFrom = new GHIntObjectHashMap<>(size);

        pqOpenSetTo = new PriorityQueue<>(size);
        bestWeightMapTo = new GHIntObjectHashMap<>(size);
    }

    /**
     * Creates the root shortest path tree entry for the forward or backward search.
     */
    protected abstract SPTEntry createStartEntry(int node, double weight, boolean reverse);

    /**
     * Creates a new entry of the shortest path tree (a {@link SPTEntry} or one of its subclasses) during a dijkstra
     * expansion.
     *
     * @param edge    the edge that is currently processed for the expansion
     * @param incEdge the id of the edge that is incoming to the node the edge is pointed at. usually this is the same as
     *                edge.getEdge(), but for edge-based CH and in case edge is a shortcut incEdge is the original edge
     *                that is incoming to the node
     * @param weight  the weight the shortest path three entry should carry
     * @param parent  the parent entry of in the shortest path tree
     * @param reverse true if we are currently looking at the backward search, false otherwise
     */
    protected abstract SPTEntry createEntry(RoutingCHEdgeIteratorState edge, int incEdge, double weight, SPTEntry parent, boolean reverse);

    @Override
    public Path calcPath(int from, int to) {
        return calcPath(from, to, ANY_EDGE, ANY_EDGE);
    }

    @Override
    public Path calcPath(int from, int to, int fromOutEdge, int toInEdge) {
        if ((fromOutEdge != ANY_EDGE || toInEdge != ANY_EDGE) && !traversalMode.isEdgeBased()) {
            throw new IllegalArgumentException("Restricting the start/target edges is only possible for edge-based graph traversal");
        }
        this.fromOutEdge = fromOutEdge;
        this.toInEdge = toInEdge;
        checkAlreadyRun();
        init(from, 0, to, 0);
        runAlgo();
        return extractPath();
    }

    abstract protected BidirPathExtractor createPathExtractor(RoutingCHGraph graph);

    void init(int from, double fromWeight, int to, double toWeight) {
        initFrom(from, fromWeight);
        initTo(to, toWeight);
        postInit(from, to);
    }

    protected void initFrom(int from, double weight) {
        this.from = from;
        currFrom = createStartEntry(from, weight, false);
        pqOpenSetFrom.add(currFrom);
        if (!traversalMode.isEdgeBased()) {
            bestWeightMapFrom.put(from, currFrom);
        }
    }

    protected void initTo(int to, double weight) {
        this.to = to;
        currTo = createStartEntry(to, weight, true);
        pqOpenSetTo.add(currTo);
        if (!traversalMode.isEdgeBased()) {
            bestWeightMapTo.put(to, currTo);
        }
    }

    protected void postInit(int from, int to) {
        if (!traversalMode.isEdgeBased()) {
            if (updateBestPath) {
                bestWeightMapOther = bestWeightMapFrom;
                updateBestPath(getEdge(currFrom.adjNode, to), currFrom, to, true);
            }
        } else if (from == to && fromOutEdge == ANY_EDGE && toInEdge == ANY_EDGE) {
            // special handling if start and end are the same and no directions are restricted
            // the resulting weight should be zero
            if (currFrom.weight != 0 || currTo.weight != 0) {
                throw new IllegalStateException("If from=to, the starting weight must be zero for from and to");
            }
            bestFwdEntry = currFrom;
            bestBwdEntry = currTo;
            bestWeight = 0;
            finishedFrom = true;
            finishedTo = true;
            return;
        }
        postInitFrom();
        postInitTo();
    }

    protected void postInitFrom() {
        if (fromOutEdge == ANY_EDGE) {
            fillEdgesFromUsingFilter(levelEdgeFilter);
        } else {
            // need to use a local reference here, because additionalEdgeFilter is modified when calling fillEdgesFromUsingFilter
            final CHEdgeFilter tmpFilter = levelEdgeFilter;
            fillEdgesFromUsingFilter(new CHEdgeFilter() {
                @Override
                public boolean accept(RoutingCHEdgeIteratorState edgeState) {
                    return (tmpFilter == null || tmpFilter.accept(edgeState)) && edgeState.getOrigEdgeFirst() == fromOutEdge;
                }
            });
        }
    }

    private RoutingCHEdgeIteratorState getEdge(int base, int adj) {
        RoutingCHEdgeIterator iter = allEdgeExplorer.setBaseNode(base);
        while (iter.next()) {
            if (iter.getAdjNode() == adj)
                return iter;
        }
        return null;
    }

    protected void postInitTo() {
        if (toInEdge == ANY_EDGE) {
            fillEdgesToUsingFilter(levelEdgeFilter);
        } else {
            final CHEdgeFilter tmpFilter = levelEdgeFilter;
            fillEdgesToUsingFilter(new CHEdgeFilter() {
                @Override
                public boolean accept(RoutingCHEdgeIteratorState edgeState) {
                    return (tmpFilter == null || tmpFilter.accept(edgeState)) && edgeState.getOrigEdgeLast() == toInEdge;
                }
            });
        }
    }

    /**
     * @param edgeFilter edge filter used to fill edges. the {@link #levelEdgeFilter} reference will be set to
     *                   edgeFilter by this method, so make sure edgeFilter does not use it directly.
     */
    protected void fillEdgesFromUsingFilter(CHEdgeFilter edgeFilter) {
        // we temporarily ignore the additionalEdgeFilter
        CHEdgeFilter tmpFilter = levelEdgeFilter;
        levelEdgeFilter = edgeFilter;
        finishedFrom = !fillEdgesFrom();
        levelEdgeFilter = tmpFilter;
    }

    /**
     * @see #fillEdgesFromUsingFilter(CHEdgeFilter)
     */
    protected void fillEdgesToUsingFilter(CHEdgeFilter edgeFilter) {
        // we temporarily ignore the additionalEdgeFilter
        CHEdgeFilter tmpFilter = levelEdgeFilter;
        levelEdgeFilter = edgeFilter;
        finishedTo = !fillEdgesTo();
        levelEdgeFilter = tmpFilter;
    }

    protected void runAlgo() {
        while (!finished() && !isMaxVisitedNodesExceeded()) {
            if (!finishedFrom)
                finishedFrom = !fillEdgesFrom();

            if (!finishedTo)
                finishedTo = !fillEdgesTo();
        }
    }

    // http://www.cs.princeton.edu/courses/archive/spr06/cos423/Handouts/EPP%20shortest%20path%20algorithms.pdf
    // a node from overlap may not be on the best path!
    // => when scanning an arc (v, w) in the forward search and w is scanned in the reverseOrder
    //    search, update extractPath = μ if df (v) + (v, w) + dr (w) < μ
    protected boolean finished() {
        if (finishedFrom || finishedTo)
            return true;

        return currFrom.weight + currTo.weight >= bestWeight;
    }

    boolean fillEdgesFrom() {
        if (pqOpenSetFrom.isEmpty()) {
            return false;
        }
        currFrom = pqOpenSetFrom.poll();
        visitedCountFrom++;
        if (fromEntryCanBeSkipped()) {
            return true;
        }
        if (fwdSearchCanBeStopped()) {
            return false;
        }
        bestWeightMapOther = bestWeightMapTo;
        fillEdges(currFrom, pqOpenSetFrom, bestWeightMapFrom, outEdgeExplorer, false);
        return true;
    }

    boolean fillEdgesTo() {
        if (pqOpenSetTo.isEmpty()) {
            return false;
        }
        currTo = pqOpenSetTo.poll();
        visitedCountTo++;
        if (toEntryCanBeSkipped()) {
            return true;
        }
        if (bwdSearchCanBeStopped()) {
            return false;
        }
        bestWeightMapOther = bestWeightMapFrom;
        fillEdges(currTo, pqOpenSetTo, bestWeightMapTo, inEdgeExplorer, true);
        return true;
    }

    private void fillEdges(SPTEntry currEdge, PriorityQueue<SPTEntry> prioQueue,
                           IntObjectMap<SPTEntry> bestWeightMap, RoutingCHEdgeExplorer explorer, boolean reverse) {
        RoutingCHEdgeIterator iter = explorer.setBaseNode(currEdge.adjNode);
        while (iter.next()) {
            if (!accept(iter, currEdge, reverse))
                continue;

            final double weight = calcResultingWeight(iter, currEdge, reverse);
            if (Double.isInfinite(weight)) {
                continue;
            }
            final int origEdgeId = getOrigEdgeId(iter, reverse);
            final int traversalId = getTraversalId(iter, origEdgeId, reverse);
            SPTEntry entry = bestWeightMap.get(traversalId);
            if (entry == null) {
                entry = createEntry(iter, origEdgeId, weight, currEdge, reverse);
                bestWeightMap.put(traversalId, entry);
                prioQueue.add(entry);
            } else if (entry.getWeightOfVisitedPath() > weight) {
                prioQueue.remove(entry);
                updateEntry(entry, iter, origEdgeId, weight, currEdge, reverse);
                prioQueue.add(entry);
            } else
                continue;

            if (updateBestPath)
                updateBestPath(iter, entry, traversalId, reverse);
        }
    }

    protected void updateBestPath(RoutingCHEdgeIteratorState edgeState, SPTEntry entry, int traversalId, boolean reverse) {
        SPTEntry entryOther = bestWeightMapOther.get(traversalId);
        if (entryOther == null)
            return;

        // update μ
        double weight = entry.getWeightOfVisitedPath() + entryOther.getWeightOfVisitedPath();
        if (traversalMode.isEdgeBased()) {
            if (getIncomingEdge(entryOther) != getIncomingEdge(entry))
                throw new IllegalStateException("cannot happen for edge based execution of " + getName());

            // prevents the path to contain the edge at the meeting point twice and subtracts the weight (excluding turn weight => no previous edge)
            entry = entry.getParent();
            weight -= calcWeight(edgeState, reverse, EdgeIterator.NO_EDGE);
        }

        if (weight < bestWeight) {
            bestFwdEntry = reverse ? entryOther : entry;
            bestBwdEntry = reverse ? entry : entryOther;
            bestWeight = weight;
        }
    }

    protected double calcWeight(RoutingCHEdgeIteratorState edgeState, boolean reverse, int prevOrNextEdgeId) {
        double edgeWeight = edgeState.getWeight(reverse);
        final int origEdgeId = reverse ? edgeState.getOrigEdgeLast() : edgeState.getOrigEdgeFirst();
        double turnCosts = reverse
                ? graph.getTurnWeight(origEdgeId, edgeState.getBaseNode(), prevOrNextEdgeId)
                : graph.getTurnWeight(prevOrNextEdgeId, edgeState.getBaseNode(), origEdgeId);
        return edgeWeight + turnCosts;
    }

    protected void updateEntry(SPTEntry entry, RoutingCHEdgeIteratorState edge, int edgeId, double weight, SPTEntry parent, boolean reverse) {
        entry.edge = edge.getEdge();
        entry.weight = weight;
        entry.parent = parent;
    }

    protected boolean accept(RoutingCHEdgeIteratorState edge, SPTEntry currEdge, boolean reverse) {
        return accept(edge, getIncomingEdge(currEdge));
    }

    protected int getOrigEdgeId(RoutingCHEdgeIteratorState edge, boolean reverse) {
        return edge.getEdge();
    }

    protected int getIncomingEdge(SPTEntry entry) {
        return entry.edge;
    }

    protected int getTraversalId(RoutingCHEdgeIteratorState edge, int origEdgeId, boolean reverse) {
        return getTraversalId(edge, reverse);
    }

    protected int getTraversalId(RoutingCHEdgeIteratorState edge, boolean reverse) {
        return traversalMode.createTraversalId(edge.getBaseNode(), edge.getAdjNode(), edge.getEdge(), reverse);
    }

    protected double calcResultingWeight(RoutingCHEdgeIteratorState iter, SPTEntry currEdge, boolean reverse) {
        return calcWeight(iter, reverse, getIncomingEdge(currEdge)) + currEdge.getWeightOfVisitedPath();
    }

    protected Path extractPath() {
        if (finished())
            return createPathExtractor(graph).extract(bestFwdEntry, bestBwdEntry, bestWeight);

        return createEmptyPath();
    }

    protected boolean fromEntryCanBeSkipped() {
        return false;
    }

    protected boolean fwdSearchCanBeStopped() {
        return false;
    }

    protected boolean toEntryCanBeSkipped() {
        return false;
    }

    protected boolean bwdSearchCanBeStopped() {
        return false;
    }

    protected double getCurrentFromWeight() {
        return currFrom.weight;
    }

    protected double getCurrentToWeight() {
        return currTo.weight;
    }

    IntObjectMap<SPTEntry> getBestFromMap() {
        return bestWeightMapFrom;
    }

    IntObjectMap<SPTEntry> getBestToMap() {
        return bestWeightMapTo;
    }

    void setBestOtherMap(IntObjectMap<SPTEntry> other) {
        bestWeightMapOther = other;
    }

    protected void setUpdateBestPath(boolean b) {
        updateBestPath = b;
    }

    @Override
    public int getVisitedNodes() {
        return visitedCountFrom + visitedCountTo;
    }

    void setFromDataStructures(AbstractBidirCHAlgo other) {
        from = other.from;
        fromOutEdge = other.fromOutEdge;
        pqOpenSetFrom = other.pqOpenSetFrom;
        bestWeightMapFrom = other.bestWeightMapFrom;
        finishedFrom = other.finishedFrom;
        currFrom = other.currFrom;
        visitedCountFrom = other.visitedCountFrom;
        // outEdgeExplorer
    }

    void setToDataStructures(AbstractBidirCHAlgo other) {
        to = other.to;
        toInEdge = other.toInEdge;
        pqOpenSetTo = other.pqOpenSetTo;
        bestWeightMapTo = other.bestWeightMapTo;
        finishedTo = other.finishedTo;
        currTo = other.currTo;
        visitedCountTo = other.visitedCountTo;
        // inEdgeExplorer
    }

    @Override
    public void setMaxVisitedNodes(int numberOfNodes) {
        this.maxVisitedNodes = numberOfNodes;
    }

    protected boolean accept(RoutingCHEdgeIteratorState iter, int prevOrNextEdgeId) {
        // for edge-based traversal we leave it for TurnWeighting to decide whether or not a u-turn is acceptable,
        // but for node-based traversal we exclude such a turn for performance reasons already here
        if (!traversalMode.isEdgeBased() && iter.getEdge() == prevOrNextEdgeId)
            return false;

        return levelEdgeFilter == null || levelEdgeFilter.accept(iter);
    }

    protected void checkAlreadyRun() {
        if (alreadyRun)
            throw new IllegalStateException("Create a new instance per call");

        alreadyRun = true;
    }

    @Override
    public List<Path> calcPaths(int from, int to) {
        return Collections.singletonList(calcPath(from, to));
    }

    protected Path createEmptyPath() {
        return new Path(graph.getGraph());
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return getName() + "|" + graph.getWeighting();
    }

    protected boolean isMaxVisitedNodesExceeded() {
        return maxVisitedNodes < getVisitedNodes();
    }

    private static class CHLevelEdgeFilter implements CHEdgeFilter {
        private final RoutingCHGraph graph;
        private final int maxNodes;

        public CHLevelEdgeFilter(RoutingCHGraph graph) {
            this.graph = graph;
            maxNodes = graph.getBaseGraph().getNodes();
        }

        public boolean accept(RoutingCHEdgeIteratorState edgeState) {
            int base = edgeState.getBaseNode();
            int adj = edgeState.getAdjNode();
            // always accept virtual edges, see #288
            if (base >= maxNodes || adj >= maxNodes)
                return true;

            // minor performance improvement: shortcuts in wrong direction are disconnected, so no need to exclude them
            if (edgeState.isShortcut())
                return true;

            return graph.getLevel(base) <= graph.getLevel(adj);
        }
    }
}
