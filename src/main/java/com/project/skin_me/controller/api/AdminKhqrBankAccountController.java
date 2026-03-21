package com.project.skin_me.controller.api;

import com.project.skin_me.model.KhqrBankAccount;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.payment.IBakongKhqrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/admin/khqr-accounts")
public class AdminKhqrBankAccountController {

    private final IBakongKhqrService bakongKhqrService;

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> list() {
        List<KhqrBankAccount> accounts = bakongKhqrService.findAll();
        return ResponseEntity.ok(new ApiResponse("OK", accounts));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> getById(@PathVariable Long id) {
        return bakongKhqrService.findById(id)
                .map(a -> ResponseEntity.ok(new ApiResponse("OK", a)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse("KHQR bank account not found", null)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> create(@RequestBody KhqrBankAccount account) {
        try {
            KhqrBankAccount created = bakongKhqrService.create(account);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse("Bank account created", created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(e.getMessage(), null));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> update(@PathVariable Long id, @RequestBody KhqrBankAccount account) {
        try {
            KhqrBankAccount updated = bakongKhqrService.update(id, account);
            return ResponseEntity.ok(new ApiResponse("Bank account updated", updated));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> delete(@PathVariable Long id) {
        try {
            bakongKhqrService.deleteById(id);
            return ResponseEntity.ok(new ApiResponse("Bank account deleted", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        }
    }
}
