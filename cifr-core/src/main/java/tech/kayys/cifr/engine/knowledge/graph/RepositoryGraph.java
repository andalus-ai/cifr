package tech.kayys.cifr.engine.knowledge.graph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe, queryable repository knowledge graph.
 * Supports bidirectional traversal, indexing, and fast lookups.
 */
public class RepositoryGraph {
    // Primary storage
    private final Map<String, RepositoryNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> outgoingEdges = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> incomingEdges = new ConcurrentHashMap<>();

    // Indexes for fast lookups
    private final Map<String, Set<String>> nodesByType = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> nodesByName = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> nodesByPackage = new ConcurrentHashMap<>();

    public RepositoryGraph() {}

    public RepositoryGraph(Map<String, RepositoryNode> initialNodes, Map<String, Set<String>> initialEdges) {
        if (initialNodes != null) {
            initialNodes.values().forEach(this::addNode);
        }
        if (initialEdges != null) {
            initialEdges.forEach((from, targets) -> {
                if (targets != null) {
                    targets.forEach(to -> addEdge(from, to));
                }
            });
        }
    }

    public void addNode(RepositoryNode node) {
        if (node == null || node.id() == null) return;
        nodes.put(node.id(), node);
        nodesByType.computeIfAbsent(node.type().name(), k -> ConcurrentHashMap.newKeySet()).add(node.id());
        if (node.name() != null) {
            nodesByName.computeIfAbsent(node.name().toLowerCase(Locale.ROOT), k -> ConcurrentHashMap.newKeySet()).add(node.id());
        }

        if (node instanceof RepositoryNode.Class clazz) {
            String pkg = extractPackage(clazz.id());
            nodesByPackage.computeIfAbsent(pkg, k -> ConcurrentHashMap.newKeySet()).add(node.id());
        }
    }

    public void addEdge(String fromId, String toId) {
        if (fromId == null || toId == null) return;
        outgoingEdges.computeIfAbsent(fromId, k -> ConcurrentHashMap.newKeySet()).add(toId);
        incomingEdges.computeIfAbsent(toId, k -> ConcurrentHashMap.newKeySet()).add(fromId);
    }

    public Optional<RepositoryNode> getNode(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(nodes.get(id));
    }

    public Set<RepositoryNode> getNodesByType(NodeType type) {
        if (type == null) return Set.of();
        return nodesByType.getOrDefault(type.name(), Set.of()).stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<RepositoryNode> searchByName(String query) {
        if (query == null || query.isBlank()) return Set.of();
        String lower = query.toLowerCase(Locale.ROOT);
        Set<String> exactMatches = nodesByName.getOrDefault(lower, Set.of());
        Set<String> fuzzyMatches = nodesByName.entrySet().stream()
                .filter(e -> e.getKey().contains(lower))
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toSet());

        Set<String> all = new HashSet<>(exactMatches);
        all.addAll(fuzzyMatches);

        return all.stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<RepositoryNode> getDependencies(String nodeId) {
        if (nodeId == null) return Set.of();
        return outgoingEdges.getOrDefault(nodeId, Set.of()).stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<RepositoryNode> getDependents(String nodeId) {
        if (nodeId == null) return Set.of();
        return incomingEdges.getOrDefault(nodeId, Set.of()).stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Set<RepositoryNode> getTransitiveDependencies(String nodeId, int maxDepth) {
        if (nodeId == null || maxDepth <= 0) return Set.of();
        Set<String> visited = new HashSet<>();
        Set<String> result = new HashSet<>();
        dfsOutgoing(nodeId, visited, result, 0, maxDepth);
        return result.stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void dfsOutgoing(String current, Set<String> visited, Set<String> result, int depth, int maxDepth) {
        if (depth >= maxDepth || visited.contains(current)) return;
        visited.add(current);

        for (String neighbor : outgoingEdges.getOrDefault(current, Set.of())) {
            result.add(neighbor);
            dfsOutgoing(neighbor, visited, result, depth + 1, maxDepth);
        }
    }

    public Set<RepositoryNode> getTransitiveDependents(String nodeId, int maxDepth) {
        if (nodeId == null || maxDepth <= 0) return Set.of();
        Set<String> visited = new HashSet<>();
        Set<String> result = new HashSet<>();
        dfsIncoming(nodeId, visited, result, 0, maxDepth);
        return result.stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void dfsIncoming(String current, Set<String> visited, Set<String> result, int depth, int maxDepth) {
        if (depth >= maxDepth || visited.contains(current)) return;
        visited.add(current);

        for (String neighbor : incomingEdges.getOrDefault(current, Set.of())) {
            result.add(neighbor);
            dfsIncoming(neighbor, visited, result, depth + 1, maxDepth);
        }
    }

    private String extractPackage(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "default";
    }

    public Map<String, RepositoryNode> nodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public Map<String, Set<String>> outgoingEdges() {
        return Collections.unmodifiableMap(outgoingEdges);
    }

    public Map<String, Set<String>> incomingEdges() {
        return Collections.unmodifiableMap(incomingEdges);
    }

    public int size() {
        return nodes.size();
    }
}
