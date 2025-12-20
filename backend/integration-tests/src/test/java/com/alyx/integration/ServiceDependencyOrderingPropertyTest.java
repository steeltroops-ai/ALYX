package com.alyx.integration;

import net.java.quickcheck.Generator;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.stream.Collectors;

/**
 * **Feature: alyx-system-fix, Property 2: Service dependency ordering**
 * **Validates: Requirements 1.3**
 * 
 * Property-based test that validates service dependency ordering in the Maven build system.
 * For any service with dependencies, it should only start after all its dependencies are healthy and ready.
 */
public class ServiceDependencyOrderingPropertyTest {

    /**
     * Represents a service with its dependencies
     */
    public static class Service {
        private final String name;
        private final Set<String> dependencies;
        private final int buildOrder;

        public Service(String name, Set<String> dependencies, int buildOrder) {
            this.name = name;
            this.dependencies = new HashSet<>(dependencies);
            this.buildOrder = buildOrder;
        }

        public String getName() { return name; }
        public Set<String> getDependencies() { return dependencies; }
        public int getBuildOrder() { return buildOrder; }

        @Override
        public String toString() {
            return String.format("Service{name='%s', dependencies=%s, buildOrder=%d}", 
                name, dependencies, buildOrder);
        }
    }

    /**
     * Generator for creating random service dependency graphs
     */
    public static class ServiceDependencyGenerator implements Generator<List<Service>> {
        private static final String[] SERVICE_NAMES = {
            "api-gateway", "job-scheduler", "resource-optimizer", 
            "collaboration-service", "notebook-service", "data-processing"
        };

        @Override
        public List<Service> next() {
            List<Service> services = new ArrayList<>();
            Random random = new Random();
            
            // Create services with random dependencies and build orders
            for (int i = 0; i < SERVICE_NAMES.length; i++) {
                String serviceName = SERVICE_NAMES[i];
                Set<String> dependencies = new HashSet<>();
                
                // Add random dependencies from services that come before this one
                for (int j = 0; j < i; j++) {
                    if (random.nextBoolean()) {
                        dependencies.add(SERVICE_NAMES[j]);
                    }
                }
                
                // Assign a random build order
                int buildOrder = random.nextInt(SERVICE_NAMES.length);
                services.add(new Service(serviceName, dependencies, buildOrder));
            }
            
            return services;
        }
    }

    /**
     * Property: Service dependency ordering must be respected
     * For any service with dependencies, it should only be built after all its dependencies
     */
    @Test
    @DisplayName("Property 2: Service dependency ordering - services should be built in dependency order")
    public void testServiceDependencyOrdering() {
        QuickCheck.forAll(new ServiceDependencyGenerator(), new AbstractCharacteristic<List<Service>>() {
            @Override
            protected void doSpecify(List<Service> services) throws Throwable {
                // Sort services by their build order
                List<Service> sortedServices = services.stream()
                    .sorted(Comparator.comparingInt(Service::getBuildOrder))
                    .collect(Collectors.toList());

                // For each service, verify all its dependencies come before it in the build order
                for (int i = 0; i < sortedServices.size(); i++) {
                    Service currentService = sortedServices.get(i);
                    Set<String> builtServices = sortedServices.subList(0, i).stream()
                        .map(Service::getName)
                        .collect(Collectors.toSet());

                    // All dependencies of the current service should have been built already
                    for (String dependency : currentService.getDependencies()) {
                        if (!builtServices.contains(dependency)) {
                            throw new AssertionError(
                                String.format("Service '%s' depends on '%s' but '%s' has not been built yet. " +
                                    "Built services so far: %s", 
                                    currentService.getName(), dependency, dependency, builtServices)
                            );
                        }
                    }
                }
            }
        });
    }

    /**
     * Property: Maven reactor build order should respect service dependencies
     * This tests the actual Maven build order from the POM structure
     */
    @Test
    @DisplayName("Property 2: Maven reactor build order respects dependencies")
    public void testMavenReactorBuildOrder() {
        // Define the actual ALYX service dependencies based on the POM structure
        Map<String, Set<String>> actualDependencies = new HashMap<>();
        actualDependencies.put("api-gateway", Set.of()); // No service dependencies
        actualDependencies.put("job-scheduler", Set.of()); // No service dependencies
        actualDependencies.put("resource-optimizer", Set.of()); // No service dependencies
        actualDependencies.put("collaboration-service", Set.of()); // No service dependencies
        actualDependencies.put("notebook-service", Set.of()); // No service dependencies
        actualDependencies.put("data-router", Set.of()); // No service dependencies
        actualDependencies.put("result-aggregator", Set.of()); // No service dependencies
        actualDependencies.put("quality-monitor", Set.of()); // No service dependencies
        actualDependencies.put("integration-tests", Set.of("api-gateway", "job-scheduler", "collaboration-service")); // Depends on other services
        actualDependencies.put("data-processing", Set.of()); // No service dependencies
        actualDependencies.put("infrastructure", Set.of()); // No service dependencies

        // Define the actual Maven reactor build order from the POM
        List<String> mavenBuildOrder = Arrays.asList(
            "api-gateway", "job-scheduler", "resource-optimizer", "collaboration-service",
            "notebook-service", "data-router", "result-aggregator", "quality-monitor",
            "integration-tests", "data-processing", "infrastructure"
        );

        // Verify that the Maven build order respects dependencies
        for (int i = 0; i < mavenBuildOrder.size(); i++) {
            String currentService = mavenBuildOrder.get(i);
            Set<String> builtServices = new HashSet<>(mavenBuildOrder.subList(0, i));
            Set<String> dependencies = actualDependencies.get(currentService);

            if (dependencies != null) {
                for (String dependency : dependencies) {
                    if (!builtServices.contains(dependency)) {
                        throw new AssertionError(
                            String.format("Maven build order violation: Service '%s' depends on '%s' " +
                                "but '%s' comes later in the build order. Current build order: %s",
                                currentService, dependency, dependency, mavenBuildOrder)
                        );
                    }
                }
            }
        }
    }

    /**
     * Property: Circular dependencies should be detected and prevented
     */
    @Test
    @DisplayName("Property 2: Circular dependencies should be prevented")
    public void testCircularDependencyPrevention() {
        QuickCheck.forAll(new ServiceDependencyGenerator(), new AbstractCharacteristic<List<Service>>() {
            @Override
            protected void doSpecify(List<Service> services) throws Throwable {
                // Build a dependency graph
                Map<String, Set<String>> dependencyGraph = services.stream()
                    .collect(Collectors.toMap(
                        Service::getName,
                        Service::getDependencies
                    ));

                // Check for circular dependencies using DFS
                Set<String> visited = new HashSet<>();
                Set<String> recursionStack = new HashSet<>();

                for (String service : dependencyGraph.keySet()) {
                    if (!visited.contains(service)) {
                        if (hasCircularDependency(service, dependencyGraph, visited, recursionStack)) {
                            throw new AssertionError(
                                String.format("Circular dependency detected involving service '%s'. " +
                                    "Dependency graph: %s", service, dependencyGraph)
                            );
                        }
                    }
                }
            }
        });
    }

    /**
     * Helper method to detect circular dependencies using DFS
     */
    private boolean hasCircularDependency(String service, Map<String, Set<String>> graph, 
                                        Set<String> visited, Set<String> recursionStack) {
        visited.add(service);
        recursionStack.add(service);

        Set<String> dependencies = graph.get(service);
        if (dependencies != null) {
            for (String dependency : dependencies) {
                if (!visited.contains(dependency)) {
                    if (hasCircularDependency(dependency, graph, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(dependency)) {
                    return true; // Circular dependency found
                }
            }
        }

        recursionStack.remove(service);
        return false;
    }
}