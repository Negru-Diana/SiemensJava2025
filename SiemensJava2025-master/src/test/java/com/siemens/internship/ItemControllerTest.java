package com.siemens.internship;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemController.class)
class ItemControllerTest extends ItemController {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemService itemService;

    private final String validItemJson = """
        {
            "name": "Test Item",
            "email": "valid@example.com",
            "description": "Test Description"
        }
        """;

    private final String invalidItemJson = """
        {
            "name": "",
            "email": "invalid-email",
            "description": "Test"
        }
        """;

    @Test
    void getAllItems_ReturnsAllItems() throws Exception {
        Item item = new Item(1L, "Test", "Desc", "NEW", "test@example.com");
        Mockito.when(itemService.findAll()).thenReturn(List.of(item));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Test")));
    }

    @Test
    void createItem_ValidItem_ReturnsCreated() throws Exception {
        Item savedItem = new Item(1L, "Test", "Desc", "NEW", "valid@example.com");
        Mockito.when(itemService.save(Mockito.any())).thenReturn(savedItem);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validItemJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)));
    }

    @Test
    void createItem_InvalidItem_ReturnsBadRequest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidItemJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$", hasItem("name: Name is required")))
                .andExpect(jsonPath("$", hasItem("email: Invalid email format")));
    }

    @Test
    void getItemById_ExistingId_ReturnsItem() throws Exception {
        Item item = new Item(1L, "Test", "Desc", "NEW", "test@example.com");
        Mockito.when(itemService.findById(1L)).thenReturn(Optional.of(item));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Test")));
    }

    @Test
    void getItemById_NonExistingId_ReturnsNotFound() throws Exception {
        Mockito.when(itemService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/items/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void processItems_AllSuccess_ReturnsProcessedItems() throws Exception {
        Item processed = new Item(1L, "Test", "Desc", "PROCESSED", "test@example.com");
        Mockito.when(itemService.processItemsAsync())
                .thenReturn(CompletableFuture.completedFuture(List.of(processed)));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/items/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status", is("PROCESSED")));
    }

    @Test
    void processItems_AllFail_ReturnsErrorMessage() throws Exception {
        Mockito.when(itemService.processItemsAsync())
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Processing failed")));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/items/process"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("Processing failed")));
    }
}