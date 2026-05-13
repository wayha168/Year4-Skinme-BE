package com.project.skin_me.unit.service.payment;

import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.KhqrBankAccount;
import com.project.skin_me.repository.KhqrBankAccountRepository;
import com.project.skin_me.service.payment.BakongKhqrService;
import com.project.skin_me.service.payment.IBakongKhqrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for KHQR / payment account helpers ({@link BakongKhqrService}).
 */
@ExtendWith(MockitoExtension.class)
class BakongKhqrServiceTest {

    @Mock
    private KhqrBankAccountRepository repository;

    @InjectMocks
    private BakongKhqrService bakongKhqrService;

    private KhqrBankAccount activeAba;

    @BeforeEach
    void setUp() {
        activeAba = new KhqrBankAccount();
        activeAba.setId(1L);
        activeAba.setGateway(IBakongKhqrService.GATEWAY_ABA);
        activeAba.setAccount("85512345678");
        activeAba.setMerchantName("SkinMe");
        activeAba.setCity("Phnom Penh");
        activeAba.setCategoryCode("5999");
        activeAba.setActive(true);
        activeAba.setUseTestWhenEmpty(true);
    }

    @Test
    void getFirstActiveTelegramChatId_skipsBlankAndReturnsFirst() {
        KhqrBankAccount a = new KhqrBankAccount();
        a.setActive(true);
        a.setTelegramChatId("  ");
        KhqrBankAccount b = new KhqrBankAccount();
        b.setActive(true);
        b.setTelegramChatId("12345");

        when(repository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(a, b));

        assertThat(bakongKhqrService.getFirstActiveTelegramChatId()).contains("12345");
    }

    @Test
    void generateKhqrData_throwsWhenNoActiveAccountForGateway() {
        when(repository.findByGatewayAndActiveTrueOrderByDisplayOrderAsc(IBakongKhqrService.GATEWAY_ABA))
                .thenReturn(List.of());

        assertThatThrownBy(() -> bakongKhqrService.generateKhqrData(
                BigDecimal.TEN, "USD", IBakongKhqrService.GATEWAY_ABA, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ABA has no active bank account");
    }

    @Test
    void generateKhqrData_throwsWhenAccountMissingAndTestFallbackDisabled() {
        activeAba.setAccount("");
        activeAba.setUseTestWhenEmpty(false);
        when(repository.findByGatewayAndActiveTrueOrderByDisplayOrderAsc(IBakongKhqrService.GATEWAY_ABA))
                .thenReturn(List.of(activeAba));

        assertThatThrownBy(() -> bakongKhqrService.generateKhqrData(
                BigDecimal.ONE, "USD", IBakongKhqrService.GATEWAY_ABA, 9L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("merchant account is not set");
    }

    @Test
    void generateKhqrData_producesEmvPayloadWithCrcAndOrderTag() {
        when(repository.findByGatewayAndActiveTrueOrderByDisplayOrderAsc(IBakongKhqrService.GATEWAY_ABA))
                .thenReturn(List.of(activeAba));

        String data = bakongKhqrService.generateKhqrData(
                new BigDecimal("12.50"), "USD", IBakongKhqrService.GATEWAY_ABA, 42L);

        assertThat(data).startsWith("000201010212");
        assertThat(data).contains("6304");
        assertThat(data).contains("6206010242");
    }

    @Test
    void create_normalizesBlankGatewayToKhqr() {
        KhqrBankAccount input = new KhqrBankAccount();
        input.setGateway(null);
        input.setMerchantName("M");
        input.setCity("PP");
        input.setCategoryCode("5999");

        when(repository.save(any(KhqrBankAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bakongKhqrService.create(input);

        ArgumentCaptor<KhqrBankAccount> captor = ArgumentCaptor.forClass(KhqrBankAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getGateway()).isEqualTo(IBakongKhqrService.GATEWAY_KHQR);
    }

    @Test
    void deleteById_throwsWhenMissing() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bakongKhqrService.deleteById(5L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).delete(any());
    }
}
