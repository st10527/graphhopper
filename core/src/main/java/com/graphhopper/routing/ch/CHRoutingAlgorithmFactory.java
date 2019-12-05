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

import com.graphhopper.routing.*;
import com.graphhopper.routing.weighting.TurnWeighting;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.*;

import static com.graphhopper.util.Parameters.Algorithms.ASTAR_BI;
import static com.graphhopper.util.Parameters.Algorithms.DIJKSTRA_BI;

public class CHRoutingAlgorithmFactory implements RoutingAlgorithmFactory {
    private final CHProfile chProfile;

    public CHRoutingAlgorithmFactory(CHGraph chGraph) {
        this.chProfile = chGraph.getCHProfile();
    }

    @Override
    public RoutingAlgorithm createAlgo(Graph graph, AlgorithmOptions opts) {
        // todo: This method does not really fit for CH: We are passed a graph, but really we already know which
        // graph we have to use: the CH graph. Same with  opts.weighting: The CHProfile already contains a weighting
        // and we cannot really use it here. The real reason we do this the way its done atm is that graph might be
        // a QueryGraph that wraps (our) CHGraph.
        AbstractBidirCHAlgo algo = doCreateAlgo(graph, opts);
        algo.setMaxVisitedNodes(opts.getMaxVisitedNodes());
        return algo;
    }

    private AbstractBidirCHAlgo doCreateAlgo(Graph graph, AlgorithmOptions opts) {
        if (chProfile.isEdgeBased()) {
            TurnCostStorage turnCostStorage = graph.getTurnCostStorage();
            if (turnCostStorage == null) {
                throw new IllegalArgumentException("For edge-based CH you need a turn cost extension");
            }
            TurnWeighting turnWeighting = new TurnWeighting(getWeighting(), turnCostStorage, chProfile.getUTurnCosts());
            RoutingCHGraph g = new RoutingCHGraphImpl(graph, getWeighting(), turnWeighting);
            return createAlgoEdgeBased(g, opts);
        } else {
            RoutingCHGraph g = new RoutingCHGraphImpl(graph, chProfile.getWeighting());
            return createAlgoNodeBased(g, opts);
        }
    }

    private AbstractBidirCHAlgo createAlgoEdgeBased(RoutingCHGraph g, AlgorithmOptions opts) {
        if (ASTAR_BI.equals(opts.getAlgorithm())) {
            return new AStarBidirectionEdgeCHNoSOD(g)
                    .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, g.getBaseGraph().getNodeAccess()));
        } else if (DIJKSTRA_BI.equals(opts.getAlgorithm())) {
            return new DijkstraBidirectionEdgeCHNoSOD(g);
        } else {
            throw new IllegalArgumentException("Algorithm " + opts.getAlgorithm() + " not supported for edge-based Contraction Hierarchies. Try with ch.disable=true");
        }
    }

    private AbstractBidirCHAlgo createAlgoNodeBased(RoutingCHGraph g, AlgorithmOptions opts) {
        if (ASTAR_BI.equals(opts.getAlgorithm())) {
            return new AStarBidirectionCH(g)
                    .setApproximation(RoutingAlgorithmFactorySimple.getApproximation(ASTAR_BI, opts, g.getBaseGraph().getNodeAccess()));
        } else if (DIJKSTRA_BI.equals(opts.getAlgorithm())) {
            if (opts.getHints().getBool("stall_on_demand", true)) {
                return new DijkstraBidirectionCH(g);
            } else {
                return new DijkstraBidirectionCHNoSOD(g);
            }
        } else {
            throw new IllegalArgumentException("Algorithm " + opts.getAlgorithm() + " not supported for node-based Contraction Hierarchies. Try with ch.disable=true");
        }
    }

    public Weighting getWeighting() {
        return chProfile.getWeighting();
    }

    public CHProfile getCHProfile() {
        return chProfile;
    }
}
