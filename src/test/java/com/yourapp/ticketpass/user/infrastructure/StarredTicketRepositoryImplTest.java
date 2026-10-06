package com.yourapp.ticketpass.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StarredTicketRepositoryImplTest {

    @Mock
    private StarredTicketJpaRepository jpaRepository;

    @InjectMocks
    private StarredTicketRepositoryImpl repository;

    @Test
    @DisplayName("findUserIdsByTicketId ủy quyền sang jpaRepository khi ticketId hợp lệ")
    void testFindUserIdsByTicketId_Success() {
        when(jpaRepository.findUserIdsByTicketId(10L)).thenReturn(List.of(1L, 2L, 3L));

        List<Long> result = repository.findUserIdsByTicketId(10L);

        assertThat(result).containsExactly(1L, 2L, 3L);
        verify(jpaRepository).findUserIdsByTicketId(10L);
    }

    @Test
    @DisplayName("findUserIdsByTicketId trả về rỗng khi ticketId là null")
    void testFindUserIdsByTicketId_NullTicketId_ReturnsEmpty() {
        List<Long> result = repository.findUserIdsByTicketId(null);

        assertThat(result).isEmpty();
    }
}
