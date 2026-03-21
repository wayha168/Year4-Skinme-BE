package com.project.skin_me.repository;

import com.project.skin_me.model.KhqrBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KhqrBankAccountRepository extends JpaRepository<KhqrBankAccount, Long> {

    List<KhqrBankAccount> findAllByOrderByDisplayOrderAscGatewayAsc();

    /** All active accounts ordered for Telegram routing (first with telegramChatId wins). */
    List<KhqrBankAccount> findAllByActiveTrueOrderByDisplayOrderAsc();

    List<KhqrBankAccount> findByGatewayAndActiveTrueOrderByDisplayOrderAsc(String gateway);
}
