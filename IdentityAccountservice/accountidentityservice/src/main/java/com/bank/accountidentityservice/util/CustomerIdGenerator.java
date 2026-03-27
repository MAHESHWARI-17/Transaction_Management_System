package com.bank.accountidentityservice.util;

import com.bank.accountidentityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerIdGenerator {

    private final UserRepository userRepository;

    private static final long START_ID = 100000000001L;

    public String generate() {
        return userRepository.findMaxCustomerId()
                .map(maxId -> String.valueOf(Long.parseLong(maxId) + 1))
                .orElse(String.valueOf(START_ID));
    }
}
