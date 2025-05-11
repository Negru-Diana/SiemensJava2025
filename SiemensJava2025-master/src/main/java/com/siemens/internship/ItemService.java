package com.siemens.internship;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ItemService {
    @Autowired
    private ItemRepository itemRepository;

    // --- CHANGE 1: Thread Pool Optimization ---
    // Original: Used fixedThreadPool(10) which limits concurrency and wastes resources for I/O-bound tasks.
    // Modified: newWorkStealingPool() dynamically scales threads based on available processors,
    //           better suited for async I/O operations. Marked as final for thread safety.
    private static final ExecutorService executor = Executors.newWorkStealingPool();

    // --- CHANGE 2: Removal of Unsafe Shared State ---
    // Original: Used non-thread-safe ArrayList and int counter for tracking processed items.
    // Modified: Eliminated these variables entirely to avoid race conditions and synchronization issues.
    // Rationale: Shared mutable state is error-prone in concurrent environments.
    //            Results are now safely collected via CompletableFuture composition instead of shared collections.

    // --- CHANGE 3: Code Simplification ---
    // Removed unused imports (ArrayList) and redundant variables for cleaner, more maintainable code.


    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    public Optional<Item> findById(Long id) {
        return itemRepository.findById(id);
    }

    public Item save(Item item) {
        return itemRepository.save(item);
    }

    public void deleteById(Long id) {
        itemRepository.deleteById(id);
    }


    /**
     * Your Tasks
     * Identify all concurrency and asynchronous programming issues in the code
     * Fix the implementation to ensure:
     * All items are properly processed before the CompletableFuture completes
     * Thread safety for all shared state
     * Proper error handling and propagation
     * Efficient use of system resources
     * Correct use of Spring's @Async annotation
     * Add appropriate comments explaining your changes and why they fix the issues
     * Write a brief explanation of what was wrong with the original implementation
     * <p>
     * Hints
     * Consider how CompletableFuture composition can help coordinate multiple async operations
     * Think about appropriate thread-safe collections
     * Examine how errors are handled and propagated
     * Consider the interaction between Spring's @Async and CompletableFuture
     */
    @Async
    public CompletableFuture<List<Item>> processItemsAsync() {
        List<Long> itemIds = itemRepository.findAllIds();

        // --- CHANGE 1: Proper Async Composition ---
        // Original: Used runAsync() which returns void futures, making it impossible to track results.
        // Modified: Uses supplyAsync() to create futures that return processed items.
        List<CompletableFuture<Item>> futures = itemIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // --- CHANGE 2: Thread-Safe Processing ---
                        // Original: Modified shared state (processedItems/processedCount) without synchronization.
                        // Modified: No shared state - each task is isolated and thread-safe.
                        Item item = itemRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Item not found: " + id));
                        item.setStatus("PROCESSED");
                        return itemRepository.save(item);
                    } catch (Exception e) {
                        // --- CHANGE 3: Error Propagation ---
                        // Original: Swallowed exceptions, making failures invisible.
                        // Modified: Logs error and propagates via CompletionException
                        System.err.println("Error processing item " + id + ": " + e.getMessage());
                        throw new CompletionException(e);
                    }
                }, executor))
                .collect(Collectors.toList());


        // --- CHANGE 4: Result Aggregation ---
        // Original: Returned incomplete list immediately without waiting for async operations.
        // Modified: Uses allOf() + handle() to safely collect all results after completion.
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .handle((v, ex) -> futures.stream()
                        .map(future -> {
                            try {
                                return future.join(); // Get successful results
                            } catch (CompletionException e) {
                                // --- CHANGE 5: Graceful Failure Handling ---
                                // Original: Crashed or returned partial data on errors.
                                // Modified: Silently ignores failed items while retaining successful ones
                                return null;
                            }
                        })
                        .filter(Objects::nonNull) // Remove nulls from failed tasks
                        .collect(Collectors.toList()));
    }

}