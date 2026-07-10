package com.utopia.dms;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic and in-memory storage for customer orders. Phase 1 has no
 * database - {@link #loadOrdersFromFile(String)} seeds the in-memory list
 * from a pipe-delimited text file, and every CRUD operation below only
 * changes that in-memory list for the lifetime of the run.
 *
 * File line format (one order per line):
 * orderID|customerName|itemName|quantity|orderTotal|orderStatus|orderPlacedAt|pickupLane|completedAt
 * orderPlacedAt/completedAt use LocalDateTime's default ISO format; completedAt is "NONE" when unset.
 */
public class OrderService {

    private final List<CustomerOrder> orders;
    private final Map<String, Double> menuPrices;
    private int nextOrderId;

    public OrderService() {
        this.orders = new ArrayList<>();
        this.menuPrices = new LinkedHashMap<>();
        this.menuPrices.put("Burger Combo", 8.99);
        this.menuPrices.put("Fries", 2.99);
        this.menuPrices.put("Drink", 1.99);
        this.menuPrices.put("Salad", 6.49);
        this.menuPrices.put("Coffee", 2.49);
        this.nextOrderId = 1001;
    }

    public List<String> getMenuItemNames() {
        return new ArrayList<>(menuPrices.keySet());
    }

    /**
     * Loads orders from the given file path, replacing whatever is
     * currently in memory. Returns the number of orders successfully
     * loaded, or -1 if the path itself was blank/missing/unreadable.
     * Individual malformed lines are skipped rather than aborting the
     * whole load.
     */
    public int loadOrdersFromFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return -1;
        }

        File file = new File(filePath.trim());
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            return -1;
        }

        List<CustomerOrder> loaded = new ArrayList<>();
        int highestId = 1000;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                CustomerOrder order = parseLine(line);
                if (order != null) {
                    loaded.add(order);
                    highestId = Math.max(highestId, order.getOrderID());
                }
            }
        } catch (IOException e) {
            return -1;
        }

        orders.clear();
        orders.addAll(loaded);
        nextOrderId = highestId + 1;
        return loaded.size();
    }

    private CustomerOrder parseLine(String line) {
        try {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 9) {
                return null;
            }
            int orderID = Integer.parseInt(parts[0].trim());
            String customerName = parts[1].trim();
            String itemName = parts[2].trim();
            int quantity = Integer.parseInt(parts[3].trim());
            double orderTotal = Double.parseDouble(parts[4].trim());
            String status = parts[5].trim();
            LocalDateTime placedAt = LocalDateTime.parse(parts[6].trim());
            int pickupLane = Integer.parseInt(parts[7].trim());
            String completedText = parts[8].trim();

            if (customerName.isEmpty() || itemName.isEmpty()) {
                return null;
            }

            CustomerOrder order = new CustomerOrder(orderID, customerName, itemName,
                    quantity, orderTotal, pickupLane, placedAt);
            order.setOrderStatus(status);
            if (!"NONE".equalsIgnoreCase(completedText)) {
                order.setCompletedAt(LocalDateTime.parse(completedText));
            }
            return order;
        } catch (Exception e) {
            return null;
        }
    }

    public List<CustomerOrder> getAllOrders() {
        return new ArrayList<>(orders);
    }

    public CustomerOrder findOrderById(int orderID) {
        for (CustomerOrder order : orders) {
            if (order.getOrderID() == orderID) {
                return order;
            }
        }
        throw new OrderNotFoundException("No order found with ID " + orderID + ".");
    }

    public double calculateOrderTotal(String itemName, int quantity) {
        if (itemName == null || !menuPrices.containsKey(itemName)) {
            throw new InvalidOrderDataException("\"" + itemName + "\" is not a valid menu item.");
        }
        if (quantity <= 0) {
            throw new InvalidOrderDataException("Quantity must be greater than zero.");
        }
        return menuPrices.get(itemName) * quantity;
    }

    public CustomerOrder createOrder(String customerName, String itemName, int quantity, int pickupLane) {
        if (customerName == null || customerName.trim().isEmpty()
                || !customerName.matches("[A-Za-z' .-]+")) {
            throw new InvalidOrderDataException("Customer name is required and may only contain letters, spaces, and common punctuation.");
        }
        if (pickupLane < 1 || pickupLane > 3) {
            throw new InvalidOrderDataException("Pickup lane must be 1, 2, or 3.");
        }
        double total = calculateOrderTotal(itemName, quantity);

        CustomerOrder order = new CustomerOrder(nextOrderId, customerName.trim(), itemName,
                quantity, total, pickupLane, LocalDateTime.now());
        nextOrderId++;
        orders.add(order);
        return order;
    }

    public boolean removeOrder(int orderID) {
        CustomerOrder order = findOrderById(orderID);
        if (CustomerOrder.STATUS_COMPLETED.equals(order.getOrderStatus())) {
            throw new InvalidOrderDataException("Completed orders cannot be removed; they are kept for order history.");
        }
        return orders.remove(order);
    }

    public boolean updateOrder(int orderID, String field, String newValue) {
        CustomerOrder order = findOrderById(orderID);
        if (!order.isActive()) {
            throw new InvalidOrderDataException("Only pending orders can be edited.");
        }

        boolean applied;
        switch (field.toLowerCase()) {
            case "customername":
                applied = order.setCustomerName(newValue);
                break;
            case "itemname":
                double total = calculateOrderTotal(newValue, order.getQuantity());
                applied = order.setItemName(newValue) && order.setOrderTotal(total);
                break;
            case "quantity":
                int quantity = parseIntOrThrow(newValue, "Quantity");
                double recalculated = calculateOrderTotal(order.getItemName(), quantity);
                applied = order.setQuantity(quantity) && order.setOrderTotal(recalculated);
                break;
            case "pickuplane":
                int lane = parseIntOrThrow(newValue, "Pickup lane");
                applied = order.setPickupLane(lane);
                break;
            default:
                throw new InvalidOrderDataException("Unknown field \"" + field + "\". Valid fields: customerName, itemName, quantity, pickupLane.");
        }

        if (!applied) {
            throw new InvalidOrderDataException("\"" + newValue + "\" is not a valid value for " + field + ".");
        }
        return true;
    }

    public long completeOrder(int orderID) {
        CustomerOrder order = findOrderById(orderID);
        if (!order.isActive()) {
            throw new InvalidOrderDataException("Order " + orderID + " is already " + order.getOrderStatus().toLowerCase() + " and cannot be completed again.");
        }
        order.setOrderStatus(CustomerOrder.STATUS_COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        return order.calculatePrepTimeMinutes();
    }

    private int parseIntOrThrow(String text, String fieldLabel) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new InvalidOrderDataException(fieldLabel + " must be a whole number.");
        }
    }
}
