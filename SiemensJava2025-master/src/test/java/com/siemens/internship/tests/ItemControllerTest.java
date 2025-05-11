package com.siemens.internship.tests;

import com.siemens.internship.Item;
import com.siemens.internship.ItemController;
import com.siemens.internship.ItemService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemControllerTest extends ItemController {

    @Mock
    private ItemService itemService;

    @Mock
    private BindingResult bindingResult;

    @InjectMocks
    private ItemController itemController;

    @Test
    void testGetAllItems() {
        // Test 1: Returns the full list of items
        List<Item> mockItems = List.of(new Item(), new Item());
        when(itemService.findAll()).thenReturn(mockItems);
        ResponseEntity<List<Item>> response = itemController.getAllItems();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());

        // Test 2: Returns the empty list
        when(itemService.findAll()).thenReturn(List.of());
        response = itemController.getAllItems();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void testCreateItem() {
        // Test 1: Create valid item
        Item validItem = new Item(null, "Test", "Desc", "PENDING", "valid@test.com");
        when(bindingResult.hasErrors()).thenReturn(false);
        when(itemService.save(validItem)).thenReturn(validItem);

        ResponseEntity<?> response = itemController.createItem(validItem, bindingResult);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(validItem, response.getBody());

        // Test 2: Invalid email validation
        Item invalidItem = new Item(null, "", "Desc", "PENDING", "invalid-email");
        when(bindingResult.hasErrors()).thenReturn(true);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("item", "name", "Name is required"),
                new FieldError("item", "email", "Invalid email format")
        ));

        response = itemController.createItem(invalidItem, bindingResult);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(((List<?>) response.getBody()).contains("email: Invalid email format"));
    }

    @Test
    void testGetItemById() {
        // Test 1: Item existing
        Item mockItem = new Item(1L, "Test", "Desc", "PENDING", "test@test.com");
        when(itemService.findById(1L)).thenReturn(Optional.of(mockItem));
        ResponseEntity<Item> response = itemController.getItemById(1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockItem, response.getBody());

        // Test 2: Item nonexistent
        when(itemService.findById(999L)).thenReturn(Optional.empty());
        response = itemController.getItemById(999L);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testUpdateItem() {
        // Test 1: Correct update
        Item existingItem = new Item(1L, "Old", "Desc", "PENDING", "old@test.com");
        Item updatedItem = new Item(1L, "New", "Desc", "PROCESSED", "new@test.com");
        when(itemService.findById(1L)).thenReturn(Optional.of(existingItem));
        when(bindingResult.hasErrors()).thenReturn(false);
        when(itemService.save(any(Item.class))).thenReturn(updatedItem);
        ResponseEntity<?> response = itemController.updateItem(1L, updatedItem, bindingResult);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(updatedItem, response.getBody());
        verify(itemService).save(updatedItem);

        // Test 2: Item nonexistent for the update
        when(itemService.findById(999L)).thenReturn(Optional.empty());
        response = itemController.updateItem(999L, new Item(), bindingResult);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testDeleteItem() {
        // Test 1: Successful deletion
        when(itemService.findById(1L)).thenReturn(Optional.of(new Item()));
        ResponseEntity<Void> response = itemController.deleteItem(1L);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(itemService).deleteById(1L);

        // Test 2: Attempt deletion item nonexistent
        when(itemService.findById(999L)).thenReturn(Optional.empty());
        response = itemController.deleteItem(999L);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testProcessItems() {
        // Test 1: Successful processing
        List<Item> processedItems = List.of(new Item(), new Item());
        when(itemService.processItemsAsync()).thenReturn(CompletableFuture.completedFuture(processedItems));

        CompletableFuture<ResponseEntity<?>> future = itemController.processItems();
        ResponseEntity<?> response = future.join();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, ((List<?>) response.getBody()).size());

        // Test 2: Error processing
        when(itemService.processItemsAsync()).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Error")));
        future = itemController.processItems();
        response = future.join();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        // Test 3: No items processed
        when(itemService.processItemsAsync()).thenReturn(CompletableFuture.completedFuture(List.of()));
        future = itemController.processItems();
        response = future.join();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("No items processed", response.getBody());
    }
}