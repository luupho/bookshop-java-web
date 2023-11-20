package com.bookshopweb.servlet.client;

import com.bookshopweb.beans.User;
import com.bookshopweb.service.UserService;
import com.bookshopweb.utils.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@WebServlet(name = "ForgotPasswordServlet", value = "/forgot-password")
public class ForgotPasswordServlet extends HttpServlet {
    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/views/forgotPasswordView.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Map<String, String> values = new HashMap<>();
        values.put("username", request.getParameter("username"));
        values.put("email", request.getParameter("email"));

        Map<String, List<String>> violations = new HashMap<>();
        Optional<User> userFromServer = Protector.of(() -> userService.getByUsername(values.get("username")))
                .get(Optional::empty);
        violations.put("usernameViolations", Validator.of(values.get("username"))
                .isNotNullAndEmpty()
                .isNotBlankAtBothEnds()
                .isAtMostOfLength(25)
                .isExistent(userFromServer.isPresent(), "Tên đăng nhập")
                .toList());
        violations.put("emailViolations", Validator.of(values.get("email"))
                .isNotNullAndEmpty()
                .isNotBlankAtBothEnds()
                .isAtMostOfLength(32)
                .changeTo(values.get("email"))
                .isEqualTo(userFromServer.map(User::getEmail).orElse(""), "Email")
                .toList());

        int sumOfViolations = violations.values().stream().mapToInt(List::size).sum();

        if (sumOfViolations == 0 && userFromServer.isPresent()) {
            // ton tai user
            // Tạo mật khẩu mới
            String newPassword = PasswordGenerator.generateRandomPassword(5); // Hàm phát sinh mật khẩu ngẫu nhiên

            // Lưu mật khẩu mới vào cơ sở dữ liệu
            userFromServer.ifPresent(user -> {
                String hashedPassword = HashingUtils.hash(newPassword); // Hash mật khẩu trước khi lưu vào cơ sở dữ liệu
                user.setPassword(hashedPassword);
                userService.update(user);
            });

            // Gửi mật khẩu mới đến email của người dùng
            sendPasswordResetEmail(values.get("email"), newPassword);

            // Hiển thị thông báo thành công hoặc chuyển hướng đến trang đăng nhập
            request.setAttribute("successMessage", "Mật khẩu mới đã được gửi đến email của bạn.");
            request.getRequestDispatcher("/WEB-INF/views/forgotPasswordView.jsp").forward(request, response);

        } else {
            request.setAttribute("values", values);
            request.setAttribute("violations", violations);
            request.getRequestDispatcher("/WEB-INF/views/forgotPasswordView.jsp").forward(request, response);
        }
    }

    private void sendPasswordResetEmail(String userEmail, String newPassword) {
        // Gửi email thông báo cập nhật thông tin, bạn có thể sử dụng thư viện email của mình hoặc thư viện bên ngoài
        String subject = "Cập nhật mật khẩu người dùng";
        String body = "Mật khẩu mới của bạn là: " + newPassword;
        EmailUtils.sendEmail(userEmail, subject, body);
    }
}
