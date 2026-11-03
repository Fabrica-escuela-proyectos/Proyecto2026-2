package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountStatusServiceImpl implements AccountStatusService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabled(UUID userId) {
        return userRepository.findById(userId).map(User::isEnabled).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> enabledAmong(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return userRepository.findAllById(userIds).stream()
                .filter(User::isEnabled)
                .map(User::getId)
                .collect(Collectors.toSet());
    }
}
