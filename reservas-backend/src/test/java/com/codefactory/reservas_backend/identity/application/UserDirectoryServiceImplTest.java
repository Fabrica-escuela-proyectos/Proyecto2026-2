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
class UserDirectoryServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserDirectoryServiceImpl service;

    @Test
    void debeDevolverElNombreCompletoDelUsuario() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setFullName("Ana Cliente");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThat(service.findFullName(id)).contains("Ana Cliente");
    }

    @Test
    void debeDevolverVacioSiElUsuarioNoExiste() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findFullName(id)).isEmpty();
    }
}
