package com.bookshopweb.servlet.admin.order;

import com.bookshopweb.beans.Order;
import com.bookshopweb.beans.OrderItem;
import com.bookshopweb.service.OrderItemService;
import com.bookshopweb.service.OrderService;
import com.bookshopweb.service.UserService;
import com.bookshopweb.utils.Protector;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@WebServlet(name = "OrderManagerServlet", value = "/admin/orderManager")
public class OrderManagerServlet extends HttpServlet {
    private final OrderService orderService = new OrderService();
    private final UserService userService = new UserService();
    private final OrderItemService orderItemService = new OrderItemService();

    private static final int ORDERS_PER_PAGE = 10;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int totalOrders = Protector.of(orderService::count).get(0);
        int totalPages = totalOrders / ORDERS_PER_PAGE + (totalOrders % ORDERS_PER_PAGE != 0 ? 1 : 0);

        String pageParam = Optional.ofNullable(request.getParameter("page")).orElse("1");
        int page = Protector.of(() -> Integer.parseInt(pageParam)).get(1);
        if (page < 1 || page > totalPages) {
            page = 1;
        }

        int offset = (page - 1) * ORDERS_PER_PAGE;

        List<Order> orders;

        String startDateParam = request.getParameter("startDate");
        String endDateParam = request.getParameter("endDate");

        if (startDateParam != null && endDateParam != null) {
            // Convert startDateParam and endDateParam to LocalDate
            LocalDate startDate = LocalDate.parse(startDateParam);
            LocalDate endDate = LocalDate.parse(endDateParam).plusDays(1); // Add one day to include the end date

            // Retrieve orders within the date range
            orders = Protector.of(() -> orderService.getOrdersByDateRange(startDate, endDate)).get(ArrayList::new);
        } else {
            orders = Protector.of(() -> orderService.getOrderedPart(
                    ORDERS_PER_PAGE, offset, "id", "DESC"
            )).get(ArrayList::new);
        }

        for (Order order : orders) {
            Protector.of(() -> userService.getById(order.getUserId())).get(Optional::empty).ifPresent(order::setUser);
            List<OrderItem> orderItems = Protector.of(() -> orderItemService.getByOrderId(order.getId())).get(ArrayList::new);
            order.setOrderItems(orderItems);
            order.setTotalPrice(calculateTotalPrice(orderItems, order.getDeliveryPrice()));
        }

        double totalAmount = calculateTotalAmount(orders);
        request.setAttribute("totalAmount", totalAmount);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("page", page);
        request.setAttribute("orders", orders);
        request.getRequestDispatcher("/WEB-INF/views/orderManagerView.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Handle form submission when the user clicks the search button
        String startDateParam = request.getParameter("startDate");
        String endDateParam = request.getParameter("endDate");

        if (startDateParam != null && endDateParam != null) {
            // Convert startDateParam and endDateParam to LocalDate
            LocalDate startDate = LocalDate.parse(startDateParam);
            LocalDate endDate = LocalDate.parse(endDateParam).plusDays(1); // Add one day to include the end date

            // Retrieve orders within the date range
            List<Order> orders = Protector.of(() -> orderService.getOrdersByDateRange(startDate, endDate)).get(ArrayList::new);

            double totalAmount = calculateTotalAmount(orders);
            request.setAttribute("totalAmount", totalAmount);

            // Additional logic to populate user, order items, and total price (similar to existing code)
            System.out.println("check tổng tiền: " + totalAmount);
            request.setAttribute("orders", orders);
        }

        // Existing code to forward to JSP
        request.getRequestDispatcher("/WEB-INF/views/orderManagerView.jsp").forward(request, response);
    }

    private double calculateTotalAmount(List<Order> orders) {
        double totalAmount = 0;
        for (Order order : orders) {
            totalAmount += order.getTotalPrice();
        }
        return totalAmount;
    }

    public static double calculateTotalPrice(List<OrderItem> orderItems, double deliveryPrice) {
        double totalPrice = deliveryPrice;

        for (OrderItem orderItem : orderItems) {
            if (orderItem.getDiscount() == 0) {
                totalPrice += orderItem.getPrice() * orderItem.getQuantity();
            } else {
                totalPrice += (orderItem.getPrice() * (100 - orderItem.getDiscount()) / 100) * orderItem.getQuantity();
            }
        }

        return totalPrice;
    }
}
