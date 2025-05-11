package com.siemens.internship.tests;

import com.siemens.internship.Item;
import com.siemens.internship.ItemRepository;
import com.siemens.internship.ItemService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest extends ItemService {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemService itemService;


    @Test
    void testFindAll() {
        // Caz 1: Populated list
        when(itemRepository.findAll()).thenReturn(List.of(new Item(), new Item()));
        assertEquals(2, itemService.findAll().size());

        // Caz 2: Empty list
        when(itemRepository.findAll()).thenReturn(List.of());
        assertTrue(itemService.findAll().isEmpty());
    }

    @Test
    void testFindById() {
        // Caz 1: Item existing
        Item item = new Item(1L, "Test", "Desc", "PENDING", "test@test.com");
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        assertTrue(itemService.findById(1L).isPresent());

        // Caz 2: Item nonexistent
        when(itemRepository.findById(999L)).thenReturn(Optional.empty());
        assertTrue(itemService.findById(999L).isEmpty());
    }

    @Test
    void testSave() {
        // Caz 1: Valid save
        Item validItem = new Item(null, "Test", "Desc", "PENDING", "valid@test.com");
        when(itemRepository.save(validItem)).thenReturn(validItem);
        assertNotNull(itemService.save(validItem));

        // Caz 2: Invalid email
        Item invalidItem = new Item(null, "Test", "Desc", "PENDING", "invalid");
        when(itemRepository.save(invalidItem)).thenThrow(ConstraintViolationException.class);
        assertThrows(ConstraintViolationException.class, () -> itemService.save(invalidItem));
    }

    @Test
    void testDeleteById() {
        // Caz 1: Successful deletion
        doNothing().when(itemRepository).deleteById(1L);
        itemService.deleteById(1L);
        verify(itemRepository, times(1)).deleteById(1L);

        // Caz 2: Delete item nonexistent
        assertDoesNotThrow(() -> itemService.deleteById(999L));
    }

    @Test
    void testProcessItemsAsync() {
        // Caz 1: Successful processing of 3 items
        List<Long> ids = List.of(1L, 2L, 3L);
        configureMocksForProcessing(ids, true);
        CompletableFuture<List<Item>> future = itemService.processItemsAsync();
        assertEquals(3, future.join().size());

        // Caz 2: Processing with 1 item invalid
        ids = List.of(1L, 999L);
        configureMocksForProcessing(ids, false);
        future = itemService.processItemsAsync();
        assertEquals(1, future.join().size());

        // Caz 3: Error saving an item
        ids = List.of(1L);
        when(itemRepository.findAllIds()).thenReturn(ids);
        Item item = new Item(1L, "Test", "Desc", "PENDING", "test@test.com");
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(any())).thenThrow(new RuntimeException("DB Error"));
        future = itemService.processItemsAsync();
        assertTrue(future.join().isEmpty());
    }



    private void configureMocksForProcessing(List<Long> ids, boolean allValid) {
        when(itemRepository.findAllIds()).thenReturn(ids);

        ids.forEach(id -> {
            if(allValid || id != 999L) {
                Item item = new Item(id, "Item" + id, "Desc", "PENDING", "test@test.com");
                when(itemRepository.findById(id)).thenReturn(Optional.of(item));
                when(itemRepository.save(item)).thenReturn(item);
            } else {
                when(itemRepository.findById(id)).thenReturn(Optional.empty());
            }
        });
    }
}