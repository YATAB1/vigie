package io.github.yatab1.vigie.user;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only endpoints. Access is enforced in SecurityConfig (/api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final UserRepository users;

    public AdminUserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers() {
        return users.findAll(Sort.by("createdAt")).stream().map(UserResponse::from).toList();
    }
}
