package com.utopia.dms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for OrderService, covering file loading and every CRUD/custom
 * action per the Module 8 rubric. Each feature has one test proving correct
 * behavior with valid input and one proving invalid input/state is rejected
 * without crashing the program.
 */
public class OrderServiceTest {

    // ---- Opening a File ----

    @Test
    void testLoadOrdersFromFile_validFile_loadsAllRecords(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("orders.txt");
        Files.writeString(file, String.join("\n",
                "1001|Maya Johnson|Burger Combo|2|17.98|Pending|2026-07-06T10:15:00|1|NONE",
                "1002|Chris Lee|Fries|1|2.99|Pending|2026-07-06T10:20:00|2|NONE"));

        OrderService service = new OrderService();
        int loaded = service.loadOrdersFromFile(file.toString());

        assertEquals(2, loaded);
        assertEquals(2, service.getAllOrders().size());
    }

    @Test
    void testLoadOrdersFromFile_missingFile_returnsNegativeOne() {
        OrderService service = new OrderService();

        int loaded = service.loadOrdersFromFile("does_not_exist.txt");

        assertEquals(-1, loaded);
        assertTrue(service.getAllOrders().isEmpty());
    }

    // ---- Add Objects ----

    @Test
    void testCreateOrder_validData_addsOrderAndReturnsIt() {
        OrderService service = new OrderService();

        CustomerOrder created = service.createOrder("Nina Carter", "Coffee", 2, 2);

        assertEquals("Nina Carter", created.getCustomerName());
        assertEquals(4.98, created.getOrderTotal(), 0.001);
        assertEquals(1, service.getAllOrders().size());
    }

    @Test
    void testCreateOrder_invalidCustomerName_throwsInvalidOrderDataException() {
        OrderService service = new OrderService();

        assertThrows(InvalidOrderDataException.class,
                () -> service.createOrder("John123", "Coffee", 2, 2));
        assertTrue(service.getAllOrders().isEmpty());
    }

    // ---- Remove Objects ----

    @Test
    void testRemoveOrder_existingPendingOrder_removesSuccessfully() {
        OrderService service = new OrderService();
        CustomerOrder order = service.createOrder("Nina Carter", "Coffee", 2, 2);

        boolean removed = service.removeOrder(order.getOrderID());

        assertTrue(removed);
        assertTrue(service.getAllOrders().isEmpty());
    }

    @Test
    void testRemoveOrder_nonexistentId_throwsOrderNotFoundException() {
        OrderService service = new OrderService();

        assertThrows(OrderNotFoundException.class, () -> service.removeOrder(9999));
    }

    // ---- Update Objects ----

    @Test
    void testUpdateOrder_validQuantityChange_updatesAndRecalculatesTotal() {
        OrderService service = new OrderService();
        CustomerOrder order = service.createOrder("Nina Carter", "Coffee", 2, 2);

        boolean updated = service.updateOrder(order.getOrderID(), "quantity", "5");

        assertTrue(updated);
        assertEquals(5, order.getQuantity());
        assertEquals(12.45, order.getOrderTotal(), 0.001);
    }

    @Test
    void testUpdateOrder_nonPendingOrder_throwsInvalidOrderDataException() {
        OrderService service = new OrderService();
        CustomerOrder order = service.createOrder("Nina Carter", "Coffee", 2, 2);
        service.completeOrder(order.getOrderID());

        assertThrows(InvalidOrderDataException.class,
                () -> service.updateOrder(order.getOrderID(), "quantity", "3"));
    }

    // ---- Custom Action ----

    @Test
    void testCompleteOrder_validPendingOrder_returnsPrepTimeAndMarksCompleted() {
        OrderService service = new OrderService();
        CustomerOrder order = service.createOrder("Nina Carter", "Coffee", 2, 2);

        long prepTimeMinutes = service.completeOrder(order.getOrderID());

        assertTrue(prepTimeMinutes >= 0);
        assertEquals(CustomerOrder.STATUS_COMPLETED, order.getOrderStatus());
    }

    @Test
    void testCompleteOrder_alreadyCompleted_throwsInvalidOrderDataException() {
        OrderService service = new OrderService();
        CustomerOrder order = service.createOrder("Nina Carter", "Coffee", 2, 2);
        service.completeOrder(order.getOrderID());

        assertThrows(InvalidOrderDataException.class,
                () -> service.completeOrder(order.getOrderID()));
    }
}
