package com.codefactory.reservas_backend.identity.application;

import com.codefactory.reservas_backend.identity.domain.User;
import com.codefactory.reservas_backend.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDirectoryServiceImpl implements UserDirectoryService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findFullName(UUID userId) {
        return userRepository.findById(userId).map(User::getFullName);
    }
}
