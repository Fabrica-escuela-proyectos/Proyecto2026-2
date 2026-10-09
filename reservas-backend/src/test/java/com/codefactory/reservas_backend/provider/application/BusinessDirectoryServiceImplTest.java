package com.codefactory.reservas_backend.provider.application;

import com.codefactory.reservas_backend.provider.domain.Business;
import com.codefactory.reservas_backend.provider.domain.BusinessNotFoundException;
import com.codefactory.reservas_backend.provider.infrastructure.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessDirectoryServiceImplTest {

    @Mock
    private BusinessRepository businessRepository;

    private BusinessDirectoryServiceImpl directory;

    @BeforeEach
    void setUp() {
        directory = new BusinessDirectoryServiceImpl(businessRepository);
    }

    @Test
    void listDebePaginarOrdenandoPorNombreSinMayusculasYLuegoPorId() {
        Business b = Business.builder().id(UUID.randomUUID()).providerId(UUID.randomUUID()).name("Spa").build();
        Page<Business> page = new PageImpl<>(List.of(b), PageRequest.of(2, 10), 21);
        when(businessRepository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(page);

        BusinessPage result = directory.list(2, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(businessRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().getOrderFor("name").isIgnoreCase()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("id")).isNotNull();
        assertThat(result.items()).containsExactly(new BusinessInfo(b.getId(), "Spa"));
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.page()).isEqualTo(2);
    }

    @Test
    void getDebeDevolverElNegocio() {
        UUID id = UUID.randomUUID();
        when(businessRepository.findById(id)).thenReturn(Optional.of(
                Business.builder().id(id).providerId(UUID.randomUUID()).name("Spa").build()));

        assertThat(directory.get(id)).isEqualTo(new BusinessInfo(id, "Spa"));
    }

    @Test
    void getDebeFallarSiNoExiste() {
        UUID id = UUID.randomUUID();
        when(businessRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directory.get(id)).isInstanceOf(BusinessNotFoundException.class);
    }
}
