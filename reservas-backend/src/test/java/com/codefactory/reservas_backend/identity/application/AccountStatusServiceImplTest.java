package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatusServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private AccountStatusServiceImpl service;

    @Test
    void unaCuentaHabilitadaEstaActiva() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setEnabled(true);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThat(service.isEnabled(id)).isTrue();
    }

    @Test
    void unaCuentaDeshabilitadaNoEstaActiva() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setEnabled(false);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThat(service.isEnabled(id)).isFalse();
    }

    @Test
    void unaCuentaInexistenteNoEstaActiva() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.isEnabled(id)).isFalse();
    }
}
