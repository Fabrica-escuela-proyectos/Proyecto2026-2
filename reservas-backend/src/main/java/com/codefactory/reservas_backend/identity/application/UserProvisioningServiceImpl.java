package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.domain.DuplicateEmailException;
import com.codefactory.reservas_backend.identity.domain.DuplicatePhoneException;
import com.codefactory.reservas_backend.identity.domain.Role;
import com.codefactory.reservas_backend.identity.domain.RoleName;
import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.RoleRepository;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserProvisioningServiceImpl implements UserProvisioningService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public ProvisionedUser provisionUser(String fullName, String email, String cellphone, String rawPassword, RoleName roleName) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException("El correo electrónico ya está en uso");
        }
        if (userRepository.existsByCellphone(cellphone)) {
            throw new DuplicatePhoneException("El número de celular ya está en uso");
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol " + roleName + " no está inicializado en la base de datos. "
                                + "Verificar el seed de V1__create_identity_schema.sql"));

        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .cellphone(cellphone)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .roles(Set.of(role))
                .build();

        User saved = userRepository.save(user);
        return new ProvisionedUser(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getCellphone(), roleName);
    }
}
