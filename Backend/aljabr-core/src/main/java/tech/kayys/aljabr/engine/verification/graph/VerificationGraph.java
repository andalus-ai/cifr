package tech.kayys.aljabr.engine.verification.graph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VerificationGraph {

    private final Map<String, VerificationNode> nodes = new ConcurrentHashMap<>();
    private final List<VerificationEdge> edges = Collections.synchronizedList(new ArrayList<>());

    public void addNode(VerificationNode node) {
        if (node != null) {
            nodes.put(node.nodeId(), node);
        }
    }

    public void addEdge(VerificationNode from, VerificationNode to, EdgeType type) {
        if (from != null && to != null && type != null) {
            addNode(from);
            addNode(to);
            edges.add(new VerificationEdge(from, to, type));
        }
    }

    public Optional<VerificationNode> getNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    public List<VerificationNode> allNodes() {
        return List.copyOf(nodes.values());
    }

    public List<VerificationEdge> allEdges() {
        return List.copyOf(edges);
    }

    public List<VerificationEdge> outgoingEdges(VerificationNode from) {
        if (from == null) return List.of();
        return edges.stream()
            .filter(e -> e.from().nodeId().equals(from.nodeId()))
            .toList();
    }

    public List<VerificationEdge> incomingEdges(VerificationNode to) {
        if (to == null) return List.of();
        return edges.stream()
            .filter(e -> e.to().nodeId().equals(to.nodeId()))
            .toList();
    }

    public boolean isRequirementVerified(String requirementId) {
        String reqNodeId = "req:" + requirementId;
        if (!nodes.containsKey(reqNodeId)) return false;

        // Check if there is at least one SUPPORTS or VERIFIED_BY edge without CONTRADICTS
        boolean hasSupporting = edges.stream().anyMatch(e ->
            (e.to().nodeId().equals(reqNodeId) || e.from().nodeId().equals(reqNodeId)) &&
            (e.type() == EdgeType.VERIFIED_BY || e.type() == EdgeType.SUPPORTS)
        );

        boolean hasContradicting = edges.stream().anyMatch(e ->
            (e.to().nodeId().equals(reqNodeId) || e.from().nodeId().equals(reqNodeId)) &&
            e.type() == EdgeType.CONTRADICTS
        );

        return hasSupporting && !hasContradicting;
    }
}
