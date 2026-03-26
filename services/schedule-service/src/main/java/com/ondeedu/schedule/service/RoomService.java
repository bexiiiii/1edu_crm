package com.ondeedu.schedule.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.schedule.dto.CreateRoomRequest;
import com.ondeedu.schedule.dto.RoomDto;
import com.ondeedu.schedule.dto.UpdateRoomRequest;
import com.ondeedu.schedule.entity.Room;
import com.ondeedu.schedule.entity.RoomStatus;
import com.ondeedu.schedule.mapper.RoomMapper;
import com.ondeedu.schedule.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;

    @Transactional
    @CacheEvict(value = "rooms", allEntries = true)
    public RoomDto createRoom(CreateRoomRequest request) {
        Room room = roomMapper.toEntity(request);
        room = roomRepository.save(room);
        log.info("Created room: {} ({})", room.getName(), room.getId());
        return roomMapper.toDto(room);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "rooms", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public RoomDto getRoom(UUID id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", id));
        return roomMapper.toDto(room);
    }

    @Transactional
    @CacheEvict(value = "rooms", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public RoomDto updateRoom(UUID id, UpdateRoomRequest request) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", id));
        roomMapper.updateEntity(room, request);
        room = roomRepository.save(room);
        log.info("Updated room: {}", id);
        return roomMapper.toDto(room);
    }

    @Transactional
    @CacheEvict(value = "rooms", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public void deleteRoom(UUID id) {
        if (!roomRepository.existsById(id)) {
            throw new ResourceNotFoundException("Room", "id", id);
        }
        roomRepository.deleteById(id);
        log.info("Deleted room: {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomDto> listRooms(RoomStatus status, Pageable pageable) {
        Page<Room> page;
        if (status != null) {
            page = roomRepository.findByStatus(status, pageable);
        } else {
            page = roomRepository.findAll(pageable);
        }
        return PageResponse.from(page, roomMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomDto> searchRooms(String query, Pageable pageable) {
        Page<Room> page = roomRepository.search(query, pageable);
        return PageResponse.from(page, roomMapper::toDto);
    }
}
