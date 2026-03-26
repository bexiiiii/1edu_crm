package com.ondeedu.common.security;

import com.ondeedu.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class PermissionUtils {

    private static final Set<String> VALID_PERMISSIONS = Arrays.stream(SystemPermission.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private PermissionUtils() {
    }

    public static List<String> normalizePermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalizedPermissions = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (!StringUtils.hasText(permission)) {
                throw new BusinessException("INVALID_PERMISSION", "Permission code cannot be blank");
            }

            String normalizedPermission = permission.trim().toUpperCase(Locale.ROOT);
            if (!VALID_PERMISSIONS.contains(normalizedPermission)) {
                throw new BusinessException("INVALID_PERMISSION",
                        "Unknown permission code: " + normalizedPermission);
            }

            normalizedPermissions.add(normalizedPermission);
        }

        return List.copyOf(normalizedPermissions);
    }
}
