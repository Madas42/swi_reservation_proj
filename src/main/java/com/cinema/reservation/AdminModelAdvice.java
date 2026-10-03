package com.cinema.reservation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes the admin login state available to every rendered template
 * (used by the admin login button fragment).
 */
@ControllerAdvice
public class AdminModelAdvice {

    @ModelAttribute
    public void adminAttributes(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        model.addAttribute("isAdmin", AdminController.isLoggedIn(session));
    }
}
