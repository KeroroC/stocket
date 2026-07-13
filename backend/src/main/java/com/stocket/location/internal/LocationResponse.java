package com.stocket.location.internal;

import java.util.UUID;

record LocationResponse(UUID id, UUID parentId, String name, String fullPath, String publicCode,
                        long version, boolean archived) { }
