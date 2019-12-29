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

package com.graphhopper.routing.ch;

import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.CHEdgeIterator;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

public class PrepareCHEdgeIteratorImpl implements PrepareCHEdgeExplorer, PrepareCHEdgeIterator {
    private final EdgeExplorer edgeExplorer;
    private final Weighting weighting;
    private final DefaultEdgeFilter defaultEdgeFilter;
    private EdgeIterator chIterator;

    public static PrepareCHEdgeExplorer inEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new PrepareCHEdgeIteratorImpl(edgeExplorer, weighting, DefaultEdgeFilter.inEdges(weighting.getFlagEncoder().getAccessEnc()));
    }

    public static PrepareCHEdgeExplorer outEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new PrepareCHEdgeIteratorImpl(edgeExplorer, weighting, DefaultEdgeFilter.outEdges(weighting.getFlagEncoder().getAccessEnc()));
    }

    public static PrepareCHEdgeExplorer allEdges(EdgeExplorer edgeExplorer, Weighting weighting) {
        return new PrepareCHEdgeIteratorImpl(edgeExplorer, weighting, DefaultEdgeFilter.allEdges(weighting.getFlagEncoder().getAccessEnc()));
    }

    public PrepareCHEdgeIteratorImpl(EdgeExplorer edgeExplorer, Weighting weighting, DefaultEdgeFilter defaultEdgeFilter) {
        this.edgeExplorer = edgeExplorer;
        this.weighting = weighting;
        this.defaultEdgeFilter = defaultEdgeFilter;
    }

    @Override
    public PrepareCHEdgeIteratorImpl setBaseNode(int node) {
        chIterator = edgeExplorer.setBaseNode(node);
        return this;
    }

    @Override
    public boolean next() {
        assertBaseNodeSet();
        while (true) {
            boolean hasNext = chIterator.next();
            if (!hasNext) {
                return false;
            } else if (hasAccess()) {
                return true;
            }
        }
    }

    private boolean hasAccess() {
        return defaultEdgeFilter.accept(chIterator);
    }

    @Override
    public int getEdge() {
        assertBaseNodeSet();
        return chIterator.getEdge();
    }

    @Override
    public int getBaseNode() {
        assertBaseNodeSet();
        return chIterator.getBaseNode();
    }

    @Override
    public int getAdjNode() {
        assertBaseNodeSet();
        return chIterator.getAdjNode();
    }

    @Override
    public int getOrigEdgeFirst() {
        assertBaseNodeSet();
        return chIterator.getOrigEdgeFirst();
    }

    @Override
    public int getOrigEdgeLast() {
        assertBaseNodeSet();
        return chIterator.getOrigEdgeLast();
    }

    @Override
    public boolean isShortcut() {
        assertBaseNodeSet();
        final EdgeIterator iter = chIterator;
        return iter instanceof CHEdgeIterator && ((CHEdgeIterator) iter).isShortcut();
    }

    @Override
    public double getWeight(boolean reverse) {
        if (isShortcut()) {
            return ((CHEdgeIterator) chIterator).getWeight();
        } else {
            assertBaseNodeSet();
            return weighting.calcWeight(chIterator, reverse, EdgeIterator.NO_EDGE);
        }
    }

    @Override
    public void setWeight(double weight) {
        assertBaseNodeSet();
        ((CHEdgeIterator) chIterator).setWeight(weight);
    }

    @Override
    public String toString() {
        if (chIterator == null) {
            return "not initialized";
        } else {
            return getBaseNode() + "->" + getAdjNode() + " (" + getEdge() + ")";
        }
    }

    int getMergeStatus(int flags) {
        assertBaseNodeSet();
        return ((CHEdgeIterator) chIterator).getMergeStatus(flags);
    }

    void setFlagsAndWeight(int flags, double weight) {
        assertBaseNodeSet();
        ((CHEdgeIterator) chIterator).setFlagsAndWeight(flags, weight);
    }

    void setSkippedEdges(int skippedEdge1, int skippedEdge2) {
        assertBaseNodeSet();
        ((CHEdgeIterator) chIterator).setSkippedEdges(skippedEdge1, skippedEdge2);
    }

    private void assertBaseNodeSet() {
        assert chIterator != null : "You need to call setBaseNode() before using the iterator";
    }
}
