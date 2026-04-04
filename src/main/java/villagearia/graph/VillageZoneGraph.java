package villagearia.graph;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import java.util.Queue;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
public class VillageZoneGraph {
    
    public static final Map<UUID, Set<UUID>> graph = new ConcurrentHashMap<>();

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static void addNode(UUID node) {
        if (!graph.containsKey(node)) {
            graph.put(node, ConcurrentHashMap.newKeySet());
        }
    }

    public static void removeNode(UUID node) {
        graph.remove(node);
        // Clean up references from other nodes
        for (var connections : graph.values()) {
            connections.remove(node);
        }
    }
    
    public static void addConnection(UUID node1, UUID node2) {
        graph.computeIfAbsent(node1, k -> ConcurrentHashMap.newKeySet()).add(node2);
        graph.computeIfAbsent(node2, k -> ConcurrentHashMap.newKeySet()).add(node1);
    }

    public static void removeConnection(UUID node1, UUID node2) {
        var edges1 = graph.get(node1);
        if (edges1 != null) edges1.remove(node2);
        var edges2 = graph.get(node2);
        if (edges2 != null) edges2.remove(node1);
    }

    public static UUID getNearestVillageZone(org.joml.Vector3d pos, Store<EntityStore> store) {
        UUID nearestUUUID = null;
        var minDistanceSq = Double.MAX_VALUE;

        for (var uuid : graph.keySet()) {
            if (uuid == null) continue;
            var villageZone = villagearia.VillageZoneManager.getVillageZone(store, uuid);
            if (villageZone != null && villageZone.center != null) {
                var distSq = villageZone.center.distanceSquared(pos);
                if (distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                    nearestUUUID = uuid;
                }
            }
        }
        return nearestUUUID;
    }


    public static Queue<UUID> getRandomVillageZonePath(UUID start, int length, Store<EntityStore> store) {
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> path = new LinkedList<>();
        
        path.add(start);
        visited.add(start);
        
        var current = start;
        for (var depth = 1; depth < length; depth++) {
            var neighbors = graph.get(current);
            if (neighbors == null || neighbors.isEmpty()) {
                break; // No more interconnected paths to travel
            }

            // Exclude already visited nodes to prevent backtracking loops
            var validNeighbors = new ArrayList<UUID>();
            for (var neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    validNeighbors.add(neighbor);
                }
            }

            if (validNeighbors.isEmpty()) {
                break; // Stuck, unable to fulfill target length without looping
            }
            
            // Move into a random neighbor
            current = validNeighbors.get(ThreadLocalRandom.current().nextInt(validNeighbors.size()));
            visited.add(current);
            path.add(current);
        }
        
        return path;
    }

    public static Queue<UUID> getShortestPath(UUID start, UUID end, Store<EntityStore> store) {
        if (!graph.containsKey(start) || !graph.containsKey(end)) {
            return new LinkedList<>();
        }
        if (start.equals(end)) {
            Queue<UUID> path = new LinkedList<>();
            path.add(start);
            return path;
        }

        var dist = new HashMap<UUID, Integer>();
        var predecessors = new HashMap<UUID, List<UUID>>();
        Queue<UUID> queue = new LinkedList<>();

        dist.put(start, 0);
        queue.add(start);

        var found = false;
        var shortestDist = Integer.MAX_VALUE;

        while (!queue.isEmpty()) {
            var current = queue.poll();
            var currentDist = dist.get(current);

            if (currentDist >= shortestDist) {
                break; // We already found shortest paths to end, no need to explore deeper levels
            }

            var neighbors = graph.get(current);
            if (neighbors == null) continue;

            for (var neighbor : neighbors) {
                if (!dist.containsKey(neighbor)) {
                    dist.put(neighbor, currentDist + 1);
                    predecessors.computeIfAbsent(neighbor, k -> new ArrayList<>()).add(current);
                    queue.add(neighbor);
                    if (neighbor.equals(end)) {
                        found = true;
                        shortestDist = currentDist + 1;
                    }
                } else if (dist.get(neighbor) == currentDist + 1) {
                    predecessors.get(neighbor).add(current);
                }
            }
        }

        if (!found) {
            return new LinkedList<>();
        }

        var pathList = new ArrayList<UUID>();
        var curr = end;
        pathList.add(curr);

        while (!curr.equals(start)) {
            var preds = predecessors.get(curr);
            curr = preds.get(ThreadLocalRandom.current().nextInt(preds.size()));
            pathList.add(curr);
        }

        Collections.reverse(pathList);
        return new LinkedList<>(pathList);
    }
}
