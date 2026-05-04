package com.ondeedu.settings.service;

import com.ondeedu.settings.entity.AttendanceStatusConfig;
import com.ondeedu.settings.mapper.AttendanceStatusConfigMapper;
import com.ondeedu.settings.repository.AttendanceStatusConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceStatusConfigServiceTest {

    @Mock
    private AttendanceStatusConfigRepository repository;

    @Mock
    private AttendanceStatusConfigMapper mapper;

    @InjectMocks
    private AttendanceStatusConfigService service;

    @Test
    void deleteAllowsSeededSystemStatus() {
        UUID statusId = UUID.randomUUID();
        AttendanceStatusConfig entity = AttendanceStatusConfig.builder()
                .systemStatus(true)
                .build();

        when(repository.findById(statusId)).thenReturn(Optional.of(entity));

        service.delete(statusId);

        verify(repository).deleteById(statusId);
    }
}
