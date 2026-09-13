package com.carbonlink.service;

import com.carbonlink.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderNumberGeneratorTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderNumberGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new OrderNumberGenerator(orderRepository);
    }

    @Test
    void next_firstCallOfTheYear_seedsFromPersistedCountAndReturnsTheNextNumber() {
        int year = LocalDate.now().getYear();
        when(orderRepository.countByOrderNumberStartingWith("CL-" + year + "-")).thenReturn(3L);

        String orderNumber = generator.next();

        assertThat(orderNumber).isEqualTo("CL-" + year + "-0004");
    }

    @Test
    void next_freshYearWithNoExistingOrders_startsAtOne() {
        int year = LocalDate.now().getYear();
        when(orderRepository.countByOrderNumberStartingWith("CL-" + year + "-")).thenReturn(0L);

        assertThat(generator.next()).isEqualTo("CL-" + year + "-0001");
    }

    @Test
    void next_subsequentCallsInTheSameProcess_incrementWithoutRequeryingTheRepository() {
        int year = LocalDate.now().getYear();
        when(orderRepository.countByOrderNumberStartingWith("CL-" + year + "-")).thenReturn(0L);

        assertThat(generator.next()).isEqualTo("CL-" + year + "-0001");
        assertThat(generator.next()).isEqualTo("CL-" + year + "-0002");
        assertThat(generator.next()).isEqualTo("CL-" + year + "-0003");

        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.times(1))
                .countByOrderNumberStartingWith("CL-" + year + "-");
    }

    @Test
    void next_padsSequenceToFourDigits_evenForDoubleDigitCounts() {
        int year = LocalDate.now().getYear();
        when(orderRepository.countByOrderNumberStartingWith("CL-" + year + "-")).thenReturn(98L);

        assertThat(generator.next()).isEqualTo("CL-" + year + "-0099");
        assertThat(generator.next()).isEqualTo("CL-" + year + "-0100");
    }
}
