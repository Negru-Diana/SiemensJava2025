package com.siemens.internship;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    @Autowired
    private ItemService itemService;

    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        return new ResponseEntity<>(itemService.findAll(), HttpStatus.OK);
    }



    @PostMapping
    public ResponseEntity<?> createItem(@Valid @RequestBody Item item, BindingResult result) {
        // --- CHANGE 1: Validation Error Handling ---
        // Original code returned HTTP 201 for errors, which is semantically incorrect.
        // Modified to check for validation errors using BindingResult.
        if (result.hasErrors()) {
            // --- CHANGE 2: Detailed Error Messages ---
            // Original code returned null with HTTP 201.
            // Modified to extract field-specific errors (ex: email format) and provide actionable feedback to the client.
            List<String> errors = result.getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .collect(Collectors.toList());

            // --- CHANGE 3: Correct HTTP Status Code for Errors ---
            // Original: Incorrectly used HTTP 201 (CREATED) for validation errors.
            // Modified: Returns HTTP 400 (BAD_REQUEST) with error details.
            return ResponseEntity.badRequest().body(errors);
        }

        // --- CHANGE 4: Proper Success Response ---
        // Original code returned HTTP 400 (BAD_REQUEST) on success, which was incorrect.
        // Modified to return HTTP 201 (CREATED) with the saved item, adhering to REST standards for resource creation.
        Item savedItem = itemService.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedItem);
    }



    @GetMapping("/{id}")
    public ResponseEntity<Item> getItemById(@PathVariable Long id) {
        // --- CHANGE 1: Correct HTTP Status Code for Missing Resource ---
        // Original code returned HTTP 204 (NO_CONTENT) when the item was not found.
        // HTTP 204 implies a successful request with no content to return (e.g., after a DELETE).
        // This is semantically incorrect for a missing resource during a GET request.
        //
        // Modified to return HTTP 404 (NOT_FOUND) when the item does not exist.
        // HTTP 404 clearly indicates the resource is missing, aligning with REST standards.
        return itemService.findById(id)
                .map(item -> new ResponseEntity<>(item, HttpStatus.OK)) // HTTP 200 for success
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND)); // HTTP 404 for missing resource
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateItem(@PathVariable Long id, @Valid @RequestBody Item item, BindingResult result) {
        // --- CHANGE 1: Added Request Validation ---
        // Original code lacked validation for the incoming request body.
        // Added @Valid and BindingResult to enforce validation rules defined in the Item entity (ex: email format).
        if (result.hasErrors()) {
            // --- CHANGE 2: Detailed Validation Error Response ---
            // Original code did not handle validation errors.
            // Added error messages for invalid fields (ex: "email: Invalid email format").
            return new ResponseEntity<>(
                    result.getFieldErrors().stream()
                            .map(e -> e.getField() + ": " + e.getDefaultMessage())
                            .toList(),
                    HttpStatus.BAD_REQUEST
            );
        }

        // --- CHANGE 3: Proper Resource Existence Check ---
        // Original code returned HTTP 202 (ACCEPTED) if the item didn't exist, which is semantically incorrect.
        // Modified to return HTTP 404 (NOT_FOUND) when the resource is missing.
        Optional<Item> existingItem = itemService.findById(id);
        if (existingItem.isPresent()) {
            // --- CHANGE 4: Correct Success Status Code ---
            // Original code returned HTTP 201 (CREATED) for updates, which is meant for new resource creation.
            // Modified to return HTTP 200 (OK) to indicate successful updates, aligning with REST standards.
            item.setId(id); // Ensure the ID matches the path variable
            return new ResponseEntity<>(itemService.save(item), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // HTTP 404 for missing resource
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        // --- CHANGE 1: Resource Existence Check ---
        // Original code attempted deletion without checking if the resource exists.
        // Added check to verify if the item exists before proceeding with deletion.
        if (itemService.findById(id).isEmpty()) {
            // --- CHANGE 2: Correct Missing Resource Response ---
            // Original code returned HTTP 409 (CONFLICT) for missing resources, which is semantically incorrect.
            // Modified to return HTTP 404 (NOT_FOUND) when the item does not exist.
            return ResponseEntity.notFound().build();
        }

        // --- CHANGE 3: Proper Deletion Workflow ---
        // Original code returned HTTP 409 (CONFLICT) even after deletion, which is invalid.
        // Delete the resource and return HTTP 204 (NO_CONTENT) to indicate successful deletion with no response body.
        itemService.deleteById(id);

        // --- CHANGE 4: Correct Success Status Code ---
        // Original code used HTTP 409 (CONFLICT), which implies a conflict state.
        // Modified to return HTTP 204 (NO_CONTENT), the standard response for successful deletions.
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/process")
    public CompletableFuture<ResponseEntity<?>> processItems() {
        // --- CHANGE 1: Proper Asynchronous Handling ---
        // Original code wrapped the raw CompletableFuture in ResponseEntity, which is incorrect.
        // Modified to return CompletableFuture<ResponseEntity<?>> directly, allowing Spring to handle async resolution.
        return itemService.processItemsAsync()
                .thenApply(processedItems -> {
                    // --- CHANGE 2: Empty Result Handling ---
                    // Original code returned empty lists without feedback.
                    // Added conditional response to improve API clarity when no items are processed.
                    if (processedItems.isEmpty()) {
                        return ResponseEntity.ok("No items processed"); // HTTP 200 with message
                    }
                    return ResponseEntity.ok(processedItems); // HTTP 200 with processed items
                })
                .exceptionally(ex -> {
                    // --- CHANGE 3: Error Handling & Propagation ---
                    // Original code did NOT handle exceptions, risking uncaught errors.
                    // Added error logging and proper HTTP 500 response with error details.
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    System.err.println("Processing failed: " + cause.getMessage()); // Log error
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Error: " + cause.getMessage()); // Return error details
                });
    }
}
